import os
import json
import logging
import google.generativeai as genai
from typing import Dict, Any, List

logger = logging.getLogger(__name__)

class GeminiAnalyzer:
    def __init__(self, api_key: str = None):
        self.api_key = api_key or os.getenv("GEMINI_API_KEY")
        if self.api_key:
            genai.configure(api_key=self.api_key)
            # Note: google_search_retrieval tool is available in specific models/regions
            try:
                self.model = genai.GenerativeModel(
                    model_name="gemini-1.5-flash",
                    tools=[{"google_search_retrieval": {}}]
                )
                self.has_model = True
            except Exception as e:
                logger.error(f"Failed to initialize Gemini model with search: {e}")
                self.has_model = False
        else:
            logger.warning("GEMINI_API_KEY not found. Gemini analysis will be skipped.")
            self.has_model = False

    async def analyze_coin_deep(self, coin: str) -> Dict[str, Any]:
        """
        Perform a deep search and sentiment analysis for a coin using Gemini with Search.
        """
        if not self.has_model:
            return None

        prompt = f"""
        Search latest news and market sentiment for '{coin} crypto coin'.
        Focus on real-world events, listing updates, or significant community shifts.
        
        Return STRICT JSON in this exact format:
        {{
          "coin": "{coin}",
          "articles": [
            {{
              "title": "Short title of the news",
              "summary": "One sentence summary",
              "sentiment_score": 0.0 to 1.0 (positive) or -1.0 to 0.0 (negative),
              "sentiment_label": "positive/negative/neutral"
            }}
          ],
          "metrics": {{
            "avg_sentiment": 0.0,
            "market_sentiment": "bullish/bearish/neutral",
            "confidence": 0.0 to 1.0
          }},
          "ai_overview": "A 2-sentence summary of the overall situation."
        }}
        """

        try:
            logger.info(f"Performing Deep Gemini Analysis for {coin}...")
            # We use a loop for potential retries or formatting fixes
            response = self.model.generate_content(prompt)
            
            # Extract JSON from response text (Gemini sometimes wraps it in markdown blocks)
            text = response.text
            if "```json" in text:
                text = text.split("```json")[1].split("```")[0].strip()
            elif "```" in text:
                text = text.split("```")[1].split("```")[0].strip()
            
            data = json.loads(text)
            logger.info(f"Successfully analyzed {coin} with Gemini.")
            return data

        except Exception as e:
            logger.error(f"Gemini analysis failed for {coin}: {e}")
            return None

if __name__ == "__main__":
    # Test script
    logging.basicConfig(level=logging.INFO)
    test_key = os.getenv("GEMINI_API_KEY")
    if test_key:
        analyzer = GeminiAnalyzer(test_key)
        import asyncio
        res = asyncio.run(analyzer.analyze_coin_deep("SHIP"))
        print(json.dumps(res, indent=2))
