import aiohttp
import logging
import os
from typing import Optional

logger = logging.getLogger("JinaReader")

class JinaReader:
    """
    Integrates r.jina.ai to fetch clean, LLM-friendly markdown content from any URL.
    """
    def __init__(self, api_key: str = "jina_fbb646d54eb0451f87ad8509dc9b71cb-DgkV3hIr81ou9o_xSBX42-Zll_e"):
        self.api_key = api_key or os.getenv("JINA_API_KEY")
        self.base_url = "https://r.jina.ai/"

    async def fetch_content(self, url: str) -> Optional[str]:
        """
        Fetch the clean markdown content of a URL using Jina Reader.
        """
        try:
            # Jina Reader works by prefixing the URL: https://r.jina.ai/https://example.com
            reader_url = f"{self.base_url}{url}"
            headers = {
                "Authorization": f"Bearer {self.api_key}",
                "X-Return-Format": "markdown"
            }
            
            async with aiohttp.ClientSession() as session:
                async with session.get(reader_url, headers=headers) as response:
                    if response.status == 200:
                        content = await response.text()
                        logger.info(f"Successfully fetched content for {url} via Jina")
                        return content
                    else:
                        logger.error(f"Jina failed for {url}: {response.status}")
                        return None
        except Exception as e:
            logger.error(f"Error calling Jina Reader for {url}: {e}")
            return None

if __name__ == "__main__":
    # Test Script
    import asyncio
    import sys
    
    logging.basicConfig(level=logging.INFO)
    reader = JinaReader()
    
    test_url = "https://www.coindesk.com/market-analysis/2024/04/29/bitcoin-price-analysis-is-the-bottom-in/"
    if len(sys.argv) > 1:
        test_url = sys.argv[1]
        
    print(f"--- Fetching content for: {test_url} ---")
    content = asyncio.run(reader.fetch_content(test_url))
    if content:
        print("\n[CONTENT START]\n")
        print(content[:1000] + "...") # Show first 1000 chars
        print("\n[CONTENT END]\n")
    else:
        print("Failed to fetch content.")
