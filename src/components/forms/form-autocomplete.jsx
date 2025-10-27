import { Controller } from 'react-hook-form';
import Autocomplete from '@/components/ui/autocomplete';
import { Field, FieldError, FieldLabel } from '@/components/ui/field';

/**
 * @param {Object} props - The properties for the component.
 * @param {string} props.label - The label for the form field.
 * @param {string} props.fieldName - The name of the form field.
 * @param {string} props.placeholder - The placeholder for the input.
 * @param {Option[]} [props.options=[]] - The list of options
 */
const FormAutocomplete = ({ form, label, fieldName, ...props }) => {
  const { control } = form;

  return (
    <Controller
      name={fieldName}
      control={control}
      render={({ field, fieldState }) => (
        <Field data-invalid={fieldState.invalid}>
          <FieldLabel htmlFor={field.name} className="pl-1">
            {label}
          </FieldLabel>
          <Autocomplete
            onInputChange={(value) => field.onChange(value)}
            aria-invalid={fieldState.invalid}
            {...field}
            {...props}
          />
          {fieldState.invalid && <FieldError errors={[fieldState.error]} />}
        </Field>
      )}
    />
  );
};

export default FormAutocomplete;
