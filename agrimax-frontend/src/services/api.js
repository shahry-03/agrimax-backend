import axios from 'axios';

const api = axios.create({
  baseURL: '/api/v1',
  headers: { 'Content-Type': 'application/json' },
});

// JWT interceptor — auto-attach token
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('agrimax_token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Response interceptor — handle 401
api.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response?.status === 401) {
      localStorage.removeItem('agrimax_token');
      localStorage.removeItem('agrimax_user');
      window.location.href = '/login';
    }
    return Promise.reject(err);
  }
);

// ── Auth APIs ──
export const authApi = {
  sendOtp: (phoneNumber) => api.post('/auth/send-otp', { phoneNumber }),
  verifyOtp: (phoneNumber, otp) => api.post('/auth/verify-otp', { phoneNumber, otp }),
};

// ── Voice API ──
export const voiceApi = {
  sendCommand: (transcript) => api.post('/voice/command', { transcript }),
};

// ── Catalog APIs ──
export const catalogApi = {
  listCrops: () => api.get('/crops/public/list'),
  getCropPrice: (cropId) => api.get(`/crops/public/${cropId}/price`),
};

// ── Marketplace APIs ──
export const marketplaceApi = {
  getActivePosts: () => api.get('/marketplace/posts'),
  getMyPosts: () => api.get('/marketplace/my-posts'),
};

// ── Wallet APIs ──
export const walletApi = {
  getBalance: () => api.get('/wallet/balance'),
  getTransactions: () => api.get('/wallet/transactions'),
};

export default api;
