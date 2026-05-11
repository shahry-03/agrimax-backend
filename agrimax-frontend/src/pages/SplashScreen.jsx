import { useState, useEffect } from 'react';

export default function SplashScreen({ onFinish }) {
  const [progress, setProgress] = useState(0);

  useEffect(() => {
    const timer = setInterval(() => {
      setProgress((p) => {
        if (p >= 100) { clearInterval(timer); setTimeout(onFinish, 300); return 100; }
        return p + 5;
      });
    }, 50);
    return () => clearInterval(timer);
  }, [onFinish]);

  return (
    <div className="fixed inset-0 bg-slate-900 flex flex-col items-center justify-center z-50">
      <div className="animate-fade-up text-center">
        <span className="text-7xl block mb-4">🌾</span>
        <h1 className="text-4xl font-bold bg-gradient-to-r from-green-400 via-emerald-300 to-green-400 bg-clip-text text-transparent">
          AgriMax
        </h1>
        <p className="text-slate-400 mt-2 text-lg">کسانوں کا ذہین ساتھی</p>
        <p className="text-slate-500 text-sm mt-1">AI-Powered Smart Farming</p>
      </div>

      <div className="w-48 h-1 bg-slate-700 rounded-full mt-10 overflow-hidden">
        <div
          className="h-full bg-gradient-to-r from-green-500 to-emerald-400 rounded-full transition-all duration-100"
          style={{ width: `${progress}%` }}
        />
      </div>
      <p className="text-slate-500 text-xs mt-3">Loading... {progress}%</p>
    </div>
  );
}
