import React from 'react';
import { Link } from 'react-router';

import LoginForm from '@/components/login/LoginForm';
import {
  Card,
  CardContent,
  CardFooter,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';

export const Login = React.memo(() => {
  console.log('Rendering login page');

  return (
    <div className="flex min-h-svh w-full items-center justify-center">
      <Card className="w-[60%] max-w-[600px] min-w-[400px]">
        <CardHeader className="place-items-center gap-1 p-6">
          <img src="logo.svg" alt="logo-image" className="w-12" />
          <CardTitle className="text-3xl font-semibold">Влизане</CardTitle>
        </CardHeader>
        <CardContent className="flex w-full justify-center">
          <LoginForm />
        </CardContent>
        <CardFooter className="mt-4 flex h-4 flex-row items-center justify-center gap-2">
          <div>
            Нямате акаунт?{' '}
            <Link to="/register" className="text-primary">
              Регистрирайте се
            </Link>
          </div>
        </CardFooter>
      </Card>
    </div>
  );
});

Login.displayName = 'Login';
