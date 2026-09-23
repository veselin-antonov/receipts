import { zodResolver } from '@hookform/resolvers/zod';
import { bg } from 'date-fns/locale';
import { X } from 'lucide-react';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';

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
import useFetchResource from '@/lib/useFetchResource';
import { API_URL } from '@/lib/utils';

const priceRegex = new RegExp('^\\d*[.,]?\\d{0,2}$');

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

const productToOption = (setFormValue, p) => ({
  value: p.name,
  label: p.name,
  key: p.id,
  onSelect: () => setFormValue('product', p.name),
});

const storeToOption = (setFormValue, s) => ({
  value: s.name,
  label: s.name,
  iconID: s.iconID,
  key: s.id,
  onSelect: () => setFormValue('store', s.name),
});

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
  const [dialogOpen, setDialogOpen] = useState(false);

  const { data: stores, isLoading: areStoresLoading } = useFetchResource(
    'stores',
    'stores'
  );

  const { data: products, isLoading: areProductsLoading } = useFetchResource(
    'products',
    'products'
  );

  const form = useForm({
    resolver: zodResolver(formSchema),
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
              optionsLoading={areProductsLoading}
            />
            <FormInput
              form={form}
              label="Сума (€)"
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
              optionsLoading={areStoresLoading}
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
            <DialogClose className="ring-offset-background focus:ring-ring data-[state=open]:bg-accent data-[state=open]:text-muted-foreground absolute top-4 right-4 rounded-sm opacity-70 transition-opacity hover:opacity-100 focus:ring-2 focus:ring-offset-2 focus:outline-hidden disabled:pointer-events-none">
              <X className="h-4 w-4" />
              <span className="sr-only">Close</span>
            </DialogClose>
          </FieldGroup>
        </form>
      </DialogContent>
    </Dialog>
  );
};

export default FormDialog;
