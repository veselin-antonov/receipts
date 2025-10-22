import { buttonVariants } from '@/components/ui/button';
import { Card, CardContent, CardTitle, CardHeader } from '@/components/ui/card';
import { cn } from '@/lib/utils';
import { Link } from 'react-router';

export const NotVerified = () => {
  return (
    <Card className="min-w-[400px] w-[90%] max-w-[600px] mx-auto my-10 sm:mt-24 px-12 py-5">
      <CardHeader>
        <CardTitle className="text-center">Профилът не е потвърден</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col justify-center items-center gap-6">
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
