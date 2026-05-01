"""FastAPI backend for the Crypto News Analyzer Debug Dashboard."""

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import List, Dict, Any, Optional
import logging
import json

from redis_client import RedisClient

# Configure logging
logger = logging.getLogger(__name__)
logging.basicConfig(level=logging.INFO)

# Initialize FastAPI app
app = FastAPI(
    title="Crypto News Analyzer API",
    description="Debug and visualization API for cryptocurrency news analysis",
    version="1.0.0"
)

# Enable CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Initialize Redis client
redis_client = RedisClient()

# Pydantic models for validation
class ArticleDetail(BaseModel):
    title: str
    url: str
    source: str
    published_at: Optional[int]
    content_snippet: str
    sentiment_score: float
    source_weight: float
    keyword_signal: float
    keywords_detected: List[str]
    coin_mentions: List[str]
    final_article_score: float

class DebugInfo(BaseModel):
    search_query: str
    fetched_urls: List[str]
    filtered_out_urls: List[str]
    total_fetched: int
    total_analyzed: int

class DetailedAnalysis(BaseModel):
    coin: str
    final_score: float
    decision: str
    avg_sentiment: float
    avg_source_weight: float
    avg_keyword_signal: float
    articles_analyzed: int
    analysis_time: int
    articles: List[ArticleDetail]
    debug_info: DebugInfo

class AnalysisSummary(BaseModel):
    coin: str
    score: float
    decision: str
    articles_analyzed: int

@app.get("/health")
async def health_check():
    """Health check endpoint."""
    try:
        redis_client.client.ping()
        return {
            "status": "healthy",
            "redis": "connected"
        }
    except Exception as e:
        logger.error(f"Health check failed: {e}")
        return {
            "status": "unhealthy",
            "redis": "disconnected",
            "error": str(e)
        }

@app.get("/analysis")
async def get_all_analyses() -> Dict[str, Any]:
    """
    Get summary of all analyzed coins.

    Returns:
        Dictionary with list of all coin analyses and stats
    """
    try:
        coins = redis_client.get_all_analysis_keys()
        analyses = []

        for coin in coins:
            analysis = redis_client.get_detailed_analysis(coin)
            if analysis:
                analyses.append({
                    "coin": coin,
                    "score": analysis.get("final_score", 0),
                    "decision": analysis.get("decision", "HOLD"),
                    "articles_analyzed": analysis.get("articles_analyzed", 0),
                    "analysis_time": analysis.get("analysis_time", 0),
                    "sentiment": "positive" if analysis.get("avg_sentiment", 0) > 0.1 else ("negative" if analysis.get("avg_sentiment", 0) < -0.1 else "neutral")
                })

        return {
            "success": True,
            "count": len(analyses),
            "analyses": sorted(analyses, key=lambda x: abs(x["score"]), reverse=True),
            "stats": {
                "buy_signals": len([a for a in analyses if a["decision"] == "BUY"]),
                "sell_signals": len([a for a in analyses if a["decision"] == "SELL"]),
                "hold_signals": len([a for a in analyses if a["decision"] == "HOLD"]),
                "total_coins": len(analyses)
            }
        }
    except Exception as e:
        logger.error(f"Failed to get all analyses: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/binance/symbols")
async def get_binance_symbols() -> Dict[str, Any]:
    """
    Get all Binance symbols and their details from Redis.
    (Pulls from BINANCE:SYMBOL:* keys)
    """
    try:
        symbols, details = redis_client.get_binance_symbols()
        return {
            "success": True,
            "count": len(symbols),
            "symbols": symbols,
            "details": details
        }
    except Exception as e:
        logger.error(f"Failed to get Binance symbols: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/analysis/{coin}")
async def get_analysis(coin: str) -> Dict[str, Any]:
    """
    Get detailed analysis for a specific coin.

    Args:
        coin: Coin symbol (e.g., "BTC", "ETH")

    Returns:
        Detailed analysis with article breakdown
    """
    try:
        analysis = redis_client.get_detailed_analysis(coin)

        if not analysis:
            return {
                "success": False,
                "message": f"No analysis found for {coin}",
                "coin": coin
            }

        return {
            "success": True,
            "data": analysis
        }
    except Exception as e:
        logger.error(f"Failed to get analysis for {coin}: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/analysis/{coin}/articles")
