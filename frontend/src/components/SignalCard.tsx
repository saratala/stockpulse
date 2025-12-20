import React, { useState, useEffect, useRef } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { 
  TrendingUp, 
  TrendingDown, 
  Minus,
  Star,
  Activity,
  BarChart3,
  DollarSign,
  Info,
  ChevronRight,
  Target,
  Zap,
  Shield,
  AlertTriangle,
  ExternalLink,
  Eye,
  X
} from 'lucide-react';
import GlassContainer from './GlassContainer';
import GlassButton from './GlassButton';
import '../styles/glassmorphism.css';

interface SignalCardProps {
  candidate: {
    ticker: string;
    name: string;
    sector: string;
    price?: number;
    currentPrice?: number;
    screeningScore: number;
    passesAllScreens?: boolean;
    emaStackAligned?: boolean;
    adxStrength?: number;
    stochPosition?: number;
    rsi?: number;
    volumeRatio?: number;
    changePercent?: number;
    signalAnalysis?: {
      ticker: string;
      primarySignal: 'BULLISH' | 'BEARISH' | 'NEUTRAL';
      primaryConfidence: number;
      reversalSignal?: string;
      reversalConfidence?: number;
      riskLevel?: string;
      keyLevels?: {
        support: number;
        resistance: number;
        pivot: number;
      };
      primaryReasons?: string[];
    };
  };
  index?: number;
}

