import { API_URL } from '@/lib/utils';

const normalizeEndpoint = (endpoint) => endpoint.replace(/^\/+/, '');

export const buildApiUrl = (endpoint, queryParams = {}) => {
  const query = new URLSearchParams();

  Object.entries(queryParams).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      query.set(key, String(value));
    }
  });

  const queryString = query.toString();
  const baseUrl = `${API_URL}/${normalizeEndpoint(endpoint)}`;
  return queryString ? `${baseUrl}?${queryString}` : baseUrl;
};

const getResponseText = async (response) => {
  try {
    return await response.text();
  } catch {
    return '';
  }
};

const parseErrorMessage = (response, bodyText) => {
  if (bodyText) {
    try {
      const parsed = JSON.parse(bodyText);
      return (
        parsed.message ??
        parsed.error ??
        parsed.detail ??
        parsed.title ??
        bodyText
      );
    } catch {
      return bodyText;
    }
  }

  return response.statusText || `HTTP ${response.status}`;
};

export class ApiError extends Error {
  constructor(message, { status, statusText, body } = {}) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.statusText = statusText;
    this.body = body;
  }
}

export const apiFetchJson = async (endpoint, options = {}) => {
  const { query, signal, headers, ...fetchOptions } = options;
  const response = await fetch(buildApiUrl(endpoint, query), {
    ...fetchOptions,
    signal,
    credentials: fetchOptions.credentials ?? 'same-origin',
    headers: {
      Accept: 'application/json',
      ...headers,
    },
  });

  if (!response.ok) {
    const body = await getResponseText(response);
    throw new ApiError(parseErrorMessage(response, body), {
      status: response.status,
      statusText: response.statusText,
      body,
    });
  }

  if (response.status === 204) {
    return null;
  }

  try {
    return await response.json();
  } catch (error) {
    throw new ApiError(
      `Invalid JSON response from ${endpoint}: ${error.message}`,
      {
        status: response.status,
        statusText: response.statusText,
      }
    );
  }
};
