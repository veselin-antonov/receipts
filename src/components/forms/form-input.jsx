import { Controller } from 'react-hook-form';

import { Field, FieldError, FieldLabel } from '@/components/ui/field';
import { Input } from '@/components/ui/input';

const FormInput = ({ form, label, fieldName, parseInput, ...props }) => {
  const { control } = form;

  const handleChange = (e, field) => {
    let input = e.target.value;
    if (parseInput) {
      input = parseInput(input, field.value);
    }
    field.onChange(input);
  };

  return (
    <Controller
      name={fieldName}
      control={control}
      render={({ field, fieldState }) => (
        <Field data-invalid={fieldState.invalid}>
          <FieldLabel htmlFor={field.name} className="pl-1">
            {label}
          </FieldLabel>
          <Input
            {...field}
            onChange={(e) => handleChange(e, field)}
            aria-invalid={fieldState.invalid}
            {...props}
          />
          {fieldState.invalid && <FieldError errors={[fieldState.error]} />}
        </Field>
      )}
    />
  );
};
FormInput.displayName = 'FormInput';
export default FormInput;
