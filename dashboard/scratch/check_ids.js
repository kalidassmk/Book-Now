const fs = require('fs');
const html = fs.readFileSync('public/index.html', 'utf8');
const ids = html.match(/id="([^"]+)"/g);
if (ids) {
    const idMap = {};
    ids.forEach(idStr => {
        const id = idStr.match(/id="([^"]+)"/)[1];
        idMap[id] = (idMap[id] || 0) + 1;
    });
    const duplicates = Object.entries(idMap).filter(([id, count]) => count > 1);
    if (duplicates.length > 0) {
        console.log('Duplicate IDs found:');
        duplicates.forEach(([id, count]) => console.log(`${id}: ${count}`));
    } else {
        console.log('No duplicate IDs found.');
    }
} else {
    console.log('No IDs found.');
}

// ── Added Task: Create e2e directory structure ──
if (!fs.existsSync('e2e')) {
    fs.mkdirSync('e2e');
    fs.writeFileSync('e2e/README.md', '# E2E Tests\n\nRun with `npx playwright test` if installed.');
    fs.writeFileSync('e2e/smoke.test.js', `
const { test, expect } = require('@playwright/test');

test('Dashboard loads and navigates', async ({ page }) => {
    await page.goto('http://localhost:3000');
    await expect(page).toHaveTitle(/BookNow/);
    
    // Test navigation
    await page.click('text=Portfolio');
    await expect(page.locator('#view-portfolio')).toBeVisible();
    
    await page.click('text=Scanner');
    await expect(page.locator('#view-home')).toBeVisible();
});
`);
}
