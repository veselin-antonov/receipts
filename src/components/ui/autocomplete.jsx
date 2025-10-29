import {
  Command,
  CommandGroup,
  CommandInputClean,
  CommandItem,
  CommandList,
} from '@/components/ui/command';
import { Label } from '@/components/ui/label';
import { cn } from '@/lib/utils';
import React, { useCallback, useRef, useState } from 'react';

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
 * @param {string} props.placeholder - The placeholder for the input.
 * @param {string} props.inputClassName - Additional class names for the input.
 * @param {function} props.onInputChange - The function to call when the input value changes.
 * @param {Option[]} [props.options=[]] - The list of options
 */
const Autocomplete = ({
  label,
  placeholder,
  options = [],
  onInputChange,
  ...props
}) => {
  const inputRef = useRef(null);
  const documentRef = useRef(null);

  const [search, setSearch] = useState('');
  const [showOptions, setShowOptions] = useState(false);

  const filteredOptions =
    search && search.length > 0
      ? options.filter((o) =>
          o.value.toLowerCase().includes(search.toLowerCase())
        )
      : options;

  const handleInput = useCallback(
    (input) => {
      setSearch(input);
      setShowOptions(true);
      onInputChange(input);
    },
    [onInputChange]
  );

  const handleItemSelect = useCallback((value) => {
    setShowOptions(false);
    setSearch(value);
  }, []);

  const handleFocus = useCallback(() => {
    setShowOptions(true);
    if (!search) {
      setSearch(inputRef.current.value);
    }
  }, [search]);

  const handleBlur = () => {
    setShowOptions(false);
  };

  return (
    <div className="flex-row space-y-2">
      {label && <Label className="pl-1">{label}</Label>}
      <Command
        shouldFilter={false}
        className="relative overflow-visible bg-transparent"
      >
        <CommandInputClean
          {...props}
          ref={inputRef}
          className="border-input placeholder:text-muted-foreground focus-visible:ring-ring flex h-9 w-full rounded-md border px-3 py-1 text-sm shadow-xs transition-colors focus-visible:ring-1 focus-visible:outline-hidden disabled:cursor-not-allowed disabled:opacity-50"
          onValueChange={(input) => handleInput(input)}
          value={search}
          placeholder={placeholder}
          onFocus={handleFocus}
          onBlur={handleBlur}
        />
        <div className={cn('absolute top-8 mt-2 overflow-visible')}>
          <div
            ref={documentRef}
            className={cn(
              'border-input bg-background animate-in fade-in-0 zoom-in-95 relative z-10 w-full overflow-hidden rounded-lg border',
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
                    handleItemSelect={(value) => handleItemSelect(value)}
                  />
                ))}
              </CommandGroup>
            </CommandList>
          </div>
        </div>
      </Command>
    </div>
  );
};

export default Autocomplete;
