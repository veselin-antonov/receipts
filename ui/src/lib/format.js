// The one place wire values meet the screen. The API sends and accepts data,
// not presentation: amounts are full-precision EUR numbers and every date is
// ISO-8601 (yyyy-MM-dd for a calendar date). Rounding and display formats
// happen here and nowhere else, so nothing computed from an amount inherits a
// rounding error and no component invents its own date format.

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

// A picked calendar day as the API expects it. Built from the local date
// parts, not toISOString(): that converts to UTC first, so a date picked at
// local midnight east of Greenwich would be sent as the previous day.
export const toIsoDate = (date) => {
  if (!(date instanceof Date) || Number.isNaN(date.getTime())) return null;
  const year = String(date.getFullYear()).padStart(4, '0');
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
};

// A decimal a person typed, as a number for the API. Bulgarian writes 2,49:
// Number('2,49') is NaN, which JSON sends as null and the API would store as 0.
// Accepts either separator; anything else is NaN, for the caller to reject.
export const parseDecimal = (value) => {
  if (typeof value === 'number') return value;
  const text = String(value ?? '')
    .trim()
    .replace(',', '.');
  return /^(\d+(\.\d*)?|\.\d+)$/.test(text) ? Number(text) : Number.NaN;
};

// The units a purchase's quantity can be in, as the API names them, with
// their Bulgarian abbreviations.
export const QUANTITY_UNITS = [
  { value: 'PIECE', label: 'бр.' },
  { value: 'KILOGRAM', label: 'кг' },
  { value: 'LITER', label: 'л' },
  { value: 'GRAM', label: 'г' },
  { value: 'MILLILITER', label: 'мл' },
];

const quantityFormatter = new Intl.NumberFormat('bg-BG', {
  maximumFractionDigits: 3,
});

// "0,742 кг", "2 бр.". Nothing for a purchase without a quantity: a legacy
// row the migration has not reached yet.
export const formatQuantity = (quantity, unit) => {
  const label = QUANTITY_UNITS.find((u) => u.value === unit)?.label;
  return typeof quantity === 'number' && Number.isFinite(quantity) && label
    ? `${quantityFormatter.format(quantity)} ${label}`
    : '';
};
