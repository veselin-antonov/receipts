import { AuthLayout } from '@/components/auth/AuthLayout';
import { PrivateRoute } from '@/components/auth/PrivateRoute';
import { Login } from '@/pages/login/Login';
import { Purchases } from '@/pages/purchases/Purchases';
import { Register } from '@/pages/register/Register';
import { Root } from '@/pages/Root';
import {
  createBrowserRouter,
  createRoutesFromElements,
  RouterProvider,
  Route,
} from 'react-router-dom';
import { PublicRoute } from "@/components/auth/PublicRoute.jsx";

const router = createBrowserRouter(
  createRoutesFromElements(
    <Route element={<AuthLayout />}>
      <Route path='/' element={<Root />} />
      <Route element={<PublicRoute />}>
        <Route path='/login' element={<Login />} />
        <Route path='/register' element={<Register />} />
      </Route>

      <Route element={<PrivateRoute />}>
        <Route path='/purchases' element={<Purchases />} />
      </Route>
    </Route>
  )
);

const App = () => {
  return <RouterProvider router={router} />;
};

export default App;
