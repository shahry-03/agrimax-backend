import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import {
  HiHome, HiShoppingBag, HiChartBar, HiBell, HiUser, HiWallet,
} from 'react-icons/hi2';
import VoiceAssistant from '../components/voice/VoiceAssistant';
import useAuthStore from '../store/useAuthStore';

const NAV_ITEMS = [
  { path: '/',             icon: HiHome,       label: 'ہوم',     labelEn: 'Home'    },
  { path: '/prices',       icon: HiChartBar,   label: 'قیمتیں', labelEn: 'Prices'  },
  { path: '/marketplace',  icon: HiShoppingBag,label: 'مارکیٹ', labelEn: 'Market'  },
  { path: '/wallet',       icon: HiWallet,     label: 'والیٹ',  labelEn: 'Wallet'  },
  { path: '/profile',      icon: HiUser,       label: 'پروفائل',labelEn: 'Profile' },
];

export default function MainLayout() {
  const navigate = useNavigate();
  const location = useLocation();
  const { user } = useAuthStore();

  return (
    <div className="min-h-screen bg-slate-900 flex flex-col">
      {/* ── Top Bar ── */}
      <header className="sticky top-0 z-30 glass-card rounded-none border-x-0 border-t-0 px-4 py-3 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <span className="text-2xl">🌾</span>
          <h1 className="text-xl font-bold bg-gradient-to-r from-green-400 to-emerald-300 bg-clip-text text-transparent">
            AgriMax
          </h1>
        </div>
        <div className="flex items-center gap-2">
          {/* User greeting */}
          {user?.name && (
            <span className="text-slate-400 text-xs hidden sm:block urdu-text">
              {user.name}
            </span>
          )}
          {/* Notifications bell */}
          <button
            id="notifications-btn"
            onClick={() => navigate('/notifications')}
            className="relative p-2 rounded-full hover:bg-slate-700/50 transition-colors"
            aria-label="Notifications"
          >
            <HiBell className="text-xl text-slate-300" />
            <span className="absolute top-1.5 right-1.5 w-2 h-2 bg-red-500 rounded-full animate-pulse" />
          </button>
        </div>
      </header>

      {/* ── Page Content ── */}
      <main className="flex-1 overflow-y-auto pb-24 px-4 pt-4 max-w-2xl mx-auto w-full">
        <Outlet />
      </main>

      {/* ── Voice Assistant (floating mic button + overlay) ── */}
      <VoiceAssistant />

      {/* ── Bottom Navigation ── */}
      <nav className="fixed bottom-0 left-0 right-0 z-30 glass-card rounded-none border-x-0 border-b-0 safe-area-pb">
        {/* pr-20 makes room for the floating mic button */}
        <div className="flex items-center justify-around py-2 px-2 pr-20 max-w-2xl mx-auto">
          {NAV_ITEMS.map((item) => {
            const isActive = location.pathname === item.path;
            const Icon = item.icon;
            return (
              <button
                key={item.path}
                id={`nav-${item.labelEn.toLowerCase()}`}
                onClick={() => navigate(item.path)}
                className={`flex flex-col items-center gap-0.5 px-2 py-1.5 rounded-xl transition-all min-w-0 ${
                  isActive
                    ? 'text-green-400 bg-green-500/10'
                    : 'text-slate-400 hover:text-slate-200 hover:bg-slate-700/30'
                }`}
              >
                <Icon className="text-xl" />
                <span className="text-[9px] font-medium urdu-text truncate max-w-[3rem]">
                  {item.label}
                </span>
                {isActive && (
                  <div className="w-1 h-1 rounded-full bg-green-400 mt-0.5" />
                )}
              </button>
            );
          })}
        </div>
      </nav>
    </div>
  );
}
