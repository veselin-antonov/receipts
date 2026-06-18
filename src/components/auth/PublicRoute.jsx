import { Navigate, Outlet } from 'react-router';

import { useAuth } from '@/components/auth/AuthContext';
import { PageLoader } from '@/components/ui/page-loader';

export const PublicRoute = () => {
  const { isAuthInProgress, isAuthenticated } = useAuth();

  if (isAuthInProgress()) {
    console.debug('Auth in progress.. waiting to render the public route');
    return <PageLoader className="animate-spin" />;
  }

  const authenticated = isAuthenticated();

  console.debug(
    authenticated
      ? 'Authenticated. Redirecting to dashboard...'
      : 'Not authenticated. Rendering public page...'
  );

  return !authenticated ? <Outlet /> : <Navigate to="/purchases" />;
};
