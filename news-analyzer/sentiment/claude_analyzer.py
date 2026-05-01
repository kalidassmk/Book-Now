import os
import json
import logging
from typing import Dict, Any

logger = logging.getLogger("ClaudeAnalyzer")

class ClaudeAnalyzer:
    """
    Uses Anthropic Claude 3.5 Sonnet to analyze crypto news with high precision.
    """
    def __init__(self, api_key: str = "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"):
        try:
            from anthropic import Anthropic
            self.api_key = api_key or os.getenv("ANTHROPIC_API_KEY")
            if self.api_key:
                self.client = Anthropic(api_key=self.api_key)
                self.active = True
            else:
                logger.warning("ANTHROPIC_API_KEY not found. Claude analysis will be skipped.")
                self.active = False
        except ImportError:
            logger.warning("anthropic library not installed. Skipping Claude analysis.")
            self.active = False

    async def analyze_sentiment(self, coin: str, context: str) -> Dict[str, Any]:
        """
        Analyze a coin's sentiment based on provided context using Claude.
        """
        if not self.active:
            return None

        prompt = f"""
        Analyze the following news context for the cryptocurrency '{coin}'.
        Provide a logical sentiment analysis and a trading signal.
        
        Context:
        {context[:4000]}
        
        Return STRICT JSON format:
        {{
          "coin": "{coin}",
          "sentiment_score": -1.0 to 1.0,
          "signal": "BUY/SELL/HOLD",
          "analysis": "A concise explanation of the market situation.",
          "risk_level": "Low/Medium/High"
        }}
        """

        try:
            logger.info(f"Performing Claude 3.5 Sonnet analysis for {coin}...")
            response = self.client.messages.create(
                model="claude-3-5-sonnet-20240620",
                max_tokens=1000,
                temperature=0,
                system="You are an expert crypto analyst who provides structured sentiment data in JSON.",
                messages=[
                    {"role": "user", "content": prompt}
                ]
            )
            
            text = response.content[0].text
            if "{" in text:
                text = "{" + text.split("{", 1)[1].rsplit("}", 1)[0] + "}"
                
            data = json.loads(text)
            logger.info(f"Successfully analyzed {coin} with Claude.")
            return data

        except Exception as e:
            logger.error(f"Claude analysis failed for {coin}: {e}")
            return None

if __name__ == "__main__":
    # Test script
    import asyncio
    logging.basicConfig(level=logging.INFO)
    analyzer = ClaudeAnalyzer()
    
    test_context = "Solana is showing massive growth in its DEX volume. The network is stable and attracting new projects."
    res = asyncio.run(analyzer.analyze_sentiment("SOL", test_context))
    print(json.dumps(res, indent=2))
