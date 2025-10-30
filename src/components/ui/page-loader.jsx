import { DialogContent } from '@radix-ui/react-dialog';
import { Loader2 } from 'lucide-react';

import { Dialog, DialogTitle } from '@/components/ui/dialog';

export const PageLoader = () => {
  return (
    <Dialog open={true}>
      <DialogContent
        aria-describedby={undefined}
        className="mx-auto mt-40 grid h-60 w-60 place-items-center rounded-md bg-white focus-visible:outline-hidden"
      >
        <DialogTitle className="hidden" />
        <Loader2
          className="text-primary animate-spin duration-2000"
          size={100}
        />
        <span className="text-xl">Моля изчакайте...</span>
      </DialogContent>
    </Dialog>
  );
};
