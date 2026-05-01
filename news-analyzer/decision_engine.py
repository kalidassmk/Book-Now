import logging
import time
from typing import List, Dict, Any

logger = logging.getLogger(__name__)

class DecisionEngine:
    def __init__(self):
        # Source credibility weights
        self.source_weights = {
            'coindesk': 1.0,
            'cointelegraph': 0.9,
            'theblock': 1.0,
            'decrypt': 0.8,
            'reddit': 0.5,
            'cmc': 0.6
        }

    def analyze_coin(self, coin: str, articles: List[Dict[str, Any]]) -> Dict[str, Any]:
        """Analyze all articles for a coin and generate trading signal."""
        if not articles:
            return self._empty_analysis(coin)

        # Filter articles that mention this coin
        relevant_articles = [a for a in articles if coin in a.get('coins_mentioned', [])]

        if not relevant_articles:
            return self._empty_analysis(coin)

        # Calculate scores for each article
        article_scores = []
        sentiment_scores = []
        source_scores = []
        keyword_scores = []

        for article in relevant_articles:
            # Sentiment score
            sentiment = article.get('sentiment_analysis', {})
            combined_sentiment = sentiment.get('combined_score', 0.0)
            sentiment_scores.append(combined_sentiment)

            # Source credibility
            source = article.get('source', 'unknown')
            source_weight = self.source_weights.get(source, 0.5)
            source_scores.append(source_weight)

            # Keyword signal
            keyword_signal = article.get('keyword_signal', 0.0)
            keyword_scores.append(keyword_signal)

            # Calculate article-level score
            article_score = (
                combined_sentiment * 0.5 +
                source_weight * 0.3 +
                keyword_signal * 0.2
            )

            article_scores.append({
                'title': article.get('title', '')[:100],
                'url': article.get('url', ''),
                'source': source,
                'published_at': article.get('published'),
                'content_snippet': article.get('text', '')[:200] if article.get('text') else '',
                'sentiment_score': round(combined_sentiment, 4),
                'source_weight': round(source_weight, 4),
                'keyword_signal': round(keyword_signal, 4),
                'keywords_detected': article.get('keywords_detected', []),
                'coin_mentions': article.get('coins_mentioned', []),
                'final_article_score': round(article_score, 4)
            })

        # Average scores
        avg_sentiment = sum(sentiment_scores) / len(sentiment_scores) if sentiment_scores else 0.0
        avg_source_weight = sum(source_scores) / len(source_scores) if source_scores else 0.5
        avg_keyword_signal = sum(keyword_scores) / len(keyword_scores) if keyword_scores else 0.0

        # Final score calculation
        final_score = (
            avg_sentiment * 0.5 +      # 50% sentiment
            avg_source_weight * 0.3 +  # 30% source credibility
            avg_keyword_signal * 0.2   # 20% keyword signals
        )

        # Decision logic
        if final_score > 0.25:
            decision = 'BUY'
        elif final_score < -0.25:
            decision = 'SELL'
        else:
            decision = 'HOLD'

        # Sentiment label
        if avg_sentiment > 0.1:
            sentiment_label = 'positive'
        elif avg_sentiment < -0.1:
            sentiment_label = 'negative'
        else:
            sentiment_label = 'neutral'

        analysis_result = {
            'coin': coin,
            'score': round(final_score, 4),
            'sentiment': sentiment_label,
            'decision': decision,
            'sources': len(relevant_articles),
            'timestamp': int(time.time()),
            'details': {
                'avg_sentiment': round(avg_sentiment, 4),
                'avg_source_weight': round(avg_source_weight, 4),
                'avg_keyword_signal': round(avg_keyword_signal, 4),
                'articles_analyzed': len(relevant_articles)
            }
        }

        logger.info(f"Analysis for {coin}: score={final_score:.4f}, decision={decision}")
        return analysis_result

    def analyze_coin_detailed(self, coin: str, articles: List[Dict[str, Any]], search_query: str = "", fetched_urls: List[str] = None) -> Dict[str, Any]:
        """Analyze coin with detailed tracking for debugging."""
        if not articles:
            return self._empty_detailed_analysis(coin, search_query, fetched_urls or [])

        # Filter articles that mention this coin
        relevant_articles = [a for a in articles if coin in a.get('coins_mentioned', [])]

        if not relevant_articles:
            return self._empty_detailed_analysis(coin, search_query, fetched_urls or [])

        # Calculate scores for each article with detailed tracking
        article_scores = []
        sentiment_scores = []
        source_scores = []
        keyword_scores = []

        for article in relevant_articles:
            # Sentiment score
            sentiment = article.get('sentiment_analysis', {})
            combined_sentiment = sentiment.get('combined_score', 0.0)
            sentiment_scores.append(combined_sentiment)

            # Source credibility
            source = article.get('source', 'unknown')
            source_weight = self.source_weights.get(source, 0.5)
            source_scores.append(source_weight)

            # Keyword signal
            keyword_signal = article.get('keyword_signal', 0.0)
            keyword_scores.append(keyword_signal)

            # Calculate article-level score
            article_score = (
                combined_sentiment * 0.5 +
                source_weight * 0.3 +
                keyword_signal * 0.2
            )

            article_scores.append({
                'title': article.get('title', '')[:150],
                'url': article.get('url', ''),
                'source': source,
                'published_at': article.get('published'),
                'content_snippet': article.get('text', '')[:300] if article.get('text') else '',
                'sentiment_score': round(combined_sentiment, 4),
                'source_weight': round(source_weight, 4),
                'keyword_signal': round(keyword_signal, 4),
                'keywords_detected': article.get('keywords_detected', []),
                'coin_mentions': article.get('coins_mentioned', []),
                'final_article_score': round(article_score, 4)
            })

        # Average scores
        avg_sentiment = sum(sentiment_scores) / len(sentiment_scores) if sentiment_scores else 0.0
        avg_source_weight = sum(source_scores) / len(source_scores) if source_scores else 0.5
        avg_keyword_signal = sum(keyword_scores) / len(keyword_scores) if keyword_scores else 0.0

        # Final score calculation
        final_score = (
            avg_sentiment * 0.5 +
            avg_source_weight * 0.3 +
            avg_keyword_signal * 0.2
        )

        # Decision logic
        if final_score > 0.25:
            decision = 'BUY'
        elif final_score < -0.25:
            decision = 'SELL'
        else:
            decision = 'HOLD'

        # Sentiment label
        if avg_sentiment > 0.1:
            sentiment_label = 'positive'
        elif avg_sentiment < -0.1:
            sentiment_label = 'negative'
        else:
            sentiment_label = 'neutral'

        detailed_analysis = {
            'coin': coin,
            'final_score': round(final_score, 4),
            'decision': decision,
            'avg_sentiment': round(avg_sentiment, 4),
            'avg_source_weight': round(avg_source_weight, 4),
            'avg_keyword_signal': round(avg_keyword_signal, 4),
            'articles_analyzed': len(relevant_articles),
            'analysis_time': int(time.time()),
            'articles': article_scores,
            'debug_info': {
                'search_query': search_query,
                'fetched_urls': fetched_urls or [],
                'filtered_out_urls': [u for u in (fetched_urls or []) if u not in [a.get('url') for a in relevant_articles]],
                'total_fetched': len(fetched_urls or []),
                'total_analyzed': len(relevant_articles)
            }
        }

        logger.info(f"Detailed analysis for {coin}: score={final_score:.4f}, decision={decision}, articles={len(relevant_articles)}")
        return detailed_analysis

    def generate_ai_summary(self, coin: str, detailed_analysis: Dict[str, Any]) -> Dict[str, Any]:
        """
        Generate a structured AI summary table matching the format in the Google AI Search screenshot.
        """
        summary = {
            'coin': coin,
            'title': f"Crypto {coin} News & Sentiment Summary",
            'table': [],
            'market_performance': {
                'status': 'Active',
                'sentiment': detailed_analysis.get('decision', 'Neutral'),
                'score': detailed_analysis.get('final_score', 0)
            }
        }

        # Add the main coin as the first entry in the table
        summary['table'].append({
            'project_event': f"{coin} (Main Symbol)",
            'key_information': f"Latest analysis based on {detailed_analysis.get('articles_analyzed', 0)} sources.",
            'sentiment': detailed_analysis.get('decision', 'Neutral')
        })

        # Add top articles as "Events"
        for article in detailed_analysis.get('articles', [])[:3]:
            sentiment_val = article.get('sentiment_score', 0)
            sentiment_label = 'Bullish' if sentiment_val > 0.2 else ('Bearish' if sentiment_val < -0.2 else 'Neutral')
            
            summary['table'].append({
                'project_event': article.get('title', '')[:50] + "...",
                'key_information': article.get('content_snippet', '')[:100] + "...",
                'sentiment': sentiment_label
            })

        return summary

    def _empty_analysis(self, coin: str) -> Dict[str, Any]:
        """Return empty analysis when no data available."""
        return {
            'coin': coin,
            'score': 0.0,
            'sentiment': 'neutral',
            'decision': 'HOLD',
            'sources': 0,
            'timestamp': int(time.time()),
            'details': {
                'avg_sentiment': 0.0,
                'avg_source_weight': 0.5,
                'avg_keyword_signal': 0.0,
                'articles_analyzed': 0
            }
        }

    def _empty_detailed_analysis(self, coin: str, search_query: str = "", fetched_urls: List[str] = None) -> Dict[str, Any]:
        """Return empty detailed analysis when no data available."""
        return {
            'coin': coin,
            'final_score': 0.0,
            'decision': 'HOLD',
            'avg_sentiment': 0.0,
            'avg_source_weight': 0.5,
            'avg_keyword_signal': 0.0,
            'articles_analyzed': 0,
            'analysis_time': int(time.time()),
            'articles': [],
            'debug_info': {
                'search_query': search_query,
                'fetched_urls': fetched_urls or [],
                'filtered_out_urls': [],
                'total_fetched': len(fetched_urls or []),
                'total_analyzed': 0
            }
        }

    def validate_analysis(self, analysis: Dict[str, Any]) -> bool:
        """Validate analysis result structure."""
        required_fields = ['coin', 'score', 'sentiment', 'decision', 'sources', 'timestamp']
        return all(field in analysis for field in required_fields)
