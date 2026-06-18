import { renderHook, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import useFetchResource from '@/lib/useFetchResource';

const wrapper = ({ children }) => <MemoryRouter>{children}</MemoryRouter>;

beforeEach(() => {
  globalThis.fetch = vi.fn(() =>
    Promise.resolve({
      ok: true,
      json: () => Promise.resolve([{ id: 'product-1', name: 'Milk' }]),
    })
  );
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('useFetchResource', () => {
  it('fetches resources through the shared same-origin API service', async () => {
    const { result } = renderHook(
      () => useFetchResource('products', 'products'),
      {
        wrapper,
      }
    );

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(globalThis.fetch).toHaveBeenCalledWith(
      '/api/products',
      expect.objectContaining({ credentials: 'same-origin' })
    );
    expect(
      globalThis.fetch.mock.calls[0][1].headers?.Authorization
    ).toBeUndefined();
    expect(result.current.data).toEqual([{ id: 'product-1', name: 'Milk' }]);
  });

  it('keeps backend error details for resource fetch failures', async () => {
    globalThis.fetch = vi.fn(() =>
      Promise.resolve({
        ok: false,
        status: 502,
        statusText: 'Bad Gateway',
        text: () =>
          Promise.resolve(JSON.stringify({ message: 'Products API down' })),
      })
    );

    const { result } = renderHook(
      () => useFetchResource('products', 'products'),
      {
        wrapper,
      }
    );

    await waitFor(() =>
      expect(result.current.error?.message).toBe('Products API down')
    );
    expect(result.current.isLoading).toBe(false);
  });
});
