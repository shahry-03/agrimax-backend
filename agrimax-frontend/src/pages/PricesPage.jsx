import { useState, useEffect } from 'react';
import { catalogApi } from '../services/api';
import { HiArrowTrendingUp, HiArrowTrendingDown } from 'react-icons/hi2';

const CROP_EMOJIS = {
  Wheat: '🌾', Rice: '🍚', Corn: '🌽', Cotton: '🧶', Sugarcane: '🎋',
  Mustard: '🌿', Chickpea: '🫘', Lentil: '🍲', Mango: '🥭',
  Potato: '🥔', Onion: '🧅', Tomato: '🍅',
};

export default function PricesPage() {
  const [crops, setCrops] = useState([]);
  const [prices, setPrices] = useState({});
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    try {
      const { data } = await catalogApi.listCrops();
      const cropList = data.data || [];
      setCrops(cropList);
      // Load prices for each crop
      const priceMap = {};
      await Promise.all(
        cropList.map(async (crop) => {
          try {
            const { data: priceData } = await catalogApi.getCropPrice(crop.id);
            if (priceData.data) priceMap[crop.id] = priceData.data;
          } catch {}
        })
      );
      setPrices(priceMap);
    } catch (err) {
      console.error('Failed to load crops', err);
    } finally {
      setLoading(false);
    }
  };

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
        <h2 className="text-xl font-bold">📊 آج کی قیمتیں</h2>
        <span className="text-xs text-slate-400">Today's Prices</span>
      </div>

      <div className="space-y-3">
        {crops.map((crop, i) => {
          const price = prices[crop.id];
          const emoji = CROP_EMOJIS[crop.nameEn] || '🌱';
          return (
            <div
              key={crop.id}
              className="glass-card p-4 flex items-center gap-4 animate-fade-up"
              style={{ animationDelay: `${i * 0.05}s` }}
            >
              <span className="text-3xl">{emoji}</span>
              <div className="flex-1">
                <p className="font-semibold text-white">{crop.nameUr}</p>
                <p className="text-slate-400 text-xs">{crop.nameEn} • {crop.unit}</p>
              </div>
              {price ? (
                <div className="text-right">
                  <p className="text-green-400 font-bold text-lg">
                    ₨ {Number(price.avgPrice).toLocaleString()}
                  </p>
                  <p className="text-slate-400 text-xs">
                    {Number(price.minPrice).toLocaleString()} - {Number(price.maxPrice).toLocaleString()}
                  </p>
                  <p className="text-slate-500 text-[10px]">{price.marketName}</p>
                </div>
              ) : (
                <span className="text-slate-500 text-sm">—</span>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
