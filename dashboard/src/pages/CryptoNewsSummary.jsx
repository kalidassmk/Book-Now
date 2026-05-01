/**
 * CryptoNewsSummary.jsx
 * Beautiful dashboard displaying crypto news & sentiment summary in tabular format
 * Shows Project/Event, Key Information, and Sentiment columns
 */

import React, { useState, useEffect } from 'react';
import {
  Container,
  Row,
  Col,
  Card,
  Table,
  Badge,
  Button,
  Spinner,
  Alert,
  Form,
  InputGroup
} from 'react-bootstrap';
import '../styles/CryptoNewsSummary.css';

const CryptoNewsSummary = () => {
  const [summaryData, setSummaryData] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [refreshInterval, setRefreshInterval] = useState(30);
  const [filterSentiment, setFilterSentiment] = useState('');
  const [searchTerm, setSearchTerm] = useState('');
  const [sortBy, setSortBy] = useState('name');
  const [majorCoinsOnly, setMajorCoinsOnly] = useState(false);

  const FLASK_API_BASE = process.env.REACT_APP_FLASK_API || 'http://localhost:8000';
  const major_coins = ['BTC', 'ETH', 'BNB', 'ADA', 'SOL', 'DOGE', 'XRP', 'DOT'];

  // Fetch summary data from backend
  const fetchSummaryData = async () => {
    try {
      setLoading(true);

      // Call the news-analyzer API to get all coins analysis
      const response = await fetch(`${FLASK_API_BASE}/analysis`);

      if (!response.ok) {
        throw new Error(`API Error: ${response.status}`);
      }

      const data = await response.json();

      // Transform API data to our display format
      let formattedData = [];

      if (Array.isArray(data.coins)) {
        formattedData = data.coins.map(coin => ({
          symbol: coin.coin || coin.symbol || '',
          projectEvent: coin.coin || '',
          keyInformation: generateKeyInformation(coin),
          sentiment: determineSentiment(coin),
          sentimentScore: coin.final_score || 0,
          decision: coin.decision || 'HOLD',
          articlesCount: coin.articles_analyzed || 0
        }));
      } else if (typeof data === 'object' && !Array.isArray(data)) {
        // If data is an object with coins as values
        formattedData = Object.values(data).map(coin => ({
          symbol: coin.coin || coin.symbol || '',
          projectEvent: coin.coin || '',
          keyInformation: generateKeyInformation(coin),
          sentiment: determineSentiment(coin),
          sentimentScore: coin.final_score || 0,
          decision: coin.decision || 'HOLD',
          articlesCount: coin.articles_analyzed || 0
        }));
      }

      // Apply filters
      if (majorCoinsOnly) {
        formattedData = formattedData.filter(item =>
          major_coins.includes(item.symbol)
        );
      }

      if (filterSentiment) {
        formattedData = formattedData.filter(item =>
          item.sentiment.toLowerCase().includes(filterSentiment.toLowerCase())
        );
      }

      if (searchTerm) {
        formattedData = formattedData.filter(item =>
          item.projectEvent.toLowerCase().includes(searchTerm.toLowerCase())
        );
      }

      // Sort data
      if (sortBy === 'sentiment') {
        formattedData.sort((a, b) => b.sentimentScore - a.sentimentScore);
      } else if (sortBy === 'name') {
        formattedData.sort((a, b) => a.projectEvent.localeCompare(b.projectEvent));
      }

      setSummaryData(formattedData);
      setError(null);
    } catch (err) {
      console.error('Error fetching summary data:', err);
      setError(`Failed to fetch data: ${err.message}`);
      setSummaryData([]);
    } finally {
      setLoading(false);
    }
  };

  // Generate key information from coin data
  const generateKeyInformation = (coin) => {
    const parts = [];

    if (coin.final_score !== undefined) {
      parts.push(`Analysis Score: ${coin.final_score.toFixed(4)}`);
    }

    if (coin.articles_analyzed) {
      parts.push(`${coin.articles_analyzed} articles analyzed`);
    }

    if (coin.avg_sentiment !== undefined) {
      parts.push(`Sentiment: ${coin.avg_sentiment.toFixed(4)}`);
    }

    return parts.length > 0
      ? parts.join('; ')
      : 'No analysis data available';
  };

  // Determine sentiment based on decision and score
  const determineSentiment = (coin) => {
    const decision = coin.decision || 'HOLD';
    const score = coin.final_score || 0;

    if (coin.articles_analyzed < 3) {
      return 'Insufficient Data';
    }

    switch (decision) {
      case 'BUY':
        return score > 0.3 ? 'Highly Bullish' : 'Bullish';
      case 'SELL':
        return score < -0.3 ? 'Highly Bearish' : 'Bearish';
      default:
        if (Math.abs(score) < 0.1) {
          return 'Neutral';
        }
        return score > 0 ? 'Slightly Bullish' : 'Slightly Bearish';
    }
  };

  // Initial load
  useEffect(() => {
    fetchSummaryData();
  }, [majorCoinsOnly]);

  // Auto-refresh effect
  useEffect(() => {
    if (refreshInterval > 0) {
      const interval = setInterval(() => {
        fetchSummaryData();
      }, refreshInterval * 1000);

      return () => clearInterval(interval);
    }
  }, [refreshInterval]);

  // Get badge variant for sentiment
  const getSentimentBadgeVariant = (sentiment) => {
    if (sentiment.includes('Bullish')) {
      return sentiment.includes('Highly') ? 'danger' : 'success';
    } else if (sentiment.includes('Bearish')) {
      return sentiment.includes('Highly') ? 'danger' : 'warning';
    } else if (sentiment.includes('Neutral')) {
      return 'secondary';
    } else if (sentiment.includes('Fearful')) {
      return 'danger';
    }
    return 'info';
  };

  return (
    <div className="crypto-news-summary">
      {/* Header */}
      <div className="summary-header bg-gradient">
        <Container>
          <Row className="align-items-center py-5">
            <Col md={8}>
              <h1 className="summary-title">
                <i className="fas fa-newspaper"></i> Crypto Ship News & Sentiment Summary
              </h1>
              <p className="text-muted mt-2">Real-time cryptocurrency sentiment analysis from multiple news sources</p>
            </Col>
            <Col md={4} className="text-end">
              <div className="refresh-controls">
                <Form.Group className="mb-0">
                  <Form.Label className="small fw-bold">Auto-Refresh</Form.Label>
                  <Form.Select
                    size="sm"
                    value={refreshInterval}
                    onChange={(e) => setRefreshInterval(parseInt(e.target.value))}
                    className="mt-2"
                  >
                    <option value="0">Disabled</option>
                    <option value="10">10s</option>
                    <option value="30">30s</option>
                    <option value="60">60s</option>
                  </Form.Select>
                </Form.Group>
              </div>
            </Col>
          </Row>
        </Container>
      </div>

      {/* Main Content */}
      <Container fluid className="py-4">
        {/* Controls */}
        <Row className="mb-4">
          <Col md={4}>
            <InputGroup>
              <InputGroup.Text>
                <i className="fas fa-search"></i>
              </InputGroup.Text>
              <Form.Control
                placeholder="Search coins..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
              />
            </InputGroup>
          </Col>

          <Col md={3}>
            <Form.Select
              value={filterSentiment}
              onChange={(e) => setFilterSentiment(e.target.value)}
            >
              <option value="">All Sentiments</option>
              <option value="Bullish">Bullish</option>
              <option value="Bearish">Bearish</option>
              <option value="Neutral">Neutral</option>
              <option value="Highly">Highly (Bullish/Bearish)</option>
            </Form.Select>
          </Col>

          <Col md={3}>
            <Form.Select
              value={sortBy}
              onChange={(e) => setSortBy(e.target.value)}
            >
              <option value="name">Sort by Name</option>
              <option value="sentiment">Sort by Sentiment Score</option>
            </Form.Select>
          </Col>

          <Col md={2} className="text-end">
            <Form.Check
              type="checkbox"
              id="major-coins-check"
              label="Major Coins Only"
              checked={majorCoinsOnly}
              onChange={(e) => setMajorCoinsOnly(e.target.checked)}
              className="mt-2"
            />
          </Col>
        </Row>

        {/* Error Alert */}
        {error && (
          <Alert variant="danger" dismissible onClose={() => setError(null)}>
            <i className="fas fa-exclamation-circle"></i> {error}
          </Alert>
        )}

        {/* Summary Table */}
        {loading ? (
          <div className="text-center py-5">
            <Spinner animation="border" role="status" className="mb-3">
              <span className="visually-hidden">Loading...</span>
            </Spinner>
            <p>Loading sentiment analysis data...</p>
          </div>
        ) : (
          <Card className="summary-card">
            <Card.Header className="bg-white border-bottom">
              <Row className="align-items-center">
                <Col>
                  <h5 className="mb-0">
                    <i className="fas fa-table"></i> Analysis Summary
                  </h5>
                </Col>
                <Col className="text-end">
                  <small className="text-muted">
                    {summaryData.length} coin{summaryData.length !== 1 ? 's' : ''}
                  </small>
                </Col>
              </Row>
            </Card.Header>

            <Card.Body className="p-0">
              {summaryData.length > 0 ? (
                <div className="table-responsive">
                  <Table hover className="summary-table mb-0">
                    <thead>
                      <tr className="table-header">
                        <th className="col-3">Project/Event</th>
                        <th className=\"col-5\">Key Information</th>
                        <th className=\"col-1\">Sentiment</th>
                        <th className=\"col-1 text-center\">Trade</th>
                      </tr>
                    </thead>
                    <tbody>
                      {summaryData.map((item, index) => (
                        <tr key={index} className="data-row">
                          <td className="project-column">
                            <div className="project-name">
                              <strong>{item.projectEvent}</strong>
                            </div>
                            <small className="text-muted">
                              {item.symbol}USDT
                            </small>
                          </td>
                          <td className="info-column">
                            <p className="mb-0 text-wrap">
                              {item.keyInformation}
                            </p>
                          </td>
                          <td className="sentiment-column text-center">
                            <Badge
                              bg={getSentimentBadgeVariant(item.sentiment)}
                              className="sentiment-badge"
                            >
                              {item.sentiment}
                            </Badge>
                            {item.articlesCount > 0 && (
                              <div className="mt-2">
                                <small className="text-muted d-block">
                                  {item.articlesCount} sources
                                </small>
                                <small className="text-muted d-block">
                                  Score: {item.sentimentScore.toFixed(3)}
                                </small>
                              </div>
                            )}
                          </td>
                          <td className=\"text-center\">
                            <Button 
                              variant=\"outline-warning\" 
                              size=\"sm\"
                              onClick={(e) => {
                                e.stopPropagation();
                                window.open(`https://www.binance.com/en-IN/trade/${item.symbol}_USDT?_from=markets&type=spot`, '_blank');
                              }}
                              title=\"Trade on Binance\"
                              style={{ 
                                backgroundColor: 'rgba(240, 185, 11, 0.1)', 
                                borderColor: '#f0b90b',
                                color: '#f0b90b'
                              }}
                            >
                              <img src=\"https://bin.bnbstatic.com/static/images/common/favicon.ico\" width=\"14\" height=\"14\" alt=\"Binance\" />
                            </Button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </Table>
                </div>
              ) : (
                <div className="text-center py-5">
                  <i className="fas fa-inbox fa-3x text-muted mb-3"></i>
                  <p className="text-muted">No data available. Try adjusting your filters.</p>
                </div>
              )}
            </Card.Body>

            <Card.Footer className="bg-light text-muted small">
              <Row>
                <Col>
                  Total: {summaryData.length} cryptocurrencies analyzed
                </Col>
                <Col className="text-end">
                  Last updated: {new Date().toLocaleTimeString()}
                </Col>
              </Row>
            </Card.Footer>
          </Card>
        )}
      </Container>
    </div>
  );
};

export default CryptoNewsSummary;

