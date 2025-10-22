import { Calendar } from '@/components/common/calendar';
import {
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/common/form';
import { cn } from '@/lib/utils';
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '@/components/common/popover';
import { format } from 'date-fns';
import { bg } from 'date-fns/locale';
import { CalendarIcon } from 'lucide-react';
import { Button } from '@/components/common/button';

const customBg = {
  ...bg,
  localize: {
    ...bg.localize,
    month: (n) => {
      const month = bg.localize.month(n);
      return month.charAt(0).toUpperCase() + month.slice(1);
    },
  },
};

const FormDatePicker = ({
  containerClassName,
  form,
  label,
  fieldName,
  placeholder,
}) => {
  return (
    <FormField
      control={form.control}
      name={fieldName}
      render={({ field }) => (
        <FormItem className={cn('flex flex-col', containerClassName)}>
          <FormLabel>{label}</FormLabel>
          <Popover>
            <PopoverTrigger asChild>
              <FormControl>
                <Button
                  variant={'outline'}
                  className={cn(
                    'w-[80%] pl-3 text-left font-normal',
                    !field.value && 'text-muted-foreground'
                  )}
                >
                  {field.value ? (
                    format(field.value, 'dd MMM yyyy', { locale: customBg })
                  ) : (
                    <span>{placeholder}</span>
                  )}
                  <CalendarIcon className="ml-4 h-4 w-4 opacity-50" />
                </Button>
              </FormControl>
            </PopoverTrigger>
            <PopoverContent className="w-auto p-0" align="start">
              <Calendar
                mode="single"
                locale={customBg}
                selected={field.value}
                onSelect={field.onChange}
                disabled={(date) =>
                  date > new Date() || date < new Date('1900-01-01')
                }
                initialFocus
              />
            </PopoverContent>
          </Popover>
          <FormMessage />
        </FormItem>
      )}
    />
  );
};

export default FormDatePicker;
