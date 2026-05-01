# Binance USDT Symbols Integration

This document explains how to fetch and use Binance USDT trading symbols in the news-analyzer project.

## Overview

The news-analyzer project now integrates with Binance to automatically fetch and store all active USDT trading pairs. This allows the sentiment analysis system to analyze news for all available cryptocurrencies on Binance.

## Features

- ✅ Fetches all active USDT trading pairs from Binance API
- ✅ Stores symbols and detailed information in Redis
- ✅ Automatic integration with existing news-analyzer pipeline
- ✅ Comprehensive symbol metadata (precision, trading limits, etc.)
- ✅ Backward compatibility with existing Redis key patterns

## Setup

### 1. Fetch Binance Symbols

Run the fetch script to populate Redis with Binance symbols:

```bash
cd /Users/bogoai/Book-Now/news-analyzer
source venv/bin/activate
python fetch_binance_symbols.py
```

This will:
- Fetch exchange information from Binance API
- Extract all active USDT trading pairs (currently ~431 symbols)
- Store symbols in Redis under multiple key patterns for compatibility

### 2. Verify Installation

Check that symbols were stored correctly:

```bash
# Check total count
python -c "from redis_client import RedisClient; rc = RedisClient(); symbols, _ = rc.get_binance_symbols(); print(f'Total symbols: {len(symbols)}')"

# Check specific symbol details
python -c "from redis_client import RedisClient; rc = RedisClient(); _, details = rc.get_binance_symbols(); print(details.get('BTCUSDT', 'Not found'))"
```

### 3. Run News Analysis

The news-analyzer will now automatically use all Binance USDT symbols:

```bash
# Run analysis for all symbols
python main.py run-once

# Start scheduled analysis
python main.py start 15  # Every 15 minutes
```

## Redis Data Structure

### Main Symbols List
```
Key: binance:usdt_symbols
Type: STRING (JSON Array)
Content: Array of symbol objects with full metadata
```

### Individual Symbol Details
```
Key: BINANCE:SYMBOL:{SYMBOL}  # e.g., BINANCE:SYMBOL:BTCUSDT
Key: BINANCE:{SYMBOL}         # e.g., BINANCE:BTCUSDT (compatibility)
Type: STRING (JSON Object)
Content: Detailed symbol information
```

### Symbol Object Structure
```json
{
  "symbol": "BTCUSDT",
  "base_asset": "BTC",
  "quote_asset": "USDT",
  "status": "TRADING",
  "base_asset_precision": 8,
  "quote_asset_precision": 8,
  "min_qty": "0.00001000",
  "max_qty": "9000.00000000",
  "step_size": "0.00001000"
}
```

## API Usage

### Get All Symbols
```python
from redis_client import RedisClient

rc = RedisClient()
symbols, details = rc.get_binance_symbols()

print(f"Total symbols: {len(symbols)}")
print(f"First 5: {symbols[:5]}")

# Get BTC details
btc_info = details.get('BTCUSDT')
if btc_info:
    print(f"BTC base asset: {btc_info['base_asset']}")
```

### Get USDT Symbols Only
```python
symbols = rc.get_usdt_symbols()  # Returns list of symbol strings
```

## Integration with News Analysis

The news-analyzer automatically:

1. **Fetches symbols** from Redis using `get_binance_symbols()`
2. **Extracts coin names** by removing 'USDT' suffix (BTCUSDT → BTC)
3. **Runs sentiment analysis** for each coin
4. **Stores results** with detailed analysis data

### Analysis Flow
```
Binance API → fetch_binance_symbols.py → Redis → main.py → News Analysis → Redis Results
```

# Generate Summary Reports

The news-analyzer now includes a comprehensive summary report generator that creates tabular reports for all Binance USDT coins in the requested format:

```
Project/Event | Key Information | Sentiment
```

## Generate Summary Report

Run the summary report generator:

