import { API_URL } from '@/lib/utils';
import {
  createContext,
  useContext,
  useEffect,
  useState,
  useCallback,
} from 'react';

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
  const [authExpirationDate, setAuthExpirationState] = useState(null);

  function saveAuthExpiration(expirationPeriod) {
    const expirationDateInMS = Date.now() + expirationPeriod;
    localStorage.setItem(AUTH_EXPIRATION, expirationDateInMS);
    setAuthExpirationState(expirationDateInMS);
    setAuthStatus(AUTH_STATUS.AUTHENTICATED); // Set status to authenticated
  }

  function invalidateAuthExpiration() {
    localStorage.removeItem(AUTH_EXPIRATION);
    setAuthExpirationState(null);
  }

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

    fetch(API_URL + '/auth/status', {
      method: 'GET',
    })
      .then((response) => {
        if (response.ok || response.status === 403) {
          return response.text();
        } else {
          throw new Error(
            response.status === 401
              ? 'Invalid or expired token'
              : 'Unknown server error'
          );
        }
      })
      .then((responseText) => {
        const expirationPeriod = Number.parseInt(responseText);
        console.log(
          'Fetched status. Auth expires in',
          expirationPeriod / 1000,
          'seconds'
        );
        saveAuthExpiration(expirationPeriod);
        setAuthStatus(AUTH_STATUS.AUTHENTICATED);
      })
      .catch((error) => {
        console.error('Error fetching auth status:', error);
        invalidateAuthExpiration();
        setAuthStatus(AUTH_STATUS.UNAUTHENTICATED);
      });
  }, []);

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
    console.log(
      'Checking authentication status... Current status:',
      authStatus
    );
    setAuthStatus(AUTH_STATUS.PENDING);

    if (!authExpirationDate) {
      const storedExpirationPeriod = Number.parseInt(
        localStorage.getItem(AUTH_EXPIRATION)
      );

      if (storedExpirationPeriod > Date.now()) {
        console.log('Stored expiration is valid, setting as AUTHENTICATED');
        setAuthExpirationState(storedExpirationPeriod);
        setAuthStatus(AUTH_STATUS.AUTHENTICATED);
      } else if (storedExpirationPeriod) {
        console.log('Stored expiration is expired, setting as EXPIRED');
        localStorage.removeItem(AUTH_EXPIRATION);
        setAuthStatus(AUTH_STATUS.EXPIRED);
        // Could call handleExpiredAuth() here for refresh token logic
      } else {
        console.log('No stored expiration. Setting status as UNAUTHENTICATED');
        setAuthStatus(AUTH_STATUS.UNAUTHENTICATED);
      }
    } else {
      // We already have expiration date, check if it's still valid
      if (authExpirationDate > Date.now()) {
        setAuthStatus(AUTH_STATUS.AUTHENTICATED);
      } else {
        setAuthStatus(AUTH_STATUS.EXPIRED);
        // Could call handleExpiredAuth() here for refresh token logic
      }
    }
  }, [authExpirationDate, authStatus]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};
