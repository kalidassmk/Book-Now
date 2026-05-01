/**
 * CoinList.jsx
 * Elite Trading Radar with Advanced Filtering, Sorting, and Column Management
 * Features: Multi-column sort, advanced filters, column visibility toggle
 */

import React, { useState, useMemo } from 'react';
import { Card, Table, Badge, Form, Row, Col, InputGroup, Button } from 'react-bootstrap';
import './CoinList.css';

const CoinList = ({ coins, selectedCoin, onSelectCoin, debugMode }) => {
  const [searchTerm, setSearchTerm] = useState('');
  const [filterSignal, setFilterSignal] = useState('ALL');
  const [filterSentiment, setFilterSentiment] = useState('ALL');
  const [filterPrice, setFilterPrice] = useState('ALL');
  const [sortConfig, setSortConfig] = useState([{ key: 'score', direction: 'desc' }]);
  const [visibleColumns, setVisibleColumns] = useState({
    symbol: true,
    signal: true,
    sentiment: true,
    analysis: true,
    price: true,
    volume: true
  });
  const [showColumnFilter, setShowColumnFilter] = useState(false);

  // Multi-column sorting handler
  const handleSort = (key) => {
    let newSort = [...sortConfig];
    const existingIndex = newSort.findIndex(s => s.key === key);

    if (existingIndex > -1) {
      // Toggle direction if already sorted by this column
      if (newSort[existingIndex].direction === 'desc') {
        newSort[existingIndex].direction = 'asc';
      } else {
        // Remove if was ascending
        newSort.splice(existingIndex, 1);
      }
    } else {
      // Add new sort (max 3 columns)
      if (newSort.length < 3) {
        newSort.unshift({ key, direction: 'desc' });
      }
    }

    setSortConfig(newSort.length > 0 ? newSort : [{ key: 'score', direction: 'desc' }]);
  };

  const getDecisionWeight = (decision) => {
    if (decision?.includes('BUY')) return 3;
    if (decision?.includes('HOLD')) return 2;
    return 1;
  };

  const getSentimentScore = (sentiment) => {
    if (sentiment?.includes('Highly Bullish')) return 5;
    if (sentiment?.includes('Bullish')) return 4;
    if (sentiment?.includes('Neutral')) return 3;
    if (sentiment?.includes('Bearish')) return 2;
    if (sentiment?.includes('Highly Bearish')) return 1;
    return 0;
  };

  // Multi-column sorting with memoization
  const sortedCoins = useMemo(() => {
    return [...coins].sort((a, b) => {
      for (let sort of sortConfig) {
        let aVal, bVal;
        let direction = sort.direction;

        switch (sort.key) {
          case 'coin':
            aVal = a.coin || '';
            bVal = b.coin || '';
            break;
          case 'decision':
            aVal = getDecisionWeight(a.decision);
            bVal = getDecisionWeight(b.decision);
            break;
          case 'score':
            aVal = parseFloat(a.score) || 0;
            bVal = parseFloat(b.score) || 0;
            break;
          case 'price':
            aVal = parseFloat(a.price) || 0;
            bVal = parseFloat(b.price) || 0;
            break;
          case 'sentiment':
            aVal = getSentimentScore(a.sentiment);
            bVal = getSentimentScore(b.sentiment);
            break;
          case 'volume':
            aVal = parseFloat(a.volume) || 0;
            bVal = parseFloat(b.volume) || 0;
            break;
          default:
            continue;
        }

        if (aVal < bVal) return direction === 'asc' ? -1 : 1;
        if (aVal > bVal) return direction === 'asc' ? 1 : -1;
      }
      return 0;
    });
  }, [coins, sortConfig]);

  // Advanced filtering with memoization
  const filteredCoins = useMemo(() => {
    return sortedCoins.filter(c => {
      const matchesSearch = c.coin.toLowerCase().includes(searchTerm.toLowerCase());
      const matchesSignal = filterSignal === 'ALL' || (c.decision && c.decision.includes(filterSignal));
      const matchesSentiment = filterSentiment === 'ALL' || (c.sentiment && c.sentiment.includes(filterSentiment));

      let matchesPrice = true;
      if (filterPrice !== 'ALL') {
        const price = parseFloat(c.price) || 0;
        if (filterPrice === 'LOW' && price > 0.01) matchesPrice = false;
        if (filterPrice === 'MID' && (price < 0.01 || price > 1)) matchesPrice = false;
        if (filterPrice === 'HIGH' && price < 1) matchesPrice = false;
      }

      return matchesSearch && matchesSignal && matchesSentiment && matchesPrice;
    });
  }, [sortedCoins, searchTerm, filterSignal, filterSentiment, filterPrice]);

  const SortIcon = ({ column }) => {
    const sort = sortConfig.find(s => s.key === column);
    if (!sort) return <i className="fas fa-sort text-muted ms-2 small opacity-50"></i>;
    return sort.direction === 'asc'
      ? <i className="fas fa-sort-up ms-2 text-info"></i>
      : <i className="fas fa-sort-down ms-2 text-info"></i>;
  };

  const toggleColumn = (col) => {
    setVisibleColumns(prev => ({
      ...prev,
      [col]: !prev[col]
    }));
  };

  return (
    <Card className="coin-list-card bg-dark text-light shadow-lg border-0 rounded-0">
      <Card.Header className="bg-dark border-bottom border-secondary py-3">
        {/* Search and Quick Stats Row */}
        <Row className="g-2 mb-2">
          <Col md={5}>
            <InputGroup size="sm">
              <InputGroup.Text className="bg-secondary text-light border-0">
                <i className="fas fa-search"></i>
              </InputGroup.Text>
              <Form.Control
                className="bg-dark text-light border-secondary shadow-none"
                placeholder="SEARCH SYMBOL..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
              />
            </InputGroup>
          </Col>
          <Col md={7} className="d-flex gap-2">
            <span className="badge bg-info text-dark align-self-center" style={{ fontSize: '0.75rem' }}>
              {filteredCoins.length}/{coins.length} COINS
            </span>
            <Button
              size="sm"
              variant="outline-secondary"
              onClick={() => setShowColumnFilter(!showColumnFilter)}
              className="ms-auto"
            >
              <i className="fas fa-sliders-h me-1"></i> COLUMNS
            </Button>
          </Col>
        </Row>

        {/* Advanced Filters Row */}
        <Row className="g-2">
          <Col md={3}>
            <Form.Select
              size="sm"
              className="bg-dark text-light border-secondary shadow-none"
              value={filterSignal}
              onChange={(e) => setFilterSignal(e.target.value)}
            >
              <option value="ALL">ALL SIGNALS</option>
              <option value="BUY">🔺 BUY SIGNALS</option>
              <option value="HOLD">➡️ HOLD</option>
              <option value="SELL">🔻 SELL</option>
            </Form.Select>
          </Col>
          <Col md={3}>
            <Form.Select
              size="sm"
              className="bg-dark text-light border-secondary shadow-none"
              value={filterSentiment}
              onChange={(e) => setFilterSentiment(e.target.value)}
            >
              <option value="ALL">ALL SENTIMENTS</option>
              <option value="Highly Bullish">⭐ Highly Bullish</option>
              <option value="Bullish">📈 Bullish</option>
              <option value="Neutral">➡️ Neutral</option>
              <option value="Bearish">📉 Bearish</option>
              <option value="Highly Bearish">❌ Highly Bearish</option>
            </Form.Select>
          </Col>
          <Col md={3}>
            <Form.Select
              size="sm"
              className="bg-dark text-light border-secondary shadow-none"
              value={filterPrice}
              onChange={(e) => setFilterPrice(e.target.value)}
            >
              <option value="ALL">ALL PRICES</option>
              <option value="LOW">&lt; $0.01 (Micro)</option>
              <option value="MID">$0.01 - $1 (Mid)</option>
              <option value="HIGH">&gt; $1 (Large)</option>
            </Form.Select>
          </Col>
          <Col md={3}>
            <Button
              size="sm"
              variant="outline-danger"
              className="w-100"
              onClick={() => {
                setSearchTerm('');
                setFilterSignal('ALL');
                setFilterSentiment('ALL');
                setFilterPrice('ALL');
                setSortConfig([{ key: 'score', direction: 'desc' }]);
              }}
            >
              <i className="fas fa-redo me-1"></i> RESET
            </Button>
          </Col>
        </Row>

        {/* Column Visibility Filter */}
        {showColumnFilter && (
          <Row className="g-2 mt-2 p-2 bg-secondary rounded" style={{ fontSize: '0.75rem' }}>
            {Object.keys(visibleColumns).map(col => (
              <Col key={col} xs="auto">
                <Form.Check
                  type="checkbox"
                  id={`col-${col}`}
                  label={col.toUpperCase()}
                  checked={visibleColumns[col]}
                  onChange={() => toggleColumn(col)}
                  className="text-uppercase"
                />
              </Col>
            ))}
          </Row>
        )}
      </Card.Header>

      <Card.Body className="p-0">
        <div className="table-responsive scanner-wrap" style={{ maxHeight: '800px', overflowY: 'auto' }}>
          <Table hover variant="dark" className="align-middle mb-0 custom-trading-table">
            <thead className="bg-dark text-muted sticky-top" style={{ zIndex: 10 }}>
              <tr style={{ fontSize: '0.75rem', letterSpacing: '1px' }}>
                {visibleColumns.symbol && (
                  <th onClick={() => handleSort('coin')} className="cursor-pointer py-3 ps-4 text-uppercase border-secondary" title="Click to sort">
                    SYMBOL <SortIcon column="coin" />
                  </th>
                )}
                {visibleColumns.signal && (
                  <th onClick={() => handleSort('decision')} className="cursor-pointer py-3 text-uppercase border-secondary" title="Click to sort">
                    SIGNAL <SortIcon column="decision" />
                  </th>
                )}
                {visibleColumns.sentiment && (
                  <th onClick={() => handleSort('sentiment')} className="cursor-pointer py-3 text-uppercase border-secondary" title="Click to sort">
                    SENTIMENT <SortIcon column="sentiment" />
                  </th>
                )}
                {visibleColumns.analysis && (
                  <th onClick={() => handleSort('score')} className="cursor-pointer py-3 text-uppercase border-secondary" title="Click to sort by analysis score">
                    ANALYSIS <SortIcon column="score" />
                  </th>
                )}
                {visibleColumns.price && (
                  <th onClick={() => handleSort('price')} className="cursor-pointer py-3 text-uppercase border-secondary" title="Click to sort">
                    PRICE <SortIcon column="price" />
                  </th>
                )}
                {visibleColumns.volume && (
                  <th onClick={() => handleSort('volume')} className=\"cursor-pointer py-3 text-uppercase border-secondary\" title=\"Click to sort\">
                    VOLUME <SortIcon column=\"volume\" />
                  </th>
                )}
                <th className=\"py-3 text-uppercase border-secondary\">
                  TRADE
                </th>
              </tr>
            </thead>
            <tbody style={{ borderTop: 'none' }}>
              {filteredCoins.map((c) => (
                <tr 
                  key={c.coin} 
                  className={`border-secondary ${selectedCoin === c.coin ? 'bg-secondary' : ''}`}
                  onClick={() => onSelectCoin(c.coin)}
                  style={{ cursor: 'pointer', transition: 'background 0.2s' }}
                >
                  {visibleColumns.symbol && (
                    <td className="ps-4">
                      <div className="fw-bold fs-5 text-white">{c.coin}</div>
                    </td>
                  )}
                  {visibleColumns.signal && (
                    <td>
                      <Badge className="px-3 py-2 rounded-1" style={{
                        backgroundColor: c.decision?.includes('BUY') ? '#1a3a1a' : c.decision?.includes('SELL') ? '#3a1a1a' : '#2c2c2c',
                        color: c.decision?.includes('BUY') ? '#4cff4c' : c.decision?.includes('SELL') ? '#ff4d4d' : '#cccccc',
                        fontSize: '0.7rem',
                        border: `1px solid ${c.decision?.includes('BUY') ? '#1a6b1a' : c.decision?.includes('SELL') ? '#6b1a1a' : '#4d4d4d'}`
                      }}>
                        {c.decision || 'HOLD'}
                      </Badge>
                    </td>
                  )}
                  {visibleColumns.sentiment && (
                    <td>
                      <Badge style={{
                        backgroundColor: c.sentiment?.includes('Highly Bullish') ? '#004d00' :
                                       c.sentiment?.includes('Bullish') ? '#1a661a' :
                                       c.sentiment?.includes('Neutral') ? '#4d4d00' :
                                       c.sentiment?.includes('Bearish') ? '#661a1a' : '#3a0000',
                        color: c.sentiment?.includes('Highly Bullish') ? '#66ff66' :
                              c.sentiment?.includes('Bullish') ? '#99ff99' :
                              c.sentiment?.includes('Neutral') ? '#ffff99' :
                              c.sentiment?.includes('Bearish') ? '#ff9999' : '#ff4d4d',
                        fontSize: '0.65rem',
                        padding: '0.35rem 0.6rem'
                      }}>
                        {c.sentiment || 'No Data'}
                      </Badge>
                    </td>
                  )}
                  {visibleColumns.analysis && (
                    <td>
                      <div className="d-flex flex-column align-items-start">
                        <div className="px-2 py-0 rounded-1" style={{
                          border: `1px solid ${(c.score || 0) > 0.25 ? '#4cff4c' : (c.score || 0) < -0.25 ? '#ff4d4d' : '#ffff99'}`,
                          color: `${(c.score || 0) > 0.25 ? '#4cff4c' : (c.score || 0) < -0.25 ? '#ff4d4d' : '#ffff99'}`,
                          fontSize: '0.65rem',
                          fontWeight: 'bold',
                          letterSpacing: '0.5px'
                        }}>
                          {(c.score || 0).toFixed(3)}
                        </div>
                        <div className="text-muted small mt-1" style={{ fontSize: '0.7rem' }}>
                          {c.indicators_passed || '0'}/7
                        </div>
                      </div>
                    </td>
                  )}
                  {visibleColumns.price && (
                    <td>
                      <div className="fw-bold text-light fs-6">
                        {(c.price || 0.041000).toFixed(6)}
                      </div>
                    </td>
                  )}
                  {visibleColumns.volume && (
                    <td>
                      <div className=\"text-muted small\" style={{ fontSize: '0.7rem' }}>
                        {c.volume ? (parseFloat(c.volume) / 1000000).toFixed(2) + 'M' : 'N/A'}
                      </div>
                    </td>
                  )}
                  <td>
                    <Button 
                      variant=\"outline-warning\" 
                      size=\"sm\"
                      className=\"btn-binance-logo py-1 px-2\"
                      onClick={(e) => {
                        e.stopPropagation();
                        window.open(`https://www.binance.com/en-IN/trade/${c.coin}_USDT?_from=markets&type=spot`, '_blank');
                      }}
                      title=\"Trade on Binance\"
                      style={{ 
                        backgroundColor: 'rgba(240, 185, 11, 0.1)', 
                        borderColor: '#f0b90b',
                        color: '#f0b90b'
                      }}
                    >
                      <img src=\"https://bin.bnbstatic.com/static/images/common/favicon.ico\" width=\"16\" height=\"16\" alt=\"Binance\" />
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </Table>
        </div>
      </Card.Body>

      {/* Footer with indicators */}
      <Card.Footer className="bg-dark border-top border-secondary text-muted py-2" style={{ fontSize: '0.7rem' }}>
        <Row className="g-3">
          <Col xs="auto">
            <span>
              <i className="fas fa-circle text-success me-1"></i>
              BUY: {filteredCoins.filter(c => c.decision?.includes('BUY')).length}
            </span>
          </Col>
          <Col xs="auto">
            <span>
              <i className="fas fa-circle text-warning me-1"></i>
              HOLD: {filteredCoins.filter(c => c.decision?.includes('HOLD')).length}
            </span>
          </Col>
          <Col xs="auto">
            <span>
              <i className="fas fa-circle text-danger me-1"></i>
              SELL: {filteredCoins.filter(c => c.decision?.includes('SELL')).length}
            </span>
          </Col>
          <Col xs="auto" className="ms-auto">
            {sortConfig.length > 1 && (
              <span className="text-info">
                <i className="fas fa-sort me-1"></i>
                Sorting by {sortConfig.length} columns
              </span>
            )}
          </Col>
        </Row>
      </Card.Footer>
    </Card>
  );
};

export default CoinList;
