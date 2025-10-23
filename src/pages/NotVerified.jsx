import { buttonVariants } from '@/components/ui/button';
import { Card, CardContent, CardTitle, CardHeader } from '@/components/ui/card';
import { cn } from '@/lib/utils';
import { Link } from 'react-router';

export const NotVerified = () => {
  return (
    <Card className="mx-auto my-10 w-[90%] min-w-[400px] max-w-[600px] px-12 py-5 sm:mt-24">
      <CardHeader>
        <CardTitle className="text-center">Профилът не е потвърден</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col items-center justify-center gap-6">
        Не сте потвърдили профилът си, чрез линка изпратен на имейла ви.
        <br />
        Ако не намирате имейла, проверете папката за спам или изпратете нов линк
        за потвърждение чрез бутона отдолу.
        <Link
          to="/resend-verification"
          className={cn(buttonVariants({ variant: 'default' }), 'w-[50%]')}
        >
          Изпрати нов линк
        </Link>
      </CardContent>
    </Card>
  );
};
