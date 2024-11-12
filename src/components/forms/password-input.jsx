import {
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/common/form';
import { Input } from '@/components/common/input';
import { EyeIcon, EyeOffIcon } from 'lucide-react';
import { forwardRef, useState } from 'react';
import { useFormContext } from 'react-hook-form';

const PasswordInput = forwardRef(
  ({ containerClassName, label, fieldName, ...props }, ref) => {
    const [hidden, setHidden] = useState(true);

    const form = useFormContext();

    return (
      <FormField
        control={form.control}
        name={fieldName}
        render={({ field }) => (
          <FormItem className={containerClassName}>
            <FormLabel>{label}</FormLabel>
            <FormControl>
              <div className='relative'>
                <Input
                  {...field}
                  {...props}
                  ref={ref}
                  onChange={field.onChange}
                  type={hidden ? 'password' : 'text'}
                />
                <div
                  className='absolute top-2.5 right-4'
                  onClick={() => setHidden(!hidden)}
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
  }
);

PasswordInput.displayName = 'PasswordInput';
export default PasswordInput;
