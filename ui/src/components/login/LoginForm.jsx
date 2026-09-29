import { zodResolver } from '@hookform/resolvers/zod';
import { Loader2 } from 'lucide-react';
import { useCallback, useState } from 'react';
import { useForm } from 'react-hook-form';
import { useNavigate } from 'react-router';
import { z } from 'zod';

import { useAuth } from '@/components/auth/AuthContext';
import FormInput from '@/components/forms/form-input';
import PasswordInput from '@/components/forms/password-input';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { API_URL } from '@/lib/utils';

const formSchema = z.object({
  email: z.string().min(1, 'Имейлът е задължителен').email('Невалиден имейл'),
  password: z.string().min(1, 'Паролата е задължителна'),
});

// The API's ProblemDetail code for a correct login to an unverified account.
const ACCOUNT_NOT_VERIFIED = 'ACCOUNT_NOT_VERIFIED';

// Only the error code says the account is unverified; a bare 403 does not (D15).
const isAccountNotVerified = async (response) => {
  if (response.status !== 403) {
    return false;
  }
  try {
    const body = JSON.parse(await response.text());
    return body?.error === ACCOUNT_NOT_VERIFIED;
  } catch {
    return false;
  }
};

const LoginForm = () => {
  console.log('Rendering LoginForm component...');
  const [loginError, setLoginError] = useState();
  // A login attempt is local to this form. Setting the app-wide auth status to
  // PENDING would make PublicRoute swap the page for its loader, unmounting
  // this form and losing the error it is about to show.
  const [isSubmitting, setIsSubmitting] = useState(false);
  const { saveAuthExpiration } = useAuth();

  const navigate = useNavigate();

  // Authenticate via the API
  const login = useCallback(
    async (email, password) => {
      setIsSubmitting(true);
      setLoginError(null); // Clear any previous errors

      fetch(API_URL + '/auth/token', {
        method: 'POST',
        credentials: 'same-origin',
        headers: {
          'Content-Type': 'application/json',
          Authorization: 'Basic ' + btoa(email + ':' + password),
        },
      })
        .then(async (response) => {
          if (response.ok) {
            return response.text();
          } else if (await isAccountNotVerified(response)) {
            console.log('Navigating to /not-verified');
            navigate('/not-verified');
            return Promise.reject(new Error('Account not verified!'));
          } else if (response.status === 403) {
            // Not the account: a CORS rejection, for one, is also a 403 (D15)
            throw new Error(
              'Сървърът отказа достъп (403). Моля опитайте по-късно.'
            );
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
          setIsSubmitting(false);
        });
    },
    [saveAuthExpiration, navigate]
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
      <Button disabled={isSubmitting} type="submit" className="text-lg">
        {isSubmitting ? <Loader2 className="animate-spin" /> : 'Влизане'}
      </Button>
    </form>
  );
};

export default LoginForm;
