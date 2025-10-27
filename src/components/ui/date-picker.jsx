import { CalendarIcon } from '@radix-ui/react-icons';
import { formatDate } from 'date-fns';

import { cn } from '@/lib/utils';
import { Button } from '@/components/ui/button';
import { Calendar } from '@/components/ui/calendar';
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '@/components/ui/popover';
import { useState } from 'react';

export function DatePicker({
  placeholder,
  onSelect,
  format = 'PPP',
  formatOptions,
  isInvalid,
  ...props
}) {
  const [date, setDate] = useState();

  const handleSelect = (date) => {
    setDate(date);
    onSelect(date);
  };

  return (
    <Popover>
      <PopoverTrigger asChild>
        <Button
          variant={'outline'}
          className={cn(
            'justify-start pl-3 text-left font-normal',
            !date && 'text-muted-foreground'
          )}
          aria-invalid={isInvalid}
        >
          <CalendarIcon className="mr-2 h-4 w-4" />
          {date ? (
            formatDate(date, format, formatOptions)
          ) : (
            <span>{placeholder}</span>
          )}
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-auto p-0" align="start">
        <Calendar
          mode="single"
          selected={date}
          initialFocus
          onSelect={handleSelect}
          {...props}
        />
      </PopoverContent>
    </Popover>
  );
}
