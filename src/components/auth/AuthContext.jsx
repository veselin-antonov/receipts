import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
} from 'react';

import { API_URL } from '@/lib/utils';

const parseExpirationPeriod = (responseText) => {
  const expirationPeriod = Number.parseInt(responseText, 10);

  if (!Number.isFinite(expirationPeriod) || expirationPeriod <= 0) {
    throw new Error('Invalid auth status response');
  }

  return expirationPeriod;
};

const AuthContext = createContext();

export function useAuth() {
  return useContext(AuthContext);
}

// Auth status enum
export const AUTH_STATUS = {
  UNAUTHENTICATED: 'UNAUTHENTICATED',
  EXPIRED: 'EXPIRED',
  AUTHENTICATED: 'AUTHENTICATED',
  PENDING: 'PENDING',
};

const AUTH_EXPIRATION = 'authExpiration';

export const AuthProvider = ({ children }) => {
  const [authStatus, setAuthStatus] = useState(AUTH_STATUS.PENDING);
  const [, setAuthExpirationState] = useState(null);

  const saveAuthExpiration = useCallback((expirationPeriod) => {
    const expirationDateInMS = Date.now() + expirationPeriod;
    localStorage.setItem(AUTH_EXPIRATION, expirationDateInMS);
    setAuthExpirationState(expirationDateInMS);
    setAuthStatus(AUTH_STATUS.AUTHENTICATED); // Set status to authenticated
  }, []);

  const invalidateAuthExpiration = useCallback(() => {
    localStorage.removeItem(AUTH_EXPIRATION);
    setAuthExpirationState(null);
  }, []);

  function handleExpiredAuth() {
    // Placeholder for refresh token logic
    // For now, treat expired as unauthenticated
    console.log('Auth expired, setting as UNAUTHENTICATED');
    invalidateAuthExpiration();
    setAuthStatus(AUTH_STATUS.UNAUTHENTICATED);

    // TODO: Implement refresh token logic here
    // try {
    //   const refreshResult = await refreshToken();
    //   if (refreshResult.success) {
    //     saveAuthExpiration(refreshResult.expirationPeriod);
    //     setAuthStatus(AUTH_STATUS.AUTHENTICATED);
    //   } else {
    //     setAuthStatus(AUTH_STATUS.UNAUTHENTICATED);
    //   }
    // } catch (error) {
    //   setAuthStatus(AUTH_STATUS.UNAUTHENTICATED);
    // }
  }

  // Helper functions for consumers
  function isAuthenticated() {
    return authStatus === AUTH_STATUS.AUTHENTICATED;
  }

  function isAuthInProgress() {
    return authStatus === AUTH_STATUS.PENDING;
  }

  function isUnauthenticated() {
    return authStatus === AUTH_STATUS.UNAUTHENTICATED;
  }

  function isExpired() {
    return authStatus === AUTH_STATUS.EXPIRED;
  }

  const fetchAuthStatus = useCallback(async () => {
    console.log('Fetching auth status from server...');
    setAuthStatus(AUTH_STATUS.PENDING);

    try {
      const response = await fetch(API_URL + '/auth/status', {
        method: 'GET',
        credentials: 'same-origin',
      });

      if (!response.ok) {
        throw new Error(
          response.status === 401
            ? 'Invalid or expired token'
            : 'Unknown server error'
        );
      }

      const responseText = await response.text();
      const expirationPeriod = parseExpirationPeriod(responseText);
      console.log(
        'Fetched status. Auth expires in',
        expirationPeriod / 1000,
        'seconds'
      );
      saveAuthExpiration(expirationPeriod);
      return true;
    } catch (error) {
      console.error('Error fetching auth status:', error);
      invalidateAuthExpiration();
      setAuthStatus(AUTH_STATUS.UNAUTHENTICATED);
      return false;
    }
  }, [invalidateAuthExpiration, saveAuthExpiration]);

  const value = {
    // Auth status and helpers
    authStatus,
    setAuthStatus,
    isAuthenticated,
    isAuthInProgress,
    isUnauthenticated,
    isExpired,

    // Actions
    fetchAuthStatus,
    saveAuthExpiration,
    handleExpiredAuth,

    // Backward compatibility (deprecated)
    setIsAuthInProgress: () => setAuthStatus(AUTH_STATUS.PENDING),
  };

  // Fetch the authentication status on component mount
  useEffect(() => {
    console.log('Checking authentication status...');

    const storedExpirationPeriod = Number.parseInt(
      localStorage.getItem(AUTH_EXPIRATION),
      10
    );

    if (storedExpirationPeriod > Date.now()) {
      console.log('Stored expiration is valid; verifying server cookie');
    } else if (storedExpirationPeriod) {
      console.log('Stored expiration is expired; checking server cookie');
      localStorage.removeItem(AUTH_EXPIRATION);
    } else {
      console.log('No stored expiration. Checking server cookie');
    }

    fetchAuthStatus();
  }, [fetchAuthStatus]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};
