import { useEffect, useMemo, useState } from 'react';

import Autocomplete from '@/components/ui/autocomplete';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { API_URL } from '@/lib/utils';

const toDisplayDate = (dateValue) => {
  if (!dateValue) return '';

  if (/^\d{2}\/\d{2}\/\d{4}$/.test(dateValue)) {
    return dateValue;
  }

  const [year, month, day] = dateValue.split('-');
  if (year && month && day) {
    return `${day}/${month}/${year}`;
  }

  return dateValue;
};

const toInitialReviewPurchase = (scanResult, purchase) => {
  const productSuggestion = purchase.productSuggestions?.[0];
  const storeSuggestion = scanResult.storeSuggestion?.storeSuggestion;

  return {
    productId: productSuggestion?.id ?? '',
    productName: productSuggestion?.name ?? purchase.rawProductName ?? '',
    rawProductName: purchase.rawProductName ?? '',
    productSuggestions: purchase.productSuggestions ?? [],
    storeId: storeSuggestion?.id ?? '',
    storeName:
      storeSuggestion?.name ??
      scanResult.storeSuggestion?.rawStoreName ??
      scanResult.rawStoreName ??
      '',
    price: purchase.price ?? 0,
    date: toDisplayDate(scanResult.purchaseDate),
    quantity: purchase.quantity ?? 1,
    quantityUnit: purchase.quantityUnit ?? 'PIECE',
    discountAmount: purchase.discountAmount ?? 0,
  };
};

const updatePurchaseAt = (purchases, index, field, value) =>
  purchases.map((purchase, currentIndex) =>
    currentIndex === index ? { ...purchase, [field]: value } : purchase
  );

const updatePurchaseFieldsAt = (purchases, index, fields) =>
  purchases.map((purchase, currentIndex) =>
    currentIndex === index ? { ...purchase, ...fields } : purchase
  );

const uniqueProductsById = (products) => {
  const seen = new Set();
  return products.filter((product) => {
    if (!product?.id || seen.has(product.id)) {
      return false;
    }
    seen.add(product.id);
    return true;
  });
};

const toProductOption = (product, onSelect) => ({
  value: product.name,
  label: product.name,
  key: product.id,
  onSelect: () => onSelect(product),
});

const toSubmitPurchase = (purchase) => ({
  productId: purchase.productId || null,
  productName: purchase.productName,
  storeId: purchase.storeId || null,
  storeName: purchase.storeName,
  price: Number(purchase.price),
  date: purchase.date,
  quantity: Number(purchase.quantity),
  quantityUnit: purchase.quantityUnit,
  discountAmount: Number(purchase.discountAmount),
});

