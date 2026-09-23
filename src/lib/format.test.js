import { describe, expect, it } from 'vitest';

import { formatDate, formatEur, toIsoDate } from '@/lib/format';

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

describe('toIsoDate', () => {
  it('sends the picked calendar day, not its UTC instant', () => {
    // Local midnight, as a date picker produces. In Sofia this instant is
    // 21:00 or 22:00 UTC the day before; toISOString() would send that day.
    expect(toIsoDate(new Date(2026, 0, 1))).toBe('2026-01-01');
    expect(toIsoDate(new Date(2026, 8, 23, 0, 0, 0))).toBe('2026-09-23');
  });

  it('round-trips through formatDate', () => {
    expect(formatDate(toIsoDate(new Date(2025, 9, 27)))).toBe('27.10.2025');
  });

  it('returns null for anything that is not a valid Date', () => {
    expect(toIsoDate('')).toBeNull();
    expect(toIsoDate(new Date('nope'))).toBeNull();
  });
});
