import os
import json
import logging
from typing import Dict, Any, List

logger = logging.getLogger("OpenAIAnalyzer")

class OpenAIAnalyzer:
    """
    Uses OpenAI GPT-4o to analyze crypto news and provide high-accuracy sentiment and decisions.
    """
    def __init__(self, api_key: str = "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"):
        try:
            from openai import OpenAI
            self.api_key = api_key or os.getenv("OPENAI_API_KEY")
            if self.api_key:
                self.client = OpenAI(api_key=self.api_key)
                self.active = True
            else:
                logger.warning("OPENAI_API_KEY not found. OpenAI analysis will be skipped.")
                self.active = False
        except ImportError:
            logger.warning("openai library not installed. Skipping OpenAI analysis.")
            self.active = False

    async def analyze_with_context(self, coin: str, context: str) -> Dict[str, Any]:
        """
        Analyze a coin based on the provided news context using GPT-4o.
        """
        if not self.active:
            return None

        prompt = f"""
        Analyze the following news context for the cryptocurrency '{coin}'.
        Provide a professional sentiment analysis and a trading decision.
        
        Context:
        {context[:4000]}
        
        Return STRICT JSON format:
        {{
          "coin": "{coin}",
          "sentiment_label": "Bullish/Bearish/Neutral",
          "confidence_score": 0.0 to 1.0,
          "key_takeaways": ["point 1", "point 2"],
          "trading_decision": "BUY/SELL/HOLD",
          "reasoning": "A concise explanation of the decision."
        }}
        """

        try:
            logger.info(f"Performing OpenAI GPT-4o analysis for {coin}...")
            response = self.client.chat.completions.create(
                model="gpt-4o",
                messages=[
                    {"role": "system", "content": "You are a professional crypto sentiment analyst."},
                    {"role": "user", "content": prompt}
                ],
                response_format={ "type": "json_object" }
            )
            
            data = json.loads(response.choices[0].message.content)
            logger.info(f"Successfully analyzed {coin} with OpenAI.")
            return data

        except Exception as e:
            logger.error(f"OpenAI analysis failed for {coin}: {e}")
            return None

if __name__ == "__main__":
    # Test script
    import asyncio
    logging.basicConfig(level=logging.INFO)
    analyzer = OpenAIAnalyzer()
    
    test_context = "Bitcoin is seeing massive institutional adoption after the ETF approval. Prices are surging towards new ATH."
    res = asyncio.run(analyzer.analyze_with_context("BTC", test_context))
    print(json.dumps(res, indent=2))
