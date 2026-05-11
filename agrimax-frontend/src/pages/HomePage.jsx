import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { HiMicrophone, HiChatBubbleLeftRight, HiArrowPath } from 'react-icons/hi2';
import useVoice from '../hooks/useVoice';
import useAuthStore from '../store/useAuthStore';
import { aiApi } from '../services/api';

const QUICK_ACTIONS = [
  { icon: '🌾', label: 'فصل بیچیں', labelEn: 'Sell Crop', voiceCmd: 'Meri fasal bechni hai' },
  { icon: '💰', label: 'قیمتیں دیکھیں', labelEn: 'Check Prices', path: '/prices', voiceCmd: 'Gehu ki price batao' },
  { icon: '👛', label: 'والیٹ', labelEn: 'Wallet', path: '/wallet', voiceCmd: 'Mera wallet dikhao' },
  { icon: '🛒', label: 'مارکیٹ', labelEn: 'Market', path: '/marketplace', voiceCmd: 'Market dikhao' },
];

const VOICE_HINTS = [
  '"گندم کی قیمت بتاؤ"',
  '"میری فصل بیچنی ہے"',
  '"والیٹ دکھاؤ"',
  '"آج کا مشورہ دو"',
];

const TIP_CATEGORY_ICONS = {
  price: '💰', planting: '🌱', harvest: '🌾',
  weather: '☁️', pest: '🐛', irrigation: '💧', market: '📊',
};

export default function HomePage() {
  const navigate = useNavigate();
  const { startListening, processText } = useVoice();
  const { isAuthenticated, user } = useAuthStore();

  const [tip, setTip] = useState(null);
  const [tipLoading, setTipLoading] = useState(true);
  const [hintIndex, setHintIndex] = useState(0);

  // Rotate voice hints every 3s
  useEffect(() => {
    const timer = setInterval(() => {
      setHintIndex((i) => (i + 1) % VOICE_HINTS.length);
    }, 3000);
    return () => clearInterval(timer);
  }, []);

  // Load AI tip from backend (routed through backend — no direct Gemini calls)
  useEffect(() => {
    const loadTip = async () => {
      try {
        // Cache in sessionStorage for the day
        const cacheKey = `agrimax_tip_${new Date().toDateString()}`;
        const cached = sessionStorage.getItem(cacheKey);
        if (cached) {
          setTip(JSON.parse(cached));
          setTipLoading(false);
          return;
        }

        const { data } = await aiApi.getDailyTip();
        if (data.data) {
          setTip(data.data);
          sessionStorage.setItem(cacheKey, JSON.stringify(data.data));
        }
      } catch (err) {
        console.warn('Could not load daily tip:', err.message);
        // Use a static fallback
        setTip({
          titleUr: 'آج کی زرعی ٹپ',
          titleEn: "Today's Farming Tip",
          tipUr: 'اس موسم میں اپنی فصل کی آبپاشی باقاعدگی سے کریں۔ صبح یا شام کو پانی دینا بہتر ہے۔',
          tipEn: 'Irrigate your crops regularly this season. Morning or evening watering is best.',
          category: 'irrigation',
        });
      } finally {
        setTipLoading(false);
      }
    };

    loadTip();
  }, []);

  const handleQuickAction = (action) => {
    if (action.path) {
      navigate(action.path);
    } else {
      // Trigger voice command directly
      processText(action.voiceCmd);
    }
  };

  return (
    <div className="space-y-5">
      {/* Welcome Card */}
      <div className="glass-card p-5 animate-fade-up">
        <p className="text-slate-400 text-sm">
          {isAuthenticated ? 'خوش آمدید 👋' : 'AgriMax میں خوش آمدید'}
        </p>
        <h2 className="text-2xl font-bold mt-1 urdu-text">
          {isAuthenticated && user?.name ? user.name : 'کسان بھائی'}
        </h2>
        <p className="text-slate-400 text-sm mt-1 urdu-text">
          آواز سے بات کریں یا نیچے بٹن دبائیں
        </p>
      </div>

      {/* Voice Prompt Card */}
      <div
        onClick={startListening}
        className="glass-card p-5 cursor-pointer border border-green-500/20 hover:border-green-500/50 transition-all animate-fade-up group"
        style={{ animationDelay: '0.1s' }}
      >
        <div className="flex items-center gap-4">
          <div className="w-14 h-14 rounded-full bg-green-600 flex items-center justify-center text-white text-2xl group-hover:animate-mic-pulse transition-all shadow-lg shadow-green-900/30">
            <HiMicrophone />
          </div>
          <div className="flex-1">
            <p className="text-lg font-semibold text-green-400 urdu-text">🎤 بولیں</p>
            <p className="text-slate-400 text-sm">Tap to speak a command</p>
          </div>
        </div>

        {/* Rotating voice hint */}
        <div className="mt-4 h-8 flex items-center">
          <span
            key={hintIndex}
            className="text-sm bg-slate-700/50 text-green-300 px-3 py-1.5 rounded-full urdu-text animate-fade-up"
          >
            {VOICE_HINTS[hintIndex]}
          </span>
        </div>
      </div>

      {/* Quick Actions Grid */}
      <div className="grid grid-cols-2 gap-3">
        {QUICK_ACTIONS.map((action, i) => (
          <button
            key={i}
            onClick={() => handleQuickAction(action)}
            className="farmer-btn animate-fade-up"
            style={{ animationDelay: `${0.2 + i * 0.08}s` }}
          >
            <span className="icon">{action.icon}</span>
            <span className="text-sm font-semibold urdu-text">{action.label}</span>
            <span className="text-[10px] text-slate-400">{action.labelEn}</span>
          </button>
        ))}
      </div>

      {/* AI Daily Tip (from backend — no direct Gemini calls) */}
      <div className="glass-card p-5 animate-fade-up" style={{ animationDelay: '0.5s' }}>
        <div className="flex items-center justify-between mb-3">
          <div className="flex items-center gap-2">
            <HiChatBubbleLeftRight className="text-amber-400 text-xl" />
            <span className="text-sm font-semibold text-amber-400 urdu-text">آج کی ٹپ</span>
          </div>
          {tip && (
            <span className="text-lg">
              {TIP_CATEGORY_ICONS[tip.category] || '💡'}
            </span>
          )}
        </div>

        {tipLoading ? (
          <div className="space-y-2">
            <div className="h-4 bg-slate-700/60 rounded animate-pulse w-3/4" />
            <div className="h-4 bg-slate-700/60 rounded animate-pulse w-full" />
            <div className="h-4 bg-slate-700/60 rounded animate-pulse w-2/3" />
          </div>
        ) : tip ? (
          <>
            <p className="text-slate-200 font-medium text-sm urdu-text leading-relaxed">
              {tip.tipUr}
            </p>
            <p className="text-slate-500 text-xs mt-2 leading-relaxed">
              {tip.tipEn}
            </p>
          </>
        ) : null}
      </div>
    </div>
  );
}
