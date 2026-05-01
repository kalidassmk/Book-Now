package com.bogoai.booknow.model;

/**
 * Result of a 2-month coin analysis.
 * Produced by CoinAnalyzer and consumed by:
 *   - TradeExecutor (buy-gate)
 *   - GET /api/v1/analyze/{symbol} (dashboard display)
 */
public class CoinAnalysisResult {

    private String  symbol;
    private double  currentPrice;

    // ── 2-month price stats (60 daily candles) ────────────────────────────────
    private double high2m;          // highest price in 2 months
    private double low2m;           // lowest price in 2 months
    private double avg2m;           // average close price over 2 months
    private double pricePosition;   // where current sits in 2m range (0–100 %)
    private int    daysAnalyzed;

    // ── Trend ─────────────────────────────────────────────────────────────────
    private double trend7d;         // % change: last-7-day avg vs prior-7-day avg
    private double trend30d;        // % change: last-30-day avg vs prior-30-day avg

    // ── Volume ────────────────────────────────────────────────────────────────
    private double vol24hUsdt;      // 24-h quote-asset (USDT) volume
    private double vol30dAvgUsdt;   // 30-day average daily USDT volume
    private double volumeRatio;     // vol24h / vol30dAvg  (>1.5 = high activity)
    private double vol7dAvgUsdt;    // 7-day average daily USDT volume
    private double rsi;             // Relative Strength Index (14d)
    private double ema50;           // 50-day EMA
    private double ema200;          // 200-day EMA

    // ── Decision ──────────────────────────────────────────────────────────────
    private int     buyScore;       // 0–7
    private String  recommendation; // STRONG_BUY | BUY | NEUTRAL | WAIT | DONT_BUY
    private String  reason;         // human-readable summary
    private boolean shouldBuy;      // final flag (score >= 4)

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public String getSymbol()               { return symbol; }
    public void   setSymbol(String v)       { symbol = v; }

    public double getCurrentPrice()         { return currentPrice; }
    public void   setCurrentPrice(double v) { currentPrice = v; }

    public double getHigh2m()               { return high2m; }
    public void   setHigh2m(double v)       { high2m = v; }

    public double getLow2m()                { return low2m; }
    public void   setLow2m(double v)        { low2m = v; }

    public double getAvg2m()                { return avg2m; }
    public void   setAvg2m(double v)        { avg2m = v; }

    public double getPricePosition()        { return pricePosition; }
    public void   setPricePosition(double v){ pricePosition = v; }

    public int getDaysAnalyzed()            { return daysAnalyzed; }
    public void setDaysAnalyzed(int v)      { daysAnalyzed = v; }

    public double getTrend7d()              { return trend7d; }
    public void   setTrend7d(double v)      { trend7d = v; }

    public double getTrend30d()             { return trend30d; }
    public void   setTrend30d(double v)     { trend30d = v; }

    public double getVol24hUsdt()           { return vol24hUsdt; }
    public void   setVol24hUsdt(double v)   { vol24hUsdt = v; }

    public double getVol30dAvgUsdt()        { return vol30dAvgUsdt; }
    public void   setVol30dAvgUsdt(double v){ vol30dAvgUsdt = v; }

    public double getVolumeRatio()          { return volumeRatio; }
    public void   setVolumeRatio(double v)  { volumeRatio = v; }

    public double getVol7dAvgUsdt()         { return vol7dAvgUsdt; }
    public void   setVol7dAvgUsdt(double v) { vol7dAvgUsdt = v; }

    public double getRsi()                  { return rsi; }
    public void   setRsi(double v)          { rsi = v; }

    public double getEma50()                { return ema50; }
    public void   setEma50(double v)        { ema50 = v; }

    public double getEma200()               { return ema200; }
    public void   setEma200(double v)       { ema200 = v; }

    public int  getBuyScore()               { return buyScore; }
    public void setBuyScore(int v)          { buyScore = v; }

    public String getRecommendation()              { return recommendation; }
    public void   setRecommendation(String v)      { recommendation = v; }

    public String getReason()               { return reason; }
    public void   setReason(String v)       { reason = v; }

    public boolean isShouldBuy()            { return shouldBuy; }
    public void    setShouldBuy(boolean v)  { shouldBuy = v; }
}
