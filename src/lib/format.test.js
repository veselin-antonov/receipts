import { describe, expect, it } from 'vitest';

import { formatDate, formatEur } from '@/lib/format';

// Intl separates the amount and the sign with a narrow no-break space.
const normalizeSpaces = (s) => s.replace(/[\u00a0\u202f]/g, ' ');

describe('formatEur', () => {
  it('rounds a full-precision amount to cents only for display', () => {
    // 12.65 BGN as the API serves it, converted but unrounded.
    expect(normalizeSpaces(formatEur(6.467842297132164))).toBe('6,47 €');
  });

  it('renders whole amounts with two decimals', () => {
    expect(normalizeSpaces(formatEur(3))).toBe('3,00 €');
  });

  it('renders nothing for a missing amount rather than NaN', () => {
    expect(formatEur(undefined)).toBe('');
    expect(formatEur('3,29 лв.')).toBe('');
  });
});

describe('formatDate', () => {
  it('renders an ISO date day-first', () => {
    expect(formatDate('2025-10-27')).toBe('27.10.2025');
  });

  it('renders nothing for anything that is not an ISO date', () => {
    expect(formatDate(undefined)).toBe('');
    expect(formatDate('27.10.25 г.')).toBe('');
  });
});
