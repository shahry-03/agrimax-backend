import { Navigate, Outlet } from 'react-router-dom';
import useAuthStore from '../store/useAuthStore';

/**
 * ProtectedRoute — redirects to /login if not authenticated.
 * Wraps protected routes in the BrowserRouter.
 */
export default function ProtectedRoute() {
  const { isAuthenticated } = useAuthStore();
  return isAuthenticated ? <Outlet /> : <Navigate to="/login" replace />;
}
