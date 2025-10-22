import { useAuth } from '@/components/auth/AuthContext';
import {
  Card,
  CardContent,
  CardFooter,
  CardHeader,
} from '@/components/common/card';
import { Input } from '@/components/common/input';
import PageSelector from '@/components/common/page-selector';
import StoreIcon from '@/components/common/store-icon';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/common/table';
import FormDialog from '@/components/forms/form-modal';
import { API_URL } from '@/lib/utils';
import {
  CheckCircledIcon,
  CookieIcon,
  Cross1Icon,
} from '@radix-ui/react-icons';
import { Search } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router';

const toTableRow = (purchase) => {
  return (
    <TableRow key={purchase.id}>
      <TableCell>
        <div className="flex flex-row items-center gap-2 overflow-x-hidden">
          <CookieIcon />
          {purchase.product.name}
        </div>
      </TableCell>
      <TableCell>{purchase.price}</TableCell>
      <TableCell>
        <div className="flex flex-row items-center gap-2">
          <StoreIcon iconId={purchase.store.iconID} /> {purchase.store.name}
        </div>
      </TableCell>
      <TableCell>
        <div className="flex flex-row justify-center items-center">
          {purchase.discount ? (
            <CheckCircledIcon className="text-green-600 stroke-2" />
          ) : (
            <Cross1Icon className="text-red-600 stroke-2" />
          )}
        </div>
      </TableCell>
      <TableCell>{purchase.date}</TableCell>
    </TableRow>
  );
};

export const Purchases = () => {
  console.log('Rendering Purchases component...');

  // Define state
  const [searchQuery, setSearchQuery] = useState('');
  const [purchasesPage, setPurchasesPages] = useState();

  // Use the authentication context
  const { isAuthenticated, fetchAuthStatus } = useAuth();

  const navigate = useNavigate();

  // Fetch purchases from API
  const fetchPurchases = useCallback(
    async (pageNumber = 0, pageSize = 0, searchQuery = '') => {
      if (!isAuthenticated()) {
        console.log('Cannot fetch purchases. User is not authenticated.');
        navigate('/login');
      }

      console.log(
        `Fetching purchases with params: pageNumber=${pageNumber}, pageSize=${pageSize}, searchQuery=${searchQuery}`
      );

      let url = `${API_URL}/purchases?pageNumber=${pageNumber}&pageSize=${pageSize}&searchQuery=${searchQuery}`;

      const response = await fetch(url, {
        headers: { Authorization: `Bearer ${localStorage.getItem('token')}` },
      });

      if (response.status === 401) {
        console.log('Authorization problem. Fetching token status...');
        fetchAuthStatus();
      } else {
        const jsonData = await response.json();
        setPurchasesPages(jsonData);
        console.log('Fetched purchases:', jsonData);
      }
    },
    [isAuthenticated, fetchAuthStatus, navigate]
  );

  // Effect to run when searchQuery changes
  useEffect(() => {
    console.log('effect() function triggered with searchQuery:', searchQuery);
    let timer = setTimeout(() => {
      fetchPurchases(0, 10, searchQuery);
    }, 1500);

    return () => clearTimeout(timer);
  }, [fetchPurchases, searchQuery]);

  // Logic when submitting the purchase register form
  const handlePurchaseCreation = useCallback(
    (purchase) => {
      const currentPurchases = purchasesPage.contents;

      currentPurchases.pop();
      currentPurchases.unshift(purchase);

      setPurchasesPages({
        ...purchasesPage,
        contents: currentPurchases,
      });
    },
    [purchasesPage]
  );

  // Render the component
  return (
    <div>
      <Card className="w-[800px] mx-auto my-[5vh]">
        <CardHeader className="px-7">
          <div className="relative">
            <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
            <Input
              type="search"
              placeholder="Търсене..."
              className="pl-8"
              onInput={(e) => {
                console.log('searchQuery:', e.target.value);
                setSearchQuery(e.target.value);
              }}
            />
          </div>
          <FormDialog
            buttonLabel="Нова Покупка"
            dialogueTitle="Нова покупка"
            dialogDescription='Запазете нова покупка. Натиснете "Запази" когато сте готови.'
            handlePurchaseCreation={handlePurchaseCreation}
          />
        </CardHeader>
        <CardContent className="text-nowrap">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="w-[50%]">Продукт</TableHead>
                <TableHead className="w-[10%]">Сума</TableHead>
                <TableHead className="w-[20%]">Магазин</TableHead>
                <TableHead className="w-[5%] text-center">Намаление</TableHead>
                <TableHead className="w-[15%]">Дата</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {purchasesPage && purchasesPage.contents?.length ? (
                purchasesPage.contents.map(toTableRow)
              ) : (
                <TableRow>
                  <TableCell className="h-24 text-center" colSpan="5">
                    Няма намерени покупки.
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </CardContent>
        {purchasesPage && purchasesPage.totalPages > 0 && (
          <CardFooter>
            <PageSelector
              currentPage={purchasesPage.pageId + 1}
              pagesCount={purchasesPage.totalPages}
              onClick={(pageId) => fetchPurchases(pageId - 1, 10, searchQuery)}
            />
          </CardFooter>
        )}
      </Card>
    </div>
  );
};
