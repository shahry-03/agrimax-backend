import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { authApi } from '../services/api';
import useAuthStore from '../store/useAuthStore';
import { HiPhone, HiKey, HiArrowRight } from 'react-icons/hi2';

export default function LoginPage() {
  const navigate = useNavigate();
  const { login } = useAuthStore();
  const [step, setStep] = useState('phone'); // phone | otp
  const [phone, setPhone] = useState('');
  const [otp, setOtp] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleSendOtp = async () => {
    if (phone.length !== 11) { setError('11 ہندسے کا نمبر درج کریں'); return; }
    setLoading(true); setError('');
    try {
      await authApi.sendOtp(phone);
      setStep('otp');
    } catch (err) {
      setError(err.response?.data?.messageUr || 'OTP نہیں بھیجا جا سکا');
    } finally { setLoading(false); }
  };

  const handleVerifyOtp = async () => {
    if (otp.length !== 6) { setError('6 ہندسے کا OTP درج کریں'); return; }
    setLoading(true); setError('');
    try {
      const { data } = await authApi.verifyOtp(phone, otp);
      const d = data.data;
      login({ id: d.userId, name: d.name, role: d.role }, d.accessToken, d.refreshToken);
      navigate('/');
    } catch (err) {
      setError(err.response?.data?.messageUr || 'OTP غلط ہے');
    } finally { setLoading(false); }
  };

  return (
    <div className="min-h-screen bg-slate-900 flex flex-col items-center justify-center px-4">
      {/* Logo */}
      <div className="text-center mb-8 animate-fade-up">
        <span className="text-6xl">🌾</span>
        <h1 className="text-3xl font-bold mt-3 bg-gradient-to-r from-green-400 to-emerald-300 bg-clip-text text-transparent">
          AgriMax
        </h1>
        <p className="text-slate-400 mt-2">کسانوں کا ذہین ساتھی</p>
        <p className="text-slate-500 text-sm">Smart Partner for Farmers</p>
      </div>

      {/* Login Card */}
      <div className="glass-card p-6 w-full max-w-sm animate-fade-up" style={{ animationDelay: '0.2s' }}>
        {step === 'phone' ? (
          <>
            <div className="flex items-center gap-3 mb-4">
              <HiPhone className="text-green-400 text-xl" />
              <h2 className="text-lg font-semibold">فون نمبر</h2>
            </div>
            <input
              type="tel"
              value={phone}
              onChange={(e) => setPhone(e.target.value.replace(/\D/g, ''))}
              placeholder="03001234567"
              maxLength={11}
              className="w-full bg-slate-800 border border-slate-600 rounded-xl px-4 py-4 text-xl text-center tracking-widest focus:border-green-500 focus:outline-none transition-colors"
              dir="ltr"
            />
            <button
              onClick={handleSendOtp}
              disabled={loading || phone.length !== 11}
              className="w-full mt-4 bg-green-600 hover:bg-green-500 disabled:bg-slate-700 disabled:text-slate-500 text-white py-4 rounded-xl text-lg font-semibold flex items-center justify-center gap-2 transition-all"
            >
              {loading ? (
                <div className="w-5 h-5 border-2 border-white border-t-transparent rounded-full animate-spin" />
              ) : (
                <>OTP بھیجیں <HiArrowRight /></>
              )}
            </button>
          </>
        ) : (
          <>
            <div className="flex items-center gap-3 mb-2">
              <HiKey className="text-amber-400 text-xl" />
              <h2 className="text-lg font-semibold">OTP درج کریں</h2>
            </div>
            <p className="text-slate-400 text-sm mb-4">{phone} پر بھیجا گیا</p>
            <input
              type="tel"
              value={otp}
              onChange={(e) => setOtp(e.target.value.replace(/\D/g, ''))}
              placeholder="000000"
              maxLength={6}
              className="w-full bg-slate-800 border border-slate-600 rounded-xl px-4 py-4 text-2xl text-center tracking-[0.5em] focus:border-green-500 focus:outline-none transition-colors"
              dir="ltr"
            />
            <button
              onClick={handleVerifyOtp}
              disabled={loading || otp.length !== 6}
              className="w-full mt-4 bg-green-600 hover:bg-green-500 disabled:bg-slate-700 disabled:text-slate-500 text-white py-4 rounded-xl text-lg font-semibold flex items-center justify-center gap-2 transition-all"
            >
              {loading ? (
                <div className="w-5 h-5 border-2 border-white border-t-transparent rounded-full animate-spin" />
              ) : (
                <>لاگ ان <HiArrowRight /></>
              )}
            </button>
            <button onClick={() => { setStep('phone'); setOtp(''); }} className="w-full mt-2 text-slate-400 text-sm py-2">
              ← نمبر بدلیں
            </button>
          </>
        )}

        {error && (
          <p className="mt-3 text-red-400 text-sm text-center animate-slide-in">❌ {error}</p>
        )}
      </div>
    </div>
  );
}
