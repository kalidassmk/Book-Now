/**
 * AnalysisSummary.jsx
 * Displays summary statistics and scores for a coin
 */

import React from 'react';
import { Card, Row, Col, ProgressBar, Alert } from 'react-bootstrap';

const AnalysisSummary = ({ analysis }) => {
  const { final_score, decision, avg_sentiment, avg_source_weight, avg_keyword_signal, articles_analyzed } = analysis;

  const getDecisionColor = (decision) => {
    switch (decision) {
      case 'BUY':
        return 'success';
      case 'SELL':
        return 'danger';
      case 'HOLD':
        return 'warning';
      default:
        return 'secondary';
    }
  };

  const getSentimentLabel = (sentiment) => {
    return avg_sentiment > 0.1 ? 'Positive' : avg_sentiment < -0.1 ? 'Negative' : 'Neutral';
  };

  // Validation warnings
  const warnings = [];
  if (articles_analyzed < 3) {
    warnings.push(`Only ${articles_analyzed} article(s) analyzed - signal may be unreliable`);
  }

  return (
    <>
      {/* Warnings */}
      {warnings.length > 0 && (
        <Alert variant="warning" className="mb-3">
          <i className="fas fa-exclamation-triangle"></i>
          {warnings.map((warning, idx) => (
            <div key={idx}>{warning}</div>
          ))}
        </Alert>
      )}

      {/* Summary Cards */}
      <Row className="mb-4 g-3">
        {/* Final Score Card */}
        <Col md={6} lg={3}>
          <Card className="summary-card">
            <Card.Body>
              <div className="summary-label">Final Score</div>
              <div className={`summary-value ${final_score > 0 ? 'text-success' : final_score < 0 ? 'text-danger' : 'text-warning'}`}>
                {final_score.toFixed(4)}
              </div>
              <ProgressBar
                now={((final_score + 1) * 50)}
                className="mt-2"
                variant={final_score > 0.25 ? 'success' : final_score < -0.25 ? 'danger' : 'warning'}
                style={{ height: '6px' }}
              />
            </Card.Body>
          </Card>
        </Col>

        {/* Decision Card */}
        <Col md={6} lg={3}>
          <Card className="summary-card">
            <Card.Body>
              <div className="summary-label">Decision</div>
              <div className={`summary-value text-${getDecisionColor(decision)}`} style={{ fontSize: '1.8rem' }}>
                {decision}
              </div>
              <div className="small text-muted mt-2">Trading Signal</div>
            </Card.Body>
          </Card>
        </Col>

        {/* Sentiment Card */}
        <Col md={6} lg={3}>
          <Card className="summary-card">
            <Card.Body>
              <div className="summary-label">Avg Sentiment (50%)</div>
              <div className={`summary-value ${avg_sentiment > 0.1 ? 'text-success' : avg_sentiment < -0.1 ? 'text-danger' : 'text-warning'}`}>
                {avg_sentiment.toFixed(4)}
              </div>
              <div className="small text-muted mt-2">{getSentimentLabel(avg_sentiment)}</div>
            </Card.Body>
          </Card>
        </Col>

        {/* Source Weight Card */}
        <Col md={6} lg={3}>
          <Card className="summary-card">
            <Card.Body>
              <div className="summary-label">Avg Source Weight (30%)</div>
              <div className="summary-value text-info">
                {avg_source_weight.toFixed(4)}
              </div>
              <ProgressBar
                now={avg_source_weight * 100}
                className="mt-2"
                variant="info"
                style={{ height: '6px' }}
              />
            </Card.Body>
          </Card>
        </Col>

        {/* Keyword Signal Card */}
        <Col md={6} lg={3}>
          <Card className="summary-card">
            <Card.Body>
              <div className="summary-label">Keyword Signal (20%)</div>
              <div className={`summary-value ${avg_keyword_signal > 0 ? 'text-success' : avg_keyword_signal < 0 ? 'text-danger' : 'text-secondary'}`}>
                {avg_keyword_signal.toFixed(4)}
              </div>
              <ProgressBar
                now={((avg_keyword_signal + 1) * 50)}
                className="mt-2"
                variant={avg_keyword_signal > 0 ? 'success' : avg_keyword_signal < 0 ? 'danger' : 'secondary'}
                style={{ height: '6px' }}
              />
            </Card.Body>
          </Card>
        </Col>

        {/* Articles Count Card */}
        <Col md={6} lg={3}>
          <Card className="summary-card">
            <Card.Body>
              <div className="summary-label">Articles Analyzed</div>
              <div className="summary-value">
                {articles_analyzed}
              </div>
              <div className="small text-muted mt-2">News sources</div>
            </Card.Body>
          </Card>
        </Col>
      </Row>

      {/* Scoring Breakdown */}
      <Card className="mb-4">
        <Card.Header>
          <Card.Title className="mb-0">
            <i className="fas fa-calculator"></i> Scoring Breakdown
          </Card.Title>
        </Card.Header>
        <Card.Body>
          <div className="scoring-breakdown">
            <div className="breakdown-row">
              <div className="breakdown-label">Sentiment Score (50% weight)</div>
              <div className="breakdown-value">
                <span className="value-number">{(avg_sentiment * 0.5).toFixed(4)}</span>
                <span className="value-calc"> = {avg_sentiment.toFixed(4)} × 0.5</span>
              </div>
            </div>
            <div className="breakdown-row">
              <div className="breakdown-label">Source Weight (30% weight)</div>
              <div className="breakdown-value">
                <span className="value-number">{(avg_source_weight * 0.3).toFixed(4)}</span>
                <span className="value-calc"> = {avg_source_weight.toFixed(4)} × 0.3</span>
              </div>
            </div>
            <div className="breakdown-row">
              <div className="breakdown-label">Keyword Signal (20% weight)</div>
              <div className="breakdown-value">
                <span className="value-number">{(avg_keyword_signal * 0.2).toFixed(4)}</span>
                <span className="value-calc"> = {avg_keyword_signal.toFixed(4)} × 0.2</span>
              </div>
            </div>
            <hr />
            <div className="breakdown-row fw-bold">
              <div className="breakdown-label">Final Score</div>
              <div className={`breakdown-value text-${final_score > 0.25 ? 'success' : final_score < -0.25 ? 'danger' : 'warning'}`}>
                {final_score.toFixed(4)}
              </div>
            </div>
          </div>
          <div className="mt-3 small text-muted">
            <p className="mb-1"><strong>Decision Thresholds:</strong></p>
            <p className="mb-0">BUY: score &gt; 0.25 | SELL: score &lt; -0.25 | HOLD: otherwise</p>
          </div>
        </Card.Body>
      </Card>
    </>
  );
};

export default AnalysisSummary;

