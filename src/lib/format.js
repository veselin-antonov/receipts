// Display formatting. The API sends data, not presentation: amounts are
// full-precision EUR numbers and dates are ISO yyyy-MM-dd. Rounding happens
// here and nowhere else, so nothing computed from an amount inherits a
// rounding error.

const eurFormatter = new Intl.NumberFormat('bg-BG', {
  style: 'currency',
  currency: 'EUR',
});

export const formatEur = (amount) =>
  typeof amount === 'number' && Number.isFinite(amount)
    ? eurFormatter.format(amount)
    : '';

// Split the string rather than going through Date: new Date('2025-10-27')
// is midnight UTC, which renders as the previous day west of Greenwich.
export const formatDate = (isoDate) => {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(isoDate ?? '');
  return match ? `${match[3]}.${match[2]}.${match[1]}` : '';
};
