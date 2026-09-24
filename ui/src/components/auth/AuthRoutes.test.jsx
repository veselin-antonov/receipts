import { cleanup, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthProvider } from '@/components/auth/AuthContext';
import { PrivateRoute } from '@/components/auth/PrivateRoute';
import { PublicRoute } from '@/components/auth/PublicRoute';

const renderRoutes = (initialPath) =>
  render(
    <AuthProvider>
      <MemoryRouter initialEntries={[initialPath]}>
        <Routes>
          <Route element={<PublicRoute />}>
            <Route path="/login" element={<h1>Login page</h1>} />
          </Route>
          <Route element={<PrivateRoute />}>
            <Route path="/purchases" element={<h1>Purchases page</h1>} />
          </Route>
        </Routes>
      </MemoryRouter>
    </AuthProvider>
  );

beforeEach(() => {
  const storage = new Map();
  vi.stubGlobal('localStorage', {
    getItem: vi.fn((key) => storage.get(key) ?? null),
    setItem: vi.fn((key, value) => storage.set(key, String(value))),
    removeItem: vi.fn((key) => storage.delete(key)),
    clear: vi.fn(() => storage.clear()),
  });
  globalThis.fetch = vi.fn(() =>
    Promise.resolve({
      ok: true,
      status: 200,
      text: () => Promise.resolve('3600000'),
    })
  );
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

describe('auth route guards', () => {
  it('restores authenticated state from the HttpOnly cookie on page refresh', async () => {
    renderRoutes('/purchases');

    await screen.findByRole('heading', { name: /purchases page/i });

    expect(globalThis.fetch).toHaveBeenCalledWith(
      '/api/auth/status',
      expect.objectContaining({
        method: 'GET',
        credentials: 'same-origin',
      })
    );
    expect(
      screen.queryByRole('heading', { name: /login page/i })
    ).not.toBeInTheDocument();
  });

  it('keeps public routes in a pending state until the cookie auth check resolves', async () => {
    renderRoutes('/login');

    await waitFor(() =>
      expect(
        screen.queryByRole('heading', { name: /login page/i })
      ).not.toBeInTheDocument()
    );
    await screen.findByRole('heading', { name: /purchases page/i });
  });

  it('does not trust stale local auth expiration when the server cookie is gone', async () => {
    localStorage.setItem('authExpiration', String(Date.now() + 3600000));
    globalThis.fetch = vi.fn(() =>
      Promise.resolve({
        ok: false,
        status: 401,
        text: () => Promise.resolve(''),
      })
    );

    renderRoutes('/login');

    await screen.findByRole('heading', { name: /login page/i });
    expect(
      screen.queryByRole('heading', { name: /purchases page/i })
    ).not.toBeInTheDocument();
    expect(globalThis.fetch).toHaveBeenCalledWith(
      '/api/auth/status',
      expect.objectContaining({ credentials: 'same-origin' })
    );
  });
});
