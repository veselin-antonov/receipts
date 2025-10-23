import {
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { EyeIcon, EyeOffIcon } from 'lucide-react';
import { useCallback, useState } from 'react';
import { useFormContext } from 'react-hook-form';

const PasswordField = ({
  containerClassName,
  label,
  fieldName,
  hidden,
  toggleVisibility,
}) => {
  const form = useFormContext();

  return (
    <FormField
      control={form.control}
      name={fieldName}
      render={({ field }) => (
        <FormItem className={containerClassName}>
          <FormLabel>{label}</FormLabel>
          <FormControl>
            <div className="relative">
              <Input
                {...field}
                onChange={field.onChange}
                type={hidden ? 'password' : 'text'}
              />
              <div
                className="absolute right-4 top-2.5"
                onClick={toggleVisibility}
              >
                {hidden ? (
                  <EyeOffIcon size={'1.2rem'} />
                ) : (
                  <EyeIcon size={'1.2rem'} />
                )}
              </div>
            </div>
          </FormControl>
          <FormMessage />
        </FormItem>
      )}
    />
  );
};

const PasswordInput = ({
  inputProps,
  confirmation = false,
  confirmationInputProps,
}) => {
  const [hidden, setHidden] = useState(true);

  const toggleVisibility = useCallback(() => setHidden(!hidden), [hidden]);

  inputProps = {
    ...inputProps,
    hidden: hidden,
    toggleVisibility: toggleVisibility,
  };

  confirmationInputProps = {
    ...confirmationInputProps,
    hidden: hidden,
    toggleVisibility: toggleVisibility,
  };

  return (
    <>
      <PasswordField {...inputProps} />
      {confirmation && <PasswordField {...confirmationInputProps} />}
    </>
  );
};

PasswordInput.displayName = 'PasswordInput';
export default PasswordInput;
