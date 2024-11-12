import { useAuth } from '@/components/auth/AuthContext';
import { Navigate, Outlet } from 'react-router-dom';

export const PublicRoute = () => {
  const { isAuthenticated } = useAuth();

  const authenticated = isAuthenticated();

  console.log(
    authenticated
      ? 'Authenticated. Redirecting to dashboard...'
      : 'Not authenticated. Rendering public page...'
  );

  return !authenticated ? <Outlet /> : <Navigate to='/purchases' />;
};