const ReceiptScanPanel = ({ onPurchasesCreated }) => {
  const [file, setFile] = useState(null);
  const [reviewPurchases, setReviewPurchases] = useState([]);
  const [products, setProducts] = useState([]);
  const [areProductsLoading, setAreProductsLoading] = useState(false);
  const [isScanning, setIsScanning] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState('');

  const hasReviewPurchases = reviewPurchases.length > 0;
  const matchedProductsCount = reviewPurchases.filter(
    (purchase) => purchase.productId
  ).length;
  const newProductsCount = reviewPurchases.length - matchedProductsCount;

  useEffect(() => {
    let isActive = true;
    const abortController = new AbortController();

    const fetchProducts = async () => {
      setAreProductsLoading(true);

      try {
        const response = await fetch(`${API_URL}/products`, {
          credentials: 'same-origin',
          signal: abortController.signal,
        });

        if (!response.ok) {
          throw new Error('Products fetch failed');
        }

        const result = await response.json();
        if (isActive) {
          setProducts(result ?? []);
        }
      } catch (e) {
        if (e.name !== 'AbortError') {
          console.error('Error fetching products:', e);
        }
      } finally {
        if (isActive) {
          setAreProductsLoading(false);
        }
      }
    };

    fetchProducts();

    return () => {
      isActive = false;
      abortController.abort();
    };
  }, []);

  const productOptionsByRow = useMemo(
    () =>
      reviewPurchases.map((purchase) =>
        uniqueProductsById([
          ...(purchase.productSuggestions ?? []),
          ...products,
        ])
      ),
    [products, reviewPurchases]
  );

  const handleProductInputChange = (index, value) => {
    setReviewPurchases((current) =>
      updatePurchaseFieldsAt(current, index, {
        productId: '',
        productName: value,
      })
    );
  };

  const handleProductSelect = (index, product) => {
    setReviewPurchases((current) =>
      updatePurchaseFieldsAt(current, index, {
        productId: product.id,
        productName: product.name,
      })
    );
  };

  const handleScan = async () => {
    if (!file) {
      setError('Изберете файл за сканиране.');
      return;
    }

    setIsScanning(true);
    setError('');

    const body = new FormData();
    body.append('file', file);

    try {
      const response = await fetch(`${API_URL}/receipts/scan`, {
        method: 'POST',
        credentials: 'same-origin',
        body,
      });

      if (!response.ok) {
        throw new Error('Receipt scan failed');
      }

      const scanResult = await response.json();
      setReviewPurchases(
        (scanResult.purchases ?? []).map((purchase) =>
          toInitialReviewPurchase(scanResult, purchase)
        )
      );
    } catch (e) {
      console.error('Error scanning receipt:', e);
      setError('Сканирането не беше успешно. Опитайте отново.');
    } finally {
      setIsScanning(false);
    }
  };

  const handleSubmit = async () => {
    setIsSubmitting(true);
    setError('');

    try {
      const response = await fetch(`${API_URL}/receipts/submit`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        credentials: 'same-origin',
        body: JSON.stringify({
          purchases: reviewPurchases.map(toSubmitPurchase),
        }),
      });

      if (!response.ok) {
        throw new Error('Receipt submit failed');
      }

      const createdPurchases = await response.json();
      setReviewPurchases([]);
      setFile(null);
      onPurchasesCreated?.(createdPurchases);
    } catch (e) {
      console.error('Error submitting scanned purchases:', e);
      setError('Покупките не бяха запазени. Опитайте отново.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <section className="space-y-4 rounded-lg border p-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end">
        <div className="grow space-y-2">
          <label className="text-sm font-medium" htmlFor="receipt-file">
            Качете касова бележка
          </label>
          <Input
            id="receipt-file"
            type="file"
            accept="image/*,.pdf"
            onChange={(event) => setFile(event.target.files?.[0] ?? null)}
          />
        </div>
        <Button type="button" onClick={handleScan} disabled={isScanning}>
          {isScanning ? 'Сканиране...' : 'Сканирай'}
        </Button>
      </div>

      {error && <p className="text-sm text-red-600">{error}</p>}

      {hasReviewPurchases && (
        <div className="space-y-3">
          <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
            <h3 className="font-medium">Преглед на разпознатите покупки</h3>
            <div className="flex flex-wrap gap-2 text-sm">
              <span className="rounded-full bg-green-100 px-3 py-1 text-green-800">
                Разпознати: {matchedProductsCount}
              </span>
              <span className="rounded-full bg-amber-100 px-3 py-1 text-amber-800">
                Нови: {newProductsCount}
              </span>
            </div>
          </div>
          <div className="space-y-3">
            {reviewPurchases.map((purchase, index) => (
              <div
                className="grid gap-3 rounded-md border p-3 md:grid-cols-[1.5fr_1.5fr_1fr]"
                key={`${purchase.rawProductName}-${index}`}
              >
                <div className="text-muted-foreground text-sm">
                  {purchase.rawProductName}
                </div>
                <div className="space-y-1 text-sm">
                  <div className="flex items-center justify-between gap-2">
                    <span>Продукт</span>
                    <span
                      className={
                        purchase.productId
                          ? 'text-xs text-green-700'
                          : 'text-xs text-amber-700'
                      }
                    >
                      {purchase.productId ? 'съществуващ' : 'нов продукт'}
                    </span>
                  </div>
                  <Autocomplete
                    aria-label={`Продукт за ред ${index + 1}`}
                    value={purchase.productName}
                    placeholder={
                      areProductsLoading ? 'Зареждане...' : 'Търси продукт'
                    }
                    options={(productOptionsByRow[index] ?? []).map((product) =>
                      toProductOption(product, (selectedProduct) =>
                        handleProductSelect(index, selectedProduct)
                      )
                    )}
                    onInputChange={(value) =>
                      handleProductInputChange(index, value)
                    }
                  />
                </div>
                <label className="space-y-1 text-sm">
                  <span>Магазин</span>
                  <Input
                    aria-label={`Магазин за ред ${index + 1}`}
                    value={purchase.storeName}
                    onChange={(event) =>
                      setReviewPurchases((current) =>
                        updatePurchaseAt(
                          current,
                          index,
                          'storeName',
                          event.target.value
                        )
                      )
                    }
                  />
                </label>
                <label className="space-y-1 text-sm">
                  <span>Цена</span>
                  <Input
                    aria-label={`Цена за ред ${index + 1}`}
                    inputMode="decimal"
                    value={purchase.price}
                    onChange={(event) =>
                      setReviewPurchases((current) =>
                        updatePurchaseAt(
                          current,
                          index,
                          'price',
                          event.target.value
                        )
                      )
                    }
                  />
                </label>
              </div>
            ))}
          </div>
          <Button type="button" onClick={handleSubmit} disabled={isSubmitting}>
            {isSubmitting ? 'Запазване...' : 'Запази покупките'}
          </Button>
        </div>
      )}
    </section>
  );
};

export default ReceiptScanPanel;
