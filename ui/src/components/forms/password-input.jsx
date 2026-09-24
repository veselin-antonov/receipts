import { EyeIcon, EyeOffIcon } from 'lucide-react';
import { useCallback, useState } from 'react';
import { Controller } from 'react-hook-form';

import { Field, FieldError, FieldLabel } from '@/components/ui/field';
import { Input } from '@/components/ui/input';

const PasswordField = ({
  form,
  containerClassName,
  label,
  fieldName,
  hidden,
  toggleVisibility,
  forgotPassword = false,
}) => {
  return (
    <Controller
      control={form.control}
      name={fieldName}
      render={({ field, fieldState }) => (
        <Field data-invalid={fieldState.invalid} className={containerClassName}>
          <div className="flex justify-between">
            <FieldLabel htmlFor={fieldName} className="pl-1">
              {label}
            </FieldLabel>
            {/* <Link to='/forgot-password' className='text-primary'>
                Забравена парола?
              </Link> */}
            {forgotPassword && (
              <span className="text-border text-nowrap">Забравена парола?</span>
            )}
          </div>
          <div className="relative">
            <Input
              type={hidden ? 'password' : 'text'}
              aria-invalid={fieldState.invalid}
              {...field}
            />
            <div
              className="absolute top-2.5 right-4"
              onClick={toggleVisibility}
            >
              {hidden ? (
                <EyeOffIcon size={'1.2rem'} />
              ) : (
                <EyeIcon size={'1.2rem'} />
              )}
            </div>
          </div>
          {fieldState.invalid && <FieldError errors={[fieldState.error]} />}
        </Field>
      )}
    />
  );
};

const PasswordInput = ({ form, inputProps, confirmationInputProps }) => {
  const [hidden, setHidden] = useState(true);

  const toggleVisibility = useCallback(() => setHidden(!hidden), [hidden]);

  inputProps = {
    form,
    hidden: hidden,
    toggleVisibility: toggleVisibility,
    ...inputProps,
  };

  confirmationInputProps = confirmationInputProps
    ? {
        form,
        hidden: hidden,
        toggleVisibility: toggleVisibility,
        ...confirmationInputProps,
      }
    : null;

  return (
    <>
      <PasswordField {...inputProps} />
      {confirmationInputProps && <PasswordField {...confirmationInputProps} />}
    </>
  );
};

PasswordInput.displayName = 'PasswordInput';
export default PasswordInput;
