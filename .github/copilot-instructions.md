# Copilot Instructions - Receipts API

This file provides context for GitHub Copilot to assist with development on this project.

---

## Project Overview

**Receipts API** is a Spring Boot REST API for tracking product prices from receipts. It provides user authentication, purchase management, product/store tracking, and related statistics.

- **Base Package**: `dev.vasoft.homeapp`
- **Java Version**: 25
- **Spring Boot Version**: 3.5.6
- **Database**: MongoDB
- **Build Tool**: Gradle

---

## Architecture

### Module Structure

The project follows a **modular monolith** pattern with three main bounded contexts:

```
src/main/java/dev/vasoft/homeapp/
├── ReceiptsApplication.java    # Main entry point
├── auth/                        # Authentication module
│   ├── api/controllers/        # AuthController, AuthControllerAdvice
│   ├── config/                 # SecurityConfiguration, JwtKeyProperties, filters/
│   └── services/               # TokenService, UserDetailsServiceImpl, CustomUserDetails
├── receipts/                    # Receipts/purchases module
│   ├── api/controllers/        # ProductController, PurchasesController, StoresController
│   ├── api/request/            # Request DTOs (ReqPurchase, etc.)
│   ├── api/response/           # Response DTOs (ResPurchase, ResProduct, ResPage, etc.)
│   ├── config/                 # WebConfig (CORS)
│   ├── model/entities/         # Purchase, Product, Store, Statistics
│   ├── model/repositories/     # MongoDB repositories
│   ├── services/               # PurchaseService, ProductService, StoreService, mappers/
│   └── scanning/               # Receipt scanning submodule (OCR + LLM pipeline)
│       ├── api/controllers/    # ReceiptScanController
│       ├── api/request/        # ReqSubmitPurchases
│       ├── api/response/       # ResScanResult, ResScanPurchase, ResScanStore
│       ├── config/             # OcrConfig, OcrProperties
│       └── services/           # ReceiptScanService, LlmReceiptParser, OcrService
└── users/                       # User management module
    ├── api/controllers/        # UserController
    ├── api/request/            # Request DTOs (ReqRegisterUser)
    ├── api/response/           # Response DTOs (ResRegisterUser)
    ├── exceptions/             # UserAlreadyExistsException
    ├── model/entities/         # User, VerificationToken
    ├── model/repositories/     # UserRepository, VerificationTokenRepository
    └── services/               # UserService, VerificationTokenService, event listeners
```

### Layer Pattern

Each module follows this layered architecture:
1. **API Layer** (`api/`) - Controllers, request/response DTOs
2. **Service Layer** (`services/`) - Business logic, mappers
3. **Model Layer** (`model/`) - Entities, repositories

---

## Key Technologies & Libraries

| Library | Purpose |
|---------|---------|
| Spring Boot Starter Web | REST API |
| Spring Boot Starter Data MongoDB | Database persistence |
| Spring Boot Starter Security | Authentication/authorization |
| Spring Boot Starter OAuth2 Resource Server | JWT token validation |
| Spring Boot Starter Mail | Email verification |
| Spring Boot Starter Validation | Request validation |
| Spring AI (OpenAI) | LLM-powered receipt scanning (vision + text parsing) |
| Tess4J | Tesseract OCR engine for receipt image text extraction |
| metadata-extractor | EXIF orientation reading for camera photo rotation |
| Bucket4j | Rate limiting |
| Caffeine | Caching |
| Lombok | Boilerplate reduction |
| spring-dotenv | Environment variable loading from `.env` |

---

## Authentication System

### JWT Token Flow
1. User authenticates via HTTP Basic Auth to `POST /api/auth/token`
2. Server generates JWT signed with RSA keys
3. JWT is sent as HTTP-only secure cookie
4. Subsequent requests use JWT from cookie for authorization

### Security Scopes
- `SCOPE_ACTIVE_USER` - Full API access for verified users
- `SCOPE_INACTIVE_USER` - Limited access for unverified users (can only resend verification)

### Key Configuration
- JWT keys loaded from `JwtKeyProperties` (classpath in dev, environment variables in prod)
- Token expiration: 1 hour (active users), 15 minutes (inactive users)
- Cookie: HTTP-only, Secure, SameSite=Strict

---

## API Endpoints

