import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/common/form';
import FormAutocomplete from '@/components/forms/form-autocomplete';
import FormCheckbox from '@/components/forms/form-checkbox';
import FormDatePicker from '@/components/forms/form-date-picker';
import FormInput from '@/components/forms/form-input';
import { Button } from '@/components/common/button';
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from '@/components/common/command';
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/common/dialog';
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '@/components/common/popover';
import StoreIcon from '@/components/common/store-icon';
import { API_URL, cn } from '@/lib/utils';
import { zodResolver } from '@hookform/resolvers/zod';
import { CaretSortIcon, CheckIcon, Cross2Icon } from '@radix-ui/react-icons';
import { PlusIcon } from 'lucide-react';
import { useCallback, useEffect, useRef, useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { useAuth } from '@/components/auth/AuthContext';

const priceRegex = new RegExp('^\\d*[.,]?\\d{0,2}$');

const formSchema = z.object({
  product: z.string().min(1, '').max(50),
  price: z.string().min(1, '').regex(priceRegex, 'Невалидна сума!'),
  store: z.string().min(1, ''),
  discount: z.boolean().optional(),
  date: z.date({ message: '' }),
});

const productToOption = (form, p) => {
  const option = {
    value: p.name,
    label: p.name,
    key: p.id,
    onSelect: () => form.setValue('product', p.name),
  };

  return option;
};

/** @param {Object} props - The properties for the component.
 * @param {string} [props.buttonLabel='Button'] - The label for the button.
 * @param {string} [props.dialogueTitle='Title'] - The title for the dialog.
 * @param {string} [props.dialogDescription='Description'] - The description for the dialog.
 * @param {function} [props.handlePurchaseCreation] - The function to call when the purchase is created.
 */
const FormDialog = ({
  buttonLabel = 'Button',
  dialogueTitle = 'Title',
  dialogDescription = 'Description',
  handlePurchaseCreation,
}) => {
  const { isAuthenticated } = useAuth();

  const [dialogOpen, setDialogOpen] = useState(false);
  const [showNewStoreInput, setShowNewStoreInput] = useState(false);

  const newStoreInputRef = useRef(null);

  const form = useForm({
    resolver: zodResolver(formSchema),
    defaultValues: {
      product: '',
      price: '',
      discount: false,
      date: '',
      store: '',
    },
    reValidateMode: 'onChange',
  });
  const { trigger, getFieldState } = form;

  const [stores, setStores] = useState([]);

  const fetchStores = useCallback(async () => {
    if (!isAuthenticated()) {
      console.log('Cannot fetch stores. User is not authenticated.');
      return;
    }

    const response = await fetch(`${API_URL}/stores`, {
      headers: { Authorization: `Bearer ${localStorage.getItem('token')}` },
    });
    const stores = await response.json();
    setStores(stores);
  }, [isAuthenticated]);

  useEffect(() => {
    fetchStores();
  }, [fetchStores]);

  const [products, setProducts] = useState([]);

  const fetchProducts = useCallback(async () => {
    if (!isAuthenticated()) {
      console.log('Cannot fetch products. User is not authenticated.');
      return;
    }
    const response = await fetch(`${API_URL}/products`, {
      headers: { Authorization: `Bearer ${localStorage.getItem('token')}` },
    });
    const products = await response.json();
    setProducts(products);
  }, [isAuthenticated]);

  useEffect(() => {
    fetchProducts();
  }, [fetchProducts]);

  async function onSubmit(values) {
    const date = String(values.date.getDate()).padStart(2, '0');
    const month = String(values.date.getMonth() + 1).padStart(2, '0');
    const year = String(values.date.getFullYear());

    values.date = `${date}/${month}/${year}`;
    values.price = values.price.replace(',', '.');

    fetch(`${API_URL}/purchases`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${localStorage.getItem('token')}`,
      },
      body: JSON.stringify(values),
    })
      .then((response) => response.json())
      .then((purchase) => {
        setDialogOpen(false);
        handlePurchaseCreation(purchase);
      })
      .catch((error) => console.error('Error submitting form: ', error));
  }

  return (
    <Dialog
      open={dialogOpen}
      onOpenChange={(b) => {
        setDialogOpen(b);
      }}
    >
      <DialogTrigger asChild>
        <Button>{buttonLabel}</Button>
      </DialogTrigger>
      <DialogContent className="sm:max-w-[700px] max-h-[90vh]">
        <DialogHeader>
          <DialogTitle>{dialogueTitle}</DialogTitle>
          <DialogDescription>{dialogDescription}</DialogDescription>
        </DialogHeader>
        <Form {...form}>
          <form
            onSubmit={form.handleSubmit(onSubmit)}
            className="gap-8 grid grid-cols-3"
          >
            <FormAutocomplete
              form={form}
              label="Продукт"
              placeholder="Име на продукта"
              fieldName="product"
              options={products.map((p) => productToOption(form, p))}
              mandatory={true}
            />
            <FormInput
              form={form}
              label="Сума"
              fieldName={'price'}
              placeholder={'Сума на покупка'}
              mandatory={true}
              parseInput={(input, currentValue) => {
                return priceRegex.test(input) ? input : currentValue;
              }}
            />
            <div>
              <FormField
                control={form.control}
                name="store"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel mandatory={true}>Магазин</FormLabel>
                    <Popover>
                      <PopoverTrigger asChild>
                        <FormControl>
                          <Button
                            variant="outline"
                            role="combobox"
                            className={cn(
                              'w-full justify-between',
                              (!field.value || showNewStoreInput) &&
                                'text-muted-foreground',
                              getFieldState('store').error &&
                                'border-destructive'
                            )}
                          >
                            <div className="flex flex-row gap-2">
                              {field.value && !showNewStoreInput && (
                                <StoreIcon
                                  iconId={
                                    stores.find((s) => s.name === field.value)
                                      ?.iconID
                                  }
                                />
                              )}
                              {!showNewStoreInput && field.value
                                ? field.value
                                : 'Избери магазин'}
                            </div>
                            <CaretSortIcon className="ml-2 h-4 w-4 shrink-0 opacity-50" />
                          </Button>
                        </FormControl>
                      </PopoverTrigger>
                      <PopoverContent className="w-[200px] p-0">
                        <Command>
                          <CommandInput
                            placeholder="Търсене..."
                            className="h-9"
                          />
                          <CommandEmpty>No framework found.</CommandEmpty>
                          <CommandList>
                            <CommandGroup>
                              <Button
                                variant="ghost"
                                type="button"
                                onClick={() => {
                                  field.onChange('');
                                  setShowNewStoreInput(true);
                                  setTimeout(
                                    () => newStoreInputRef.current.focus(),
                                    100
                                  );
                                }}
                                className="my-1 w-full font-normal text-sm justify-center"
                              >
                                <PlusIcon
                                  size={15}
                                  className="text-primary mr-1"
                                />
                                Add new
                              </Button>
                              {stores.map((store) => (
                                <CommandItem
                                  value={store.name}
                                  key={store.id}
                                  onSelect={() => {
                                    form.setValue('store', store.name);
                                    trigger('store');
                                    setShowNewStoreInput(false);
                                  }}
                                >
                                  <div className="flex flex-row gap-2">
                                    <StoreIcon iconId={store.iconID} />
                                    {store.name || 'Изберете магазин'}
                                  </div>
                                  <CheckIcon
                                    className={cn(
                                      'ml-auto h-4 w-4',
                                      store.name === field.value
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
                    {!showNewStoreInput && <FormMessage />}
                  </FormItem>
                )}
              />
              {showNewStoreInput && (
                <FormInput
                  form={form}
                  fieldName="store"
                  placeholder="Име на магазин"
                  containerClassName="mt-3"
                  ref={newStoreInputRef}
                />
              )}
            </div>
            <FormDatePicker
              form={form}
              fieldName="date"
              label="Дата на покупката"
              placeholder={'Изберете дата'}
            />
            <FormCheckbox form={form} fieldName="discount" label="Намаление" />
            <Button type="submit" className="col-span-1 col-start-2">
              Submit
            </Button>
            <DialogClose className="absolute right-4 top-4 rounded-sm opacity-70 ring-offset-background transition-opacity hover:opacity-100 focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2 disabled:pointer-events-none data-[state=open]:bg-accent data-[state=open]:text-muted-foreground">
              <Cross2Icon className="h-4 w-4" />
              <span className="sr-only">Close</span>
            </DialogClose>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
};

export default FormDialog;
