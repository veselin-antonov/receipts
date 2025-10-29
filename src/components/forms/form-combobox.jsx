import FormInput from '@/components/forms/form-input';
import { Button } from '@/components/ui/button';
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from '@/components/ui/command';
import { Field, FieldError, FieldLabel } from '@/components/ui/field';
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '@/components/ui/popover';
import StoreIcon from '@/components/ui/store-icon';
import { cn } from '@/lib/utils';
import { CaretSortIcon } from '@radix-ui/react-icons';
import { PlusIcon, CheckIcon } from 'lucide-react';
import { useRef, useState } from 'react';
import { Controller } from 'react-hook-form';

const FormCombobox = ({
  form,
  fieldName,
  options,
  label,
  placeholder,
  searchPlaceholder,
  newOptionLabel,
  noResultsMessage,
}) => {
  const [showNewOptionInput, setShowNewOptionInput] = useState(false);

  const newOptionInputRef = useRef(null);

  const { trigger, control, setValue } = form;

  const buttonLabel = (fieldValue) => {
    if (showNewOptionInput) {
      return newOptionLabel;
    } else if (fieldValue) {
      return fieldValue;
    } else {
      return placeholder;
    }
  };

  return (
    <div className="flex flex-col gap-3">
      <Controller
        name={fieldName}
        control={control}
        render={({ field, fieldState }) => (
          <Field data-invalid={fieldState.invalid}>
            <FieldLabel htmlFor={field.name} className="pl-1">
              {label}
            </FieldLabel>
            <Popover>
              <PopoverTrigger asChild>
                <Button
                  variant="outline"
                  role="combobox"
                  className={cn(
                    'w-full justify-between',
                    !field.value &&
                      !showNewOptionInput &&
                      'text-muted-foreground',
                    showNewOptionInput && 'text-foreground'
                  )}
                  aria-invalid={fieldState.invalid}
                >
                  <div className="flex flex-row items-center gap-2">
                    {field.value && !showNewOptionInput && (
                      <StoreIcon
                        iconId={
                          options.find((o) => o.value === field.value)?.iconID
                        }
                      />
                    )}
                    {buttonLabel(field.value)}
                  </div>
                  <CaretSortIcon className="ml-2 h-4 w-4 shrink-0 opacity-50" />
                </Button>
              </PopoverTrigger>
              <PopoverContent className="w-[200px] p-0" modal={true}>
                <Command>
                  <CommandInput
                    placeholder={searchPlaceholder}
                    className="h-9"
                  />
                  <CommandEmpty>{noResultsMessage}</CommandEmpty>
                  <CommandList>
                    <CommandGroup>
                      <Button
                        variant="ghost"
                        type="button"
                        onClick={() => {
                          setValue(field.name, '');
                          setShowNewOptionInput(true);
                          setTimeout(
                            () => newOptionInputRef.current.focus(),
                            100
                          );
                        }}
                        className="my-1 w-full justify-center text-sm font-normal"
                      >
                        <PlusIcon size={15} className="text-primary mr-1" />
                        Добави
                      </Button>
                      {options.map((option) => (
                        <CommandItem
                          value={option.value}
                          key={option.key}
                          onSelect={() => {
                            setValue(field.name, option.value);
                            trigger(field.name);
                            setShowNewOptionInput(false);
                          }}
                        >
                          <div className="flex flex-row items-center gap-2">
                            <StoreIcon iconId={option.iconID} />
                            {option.value || placeholder}
                          </div>
                          <CheckIcon
                            className={cn(
                              'ml-auto h-4 w-4',
                              option.value === field.value
                                ? 'opacity-100'
                                : 'opacity-0'
                            )}
                          />
                        </CommandItem>
                      ))}
                    </CommandGroup>
                  </CommandList>
                </Command>
              </PopoverContent>
            </Popover>
            {showNewOptionInput && (
              <FormInput
                form={form}
                fieldName={fieldName}
                placeholder={placeholder}
                ref={newOptionInputRef}
              />
            )}
            {!showNewOptionInput && fieldState.invalid && (
              <FieldError errors={[fieldState.error]} />
            )}
          </Field>
        )}
      />
    </div>
  );
};

export default FormCombobox;
