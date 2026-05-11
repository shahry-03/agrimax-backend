import { useState, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { authApi } from '../services/api';
import useAuthStore from '../store/useAuthStore';
import { HiPhone, HiKey, HiArrowRight, HiMicrophone } from 'react-icons/hi2';
import useVoice from '../hooks/useVoice';

export default function LoginPage() {
  const navigate = useNavigate();
  const { login } = useAuthStore();
  const { startListening } = useVoice();

  const [step, setStep] = useState('phone'); // 'phone' | 'otp'
  const [phone, setPhone] = useState('');
  const [otp, setOtp] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [resendCooldown, setResendCooldown] = useState(0);
  const resendTimerRef = useRef(null);

  // Start 60-second resend cooldown
  const startResendCooldown = () => {
    setResendCooldown(60);
    resendTimerRef.current = setInterval(() => {
      setResendCooldown((c) => {
        if (c <= 1) {
          clearInterval(resendTimerRef.current);
          return 0;
        }
        return c - 1;
      });
    }, 1000);
  };

  const handleSendOtp = async () => {
    if (phone.length !== 11) {
      setError('11 ہندسے کا نمبر درج کریں (مثلاً 03001234567)');
      return;
    }
    setLoading(true);
    setError('');
    try {
      await authApi.sendOtp(phone);
      setStep('otp');
      startResendCooldown();
    } catch (err) {
      setError(err.response?.data?.messageUr || 'OTP نہیں بھیجا جا سکا۔ دوبارہ کوشش کریں۔');
    } finally {
      setLoading(false);
    }
  };

  const handleVerifyOtp = async () => {
    if (otp.length !== 6) {
      setError('6 ہندسے کا OTP درج کریں');
      return;
    }
    setLoading(true);
    setError('');
    try {
      const { data } = await authApi.verifyOtp(phone, otp);
      const d = data.data; // AuthResponse object

      // Fix: backend sends userId (not id), name, role, accessToken, refreshToken
      login(
        { id: d.userId, name: d.name, role: d.role },
        d.accessToken,
        d.refreshToken
      );
      navigate('/');
    } catch (err) {
      setError(err.response?.data?.messageUr || 'OTP غلط ہے یا میعاد ختم ہو گئی۔');
    } finally {
      setLoading(false);
    }
  };

  const handleResendOtp = async () => {
    if (resendCooldown > 0) return;
    setLoading(true);
    setError('');
    try {
      await authApi.sendOtp(phone);
      startResendCooldown();
      setOtp('');
    } catch (err) {
      setError(err.response?.data?.messageUr || 'OTP دوبارہ نہیں بھیجا جا سکا۔');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-900 flex flex-col items-center justify-center px-4">
      {/* Logo */}
      <div className="text-center mb-8 animate-fade-up">
        <span className="text-7xl block mb-2">🌾</span>
        <h1 className="text-3xl font-bold bg-gradient-to-r from-green-400 to-emerald-300 bg-clip-text text-transparent">
          AgriMax
        </h1>
        <p className="text-slate-400 mt-2 urdu-text">کسانوں کا ذہین ساتھی</p>
        <p className="text-slate-500 text-sm">Smart Partner for Farmers</p>
      </div>

      {/* Login Card */}
      <div className="glass-card p-6 w-full max-w-sm animate-fade-up" style={{ animationDelay: '0.2s' }}>

        {step === 'phone' ? (
          <>
            <div className="flex items-center gap-3 mb-5">
              <div className="w-9 h-9 rounded-full bg-green-500/20 flex items-center justify-center">
                <HiPhone className="text-green-400 text-lg" />
              </div>
              <div>
                <h2 className="text-lg font-semibold">فون نمبر</h2>
                <p className="text-slate-500 text-xs">Phone Number</p>
              </div>
            </div>

            <input
              id="phone-input"
              type="tel"
              value={phone}
              onChange={(e) => {
                setPhone(e.target.value.replace(/\D/g, ''));
                setError('');
              }}
              onKeyDown={(e) => e.key === 'Enter' && phone.length === 11 && handleSendOtp()}
              placeholder="03001234567"
              maxLength={11}
              className="w-full bg-slate-800 border border-slate-600 rounded-xl px-4 py-4 text-xl text-center tracking-widest focus:border-green-500 focus:outline-none focus:ring-2 focus:ring-green-500/20 transition-all"
              dir="ltr"
              autoFocus
            />

            <button
              id="send-otp-btn"
              onClick={handleSendOtp}
              disabled={loading || phone.length !== 11}
              className="w-full mt-4 bg-green-600 hover:bg-green-500 disabled:bg-slate-700 disabled:text-slate-500 text-white py-4 rounded-xl text-lg font-semibold flex items-center justify-center gap-2 transition-all active:scale-98"
            >
              {loading ? (
                <div className="w-5 h-5 border-2 border-white border-t-transparent rounded-full animate-spin" />
              ) : (
                <><span className="urdu-text">OTP بھیجیں</span> <HiArrowRight /></>
              )}
            </button>

            {/* Voice login hint */}
            <button
              onClick={startListening}
              className="w-full mt-3 flex items-center justify-center gap-2 text-slate-400 hover:text-green-400 text-sm py-2 transition-colors"
            >
              <HiMicrophone className="text-lg" />
              <span className="urdu-text">آواز سے لاگ ان کریں</span>
            </button>
          </>
        ) : (
          <>
            <div className="flex items-center gap-3 mb-3">
              <div className="w-9 h-9 rounded-full bg-amber-500/20 flex items-center justify-center">
                <HiKey className="text-amber-400 text-lg" />
              </div>
              <div>
                <h2 className="text-lg font-semibold urdu-text">OTP درج کریں</h2>
                <p className="text-slate-500 text-xs">Enter OTP</p>
              </div>
            </div>

            <p className="text-slate-400 text-sm mb-4 urdu-text">{phone} پر بھیجا گیا</p>

            <input
              id="otp-input"
              type="tel"
              value={otp}
              onChange={(e) => {
                setOtp(e.target.value.replace(/\D/g, ''));
                setError('');
              }}
              onKeyDown={(e) => e.key === 'Enter' && otp.length === 6 && handleVerifyOtp()}
              placeholder="000000"
              maxLength={6}
              className="w-full bg-slate-800 border border-slate-600 rounded-xl px-4 py-4 text-2xl text-center tracking-[0.5em] focus:border-green-500 focus:outline-none focus:ring-2 focus:ring-green-500/20 transition-all"
              dir="ltr"
              autoFocus
            />

            <button
              id="verify-otp-btn"
              onClick={handleVerifyOtp}
              disabled={loading || otp.length !== 6}
              className="w-full mt-4 bg-green-600 hover:bg-green-500 disabled:bg-slate-700 disabled:text-slate-500 text-white py-4 rounded-xl text-lg font-semibold flex items-center justify-center gap-2 transition-all"
            >
              {loading ? (
                <div className="w-5 h-5 border-2 border-white border-t-transparent rounded-full animate-spin" />
              ) : (
                <><span className="urdu-text">لاگ ان</span> <HiArrowRight /></>
              )}
            </button>

            {/* Resend OTP */}
            <div className="flex items-center justify-between mt-3">
              <button
                onClick={() => { setStep('phone'); setOtp(''); setError(''); }}
                className="text-slate-400 hover:text-white text-sm py-2 transition-colors urdu-text"
              >
                ← نمبر بدلیں
              </button>
              <button
                onClick={handleResendOtp}
                disabled={resendCooldown > 0 || loading}
                className="text-sm py-2 transition-colors disabled:text-slate-600 text-green-400 hover:text-green-300 urdu-text"
              >
                {resendCooldown > 0 ? `دوبارہ بھیجیں (${resendCooldown}s)` : 'دوبارہ بھیجیں'}
              </button>
            </div>
          </>
        )}

        {/* Error display */}
        {error && (
          <div className="mt-3 p-3 rounded-xl bg-red-500/10 border border-red-500/20 animate-slide-in">
            <p className="text-red-400 text-sm text-center urdu-text">❌ {error}</p>
          </div>
        )}
      </div>

      {/* Dev hint */}
      <p className="mt-6 text-slate-600 text-xs text-center">
        💡 OTP appears in backend console logs (dev mode)
      </p>
    </div>
  );
}
