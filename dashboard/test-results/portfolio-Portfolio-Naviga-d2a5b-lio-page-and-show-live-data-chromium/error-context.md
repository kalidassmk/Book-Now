# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: portfolio.test.js >> Portfolio & Navigation >> should switch to Portfolio page and show live data
- Location: e2e/portfolio.test.js:15:5

# Error details

```
Error: expect(locator).toBeVisible() failed

Locator:  locator('text=Live Balances')
Expected: visible
Received: hidden
Timeout:  5000ms

Call log:
  - Expect "toBeVisible" with timeout 5000ms
  - waiting for locator('text=Live Balances')
    8 × locator resolved to <span class="p-card-title">Live Balances</span>
      - unexpected value "hidden"

```

# Page snapshot

```yaml
- generic [ref=e2]:
  - generic [ref=e3]:
    - generic [ref=e4]: ⚡
    - heading "Fast Trade Scanner" [level=1] [ref=e5]
  - generic [ref=e6]:
    - generic [ref=e7]: Fast Movers
    - generic [ref=e8]: "0"
  - generic [ref=e9]:
    - generic [ref=e10]: Positions
    - generic [ref=e11]: "0"
  - generic [ref=e12]:
    - generic [ref=e13]: P&L
    - generic [ref=e14]: +$0.0000
  - generic [ref=e15] [cursor=pointer]:
    - generic [ref=e16]: Wallet
    - generic [ref=e17]: "3"
  - generic [ref=e18]:
    - generic [ref=e19]: Auto
    - generic [ref=e20]: "OFF"
  - generic [ref=e21]:
    - navigation [ref=e22]:
      - button "🏠 Scanner" [ref=e23] [cursor=pointer]
      - button "💰 Portfolio" [active] [ref=e24] [cursor=pointer]
    - button "⏹ Stop" [ref=e26] [cursor=pointer]:
      - generic [ref=e27]: ⏹
      - generic [ref=e28]: Stop
    - button "🗑️ Clear Cache" [ref=e29] [cursor=pointer]:
      - generic [ref=e30]: 🗑️
      - generic [ref=e31]: Clear Cache
    - button "▶ Start" [ref=e32] [cursor=pointer]:
      - generic [ref=e33]: ▶
      - generic [ref=e34]: Start
    - generic "Spring Boot service status" [ref=e35]:
      - generic [ref=e37]: Running
    - button "USDT Mode" [ref=e38] [cursor=pointer]:
      - generic [ref=e39]: USDT Mode
  - generic [ref=e40]:
    - generic [ref=e42]: Live
    - generic [ref=e43]: 7:13:51 AM
```

# Test source

```ts
  1  | const { test, expect } = require('@playwright/test');
  2  | 
  3  | /**
  4  |  * Portfolio Page & Navigation Test Suite
  5  |  */
  6  | test.describe('Portfolio & Navigation', () => {
  7  |     
  8  |     test.beforeEach(async ({ page }) => {
  9  |         // Go to the dashboard
  10 |         await page.goto('http://localhost:3000');
  11 |         // Wait for the page to load (checking for title)
  12 |         await expect(page).toHaveTitle(/BookNow/i);
  13 |     });
  14 | 
  15 |     test('should switch to Portfolio page and show live data', async ({ page }) => {
  16 |         // 1. Click Portfolio navigation button
  17 |         await page.click('button:has-text("Portfolio")');
  18 |         
  19 |         // 2. Verify URL has portfolio hash
  20 |         await expect(page).toHaveURL('http://localhost:3000/#/portfolio');
  21 | 
  22 |         // 3. Verify Portfolio container is visible
  23 |         const portfolio = page.locator('#view-portfolio');
  24 |         const display = await portfolio.evaluate(el => getComputedStyle(el).display);
  25 |         expect(display).toBe('flex');
  26 | 
  27 |         // 3.5. Verify home is hidden
  28 |         const home = page.locator('#view-home');
  29 |         const homeDisplay = await home.evaluate(el => getComputedStyle(el).display);
  30 |         expect(homeDisplay).toBe('none');
  31 | 
  32 |         // 4. Verify opacity is 1 (checking the fix for the previous bug)
  33 |         const opacity = await portfolio.evaluate(el => getComputedStyle(el).opacity);
  34 |         expect(parseFloat(opacity)).toBeGreaterThan(0.9);
  35 | 
  36 |         // 5. Check for 'Live Balances' card
> 37 |         await expect(page.locator('text=Live Balances')).toBeVisible();
     |                                                          ^ Error: expect(locator).toBeVisible() failed
  38 |         
  39 |         // 6. Check for 'Open Orders' card
  40 |         await expect(page.locator('text=Portfolio Open Orders')).toBeVisible();
  41 |     });
  42 | 
  43 |     test('should switch back to Scanner page', async ({ page }) => {
  44 |         // Go to portfolio first
  45 |         await page.click('button:has-text("Portfolio")');
  46 |         
  47 |         // Go back to scanner
  48 |         await page.click('button:has-text("Scanner")');
  49 |         
  50 |         // Verify Scanner container is visible
  51 |         await expect(page.locator('#view-home')).toBeVisible();
  52 |         
  53 |         // Verify Portfolio container is hidden
  54 |         await expect(page.locator('#view-portfolio')).toBeHidden();
  55 |     });
  56 | 
  57 |     test('should show connection status correctly', async ({ page }) => {
  58 |         const liveLabel = page.locator('#live-label');
  59 |         // It might be 'Connecting' initially, so we wait for 'Live'
  60 |         await expect(liveLabel).toHaveText(/Live|Connecting/);
  61 |     });
  62 | });
  63 | 
```