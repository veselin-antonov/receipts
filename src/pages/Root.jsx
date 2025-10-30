import { Navigate } from 'react-router';

import { useAuth } from '@/components/auth/AuthContext';

const Root = () => {
  const { isAuthenticated } = useAuth();

  if (!isAuthenticated()) {
    console.log('Redirecting to login');
    return <Navigate to="/login" />;
  } else {
    console.log('Redirecting to purchases');
    return <Navigate to="/purchases" />;
  }
};

export default Root;
