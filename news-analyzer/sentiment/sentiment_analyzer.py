import logging
import re
from textblob import TextBlob

try:
    from vaderSentiment.vaderSentiment import SentimentIntensityAnalyzer
    HAS_VADER = True
except ImportError:
    HAS_VADER = False
    logging.warning("vaderSentiment not installed. Falling back to TextBlob sentiment analysis.")

logger = logging.getLogger(__name__)

class SentimentAnalyzer:
    def __init__(self):
        self.vader = SentimentIntensityAnalyzer() if HAS_VADER else None
        
        # Comprehensive Crypto Keyword Lists
        self.bullish_keywords = {
            'growth': 1.0, 'bullish': 1.0, 'surge': 1.2, 'rally': 1.2, 'positive': 0.8,
            'adoption': 1.5, 'partnership': 1.3, 'listing': 1.4, 'upgrade': 1.0, 
            'outperform': 1.2, 'breakout': 1.3, 'moon': 1.5, 'ath': 1.5, 'buying': 0.8,
            'accumulating': 1.0, 'institutional': 1.5, 'etf': 2.0, 'approved': 1.5,
            'launch': 1.0, 'mainnet': 1.2, 'burn': 1.0, 'reward': 0.8, 'halving': 2.0,
            'undervalued': 1.2, 'support': 0.5, 'recovery': 0.8, 'gain': 1.0
        }
        
        self.bearish_keywords = {
            'crash': 1.5, 'dump': 1.5, 'bearish': 1.0, 'decline': 0.8, 'negative': 0.8,
            'hack': 2.0, 'exploit': 2.0, 'scam': 1.8, 'fraud': 1.8, 'sec': 1.0,
            'lawsuit': 1.5, 'regulation': 0.5, 'ban': 2.0, 'rejected': 1.5,
            'fud': 1.2, 'selling': 0.8, 'outflow': 1.0, 'bankruptcy': 2.0,
            'liquidated': 1.5, 'resistance': 0.5, 'plunge': 1.3, 'drop': 1.0,
            'whale': 0.2, 'scary': 0.8, 'warning': 1.0, 'overvalued': 1.2
        }

    def analyze_sentiment(self, text):
        """Analyze sentiment of text using VADER and TextBlob."""
        if not text or not text.strip():
            return {'polarity': 0.0, 'vader_score': 0.0, 'combined_score': 0.0}

        # Clean text
        cleaned_text = self._clean_text(text)

        # 1. VADER Analysis (if available)
        vader_score = 0.0
        if self.vader:
            vader_scores = self.vader.polarity_scores(cleaned_text)
            vader_score = vader_scores['compound']

        # 2. TextBlob Analysis (Classic polarity)
        blob = TextBlob(cleaned_text)
        blob_polarity = blob.sentiment.polarity

        # 3. Keyword-based Analysis
        keyword_score = self.analyze_keywords(cleaned_text)

        # Combine scores (weighted)
        if self.vader:
            combined_score = (vader_score * 0.6) + (blob_polarity * 0.2) + (keyword_score * 0.2)
        else:
            # Fallback weighting if VADER is missing
            combined_score = (blob_polarity * 0.6) + (keyword_score * 0.4)

        return {
            'polarity': blob_polarity,
            'vader_score': vader_score,
            'keyword_score': keyword_score,
            'combined_score': combined_score
        }

    def analyze_keywords(self, text):
        """Analyze sentiment based on crypto-specific keywords."""
        if not text:
            return 0.0

        text_lower = text.lower()
        score = 0.0
        count = 0

        # Check bullish keywords
        for word, weight in self.bullish_keywords.items():
            if word in text_lower:
                score += weight
                count += 1
        
        # Check bearish keywords
        for word, weight in self.bearish_keywords.items():
            if word in text_lower:
                score -= weight
                count += 1

        if count == 0:
            return 0.0
            
        # Normalize and damp the score
        normalized_score = score / (count + 2) 
        return min(max(normalized_score, -1.0), 1.0)

    def _clean_text(self, text):
        """Clean text for analysis."""
        # Remove URLs
        text = re.sub(r'http[s]?://(?:[a-zA-Z]|[0-9]|[$-_@.&+]|[!*\\(\\),]|(?:%[0-9a-fA-F][0-9a-fA-F]))+', '', text)
        # Remove extra whitespace
        text = re.sub(r'\s+', ' ', text).strip()
        return text

    def get_sentiment_label(self, score):
        """Convert sentiment score to label."""
        if score > 0.2:
            return 'positive'
        elif score < -0.2:
            return 'negative'
        else:
            return 'neutral'
