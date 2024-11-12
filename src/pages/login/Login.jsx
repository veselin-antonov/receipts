import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from '@/components/common/card';
import React from 'react';
import { Link } from 'react-router-dom';
import { Separator } from '@/components/common/separator';
import LoginForm from '@/components/login/LoginForm';

export const Login = React.memo(() => {
  console.log('Rendering login page');

  return (
    <div>
      <Card className='min-w-[400px] w-[90%] max-w-[600px] mx-auto mt-24 px-12 py-5'>
        <CardHeader className='items-center gap-1'>
          <img src="logo.svg" alt="logo-image" className='w-12'/>
          <CardTitle className='text-3xl font-semibold'>Влизане</CardTitle>
        </CardHeader>
        <CardContent>
          <LoginForm />
          <div className='flex justify-center items-center gap-5 mt-4'>
            <div>
              {/* <Link to='/forgot-password' className='text-primary'>
                Забравена парола?
              </Link> */}
              <span className='text-border'>Забравена парола?</span>
            </div>
            <Separator orientation='vertical' className='w-[2px] h-6' />
            <div>
              Нямате акаунт?{' '}
              <Link to='/register' className='text-primary'>
                Регистрирайте се
              </Link>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  );
});

Login.displayName = 'Login';
