"""
test_analysis_api.py
Simple test script to verify the Analysis Debug Dashboard API works correctly.
Run with: python test_analysis_api.py
"""

import asyncio
import json
import sys
import time
from typing import Dict, Any

async def test_api():
    """Test the API endpoints."""
    import aiohttp

    BASE_URL = 'http://localhost:8000'

    tests_passed = 0
    tests_failed = 0

    async with aiohttp.ClientSession() as session:
        # Test 1: Health Check
        print("🏥 Test 1: Health Check")
        try:
            async with session.get(f'{BASE_URL}/health', timeout=aiohttp.ClientTimeout(total=5)) as resp:
                if resp.status == 200:
                    data = await resp.json()
                    print(f"   ✅ PASS - API is healthy: {data['status']}")
                    tests_passed += 1
                else:
                    print(f"   ❌ FAIL - Status: {resp.status}")
                    tests_failed += 1
        except Exception as e:
            print(f"   ❌ FAIL - {str(e)}")
            print(f"   💡 Make sure API is running: python -m uvicorn api:app --reload")
            tests_failed += 1
            return tests_passed, tests_failed

        # Test 2: Get All Analyses
        print("\n📋 Test 2: Get All Analyses")
        try:
            async with session.get(f'{BASE_URL}/analysis') as resp:
                if resp.status == 200:
                    data = await resp.json()
                    if data.get('success'):
                        count = data.get('count', 0)
                        print(f"   ✅ PASS - Found {count} coins")
                        tests_passed += 1

                        if count > 0:
                            # Use first coin for further tests
                            first_coin = data['analyses'][0]['coin']
                            print(f"   ℹ️  Using '{first_coin}' for detailed tests")
                        else:
                            print(f"   ⚠️  WARNING - No coins found. Run analyzer first!")
                    else:
                        print(f"   ❌ FAIL - {data.get('message')}")
                        tests_failed += 1
                else:
                    print(f"   ❌ FAIL - Status: {resp.status}")
                    tests_failed += 1
        except Exception as e:
            print(f"   ❌ FAIL - {str(e)}")
            tests_failed += 1

        # Test 3: Get Statistics
        print("\n📊 Test 3: Get Statistics")
        try:
            async with session.get(f'{BASE_URL}/stats') as resp:
                if resp.status == 200:
                    data = await resp.json()
                    if data.get('success'):
                        stats = data.get('stats', {})
                        print(f"   ✅ PASS - Stats retrieved")
                        print(f"      BUY signals: {stats.get('buy', 0)}")
                        print(f"      SELL signals: {stats.get('sell', 0)}")
                        print(f"      HOLD signals: {stats.get('hold', 0)}")
                        print(f"      Avg score: {stats.get('avg_score', 0):.4f}")
                        tests_passed += 1
                    else:
                        print(f"   ❌ FAIL - {data.get('message')}")
                        tests_failed += 1
                else:
                    print(f"   ❌ FAIL - Status: {resp.status}")
                    tests_failed += 1
        except Exception as e:
            print(f"   ❌ FAIL - {str(e)}")
            tests_failed += 1

        # Test 4: Get Detailed Analysis (if coins exist)
        print("\n🔍 Test 4: Get Detailed Analysis")
        try:
            # Get first coin
            async with session.get(f'{BASE_URL}/analysis') as resp:
                data = await resp.json()
                if data.get('count', 0) > 0:
                    coin = data['analyses'][0]['coin']

                    async with session.get(f'{BASE_URL}/analysis/{coin}') as detail_resp:
                        if detail_resp.status == 200:
                            detail_data = await detail_resp.json()
                            if detail_data.get('success'):
                                analysis = detail_data.get('data', {})
                                print(f"   ✅ PASS - Detailed analysis for {coin}")
                                print(f"      Score: {analysis.get('final_score', 0):.4f}")
                                print(f"      Decision: {analysis.get('decision', 'N/A')}")
                                print(f"      Articles: {analysis.get('articles_analyzed', 0)}")
                                tests_passed += 1
                            else:
                                print(f"   ❌ FAIL - {detail_data.get('message')}")
                                tests_failed += 1
                        else:
                            print(f"   ❌ FAIL - Status: {detail_resp.status}")
                            tests_failed += 1
                else:
                    print(f"   ⏭️  SKIP - No coins available")
        except Exception as e:
            print(f"   ❌ FAIL - {str(e)}")
            tests_failed += 1

        # Test 5: Get Articles (if coins exist)
        print("\n📰 Test 5: Get Articles")
        try:
            async with session.get(f'{BASE_URL}/analysis') as resp:
                data = await resp.json()
                if data.get('count', 0) > 0:
                    coin = data['analyses'][0]['coin']

                    async with session.get(f'{BASE_URL}/analysis/{coin}/articles?skip=0&limit=5') as articles_resp:
                        if articles_resp.status == 200:
                            articles_data = await articles_resp.json()
                            if articles_data.get('success'):
                                total = articles_data.get('total', 0)
                                returned = articles_data.get('returned', 0)
                                print(f"   ✅ PASS - Articles endpoint working")
                                print(f"      Total articles: {total}")
                                print(f"      Returned: {returned}")
                                tests_passed += 1
                            else:
                                print(f"   ❌ FAIL - {articles_data.get('message')}")
                                tests_failed += 1
                        else:
                            print(f"   ❌ FAIL - Status: {articles_resp.status}")
                            tests_failed += 1
                else:
                    print(f"   ⏭️  SKIP - No coins available")
        except Exception as e:
            print(f"   ❌ FAIL - {str(e)}")
            tests_failed += 1

        # Test 6: Get Debug Info (if coins exist)
        print("\n🐛 Test 6: Get Debug Info")
        try:
            async with session.get(f'{BASE_URL}/analysis') as resp:
                data = await resp.json()
                if data.get('count', 0) > 0:
                    coin = data['analyses'][0]['coin']

                    async with session.get(f'{BASE_URL}/analysis/debug/{coin}') as debug_resp:
                        if debug_resp.status == 200:
                            debug_data = await debug_resp.json()
                            if debug_data.get('success'):
                                debug_info = debug_data.get('debug_info', {})
                                print(f"   ✅ PASS - Debug info retrieved")
                                print(f"      Search query: {debug_info.get('search_query', 'N/A')}")
                                print(f"      Total fetched: {debug_info.get('total_fetched', 0)}")
                                print(f"      Total analyzed: {debug_info.get('total_analyzed', 0)}")
                                tests_passed += 1
                            else:
                                print(f"   ❌ FAIL - {debug_data.get('message')}")
                                tests_failed += 1
                        else:
                            print(f"   ❌ FAIL - Status: {debug_resp.status}")
                            tests_failed += 1
                else:
                    print(f"   ⏭️  SKIP - No coins available")
        except Exception as e:
            print(f"   ❌ FAIL - {str(e)}")
            tests_failed += 1

    return tests_passed, tests_failed

async def main():
    """Main test runner."""
    print("🚀 Analysis Dashboard API Test Suite")
    print("=" * 50)
    print()

    passed, failed = await test_api()

    print()
    print("=" * 50)
    print(f"📊 Test Results: {passed} passed, {failed} failed")
    print("=" * 50)

    if failed == 0 and passed > 0:
        print("✅ All tests passed! Dashboard is ready to use.")
        print()
        print("📱 Access the dashboard at:")
        print("   http://localhost:3000/analysis-dashboard")
        return 0
    elif failed > 0:
        print("❌ Some tests failed. Check the errors above.")
        return 1
    else:
        print("⚠️  Could not connect to API. Make sure:")
        print("   1. Redis is running: redis-server")
        print("   2. API is running: python -m uvicorn api:app --reload")
        print("   3. Analyzer has run: python main.py run-once")
        return 1

if __name__ == '__main__':
    exit_code = asyncio.run(main())
    sys.exit(exit_code)

