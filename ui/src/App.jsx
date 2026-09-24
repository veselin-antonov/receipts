import { BrowserRouter, Route, Routes } from 'react-router';

import { AuthProvider } from '@/components/auth/AuthContext';
import { PrivateRoute } from '@/components/auth/PrivateRoute';
import { PublicRoute } from '@/components/auth/PublicRoute.jsx';
import { Login } from '@/pages/Login';
import { NotVerified } from '@/pages/NotVerified';
import { Purchases } from '@/pages/Purchases';
import { Register } from '@/pages/Register';
import Root from '@/pages/Root';
import SendVerification from '@/pages/SendVerification';
import VerifyAccount from '@/pages/VerifyAccount';

const App = () => {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/*" element={<Root />} />
          <Route element={<PublicRoute />}>
            <Route path="/login" element={<Login />} />
            <Route path="/register" element={<Register />} />
            <Route path="/verify" element={<VerifyAccount />} />
            <Route path="/resend-verification" element={<SendVerification />} />
            <Route path="/not-verified" element={<NotVerified />} />
          </Route>

          <Route element={<PrivateRoute />}>
            <Route path="/purchases" element={<Purchases />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
};

export default App;
