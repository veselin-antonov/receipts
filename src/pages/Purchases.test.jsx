import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { Purchases } from '@/pages/Purchases';

const usePaginatedResourceMock = vi.hoisted(() => vi.fn());

vi.mock('@/lib/usePaginatedResource', () => ({
  default: usePaginatedResourceMock,
}));

vi.mock('@/components/forms/form-modal', () => ({
  default: () => <div data-testid="form-dialog" />,
}));

vi.mock('@/components/receipts/ReceiptScanPanel', () => ({
  default: () => <div data-testid="receipt-scan-panel" />,
}));

describe('Purchases', () => {
  it('shows a useful error when receipt fetching fails', () => {
    usePaginatedResourceMock.mockReturnValue({
      data: null,
      isLoading: false,
      error: new Error('Mongo timeout while fetching receipts'),
      searchQuery: '',
      setSearchQuery: vi.fn(),
      fetchPage: vi.fn(),
    });

    render(<Purchases />);

    expect(screen.getByRole('alert')).toHaveTextContent(
      'Mongo timeout while fetching receipts'
    );
    expect(
      screen.queryByText('Няма намерени покупки.')
    ).not.toBeInTheDocument();
  });

  it("renders the API's EUR numbers and ISO dates for display", () => {
    usePaginatedResourceMock.mockReturnValue({
      data: {
        contents: [
          {
            id: 'p1',
            product: { id: 'x', name: 'Мляко', iconID: null },
            // 12.65 BGN, converted by the API and deliberately unrounded
            priceEur: 6.467842297132164,
            date: '2025-10-27',
            store: { id: 's', name: 'Billa', iconID: 'billa' },
            discountAmountEur: 0.5,
          },
        ],
        pageId: 0,
        totalPages: 1,
      },
      isLoading: false,
      error: null,
      searchQuery: '',
      setSearchQuery: vi.fn(),
      fetchPage: vi.fn(),
    });

    render(<Purchases />);

    expect(
      screen.getByText((text) => text.replace(/\s/g, ' ') === '6,47 €')
    ).toBeInTheDocument();
    expect(screen.getByText('27.10.2025')).toBeInTheDocument();
    expect(screen.queryByText(/лв/)).not.toBeInTheDocument();
  });
});
