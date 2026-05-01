# Cryptocurrency Sentiment Analysis Pipeline

A complete system that processes any cryptocurrency input and stores it in Redis, combining three layers: Market Data (via CoinGecko), News Scraping, and Sentiment Analysis (via VADER).

## Overview

The **Crypto Sentiment Pipeline** is a sophisticated analysis tool that:

1. **Fetches Market Data** - Gets real-time prices and market information from CoinGecko API
2. **Scrapes News** - Collects cryptocurrency news from multiple sources
3. **Analyzes Sentiment** - Uses VADER sentiment analysis to determine bullish/bearish sentiment
4. **Stores Results** - Maintains comprehensive data in Redis for quick retrieval

## Prerequisites

### System Requirements
- Python 3.7+
- Redis server (local or Docker)
- Internet connection for API calls

### Redis Setup

#### Option 1: Local Redis Installation
```bash
# macOS
brew install redis
brew services start redis

# Linux
sudo apt-get install redis-server
sudo systemctl start redis-server

# Verify Redis is running
redis-cli ping  # Should return: PONG
```

#### Option 2: Docker Redis
```bash
docker run -d -p 6379:6379 --name redis redis:latest

# Verify
docker exec redis redis-cli ping  # Should return: PONG
```

### Python Dependencies

Install the required packages:

```bash
cd /Users/bogoai/Book-Now/news-analyzer
pip install -r requirements.txt
```

Key packages:
- `redis==5.0.1` - Redis client for Python
- `requests==2.31.0` - HTTP requests
- `beautifulsoup4==4.12.2` - Web scraping
- `vaderSentiment==3.3.2` - Sentiment analysis
- `apscheduler==3.10.4` - Task scheduling

## Project Structure

```
news-analyzer/
├── crypto_sentiment_pipeline.py    # Main pipeline implementation
├── test_crypto_sentiment.py         # Comprehensive test suite
├── requirements.txt                 # Python dependencies
├── redis_client.py                  # Existing Redis utilities
├── sentiment/
│   └── sentiment_analyzer.py       # Existing sentiment analyzer
└── scraper/
    └── news_scraper.py            # Existing news scraper
```

## Implementation Details

### Architecture

The pipeline uses a three-layer architecture:

```
┌─────────────────────────────────────────────────────────────┐
│                 CryptoSentimentPipeline                      │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  Layer 1: Market Data (CoinGecko API)                        │
│  ├─ Price (USD)                                              │
│  ├─ Market Cap                                               │
│  ├─ 24h Volume                                               │
│  └─ 24h Price Change                                         │
│                                                               │
│  Layer 2: News Scraping                                      │
│  ├─ Google News                                              │
│  └─ CryptoNews                                               │
│                                                               │
│  Layer 3: Sentiment Analysis (VADER)                         │
│  ├─ VADER Polarity Scores                                    │
│  └─ Sentiment Classification                                 │
│       • Bullish (score >= 0.05)                              │
│       • Neutral (score: -0.05 to 0.05)                       │
│       • Bearish (score <= -0.05)                             │
│                                                               │
└────────────────→ Redis Storage (Structured Data) ────────────┘
```

### Redis Data Structure

#### Main Entry (Hash)
```
Key: crypto_sentiment:{coin_id}
Type: HASH
Fields:
  - name: "Bitcoin"
  - id: "bitcoin"
  - price_usd: "45000.50"
  - market_cap: "900000000000"
  - volume_24h: "25000000000"
  - price_change_24h: "2.45"
  - sentiment_score: 0.0848
  - sentiment_label: "Bullish"
  - num_articles_analyzed: 10
  - timestamp: "2026-04-29T14:30:45.123456"
  - last_update: "Analysis for Bitcoin as of 2026-04-29 14:30:45"
```

#### Headlines (JSON)
```
Key: crypto_sentiment:{coin_id}:headlines
Type: STRING (JSON Array)
Value: [
  {
    "headline": "Bitcoin Surges to New All-Time High",
    "source": "CoinNews",
    "sentiment_score": 0.84,
    "sentiment_label": "Bullish",
    "vader_scores": { "neg": 0.0, "neu": 0.45, "pos": 0.55, "compound": 0.84 }
  },
  ...
]
```

