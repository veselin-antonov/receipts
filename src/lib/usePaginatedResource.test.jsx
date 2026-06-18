import { act, renderHook, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import usePaginatedResource from '@/lib/usePaginatedResource';

const wrapper = ({ children }) => <MemoryRouter>{children}</MemoryRouter>;

beforeEach(() => {
  globalThis.fetch = vi.fn(() =>
    Promise.resolve({
      ok: true,
      json: () => Promise.resolve({ contents: [], pageId: 0, totalPages: 0 }),
    })
  );
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('usePaginatedResource', () => {
  it('fetches paginated resources with same-origin cookies and an encoded search query', async () => {
    const { result } = renderHook(
      () => usePaginatedResource('purchases', { pageSize: 10, debounceMs: 0 }),
      { wrapper }
    );

    act(() => {
      result.current.setSearchQuery('milk & coffee');
    });

    await waitFor(() => expect(globalThis.fetch).toHaveBeenCalled());

    const [url, options] = globalThis.fetch.mock.calls.at(-1);
    expect(url).toBe(
      '/api/purchases?pageNumber=0&pageSize=10&searchQuery=milk+%26+coffee'
    );
    expect(options).toMatchObject({ credentials: 'same-origin' });
    expect(options.headers?.Authorization).toBeUndefined();
  });

  it('exposes a useful API error message instead of dropping response details', async () => {
    globalThis.fetch = vi.fn(() =>
      Promise.resolve({
        ok: false,
        status: 500,
        statusText: 'Internal Server Error',
        text: () => Promise.resolve('Mongo timeout while fetching receipts'),
      })
    );

    const { result } = renderHook(
      () => usePaginatedResource('purchases', { debounceMs: 0 }),
      { wrapper }
    );

    await waitFor(() =>
      expect(result.current.error?.message).toContain(
        'Mongo timeout while fetching receipts'
      )
    );
    expect(result.current.isLoading).toBe(false);
  });

  it('surfaces malformed JSON responses as fetch errors', async () => {
    globalThis.fetch = vi.fn(() =>
      Promise.resolve({
        ok: true,
        json: () =>
          Promise.reject(new SyntaxError('Unexpected end of JSON input')),
      })
    );

    const { result } = renderHook(
      () => usePaginatedResource('purchases', { debounceMs: 0 }),
      { wrapper }
    );

    await waitFor(() =>
      expect(result.current.error?.message).toContain('Invalid JSON')
    );
    expect(result.current.data).toBeNull();
    expect(result.current.isLoading).toBe(false);
  });
});
