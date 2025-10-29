import { Button } from '@/components/ui/button';
import FormInput from '@/components/forms/form-input';
import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { Link } from 'react-router';
import { Loader2 } from 'lucide-react';
import { Separator } from '@/components/ui/separator';
import {
  Card,
  CardContent,
  CardFooter,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { API_URL } from '@/lib/utils';
import PasswordInput from '@/components/forms/password-input';

const formSchema = z.object({
  email: z.string().email(),
  password: z.string().min(1),
  passwordConfirmation: z.string().min(1),
});

export const Register = () => {
  const [registrationStatus, setRegistrationStatus] = useState('IDLE'); // IDLE, PENDING, SUCCESS

  const form = useForm({
    resolver: zodResolver(formSchema),
    defaultValues: {
      email: '',
      password: '',
      passwordConfirmation: '',
    },
    reValidateMode: 'onChange',
  });

  const onSubmit = (data) => {
    register(data.email, data.password, data.passwordConfirmation);
  };

  // An asynchronous function to register a new user
  async function register(email, password, passwordConfirmation) {
    setRegistrationStatus('PENDING');
    // Clear any previous form errors
    form.clearErrors();

    fetch(API_URL + '/register', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ email, password, passwordConfirmation }),
    })
      .then((response) => {
        if (response.ok) {
          setRegistrationStatus('SUCCESS');
          return response.text();
        } else if (response.status === 400) {
          // Handle validation errors from backend
          return response.json().then((errorData) => {
            throw new Error(
              errorData.message || 'Възникна грешка при регистрацията'
            );
          });
        } else {
          throw new Error(
            'Възникна грешка при регистрацията. Моля опитайте отново.'
          );
        }
      })
      .catch((error) => {
        console.error('Error:', error);
        setRegistrationStatus('IDLE');
        // Set form-level error that will be displayed in the form
        form.setError('root', {
          message: error.message,
        });
      });
  }

  const renderContent = () => {
    if (registrationStatus === 'SUCCESS') {
      return (
        <div className="grid gap-4 text-center">
          <p className="text-lg">
            Регистрацията е успешна! Потвърдителен линк е изпратен на вашия
            имейл.
          </p>
          <p>
            Моля проверете вашата пощенска кутия и последвайте инструкциите в
            полученото съобщение, за да завършите регистрацията.
          </p>
          <Separator />
          <div className="w-full text-center">
            <Link to="/login" className="text-primary">
              Обратно към вход
            </Link>
          </div>
        </div>
      );
    }

    return (
      <form
        onSubmit={form.handleSubmit(onSubmit)}
        className="flex w-sm flex-col gap-4"
      >
        {form.formState.errors.root && (
          <div className="text-destructive text-center text-sm">
            {form.formState.errors.root.message}
          </div>
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
          }}
          confirmationInputProps={{
            label: 'Потвърдете паролата',
            fieldName: 'passwordConfirmation',
          }}
        />
        <Button
          type="submit"
          className="text-lg"
          disabled={registrationStatus === 'PENDING'}
        >
          {registrationStatus === 'PENDING' ? (
            <Loader2 className="animate-spin" />
          ) : (
            'Регистриране'
          )}
        </Button>
      </form>
    );
  };

  return (
    <div className="flex min-h-svh w-full items-center justify-center">
      <Card className="w-[60%] max-w-[600px] min-w-[400px]">
        <CardHeader className="place-items-center gap-1 p-6">
          <img src="logo.svg" alt="logo-image" className="w-12" />
          <CardTitle className="text-3xl font-semibold">Регистрация</CardTitle>
        </CardHeader>
        <CardContent className="flex w-full justify-center">
          {renderContent()}
        </CardContent>
        <CardFooter className="mt-4 flex items-center justify-center">
          <span className="text-center">
            Имате акаунт?{' '}
            <Link to="/login" className="text-primary text-center">
              Влезте
            </Link>
          </span>
        </CardFooter>
      </Card>
    </div>
  );
};
