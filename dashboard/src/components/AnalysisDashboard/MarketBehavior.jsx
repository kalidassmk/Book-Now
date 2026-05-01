import React from 'react';

const MarketBehavior = ({ sentimentData }) => {
  if (!sentimentData || Object.keys(sentimentData).length === 0) return null;

  // Filter for "Fast Movers" (Score > 60 or Confidence > 80)
  const fastMovers = Object.values(sentimentData)
    .filter(coin => coin.score > 60 || coin.confidence > 80)
    .sort((a, b) => b.score - a.score);

  return (
    <div className="bg-gray-900/50 p-4 rounded-xl border border-gray-800 backdrop-blur-sm mt-4">
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-xl font-bold text-white flex items-center gap-2">
          <span className="text-blue-500">⚡</span> Adaptive Market Intelligence
        </h2>
        <span className="text-xs text-gray-500 uppercase tracking-widest">Real-time Behavioral Analysis</span>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {fastMovers.map((coin) => (
          <div key={coin.symbol} className="bg-gray-800/40 p-4 rounded-lg border border-gray-700/50 hover:border-blue-500/50 transition-all group">
            <div className="flex justify-between items-start mb-2">
              <div>
                <span className="text-lg font-bold text-white">{coin.symbol.replace('/USDT', '')}</span>
                <span className={`ml-2 text-[10px] px-2 py-0.5 rounded-full ${
                  coin.regime === 'TRENDING' ? 'bg-green-500/20 text-green-400' :
                  coin.regime === 'VOLATILE' ? 'bg-orange-500/20 text-orange-400' :
                  'bg-blue-500/20 text-blue-400'
                }`}>
                  {coin.regime}
                </span>
              </div>
              <div className="text-right">
                <div className={`text-sm font-bold ${coin.score > 70 ? 'text-green-400' : 'text-yellow-400'}`}>
                  {coin.sentiment}
                </div>
              </div>
            </div>

            {/* Sentiment Score Bar */}
            <div className="space-y-3">
              <div>
                <div className="flex justify-between text-[10px] text-gray-400 mb-1 uppercase tracking-tighter">
                  <span>Sentiment Score</span>
                  <span>{coin.score.toFixed(1)}%</span>
                </div>
                <div className="h-1.5 w-full bg-gray-700 rounded-full overflow-hidden">
                  <div 
                    className={`h-full transition-all duration-1000 ${coin.score > 70 ? 'bg-green-500 shadow-[0_0_8px_rgba(34,197,94,0.5)]' : 'bg-yellow-500'}`}
                    style={{ width: `${coin.score}%` }}
                  />
                </div>
              </div>

              <div>
                <div className="flex justify-between text-[10px] text-gray-400 mb-1 uppercase tracking-tighter">
                  <span>Signal Confidence</span>
                  <span>{coin.confidence}%</span>
                </div>
                <div className="h-1 w-full bg-gray-700 rounded-full overflow-hidden">
                  <div 
                    className="h-full bg-blue-500 transition-all duration-1000"
                    style={{ width: `${coin.confidence}%` }}
                  />
                </div>
              </div>
            </div>

            {/* Quick Metrics */}
            <div className="grid grid-cols-2 gap-2 mt-4 pt-3 border-t border-gray-700/50">
               <div className="text-[10px]">
                  <div className="text-gray-500">MOMENTUM</div>
                  <div className="text-gray-300 font-mono">{coin.timeframes['5m'].toFixed(1)}</div>
               </div>
               <div className="text-[10px]">
                  <div className="text-gray-500">TREND (1H)</div>
                  <div className="text-gray-300 font-mono">{coin.timeframes['1h'].toFixed(1)}</div>
               </div>
            </div>
          </div>
        ))}
      </div>
      
      {fastMovers.length === 0 && (
        <div className="text-center py-8 text-gray-500 italic text-sm">
          No high-confidence fast moves detected. Monitoring market behavior...
        </div>
      )}
    </div>
  );
};

export default MarketBehavior;
