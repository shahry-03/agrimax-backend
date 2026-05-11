import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { Toaster } from 'react-hot-toast';
import MainLayout from './layouts/MainLayout';
import SplashScreen from './pages/SplashScreen';
import HomePage from './pages/HomePage';
import LoginPage from './pages/LoginPage';
import PricesPage from './pages/PricesPage';
import MarketplacePage from './pages/MarketplacePage';
import WalletPage from './pages/WalletPage';
import ProfilePage from './pages/ProfilePage';
import NotificationsPage from './pages/NotificationsPage';
import NotFoundPage from './pages/NotFoundPage';
import ProtectedRoute from './components/ProtectedRoute';
import useAuthStore from './store/useAuthStore';
import { useState, useEffect } from 'react';

export default function App() {
  const { isAuthenticated } = useAuthStore();

  // Use sessionStorage so splash only shows once per browser session (not every hot-reload)
  const [showSplash, setShowSplash] = useState(
    () => !sessionStorage.getItem('agrimax_splash_seen')
  );

  const handleSplashFinish = () => {
    sessionStorage.setItem('agrimax_splash_seen', '1');
    setShowSplash(false);
  };

  if (showSplash) {
    return <SplashScreen onFinish={handleSplashFinish} />;
  }

  return (
    <BrowserRouter>
      <Toaster
        position="top-center"
        toastOptions={{
          style: {
            background: '#1e293b',
            color: '#f8fafc',
            border: '1px solid rgba(148,163,184,0.15)',
          },
          duration: 3000,
        }}
      />
      <Routes>
        {/* Public route — redirect to home if already logged in */}
        <Route
          path="/login"
          element={isAuthenticated ? <Navigate to="/" replace /> : <LoginPage />}
        />

        {/* Protected routes — all inside MainLayout */}
        <Route element={<ProtectedRoute />}>
          <Route element={<MainLayout />}>
            <Route path="/" element={<HomePage />} />
            <Route path="/prices" element={<PricesPage />} />
            <Route path="/marketplace" element={<MarketplacePage />} />
            <Route path="/wallet" element={<WalletPage />} />
            <Route path="/profile" element={<ProfilePage />} />
            <Route path="/notifications" element={<NotificationsPage />} />
          </Route>
        </Route>

        {/* 404 catch-all */}
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </BrowserRouter>
  );
}
