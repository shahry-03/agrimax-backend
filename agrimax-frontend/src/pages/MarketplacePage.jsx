import { useState, useEffect } from 'react';
import { marketplaceApi } from '../services/api';
import useVoice from '../hooks/useVoice';
import { HiMicrophone, HiClock, HiMapPin } from 'react-icons/hi2';

// Local crop name map (since API returns cropId, we show a friendly name)
const CROP_NAMES = {
  default: { ur: 'فصل', en: 'Crop', emoji: '🌾' },
};

const STATUS_STYLES = {
  ACTIVE: 'bg-green-500/20 text-green-400',
  SOLD: 'bg-blue-500/20 text-blue-400',
  EXPIRED: 'bg-slate-500/20 text-slate-400',
  CANCELLED: 'bg-red-500/20 text-red-400',
};

export default function MarketplacePage() {
  const [posts, setPosts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const { processText } = useVoice();

  useEffect(() => {
    loadPosts();
  }, []);

  const loadPosts = async () => {
    setLoading(true);
    setError(null);
    try {
      const { data } = await marketplaceApi.getActivePosts();
      setPosts(data.data || []);
    } catch (err) {
      console.error('Failed to load posts', err);
      setError('مارکیٹ لوڈ نہیں ہو سکا');
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="space-y-3">
        {[1, 2, 3].map((i) => (
          <div key={i} className="glass-card p-4 animate-pulse">
            <div className="h-4 bg-slate-700 rounded w-1/3 mb-3" />
            <div className="flex justify-between">
              <div className="h-6 bg-slate-700 rounded w-1/2" />
              <div className="h-6 bg-slate-700 rounded w-1/4" />
            </div>
          </div>
        ))}
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h2 className="text-xl font-bold urdu-text">🛒 مارکیٹ</h2>
        <button
          onClick={loadPosts}
          className="text-xs text-slate-400 hover:text-white px-3 py-1.5 rounded-lg hover:bg-slate-700/50 transition-colors"
        >
          🔄 Refresh
        </button>
      </div>

      {error && (
        <div className="glass-card p-4 text-center border-red-500/20">
          <p className="text-red-400 urdu-text">❌ {error}</p>
          <button onClick={loadPosts} className="text-green-400 text-sm mt-2">
            دوبارہ کوشش کریں
          </button>
        </div>
      )}

      {/* Sell via voice prompt */}
      <button
        onClick={() => processText('Meri fasal bechni hai')}
        className="w-full glass-card p-4 border border-green-500/20 hover:border-green-500/50 flex items-center gap-3 transition-all group"
      >
        <div className="w-10 h-10 rounded-full bg-green-600/20 flex items-center justify-center group-hover:bg-green-600/40 transition-colors">
          <HiMicrophone className="text-green-400" />
        </div>
        <div className="text-left">
          <p className="text-green-400 font-medium urdu-text">🌾 اپنی فصل بیچیں</p>
          <p className="text-slate-400 text-xs">Say: "Meri fasal bechni hai"</p>
        </div>
      </button>

      {posts.length === 0 && !error ? (
        <div className="glass-card p-8 text-center">
          <span className="text-5xl">📭</span>
          <p className="text-slate-400 mt-3 urdu-text">ابھی کوئی پوسٹ نہیں ہے</p>
          <p className="text-slate-500 text-sm">No active listings</p>
        </div>
      ) : (
        <div className="space-y-3">
          {posts.map((post, i) => {
            const formattedDate = post.createdAt
              ? new Date(post.createdAt).toLocaleDateString('en-PK', {
                  day: 'numeric', month: 'short'
                })
              : '';

            return (
              <div
                key={post.id}
                className="glass-card p-4 animate-fade-up"
                style={{ animationDelay: `${i * 0.05}s` }}
              >
                <div className="flex items-center justify-between mb-3">
                  <div className="flex items-center gap-2">
                    <span className="text-2xl">🌾</span>
                    <div>
                      {/* Show crop name if available, otherwise quantity/unit */}
                      <p className="font-semibold">
                        {post.cropNameEn || post.cropName || 'Crop'}
                        {post.cropNameUr && (
                          <span className="text-slate-400 text-sm urdu-text ml-1">({post.cropNameUr})</span>
                        )}
                      </p>
                      <p className="text-slate-400 text-xs">
                        {post.quantity} {post.unit}
                      </p>
                    </div>
                  </div>
                  <p className="text-green-400 font-bold text-xl">
                    ₨ {Number(post.askingPrice).toLocaleString('en-PK')}
                  </p>
                </div>

                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-3">
                    <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${STATUS_STYLES[post.status] || STATUS_STYLES.ACTIVE}`}>
                      {post.status}
                    </span>
                    {post.location && (
                      <span className="text-slate-500 text-xs flex items-center gap-1">
                        <HiMapPin className="text-xs" /> {post.location}
                      </span>
                    )}
                  </div>
                  {formattedDate && (
                    <span className="text-slate-600 text-xs flex items-center gap-1">
                      <HiClock className="text-xs" /> {formattedDate}
                    </span>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
