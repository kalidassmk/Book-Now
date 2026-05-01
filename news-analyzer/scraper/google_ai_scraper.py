import asyncio
import logging
import json
from playwright.async_api import async_playwright
from bs4 import BeautifulSoup

logger = logging.getLogger(__name__)

class GoogleAIScraper:
    def __init__(self):
        self.browser = None
        self.context = None

    async def __aenter__(self):
        self.playwright = await async_playwright().start()
        self.browser = await self.playwright.chromium.launch(headless=True)
        self.context = await self.browser.new_context(
            user_agent="Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"
        )
        return self

    async def __aexit__(self, exc_type, exc_val, exc_tb):
        if self.browser:
            await self.browser.close()
        if self.playwright:
            await self.playwright.stop()

    async def get_ai_sentiment(self, coin):
        """
        Search Google for coin sentiment and try to extract AI-generated summary.
        Matches the format requested by the user.
        """
        page = await self.context.new_page()
        # Using udm=50 and other parameters from the user's sample to trigger AI mode
        search_query = f"{coin} crypto coin sentiment summary"
        url = f"https://www.google.com/search?q={search_query.replace(' ', '+')}&udm=50"
        
        try:
            logger.info(f"Searching Google AI for {coin} via udm=50...")
            await page.goto(url, wait_until="networkidle")
            await asyncio.sleep(4) # Wait for AI generation
            
            content = await page.content()
            soup = BeautifulSoup(content, 'html.parser')
            
            from datetime import datetime
            
            ai_data = {
                "coin": coin,
                "timestamp": datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%SZ"),
                "sentiment": {
                    "polarity": 0.0,
                    "subjectivity": 0.5,
                    "label": "Neutral"
                },
                "sources": []
            }
            
            # Extract content from AI Overview
            ai_container = soup.find('div', {'role': 'region', 'aria-label': 'AI Overview'})
            if not ai_container:
                ai_container = soup.find('div', string=lambda t: t and "AI Overview" in t)
                if ai_container:
                    ai_container = ai_container.find_parent('div')

            summary_text = ""
            if ai_container:
                summary_text = ai_container.get_text(separator=' ', strip=True)
                
                # Extract sources from the AI block
                source_links = ai_container.find_all('a')
                for link in source_links:
                    source_name = link.get_text(strip=True)
                    if source_name and len(source_name) < 30:
                        ai_data["sources"].append(source_name)
            else:
                # Fallback to main snippet
                snippet = soup.select_one('.VwiC3b')
                if snippet:
                    summary_text = snippet.get_text(strip=True)
                ai_data["sources"] = ["Google Search Snippet"]

            # Heuristic sentiment analysis on the summary text
            if summary_text:
                text_lower = summary_text.lower()
                if any(w in text_lower for w in ['bullish', 'positive', 'surge', 'growth']):
                    ai_data["sentiment"]["label"] = "Bullish"
                    ai_data["sentiment"]["polarity"] = 0.5
                elif any(w in text_lower for w in ['bearish', 'negative', 'crash', 'decline']):
                    ai_data["sentiment"]["label"] = "Bearish"
                    ai_data["sentiment"]["polarity"] = -0.5
                
                # Deduplicate and clean sources
                ai_data["sources"] = sorted(list(set(ai_data["sources"])))[:5]
                if not ai_data["sources"]:
                    ai_data["sources"] = ["General News", "Social Media"]

            return ai_data

        except Exception as e:
            logger.error(f"Google AI search failed for {coin}: {e}")
            return None
        finally:
            await page.close()

async def main():
    async with GoogleAIScraper() as scraper:
        res = await scraper.get_ai_sentiment("Bitcoin")
        print(json.dumps(res, indent=2))

if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    asyncio.run(main())
