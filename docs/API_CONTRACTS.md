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
- `200 OK` for active users; the body is a number, the cookie max-age in
  milliseconds
- `403 Forbidden` with a `ProblemDetail` whose `error` is
  `ACCOUNT_NOT_VERIFIED` when the credentials are correct but the account is
  not verified yet
- `401 Unauthorized` for wrong credentials
- both `200` and the unverified `403` include `Set-Cookie`; for an unverified
  account it is a limited token that only allows
  `POST /api/users/resend-verification` and `GET /api/auth/status`

Example request:

```http
POST /api/auth/token HTTP/1.1
Authorization: Basic base64(user@example.com:password123)
```

Example success response:

```http
HTTP/1.1 200 OK
Set-Cookie: JWT=...; Path=/; HttpOnly; Secure; SameSite=Strict
Content-Type: application/json

3600000
```

Example unverified-account response:

```http
HTTP/1.1 403 Forbidden
Set-Cookie: JWT=...; Path=/; HttpOnly; Secure; SameSite=Strict
Content-Type: application/problem+json

{
  "type": "about:blank",
  "title": "Account Not Verified",
  "status": 403,
  "detail": "The account's e-mail address has not been verified yet.",
  "instance": "/api/auth/token",
  "error": "ACCOUNT_NOT_VERIFIED"
}
```

Important interpretation:
- only `error: ACCOUNT_NOT_VERIFIED` means the account is unverified. A `403`
  without it comes from something else, such as a CORS rejection of the
  browser's origin, and must not be read as "unverified" (SPEC D15)

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

## Price history

### GET /api/products/{id}/prices

Purpose:
- a product's price history and the verdict figures (SPEC F2.2, F2.4), for the
  authenticated user's purchases only
- computed on every request, never cached (SPEC 9.3)

Rules:
- every price is a **unit price in EUR**, unrounded: the line total divided by
  the quantity, converted from the currency paid. The discount is reported but
  not subtracted, so prices compare with a shelf price
- grams and millilitres are priced per kilogram and per litre; `PIECE` is one
  package, whatever its size