#### Recent Coins (Sorted Set)
```
Key: crypto_sentiment:recent
Type: ZSET
Members: {coin_id: timestamp}
Use: Quick lookup of recently analyzed coins
```

## Usage Examples

### Example 1: Basic Usage

```python
from crypto_sentiment_pipeline import CryptoSentimentPipeline

# Initialize pipeline
pipeline = CryptoSentimentPipeline()

# Process a single cryptocurrency
result = pipeline.process_coin("Bitcoin", "bitcoin")

if result["success"]:
    data = result["data"]
    print(f"Price: ${data['price_usd']}")
    print(f"Sentiment: {data['sentiment_label']}")
    print(f"Score: {data['sentiment_score']}")
    print(f"Headlines: {data['num_articles_analyzed']}")
```

### Example 2: Process Multiple Coins

```python
from crypto_sentiment_pipeline import CryptoSentimentPipeline
import time

pipeline = CryptoSentimentPipeline()

coins = [
    ("Bitcoin", "bitcoin"),
    ("Ethereum", "ethereum"),
    ("Solana", "solana"),
    ("Cardano", "cardano"),
]

for name, coin_id in coins:
    result = pipeline.process_coin(name, coin_id)
    if result["success"]:
        print(f"{name}: {result['data']['sentiment_label']}")
    time.sleep(2)  # Rate limiting
```

### Example 3: Retrieve Stored Data

```python
from crypto_sentiment_pipeline import CryptoSentimentPipeline

pipeline = CryptoSentimentPipeline()

# Get analysis for a specific coin
bitcoin_data = pipeline.get_coin_analysis("bitcoin")

if bitcoin_data:
    # Access main data
    main = bitcoin_data["data"]
    print(f"Bitcoin: ${main['price_usd']}")
    
    # Access headlines
    headlines = bitcoin_data["headlines"]
    for headline in headlines[:3]:
        print(f"  - {headline['headline']} ({headline['sentiment_label']})")

# Get all coins analysis
all_data = pipeline.get_all_coins_analysis()
print(f"Tracked coins: {len(all_data)}")
```

### Example 4: Direct Sentiment Analysis

```python
from crypto_sentiment_pipeline import CryptoSentimentPipeline

pipeline = CryptoSentimentPipeline()

headlines = [
    "Bitcoin Surges to New All-Time High",
    "Crypto Market Crashes Due to Regulatory Fears"
]

for headline in headlines:
    scores = pipeline.analyzer.polarity_scores(headline)
    print(f"Headline: {headline}")
    print(f"Compound Score: {scores['compound']}")
```

## Running the Tests

The project includes a comprehensive test suite:

```bash
# Run all tests
python test_crypto_sentiment.py

# Run and clean up test data
python test_crypto_sentiment.py cleanup
```

### Test Coverage

1. **Basic Operations** - Single coin processing and retrieval
2. **Multiple Coins** - Processing cryptocurrency portfolio
3. **Data Retrieval** - Fetching stored analysis
4. **Direct Sentiment Analysis** - VADER analysis samples
5. **Redis Operations** - Direct database interactions

## API Reference

### CryptoSentimentPipeline Class

#### Constructor
```python
pipeline = CryptoSentimentPipeline(
    redis_host='localhost',      # Redis server hostname
    redis_port=6379,              # Redis server port
    redis_db=0                    # Redis database number
)
```

#### Methods

##### process_coin(coin_name: str, coin_id: str) -> Dict
Processes a cryptocurrency through the complete pipeline.

**Parameters:**
- `coin_name` (str): Display name (e.g., 'Bitcoin')
- `coin_id` (str): CoinGecko ID (e.g., 'bitcoin')

**Returns:** Dictionary with success status and data

**Example:**
```python
result = pipeline.process_coin("Bitcoin", "bitcoin")
```

---

##### fetch_price(coin_id: str) -> Dict
Fetches current price and market data from CoinGecko.

**Parameters:**
- `coin_id` (str): CoinGecko ID

**Returns:** Dictionary with price, market cap, volume, and change

**Example:**
```python
price_data = pipeline.fetch_price("bitcoin")
print(price_data['price_usd'])  # "45000.50"
```

