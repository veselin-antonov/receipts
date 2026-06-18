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
});
