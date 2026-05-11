import axios from 'axios';

const BASE_URL = import.meta.env.VITE_API_BASE_URL || '';

const api = axios.create({
  baseURL: `${BASE_URL}/api/v1`,
  headers: { 'Content-Type': 'application/json' },
  timeout: 15000, // 15 second timeout
});

// ── JWT interceptor — auto-attach token ──
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('agrimax_token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// ── Response interceptor — handle 401 + network errors ──
api.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response?.status === 401) {
      // Don't redirect for auth endpoints — let LoginPage handle OTP errors itself
      const url = err.config?.url || '';
      const isAuthEndpoint = url.includes('/auth/');
      if (!isAuthEndpoint) {
        localStorage.removeItem('agrimax_token');
        localStorage.removeItem('agrimax_refresh_token');
        localStorage.removeItem('agrimax_user');
        window.location.href = '/login';
      }
    }
    // Network error (backend down, timeout, etc.)
    if (!err.response) {
      err.message = 'Server not reachable. Please check your connection.';
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
  /** Send a text transcript to the AI pipeline. Returns full ApiResponse. */
  sendCommand: (transcript) => api.post('/voice/command', { transcript }),
};

// ── AI APIs (all routed through backend — no direct Gemini calls from frontend) ──
export const aiApi = {
  /** GET daily agricultural tip (Urdu + English, Gemini-powered, cached daily). */
  getDailyTip: () => api.get('/ai/tip'),
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

// ── User / Profile APIs ──
export const userApi = {
  getProfile: () => api.get('/users/profile'),
  updateProfile: (name) => api.patch('/users/profile', { name }),
};

export default api;