---

##### analyze_news_sentiment(coin_name: str) -> Tuple
Scrapes news and analyzes sentiment using VADER.

**Parameters:**
- `coin_name` (str): Cryptocurrency name

**Returns:** Tuple of (score, label, headlines)

**Example:**
```python
score, label, headlines = pipeline.analyze_news_sentiment("Bitcoin")
print(f"Sentiment: {label} ({score})")
```

---

##### get_coin_analysis(coin_id: str) -> Optional[Dict]
Retrieves stored analysis from Redis.

**Parameters:**
- `coin_id` (str): CoinGecko ID

**Returns:** Dictionary with data and headlines, or None

**Example:**
```python
data = pipeline.get_coin_analysis("bitcoin")
```

---

##### get_all_coins_analysis() -> Dict
Retrieves analysis for all tracked coins.

**Returns:** Dictionary mapping coin_id to analysis

**Example:**
```python
all_data = pipeline.get_all_coins_analysis()
```

---

##### clear_coin_data(coin_id: str) -> bool
Clears stored data for a specific coin.

**Parameters:**
- `coin_id` (str): CoinGecko ID

**Returns:** True if successful

**Example:**
```python
pipeline.clear_coin_data("bitcoin")
```

---

## CoinGecko API Reference

The pipeline uses the CoinGecko free API:

```
Base URL: https://api.coingecko.com/api/v3
Endpoint: /simple/price
Parameters:
  - ids: Comma-separated list of coin IDs
  - vs_currencies: Target currency (e.g., usd)
  - include_market_cap: Include market cap data
  - include_24hr_vol: Include 24h volume
  - include_24hr_change: Include 24h price change
```

**Rate Limits:**
- Free tier: 10-50 calls/minute
- No API key required

**Supported Coins:**
- bitcoin, ethereum, solana, cardano, ripple, polkadot, dogecoin, litecoin, and thousands more

See: https://api.coingecko.com/api/v3/coins/list for complete list

## VADER Sentiment Analysis

VADER (Valence Aware Dictionary and sEntiment Reasoner) is specifically optimized for sentiment analysis on social media and short texts.

### Sentiment Thresholds

The pipeline uses standard VADER compound scores:

```
Compound Score    Label      Classification
>= 0.05          Bullish    Positive sentiment
-0.05 to 0.05    Neutral    Mixed or neutral
<= -0.05         Bearish    Negative sentiment
```

### VADER Components

Each analysis returns:
- **positive** (pos): Proportion of positive words
- **negative** (neg): Proportion of negative words
- **neutral** (neu): Proportion of neutral words
- **compound**: Aggregate score (-1 to 1)

## News Sources

The pipeline scrapes from:

1. **Google News**
   - URL: https://news.google.com/search
   - Limits: 5 articles per request
   - Advantage: Aggregates multiple sources

2. **CryptoNews**
   - URL: https://cryptonews.com
   - Limits: 5 articles per request
   - Advantage: Crypto-focused

## Performance Considerations

### Rate Limiting
- The pipeline automatically adds 2-second delays between coin processing
- CoinGecko API: 10-50 calls/minute (free tier)
- Adjust delays based on your needs

```python
import time
time.sleep(2)  # Delay between coins
```

### Redis Optimization
- Use HSET for structured data (faster than JSON for field access)
- Use JSON for arrays (headlines)
- Implement TTL for automatic cleanup

```python
# TTL not currently set - consider adding:
self.r.expire(redis_key, 3600)  # 1 hour expiry
```

### Scaling Recommendations

1. **Use Redis Persistence**
   ```bash
   # Enable RDB snapshots in redis.conf
   save 900 1      # Save every 15 minutes if at least 1 key changed
   ```

2. **Monitor Storage**
   ```bash
   redis-cli INFO memory
   ```

3. **Batch Processing**
   - Process coins in batches to avoid rate limiting
   - Use APScheduler for periodic analysis

## Troubleshooting

### Redis Connection Error
```
ConnectionError: Error 111 connecting to localhost:6379
```
**Solution:** Ensure Redis is running:
```bash
redis-cli ping  # Should return PONG
```

