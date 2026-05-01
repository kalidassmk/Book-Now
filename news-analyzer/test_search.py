import asyncio
import logging
import json
from google import genai
from google.genai import types

# Logging setup
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger("AITester")

class AITester:
    def __init__(self, api_key="AIzaSyCGaZdxqEHKJIlV11aaGg9qO5Vj1nMJYxg"):
        self.api_key = api_key
        # Initialize the NEW Client
        self.client = genai.Client(api_key=self.api_key)

    async def test_gemini_search(self, coin: str):
        logger.info(f"--- Testing AI Search (New SDK) for {coin} ---")
        try:
            # We use gemini-2.0-flash as it is the most modern
            target_model = "gemini-2.0-flash"
            
            prompt = f"Find the latest news and price sentiment for {coin} crypto from the last 24 hours. Provide a summary and 3 news titles."

            # New SDK syntax for Search Tool
            response = self.client.models.generate_content(
                model=target_model,
                contents=prompt,
                config=types.GenerateContentConfig(
                    tools=[types.Tool(google_search=types.GoogleSearch())]
                )
            )
            
            if response.text:
                logger.info(f"✅ SUCCESS! AI Search Result for {coin}:")
                print("\n" + "="*50)
                print(response.text)
                print("="*50 + "\n")
            else:
                logger.warning(f"⚠️ Empty response received for {coin}.")
                
        except Exception as e:
            logger.error(f"❌ Gemini Search failed: {e}")
            if "429" in str(e):
                logger.warning("Tip: You have exceeded your daily quota. Please wait for a reset.")

async def main():
    # Only testing BTC and ETH
    test_coins = ["BTC", "ETH"]
    tester = AITester()
    
    logger.info("🚀 Starting Phase 1: AI Engine Search Testing (New SDK)")
    for coin in test_coins:
        await tester.test_gemini_search(coin)
        await asyncio.sleep(1)

if __name__ == "__main__":
    asyncio.run(main())
