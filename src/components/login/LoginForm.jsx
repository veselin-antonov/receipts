import { zodResolver } from '@hookform/resolvers/zod';
import { Loader2 } from 'lucide-react';
import { useCallback, useState } from 'react';
import { useForm } from 'react-hook-form';
import { useNavigate } from 'react-router';
import { z } from 'zod';

import { AUTH_STATUS, useAuth } from '@/components/auth/AuthContext';
import FormInput from '@/components/forms/form-input';
import PasswordInput from '@/components/forms/password-input';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { API_URL } from '@/lib/utils';

const formSchema = z.object({
  email: z.string().min(1, 'Имейлът е задължителен').email('Невалиден имейл'),
  password: z.string().min(1, 'Паролата е задължителна'),
});

const LoginForm = () => {
  console.log('Rendering LoginForm component...');
  const [loginError, setLoginError] = useState();
  const { setAuthStatus, isAuthInProgress, saveAuthExpiration } = useAuth();

  const navigate = useNavigate();

  // Authenticate via the API
  const login = useCallback(
    async (email, password) => {
      setAuthStatus(AUTH_STATUS.PENDING); // Set status to PENDING
      setLoginError(null); // Clear any previous errors

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
          } else if (response.status === 403) {
            console.log('Navigating to /not-verified');
            navigate('/not-verified');
            return Promise.reject(new Error('Account not verified!'));
          } else if (response.status === 401) {
            throw new Error(
              'Неправилни имейл и/или парола. Моля опитайте отново.'
            );
          } else if (response.status === 429) {
            throw new Error(
              'Твърде много неуспешни опити за влизане. Моля опитайте по-късно.'
            );
          } else {
            throw new Error(
              'Неизвестна грешка от сървъра. Моля опитайте по-късно.'
            );
          }
        })
        .then((responseText) => {
          const expirationPeriod = Number.parseInt(responseText);

          console.log('Authenticated for', expirationPeriod / 1000, 'seconds');

          // Save expiration (this will set status to AUTHENTICATED)
          saveAuthExpiration(expirationPeriod);
          navigate('/purchases');
        })
        .catch((error) => {
          console.error('Login error:', error);
          setLoginError(error.message);
          setAuthStatus(AUTH_STATUS.UNAUTHENTICATED); // Set error status
        });
    },
    [setAuthStatus, saveAuthExpiration, navigate]
  );

  const form = useForm({
    resolver: zodResolver(formSchema),
    defaultValues: {
      email: '',
      password: '',
    },
    reValidateMode: 'onChange',
  });

  const onValidForm = useCallback(
    (data) => {
      login(data.email, data.password);
    },
    [login]
  );

  const onSubmit = useCallback(
    (data) => form.handleSubmit(onValidForm)(data),
    [form, onValidForm]
  );

  return (
    <form onSubmit={onSubmit} className="flex w-sm flex-col gap-4">
      {loginError && (
        <Alert variant="destructive" className="mb-2 border-2 text-center">
          <AlertDescription className="font-semibold">
            {loginError}
          </AlertDescription>
        </Alert>
      )}
      <FormInput
        form={form}
        label="Имейл"
        fieldName="email"
        autoComplete="username"
      />
      <PasswordInput
        form={form}
        inputProps={{
          label: 'Парола',
          fieldName: 'password',
          forgotPassword: true,
        }}
      />
      <Button disabled={isAuthInProgress()} type="submit" className="text-lg">
        {isAuthInProgress() ? <Loader2 className="animate-spin" /> : 'Влизане'}
      </Button>
    </form>
  );
};

export default LoginForm;
