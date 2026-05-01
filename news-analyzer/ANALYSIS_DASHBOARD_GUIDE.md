# Crypto News Analysis Debug Dashboard

A comprehensive full-stack feature for storing, visualizing, and debugging cryptocurrency news analysis results with detailed signal generation transparency.

## 📋 Table of Contents

- [Overview](#overview)
- [Backend Architecture](#backend-architecture)
- [Frontend Architecture](#frontend-architecture)
- [Installation & Setup](#installation--setup)
- [API Documentation](#api-documentation)
- [UI Components](#ui-components)
- [Debugging Features](#debugging-features)
- [Troubleshooting](#troubleshooting)

## 🎯 Overview

This feature provides:

1. **Detailed Analysis Storage**: Comprehensive Redis data model capturing every step of the analysis
2. **REST API**: FastAPI backend with multiple endpoints for querying analysis data
3. **Interactive Dashboard**: React-based UI for exploring and debugging analysis results
4. **Transparency**: Full visibility into scoring logic, article contributions, and keyword detection
5. **Validation**: Warning indicators for low-confidence signals
6. **Debug Mode**: Access to raw inputs, intermediate calculations, and URL tracking

---

## 🏗️ Backend Architecture

### Enhanced Redis Data Model

#### Primary Analysis Storage
**Key Format**: `analysis:<COIN>:detailed`

```json
{
  "coin": "BTCUSDT",
  "final_score": 0.41,
  "decision": "BUY",
  "avg_sentiment": 0.35,
  "avg_source_weight": 0.95,
  "avg_keyword_signal": 0.2,
  "articles_analyzed": 5,
  "analysis_time": 1710000000,
  "articles": [
    {
      "title": "Bitcoin ETF Approval Expected Soon",
      "url": "https://example.com/article",
      "source": "CoinDesk",
      "published_at": 1710000000,
      "content_snippet": "...",
      "sentiment_score": 0.4,
      "source_weight": 1.0,
      "keyword_signal": 0.3,
      "keywords_detected": ["ETF", "adoption"],
      "coin_mentions": ["BTC"],
      "final_article_score": 0.46
    }
  ],
  "debug_info": {
    "search_query": "BTC crypto news",
    "fetched_urls": ["https://...", "https://..."],
    "filtered_out_urls": ["https://..."],
    "total_fetched": 15,
    "total_analyzed": 5
  }
}
```

### Python Components

#### 1. **redis_client.py** - Enhanced
New methods:
- `store_detailed_analysis(coin, data)` - Store full analysis
- `get_detailed_analysis(coin)` - Retrieve analysis
- `get_all_analysis_keys()` - List all analyzed coins

#### 2. **decision_engine.py** - Extended
New methods:
- `analyze_coin_detailed(coin, articles, search_query, fetched_urls)` - Comprehensive analysis with debug info
- Tracks article-level scores
- Collects keyword data
- Records filtering statistics

#### 3. **api.py** - FastAPI Backend
Production-ready REST API with comprehensive endpoints:

```python
GET  /health                    # Health check
GET  /analysis                  # List all analyses
GET  /analysis/{coin}           # Detailed analysis for coin
GET  /analysis/{coin}/articles  # Article breakdown (paginated)
GET  /analysis/debug/{coin}     # Debug info
GET  /stats                     # Overall statistics
POST /refresh/{coin}            # Trigger refresh (placeholder)
```

---

## 🎨 Frontend Architecture

### React Component Structure

```
/pages
  └── AnalysisDashboard.jsx          # Main page component

/components/AnalysisDashboard/
  ├── CoinList.jsx                   # Left panel: Coin selection
  ├── AnalysisSummary.jsx            # Summary statistics & cards
  ├── ArticleBreakdown.jsx           # Article table & expandable rows
  ├── DebugPanel.jsx                 # Debug information
  └── index.js                       # Component exports

/styles
  └── AnalysisDashboard.css          # Comprehensive styling
```

### Component Responsibilities

#### **CoinList.jsx**
- Displays all analyzed coins
- Search/filter functionality
- Quick stats (score, decision, articles)
- Highlights selected coin
- Shows sentiment indicators

#### **AnalysisSummary.jsx**
- Summary cards for key metrics
- Final score visualization
- Decision badge
- Scoring breakdown with math
- Warning indicators

#### **ArticleBreakdown.jsx**
- Accordion-style article list
- Article-by-article scores
- Expandable detailed view
- Keywords and coin mentions
- Direct links to original articles
- Metadata display

#### **DebugPanel.jsx**
- Search query display
- URL statistics
- Fetched vs analyzed URLs
- Filtered URLs list
- Raw JSON data viewer
- Debug indicators and warnings

---

## 📦 Installation & Setup

### Backend Setup

1. **Install FastAPI dependencies** (already done):
```bash
pip install fastapi uvicorn pydantic
```

2. **Start the API server**:
```bash
cd /Users/bogoai/Book-Now/news-analyzer
source venv/bin/activate
python -m uvicorn api:app --host 0.0.0.0 --port 8000 --reload
```

3. **Verify API is running**:
```bash
curl http://localhost:8000/health
```

### Frontend Setup

1. **Install React dependencies** (if not already done):
```bash
cd /Users/bogoai/Book-Now/dashboard
npm install
```

2. **Configure API URL** (optional):
Create `.env` file:
```env
REACT_APP_API_URL=http://localhost:8000
```

3. **Start React development server**:
```bash
npm start
```

4. **Build for production**:
```bash
npm run build
```

### Integration with Existing System

1. **Update main.py** to use detailed analysis:
```python
# Already updated in main.py
detailed_analysis = self.decision_engine.analyze_coin_detailed(
    coin, 
    all_articles,
    search_query=f"{coin} crypto news",
    fetched_urls=[a.get('url') for a in all_articles]
)
self.redis_client.store_detailed_analysis(coin, detailed_analysis)
```

2. **Verify Redis connection**:
```bash
redis-cli ping
```

---

## 📡 API Documentation

### Endpoint: GET /analysis

**Description**: Get summary of all analyzed coins

**Query Parameters**: None

**Response**:
```json
{
  "success": true,
  "count": 10,
  "analyses": [
    {
      "coin": "BTC",
      "score": 0.41,
      "decision": "BUY",
      "articles_analyzed": 5,
      "sentiment": "positive"
    }
  ],
  "stats": {
    "buy_signals": 4,
    "sell_signals": 1,
    "hold_signals": 5,
    "total_coins": 10
  }
}
```

### Endpoint: GET /analysis/{coin}

**Description**: Get detailed analysis for a specific coin

**Parameters**:
- `coin` (string, required): Coin symbol (e.g., "BTC", "ETH")

**Response**:
```json
{
  "success": true,
  "data": {
    "coin": "BTC",
    "final_score": 0.41,
    "decision": "BUY",
    "articles": [...],
    "debug_info": {...}
  }
}
```

### Endpoint: GET /analysis/{coin}/articles

**Description**: Paginated article breakdown

**Parameters**:
- `coin` (string, required): Coin symbol
- `skip` (integer, optional): Pagination offset (default: 0)
- `limit` (integer, optional): Results per page (default: 10)

**Response**:
```json
{
  "success": true,
  "coin": "BTC",
  "total": 15,
  "returned": 10,
  "articles": [...]
}
```

### Endpoint: GET /analysis/debug/{coin}

**Description**: Raw inputs and debug information

**Parameters**:
- `coin` (string, required): Coin symbol
- `raw` (boolean, optional): Return complete raw data (default: false)

**Response**:
```json
{
  "success": true,
  "debug_info": {
    "search_query": "BTC crypto news",
    "fetched_urls_count": 15,
    "filtered_out_urls_count": 10,
    "total_fetched": 15,
    "total_analyzed": 5
  }
}
```

### Endpoint: GET /stats

**Description**: Overall statistics

**Response**:
```json
{
  "success": true,
  "total_coins": 10,
  "stats": {
    "buy": 4,
    "sell": 1,
    "hold": 5,
    "avg_score": 0.23,
    "buy_percentage": 40.0,
    "sell_percentage": 10.0
  }
}
```

---

## 🎯 UI Components Guide

### Main Dashboard Layout

```
┌─────────────────────────────────────────┐
│  Header with Title & Debug Mode Toggle  │
├─────────────────────────────────────────┤
│  Stats Bar (BUY/SELL/HOLD counts)       │
├──────────────────┬──────────────────────┤
│                  │                      │
│  Coin List       │  Analysis Details    │
│  (Searchable)    │  - Summary (Cards)   │
│                  │  - Article Table     │
│                  │  - Debug Panel       │
│                  │  (if Mode ON)        │
│                  │                      │
└──────────────────┴──────────────────────┘
```

### Feature Highlights

**Color Coding**:
- BUY signals: 🟢 Green
- SELL signals: 🔴 Red
- HOLD signals: 🟡 Yellow

**Validation Warnings**:
- ⚠️ Less than 3 articles
- ⚠️ Only 1 source used
- ⚠️ Extreme sentiment values
- ⚠️ High filtering rate

**Auto-Refresh Options**:
- Disabled (default)
- 10 seconds
- 30 seconds
- 60 seconds

**Debug Mode Toggle**:
- Off (default): Clean UI, summary only
- On: Show debug panels, raw data, calculations

---

## 🔍 Debugging Features

### Debug Information Available

1. **Search Query**: What was searched for this coin
2. **URL Fetching**: How many URLs were attempted
3. **Filtering**: How many articles were filtered out and why
4. **Scoring Math**: Full breakdown of final score calculation
5. **Article Contribution**: Each article's impact on final score
6. **Keywords**: What bullish/bearish words were detected
7. **Raw JSON**: Complete unformatted analysis data

### Common Debugging Scenarios

#### Low Signal Confidence
```
Check:
- Number of articles analyzed (should be ≥ 3)
- Source diversity (should use multiple reputable sources)
- Sentiment consistency (all positive/negative?)
- Keyword detection (expected keywords present?)
```

#### Unexpected Signal
```
Check:
- Individual article scores (outliers?)
- Source weights (trusted source or not?)
- Sentiment analysis correctness
- Keyword signal calculation
```

#### Missing Data
```
Check:
- URLs were fetched but filtered out (why?)
- Articles couldn't be parsed
- Coin not found in articles
- Search query produced no results
```

---

## 📊 Verification Checklist

Before using signals for trading:

- [ ] At least 3 articles analyzed
- [ ] Multiple sources (not just 1)
- [ ] Sentiment score within expected range
- [ ] Scoring breakdown makes sense
- [ ] Key phrases detected correctly
- [ ] Articles are recent (<24h old)
- [ ] No extreme outlier scores
- [ ] Source credibility is appropriate

---

## 🚀 Usage Example

### Step 1: Run Analysis
```bash
# Terminal 1: Run news analyzer
cd /Users/bogoai/Book-Now/news-analyzer
source venv/bin/activate
python main.py run-once
```

### Step 2: Start Backend API
```bash
# Terminal 2: Start FastAPI
cd /Users/bogoai/Book-Now/news-analyzer
source venv/bin/activate
python -m uvicorn api:app --reload
```

### Step 3: Launch Dashboard
```bash
# Terminal 3: Start React app
cd /Users/bogoai/Book-Now/dashboard
npm start
```

### Step 4: View Analysis
- Open http://localhost:3000/analysis-dashboard
- Select a coin from left panel
- Review summary cards and scores
- Expand articles to see details
- Toggle debug mode to see raw data

---

## 🔧 Troubleshooting

### Issue: API Returns 404
```
Solution:
1. Check Redis is running: redis-cli ping
2. Check  data was stored: redis-cli GET "analysis:BTC:detailed"
3. Verify coin symbol format (uppercase)
```

### Issue: No Data Displayed
```
Solution:
1. Run analyzer: python main.py run-once
2. Check Redis connection in browser console
3. Verify API URL in .env matches your setup
4.Check CORS settings in api.py
```

### Issue: Slow Loading
```
Solution:
1. Enable Redux caching for API responses
2. Implement pagination for large datasets
3. Reduce article content snippet size
4. Use lazy loading for expandable rows
```

### Issue: Old Data Displayed
```
Solution:
1. Check auto-refresh setting
2. Manually refresh page (Ctrl+R)
3. Check Redis expiration times
4. Clear browser cache
```

---

## 📈 Performance Optimization

1. **API Caching**: Implement response caching
```python
from functools import lru_cache
@lru_cache(maxsize=128)
def get_analysis_cached(coin: str):
    return redis_client.get_detailed_analysis(coin)
```

2. **Frontend Caching**: Cache API responses in React
```javascript
const [cache, setCache] = useState({});
```

3. **Pagination**: Limit articles displayed
```python
articles = articles[skip:skip + limit]
```

4. **Lazy Loading**: Load article details on demand

---

## 📝 Future Enhancements

1. **Export Functionality**: Download analysis as PDF/CSV
2. **Comparison Tool**: Compare analyses across coins
3. **Historical Tracking**: View analysis evolution over time
4. **Custom Alerts**: Notify when signals change
5. **Backtesting Integration**: Test signals against historical data
6. **Custom Thresholds**: Adjustable score thresholds
7. **Sentiment Heatmap**: Visual representation of sentiment trends

---

## 📄 License

This feature is part of the proprietary trading system. All rights reserved.

