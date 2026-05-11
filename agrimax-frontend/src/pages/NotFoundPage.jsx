import { useNavigate } from 'react-router-dom';
import { HiHome } from 'react-icons/hi2';

export default function NotFoundPage() {
  const navigate = useNavigate();

  return (
    <div className="min-h-screen bg-slate-900 flex flex-col items-center justify-center px-4 text-center">
      <span className="text-8xl mb-4">🌾</span>
      <h1 className="text-6xl font-bold text-green-400 mb-2">404</h1>
      <p className="text-2xl font-semibold text-white mb-2 urdu-text">صفحہ نہیں ملا</p>
      <p className="text-slate-400 mb-8">Page not found</p>
      <button
        onClick={() => navigate('/')}
        className="flex items-center gap-2 bg-green-600 hover:bg-green-500 text-white px-6 py-3 rounded-xl font-semibold transition-all"
      >
        <HiHome /> گھر جائیں / Go Home
      </button>
    </div>
  );
}
