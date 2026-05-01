# Analysis Debug Dashboard - Quick Start Guide

## 🚀 30-Second Quick Start

```bash
# 1. Run the setup script (one time only)
bash /Users/bogoai/Book-Now/setup_analysis_dashboard.sh

# 2. Make sure Redis is running
redis-server

# 3. In three separate terminals:

# Terminal 1: Run Analysis
cd /Users/bogoai/Book-Now/news-analyzer
source venv/bin/activate
python main.py run-once

# Terminal 2: Start API
cd /Users/bogoai/Book-Now/news-analyzer
source venv/bin/activate
python -m uvicorn api:app --reload

# Terminal 3: Start Dashboard
cd /Users/bogoai/Book-Now/dashboard
npm start

# 4. Open browser
# → http://localhost:3000/analysis-dashboard
```

## 🏗️ System Architecture Overview

```
┌─────────────────────────────────────────────────────────┐
│           Cryptocurrency News Sources                   │
│       (CoinDesk, Cointelegraph, The Block, etc)        │
└───────────────────┬─────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────────────────┐
│        Python News Analyzer (main.py)                   │
│  - Scrapes articles                                     │
│  - Parses content                                       │
│  - Analyzes sentiment                                   │
│  - Calculates scores                                    │
└───────────────────┬─────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────────────────┐
│            Redis Data Store                             │
│  analysis:<COIN>:detailed = Full analysis JSON          │
└───────────────────┬─────────────────────────────────────┘
                    │
         ┌──────────┴──────────┐
         │                     │
         ▼                     ▼
┌──────────────────┐   ┌──────────────────┐
│   FastAPI REST   │   │  React Dashboard │
│   Server (8000)  │   │  App (3000)      │
│                  │   │                  │
│ GET /analysis    │   │- Coin List      │
│ GET /analysis/   │   │ - Summary Cards  │
│ GET /stats       │   │ - Article Table  │
│ GET /debug/      │   │ - Debug Panel    │
└──────────────────┘   └──────────────────┘
```

## 📊 Data Flow Example

### 1. Analysis Generation
```
News Scraper → Articles (5 found)
                ↓
            Parser (detect coins, extract text)
                ↓
            Sentiment Analyzer (compute sentiment scores)
                ↓
            Keyword Detector (find bullish/bearish keywords)
                ↓
            Decision Engine (calculate final score)
                ↓
            Redis Storage (store detailed analysis)
```

### 2. Dashboard Query
```
User selects "BTC" in Dashboard
           ↓
    Fetch from API: GET /analysis/BTC
           ↓
    FastAPI retrieves from Redis
           ↓
    Returns detailed JSON
           ↓
    React renders analysis summary, articles, debug info
```

## 🔄 Complete Workflow

### Step 1: Prepare Environment
```bash
# Ensure Redis is running
redis-cli ping
# Output: PONG
```

### Step 2: Generate Analysis
```bash
cd /Users/bogoai/Book-Now/news-analyzer
source venv/bin/activate

# Run one-time analysis
python main.py run-once

# Or run scheduled analysis (every 15 min)
python main.py start 15
```

### Step 3: Start API Server
```bash
cd /Users/bogoai/Book-Now/news-analyzer
source venv/bin/activate

# Start FastAPI (development mode)
python -m uvicorn api:app --reload

# Production mode (optional)
python -m uvicorn api:app --host 0.0.0.0 --port 8000 --workers 4
```

### Step 4: Launch React Dashboard
```bash
cd /Users/bogoai/Book-Now/dashboard

# Development mode
npm start

# Production build
npm run build
npm install -g serve
serve -s build -l 3000
```

### Step 5: Access Dashboard
```
Browser: http://localhost:3000/analysis-dashboard
```

## 📱 Dashboard Features

### Coin List (Left Panel)
- ✅ Search and filter coins
- ✅ View score at a glance
- ✅ See decision (BUY/SELL/HOLD)
- ✅ Article count
- ✅ Sentiment indicator

### Analysis Summary (Top Right)
- ✅ Final score visualization
- ✅ Trading decision badge (color-coded)
- ✅ Sentiment breakdown
- ✅ Source credibility score
- ✅ Keyword signal indicator
- ✅ Articles analyzed count
- ✅ Scoring formula breakdown
- ✅ Validation warnings

