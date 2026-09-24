import { Controller } from 'react-hook-form';

import { DatePicker } from '@/components/ui/date-picker';
import { Field, FieldError, FieldLabel } from '@/components/ui/field';

const FormDatePicker = ({ form, label, fieldName, ...props }) => {
  return (
    <Controller
      control={form.control}
      name={fieldName}
      render={({ field, fieldState }) => (
        <Field data-invalid={fieldState.invalid} orientation="vertical">
          <FieldLabel htmlFor={fieldName} className="pl-1">
            {label}
          </FieldLabel>
          <DatePicker
            onSelect={field.onChange}
            value={field.value}
            isInvalid={fieldState.invalid}
            {...props}
          />
          {fieldState.invalid && <FieldError errors={[fieldState.error]} />}
        </Field>
      )}
    />
  );
};

export default FormDatePicker;
