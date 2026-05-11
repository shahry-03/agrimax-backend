import { create } from 'zustand';

const useAuthStore = create((set) => ({
  user: JSON.parse(localStorage.getItem('agrimax_user') || 'null'),
  token: localStorage.getItem('agrimax_token') || null,
  isAuthenticated: !!localStorage.getItem('agrimax_token'),

  login: (user, token, refreshToken) => {
    localStorage.setItem('agrimax_token', token);
    localStorage.setItem('agrimax_refresh_token', refreshToken);
    localStorage.setItem('agrimax_user', JSON.stringify(user));
    set({ user, token, isAuthenticated: true });
  },

  logout: () => {
    localStorage.removeItem('agrimax_token');
    localStorage.removeItem('agrimax_refresh_token');
    localStorage.removeItem('agrimax_user');
    set({ user: null, token: null, isAuthenticated: false });
  },

  updateUser: (user) => {
    localStorage.setItem('agrimax_user', JSON.stringify(user));
    set({ user });
  },
}));

export default useAuthStore;
