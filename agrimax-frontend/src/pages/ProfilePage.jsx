import useAuthStore from '../store/useAuthStore';
import { useNavigate } from 'react-router-dom';
import { HiArrowLeftOnRectangle } from 'react-icons/hi2';

export default function ProfilePage() {
  const { user, isAuthenticated, logout } = useAuthStore();
  const navigate = useNavigate();

  if (!isAuthenticated) {
    return (
      <div className="flex flex-col items-center justify-center h-64 gap-4">
        <span className="text-5xl">👤</span>
        <p className="text-slate-400">پروفائل دیکھنے کے لیے لاگ ان کریں</p>
        <button onClick={() => navigate('/login')} className="bg-green-600 text-white px-6 py-3 rounded-xl font-semibold">
          لاگ ان کریں
        </button>
      </div>
    );
  }

  return (
    <div className="space-y-5">
      <div className="glass-card p-6 text-center animate-fade-up">
        <div className="w-20 h-20 rounded-full bg-green-600/20 flex items-center justify-center mx-auto text-4xl">
          👨‍🌾
        </div>
        <h2 className="text-xl font-bold mt-3">{user?.name || 'کسان'}</h2>
        <p className="text-slate-400 text-sm">{user?.role || 'FARMER'}</p>
        <p className="text-slate-500 text-xs mt-1">ID: {user?.id?.slice(0, 8)}...</p>
      </div>

      <button
        onClick={() => { logout(); navigate('/'); }}
        className="w-full glass-card p-4 flex items-center justify-center gap-3 text-red-400 hover:bg-red-500/10 transition-colors"
      >
        <HiArrowLeftOnRectangle className="text-xl" />
        <span className="font-medium">لاگ آؤٹ</span>
      </button>
    </div>
  );
}