### CoinGecko API Error
```
requests.exceptions.ConnectionError: Connection aborted
```
**Solution:** 
- Check internet connection
- Verify CoinGecko is accessible
- Wait a moment before retrying (rate limiting)

### News Scraping Not Working
```
No articles found for Bitcoin
```
**Solution:**
- Google News and CryptoNews may change HTML structure
- Update BeautifulSoup selectors as needed
- Check using:
  ```bash
  curl "https://news.google.com/search?q=Bitcoin+crypto+news" | grep -i article
  ```

### Low Sentiment Scores
If all coins return neutral sentiment:
- Check retrieved headlines (may be empty)
- Verify news scraping is working
- Consider adjusting sentiment thresholds

## Integration with Existing Project

### With Existing Redis Client
```python
from redis_client import RedisClient
from crypto_sentiment_pipeline import CryptoSentimentPipeline

# Both use the same Redis instance
redis_client = RedisClient()
pipeline = CryptoSentimentPipeline()

# Data stored by pipeline is accessible via redis_client
data = redis_client.get_cached_analysis("bitcoin")  # Won't work directly
# Instead, use:
data = pipeline.r.hgetall("crypto_sentiment:bitcoin")
```

### With Existing Sentiment Analyzer
```python
from sentiment.sentiment_analyzer import SentimentAnalyzer

# Existing analyzer
sa = SentimentAnalyzer()

# VADER analyzer (more optimized for social media)
from crypto_sentiment_pipeline import CryptoSentimentPipeline
pipeline = CryptoSentimentPipeline()
vader_scores = pipeline.analyzer.polarity_scores(text)
```

### Scheduled Analysis
```python
from apscheduler.schedulers.background import BackgroundScheduler
from crypto_sentiment_pipeline import CryptoSentimentPipeline

def analyze_coins():
    pipeline = CryptoSentimentPipeline()
    coins = [("Bitcoin", "bitcoin"), ("Ethereum", "ethereum")]
    for name, coin_id in coins:
        pipeline.process_coin(name, coin_id)

scheduler = BackgroundScheduler()
scheduler.add_job(analyze_coins, 'interval', minutes=15)
scheduler.start()
```

## Production Deployment

### Environment Variables
```bash
export REDIS_HOST=localhost
export REDIS_PORT=6379
export REDIS_DB=0
```

### Monitoring
```python
# Check Redis health
pipeline = CryptoSentimentPipeline()
info = pipeline.r.info()
print(f"Connected clients: {info['connected_clients']}")
print(f"Used memory: {info['used_memory_human']}")
```

### Error Handling
```python
try:
    result = pipeline.process_coin("Bitcoin", "bitcoin")
    if not result["success"]:
        print(f"Error: {result['error']}")
except Exception as e:
    print(f"Pipeline error: {e}")
```

## Extending the Pipeline

### Add Custom News Source
```python
def scrape_custom_source(self, coin_name: str) -> List[Dict]:
    """Add your custom news source here."""
    articles = []
    # Implement scraping logic
    return articles

# Update analyze_news_sentiment to use it
all_articles.extend(self.scrape_custom_source(coin_name))
```

### Add Additional Market Data
```python
def fetch_additional_data(self, coin_id: str) -> Dict:
    """Fetch more market data."""
    # Call additional APIs
    return {
        "exchanges": [...],
        "ath": "...",
        "atl": "..."
    }
```

### Custom Sentiment Model
```python
def analyze_sentiment_custom(self, text: str) -> float:
    """Use custom ML model instead of VADER."""
    # Implement custom sentiment analysis
    return score
```

## References

- **CoinGecko API**: https://www.coingecko.com/api/documentations/v3
- **VADER Sentiment**: https://github.com/cjhutto/vaderSentiment
- **Redis Documentation**: https://redis.io/documentation
- **BeautifulSoup**: https://www.crummy.com/software/BeautifulSoup/

## Support & Contribution

For issues or improvements:
1. Check existing logs in `news_analyzer.log`
2. Review test results with `python test_crypto_sentiment.py`
3. Verify Redis connectivity
4. Check news source availability

## License

This module is part of the Book-Now project and follows the project's license terms.

