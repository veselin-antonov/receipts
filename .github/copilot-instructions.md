# Copilot Instructions for Receipts UI

This is a React/Vite frontend for a receipts management application that tracks product prices from receipts with an interactive UI.

## Architecture Overview

- **Frontend Framework**: React 18 with Vite build system and React Router v7
- **Styling**: Tailwind CSS with shadcn/ui component library (New York style)
- **Forms**: React Hook Form with Zod validation
- **State Management**: React Context for authentication, local state for components
- **API Integration**: Proxy setup routes `/api/*` to `https://localhost:7002` backend
- **Development**: HTTPS dev server with SSL certificates (`localhost.crt`, `localhost.key`)

## Project Structure Patterns

### Component Organization

- `src/components/ui/` - Reusable shadcn/ui components (form, button, card, etc.)
- `src/components/forms/` - Form-specific wrappers (form-input, form-modal, etc.)
- `src/components/auth/` - Authentication components (AuthContext, PrivateRoute, PublicRoute)
- `src/pages/` - Route components (Login, Purchases, Register, etc.)

### Import Aliases

Use the `@/` alias for all internal imports:

```jsx
import { Button } from '@/components/ui/button';
import { API_URL } from '@/lib/utils';
```

## Authentication Flow

The app uses a stateful authentication pattern:

- `AuthContext` manages authentication state with `AUTH_STATUS` enum
- `PrivateRoute` and `PublicRoute` components handle route protection
- Authentication checks against backend API on app load
- Uses `PageLoader` component during auth verification

## Key Conventions

### Forms

- All forms use React Hook Form with Zod schemas
- Form components wrap shadcn/ui primitives with validation
- Modal forms use `FormDialog` from `form-modal.jsx`
- Form field components: `FormInput`, `FormDatePicker`, `FormAutocomplete`, `FormCheckbox`

### API Communication

- Backend calls use the `API_URL` constant from `@/lib/utils`
- Environment variable `VITE_API_URL` or defaults to `/api`
- Vite proxy forwards `/api/*` requests to backend server
- Auth is the HttpOnly `JWT` cookie: send `credentials: 'same-origin'`, never an
  `Authorization` header
- The API sends data, not presentation: amounts are full-precision EUR numbers
  (`priceEur`), dates are ISO `yyyy-MM-dd`. Send request dates the same way
- All display formatting and date conversion lives in `src/lib/format.js`:
  `formatEur` (the only place amounts are rounded), `formatDate`, and
  `toIsoDate` for request dates. Never build a request date with
  `toISOString()`: it shifts the picked day to UTC

### Styling

- Tailwind with CSS variables defined in `src/index.css`
- Use `cn()` utility from `@/lib/utils` for conditional class merging
- Components follow shadcn/ui patterns with forwardRef and className props

## Development Commands

```bash
npm run dev      # Start HTTPS dev server
npm run build    # Production build
npm run lint     # ESLint with React rules
npm run preview  # Preview production build
```

## Common Patterns

### Page Components

Pages should handle loading states, error handling, and API calls:

```jsx
const { isAuthenticated, logout } = useAuth();
// Use hooks for data fetching and state management
```

### Component Structure

Follow this pattern for new components:

```jsx
import { cn } from '@/lib/utils';
import { forwardRef } from 'react';

const Component = forwardRef(({ className, ...props }, ref) => {
  return <div ref={ref} className={cn('base-classes', className)} {...props} />;
});
```

### Error Handling

Authentication errors should redirect to login. Other errors should show user-friendly messages using the Alert component pattern.