### Authentication (`/api/auth`)
| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/token` | Basic | Generate JWT token |
| GET | `/status` | JWT | Check auth status |

### Users (`/api/users`)
| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/register` | None | Register new user |
| POST | `/verify` | None | Verify email address |
| POST | `/resend-verification` | JWT (inactive) | Resend verification email |

### Purchases (`/api/purchases`)
| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/` | JWT | Get paginated purchases (user-scoped) |
| POST | `/` | JWT | Register new purchase (user-scoped) |

### Receipt Scanning (`/api/receipts`)

Uses a dual-path pipeline: images go through Tesseract OCR → LLM text parsing, while PDFs use direct LLM vision.

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/scan` | JWT | Upload receipt image/PDF for parsing |
| POST | `/submit` | JWT | Batch save parsed purchases |

### Products (`/api/products`)
| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/` | JWT | Get all products |
| POST | `/` | JWT | Register new product |

### Stores (`/api/stores`)
| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/` | JWT | Get all stores |

---

## Database Entities

### Core Entities
- **User** - `users` collection: id, email, password, isActive
- **VerificationToken** - Verification tokens with 24hr expiry
- **Product** - `products` collection: id, name
- **Store** - `stores` collection: id, name
- **Purchase** - `purchases` collection: id, userId, product (ref), price, currency (`BGN` | `EUR`), date, store (ref), discountAmount. `price` and `discountAmount` are in `currency` and never rewritten; see SPEC §9.5 in the receipts docs repo

### MongoDB Notes
- Uses `@DocumentReference` for entity relationships
- Auto-index creation enabled
- Purchases are scoped to users via `userId` field
- ObjectId used for document IDs

---

## Configuration

### Profiles
- **default** (`application.yaml`) - Production configuration, reads from environment variables
- **dev** (`application-dev.yaml`) - Development profile with debug logging, classpath JWT keys

### Environment Variables
See `example.env` for full list. Key variables:
- `MONGODB_*` - Database connection
- `SMTP_*` - Email server
- `JWT_PRIVATE_KEY`, `JWT_PUBLIC_KEY` - RSA keys (PEM content or file path)
- `SERVER_PORT` - Application port
- `APP_HOST_URL` - Application base URL (for email links)
- `OPENAI_API_KEY` - OpenAI API key for receipt scanning feature
- `TESSDATA_PATH` - Path to Tesseract tessdata directory (default: `/usr/share/tessdata` in prod, `C:/Program Files/Tesseract-OCR/tessdata` in dev)
- `OCR_LANGUAGE` - Tesseract language codes (default: `eng+bul`)
- `OCR_DEBUG_OUTPUT_PATH` - Directory for saving preprocessed debug images (empty = disabled, default: `./ocr-debug` in dev)

### Rate Limiting (Bucket4j)
- Registration: 3 attempts/hour per IP
- Login: 5 attempts/minute per IP
- Verification: 10 attempts/hour per IP
- Protected API: 100 requests/minute per user
- Receipt scanning: 10 scans/hour per user (due to LLM API costs)

---

## Development Guidelines

### Code Style
- Use Lombok annotations (`@Data`, `@AllArgsConstructor`, etc.) for entities and DTOs
- Use constructor injection with `@Autowired` on constructors
- Follow Spring conventions for component naming (Service, Controller, Repository)
- Use SLF4J for logging via `LoggerFactory.getLogger()`

### DTOs
- Request DTOs prefixed with `Req` (e.g., `ReqPurchase`, `ReqRegisterUser`)
- Response DTOs prefixed with `Res` (e.g., `ResPurchase`, `ResRegisterUser`)
- Use Java records for DTOs when appropriate
- DTOs carry data, never presentation: money is a number with its currency in
  the field name (`priceEur`), never a formatted string. Formatting is the UI's job
- Dates are ISO-8601 both ways (`yyyy-MM-dd`, or a UTC instant), set once by
  `spring.jackson.serialization.write-dates-as-timestamps: false`. Never add a
  per-field `@JsonFormat` date pattern
- Moments are `Instant`, never `LocalDateTime`. Calendar dates are stored as
  midnight UTC via `MongoConfig`, so nothing stored depends on the JVM's zone

### Mappers
- Place mapper classes in `services/mappers/` package
- Use static methods for entity-to-DTO conversion

