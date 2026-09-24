import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router';

import { apiFetchJson } from '@/lib/api';

/**
 * Custom hook for fetching paginated resources with search support.
 * @param {string} endpoint - The API endpoint to fetch from (e.g., 'purchases').
 * @param {Object} options - Configuration options.
 * @param {number} [options.pageSize=10] - Number of items per page.
 * @param {number} [options.debounceMs=500] - Debounce delay for search in milliseconds.
 * @returns {{
 *   data: any,
 *   isLoading: boolean,
 *   error: Error | null,
 *   searchQuery: string,
 *   setSearchQuery: function,
 *   fetchPage: function,
 *   currentPage: number
 * }}
 */
const usePaginatedResource = (endpoint, options = {}) => {
  const { pageSize = 10, debounceMs = 500 } = options;

  const navigate = useNavigate();

  const [data, setData] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [currentPage, setCurrentPage] = useState(0);

  // Use ref to track active fetch and enable cleanup
  const abortControllerRef = useRef(null);

  const fetchPage = useCallback(
    async (pageNumber = 0, query = searchQuery) => {
      // Abort any in-flight request
      if (abortControllerRef.current) {
        abortControllerRef.current.abort();
      }

      const abortController = new AbortController();
      abortControllerRef.current = abortController;

      setIsLoading(true);
      setError(null);
      setCurrentPage(pageNumber);

      try {
        const result = await apiFetchJson(endpoint, {
          query: {
            pageNumber,
            pageSize,
            searchQuery: query,
          },
          signal: abortController.signal,
        });

        // Only update state if this request wasn't aborted
        if (!abortController.signal.aborted) {
          setData(result);
        }
      } catch (e) {
        if (e.name === 'AbortError') {
          console.log(`${endpoint} fetch aborted`);
        } else if (e.status === 401) {
          navigate('/login');
        } else if (!abortController.signal.aborted) {
          console.error(`Error fetching ${endpoint}:`, e);
          setError(e);
        }
      } finally {
        if (!abortController.signal.aborted) {
          setIsLoading(false);
        }
      }
    },
    [endpoint, pageSize, searchQuery, navigate]
  );

  // Debounced search effect
  useEffect(() => {
    const timer = setTimeout(() => {
      fetchPage(0, searchQuery);
    }, debounceMs);

    return () => clearTimeout(timer);
  }, [searchQuery, debounceMs, fetchPage]);

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      if (abortControllerRef.current) {
        abortControllerRef.current.abort();
      }
    };
  }, []);

  return {
    data,
    isLoading,
    error,
    searchQuery,
    setSearchQuery,
    fetchPage,
    currentPage,
  };
};

export default usePaginatedResource;
