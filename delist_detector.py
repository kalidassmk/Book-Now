import requests
import json
import re
import redis
import time
import sys

# 🔹 Redis configuration
REDIS_HOST = 'localhost'
REDIS_PORT = 6379
REDIS_DB = 0
REDIS_KEY_PREFIX = "BINANCE:DELIST:"

# 🔹 Binance CMS API Endpoints
LIST_API_URL = "https://www.binance.com/bapi/composite/v1/public/cms/article/list/query?type=1&catalogId=161&pageNo=1&pageSize=20"
ARTICLE_API_URL = "https://www.binance.com/bapi/composite/v1/public/cms/article/detail/query?articleCode="

HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
}

def get_redis_client():
    try:
        return redis.Redis(host=REDIS_HOST, port=REDIS_PORT, db=REDIS_DB, decode_responses=True)
    except Exception as e:
        print(f"❌ Failed to connect to Redis: {e}")
        sys.exit(1)

def fetch_delisting_announcements():
    print("🔍 Fetching latest Binance announcements...")
    try:
        response = requests.get(LIST_API_URL, headers=HEADERS)
        response.raise_for_status()
        data = response.json()
        articles = data.get('data', {}).get('catalogs', [{}])[0].get('articles', [])
        
        delist_articles = []
        for article in articles:
            title = article.get('title', '')
            if "Notice of Removal of Spot Trading Pairs" in title:
                delist_articles.append({"title": title, "code": article.get('code')})
        return delist_articles
    except Exception as e:
        print(f"❌ Error fetching announcements: {e}")
        return []

def process_article(r, code, depth=0, processed_codes=None):
    if depth > 1: return
    if processed_codes is None: processed_codes = set()
    if code in processed_codes: return
    processed_codes.add(code)

    try:
        url = f"{ARTICLE_API_URL}{code}"
        response = requests.get(url, headers=HEADERS)
        response.raise_for_status()
        data = response.json()
        
        body = data.get('data', {}).get('body', '')
        
        # 1. Extract symbols
        pairs = re.findall(r'\b[A-Z0-9]+(?:/USDT|USDT)\b', body)
        total_stored = 0
        for p in set(pairs):
            clean = p.replace("/", "")
            if clean.endswith("USDT"):
                r.set(f"{REDIS_KEY_PREFIX}{clean}", "true")
                print(f"Stored: {clean}")
                total_stored += 1
        
        # 2. Look for nested links
        # Find 32-character hex codes (standard Binance article codes)
        nested_codes = re.findall(r'[a-f0-9]{32}', body)
        for n_code in set(nested_codes):
            # Check if the surrounding text mentions delisting
            if "Removal" in body or "Spot Trading Pairs" in body:
                process_article(r, n_code, depth + 1, processed_codes)
                
    except Exception as e:
        print(f"❌ Error processing article {code}: {e}")

def main():
    r = get_redis_client()
    announcements = fetch_delisting_announcements()
    
    if not announcements:
        print("✅ No new delisting announcements found.")
        return

    for ann in announcements:
        print(f"\n📄 Checking: {ann['title']}")
        process_article(r, ann['code'])

    print(f"\n🚀 Done! Delisting scan complete.")

if __name__ == "__main__":
    main()
