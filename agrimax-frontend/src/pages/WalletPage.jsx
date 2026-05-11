import { useState, useEffect } from 'react';
import { walletApi } from '../services/api';
import useAuthStore from '../store/useAuthStore';
import { HiArrowTrendingUp, HiArrowTrendingDown, HiWallet } from 'react-icons/hi2';

export default function WalletPage() {
  const [wallet, setWallet] = useState(null);
  const [transactions, setTransactions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const { isAuthenticated } = useAuthStore();

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
      setError('والیٹ لوڈ نہیں ہو سکا');
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="w-8 h-8 border-2 border-green-500 border-t-transparent rounded-full animate-spin" />
      </div>
    );
  }

  return (
    <div className="space-y-5">
      {/* Balance Card */}
      <div className="glass-card p-6 text-center animate-fade-up"
           style={{ background: 'linear-gradient(135deg, rgba(22,163,74,0.15) 0%, rgba(16,185,129,0.05) 100%)' }}>
        <div className="flex items-center justify-center gap-2 mb-2">
          <HiWallet className="text-green-400 text-2xl" />
          <p className="text-slate-400 text-sm urdu-text">آپ کا بیلنس</p>
        </div>
        <p className="text-4xl font-bold text-green-400 mt-1">
          ₨ {wallet ? Number(wallet.balance).toLocaleString('en-PK') : '0'}
        </p>
        <p className="text-slate-500 text-xs mt-2">{wallet?.currency || 'PKR'} • Pakistani Rupees</p>
      </div>

      {/* Error */}
      {error && (
        <div className="glass-card p-4 border-red-500/30 text-center">
          <p className="text-red-400 urdu-text">❌ {error}</p>
          <button onClick={loadWallet} className="text-green-400 text-sm mt-2">
            دوبارہ کوشش کریں
          </button>
        </div>
      )}

      {/* Transactions */}
      <div>
        <h3 className="text-lg font-semibold mb-3 urdu-text">📋 لین دین</h3>
        {transactions.length === 0 ? (
          <div className="glass-card p-8 text-center">
            <span className="text-4xl">💸</span>
            <p className="text-slate-400 mt-3 urdu-text">ابھی کوئی لین دین نہیں</p>
            <p className="text-slate-500 text-sm mt-1">No transactions yet</p>
            <p className="text-green-400 text-sm mt-4 urdu-text">
              🎤 بولیں: &ldquo;فصل بیچنی ہے&rdquo;
            </p>
          </div>
        ) : (
          <div className="space-y-2">
            {transactions.map((tx, i) => {
              // Fix: Transaction uses createdAt, not date
              const txDate = tx.createdAt || tx.date;
              const formattedDate = txDate
                ? new Date(txDate).toLocaleDateString('en-PK', {
                    day: 'numeric', month: 'short', year: 'numeric'
                  })
                : '—';

              return (
                <div
                  key={tx.id || i}
                  className="glass-card p-4 flex items-center justify-between animate-fade-up"
                  style={{ animationDelay: `${i * 0.05}s` }}
                >
                  <div className="flex items-center gap-3">
                    <div className={`w-9 h-9 rounded-full flex items-center justify-center ${
                      tx.type === 'CREDIT'
                        ? 'bg-green-500/20'
                        : 'bg-red-500/20'
                    }`}>
                      {tx.type === 'CREDIT'
                        ? <HiArrowTrendingUp className="text-green-400" />
                        : <HiArrowTrendingDown className="text-red-400" />
                      }
                    </div>
                    <div>
                      <p className="font-medium text-sm">
                        {tx.description || tx.type}
                      </p>
                      <p className="text-slate-500 text-xs">{formattedDate}</p>
                    </div>
                  </div>
                  <p className={`font-bold text-lg ${
                    tx.type === 'CREDIT' ? 'text-green-400' : 'text-red-400'
                  }`}>
                    {tx.type === 'CREDIT' ? '+' : '-'}₨{' '}
                    {Number(tx.amount).toLocaleString('en-PK')}
                  </p>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}
