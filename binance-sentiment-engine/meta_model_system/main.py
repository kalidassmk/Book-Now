import asyncio
import aiohttp
import logging
import json
import redis
import time
import os
import sys
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))
from symbols_config import ACTIVE_SYMBOLS
from data_collector import DataCollector
# Logging Setup
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(levelname)-7s | %(name)s | %(message)s",
)
log = logging.getLogger("meta_model.main")

try:
    import pandas as pd
    from feature_engineering import FeatureEngineer
    from dataset_builder import DatasetBuilder
    from model_trainer import MetaModelTrainer, HAS_XGBOOST
    from predictor import MetaPredictor
    ML_READY = True
except Exception as e:
    log.warning(f"ML components initialization failed: {e}")
    ML_READY = False
    HAS_XGBOOST = False
    pd = None

class MetaModelSystem:
    """
    Orchestrator for the Meta-Model System.
    1. Collects live signals from Redis.
    2. Runs ML inference (XGBoost).
    3. Publishes win-probability to Redis.
    4. (Triggered manually) Trains/Retrains the model.
    """
    def __init__(self, symbols=None, interval_sec=10):
        if symbols is None:
            symbols = ACTIVE_SYMBOLS
        self.symbols = symbols
        self.interval_sec = interval_sec
        self.redis_client = redis.Redis(host='127.0.0.1', port=6379, decode_responses=True)
        
        if ML_READY:
            self.engineer = FeatureEngineer()
            self.predictor = MetaPredictor()
        else:
            self.engineer = None
            self.predictor = None
        
        # Paths
        self.data_dir = "training_data"
        os.makedirs(self.data_dir, exist_ok=True)

    async def run(self):
        log.info(f"🧠 [INITIALIZING] Meta-Model System for {self.symbols}")
        
        if not ML_READY:
            log.warning("⚠️  ML modules (pandas, sklearn, or xgboost) are missing. Meta-Model will run in HEURISTIC fallback mode.")
            # We don't return here, we proceed to use the fallback logic

        if not HAS_XGBOOST:
            log.warning("⚠️  'xgboost' not installed. System will run in PASSIVE mode (returning 0.5 for all).")
            log.warning("   To enable ML features, run: ../venv313/bin/pip install xgboost scikit-learn")

        # Check if model exists, if not, offer a warm-up training simulation
        elif ML_READY and self.predictor and self.predictor.model is None:
            log.warning("No trained model found. Running initial training simulation...")
            await self.run_training_cycle()
            self.predictor = MetaPredictor() # Reload predictor

        connector = aiohttp.TCPConnector(ssl=False)
        async with aiohttp.ClientSession(connector=connector) as session:
            collector = DataCollector(session)
            
            while True:
                start_time = time.time()
                for symbol in self.symbols:
                    try:
                        # 1. Collect live data from all other services
                        log.debug(f"[{symbol}] Step 1: Collecting market features...")
                        raw_features = await collector.fetch_all_features(symbol)
                        if not raw_features: 
                            log.warning(f"[{symbol}] Data collection returned empty results.")
                            continue
                        
                        # 2. Engineer features (normalization + interaction)
                        log.debug(f"[{symbol}] Step 2: Engineering features...")
                        if self.engineer:
                            engineered = self.engineer.transform(raw_features)
                        else:
                            engineered = raw_features
                        
                        # 3. Inference / Heuristic Fallback
                        log.debug(f"[{symbol}] Step 3: Running ML inference...")
                        if ML_READY and self.predictor and self.predictor.model:
                            prob = self.predictor.predict_probability(engineered)
                        else:
                            prob = self._heuristic_predict(raw_features)
                        
                        # 4. Storage for Consensus Engine
                        log.debug(f"[{symbol}] Step 4: Publishing to Redis...")
                        result = {
                            "symbol": symbol,
                            "probability": prob,
                            "timestamp": time.time(),
                            "features": raw_features 
                        }
                        self.redis_client.hset("META_MODEL_PREDICTIONS", symbol, json.dumps(result))
                        
                        # 5. Continuous Logging (Visible at INFO level)
                        mode = "ML" if (ML_READY and self.predictor and self.predictor.model) else "HEURISTIC"
                        log.info(f"🧠 [{symbol}] Task: COMPLETE | Prob: {prob:.4f} | Mode: {mode} | RSI: {raw_features.get('rsi', 0):.1f}")

                    except Exception as e:
                        log.error(f"Error in prediction loop for {symbol}: {e}")

                # Maintain loop interval
                elapsed = time.time() - start_time
                wait = max(0, self.interval_sec - elapsed)
                await asyncio.sleep(wait)

    def _heuristic_predict(self, features):
        """
        Fallback logic: Returns a 'confidence' score based on existing signals.
        - High OBI (Orderbook Imbalance) -> Higher prob
        - Trending Regime -> Higher prob
        - Low Risk Score -> Higher prob
        """
        score = 0.5 # Neutral base
        
        # OBI influence (0 to 100)
        obi = features.get('obi_ratio', 50)
        score += (obi - 50) / 200 # +/- 0.25
        
        # Regime influence
        regime = features.get('market_regime', 'RANGING')
        if regime == 'TRENDING_UP': score += 0.1
        elif regime == 'TRENDING_DOWN': score -= 0.1
        
        # Risk influence
        risk = features.get('risk_score', 50)
        score -= (risk - 50) / 200 # Higher risk -> lower prob
        
        return max(0.01, min(0.99, score))

    async def run_training_cycle(self):
        """
        Fetches historical klines and trains the model.
        In a live environment, this would run every 24h.
        """
        log.info("🚀 Starting Model Training Cycle...")
        connector = aiohttp.TCPConnector(ssl=False)
        async with aiohttp.ClientSession(connector=connector) as session:
            collector = DataCollector(session)
            builder = DatasetBuilder(collector, self.engineer)
            trainer = MetaModelTrainer()

            all_data = []
            for symbol in self.symbols:
                log.info(f"📊 [{symbol}] Processing training data...")
                # Fetch last 1000 candles for training
                klines = await collector.fetch_historical_klines(symbol, limit=1000)
                if klines:
                    df = await builder.build_historical_dataset(symbol, klines)
                    log.info(f"📊 [{symbol}] Prepared {len(df)} samples.")
                    all_data.append(df)
                else:
                    log.warning(f"📊 [{symbol}] Skipped due to no data.")

            if all_data:
                final_df = pd.concat(all_data, ignore_index=True)
                trainer.train(final_df)
                log.info(f"✅ Training Complete. Dataset size: {len(final_df)} samples.")
            else:
                log.error("Failed to collect training data.")

if __name__ == "__main__":
    system = MetaModelSystem()
    try:
        asyncio.run(system.run())
    except KeyboardInterrupt:
        log.info("System stopped.")
