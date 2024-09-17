import { FormField, FormItem, FormLabel } from '@/components/forms/form';
import {
  Command,
  CommandGroup,
  CommandItem,
  CommandList,
} from '@/components/ui/command';
import { cn } from '@/lib/utils';
import {
  useSignal,
  useSignalEffect,
  useSignals,
} from '@preact/signals-react/runtime';
import { Command as CommandPrimitive } from 'cmdk';
import React, { useMemo, useRef } from 'react';

/**
 * @typedef {Object} Option
 * @property {string} value - The name of the item.
 * @property {string} label - The label of the item.
 * @property {string} key - The key of the item.
 * @property {function} onSelect - The function to call when the item is selected.
 */

/**
 * @param {Option} option - The option to be transformed.
 */
const OptionItem = React.memo(({ option, handleItemSelect }) => (
  <CommandItem
    value={option.value}
    onSelect={(value) => {
      option.onSelect(value);
      handleItemSelect(value);
    }}
  >
    {option.label}
  </CommandItem>
));

/**
 * @param {Object} props - The properties for the component.
 * @param {string} props.label - The label for the form field.
 * @param {string} props.fieldName - The name of the form field.
 * @param {string} props.placeholder - The placeholder for the input.
 * @param {Option[]} [props.options=[]] - The list of options
 */
const FormAutocomplete = ({
  form,
  label,
  fieldName,
  placeholder,
  options = [],
}) => {
  const filterItems = () => {
    return search.value && search.value.length > 2
      ? options.filter((o) =>
          o.value.toLowerCase().includes(search.value.toLowerCase())
        )
      : [];
  };

  const inputRef = useRef(null);
  const documentRef = useRef(null);

  useSignals();
  const search = useSignal('');
  const showOptions = useSignal(false);
  const filteredOptions = useSignal([]);

  useSignalEffect(() => {
    console.log('Filtering items');
    filteredOptions.value = filterItems();
  });

  const handleInput = (input, field) => {
    field.onChange(input);
    search.value = input;
  };

  const handleItemSelect = (value, field) => {
    showOptions.value = false;
    search.value = value;
    field.onChange(value);
  };

  const handleFocus = () => {
    showOptions.value = true;
    if (!search.peek()) {
      search.value = inputRef.current.value;
    }
  };

  const handleBlur = () => (showOptions.value = false);

  useSignalEffect(() => {
    console.log('search.value:', search.value);
    console.log('showItems.value', showOptions.value);
  });

  return (
    <FormField
      control={form.control}
      name={fieldName}
      render={({ field }) => (
        <FormItem>
          <FormLabel>{label}</FormLabel>
          <Command shouldFilter={false} className='overflow-visible'>
            <CommandPrimitive.Input
              ref={inputRef}
              className='flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50'
              onValueChange={(input) => handleInput(input, field)}
              value={field.value}
              placeholder={placeholder}
              onFocus={handleFocus}
              onBlur={handleBlur}
            />
            <div className='relative mt-2 overflow-visible'>
              <div
                ref={documentRef}
                className={cn(
                  'animate-in fade-in-0 zoom-in-95 absolute top-0 z-10 w-full bg-white rounded-lg ring-1 ring-slate-200 overflow-visible',
                  showOptions.value && filteredOptions.value.length > 0
                    ? 'block'
                    : 'hidden'
                )}
                onMouseDown={(e) => e.preventDefault()}
              >
                <CommandList>
                  <CommandGroup>
                    {filteredOptions.value.map((o) => (
                      <OptionItem
                        key={o.key}
                        option={o}
                        handleItemSelect={(value) =>
                          handleItemSelect(value, field)
                        }
                      />
                    ))}
                  </CommandGroup>
                </CommandList>
              </div>
            </div>
          </Command>
        </FormItem>
      )}
    />
  );
};

export default FormAutocomplete;
