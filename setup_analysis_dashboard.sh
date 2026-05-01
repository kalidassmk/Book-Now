#!/bin/bash
# setup_analysis_dashboard.sh
# Complete setup script for the Analysis Debug Dashboard

set -e

echo "🚀 Crypto News Analysis Dashboard - Setup Script"
echo "=================================================="
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check prerequisites
echo "📋 Checking prerequisites..."

# Check Python
if ! command -v python3 &> /dev/null; then
    echo -e "${RED}❌ Python 3 not found${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Python 3 found${NC}"

# Check Node.js
if ! command -v node &> /dev/null; then
    echo -e "${RED}❌ Node.js not found${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Node.js found${NC}"

# Check Redis
if ! command -v redis-cli &> /dev/null; then
    echo -e "${YELLOW}⚠️  Redis CLI not found (Redis may still be running)${NC}"
else
    echo -e "${GREEN}✓ Redis found${NC}"
fi

echo ""
echo "🔧 Setting up Backend..."
echo "========================"

# Setup Python virtual environment
cd /Users/bogoai/Book-Now/news-analyzer

if [ ! -d "venv" ]; then
    echo "Creating virtual environment..."
    python3 -m venv venv
fi

source venv/bin/activate

echo "Installing Python dependencies..."
pip install -q fastapi uvicorn pydantic

echo -e "${GREEN}✓ Backend setup complete${NC}"

echo ""
echo "⚛️  Setting up Frontend..."
echo "=========================="

cd /Users/bogoai/Book-Now/dashboard

if [ ! -d "node_modules" ]; then
    echo "Installing Node dependencies..."
    npm install --silent 2>/dev/null || echo "npm install completed with warnings"
else
    echo "Node modules already installed"
fi

echo -e "${GREEN}✓ Frontend setup complete${NC}"

echo ""
echo "✅ Setup Complete!"
echo "=================="
echo ""
echo "📖 Next Steps:"
echo ""
echo "1. Make sure Redis is running:"
echo "   redis-server"
echo ""
echo "2. In Terminal 1 - Start the Analysis:"
echo "   cd /Users/bogoai/Book-Now/news-analyzer"
echo "   source venv/bin/activate"
echo "   python main.py run-once"
echo ""
echo "3. In Terminal 2 - Start the API Server:"
echo "   cd /Users/bogoai/Book-Now/news-analyzer"
echo "   source venv/bin/activate"
echo "   python -m uvicorn api:app --reload"
echo ""
echo "4. In Terminal 3 - Start the Dashboard:"
echo "   cd /Users/bogoai/Book-Now/dashboard"
echo "   npm start"
echo ""
echo "5. Open your browser and go to:"
echo "   http://localhost:3000/analysis-dashboard"
echo ""
echo "💡 Tips:"
echo "- Use 'Debug Mode' toggle to see raw analysis data"
echo "- Configure auto-refresh interval as needed"
echo "- Click on coins to see detailed analysis"
echo "- Expand articles to see full details"
echo ""

