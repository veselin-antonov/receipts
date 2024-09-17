import { clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

export const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:7002';

export function cn(...inputs) {
  return twMerge(clsx(inputs));
}
