# API Contracts

This document captures the active backend request/response shapes that the UI and other clients depend on.

Notes:
- Authenticated routes use the JWT cookie set by `POST /api/auth/token`.
- The frontend should send same-origin credentials instead of storing bearer tokens in localStorage.
- Examples below show representative payload shapes based on the current controller and DTO contracts.

## Auth

### POST /api/auth/token

Purpose:
- authenticate with HTTP Basic credentials
- set the HttpOnly JWT cookie
- return the token lifetime in milliseconds

Auth:
- HTTP Basic credentials in the request

Response behavior:
- `200 OK` for active users
- `403 Forbidden` for authenticated but inactive/unverified users
- response body is a number representing cookie max-age in milliseconds
- response includes `Set-Cookie`

Example request:

```http
POST /api/auth/token HTTP/1.1
Authorization: Basic base64(user@example.com:password123)
```

Example success response:

```http
HTTP/1.1 200 OK
Set-Cookie: jwt=...; Path=/; HttpOnly; Secure; SameSite=Strict
Content-Type: application/json

3600000
```

Example inactive-user response:

```http
HTTP/1.1 403 Forbidden
Set-Cookie: jwt=...; Path=/; HttpOnly; Secure; SameSite=Strict
Content-Type: application/json

3600000
```

Important interpretation:
- a `403` here can mean the credentials were accepted but the account is still inactive

### GET /api/auth/status

Purpose:
- return the remaining authenticated session lifetime

Success response:

```json
3600000
```

Failure response:
- `401 Unauthorized` when there is no valid authenticated cookie

## Users

### POST /api/users/register

Purpose:
- create a new user and trigger verification flow

Request body:

```json
{
  "email": "user@example.com",
  "password": "password123"
}
```

Success response:
- `201 Created`

```json
{
  "email": "user@example.com"
}
```

Error responses:
- `409 Conflict` when the user already exists

### POST /api/users/verify?user=<email>&token=<token>

Purpose:
- verify and activate a registered user

Example request:

```http
POST /api/users/verify?user=user@example.com&token=verification-token HTTP/1.1
```

Responses:
- `200 OK` when verification succeeds
- `404 Not Found` when the verification token is missing
- `400 Bad Request` when the link/token is invalid

### POST /api/users/resend-verification

Purpose:
- resend verification mail for an authenticated inactive user

Auth:
- inactive authenticated user only

Success response:

```json
"Verification email resent"
```

## Products

### GET /api/products

Success response shape:

```json
[
  {
    "id": "6840c5c4bcb1f563dc1d0001",
    "name": "Milk",
    "iconID": "milk"
  }
]
```

### POST /api/products?name=<name>

Example request:

```http
POST /api/products?name=Milk HTTP/1.1
```

Success response shape:

```json
{
  "id": "6840c5c4bcb1f563dc1d0001",
  "name": "Milk",
  "iconID": "milk"
}
```

## Stores

### GET /api/stores

Success response shape:

```json
[
  {
    "id": "6840c5c4bcb1f563dc1d0101",
    "name": "Kaufland",
    "iconID": "kaufland"
  }
]
```

## Purchases

### GET /api/purchases?pageNumber=0&pageSize=10&searchQuery=milk

Purpose:
- return purchases for the authenticated user only

Success response shape:

```json
{
  "contents": [
    {
      "id": "6840c5c4bcb1f563dc1d1001",
      "product": {
        "id": "6840c5c4bcb1f563dc1d0001",
        "name": "Milk",
        "iconID": "milk"
      },
      "priceEur": 3.99,
      "date": "2026-02-13",
      "store": {
        "id": "6840c5c4bcb1f563dc1d0101",
        "name": "Kaufland",
        "iconID": "kaufland"
      },
      "discountAmountEur": 0.0
    }
  ],
  "pageId": 0,
  "totalPages": 1
}
```

### POST /api/purchases

Request body:

```json
{
  "productId": "6840c5c4bcb1f563dc1d0001",
  "productName": "Milk",
  "storeId": "6840c5c4bcb1f563dc1d0101",
  "storeName": "Kaufland",
  "price": 3.99,
  "currency": "EUR",
  "date": "13/02/2026",
  "quantity": 1.0,
  "quantityUnit": "PIECE",
  "discountAmount": 0.0
}
```

Success response shape:

