import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router';

import { API_URL } from '@/lib/utils';

/**
 * Custom hook for fetching resources with proper loading, error, and cleanup handling.
 * @param {string} endpoint - The API endpoint to fetch from.
 * @param {string} resourceName - A friendly name for logging purposes.
 * @returns {{ data: any, isLoading: boolean, error: Error | null }}
 */
const useFetchResource = (endpoint, resourceName) => {
  const navigate = useNavigate();

  const [data, setData] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    const abortController = new AbortController();
    let isActive = true;

    const doFetch = async () => {
      setIsLoading(true);
      setError(null);

      try {
        const response = await fetch(`${API_URL}/${endpoint}`, {
          signal: abortController.signal,
          headers: { Authorization: `Bearer ${localStorage.getItem('token')}` },
        });

        if (!response.ok) {
          if (response.status === 401) {
            navigate('/login');
            return;
          }
          throw new Error(`Failed to fetch ${resourceName}`);
        }

        const result = await response.json();

        if (isActive) {
          setData(result);
        }
      } catch (e) {
        if (e.name === 'AbortError') {
          console.log(`${resourceName} fetch aborted`);
        } else if (isActive) {
          console.error(`Error fetching ${resourceName}:`, e);
          setError(e);
        }
      } finally {
        if (isActive) {
          setIsLoading(false);
        }
      }
    };

    doFetch();

    return () => {
      isActive = false;
      abortController.abort();
    };
  }, [endpoint, resourceName, navigate]);

  return { data, isLoading, error };
};

export default useFetchResource;
