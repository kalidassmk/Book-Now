/**
 * AnalysisDashboard.jsx
 * Main page for the Crypto News Analysis Debug Dashboard
 * Displays all analyses with detailed drill-down capabilities
 */

import React, { useState, useEffect } from 'react';
import {
  Container,
  Row,
  Col,
  Card,
  Spinner,
  Alert,
  Form
} from 'react-bootstrap';
import {
  CoinList,
  AnalysisSummary,
  ArticleBreakdown,
  DebugPanel,
  MarketBehavior
} from '../components/AnalysisDashboard';
import '../styles/AnalysisDashboard.css';
import io from 'socket.io-client';

const socket = io('http://localhost:8000');

const AnalysisDashboard = () => {
  const [coins, setCoin] = useState([]);
  const [selectedCoin, setSelectedCoin] = useState(null);
  const [analysis, setAnalysis] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [debugMode, setDebugMode] = useState(false);
  const [refreshInterval, setRefreshInterval] = useState(30);
  const [stats, setStats] = useState(null);
  const [sentimentData, setSentimentData] = useState({});

  const API_BASE_URL = process.env.REACT_APP_API_URL || 'http://localhost:8000';

  // Fetch all coin analyses
  const fetchAllAnalyses = async () => {
    try {
      setLoading(true);
      const response = await fetch(`${API_BASE_URL}/analysis`);
      const data = await response.json();
      if (data.success) {
        // Sort by final_score descending (High score at top)
        const sortedCoins = (data.analyses || []).sort((a, b) => 
          (b.final_score || 0) - (a.final_score || 0)
        );
        setCoin(sortedCoins);
        setStats(data.stats);
        setError(null);
      } else {
        setError(data.message || 'Failed to fetch analyses');
      }
    } catch (err) {
      setError(`Error fetching analyses: ${err.message}`);
    } finally {
      setLoading(false);
    }
  };

  // Fetch detailed analysis for selected coin
  const fetchCoinAnalysis = async (coin) => {
    try {
      const response = await fetch(`${API_BASE_URL}/analysis/${coin}`);
      const data = await response.json();
      if (data.success) {
        setAnalysis(data.data);
        setSelectedCoin(coin);
      }
    } catch (err) {
      console.error(err);
    }
  };

  // Initial load
  useEffect(() => {
    fetchAllAnalyses();
  }, []);

  // Socket listener for real-time behavioral sentiment
  useEffect(() => {
    socket.on('update', (data) => {
      if (data.behavioralSentiment) {
        setSentimentData(data.behavioralSentiment);
      }
    });
    return () => socket.off('update');
  }, []);

  // Auto-refresh effect
  useEffect(() => {
    if (refreshInterval > 0) {
      const interval = setInterval(() => {
        fetchAllAnalyses();
        if (selectedCoin) fetchCoinAnalysis(selectedCoin);
      }, refreshInterval * 1000);
      return () => clearInterval(interval);
    }
  }, [refreshInterval, selectedCoin]);

  return (
    <div className="analysis-dashboard">
      <div className="dashboard-header">
        <Container>
          <Row className="align-items-center py-4">
            <Col md={8}>
              <h1 className="dashboard-title">
                <i className="fas fa-chart-line"></i> Crypto Intelligence Dashboard
              </h1>
              <p className="text-muted">Real-time market behavior and news analysis</p>
            </Col>
            <Col md={4} className="text-end">
              <Form.Check 
                type="switch" id="debug-mode" label="Debug Mode" 
                checked={debugMode} onChange={(e) => setDebugMode(e.target.checked)} 
              />
            </Col>
          </Row>
        </Container>
      </div>

      <Container fluid className="py-4">
        {/* Real-time Market Behavior Radar */}
        <MarketBehavior sentimentData={sentimentData} />

        {error && <Alert variant="danger">{error}</Alert>}

        {loading ? (
          <div className="text-center py-5"><Spinner animation="border" /></div>
        ) : (
          <Row className="g-3 mt-4">
            <Col lg={4}>
              <CoinList coins={coins} selectedCoin={selectedCoin} onSelectCoin={fetchCoinAnalysis} debugMode={debugMode} />
            </Col>
            <Col lg={8}>
              {selectedCoin && analysis ? (
                <>
                  <AnalysisSummary analysis={analysis} />
                  <ArticleBreakdown analysis={analysis} />
                  {debugMode && <DebugPanel analysis={analysis} />}
                </>
              ) : (
                <Card className="text-center py-5">
                  <Card.Body>
                    <i className="fas fa-info-circle fa-3x text-muted mb-3"></i>
                    <p className="text-muted">Select a coin to view detailed analysis</p>
                  </Card.Body>
                </Card>
              )}
            </Col>
          </Row>
        )}
      </Container>
    </div>
  );
};

export default AnalysisDashboard;
