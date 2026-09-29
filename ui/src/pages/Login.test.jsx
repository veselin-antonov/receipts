import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthProvider } from '@/components/auth/AuthContext';
import { PublicRoute } from '@/components/auth/PublicRoute';
import { Login } from '@/pages/Login';

// The login page as App.jsx mounts it: inside the real AuthProvider and
// PublicRoute, so a login attempt goes through the same auth state changes
// as in the browser. Rendering LoginForm on its own cannot show an error
// being lost to a remount.
const renderLoginRoute = () =>
  render(
    <AuthProvider>
      <MemoryRouter initialEntries={['/login']}>
        <Routes>
          <Route element={<PublicRoute />}>
            <Route path="/login" element={<Login />} />
            <Route
              path="/not-verified"
              element={<h1>Профилът не е потвърден</h1>}
            />
          </Route>
        </Routes>
      </MemoryRouter>
    </AuthProvider>
  );

const response = (status, body = '') => ({
  ok: status >= 200 && status < 300,
  status,
  text: () => Promise.resolve(body),
});

// No session cookie: the startup status check answers 401, then the login
// attempt gets `tokenResponse`. The token call answers after a real delay, as
// a network would, so React renders whatever the page shows while it waits.
const mockApi = (tokenResponse) => {
  globalThis.fetch = vi.fn((url) =>
    url.endsWith('/auth/status')
      ? Promise.resolve(response(401))
      : new Promise((resolve) => setTimeout(() => resolve(tokenResponse), 50))
  );
};

const submitLogin = async () => {
  const user = userEvent.setup();
  renderLoginRoute();

  const submit = await screen.findByRole('button', { name: /влизане/i });
  await user.type(screen.getByLabelText('Имейл'), 'vesko@example.com');
  await user.type(screen.getByLabelText('Парола'), 'a-password');
  await user.click(submit);

  await waitFor(() =>
    expect(globalThis.fetch).toHaveBeenCalledWith(
      '/api/auth/token',
      expect.anything()
    )
  );
};

beforeEach(() => {
  const storage = new Map();
  vi.stubGlobal('localStorage', {
    getItem: vi.fn((key) => storage.get(key) ?? null),
    setItem: vi.fn((key, value) => storage.set(key, String(value))),
    removeItem: vi.fn((key) => storage.delete(key)),
    clear: vi.fn(() => storage.clear()),
  });
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

describe('login page errors', () => {
  it.each([
    [
      'a 403 without the unverified code (CORS rejection)',
      response(403, 'Invalid CORS request'),
      /сървърът отказа достъп/i,
    ],
    [
      'a 401 (wrong credentials)',
      response(401),
      /неправилни имейл и\/или парола/i,
    ],
    ['a 429 (rate limited)', response(429), /твърде много неуспешни опити/i],
  ])('shows the error for %s on the login page', async (_, token, message) => {
    mockApi(token);

    await submitLogin();

    expect(await screen.findByText(message)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /влизане/i })).toBeEnabled();
  });

  it('goes to the not-verified page on ACCOUNT_NOT_VERIFIED', async () => {
    mockApi(
      response(
        403,
        JSON.stringify({ status: 403, error: 'ACCOUNT_NOT_VERIFIED' })
      )
    );

    await submitLogin();

    expect(
      await screen.findByRole('heading', { name: /профилът не е потвърден/i })
    ).toBeInTheDocument();
  });
});
