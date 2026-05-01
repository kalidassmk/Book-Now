# Crypto News Analyzer

A production-ready Python system that analyzes cryptocurrency news from multiple trusted sources and generates BUY/HOLD/SELL signals using advanced NLP techniques. ([EMAIL_ADDRESS]  [GCP_API_KEY] AIzaSyCGaZdxqEHKJIlV11aaGg9qO5Vj1nMJYxg

## Features

- **Multi-Source News Aggregation**: Scrapes news from CoinDesk, Cointelegraph, The Block, Decrypt, Reddit, and CoinMarketCap
- **Advanced Sentiment Analysis**: Uses TextBlob and FinBERT for comprehensive sentiment analysis
- **Keyword Signal Detection**: Identifies bullish/bearish keywords for enhanced decision making
- **Source Credibility Scoring**: Weights signals based on news source reputation
- **Real-time Redis Storage**: Stores analysis results for fast retrieval
- **Automated Scheduling**: Runs analysis cycles every 5-15 minutes
- **Risk Controls**: Minimum source requirements and duplicate detection

## Architecture

```
news-analyzer/
├── main.py                 # Main orchestrator
├── redis_client.py         # Redis operations
├── scraper/
│   ├── news_scraper.py     # Multi-source news scraping
│   └── __init__.py
├── parsers/
│   ├── article_parser.py   # Article parsing & coin detection
│   └── __init__.py
├── sentiment/
│   ├── sentiment_analyzer.py # NLP sentiment analysis
│   └── __init__.py
├── decision_engine.py      # Trading signal generation
├── scheduler.py           # Automated execution
├── requirements.txt       # Python dependencies
└── test_basic.py         # Basic functionality tests
```

## Installation

1. **Clone and setup**:
```bash
cd /Users/bogoai/Book-Now/
mkdir news-analyzer
cd news-analyzer
```

2. **Create virtual environment**:
```bash
python3 -m venv venv
source venv/bin/activate
```

3. **Install dependencies**:
```bash
pip install -r requirements.txt
```

4. **Install Playwright browsers**:
```bash
playwright install
```

## Configuration

### Redis Setup
The system expects Redis to be running with:
- **Key**: `BINANCE:SYMBOL:<SYMBOL>` - JSON data for each Binance USDT trading pair (populated by Java backend)
- **Host**: localhost:6379 (default)

### Environment Variables (Optional)
```bash
export REDIS_HOST=localhost
export REDIS_PORT=6379
export REDIS_DB=0
```

## Usage

### Run Entire System (Complete Single Run)
```bash
# This performs a complete analysis sequence:
# 0. Clears all existing analysis data and cache from Redis (for a fresh start).
# 1. Fetches latest Binance USDT symbols and stores them in Redis.
# 2. Scrapes and analyzes news for fast-moving coins.
# 3. Generates a summary report for major coins.
# 4. Automatically shuts down and exits cleanly.
python run_all.py
```

### Run Entire System (Scheduler + API)
```bash
# This starts the background news analysis scheduler (every 15 min)
# and the FastAPI server (on port 8000) simultaneously.
python run_all.py start 15
```

### Run Once
```bash
python main.py run-once
```

### Start Scheduler Only (runs every 15 minutes)
```bash
python main.py start 15
```

### Start API Server Only
```bash
python api.py
```

### Check Status
```bash
python main.py status
```

### Stop System
```bash
# Press Ctrl+C when running with 'start' or 'run_all.py'
```

## Scoring Algorithm

Final Score = (Sentiment × 0.5) + (Source Weight × 0.3) + (Keyword Signal × 0.2)

### Decision Logic
- **BUY**: score > 0.25
- **SELL**: score < -0.25
- **HOLD**: otherwise

### Source Weights
- CoinDesk: 1.0
- Cointelegraph: 0.9
- The Block: 1.0
- Decrypt: 0.8
- Reddit: 0.5
- CoinMarketCap: 0.6

## Redis Storage Format

```json
{
  "coin": "BTC",
  "score": 0.42,
  "sentiment": "positive",
  "decision": "BUY",
  "sources": 3,
  "timestamp": 1710000000,
  "details": {
    "avg_sentiment": 0.35,
    "avg_source_weight": 0.95,
    "avg_keyword_signal": 0.2,
    "articles_analyzed": 5
  }
}
```

## Risk Controls

- **Duplicate Prevention**: Articles processed only once (24h cache)
- **Minimum Sources**: Requires multiple sources for reliable signals
- **Content Validation**: Filters low-quality or irrelevant content
- **Rate Limiting**: Respects source rate limits with random delays
- **Error Handling**: Graceful failure handling for network issues

## Testing

Run basic functionality tests:
```bash
python test_basic.py
```

## Future Enhancements

- **ML Model Integration**: Custom trained models for prediction
- **Real-time Alerts**: Telegram/Slack notifications
- **Portfolio Integration**: Combine with trading bot
- **Backtesting Engine**: Historical performance analysis
- **Multi-language Support**: Expand beyond English news

## Dependencies

- **aiohttp**: Async HTTP requests
- **newspaper3k**: Article parsing
- **textblob**: Basic sentiment analysis
- **transformers**: FinBERT model
- **playwright**: Dynamic web scraping
- **redis**: Data storage
- **apscheduler**: Task scheduling
- **beautifulsoup4**: HTML parsing

## Performance Optimization

- **Async Operations**: Concurrent scraping and processing
- **Article Caching**: 1-hour content cache, 24-hour processing cache
- **Batch Processing**: Efficient bulk analysis
- **Memory Management**: Limited article content size
- **Connection Pooling**: Reused HTTP connections

## Monitoring

- **Console Logging**: Real-time operation logs
- **File Logging**: Persistent `news_analyzer.log`
- **Status Checks**: Scheduler and Redis connection monitoring
- **Error Tracking**: Comprehensive error handling and reporting

## Security Considerations

- **Rate Limiting**: Prevents source abuse
- **User Agent Rotation**: Avoids blocking
- ** robots.txt Respect**: Follows web scraping best practices
- **Data Sanitization**: Cleans and validates all inputs
- **Error Isolation**: Failures don't crash the entire system

## Troubleshooting

### Common Issues

1. **Redis Connection Failed**
   - Ensure Redis is running: `redis-server`
   - Check connection: `redis-cli ping`

2. **No USDT Symbols**
   - Populate Redis: `redis-cli SADD binance:usdt_symbols BTCUSDT ETHUSDT ...`

3. **Playwright Browser Issues**
   - Reinstall: `playwright install`
   - Check system dependencies

4. **Memory Issues**
   - Reduce article limits in configuration
   - Monitor system resources

### Logs Location
- **Console**: Real-time output
- **File**: `news_analyzer.log`

## Contributing

1. Follow existing code structure
2. Add comprehensive error handling
3. Include docstrings for all functions
4. Test new features thoroughly
5. Update documentation

## License

This project is part of a proprietary trading system. See project license for details.
