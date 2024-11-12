import { useAuth } from '@/components/auth/AuthContext';
import { Alert, AlertDescription } from '@/components/common/alert';
import { Button } from '@/components/common/button';
import { Form } from '@/components/common/form';
import FormInput from '@/components/forms/form-input';
import PasswordInput from '@/components/forms/password-input';
import { API_URL } from '@/lib/utils';
import { zodResolver } from '@hookform/resolvers/zod';
import { Loader2 } from 'lucide-react';
import { useCallback, useState } from 'react';
import { useForm } from 'react-hook-form';
import { useNavigate } from 'react-router-dom';
import { z } from 'zod';

const formSchema = z.object({
  email: z.string().min(1, 'Имейлът е задължителен').email('Невалиден имейл'),
  password: z.string().min(1, 'Паролата е задължителна'),
});

const LoginForm = () => {
  console.log('Rendering LoginForm component...');
  const [loginError, setLoginError] = useState();
  const { isAuthInProgress, setIsAuthInProgress, saveAuthExpiration } =
    useAuth();

  const navigate = useNavigate();

  // Authenticate via the API
  const login = useCallback(
    async (email, password) => {
      setIsAuthInProgress(true);

      fetch(API_URL + '/auth/token', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: 'Basic ' + btoa(email + ':' + password),
        },
      })
        .then((response) => {
          if (response.ok) {
            return response.text();
          } else {
            throw new Error(
              'Неправилни имейл и/или парола. Моля опитайте отново.'
            );
          }
        })
        .then((responseText) => {
          const expirationPeriod = Number.parseInt(responseText);

          console.log('Authenticated for', expirationPeriod / 1000, 'seconds');

          saveAuthExpiration(expirationPeriod);
          setIsAuthInProgress(false);
          navigate('/purchases');
        })
        .catch((error) => {
          console.error('Error:', error);
          setLoginError(error.message);
        })
        .finally(() => setIsAuthInProgress(false));
    },
    [setIsAuthInProgress, saveAuthExpiration, navigate]
  );

  const form = useForm({
    resolver: zodResolver(formSchema),
    defaultValues: {
      email: '',
      password: '',
    },
  });

  const onValidForm = useCallback(
    (data) => {
      login(data.email, data.password);
    },
    [login]
  );

  const onSubmit = useCallback(form.handleSubmit(onValidForm), [onValidForm]);

  return (
    <Form {...form}>
      {loginError && (
        <Alert variant='destructive' className='border-2 mb-2 text-center'>
          <AlertDescription className="font-semibold">{loginError}</AlertDescription>
        </Alert>
      )}
      <form onSubmit={onSubmit} className='grid gap-4'>
        <FormInput
          label='Имейл'
          fieldName={'email'}
          className='row-start-1'
          autoComplete='username'
        />
        <PasswordInput
          label='Парола'
          fieldName={'password'}
          className='row-start-2'
        />
        <Button disabled={isAuthInProgress} type='submit' className='text-lg'>
          {isAuthInProgress ? <Loader2 className='animate-spin' /> : 'Влизане'}
        </Button>
      </form>
    </Form>
  );
};

export default LoginForm;
