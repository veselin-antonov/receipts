import { Checkbox } from '@/components/ui/checkbox';
import { Field, FieldError, FieldLabel } from '@/components/ui/field';
import { cn } from '@/lib/utils';
import { Controller } from 'react-hook-form';

const FormCheckbox = ({ form, label, fieldName, ...props }) => {
  return (
    <Controller
      control={form.control}
      name={fieldName}
      render={({ field, fieldState }) => (
        <Field orientation="horizontal" data-invalid={fieldState.invalid}>
          <Checkbox
            checked={field.value}
            onCheckedChange={field.onChange}
            aria-invalid={fieldState.invalid}
            {...field}
            {...props}
          />
          <FieldLabel htmlFor={fieldName}>{label}</FieldLabel>
          {fieldState.invalid && (
            <FieldError>{fieldState.error.message}</FieldError>
          )}
        </Field>
      )}
    />
  );
};

export default FormCheckbox;