### Exception Handling
- Create specific exception classes for domain errors
- Use `@ControllerAdvice` for global exception handling
- Return appropriate HTTP status codes

### Testing
- Use Spring Boot Test with `@SpringBootTest`
- Mock dependencies with Mockito

---

## Build & Deployment

### Gradle Tasks
```bash
# Build
./gradlew build

# Run locally (requires .env file)
./gradlew bootRun

# Build Docker image
./gradlew buildImage -PrepositoryName=<owner>/<repo>

# Push Docker image
./gradlew publishImage -PrepositoryName=<owner>/<repo>
```

### Docker
- Base image: `eclipse-temurin:25-jre-alpine`
- Includes Tesseract OCR with English + Bulgarian language data
- Compose file includes MongoDB and API containers
- JWT keys passed as environment variables

### Local Development (Windows)
1. Copy `example.env` to `.env` and fill values
2. Generate RSA keys in `src/main/resources/certs/` (see README.md)
3. Install [Tesseract OCR](https://github.com/UB-Mannheim/tesseract/wiki) with English + Bulgarian language packs
4. Run with `dev` profile: `./gradlew bootRun --args='--spring.profiles.active=dev'`

---

## Roadmap Reference

See `docs/ROADMAP.md` for planned features including:
- Account lockout mechanism
- Password reset functionality
- Refresh token implementation
- Role-based access control (RBAC)
- Multi-factor authentication
- Microservices restructure
- Additional services (Tools & Appliances, Outfit Wear)

---

## File Naming Conventions

| Type | Pattern | Example |
|------|---------|---------|
| Controller | `*Controller.java` | `ProductController.java` |
| Service | `*Service.java` | `PurchaseService.java` |
| Repository | `*Repository.java` | `PurchasesRepository.java` |
| Entity | PascalCase noun | `Purchase.java` |
| Request DTO | `Req*.java` | `ReqPurchase.java` |
| Response DTO | `Res*.java` | `ResPurchase.java` |
| Exception | `*Exception.java` | `UserAlreadyExistsException.java` |
| Mapper | `*Mapper.java` | `PurchaseMapper.java` |
| Configuration | `*Config.java` or `*Configuration.java` | `SecurityConfiguration.java` |

---

## Shell Environment

- **Shell**: ZSH (not PowerShell)
- When generating terminal commands, use Unix/ZSH syntax
- Do not use PowerShell-specific commands or syntax
- Use `;` or `&&` for command chaining
- Use Unix path separators (`/`)

---

## Receipt Scanning Pipeline

The receipt scanning module (`receipts/scanning/`) uses a dual-path architecture:

### Image Path (JPEG, PNG, WebP, GIF)
1. **EXIF orientation** — Reads EXIF tag via metadata-extractor, applies rotation/flip (fixes sideways phone photos)
2. **Screenshot detection** — PNGs skip preprocessing (already pixel-perfect digital text)
3. **Photo preprocessing** — Grayscale conversion, optional upscaling/sharpening/Otsu binarization for camera photos
4. **Tesseract OCR** — Extracts text (LSTM engine, PSM 6, 300 DPI hint, `eng+bul` languages)
5. **LLM text parsing** — OCR text sent to GPT via text prompt → structured `ParsedReceipt`

### PDF Path
1. **Direct LLM vision** — PDF sent as media attachment to GPT vision prompt → structured `ParsedReceipt`

### Debug Support
- Set `app.ocr.debug-output-path` to save preprocessed images for inspection
- Dev profile defaults to `./ocr-debug/` (gitignored)
- Files named `{original}_{timestamp}_preprocessed.png`

### Key Classes
- `OcrService` — EXIF handling, preprocessing pipeline, Tesseract integration
- `LlmReceiptParser` — Dual-mode prompts (vision vs text), structured output via `ChatClient.entity()`
- `ReceiptScanService` — Routing hub (image vs PDF), file validation, response mapping
- `OcrConfig` / `OcrProperties` — Tesseract bean configuration and Spring properties

---

## Important Notes

1. **Security**: Never log passwords or JWT tokens. Use `PasswordEncoder` for password hashing.
2. **Validation**: Add `@Valid` and validation annotations to request DTOs.
3. **Stateless**: The API is stateless - no server-side sessions.
4. **CORS**: Configured in `WebConfig` class.
5. **Events**: Uses Spring's `ApplicationEventPublisher` for email verification flow.