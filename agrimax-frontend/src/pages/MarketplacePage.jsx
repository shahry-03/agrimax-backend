import { useState, useEffect } from 'react';
import { marketplaceApi } from '../services/api';
import useAuthStore from '../store/useAuthStore';
import { useNavigate } from 'react-router-dom';

export default function MarketplacePage() {
  const [posts, setPosts] = useState([]);
  const [loading, setLoading] = useState(true);
  const { isAuthenticated } = useAuthStore();
  const navigate = useNavigate();

  useEffect(() => {
    loadPosts();
  }, []);

  const loadPosts = async () => {
    try {
      const { data } = await marketplaceApi.getActivePosts();
      setPosts(data.data || []);
    } catch (err) {
      console.error('Failed to load posts', err);
    } finally {
      setLoading(false);
    }
  };

  if (!isAuthenticated) {
    return (
      <div className="flex flex-col items-center justify-center h-64 gap-4">
        <span className="text-5xl">🔒</span>
        <p className="text-slate-400 text-center">مارکیٹ دیکھنے کے لیے لاگ ان کریں</p>
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
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h2 className="text-xl font-bold">🛒 مارکیٹ</h2>
        <span className="text-xs text-slate-400">Marketplace</span>
      </div>

      {posts.length === 0 ? (
        <div className="glass-card p-8 text-center">
          <span className="text-5xl">📭</span>
          <p className="text-slate-400 mt-3">ابھی کوئی پوسٹ نہیں ہے</p>
          <p className="text-slate-500 text-sm">No posts yet</p>
          <p className="text-green-400 text-sm mt-4">🎤 بولیں: "میری فصل بیچنی ہے"</p>
        </div>
      ) : (
        <div className="space-y-3">
          {posts.map((post, i) => (
            <div key={post.id} className="glass-card p-4 animate-fade-up" style={{ animationDelay: `${i * 0.05}s` }}>
              <div className="flex items-center justify-between mb-2">
                <span className={`px-2 py-0.5 rounded text-xs font-medium ${
                  post.status === 'ACTIVE' ? 'bg-green-500/20 text-green-400' : 'bg-slate-500/20 text-slate-400'
                }`}>{post.status}</span>
                <span className="text-slate-500 text-xs">{post.unit}</span>
              </div>
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-lg font-semibold">{post.quantity} {post.unit}</p>
                  <p className="text-slate-400 text-xs">Crop ID: {post.cropId?.slice(0, 8)}...</p>
                </div>
                <p className="text-green-400 font-bold text-xl">₨ {Number(post.askingPrice).toLocaleString()}</p>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
