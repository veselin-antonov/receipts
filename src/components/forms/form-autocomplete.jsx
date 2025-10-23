import {
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form';
import {
  Command,
  CommandGroup,
  CommandItem,
  CommandList,
} from '@/components/ui/command';
import { cn } from '@/lib/utils';
import { Command as CommandPrimitive } from 'cmdk';
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { useFormContext } from 'react-hook-form';

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

OptionItem.displayName = 'OptionItem';

/**
 * @param {Object} props - The properties for the component.
 * @param {string} props.label - The label for the form field.
 * @param {string} props.fieldName - The name of the form field.
 * @param {string} props.placeholder - The placeholder for the input.
 * @param {Option[]} [props.options=[]] - The list of options
 */
const FormAutocomplete = ({
  mandatory,
  label,
  fieldName,
  placeholder,
  options = [],
}) => {
  const inputRef = useRef(null);
  const documentRef = useRef(null);

  const [search, setSearch] = useState('');
  const [showOptions, setShowOptions] = useState(false);
  const [filteredOptions, setFilteredOptions] = useState([]);

  const form = useFormContext();
  const { getFieldState } = form;

  const hasError = getFieldState(fieldName).error;

  const filterItems = useCallback(() => {
    return search && search.length > 2
      ? options.filter((o) =>
          o.value.toLowerCase().includes(search.toLowerCase())
        )
      : [];
  }, [options, search]);

  useEffect(() => {
    console.log('Filtering items');
    setFilteredOptions(filterItems());
  }, [filterItems, search]);

  const handleInput = useCallback((input, field) => {
    field.onChange(input);
    setSearch(input);
  }, []);

  const handleItemSelect = useCallback((value, field) => {
    setShowOptions(false);
    setSearch(value);
    field.onChange(value);
  }, []);

  const handleFocus = useCallback(() => {
    setShowOptions(true);
    if (!search) {
      setSearch(inputRef.current.value);
    }
  }, [search]);

  const handleBlur = () => setShowOptions(false);

  return (
    <FormField
      control={form.control}
      name={fieldName}
      render={({ field }) => (
        <FormItem>
          <FormLabel mandatory={mandatory}>{label}</FormLabel>
          <Command shouldFilter={false} className="overflow-visible">
            <CommandPrimitive.Input
              ref={inputRef}
              className={`flex h-9 w-full rounded-md border ${
                'border-' + (hasError ? 'destructive' : 'input')
              } bg-transparent px-3 py-1 text-sm shadow-sm transition-colors placeholder:text-muted-foreground focus-visible:outline-none ${
                hasError
                  ? 'focus-visible:ring-destructive'
                  : 'focus-visible:ring-ring'
              } focus-visible:ring-1 disabled:cursor-not-allowed disabled:opacity-50`}
              onValueChange={(input) => handleInput(input, field)}
              value={field.value}
              placeholder={placeholder}
              onFocus={handleFocus}
              onBlur={handleBlur}
            />
            <div className="relative mt-2 overflow-visible">
              <div
                ref={documentRef}
                className={cn(
                  'absolute top-0 z-10 w-full overflow-visible rounded-lg bg-white ring-1 ring-slate-200 animate-in fade-in-0 zoom-in-95',
                  showOptions && filteredOptions.length > 0 ? 'block' : 'hidden'
                )}
                onMouseDown={(e) => e.preventDefault()}
              >
                <CommandList>
                  <CommandGroup>
                    {filteredOptions.map((o) => (
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
            <FormMessage />
          </Command>
        </FormItem>
      )}
    />
  );
};

export default FormAutocomplete;
