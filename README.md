# Receipts App

This app's purpose is to register product prices from receipts in order to look at statistics and manage entries with an interactive UI.

This repository contains the business logic for the app and can be accessed via REST API and is made using Spring Boot.

## Configuration

### JWT Signing Keys

The application requires an RSA key pair for JWT token signing and verification.

#### Local Development

1. Generate a key pair:
   ```bash
   # Generate private key
   openssl genrsa -out src/main/resources/certs/private.pem 2048
   # Extract public key
   openssl rsa -in src/main/resources/certs/private.pem -pubout -out src/main/resources/certs/public.pem
   ```

2. Run with `dev` profile - keys will be loaded from classpath automatically.

#### Production Deployment

Pass the keys as environment variables. The keys can be provided as:

1. **PEM content directly** (recommended for containers/Kubernetes):
   ```bash
   JWT_PRIVATE_KEY="-----BEGIN PRIVATE KEY-----
   MIIEvgIBADANBgkqhki...
   -----END PRIVATE KEY-----"
   
   JWT_PUBLIC_KEY="-----BEGIN PUBLIC KEY-----
   MIIBIjANBgkqhki...
   -----END PUBLIC KEY-----"
   ```

2. **File path** (for VM deployments):
   ```bash
   JWT_PRIVATE_KEY=file:/etc/secrets/private.pem
   JWT_PUBLIC_KEY=file:/etc/secrets/public.pem
   ```

**Docker/Kubernetes**: Mount secrets as environment variables or use a secrets manager. For Docker Compose:
```yaml
services:
  api:
    environment:
      - JWT_PRIVATE_KEY=${JWT_PRIVATE_KEY}
      - JWT_PUBLIC_KEY=${JWT_PUBLIC_KEY}
```

**Important**: Never commit keys to version control. Use secrets management (Docker secrets, Kubernetes secrets, HashiCorp Vault, etc.) in production.