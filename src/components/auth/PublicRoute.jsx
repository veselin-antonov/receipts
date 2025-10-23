import { useAuth } from '@/components/auth/AuthContext';
import { Navigate, Outlet } from 'react-router';

export const PublicRoute = () => {
  const { isAuthenticated } = useAuth();

  const authenticated = isAuthenticated();

  console.debug(
    authenticated
      ? 'Authenticated. Redirecting to dashboard...'
      : 'Not authenticated. Rendering public page...'
  );

  return !authenticated ? <Outlet /> : <Navigate to="/purchases" />;
};
