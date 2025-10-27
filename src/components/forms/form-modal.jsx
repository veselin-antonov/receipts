import { useAuth } from '@/components/auth/AuthContext';
import FormAutocomplete from '@/components/forms/form-autocomplete';
import FormCheckbox from '@/components/forms/form-checkbox';
import FormCombobox from '@/components/forms/form-combobox';
import FormDatePicker from '@/components/forms/form-date-picker';
import FormInput from '@/components/forms/form-input';
import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog';
import { FieldGroup } from '@/components/ui/field';
import { API_URL } from '@/lib/utils';
import { zodResolver } from '@hookform/resolvers/zod';
import { Cross2Icon } from '@radix-ui/react-icons';
import { bg } from 'date-fns/locale';
import { useCallback, useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';

const priceRegex = new RegExp('^\\d*[.,]?\\d{0,2}$');

// Custom error map for Bulgarian messages
const customErrorMap = (issue, ctx) => {
  if (issue.path.includes('date')) {
    if (issue.code === 'invalid_type') {
      return {
        message:
          issue.received === 'undefined'
            ? 'Датата е задължителна.'
            : 'Невалидна дата.',
      };
    }
  }
  return { message: ctx.defaultError };
};

const customBgLocale = {
  ...bg,
  localize: {
    ...bg.localize,
    month: (n) => {
      const month = bg.localize.month(n);
      return month.charAt(0).toUpperCase() + month.slice(1);
    },
  },
};

const formSchema = z.object({
  // The name of the product
  product: z
    .string({
      error: (iss) =>
        iss.input === undefined
          ? 'Продуктът е задължителен.'
          : 'Невалидно име.',
    })
    .min(1, 'Продуктът е задължителен.')
    .max(50),
  // The price of the product
  price: z
    .string({
      error: (iss) =>
        iss.input === undefined ? 'Цената е задължителна.' : 'Невалидна сума!',
    })
    .min(1, 'Цената е задължителна.')
    .regex(priceRegex, 'Невалидна сума!'),
  // The store where the product was purchased
  store: z
    .string({
      error: (iss) =>
        iss.input === undefined
          ? 'Магазинът е задължителен.'
          : 'Невалиден магазин.',
    })
    .min(1, 'Магазинът е задължителен.'),
  // Whether the product was on discount
  discount: z.boolean().optional(),
  // The date of purchase
  date: z.date('Датата е задължителна.'),
});

const productToOption = (setFormValue, p) => {
  const option = {
    value: p.name,
    label: p.name,
    key: p.id,
    onSelect: () => setFormValue('product', p.name),
  };

  return option;
};

const storeToOption = (setFormValue, s) => {
  const option = {
    value: s.name,
    label: s.name,
    iconID: s.iconID,
    key: s.id,
    onSelect: () => setFormValue('store', s.name),
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

  const form = useForm({
    resolver: zodResolver(formSchema, {
      errorMap: customErrorMap,
    }),
    defaultValues: {
      product: '',
      price: '',
      discount: false,
      store: '',
      date: '',
    },
    reValidateMode: 'onChange',
  });

  const { setValue: setFormValue } = form;

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

  async function handleSubmit(values) {
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
      <DialogContent className="max-h-[60vh] sm:max-w-[700px]">
        <DialogHeader>
          <DialogTitle>{dialogueTitle}</DialogTitle>
          <DialogDescription>{dialogDescription}</DialogDescription>
        </DialogHeader>
        <form id="purchase-form" onSubmit={form.handleSubmit(handleSubmit)}>
          <FieldGroup className="grid grid-cols-3 gap-8">
            <FormAutocomplete
              form={form}
              fieldName="product"
              label="Продукт"
              placeholder="Име на продукта"
              options={products.map((p) => productToOption(setFormValue, p))}
            />
            <FormInput
              form={form}
              label="Сума"
              fieldName={'price'}
              placeholder={'Сума на покупка'}
              parseInput={(input, currentValue) => {
                return priceRegex.test(input) ? input : currentValue;
              }}
            />
            <FormCombobox
              form={form}
              label="Магазин"
              fieldName="store"
              placeholder="Изберете магазин"
              searchPlaceholder="Търси..."
              newOptionLabel="Добави нов магазин"
              noResultsMessage="Няма намерени резултати"
              options={stores.map((s) => storeToOption(setFormValue, s))}
            />
            <FormDatePicker
              form={form}
              fieldName="date"
              label="Дата на покупката"
              placeholder={'Изберете дата'}
              locale={customBgLocale}
              mode="single"
              disabled={(date) =>
                date > new Date() || date < new Date('1900-01-01')
              }
              format="dd MMM yyyy"
              formatOptions={{ locale: customBgLocale }}
            />
            <FormCheckbox form={form} fieldName="discount" label="Намаление" />
            <Button type="submit" className="col-span-1 col-start-2">
              Submit
            </Button>
            <DialogClose className="absolute right-4 top-4 rounded-sm opacity-70 ring-offset-background transition-opacity hover:opacity-100 focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2 disabled:pointer-events-none data-[state=open]:bg-accent data-[state=open]:text-muted-foreground">
              <Cross2Icon className="h-4 w-4" />
              <span className="sr-only">Close</span>
            </DialogClose>
          </FieldGroup>
        </form>
      </DialogContent>
    </Dialog>
  );
};

export default FormDialog;
