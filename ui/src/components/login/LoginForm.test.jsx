import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthProvider } from '@/components/auth/AuthContext';
import LoginForm from '@/components/login/LoginForm';

const renderLogin = () =>
  render(
    <AuthProvider>
      <MemoryRouter initialEntries={['/login']}>
        <Routes>
          <Route path="/login" element={<LoginForm />} />
          <Route path="/purchases" element={<h1>Purchases page</h1>} />
          <Route
            path="/not-verified"
            element={<h1>Профилът не е потвърден</h1>}
          />
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
      text: () => Promise.resolve('3600000'),
    })
  );
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

describe('LoginForm', () => {
  it('enables login after initial auth check and navigates after successful login', async () => {
    const user = userEvent.setup();

    renderLogin();

    const submit = await screen.findByRole('button', { name: /влизане/i });
    await waitFor(() => expect(submit).toBeEnabled());

    await user.type(screen.getByLabelText('Имейл'), 'vesko@example.com');
    await user.type(screen.getByLabelText('Парола'), 'correct-password');
    await user.click(submit);

    await screen.findByRole('heading', { name: /purchases page/i });

    expect(globalThis.fetch).toHaveBeenCalledWith('/api/auth/token', {
      method: 'POST',
      credentials: 'same-origin',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Basic ${btoa('vesko@example.com:correct-password')}`,
      },
    });
  });

  const submitLogin = async () => {
    const user = userEvent.setup();

    renderLogin();

    const submit = await screen.findByRole('button', { name: /влизане/i });
    await waitFor(() => expect(submit).toBeEnabled());

    await user.type(screen.getByLabelText('Имейл'), 'vesko@example.com');
    await user.type(screen.getByLabelText('Парола'), 'correct-password');
    await user.click(submit);
  };

  it('navigates to the not-verified page when the API says ACCOUNT_NOT_VERIFIED', async () => {
    globalThis.fetch = vi.fn(() =>
      Promise.resolve({
        ok: false,
        status: 403,
        text: () =>
          Promise.resolve(
            JSON.stringify({
              type: 'about:blank',
              title: 'Account Not Verified',
              status: 403,
              error: 'ACCOUNT_NOT_VERIFIED',
            })
          ),
      })
    );

    await submitLogin();

    await screen.findByText(/профилът не е потвърден/i);
  });

  it('shows a generic error for a 403 without the unverified code', async () => {
    globalThis.fetch = vi.fn(() =>
      Promise.resolve({
        ok: false,
        status: 403,
        // What Spring's CORS filter sends for a disallowed origin
        text: () => Promise.resolve('Invalid CORS request'),
      })
    );

    await submitLogin();

    await screen.findByText(/сървърът отказа достъп/i);
    expect(
      screen.queryByText(/профилът не е потвърден/i)
    ).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /влизане/i })).toBeEnabled();
  });

  it('shows a generic error for a 403 with a different problem code', async () => {
    globalThis.fetch = vi.fn(() =>
      Promise.resolve({
        ok: false,
        status: 403,
        text: () =>
          Promise.resolve(
            JSON.stringify({ status: 403, error: 'SOMETHING_ELSE' })
          ),
      })
    );

    await submitLogin();

    await screen.findByText(/сървърът отказа достъп/i);
    expect(
      screen.queryByText(/профилът не е потвърден/i)
    ).not.toBeInTheDocument();
  });
});
