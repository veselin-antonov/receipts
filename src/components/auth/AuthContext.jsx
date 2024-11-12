import { API_URL } from '@/lib/utils';
import React, { createContext, useContext, useEffect, useState } from 'react';

const AuthContext = createContext();

export function useAuth() {
  return useContext(AuthContext);
}

const AUTH_EXPIRATION = 'authExpiration';

export const AuthProvider = ({ children }) => {
  const [isAuthInProgress, setIsAuthInProgress] = useState(true);
  const [authExpirationState, setAuthExpirationState] = useState(null);

  // Fetch the authentication status on component mount
  useEffect(() => {
    console.log(
      'Checking authentication status... Auth is in progress:',
      isAuthInProgress
    );
    setIsAuthInProgress(true);
    if (!authExpirationState) {
      const storedExpirationPeriod = Number.parseInt(
        localStorage.getItem(AUTH_EXPIRATION)
      );

      if (storedExpirationPeriod > Date.now()) {
        console.log(
          'Stored expiration is valid.. Auth is in progress:',
          isAuthInProgress
        );
        setAuthExpirationState(storedExpirationPeriod);
        setIsAuthInProgress(false);
      } else {
        console.log(
          'Stored expiration is invalid.. Fetching auth status... Auth is in progress:',
          isAuthInProgress
        );
        localStorage.removeItem(AUTH_EXPIRATION);
        fetchAuthStatus();
      }
    } else {
      setIsAuthInProgress(false);
    }
  }, []);

  async function fetchAuthStatus() {
    console.log('Fetching auth status...');
    setIsAuthInProgress(true);

    fetch(API_URL + '/auth/status', {
      method: 'GET',
    })
      .then((response) => {
        if (response.ok) {
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
        console.log(
          'Fetched status. Auth expires in ',
          Number.parseInt(responseText) / 1000,
          ' seconds'
        );
        saveAuthExpiration(Number.parseInt(responseText));
      })
      .catch((error) => {
        console.error('Error:', error);
        invalidateAuthExpiration();
      })
      .finally(() => setIsAuthInProgress(false));
  }

  function saveAuthExpiration(expirationPeriod) {
    const expirationDateInMS = Date.now() + expirationPeriod;
    localStorage.setItem(AUTH_EXPIRATION, expirationDateInMS);
    setAuthExpirationState(expirationDateInMS);
  }

  function invalidateAuthExpiration() {
    localStorage.removeItem(AUTH_EXPIRATION);
    setAuthExpirationState(null);
  }

  function isAuthenticated() {
    return (
      !isAuthInProgress &&
      authExpirationState &&
      authExpirationState > Date.now()
    );
  }

  const value = {
    isAuthenticated,
    isAuthInProgress,
    setIsAuthInProgress,
    saveAuthExpiration,
    fetchAuthStatus,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};
