import { useAuth } from '@/components/auth/AuthContext';
import { PageLoader } from '@/components/common/page-loader';
import { Navigate, Outlet } from 'react-router';

export const PrivateRoute = () => {
  const { isAuthInProgress, isAuthenticated } = useAuth();

  if (isAuthInProgress()) {
    console.log('Auth in progress.. waiting to render the private route');
    return <PageLoader className="animate-spin" />;
  } else if (isAuthenticated()) {
    console.log('Authenticated. Rendering private route...');
    return <Outlet />;
  } else {
    console.log('Not authenticated. Redirecting to login...');
    return <Navigate to="/login" />;
  }
};
