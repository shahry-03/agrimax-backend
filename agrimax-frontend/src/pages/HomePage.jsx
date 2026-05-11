import { useNavigate } from 'react-router-dom';
import { HiMicrophone, HiCurrencyDollar, HiShoppingBag, HiChatBubbleLeftRight } from 'react-icons/hi2';
import useVoice from '../hooks/useVoice';
import useAuthStore from '../store/useAuthStore';

const QUICK_ACTIONS = [
  { icon: '🌾', label: 'فصل بیچیں', labelEn: 'Sell Crop', path: '/marketplace', voiceCmd: 'Meri fasal bechni hai' },
  { icon: '💰', label: 'قیمتیں دیکھیں', labelEn: 'Check Prices', path: '/prices', voiceCmd: 'Gehu ki price batao' },
  { icon: '👛', label: 'والیٹ', labelEn: 'Wallet', path: '/wallet', voiceCmd: 'Mera wallet dikhao' },
  { icon: '🛒', label: 'مارکیٹ', labelEn: 'Market', path: '/marketplace', voiceCmd: 'Market dikhao' },
];

const VOICE_HINTS = [
  '"گندم کی قیمت بتاؤ"',
  '"میری فصل بیچنی ہے"',
  '"لاگ ان کرو"',
  '"والیٹ دکھاؤ"',
];

export default function HomePage() {
  const navigate = useNavigate();
  const { startListening, processText } = useVoice();
  const { isAuthenticated, user } = useAuthStore();

  return (
    <div className="space-y-6">
      {/* Welcome Card */}
      <div className="glass-card p-6 animate-fade-up">
        <p className="text-slate-400 text-sm">
          {isAuthenticated ? `خوش آمدید 👋` : 'AgriMax میں خوش آمدید'}
        </p>
        <h2 className="text-2xl font-bold mt-1">
          {isAuthenticated && user?.name ? user.name : 'کسان بھائی'}
        </h2>
        <p className="text-slate-400 text-sm mt-2">
          آواز سے بات کریں یا نیچے بٹن دبائیں
        </p>
      </div>

      {/* Voice Prompt Card */}
      <div
        onClick={startListening}
        className="glass-card p-6 cursor-pointer border-green-500/30 hover:border-green-500/60 transition-all animate-fade-up group"
        style={{ animationDelay: '0.1s' }}
      >
        <div className="flex items-center gap-4">
          <div className="w-14 h-14 rounded-full bg-green-600 flex items-center justify-center text-white text-2xl group-hover:animate-mic-pulse transition-all">
            <HiMicrophone />
          </div>
          <div>
            <p className="text-lg font-semibold text-green-400">🎤 بولیں</p>
            <p className="text-slate-400 text-sm">Tap to speak a command</p>
          </div>
        </div>
        <div className="mt-4 flex flex-wrap gap-2">
          {VOICE_HINTS.map((hint, i) => (
            <span key={i} className="text-xs bg-slate-700/50 text-slate-300 px-3 py-1 rounded-full">
              {hint}
            </span>
          ))}
        </div>
      </div>

      {/* Quick Actions Grid */}
      <div className="grid grid-cols-2 gap-3">
        {QUICK_ACTIONS.map((action, i) => (
          <button
            key={i}
            onClick={() => navigate(action.path)}
            className="farmer-btn animate-fade-up"
            style={{ animationDelay: `${0.2 + i * 0.1}s` }}
          >
            <span className="icon">{action.icon}</span>
            <span className="text-sm font-medium">{action.label}</span>
            <span className="text-[10px] text-slate-400">{action.labelEn}</span>
          </button>
        ))}
      </div>

      {/* Today's Tip */}
      <div className="glass-card p-5 animate-fade-up" style={{ animationDelay: '0.6s' }}>
        <div className="flex items-center gap-2 mb-2">
          <HiChatBubbleLeftRight className="text-amber-400" />
          <span className="text-sm font-medium text-amber-400">آج کی ٹپ</span>
        </div>
        <p className="text-slate-300 text-sm leading-relaxed">
          گندم کی فصل کی قیمت اس ہفتے بڑھنے کا امکان ہے۔ ابھی بیچنا فائدہ مند ہو سکتا ہے۔
        </p>
        <p className="text-slate-500 text-xs mt-2">
          Wheat prices may rise this week. Consider selling now.
        </p>
      </div>
    </div>
  );
}
