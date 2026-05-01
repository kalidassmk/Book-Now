/**
 * ArticleBreakdown.jsx
 * Displays detailed article-by-article breakdown with expandable rows
 */

import React, { useState } from 'react';
import { Card, Table, Badge, Accordion, Button, Row, Col } from 'react-bootstrap';

const ArticleBreakdown = ({ analysis }) => {
  const { articles } = analysis;
  const [expandedIndex, setExpandedIndex] = useState(null);

  const getScoreColor = (score) => {
    if (score > 0.25) return 'success';
    if (score < -0.25) return 'danger';
    return 'warning';
  };

  const getSentimentBadge = (score) => {
    if (score > 0.1) return <Badge bg="success">Positive</Badge>;
    if (score < -0.1) return <Badge bg="danger">Negative</Badge>;
    return <Badge bg="secondary">Neutral</Badge>;
  };

  return (
    <Card className="mb-4">
      <Card.Header>
        <Card.Title className="mb-0">
          <i className="fas fa-newspaper"></i> Article Breakdown ({articles.length} articles)
        </Card.Title>
      </Card.Header>
      <Card.Body className="p-0">
        {articles.length > 0 ? (
          <Accordion className="article-accordion">
            {articles.map((article, idx) => (
              <Accordion.Item key={idx} eventKey={idx} className="article-item">
                <Accordion.Header className="article-header">
                  <Row className="w-100 align-items-center g-2">
                    <Col md={5} className="col-title">
                      <div className="article-title">{article.title}</div>
                      <div className="text-muted small">{article.source}</div>
                    </Col>
                    <Col md={7} className="col-scores">
                      <div className="score-badges d-flex gap-2 flex-wrap">
                        <Badge bg="info">Sentiment: {article.sentiment_score.toFixed(3)}</Badge>
                        <Badge bg="secondary">Weight: {article.source_weight.toFixed(3)}</Badge>
                        <Badge bg="secondary">Keywords: {article.keyword_signal.toFixed(3)}</Badge>
                        <Badge bg={getScoreColor(article.final_article_score)} className="fs-6">
                          {article.final_article_score.toFixed(3)}
                        </Badge>
                      </div>
                    </Col>
                  </Row>
                </Accordion.Header>

                <Accordion.Body className="article-body">
                  <Row className="g-3">
                    {/* Content Snippet */}
                    <Col md={8}>
                      <h6>Content</h6>
                      <p className="text-muted small" style={{ maxHeight: '200px', overflowY: 'auto' }}>
                        {article.content_snippet || 'No content available'}
                      </p>

                      <h6 className="mt-3">Keywords Detected</h6>
                      <div>
                        {article.keywords_detected && article.keywords_detected.length > 0 ? (
                          article.keywords_detected.map((keyword, idx) => (
                            <Badge key={idx} bg="light" text="dark" className="me-2 mb-2">
                              <i className="fas fa-tag"></i> {keyword}
                            </Badge>
                          ))
                        ) : (
                          <p className="text-muted small">No keywords detected</p>
                        )}
                      </div>

                      <h6 className="mt-3">Coins Mentioned</h6>
                      <div>
                        {article.coin_mentions && article.coin_mentions.length > 0 ? (
                          article.coin_mentions.map((coin, idx) => (
                            <Badge key={idx} bg="warning" text="dark" className="me-2 mb-2">
                              <i className="fas fa-coins"></i> {coin}
                            </Badge>
                          ))
                        ) : (
                          <p className="text-muted small">No coins mentioned</p>
                        )}
                      </div>
                    </Col>

                    {/* Metadata */}
                    <Col md={4}>
                      <Card className="bg-light">
                        <Card.Body>
                          <h6 className="card-title">Article Details</h6>

                          <div className="detail-item mb-2">
                            <strong className="small">Source</strong>
                            <div className="small text-muted">{article.source}</div>
                          </div>

                          <div className="detail-item mb-2">
                            <strong className="small">Published</strong>
                            <div className="small text-muted">
                              {article.published_at
                                ? new Date(article.published_at * 1000).toLocaleString()
                                : 'Unknown'
                              }
                            </div>
                          </div>

                          <h6 className="card-title mt-3">Scoring</h6>

                          <div className="detail-item mb-2">
                            <strong className="small">Sentiment Score</strong>
                            <div className={`small ${article.sentiment_score > 0 ? 'text-success' : article.sentiment_score < 0 ? 'text-danger' : ''}`}>
                              {article.sentiment_score.toFixed(4)} {getSentimentBadge(article.sentiment_score)}
                            </div>
                          </div>

                          <div className="detail-item mb-2">
                            <strong className="small">Source Weight</strong>
                            <div className="small text-info">{article.source_weight.toFixed(4)}</div>
                          </div>

                          <div className="detail-item mb-2">
                            <strong className="small">Keyword Signal</strong>
                            <div className={`small ${article.keyword_signal > 0 ? 'text-success' : article.keyword_signal < 0 ? 'text-danger' : ''}`}>
                              {article.keyword_signal.toFixed(4)}
                            </div>
                          </div>

                          <hr />

                          <div className="detail-item">
                            <strong className="small">Final Article Score</strong>
                            <div className={`small text-${getScoreColor(article.final_article_score)} fw-bold`}>
                              {article.final_article_score.toFixed(4)}
                            </div>
                          </div>

                          <Button
                            size="sm"
                            variant="primary"
                            className="w-100 mt-3"
                            href={article.url}
                            target="_blank"
                            rel="noopener noreferrer"
                          >
                            <i className="fas fa-external-link-alt"></i> Read Article
                          </Button>
                        </Card.Body>
                      </Card>
                    </Col>
                  </Row>
                </Accordion.Body>
              </Accordion.Item>
            ))}
          </Accordion>
        ) : (
          <div className="p-4 text-center text-muted">
            <i className="fas fa-inbox fa-2x mb-3"></i>
            <p>No articles in this analysis</p>
          </div>
        )}
      </Card.Body>
    </Card>
  );
};

export default ArticleBreakdown;

