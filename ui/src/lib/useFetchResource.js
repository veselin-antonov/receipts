import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router';

import { apiFetchJson } from '@/lib/api';

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
        const result = await apiFetchJson(endpoint, {
          signal: abortController.signal,
        });

        if (isActive) {
          setData(result);
        }
      } catch (e) {
        if (e.name === 'AbortError') {
          console.log(`${resourceName} fetch aborted`);
        } else if (e.status === 401) {
          navigate('/login');
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