- `unit` is what the figures are per. A product bought in more than one unit is
  compared in its most frequent one (ties: the most recent purchase's unit)
- a purchase without a positive quantity and a unit (a legacy row the quantity
  migration has not reached) is listed with `unitPriceEur: null` and left out
  of every figure; `comparable` says whether a purchase is counted
- `quantityAssumed: true` marks a legacy row whose quantity the migration set
  to 1; `stats.assumedCount` counts them
- `stats` is `null` when nothing is comparable. `typicalUnitPriceEur` is the
  median; `lowest` breaks ties to the most recent purchase; `usualStore` is
  the store bought at most often, ties to the most recent
- `stores` is cheapest typical price first; purchases without a store count in
  `stats` but not in `stores` or `usualStore`
- `series` is oldest first, one point per comparable purchase; `storeId`
  matches a `stores[].store.id`
- `purchases` is every purchase of the product, newest first; same-day
  purchases in the order they were saved

Status codes:
- `200` with empty lists and `stats: null` for a product the user never bought
- `404` for an unknown or malformed product id

Success response shape:

```json
{
  "product": { "id": "6840c5c4bcb1f563dc1d0001", "name": "Банани", "iconID": null },
  "unit": "KILOGRAM",
  "stats": {
    "count": 3,
    "assumedCount": 1,
    "typicalUnitPriceEur": 2.49,
    "lowest": {
      "purchaseId": "6840c5c4bcb1f563dc1d1002",
      "unitPriceEur": 2.4,
      "store": { "id": "6840c5c4bcb1f563dc1d0101", "name": "Lidl", "iconID": "lidl" },
      "date": "2026-03-01"
    },
    "last": {
      "purchaseId": "6840c5c4bcb1f563dc1d1003",
      "unitPriceEur": 2.49,
      "store": { "id": "6840c5c4bcb1f563dc1d0101", "name": "Lidl", "iconID": "lidl" },
      "date": "2026-10-01"
    },
    "usualStore": { "id": "6840c5c4bcb1f563dc1d0101", "name": "Lidl", "iconID": "lidl" }
  },
  "stores": [
    {
      "store": { "id": "6840c5c4bcb1f563dc1d0101", "name": "Lidl", "iconID": "lidl" },
      "count": 2,
      "typicalUnitPriceEur": 2.445,
      "lowestUnitPriceEur": 2.4,
      "last": {
        "purchaseId": "6840c5c4bcb1f563dc1d1003",
        "unitPriceEur": 2.49,
        "store": { "id": "6840c5c4bcb1f563dc1d0101", "name": "Lidl", "iconID": "lidl" },
        "date": "2026-10-01"
      }
    },
    {
      "store": { "id": "6840c5c4bcb1f563dc1d0102", "name": "Billa", "iconID": "billa" },
      "count": 1,
      "typicalUnitPriceEur": 2.49,
      "lowestUnitPriceEur": 2.49,
      "last": {
        "purchaseId": "6840c5c4bcb1f563dc1d1001",
        "unitPriceEur": 2.49,
        "store": { "id": "6840c5c4bcb1f563dc1d0102", "name": "Billa", "iconID": "billa" },
        "date": "2025-05-01"
      }
    }
  ],
  "series": [
    { "purchaseId": "6840c5c4bcb1f563dc1d1001", "date": "2025-05-01", "unitPriceEur": 2.49, "storeId": "6840c5c4bcb1f563dc1d0102", "quantityAssumed": true },
    { "purchaseId": "6840c5c4bcb1f563dc1d1002", "date": "2026-03-01", "unitPriceEur": 2.4, "storeId": "6840c5c4bcb1f563dc1d0101", "quantityAssumed": false },
    { "purchaseId": "6840c5c4bcb1f563dc1d1003", "date": "2026-10-01", "unitPriceEur": 2.49, "storeId": "6840c5c4bcb1f563dc1d0101", "quantityAssumed": false }
  ],
  "purchases": [
    {
      "id": "6840c5c4bcb1f563dc1d1003",
      "date": "2026-10-01",
      "store": { "id": "6840c5c4bcb1f563dc1d0101", "name": "Lidl", "iconID": "lidl" },
      "priceEur": 3.0876,
      "discountAmountEur": 0.0,
      "quantity": 1.24,
      "quantityUnit": "KILOGRAM",
      "quantityAssumed": false,
      "unitPriceEur": 2.49,
      "unit": "KILOGRAM",
      "comparable": true
    },
    {
      "id": "6840c5c4bcb1f563dc1d1002",
      "date": "2026-03-01",
      "store": { "id": "6840c5c4bcb1f563dc1d0101", "name": "Lidl", "iconID": "lidl" },
      "priceEur": 1.2,
      "discountAmountEur": 0.0,
      "quantity": 500.0,
      "quantityUnit": "GRAM",
      "quantityAssumed": false,
      "unitPriceEur": 2.4,
      "unit": "KILOGRAM",
      "comparable": true
    },
    {
      "id": "6840c5c4bcb1f563dc1d1001",
      "date": "2025-05-01",
      "store": { "id": "6840c5c4bcb1f563dc1d0102", "name": "Billa", "iconID": "billa" },
      "priceEur": 2.49,
      "discountAmountEur": 0.0,
      "quantity": 1.0,
      "quantityUnit": "KILOGRAM",
      "quantityAssumed": true,
      "unitPriceEur": 2.49,
      "unit": "KILOGRAM",
      "comparable": true
    }
  ]
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
  "date": "2026-02-13",
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
- every date on the wire, in both directions, is ISO-8601: a calendar date is
  `yyyy-MM-dd`, a moment is UTC like `2026-09-23T18:15:10Z`. Any other date
  string in a request is rejected with `400`. Display formats are the UI's job
- a stored purchase with no currency is a server error, never a guess

`searchQuery` matches a product's or a store's canonical name, ignoring case.
It is plain text: regex characters such as `(` or `.*` match themselves.

Product and store resolution, here and on `POST /api/receipts/submit`: a
submitted `productId` / `storeId` wins; the name is the fallback.

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
- max size: 25MB by default, configurable with `MAX_UPLOAD_MB`

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
      "date": "2026-02-13",
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
