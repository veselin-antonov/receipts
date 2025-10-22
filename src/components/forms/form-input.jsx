import {
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/common/form';
import { Input } from '@/components/common/input';
import { forwardRef } from 'react';
import { useFormContext } from 'react-hook-form';

const FormInput = forwardRef(
  (
    { containerClassName, label, fieldName, parseInput, mandatory, ...props },
    ref
  ) => {
    const form = useFormContext();
    const { getFieldState } = form;

    const hasError = getFieldState(fieldName).error;

    return (
      <FormField
        control={form.control}
        name={fieldName}
        render={({ field }) => (
          <FormItem className={containerClassName}>
            <FormLabel mandatory={mandatory}>{label}</FormLabel>
            <FormControl>
              <Input
                {...field}
                {...props}
                className={
                  hasError
                    ? 'border-destructive focus-visible:ring-destructive'
                    : ''
                }
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
FormInput.displayName = 'FormInput';
export default FormInput;
