import pandas as pd
import numpy as np

class BTCFilter:
    """
    Analyzes BTC trend, momentum, and strength.
    """
    @staticmethod
    def analyze(btc_klines):
        if not btc_klines:
            return "neutral", 0.0

        df = pd.DataFrame(btc_klines, columns=['t', 'o', 'h', 'l', 'c', 'v', 'ct', 'qv', 'tr', 'tbv', 'tbq', 'i'])
        df['c'] = df['c'].astype(float)
        df['h'] = df['h'].astype(float)
        df['l'] = df['l'].astype(float)

        # 1. EMA Trend (9, 21)
        ema9 = df['c'].ewm(span=9, adjust=False).mean().iloc[-1]
        ema21 = df['c'].ewm(span=21, adjust=False).mean().iloc[-1]
        
        trend = "bullish" if ema9 > ema21 else "bearish"

        # 2. Momentum Strength (-1 to 1)
        # % change over last 20 candles normalized
        pct_change = (df['c'].iloc[-1] - df['c'].iloc[-21]) / df['c'].iloc[-21]
        
        # 3. Volatility (ATR-like)
        range_avg = (df['h'] - df['l']).rolling(window=14).mean().iloc[-1]
        curr_range = df['h'].iloc[-1] - df['l'].iloc[-1]
        vol_ratio = curr_range / range_avg if range_avg > 0 else 1.0

        # Composite Strength Score
        # Start with trend bias
        score = 0.4 if trend == "bullish" else -0.4
        
        # Add momentum influence
        score += np.clip(pct_change * 20, -0.4, 0.4) # scale 2% move to 0.4
        
        # Penalize if volatility is extreme (potential reversal/unstable)
        if vol_ratio > 2.5:
            score *= 0.5 

        return trend, round(score, 2)
