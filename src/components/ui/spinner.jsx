import { Loader2Icon } from 'lucide-react';

import { cn } from '@/lib/utils';

function SpinnerPrimitive({ className, ...props }) {
  return (
    <Loader2Icon
      role="status"
      aria-label="Loading"
      className={cn('size-4 animate-spin', className)}
      {...props}
    />
  );
}

function Spinner({ className, ...props }) {
  return (
    <div className="flex items-center justify-center py-10">
      <SpinnerPrimitive
        className={cn('stroke-primary size-20 stroke-1', className)}
        {...props}
      />
    </div>
  );
}

export { Spinner };
