import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import SendVerification from '@/pages/SendVerification';

beforeEach(() => {
  globalThis.fetch = vi.fn(() => Promise.resolve({ ok: true }));
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

describe('SendVerification', () => {
  it('requests a new verification email through the inactive-user endpoint', async () => {
    render(<SendVerification />);

    await screen.findByText(/линкът е изпратен/i);

    expect(globalThis.fetch).toHaveBeenCalledWith(
      '/api/users/resend-verification',
      {
        method: 'POST',
        credentials: 'same-origin',
      }
    );
  });
});
