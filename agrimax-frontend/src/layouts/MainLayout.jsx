import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { HiHome, HiShoppingBag, HiCurrencyDollar, HiBell, HiUser } from 'react-icons/hi2';
import VoiceAssistant from '../components/voice/VoiceAssistant';

const NAV_ITEMS = [
  { path: '/', icon: HiHome, label: 'ہوم', labelEn: 'Home' },
  { path: '/prices', icon: HiCurrencyDollar, label: 'قیمتیں', labelEn: 'Prices' },
  { path: '/marketplace', icon: HiShoppingBag, label: 'مارکیٹ', labelEn: 'Market' },
  { path: '/wallet', icon: HiCurrencyDollar, label: 'والیٹ', labelEn: 'Wallet' },
  { path: '/profile', icon: HiUser, label: 'پروفائل', labelEn: 'Profile' },
];

export default function MainLayout() {
  const navigate = useNavigate();
  const location = useLocation();

  return (
    <div className="min-h-screen bg-slate-900 flex flex-col">
      {/* Top Bar */}
      <header className="sticky top-0 z-30 glass-card rounded-none border-x-0 border-t-0 px-4 py-3 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <span className="text-2xl">🌾</span>
          <h1 className="text-xl font-bold bg-gradient-to-r from-green-400 to-emerald-300 bg-clip-text text-transparent">
            AgriMax
          </h1>
        </div>
        <button
          onClick={() => navigate('/notifications')}
          className="relative p-2 rounded-full hover:bg-slate-700/50 transition-colors"
        >
          <HiBell className="text-xl text-slate-300" />
          <span className="absolute top-1 right-1 w-2 h-2 bg-red-500 rounded-full" />
        </button>
      </header>

      {/* Page Content */}
      <main className="flex-1 overflow-y-auto pb-24 px-4 pt-4">
        <Outlet />
      </main>

      {/* Voice Assistant */}
      <VoiceAssistant />

      {/* Bottom Navigation */}
      <nav className="fixed bottom-0 left-0 right-0 z-30 glass-card rounded-none border-x-0 border-b-0">
        <div className="flex items-center justify-around py-2 pr-20">
          {NAV_ITEMS.map((item) => {
            const isActive = location.pathname === item.path;
            const Icon = item.icon;
            return (
              <button
                key={item.path}
                onClick={() => navigate(item.path)}
                className={`flex flex-col items-center gap-0.5 px-3 py-1.5 rounded-xl transition-all ${
                  isActive ? 'text-green-400 bg-green-500/10' : 'text-slate-400 hover:text-slate-200'
                }`}
              >
                <Icon className="text-xl" />
                <span className="text-[10px] font-medium">{item.label}</span>
              </button>
            );
          })}
        </div>
      </nav>
    </div>
  );
}
