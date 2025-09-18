import { useAuth } from '@/components/auth/AuthContext';
import { Button } from '@/components/common/button';
import { Form } from '@/components/common/form';
import FormInput from '@/components/forms/form-input';
import { zodResolver } from '@hookform/resolvers/zod';
import React from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { Link } from 'react-router';
import { Loader2 } from 'lucide-react';
import { Separator } from '@/components/common/separator';
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from '@/components/common/card';
import { API_URL } from '@/lib/utils';
import PasswordInput from '@/components/forms/password-input';

const formSchema = z.object({
  email: z.string().email(),
  password: z.string().min(1),
  passwordConfirmation: z.string().min(1),
});

export const Register = () => {
  const { isAuthInProgress, setIsAuthInProgress } = useAuth();

  const form = useForm({
    resolver: zodResolver(formSchema),
    defaultValues: {
      email: '',
      password: '',
      passwordConfirmation: '',
    },
  });

  const onSubmit = (data) => {
    register(data.email, data.password);
  };

  // An asynchronous function to register a new user
  async function register(email, password, passwordConfirmation) {
    setIsAuthInProgress(true);

    fetch(API_URL + '/register', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ email, password, passwordConfirmation }),
    })
      .then((response) => {
        if (response.ok) {
          return response.text();
        } else {
          throw new Error('Bad credentials');
        }
      })
      .catch((error) => console.error('Error:', error))
      .finally(() => setIsAuthInProgress(false));
  }

  return (
    <div>
      <Card className='min-w-[400px] w-[90%] max-w-[600px] mx-auto my-10 sm:mt-24 px-12 py-5'>
        <CardHeader className='items-center gap-1'>
          <img src='logo.svg' alt='logo-image' className='w-12' />
          <CardTitle className='text-3xl font-semibold'>Регистрация</CardTitle>
        </CardHeader>
        <CardContent>
          <Form {...form}>
            <form onSubmit={form.handleSubmit(onSubmit)} className='grid gap-4'>
              <FormInput label='Имейл' fieldName='email' autoComplete='username'/>
              <PasswordInput
                inputProps={{
                  label: 'Парола',
                  fieldName: 'password',
                }}
                confirmation={true}
                confirmationInputProps={{
                  label: 'Потвърдете паролата',
                  fieldName: 'passwordConfirmation',
                }}
              />
              <Button type='submit' className='text-lg'>
                {isAuthInProgress ? (
                  <Loader2 className='animate-spin' />
                ) : (
                  'Регистриране'
                )}
              </Button>
              <Separator />
              <span className='text-center'>
                Имате акаунт?{' '}
                <Link to='/login' className='text-center text-primary'>
                  Влезте
                </Link>
              </span>
            </form>
          </Form>
        </CardContent>
      </Card>
    </div>
  );
};
