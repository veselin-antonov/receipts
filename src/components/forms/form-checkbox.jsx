import { Checkbox } from '@/components/ui/checkbox';
import {
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/forms/form';
import { cn } from '@/lib/utils';

const FormCheckbox = ({
  form,
  label,
  fieldName,
  containerClassName,
  ...props
}) => {
  return (
    <FormField
      control={form.control}
      name={fieldName}
      render={({ field }) => (
        <FormItem
          className={cn(
            'flex flex-row items-center space-x-3 space-y-0 py-4',
            containerClassName
          )}
        >
          <FormLabel>{label}</FormLabel>
          <FormControl>
            <Checkbox
              {...field}
              {...props}
              checked={field.value}
              onCheckedChange={field.onChange}
            />
          </FormControl>
          <FormMessage />
        </FormItem>
      )}
    />
  );
};

export default FormCheckbox;
