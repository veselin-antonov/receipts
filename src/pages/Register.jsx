import { Button } from '@/components/common/button';
import { Form } from '@/components/common/form';
import FormInput from '@/components/forms/form-input';
import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
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
  const [registrationStatus, setRegistrationStatus] = useState('IDLE'); // IDLE, PENDING, SUCCESS

  const form = useForm({
    resolver: zodResolver(formSchema),
    defaultValues: {
      email: '',
      password: '',
      passwordConfirmation: '',
    },
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
      <>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="grid gap-4">
            {form.formState.errors.root && (
              <div className="text-sm text-destructive text-center">
                {form.formState.errors.root.message}
              </div>
            )}
            <FormInput
              label="Имейл"
              fieldName="email"
              autoComplete="username"
            />
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
        </Form>
        <Separator className="mt-4" />
        <div className="flex justify-center items-center mt-4">
          <span className="text-center">
            Имате акаунт?{' '}
            <Link to="/login" className="text-center text-primary">
              Влезте
            </Link>
          </span>
        </div>
      </>
    );
  };

  return (
    <div>
      <Card className="min-w-[400px] w-[90%] max-w-[600px] mx-auto my-10 sm:mt-24 px-12 py-5">
        <CardHeader className="items-center gap-1">
          <img src="logo.svg" alt="logo-image" className="w-12" />
          <CardTitle className="text-3xl font-semibold">Регистрация</CardTitle>
        </CardHeader>
        <CardContent>{renderContent()}</CardContent>
      </Card>
    </div>
  );
};