async def get_coin_articles(coin: str, skip: int = 0, limit: int = 10) -> Dict[str, Any]:
    """
    Get article-level breakdown for a specific coin with pagination.

    Args:
        coin: Coin symbol
        skip: Number of articles to skip
        limit: Maximum articles to return

    Returns:
        Paginated list of articles with scores
    """
    try:
        analysis = redis_client.get_detailed_analysis(coin)

        if not analysis:
            return {
                "success": False,
                "message": f"No analysis found for {coin}"
            }

        articles = analysis.get("articles", [])
        total = len(articles)
        paginated_articles = articles[skip:skip + limit]

        return {
            "success": True,
            "coin": coin,
            "total": total,
            "returned": len(paginated_articles),
            "skip": skip,
            "limit": limit,
            "articles": paginated_articles
        }
    except Exception as e:
        logger.error(f"Failed to get articles for {coin}: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/analysis/debug/{coin}")
async def get_debug_info(coin: str, raw: bool = False) -> Dict[str, Any]:
    """
    Get raw inputs and intermediate calculations for debugging.

    Args:
        coin: Coin symbol
        raw: If true, return raw Redis data

    Returns:
        Debug information including search queries, URLs, and calculations
    """
    try:
        analysis = redis_client.get_detailed_analysis(coin)

        if not analysis:
            return {
                "success": False,
                "message": f"No analysis found for {coin}"
            }

        if raw:
            # Return complete raw data
            return {
                "success": True,
                "coin": coin,
                "raw_data": analysis
            }
        else:
            # Return structured debug info
            debug_info = analysis.get("debug_info", {})
            return {
                "success": True,
                "coin": coin,
                "debug_info": {
                    "search_query": debug_info.get("search_query", ""),
                    "fetched_urls_count": len(debug_info.get("fetched_urls", [])),
                    "filtered_out_urls_count": len(debug_info.get("filtered_out_urls", [])),
                    "fetched_urls": debug_info.get("fetched_urls", [])[:5],  # Show first 5
                    "filtered_urls_sample": debug_info.get("filtered_out_urls", [])[:5],
                    "total_fetched": debug_info.get("total_fetched", 0),
                    "total_analyzed": debug_info.get("total_analyzed", 0),
                    "articles": [
                        {
                            "title": a.get("title", "")[:80],
                            "url": a.get("url", ""),
                            "source": a.get("source", ""),
                            "score": a.get("final_article_score", 0)
                        }
                        for a in analysis.get("articles", [])
                    ]
                }
            }
    except Exception as e:
        logger.error(f"Failed to get debug info for {coin}: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/stats")
async def get_statistics() -> Dict[str, Any]:
    """Get overall statistics about analysis results."""
    try:
        coins = redis_client.get_all_analysis_keys()

        if not coins:
            return {
                "success": True,
                "total_coins": 0,
                "stats": {
                    "buy": 0,
                    "sell": 0,
                    "hold": 0,
                    "avg_score": 0,
                    "avg_sentiment": 0
                }
            }

        buy_count = 0
        sell_count = 0
        hold_count = 0
        total_score = 0
        total_sentiment = 0

        for coin in coins:
            analysis = redis_client.get_detailed_analysis(coin)
            if analysis:
                decision = analysis.get("decision", "HOLD")
                if decision == "BUY":
                    buy_count += 1
                elif decision == "SELL":
                    sell_count += 1
                else:
                    hold_count += 1

                total_score += analysis.get("final_score", 0)
                total_sentiment += analysis.get("avg_sentiment", 0)

        num_coins = len(coins)

        return {
            "success": True,
            "total_coins": num_coins,
            "stats": {
                "buy": buy_count,
                "sell": sell_count,
                "hold": hold_count,
                "avg_score": round(total_score / num_coins, 4) if num_coins > 0 else 0,
                "avg_sentiment": round(total_sentiment / num_coins, 4) if num_coins > 0 else 0,
                "buy_percentage": round((buy_count / num_coins * 100), 2) if num_coins > 0 else 0,
                "sell_percentage": round((sell_count / num_coins * 100), 2) if num_coins > 0 else 0,
                "hold_percentage": round((hold_count / num_coins * 100), 2) if num_coins > 0 else 0
            }
        }
    except Exception as e:
        logger.error(f"Failed to get statistics: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/refresh/{coin}")
async def refresh_coin_analysis(coin: str) -> Dict[str, Any]:
    """
    Trigger a refresh of analysis for a specific coin.
    (Placeholder for future integration with scheduler)
    """
    return {
        "success": True,
        "message": f"Refresh triggered for {coin}",
        "note": "Actual refresh requires scheduler integration"
    }

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)