```json
{
  "id": "6840c5c4bcb1f563dc1d1001",
  "product": {
    "id": "6840c5c4bcb1f563dc1d0001",
    "name": "Milk",
    "iconID": "milk"
  },
  "priceEur": 3.99,
  "date": "2026-02-13",
  "store": {
    "id": "6840c5c4bcb1f563dc1d0101",
    "name": "Kaufland",
    "iconID": "kaufland"
  },
  "discountAmountEur": 0.0
}
```

Money and dates:
- responses carry `priceEur` and `discountAmountEur` as plain numbers at full
  precision, converted from the currency the purchase was paid in at the fixed
  rate 1 EUR = 1.95583 BGN. Round only for display. There is no `price` field
  in responses, so no client can read an amount in the wrong currency
- requests carry `price` and `discountAmount` in `currency`, which is optional:
  `BGN` or `EUR`, defaulting to `EUR`. The recorded amount is stored as sent
  and never converted in place
- response dates are ISO `yyyy-MM-dd`; request dates are still `dd/MM/yyyy`
- a stored purchase with no currency is a server error, never a guess

Known issues:
- `searchQuery` currently matches nothing: it filters on a renamed field
- this endpoint resolves product and store by name only; `productId` and
  `storeId` are ignored, and a request with IDs but no names creates empty
  records. `POST /api/receipts/submit` handles IDs correctly

Quantity units accepted by the request DTO:
- `PIECE`
- `GRAM`
- `KILOGRAM`
- `MILLILITER`
- `LITER`

## Receipt scanning

### POST /api/receipts/scan

Purpose:
- parse an uploaded receipt and return review data before persistence

Auth:
- active authenticated user

Request:
- `Content-Type: multipart/form-data`
- file field name: `file`
- supported types: JPEG, JPG, PNG, GIF, WebP, PDF
- max size: 10MB

Example request:

```http
POST /api/receipts/scan HTTP/1.1
Content-Type: multipart/form-data

file=@receipt.jpg
```

Success response shape:

```json
{
  "storeSuggestion": {
    "storeSuggestion": {
      "id": "6840c5c4bcb1f563dc1d0101",
      "name": "Kaufland",
      "iconID": "kaufland"
    },
    "rawStoreName": "KAUFLAND"
  },
  "rawStoreName": "KAUFLAND",
  "purchaseDate": "2026-02-13",
  "purchases": [
    {
      "rawProductName": "MILK 3.6%",
      "productSuggestions": [
        {
          "id": "6840c5c4bcb1f563dc1d0001",
          "name": "Milk",
          "iconID": "milk"
        }
      ],
      "price": 3.99,
      "quantity": 1.0,
      "quantityUnit": "PIECE",
      "discountAmount": 0.0
    }
  ]
}
```

Error response for parsing/validation failures:
- `422 Unprocessable Entity`

Representative response shape:

```json
{
  "type": "about:blank",
  "title": "Receipt Parsing Failed",
  "status": 422,
  "detail": "Unsupported file type: text/plain. Allowed types: JPEG, PNG, GIF, WebP, PDF",
  "error": "RECEIPT_PARSING_ERROR"
}
```

### POST /api/receipts/submit

Purpose:
- persist reviewed receipt purchases for the authenticated user

Request body:

```json
{
  "purchases": [
    {
      "productId": "6840c5c4bcb1f563dc1d0001",
      "productName": "Milk",
      "storeId": "6840c5c4bcb1f563dc1d0101",
      "storeName": "Kaufland",
      "price": 3.99,
      "currency": "EUR",
      "date": "13/02/2026",
      "quantity": 1.0,
      "quantityUnit": "PIECE",
      "discountAmount": 0.0
    }
  ]
}
```

Validation note:
- `purchases` must not be empty

Success response shape:

```json
[
  {
    "id": "6840c5c4bcb1f563dc1d1001",
    "product": {
      "id": "6840c5c4bcb1f563dc1d0001",
      "name": "Milk",
      "iconID": "milk"
    },
    "priceEur": 3.99,
    "date": "2026-02-13",
    "store": {
      "id": "6840c5c4bcb1f563dc1d0101",
      "name": "Kaufland",
      "iconID": "kaufland"
    },
    "discountAmountEur": 0.0
  }
]
```

## Contract maintenance rules

Update this document whenever you change:
- controller routes
- auth behavior or status codes
- request DTO fields
- response DTO fields
- validation constraints
- accepted file types or upload limits