```bash
cd /Users/bogoai/Book-Now/news-analyzer
source venv/bin/activate

# Generate report for all coins (may take a long time)
python generate_summary_report.py

# Generate report for major coins only
python generate_summary_report.py --major

# Generate report for specific coins
python generate_summary_report.py --coins BTC ETH ADA

# Force fresh analysis (slower but more current)
python generate_summary_report.py --major --refresh

# Save to file
python generate_summary_report.py --major --output crypto_report.txt

# Limit number of coins
python generate_summary_report.py --limit 50
```

## Report Format

The report generates output in the exact format you requested:

```
Project/Event	Key Information	Sentiment
--------------------------------------------------------------------------------
BTC (BTCUSDT)	Active trading pair on Binance; Min trade: 0.00001000; 5 articles analyzed; Analysis score: 0.2341	Bullish
ETH (ETHUSDT)	Active trading pair on Binance; Min trade: 0.00010000; 3 articles analyzed; Analysis score: -0.1234	Bearish
ADA (ADAUSDT)	Active trading pair on Binance; Min trade: 0.10000000	Major cryptocurrency	Neutral
```

## Sentiment Categories

- **Highly Bullish**: Strong positive sentiment (score > 0.3)
- **Bullish**: Positive sentiment (score > 0.0)
- **Neutral**: Balanced sentiment (score ≈ 0.0)
- **Bearish**: Negative sentiment (score < 0.0)
- **Highly Bearish**: Strong negative sentiment (score < -0.3)
- **Insufficient Data**: Less than 3 articles analyzed
- **No Data**: No analysis available

## Key Information Fields

Each report includes:
- Trading status on Binance
- Minimum trade quantity
- Number of articles analyzed (if available)
- Analysis score (if available)
- Market categorization (Major cryptocurrency, Established altcoin, etc.)

## Automation

Set up automated report generation:

```bash
# Daily report generation (add to crontab)
0 6 * * * cd /path/to/news-analyzer && source venv/bin/activate && python generate_summary_report.py --major --refresh --output daily_report_$(date +\%Y\%m\%d).txt
```

## Integration with Existing System

The summary report generator integrates seamlessly with:
- Binance symbol fetching (`fetch_binance_symbols.py`)
- News scraping and sentiment analysis pipeline
- Redis caching for performance
- All existing analysis components

## Troubleshooting

### No Symbols Found
If `get_binance_symbols()` returns empty:
1. Run `python fetch_binance_symbols.py` again
2. Check Redis connection: `redis-cli ping`
3. Verify Binance API is accessible

### Old Data
To refresh symbols (run periodically):
```bash
python fetch_binance_symbols.py
```

### Redis Connection Issues
Ensure Redis is running:
```bash
redis-server  # Start Redis if needed
```

## Performance Notes

- **Fetch Time**: ~2-3 seconds for 431 symbols
- **Storage**: ~50KB for all symbol data
- **Memory**: Minimal impact on Redis memory usage
- **API Rate Limits**: Single request, no rate limiting concerns

## Maintenance

### Regular Updates
Run the fetch script weekly or when new symbols are listed:
```bash
# Cron job example (weekly)
0 2 * * 1 cd /path/to/news-analyzer && source venv/bin/activate && python fetch_binance_symbols.py
```

### Monitoring
Check symbol count periodically:
```python
from redis_client import RedisClient
rc = RedisClient()
symbols, _ = rc.get_binance_symbols()
print(f"Current symbol count: {len(symbols)}")
```

## Files Modified/Created

- **Created**: `fetch_binance_symbols.py` - Main fetch script
- **Modified**: `redis_client.py` - Updated `get_binance_symbols()` method
- **Uses**: All existing news-analyzer components

## Next Steps

1. **Test Analysis**: Run `python main.py run-once` to test with real symbols
2. **Monitor Results**: Check analysis results in Redis/dashboard
3. **Schedule Updates**: Set up cron job for weekly symbol updates
4. **Extend Analysis**: Consider adding price data integration

---

## Support

For issues:
1. Check Redis connectivity
2. Verify Binance API accessibility
3. Run fetch script with verbose logging
4. Check news-analyzer logs for errors