const SignalCard: React.FC<SignalCardProps> = ({ candidate, index = 0 }) => {
  const currentPrice = candidate.currentPrice || candidate.price || 0;
  const [expanded, setExpanded] = useState(false);
  const [showDetails, setShowDetails] = useState(false);
  const [showPopup, setShowPopup] = useState(false);
  const [hoverTimeout, setHoverTimeout] = useState<ReturnType<typeof setTimeout> | null>(null);
  const [popupPosition, setPopupPosition] = useState({ x: 0, y: 0, placement: 'right' });
  const cardRef = useRef<HTMLDivElement>(null);
  
  useEffect(() => {
    return () => {
      if (hoverTimeout) clearTimeout(hoverTimeout);
    };
  }, [hoverTimeout]);

  const calculatePopupPosition = () => {
    if (!cardRef.current) return;
    
    const rect = cardRef.current.getBoundingClientRect();
    const popupWidth = 420; // reduced popup width
    const popupHeight = 400; // reduced popup height
    const margin = 15;
    
    // Calculate available space on each side
    const spaceRight = window.innerWidth - rect.right;
    const spaceLeft = rect.left;
    const spaceBottom = window.innerHeight - rect.bottom;
    const spaceTop = rect.top;
    
    let x = 0;
    let y = rect.top;
    let placement = 'right';
    
    // Determine horizontal position
    if (spaceRight >= popupWidth + margin) {
      // Place to the right
      x = rect.right + margin;
      placement = 'right';
    } else if (spaceLeft >= popupWidth + margin) {
      // Place to the left
      x = rect.left - popupWidth - margin;
      placement = 'left';
    } else {
      // Center horizontally if not enough space on sides
      x = Math.max(margin, Math.min(window.innerWidth - popupWidth - margin, rect.left + (rect.width / 2) - (popupWidth / 2)));
      placement = 'center';
    }
    
    // Adjust vertical position to keep popup in viewport
    if (y + popupHeight > window.innerHeight - margin) {
      y = Math.max(margin, window.innerHeight - popupHeight - margin);
    }
    
    setPopupPosition({ x, y, placement });
  };
  
  const getSignalVariant = () => {
    if (!candidate.signalAnalysis) return 'neutral';
    switch (candidate.signalAnalysis.primarySignal) {
      case 'BULLISH':
        return 'bullish';
      case 'BEARISH':
        return 'bearish';
      default:
        return 'neutral';
    }
  };

  const getSignalIcon = () => {
    if (!candidate.signalAnalysis) return <Minus className="w-5 h-5" />;
    switch (candidate.signalAnalysis.primarySignal) {
      case 'BULLISH':
        return <TrendingUp className="w-5 h-5" />;
      case 'BEARISH':
        return <TrendingDown className="w-5 h-5" />;
      default:
        return <Minus className="w-5 h-5" />;
    }
  };

  const getSignalColor = () => {
    if (!candidate.signalAnalysis) return 'text-orange-500';
    switch (candidate.signalAnalysis.primarySignal) {
      case 'BULLISH':
        return 'text-green-800';
      case 'BEARISH':
        return 'text-red-600';
      default:
        return 'text-orange-500';
    }
  };

  const getConfidenceLevel = () => {
    if (!candidate.signalAnalysis) return 'No Signal';
    const confidence = candidate.signalAnalysis.primaryConfidence;
    if (confidence >= 80) return 'Very High';
    if (confidence >= 60) return 'High';
    if (confidence >= 40) return 'Medium';
    return 'Low';
  };

  const getScoreGrade = (score: number) => {
    if (score >= 90) return 'A+';
    if (score >= 80) return 'A';
    if (score >= 70) return 'B+';
    if (score >= 60) return 'B';
    if (score >= 50) return 'C+';
    return 'C';
  };

  return (
    <>
      {/* Compact Card */}
      <div
        ref={cardRef}
        onMouseEnter={() => {
          if (hoverTimeout) clearTimeout(hoverTimeout);
          calculatePopupPosition();
          setShowPopup(true);
        }}
        onMouseLeave={() => {
          const timeout = setTimeout(() => setShowPopup(false), 200);
          setHoverTimeout(timeout);
        }}
        className={`glass-signal-card relative overflow-hidden cursor-pointer transition-all duration-300 ${
          candidate.signalAnalysis?.primarySignal === 'BULLISH' ? 'bullish' :
          candidate.signalAnalysis?.primarySignal === 'BEARISH' ? 'bearish' : 'neutral'
        }`}
        style={{
          boxShadow: 'var(--shadow-glass-small)',
          height: '120px' // Fixed compact height
        }}
      >

        {/* Compact Content */}
        <div className="p-4 relative z-10 h-full flex flex-col justify-between">
          {/* Top Row: Ticker + Signal */}
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <div className={`p-2 rounded-lg ${getSignalColor()}`}>
                {getSignalIcon()}
              </div>
              <div>
                <div className="flex items-center gap-1">
                  <h3 className="text-base font-bold" style={{ color: 'var(--text-primary)' }}>{candidate.ticker}</h3>
                  {(candidate.passesAllScreens || candidate.emaStackAligned) && <Shield className="w-3 h-3 text-green-600" />}
                </div>
                <p className="text-xs truncate max-w-[120px]" style={{ color: 'var(--text-secondary)' }}>
                  {candidate.name}
                </p>
              </div>
            </div>
            <div className="text-right">
              <div className={`text-lg font-bold ${
                candidate.signalAnalysis?.primarySignal === 'BULLISH' ? 'text-green-800' :
                candidate.signalAnalysis?.primarySignal === 'BEARISH' ? 'text-red-600' :
                candidate.signalAnalysis?.primarySignal === 'NEUTRAL' ? 'text-orange-500' : 'text-gray-600'
              }`}>
                ${currentPrice.toFixed(2)}
              </div>
            </div>
          </div>

          {/* Bottom Row: Signal + Score */}
          <div className="flex items-center justify-between">
            {candidate.signalAnalysis && (
              <div className="flex items-center gap-2">
                <div className={`px-2 py-1 rounded text-xs font-semibold ${
                  candidate.signalAnalysis.primarySignal === 'BULLISH' ? 'text-green-800' :
                  candidate.signalAnalysis.primarySignal === 'BEARISH' ? 'text-red-600' : 'text-orange-500'
                }`}>
                  {candidate.signalAnalysis.primarySignal}
                </div>
                <div className={`text-xs ${
                  candidate.signalAnalysis.primarySignal === 'BULLISH' ? 'text-green-800/70' :
                  candidate.signalAnalysis.primarySignal === 'BEARISH' ? 'text-red-600/70' : 'text-orange-500/70'
                }`}>
                  {candidate.signalAnalysis.primaryConfidence}%
                </div>
              </div>
            )}
            <div className="flex items-center gap-1">
              <div className={`px-2 py-1 rounded text-xs font-semibold ${
                candidate.screeningScore >= 80 ? 'bg-green-800/20 text-green-800' :
                candidate.screeningScore >= 60 ? 'bg-orange-500/20 text-orange-500' :
                'bg-red-600/20 text-red-600'
              }`}>
                {getScoreGrade(candidate.screeningScore)}
              </div>
            </div>
          </div>
        </div>
      </div>




      {/* Hover Popup */}
      <AnimatePresence>
        {showPopup && (
          <motion.div
            initial={{ 
              opacity: 0, 
              scale: 0.95,
              x: popupPosition.placement === 'right' ? -10 : popupPosition.placement === 'left' ? 10 : 0
            }}
            animate={{ 
              opacity: 1, 
              scale: 1,
              x: 0
            }}
            exit={{ 
              opacity: 0, 
              scale: 0.95,
              x: popupPosition.placement === 'right' ? -10 : popupPosition.placement === 'left' ? 10 : 0
            }}
            transition={{ duration: 0.15, ease: "easeOut" }}
            className="fixed z-50"
            onMouseEnter={() => {
              if (hoverTimeout) clearTimeout(hoverTimeout);
              setShowPopup(true);
            }}
            onMouseLeave={() => {
              const timeout = setTimeout(() => setShowPopup(false), 100);
              setHoverTimeout(timeout);
            }}
            style={{
              left: `${popupPosition.x}px`,
              top: `${popupPosition.y}px`,
              width: '420px',
              maxWidth: 'calc(100vw - 30px)',
              maxHeight: 'calc(100vh - 30px)',
              overflow: 'auto'
            }}
            >
              <div className="glass-card-dark relative" style={{
                background: 'var(--card-primary)',
                backdropFilter: 'blur(var(--blur-lg))',
                border: '1px solid var(--glass-border)',
                borderRadius: '20px',
                padding: '20px',
                boxShadow: '0 20px 40px -12px rgba(0, 0, 0, 0.25)'
              }}>
              {/* Close Button */}
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  setShowPopup(false);
                }}
                className="absolute top-4 right-4 p-2 rounded-full bg-white/10 hover:bg-white/20 transition-colors"
              >
                <X className="w-4 h-4" style={{ color: 'var(--text-primary)' }} />
              </button>

              {/* Header */}
              <div className="flex items-center justify-between mb-4">
                <div className="flex items-center gap-3">
                  <div className={`p-3 rounded-lg ${getSignalColor()}`}>
                    {getSignalIcon()}
                  </div>
                  <div>
                    <div className="flex items-center gap-2">
                      <h2 className="text-xl font-bold" style={{ color: 'var(--text-primary)' }}>
                        {candidate.ticker}
                      </h2>
                      {(candidate.passesAllScreens || candidate.emaStackAligned) && (
                        <Shield className="w-4 h-4 text-green-600" />
                      )}
                    </div>
                    <p className="text-xs" style={{ color: 'var(--text-secondary)' }}>
                      {candidate.name}
                    </p>
                    <p className="text-xs" style={{ color: 'var(--text-tertiary)' }}>
                      {candidate.sector}
                    </p>
                  </div>
                </div>
                <div className="text-right">
                  <div className={`text-2xl font-bold ${
                    candidate.signalAnalysis?.primarySignal === 'BULLISH' ? 'text-green-800' :
                    candidate.signalAnalysis?.primarySignal === 'BEARISH' ? 'text-red-600' :
                    candidate.signalAnalysis?.primarySignal === 'NEUTRAL' ? 'text-orange-500' : 'text-gray-600'
                  }`}>
                    ${currentPrice.toFixed(2)}
                  </div>
                </div>
              </div>

              {/* Signal Analysis */}
              {candidate.signalAnalysis && (
                <div className="mb-4 p-3 rounded-lg bg-white/5">
                  <div className="flex items-center justify-between mb-3">
                    <div className="flex items-center gap-2">
                      <Zap className={`w-4 h-4 ${getSignalColor()}`} />
                      <div>
                        <div className={`text-sm font-bold ${
                          candidate.signalAnalysis.primarySignal === 'BULLISH' ? 'text-green-800' :
                          candidate.signalAnalysis.primarySignal === 'BEARISH' ? 'text-red-600' : 'text-orange-500'
                        }`}>
                          {candidate.signalAnalysis.primarySignal} - {getConfidenceLevel()}
                        </div>
                      </div>
                    </div>
                    <div className="text-right">
                      <div className={`text-xl font-bold ${
                        candidate.signalAnalysis.primarySignal === 'BULLISH' ? 'text-green-800' :
                        candidate.signalAnalysis.primarySignal === 'BEARISH' ? 'text-red-600' : 'text-orange-500'
                      }`}>
                        {candidate.signalAnalysis.primaryConfidence}%
                      </div>
                      <div className="w-20 h-1.5 bg-white/20 rounded-full overflow-hidden mt-1">
                        <motion.div
                          className={`h-full ${
                            candidate.signalAnalysis.primarySignal === 'BULLISH' ? 'bg-green-800' :
                            candidate.signalAnalysis.primarySignal === 'BEARISH' ? 'bg-red-600' : 'bg-orange-500'
                          }`}
                          initial={{ width: 0 }}
                          animate={{ width: `${candidate.signalAnalysis.primaryConfidence}%` }}
                          transition={{ duration: 1, delay: 0.3 }}
                        />
                      </div>
                    </div>
                  </div>

                  {/* Signal Reasons */}
                  <div className="space-y-1.5">
                    <h4 className={`text-xs font-semibold flex items-center gap-1.5 ${
                      candidate.signalAnalysis.primarySignal === 'BULLISH' ? 'text-green-700' :
                      candidate.signalAnalysis.primarySignal === 'BEARISH' ? 'text-red-600' : 'text-orange-600'
                    }`}>
                      <Target className="w-3 h-3" />
                      Key Signals
                    </h4>
                    <div className="space-y-1">
                      {(candidate.signalAnalysis.primaryReasons || []).slice(0, 3).map((reason, idx) => (
                        <div
                          key={idx}
                          className={`flex items-start gap-1.5 text-xs ${
                            candidate.signalAnalysis?.primarySignal === 'BULLISH' ? 'text-green-700/80' :
                            candidate.signalAnalysis?.primarySignal === 'BEARISH' ? 'text-red-600/80' : 'text-orange-600/80'
                          }`}
                        >
                          <div className="w-1 h-1 rounded-full bg-current mt-1 flex-shrink-0" />
                          <span className="line-clamp-2">{reason}</span>
                        </div>
                      ))}
                    </div>
                  </div>
                </div>
              )}

              {/* Screening Score */}
              <div className="flex items-center justify-between mb-4 p-3 rounded-lg bg-white/5">
                <div className="flex items-center gap-2">
                  <BarChart3 className="w-4 h-4" style={{ color: 'var(--text-primary)' }} />
                  <div>
                    <div className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
                      Screening Score
                    </div>
                  </div>
                </div>
                <div className="flex items-center gap-2">
                  <div className={`text-xl font-bold ${
                    candidate.screeningScore >= 80 ? 'text-green-800' :
                    candidate.screeningScore >= 60 ? 'text-orange-500' : 'text-red-600'
                  }`}>
                    {getScoreGrade(candidate.screeningScore)}
                  </div>
                  <div className="text-sm font-semibold" style={{ color: 'var(--text-secondary)' }}>
                    ({candidate.screeningScore}%)
                  </div>
                </div>
              </div>

              {/* Action Buttons */}
              <div className="flex gap-2">
                <GlassButton
                  size="sm"
                  variant="accent"
                  onClick={(e) => {
                    e.stopPropagation();
                    // Navigate to detailed analysis
                  }}
                  icon={<ExternalLink className="w-3 h-3" />}
                  className="text-xs py-1.5 px-3"
                >
                  Analysis
                </GlassButton>
                <GlassButton
                  size="sm"
                  variant="secondary"
                  onClick={(e) => {
                    e.stopPropagation();
                    // Add to watchlist
                  }}
                  icon={<Star className="w-3 h-3" />}
                  className="text-xs py-1.5 px-3"
                >
                  Watchlist
                </GlassButton>
                <GlassButton
                  size="sm"
                  variant="secondary"
                  onClick={(e) => {
                    e.stopPropagation();
                    // View live data
                  }}
                  icon={<Eye className="w-3 h-3" />}
                  className="text-xs py-1.5 px-3"
                >
                  Live
                </GlassButton>
              </div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </>
  );
};

export default SignalCard;