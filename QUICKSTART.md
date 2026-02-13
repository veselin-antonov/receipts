# Quick Reference Card

## Project Setup (30 seconds)
```bash
# 1. Copy environment template
cp example.env .env

# 2. Edit .env - add OpenAI key
nano .env
# Add: OPENAI_API_KEY=sk-...

# 3. Build and run
./gradlew clean build
./gradlew bootRun
```

## Key Files Structure
```
receipts-api/
├── src/main/java/dev/vasoft/homeapp/
│   ├── auth/                 # Authentication
│   ├── receipts/
│   │   ├── model/            # Entities, repositories
│   │   ├── services/         # Business logic
│   │   ├── api/              # Controllers, DTOs
│   │   └── scanning/         # NEW: LLM receipt scanning
│   │       ├── api/
│   │       ├── services/
│   │       └── config/
│   └── users/                # User management
├── resources/
│   ├── application.yaml      # Production config
│   └── application-dev.yaml  # Dev config
└── build.gradle              # Dependencies
```

## API Quick Reference

### Authentication
```bash
# Register
POST /api/users/register
{ "email": "user@example.com", "password": "pass123" }

# Verify
POST /api/users/verify
{ "email": "user@example.com", "token": "verify-token" }

# Get JWT
POST /api/auth/token
Authorization: Basic user@example.com:pass123
```

### Receipt Scanning (NEW)
```bash
# Scan receipt
POST /api/receipts/scan
Authorization: Bearer <jwt>
Content-Type: multipart/form-data
file=@receipt.jpg

# Submit parsed purchases
POST /api/receipts/submit
Authorization: Bearer <jwt>
{
  "purchases": [
    {
      "product": "Milk",
      "store": "Store Name",
      "price": 3.99,
      "date": "13/02/2026",
      "discount": false
    }
  ]
}
```

### Purchases
```bash
# Get purchases (paginated)
GET /api/purchases?pageNumber=0&pageSize=10&searchQuery=milk
Authorization: Bearer <jwt>

# Add purchase
POST /api/purchases
Authorization: Bearer <jwt>
{
  "product": "Milk",
  "store": "Store Name",
  "price": 3.99,
  "date": "13/02/2026",
  "discount": false
}
```

## Environment Variables
```
# Required
MONGODB_HOST=localhost
MONGODB_PORT=27017
MONGODB_DATABASE=receipts
MONGODB_USER=user
MONGODB_PASS=password
MONGODB_AUTH_DB=admin

SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USER=email@gmail.com
SMTP_PASS=app-password

SERVER_PORT=8080
APP_HOST_URL=http://localhost:8080

JWT_PRIVATE_KEY=file:./src/main/resources/certs/private.pem
JWT_PUBLIC_KEY=file:./src/main/resources/certs/public.pem

# NEW: Required for receipt scanning
OPENAI_API_KEY=sk-...
```

## Useful Commands
```bash
# Build
./gradlew clean build

# Run (production)
./gradlew bootRun

# Run (dev - debug logging)
./gradlew bootRun --args='--spring.profiles.active=dev'

# Run tests
./gradlew test

# Check dependencies
./gradlew dependencies

# Generate gradle wrapper
./gradlew wrapper --gradle-version=9.3.1

# Format code
./gradlew spotlessApply

# Build Docker image
./gradlew buildImage -PrepositoryName=owner/repo
```

## Key Technologies
- **Java**: 25
- **Spring Boot**: 3.5.6
- **Spring AI**: 1.0.6 (OpenAI GPT-4o-mini)
- **MongoDB**: Document database
- **Gradle**: 9.3.1 Build tool
- **JWT**: Authentication tokens
- **Bucket4j**: Rate limiting

## Rate Limits
- **Receipt Scan**: 10/hour per user
- **Protected API**: 100/minute per user
- **Auth Token**: 5/minute per IP
- **Registration**: 3/hour per IP

## Supported Receipt Formats
✅ JPEG/JPG  
✅ PNG  
✅ GIF  
✅ WebP  
✅ PDF  
Max size: 10MB

## Default Port
```
http://localhost:8080
```

## Debug Logging
Enable with dev profile:
```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```

Logs are printed to console with:
- Spring Security: TRACE
- MongoDB: DEBUG
- Spring AI: DEBUG
- Bucket4j: DEBUG
- Receipt Scanning: DEBUG

## Common Issues & Fixes

| Problem | Solution |
|---------|----------|
| "Cannot resolve ChatClient" (IDE) | `./gradlew clean build` + restart IDE |
| "OPENAI_API_KEY not set" | Add to `.env` file |
| "Port 8080 already in use" | Change `SERVER_PORT` in `.env` |
| "MongoDB connection failed" | Check MongoDB is running on configured host/port |
| "Receipt parsing fails" | Use clear, well-lit receipt image |

## Implementation Highlights
✨ User data isolation - all purchases scoped to user  
✨ LLM-powered receipt parsing - gpt-4o-mini  
✨ Structured JSON output - guaranteed data consistency  
✨ Rate limiting - prevents API abuse  
✨ Comprehensive error handling - meaningful error messages  
✨ Full JWT authentication - secure token-based API  
✨ MongoDB integration - scalable document storage  

## Contact & Support
Check `docs/SPRING_AI_SETUP.md` for detailed setup instructions.
Check `docs/IMPLEMENTATION_COMPLETE.md` for full implementation details.