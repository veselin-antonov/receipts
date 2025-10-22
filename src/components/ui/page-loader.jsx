import { Dialog, DialogTitle } from '@/components/ui/dialog';
import { DialogContent } from '@radix-ui/react-dialog';
import { Loader2 } from 'lucide-react';

export const PageLoader = () => {
  return (
    <Dialog open={true}>
      <DialogContent
        aria-describedby={undefined}
        className="bg-white w-60 h-60 rounded-md grid place-items-center mx-auto mt-40 focus-visible:outline-none"
      >
        <DialogTitle className="hidden" />
        <Loader2
          className="animate-spin duration-2000 text-primary"
          size={100}
        />
        <span className="text-xl">Моля изчакайте...</span>
      </DialogContent>
    </Dialog>
  );
};
