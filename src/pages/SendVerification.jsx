import { Loader2 } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';

import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { API_URL } from '@/lib/utils';

const SendVerification = () => {
  const [actionStatus, setActionStatus] = useState('PENDING');

  const sendNewEmail = useCallback(() => {
    fetch(API_URL + '/users/resend-verification', {
      method: 'POST',
      credentials: 'same-origin',
    })
      .then((response) => {
        if (response.ok) {
          setActionStatus('SUCCESS');
        } else {
          throw new Error('Failed to send verification email');
        }
      })
      .catch((error) => {
        setActionStatus('ERROR');
        console.error('Error:', error);
      });
  }, []);

  useEffect(() => {
    sendNewEmail();
  }, [sendNewEmail]);

  const renderContent = () => {
    switch (actionStatus) {
      case 'PENDING':
        return <Loader2 className="animate-spin" />;
      case 'SUCCESS':
        return (
          <>
            <p>
              Линкът е изпратен. Моля последвайте инструкциите на полученият
              имейл.
            </p>
          </>
        );
      case 'ERROR':
        return (
          <>
            <p>
              Възникна грешка при изпращането на линка. Моля опитайте след
              няколко минути.
            </p>
          </>
        );
      default:
        return '';
    }
  };

  return (
    <Card className="mx-auto my-10 w-[90%] max-w-[600px] min-w-[400px] px-12 py-5 sm:mt-24">
      <CardHeader>
        <CardTitle className="text-center">Изпращане на нов линк</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col items-center justify-center gap-6">
        {renderContent()}
      </CardContent>
    </Card>
  );
};

SendVerification.displayName = 'SendVerification';

export default SendVerification;
