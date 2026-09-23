import { CircleCheck, Cookie, Search, X } from 'lucide-react';
import { useCallback } from 'react';

import FormDialog from '@/components/forms/form-modal';
import ReceiptScanPanel from '@/components/receipts/ReceiptScanPanel';
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import {
  Card,
  CardContent,
  CardFooter,
  CardHeader,
} from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import PageSelector from '@/components/ui/page-selector';
import { Spinner } from '@/components/ui/spinner';
import Icon from '@/components/ui/store-icon';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { formatDate, formatEur } from '@/lib/format';
import usePaginatedResource from '@/lib/usePaginatedResource';

const toTableRow = (purchase) => {
  return (
    <TableRow key={purchase.id}>
      <TableCell>
        <div className="flex flex-row items-center gap-2 overflow-x-hidden">
          <Cookie />
          {purchase.product.name}
        </div>
      </TableCell>
      <TableCell>{formatEur(purchase.priceEur)}</TableCell>
      <TableCell>
        <div className="flex flex-row items-center gap-2">
          <Icon iconId={purchase.store.iconID} /> {purchase.store.name}
        </div>
      </TableCell>
      <TableCell>
        <div className="flex flex-row items-center justify-center">
          {purchase.discountAmountEur > 0 ? (
            <CircleCheck className="size-5 stroke-2 text-green-600" />
          ) : (
            <X className="size-5 stroke-2 text-red-600" />
          )}
        </div>
      </TableCell>
      <TableCell>{formatDate(purchase.date)}</TableCell>
    </TableRow>
  );
};

export const Purchases = () => {
  console.log('Rendering Purchases component...');

  const {
    data: purchasesPage,
    isLoading,
    error,
    searchQuery,
    setSearchQuery,
    fetchPage,
  } = usePaginatedResource('purchases', { pageSize: 10, debounceMs: 750 });

  // Logic when submitting the purchase register form
  const handlePurchaseCreation = useCallback(() => {
    // After creating a purchase, fetch the first page to refresh data
    fetchPage(0);
  }, [fetchPage]);

  // Render the component
  return (
    <div className="flex min-h-svh w-full items-center justify-center">
      <Card className="flex w-[800px] flex-col justify-between">
        <CardHeader className="px-7">
          <div className="relative">
            <Search className="text-muted-foreground absolute top-2.5 left-2.5 h-4 w-4" />
            <Input
              type="search"
              placeholder="Търсене..."
              className="pl-8"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
            />
          </div>
          <FormDialog
            buttonLabel="Нова Покупка"
            dialogueTitle="Нова покупка"
            dialogDescription='Запазете нова покупка. Натиснете "Запази" когато сте готови.'
            handlePurchaseCreation={handlePurchaseCreation}
          />
          <ReceiptScanPanel onPurchasesCreated={handlePurchaseCreation} />
        </CardHeader>
        <CardContent className="grow text-nowrap">
          {isLoading ? (
            <Spinner className={'stroke-primary size-20 stroke-1'} />
          ) : error ? (
            <Alert variant="destructive">
              <AlertTitle>Неуспешно зареждане на покупките</AlertTitle>
              <AlertDescription>{error.message}</AlertDescription>
            </Alert>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="w-[50%]">Продукт</TableHead>
                  <TableHead className="w-[10%]">Сума</TableHead>
                  <TableHead className="w-[20%]">Магазин</TableHead>
                  <TableHead className="w-[5%] text-center">
                    Намаление
                  </TableHead>
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
          )}
        </CardContent>
        {purchasesPage && purchasesPage.totalPages > 0 && (
          <CardFooter>
            <PageSelector
              currentPage={purchasesPage.pageId + 1}
              pagesCount={purchasesPage.totalPages}
              onClick={(pageId) => fetchPage(pageId - 1)}
            />
          </CardFooter>
        )}
      </Card>
    </div>
  );
};
