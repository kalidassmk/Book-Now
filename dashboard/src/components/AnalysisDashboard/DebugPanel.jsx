/**
 * DebugPanel.jsx
 * Displays debugging information including raw inputs and intermediate data
 */

import React, { useState } from 'react';
import { Card, Tab, Nav, ListGroup, Badge, Accordion, Button, Row, Col } from 'react-bootstrap';

const DebugPanel = ({ analysis }) => {
  const { debug_info } = analysis;
  const [showRaw, setShowRaw] = useState(false);

  return (
    <Card className="debug-panel mb-4 border-danger">
      <Card.Header className="bg-danger text-white">
        <Card.Title className="mb-0">
          <i className="fas fa-bug"></i> Debug Information
        </Card.Title>
      </Card.Header>

      <Card.Body>
        <Tab.Container defaultActiveKey="inputs">
          <Nav variant="tabs" className="mb-3">
            <Nav.Item>
              <Nav.Link eventKey="inputs">Inputs</Nav.Link>
            </Nav.Item>
            <Nav.Item>
              <Nav.Link eventKey="urls">URLs</Nav.Link>
            </Nav.Item>
            <Nav.Item>
              <Nav.Link eventKey="raw">Raw Data</Nav.Link>
            </Nav.Item>
          </Nav>

          <Tab.Content>
            {/* Inputs Tab */}
            <Tab.Pane eventKey="inputs">
              <div className="debug-section">
                <h6>Search Query</h6>
                <code className="bg-light p-2 d-block mb-3">
                  {debug_info?.search_query || 'N/A'}
                </code>

                <h6>Analysis Summary</h6>
                <Row className="g-2">
                  <Col md={6}>
                    <Card className="bg-light">
                      <Card.Body className="p-3">
                        <div className="debug-stat">
                          <strong>Total Fetched:</strong>
                          <span className="badge bg-info ms-2">{debug_info?.total_fetched || 0}</span>
                        </div>
                        <div className="debug-stat mt-2">
                          <strong>Total Analyzed:</strong>
                          <span className="badge bg-success ms-2">{debug_info?.total_analyzed || 0}</span>
                        </div>
                      </Card.Body>
                    </Card>
                  </Col>
                  <Col md={6}>
                    <Card className="bg-light">
                      <Card.Body className="p-3">
                        <div className="debug-stat">
                          <strong>Filtered Out:</strong>
                          <span className="badge bg-warning ms-2">
                            {(debug_info?.total_fetched || 0) - (debug_info?.total_analyzed || 0)}
                          </span>
                        </div>
                        <div className="debug-stat mt-2">
                          <strong>Acceptance Rate:</strong>
                          <span className="badge bg-primary ms-2">
                            {debug_info?.total_fetched > 0
                              ? ((debug_info.total_analyzed / debug_info.total_fetched) * 100).toFixed(1)
                              : 0
                            }%
                          </span>
                        </div>
                      </Card.Body>
                    </Card>
                  </Col>
                </Row>
              </div>
            </Tab.Pane>

            {/* URLs Tab */}
            <Tab.Pane eventKey="urls">
              <div className="debug-section">
                <h6 className="mb-3">
                  <i className="fas fa-link"></i> Fetched URLs ({debug_info?.fetched_urls?.length || 0})
                </h6>
                {debug_info?.fetched_urls && debug_info.fetched_urls.length > 0 ? (
                  <Accordion className="mb-3">
                    {debug_info.fetched_urls.map((url, idx) => (
                      <Accordion.Item key={idx} eventKey={idx}>
                        <Accordion.Header>
                          <small className="text-truncate" style={{ maxWidth: '80%' }}>
                            {url}
                          </small>
                        </Accordion.Header>
                        <Accordion.Body className="p-2">
                          <code className="small d-block bg-light p-2 text-break">
                            {url}
                          </code>
                          <Button
                            size="sm"
                            variant="outline-primary"
                            className="mt-2"
                            href={url}
                            target="_blank"
                            rel="noopener noreferrer"
                          >
                            <i className="fas fa-external-link-alt"></i> Open
                          </Button>
                        </Accordion.Body>
                      </Accordion.Item>
                    ))}
                  </Accordion>
                ) : (
                  <p className="text-muted">No URLs fetched</p>
                )}

                <h6 className="mt-4 mb-3">
                  <i className="fas fa-times-circle"></i> Filtered URLs ({debug_info?.filtered_out_urls?.length || 0})
                </h6>
                {debug_info?.filtered_out_urls && debug_info.filtered_out_urls.length > 0 ? (
                  <ListGroup>
                    {debug_info.filtered_out_urls.slice(0, 10).map((url, idx) => (
                      <ListGroup.Item key={idx} className="p-2 small text-muted">
                        <i className="fas fa-ban text-danger"></i> {url.substring(0, 80)}...
                      </ListGroup.Item>
                    ))}
                    {debug_info.filtered_out_urls.length > 10 && (
                      <ListGroup.Item className="p-2 small text-center text-muted">
                        ... and {debug_info.filtered_out_urls.length - 10} more
                      </ListGroup.Item>
                    )}
                  </ListGroup>
                ) : (
                  <p className="text-muted">No URLs filtered out</p>
                )}
              </div>
            </Tab.Pane>

            {/* Raw Data Tab */}
            <Tab.Pane eventKey="raw">
              <div className="debug-section">
                <div className="alert alert-warning mb-3">
                  <i className="fas fa-info-circle"></i> This section displays the complete unformatted analysis data
                </div>

                <Button
                  size="sm"
                  variant={showRaw ? 'secondary' : 'outline-secondary'}
                  className="mb-3"
                  onClick={() => setShowRaw(!showRaw)}
                >
                  <i className={`fas fa-${showRaw ? 'eye-slash' : 'eye'}`}></i> {showRaw ? 'Hide' : 'Show'} Raw Data
                </Button>

                {showRaw && (
                  <pre
                    className="bg-dark text-light p-3"
                    style={{
                      borderRadius: '4px',
                      maxHeight: '400px',
                      overflowY: 'auto',
                      fontSize: '11px'
                    }}
                  >
                    {JSON.stringify(analysis, null, 2)}
                  </pre>
                )}

                <div className="mt-3">
                  <p className="small text-muted">
                    <i className="fas fa-copy"></i> Raw JSON data can be copied from the above section for further analysis
                  </p>
                </div>
              </div>
            </Tab.Pane>
          </Tab.Content>
        </Tab.Container>

        {/* Debug Type Indicators */}
        <hr />
        <div className="mt-3">
          <h6 className="small">Debug Indicators</h6>
          <div className="small">
            {(!debug_info?.search_query || debug_info.search_query === '') && (
              <Badge bg="warning" className="me-2 mb-2">
                <i className="fas fa-exclamation-triangle"></i> No Search Query
              </Badge>
            )}
            {debug_info?.total_fetched === 0 && (
              <Badge bg="danger" className="me-2 mb-2">
                <i className="fas fa-times-circle"></i> No URLs Fetched
              </Badge>
            )}
            {debug_info?.total_analyzed === 0 && (
              <Badge bg="danger" className="me-2 mb-2">
                <i className="fas fa-times-circle"></i> No Articles Analyzed
              </Badge>
            )}
            {debug_info?.filtered_out_urls && debug_info.filtered_out_urls.length > (debug_info?.total_fetched || 0) * 0.8 && (
              <Badge bg="warning" className="me-2 mb-2">
                <i className="fas fa-exclamation-triangle"></i> High Filtering Rate
              </Badge>
            )}
          </div>
        </div>
      </Card.Body>
    </Card>
  );
};

export default DebugPanel;

