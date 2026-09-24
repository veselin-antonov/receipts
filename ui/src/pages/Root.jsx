import { Navigate } from 'react-router';

import { useAuth } from '@/components/auth/AuthContext';
import { PageLoader } from '@/components/ui/page-loader';

const Root = () => {
  const { isAuthInProgress, isAuthenticated } = useAuth();

  if (isAuthInProgress()) {
    console.log('Auth in progress.. waiting to redirect from root');
    return <PageLoader className="animate-spin" />;
  }

  if (!isAuthenticated()) {
    console.log('Redirecting to login');
    return <Navigate to="/login" />;
  } else {
    console.log('Redirecting to purchases');
    return <Navigate to="/purchases" />;
  }
};

export default Root;
