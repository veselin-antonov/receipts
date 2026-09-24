import { Loader2 } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router';

import { buttonVariants } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { API_URL, cn } from '@/lib/utils';

const VerifyAccount = () => {
  const { pathname: path, search: queryString } = useLocation();
  const navigate = useNavigate();

  const [verificationStatus, setVerificationStatus] = useState('PENDING');

  const verifyAccount = useCallback(() => {
    fetch(API_URL + path + queryString, { method: 'post' })
      .then((response) => {
        if (response.ok) {
          setVerificationStatus('SUCCESS');
        } else if (new String(response.status).startsWith('40')) {
          setVerificationStatus('FAILED');
        } else if (new String(response.status).startsWith('50')) {
          setVerificationStatus('ERROR');
        }
      })
      .catch((error) => {
        console.error('Error:', error);
      });
  }, [path, queryString]);

  useEffect(() => {
    if (!queryString) {
      console.debug('No query string provided for verification');
      navigate('/login', { replace: true });
    }

    verifyAccount();
  }, [queryString, navigate, verifyAccount]);

  return (
    <Card className="mx-auto my-10 w-[90%] max-w-[600px] min-w-[400px] px-12 py-5 sm:mt-24">
      <CardHeader>
        <CardTitle className="text-center">
          {verificationStatus === 'PENDING' && (
            <span>Потвърждаване на профил...</span>
          )}
          {verificationStatus === 'SUCCESS' && (
            <span className="font-bold text-green-700">
              Потвърждението е успешно!
            </span>
          )}
          {verificationStatus === 'FAILED' && (
            <span className="font-bold text-red-700">
              Потвърждението е неуспешно!
            </span>
          )}
          {verificationStatus === 'ERROR' && (
            <span className="font-bold text-red-700">
              Грешка при потвърждението!
            </span>
          )}
        </CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col items-center justify-center gap-6">
        {verificationStatus === 'PENDING' && (
          <Loader2 className="animate-spin" />
        )}
        {verificationStatus === 'SUCCESS' && (
          <>
            <p>
              Профилът ти е потвърден! Може да се впишеш чрез бутона отдолу.
            </p>
            <Link
              to="/login"
              className={cn(buttonVariants({ variant: 'default' }), 'w-[30%]')}
            >
              Влез
            </Link>
          </>
        )}
        {verificationStatus === 'FAILED' && (
          <>
            <p>
              Линкът за потвърждение е изтекъл или невалиден. Може да изпратите
              нов, като натиснете бутона отдолу.
            </p>
            <Link
              to="/resend-verification"
              className={cn(buttonVariants({ variant: 'default' }), 'w-[50%]')}
            >
              Изпрати нов линк
            </Link>
          </>
        )}
        {verificationStatus === 'ERROR' && (
          <>
            <p>
              Нещо се обърка при потвърждението на акаунта ви. <br />
              Моля генерирайте нов линк за потвърждение чрез бутона отдолу,
              който ще получите на имейла си.
            </p>
            <Link
              to="/resend-verification"
              className={cn(buttonVariants({ variant: 'default' }), 'w-[50%]')}
            >
              Изпрати нов линк
            </Link>
          </>
        )}
      </CardContent>
    </Card>
  );
};

VerifyAccount.displayName = 'VerifyAccount';

export default VerifyAccount;
