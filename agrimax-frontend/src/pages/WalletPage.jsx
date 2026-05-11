import { useState, useEffect } from 'react';
import { walletApi } from '../services/api';
import useAuthStore from '../store/useAuthStore';
import { useNavigate } from 'react-router-dom';

export default function WalletPage() {
  const [wallet, setWallet] = useState(null);
  const [transactions, setTransactions] = useState([]);
  const [loading, setLoading] = useState(true);
  const { isAuthenticated } = useAuthStore();
  const navigate = useNavigate();

  useEffect(() => {
    if (isAuthenticated) loadWallet();
    else setLoading(false);
  }, [isAuthenticated]);

  const loadWallet = async () => {
    try {
      const [walletRes, txRes] = await Promise.all([
        walletApi.getBalance(),
        walletApi.getTransactions(),
      ]);
      setWallet(walletRes.data.data);
      setTransactions(txRes.data.data || []);
    } catch (err) {
      console.error('Wallet load failed', err);
    } finally { setLoading(false); }
  };

  if (!isAuthenticated) {
    return (
      <div className="flex flex-col items-center justify-center h-64 gap-4">
        <span className="text-5xl">👛</span>
        <p className="text-slate-400">والیٹ دیکھنے کے لیے لاگ ان کریں</p>
        <button onClick={() => navigate('/login')} className="bg-green-600 text-white px-6 py-3 rounded-xl font-semibold">
          لاگ ان کریں
        </button>
      </div>
    );
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="w-8 h-8 border-3 border-green-500 border-t-transparent rounded-full animate-spin" />
      </div>
    );
  }

  return (
    <div className="space-y-5">
      {/* Balance Card */}
      <div className="glass-card p-6 text-center animate-fade-up bg-gradient-to-br from-green-500/10 to-emerald-500/5">
        <p className="text-slate-400 text-sm">آپ کا بیلنس</p>
        <p className="text-4xl font-bold text-green-400 mt-2">
          ₨ {wallet ? Number(wallet.balance).toLocaleString() : '0'}
        </p>
        <p className="text-slate-500 text-xs mt-1">{wallet?.currency || 'PKR'}</p>
      </div>

      {/* Transactions */}
      <div>
        <h3 className="text-lg font-semibold mb-3">📋 لین دین</h3>
        {transactions.length === 0 ? (
          <div className="glass-card p-6 text-center">
            <p className="text-slate-400">ابھی کوئی لین دین نہیں</p>
            <p className="text-slate-500 text-sm">No transactions yet</p>
          </div>
        ) : (
          <div className="space-y-2">
            {transactions.map((tx, i) => (
              <div key={i} className="glass-card p-4 flex items-center justify-between animate-fade-up"
                   style={{ animationDelay: `${i * 0.05}s` }}>
                <div>
                  <p className="font-medium">{tx.description || tx.type}</p>
                  <p className="text-slate-500 text-xs">{new Date(tx.date).toLocaleDateString('ur-PK')}</p>
                </div>
                <p className={`font-bold ${tx.type === 'CREDIT' ? 'text-green-400' : 'text-red-400'}`}>
                  {tx.type === 'CREDIT' ? '+' : '-'}₨ {Number(tx.amount).toLocaleString()}
                </p>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
