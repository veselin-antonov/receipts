import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import ReceiptScanPanel from '@/components/receipts/ReceiptScanPanel';

const scanResponse = {
  storeSuggestion: {
    rawStoreName: 'BILLA',
    storeSuggestion: { id: 'store-1', name: 'BILLA', iconID: 'billa' },
  },
  rawStoreName: 'BILLA',
  purchaseDate: '2026-06-08',
  purchases: [
    {
      rawProductName: 'MILK 1L',
      productSuggestions: [
        { id: 'product-1', name: 'Milk 1L', iconID: 'milk' },
      ],
      price: 2.49,
      quantity: 1,
      quantityUnit: 'PIECE',
      discountAmount: 0,
    },
    {
      rawProductName: 'UNKNOWN COFFEE',
      productSuggestions: [],
      price: 8.99,
      quantity: 1,
      quantityUnit: 'PIECE',
      discountAmount: 0,
    },
  ],
};

const createdPurchases = [{ id: 'purchase-1' }];

beforeEach(() => {
  globalThis.fetch = vi.fn((url) => {
    if (String(url).endsWith('/products')) {
      return Promise.resolve({
        ok: true,
        json: () =>
          Promise.resolve([
            { id: 'product-1', name: 'Milk 1L', iconID: 'milk' },
            { id: 'product-2', name: 'Coffee Beans', iconID: 'coffee' },
          ]),
      });
    }

    if (String(url).endsWith('/receipts/scan')) {
      return Promise.resolve({
        ok: true,
        json: () => Promise.resolve(scanResponse),
      });
    }

    if (String(url).endsWith('/receipts/submit')) {
      return Promise.resolve({
        ok: true,
        json: () => Promise.resolve(createdPurchases),
      });
    }

    return Promise.reject(new Error(`Unexpected URL: ${url}`));
  });
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

describe('ReceiptScanPanel', () => {
  it('uploads a receipt image and renders parsed purchases for review', async () => {
    const user = userEvent.setup();
    render(<ReceiptScanPanel />);

    const file = new File(['receipt image'], 'receipt.jpg', {
      type: 'image/jpeg',
    });
    await user.upload(screen.getByLabelText(/качете касова бележка/i), file);
    await user.click(screen.getByRole('button', { name: /сканирай/i }));

    expect(await screen.findByText('MILK 1L')).toBeInTheDocument();
    expect(screen.getByDisplayValue('Milk 1L')).toBeInTheDocument();
    expect(screen.getByDisplayValue('UNKNOWN COFFEE')).toBeInTheDocument();
    expect(screen.getAllByDisplayValue('BILLA')).toHaveLength(2);
    expect(screen.getByDisplayValue('2.49')).toBeInTheDocument();
    expect(screen.getByText(/разпознати: 1/i)).toBeInTheDocument();
    expect(screen.getByText(/нови: 1/i)).toBeInTheDocument();

    const [scanUrl, scanOptions] = globalThis.fetch.mock.calls.find(([url]) =>
      String(url).endsWith('/receipts/scan')
    );
    expect(scanUrl).toBe('/api/receipts/scan');
    expect(scanOptions.method).toBe('POST');
    expect(scanOptions.credentials).toBe('same-origin');
    expect(scanOptions.body).toBeInstanceOf(FormData);
  });

  it('submits reviewed parsed purchases and notifies the parent', async () => {
    const user = userEvent.setup();
    const onPurchasesCreated = vi.fn();
    render(<ReceiptScanPanel onPurchasesCreated={onPurchasesCreated} />);

    await user.upload(
      screen.getByLabelText(/качете касова бележка/i),
      new File(['receipt image'], 'receipt.jpg', { type: 'image/jpeg' })
    );
    await user.click(screen.getByRole('button', { name: /сканирай/i }));
    await screen.findByText('MILK 1L');

    await user.clear(screen.getByLabelText(/продукт за ред 1/i));
    await user.type(screen.getByLabelText(/продукт за ред 1/i), 'Edited Milk');
    await user.click(screen.getByRole('button', { name: /запази покупките/i }));

    await waitFor(() =>
      expect(onPurchasesCreated).toHaveBeenCalledWith(createdPurchases)
    );

    const [submitUrl, submitOptions] = globalThis.fetch.mock.calls.find(
      ([url]) => String(url).endsWith('/receipts/submit')
    );
    expect(submitUrl).toBe('/api/receipts/submit');
    expect(submitOptions.method).toBe('POST');
    expect(submitOptions.headers).toMatchObject({
      'Content-Type': 'application/json',
    });
    expect(submitOptions.credentials).toBe('same-origin');
    expect(JSON.parse(submitOptions.body)).toEqual({
      purchases: [
        {
          productId: null,
          productName: 'Edited Milk',
          storeId: 'store-1',
          storeName: 'BILLA',
          price: 2.49,
          date: '2026-06-08',
          quantity: 1,
          quantityUnit: 'PIECE',
          discountAmount: 0,
        },
        {
          productId: null,
          productName: 'UNKNOWN COFFEE',
          storeId: 'store-1',
          storeName: 'BILLA',
          price: 8.99,
          date: '2026-06-08',
          quantity: 1,
          quantityUnit: 'PIECE',
          discountAmount: 0,
        },
      ],
    });
  });

  it('lets users manually match an unmatched parsed product with autocomplete', async () => {
    const user = userEvent.setup();
    render(<ReceiptScanPanel />);

    await user.upload(
      screen.getByLabelText(/качете касова бележка/i),
      new File(['receipt image'], 'receipt.jpg', { type: 'image/jpeg' })
    );
    await user.click(screen.getByRole('button', { name: /сканирай/i }));
    await screen.findByText('UNKNOWN COFFEE');

    const unmatchedProductInput = screen.getByLabelText(/продукт за ред 2/i);
    await user.clear(unmatchedProductInput);
    await user.type(unmatchedProductInput, 'Coffee');
    await user.click(await screen.findByText('Coffee Beans'));

    expect(screen.getByText(/разпознати: 2/i)).toBeInTheDocument();
    expect(screen.getByText(/нови: 0/i)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /запази покупките/i }));

    await waitFor(() =>
      expect(globalThis.fetch).toHaveBeenCalledWith(
        '/api/receipts/submit',
        expect.objectContaining({ method: 'POST' })
      )
    );

    const [, matchedSubmitOptions] = globalThis.fetch.mock.calls.find(([url]) =>
      String(url).endsWith('/receipts/submit')
    );
    expect(JSON.parse(matchedSubmitOptions.body).purchases[1]).toMatchObject({
      productId: 'product-2',
      productName: 'Coffee Beans',
    });
  });
});