### Article Breakdown (Middle Right)
- ✅ Expandable article accordion
- ✅ Per-article scores
- ✅ Sentiment by article
- ✅ Keywords detected
- ✅ Coins mentioned
- ✅ Full content snippets
- ✅ Direct link to original article
- ✅ Publication date

### Debug Panel (Bottom, Debug Mode ON)
- ✅ Search query used
- ✅ URL statistics
- ✅ Fetched URLs list
- ✅ Filtered URLs list
- ✅ Raw JSON data viewer
- ✅ Debug indicators

## 🎯 Usage Scenarios

### Scenario 1: Verify a BUY Signal
```
1. Select coin "BTC" from list
2. Review summary cards:
   - Is score > 0.25? ✓ YES
   - Is sentiment positive? ✓ YES
   - Are sources reputable? ✓ YES
3. Expand articles to verify:
   - Do articles match the coin? ✓ YES
   - Are keywords correct? ✓ YES
   - Is content recent? ✓ YES
4. Check debug panel:
   - How many articles analyzed? 5 or more ✓ GOOD
   - Any filtered URLs? Check why
5. Conclusion: Signal appears valid ✓
```

### Scenario 2: Investigate Low Confidence
```
1. Notice warning: "Only 2 articles analyzed"
2. Check debug panel:
   - Search query: Good coverage? 
   - Were many URLs filtered? Why?
   - Source diversity? Same source?
3. Decision: Wait for more data before trading
```

### Scenario 3: Debug Wrong Signal
```
1. BUY signal but was bearish last hour
2. Expand debug panel (Debug Mode ON)
3. Check raw data for:
   - Sentiment scores vs expected
   - Keywords vs actual content
   - Source selection
4. Identify issue and adjust parameters
```

## 📈 API Endpoints Quick Reference

```bash
# Get all coins summary
curl http://localhost:8000/analysis

# Get detailed analysis for BTC
curl http://localhost:8000/analysis/BTC

# Get articles with pagination
curl http://localhost:8000/analysis/BTC/articles?skip=0&limit=10

# Get debug information
curl http://localhost:8000/analysis/debug/BTC

# Get overall statistics
curl http://localhost:8000/stats

# Health check
curl http://localhost:8000/health
```

## 🔧 Common Commands

```bash
# Stop API server
# Ctrl+C in terminal 2

# Stop React app
# Ctrl+C in terminal 3

# Stop analyzer
# Ctrl+C in terminal 1

# Clear Redis data
redis-cli FLUSHDB

# View Redis keys
redis-cli KEYS "analysis:*"

# Get specific analysis
redis-cli GET "analysis:BTC:detailed"

# Monitor Redis activity
redis-cli MONITOR
```

## 📊 Performance Tips

1. **Reduce Auto-Refresh**
   - Set to 30-60 seconds in production
   - Higher frequency = more API calls

2. **Clear Old Data**
   - Periodically flush old analyses
   - Keep Redis lean for performance

3. **Cache API Responses**
   - Frontend caches responses
   - Manual refresh available

4. **Pagination**
   - Use limit parameter for articles
   - Default limit is 10

## ⚠️ Important Notes

- **NOT for Trading**: Dashboard is for debugging and verification only
- **Redis Required**: Must be running for data storage
- **Network**: API and Dashboard must be accessible to each other
- **CORS**: API has CORS enabled for all origins (secure in production)
- **Data Persistence**: Redis data persists between restarts

## 🆘 Troubleshooting

### Problem: "Cannot connect to API"
```
Solution:
1. Check API is running: lsof -i :8000
2. Check Redis: redis-cli ping → PONG
3. Check API URL in .env
4. Check browser console for errors
```

### Problem: "No data showing"
```
Solution:
1. Run analyzer: python main.py run-once
2. Wait for it to complete
3. Refresh dashboard
4. Check Redis: redis-cli KEYS "analysis:*"
```

### Problem: "API slow"
```
Solution:
1. Reduce article content size
2. Implement result caching
3. Use pagination for lists
4. Check Redis performance
```

## 📚 Further Reading

- See `ANALYSIS_DASHBOARD_GUIDE.md` for comprehensive documentation
- See individual component files for detailed code comments
- See `api.py` for API implementation details

## 🎓 Learning Resources

1. **FastAPI**: https://fastapi.tiangolo.com/
2. **React**: https://react.dev/
3. **Bootstrap**: https://getbootstrap.com/
4. **Redis**: https://redis.io/
5. **Material UI**: https://mui.com/

---

**Last Updated**: April 27, 2026  
**Version**: 1.0.0

