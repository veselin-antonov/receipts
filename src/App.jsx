import { effect, signal } from '@preact/signals-react';
import '@/App.css';
import { useSignals } from '@preact/signals-react/runtime';
import {
  Table,
  TableHeader,
  TableBody,
  TableHead,
  TableRow,
  TableCell,
} from '@/components/ui/table';
import {
  Card,
  CardHeader,
  CardContent,
  CardFooter,
} from '@/components/ui/card';
import {
  CheckCircledIcon,
  CheckIcon,
  CookieIcon,
  Cross1Icon,
} from '@radix-ui/react-icons';
import { Search } from 'lucide-react';
import { Input } from '@/components/ui/input';
import PageSelector from '@/components/ui/page-selector';
import StoreIcon from '@/components/ui/store-icon';
import FormDialog from '@/components/forms/form-modal';
import { API_URL } from '@/lib/utils';

const purchasesPage = signal();

const fetchPurchases = async (
  pageNumber = 0,
  pageSize = 0,
  searchQuery = ''
) => {
  console.log(
    `Fetching purchases with params: pageNumber=${pageNumber}, pageSize=${pageSize}, searchQuery=${searchQuery}`
  );
  let url = `${API_URL}/purchases?pageNumber=${pageNumber}&pageSize=${pageSize}&searchQuery=${searchQuery}`;
  console.log('Constructed URL:', url);
  const response = await fetch(url);
  const jsonData = await response.json();
  purchasesPage.value = jsonData;
};

const handlePurchaseCreation = (purchase) => {
  const currentPurchases = purchasesPage.value.contents;

  currentPurchases.pop();
  currentPurchases.unshift(purchase);

  purchasesPage.value = {
    ...purchasesPage.value,
    contents: currentPurchases,
  };
};

const toTableRow = (purchase) => {
  return (
    <TableRow key={purchase.id}>
      <TableCell>
        <div className='flex flex-row items-center gap-2 overflow-x-hidden'>
          <CookieIcon />
          {purchase.product.name}
        </div>
      </TableCell>
      <TableCell>{purchase.price}</TableCell>
      <TableCell>
        <div className='flex flex-row items-center gap-2'>
          <StoreIcon iconId={purchase.store.iconID} /> {purchase.store.name}
        </div>
      </TableCell>
      <TableCell>
        <div className='flex flex-row justify-center items-center'>
          {purchase.discount ? (
            <CheckCircledIcon className='text-green-600 stroke-2' />
          ) : (
            <Cross1Icon className='text-red-600 stroke-2' />
          )}
        </div>
      </TableCell>
      <TableCell>{purchase.date}</TableCell>
    </TableRow>
  );
};

const searchQuery = signal('');

effect(() => {
  console.log(
    'effect() function triggered with searchQuery:',
    searchQuery.value
  );
  let timer = setTimeout(() => {
    fetchPurchases(0, 10, searchQuery.value);
  }, 1500);

  return () => clearTimeout(timer);
});

const dialogOpen = signal(false);

const App = () => {
  useSignals();
  console.log('Rendering App with purchases:', purchasesPage.value);

  return (
    <div>
      <Card className='w-[800px] mx-auto my-[5vh]'>
        <CardHeader className='px-7'>
          <div className='relative'>
            <Search className='absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground' />
            <Input
              type='search'
              placeholder='Search...'
              //w-full rounded-lg bg-background pl-8 md:w-[200px] lg:w-[320px]
              className='pl-8'
              onInput={(e) => {
                console.log('searchQuery:', e.target.value);
                searchQuery.value = e.target.value;
              }}
            />
          </div>
          <FormDialog
            buttonLabel='Нова Покупка'
            dialogueTitle='Нова покупка'
            dialogDescription='Запазете нова покупка. Натиснете "Запази" когато сте готови.'
            dialogOpen={dialogOpen}
            handlePurchaseCreation={handlePurchaseCreation}
          />
        </CardHeader>
        <CardContent className='text-nowrap'>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className='w-[50%]'>Продукт</TableHead>
                <TableHead className='w-[10%]'>Сума</TableHead>
                <TableHead className='w-[20%]'>Магазин</TableHead>
                <TableHead className='w-[5%] text-center'>Намаление</TableHead>
                <TableHead className='w-[15%]'>Дата</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {purchasesPage.value && purchasesPage.value.contents?.length ? (
                purchasesPage.value.contents.map(toTableRow)
              ) : (
                <TableRow>
                  <TableCell className='h-24 text-center' colSpan='5'>
                    Няма намерени покупки.
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </CardContent>
        {purchasesPage.value && purchasesPage.value.totalPages > 0 && (
          <CardFooter>
            <PageSelector
              currentPage={purchasesPage.value.pageId + 1}
              pagesCount={purchasesPage.value.totalPages}
              onClick={(pageId) =>
                fetchPurchases(pageId - 1, 10, searchQuery.value)
              }
            />
          </CardFooter>
        )}
      </Card>
    </div>
  );
};

export default App;
