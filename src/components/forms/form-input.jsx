import {
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/common/form';
import { Input } from '@/components/common/input';
import { forwardRef } from 'react';

const FormInput = forwardRef(
  (
    { containerClassName, form, label, fieldName, parseInput, ...props },
    ref
  ) => {
    return (
      <FormField
        control={form.control}
        name={fieldName}
        render={({ field }) => (
          <FormItem className={containerClassName}>
            <FormLabel>{label}</FormLabel>
            <FormControl>
              <Input
                {...field}
                {...props}
                ref={ref}
                onChange={(e) => {
                  let input = e.target.value;
                  if (parseInput) {
                    input = parseInput(input, field.value);
                  }
                  field.onChange(input);
                }}
              />
            </FormControl>
            <FormMessage />
          </FormItem>
        )}
      />
    );
  }
);

export default FormInput;
