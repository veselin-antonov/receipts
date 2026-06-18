# Home App - Project Roadmap

> **Last Updated**: December 30, 2025

This roadmap consolidates all planned features, improvements, and tasks for the Home App project. Items are organized by priority and category to ensure systematic development progress.

---

## Table of Contents

- [Vision & Architecture](#vision--architecture)
- [Phase 1: Security & Foundation](#phase-1-security--foundation)
- [Phase 2: Core Features](#phase-2-core-features)
- [Phase 3: Quality & Validation](#phase-3-quality--validation)
- [Phase 4: Advanced Features](#phase-4-advanced-features)
- [Services Overview](#services-overview)
- [Infrastructure & DevOps](#infrastructure--devops)
- [Completed Items ✅](#completed-items-)

---

## Vision & Architecture

### Microservices Restructure
- [ ] Redesign the app structure into separate microservices:
  - Receipts Service
  - Users Service
  - Home Products Service
  - Tools & Appliances Service
  - Outfit Wear Service

### UI Architecture
- [ ] Split UI into two parts:
  - Main Web Application
  - Mobile Companion App
- [ ] Implement Google-like 'Apps' menu to access all services
- [ ] Different accent colors for each service/app
- [ ] Dark/Light mode toggle with System option (persist via cookie)

### Database Strategy
- [ ] Research MongoDB architecture patterns
- [ ] Decide on database separation strategy:
  - Separate instances in separate containers
  - Single instance with different schemas

### Repository Organization
- [ ] Create releases for UI repository
- [ ] Remake UI repo for "Home App" to aggregate all services
- [ ] Create a project board to aggregate tasks from all repos

---

## Phase 1: Security & Foundation

*Timeline: Week 1-2*

### 🔴 High Priority - Security Critical

#### Account Protection
- [ ] Implement account lockout mechanism after failed login attempts
  - Add fields to User entity: `failedLoginAttempts`, `lockedUntil`, `lastFailedLogin`
  - Lock account after 5 failed attempts for 15 minutes
  - Add unlock mechanism and admin override
  - Send email notification on account lockout

#### Password Security Enhancement
- [ ] Add password validation annotations to `ReqRegisterUser`
- [ ] Implement password complexity requirements (min 8 chars, uppercase, lowercase, number, special char)
- [ ] Add password confirmation field in registration
- [ ] Implement password history to prevent reuse

#### Token Security Improvements
- [ ] Implement token refresh mechanism with RefreshToken entity (30-day expiry)
- [ ] Add token blacklisting/revocation capability for logout functionality
- [ ] Make JWT configuration externalized (token expiration, cookie settings)
- [ ] Implement key rotation strategy for JWT signing keys

#### Security Headers & Protection
- [ ] Add CSRF protection for state-changing operations
- [ ] Add security headers (X-Frame-Options, X-Content-Type-Options, etc.)
- [ ] Configure proper CORS settings for authentication endpoints

### 🔴 High Priority - Code Quality

#### Error Handling & Validation
- [ ] Implement consistent error responses with standardized error codes
- [ ] Add logging for security events (failed logins, token generation, etc.)
- [ ] Improve exception handling in `TokenService.generateToken()` method
- [ ] Add validation for JWT token format and claims
- [ ] Implement proper handling of expired tokens
- [ ] Add validation annotations to all request DTOs
- [ ] Add input sanitization for email and user input fields

---

## Phase 2: Core Features

*Timeline: Week 2-3*

### 🟡 Medium Priority - Authentication Features

#### Refresh Token Implementation
*(Builds on Token Security Improvements from Phase 1)*
- [ ] Modify TokenService to generate refresh tokens
- [ ] Add `POST /auth/refresh` endpoint in AuthController
- [ ] Implement refresh token rotation for security
- [ ] Add refresh token cleanup logic

#### Forgot Password Functionality
- [ ] Create PasswordResetToken entity (similar to VerificationToken)
- [ ] Create PasswordResetTokenService for token management
- [ ] Add `POST /auth/forgot-password` endpoint
- [ ] Add `POST /auth/reset-password` endpoint
- [ ] Implement email template for password reset
- [ ] Add password reset link expiration (1 hour)

#### Additional Auth Endpoints
- [ ] Add logout endpoint with proper token invalidation
- [ ] Create user profile management endpoints
- [ ] Add endpoint for changing passwords
- [ ] Implement "remember me" functionality
- [ ] Add token introspection endpoint

### 🟡 Medium Priority - Architecture Improvements

#### Access Control
- [ ] Implement role-based access control (RBAC) with user roles and permissions management

#### Code Structure
- [ ] Implement authentication events and listeners for audit logging
- [ ] Add configuration properties for JWT settings (expiration time, issuer, etc.)
- [ ] Separate authentication logic from web layer (create AuthService)
- [ ] Implement proper dependency injection patterns throughout auth package

---

## Phase 3: Quality & Validation

*Timeline: Week 3-4*

### Token Cleanup Automation
- [ ] Enable scheduling with `@EnableScheduling`
- [ ] Create TokenCleanupService with `@Scheduled` methods
- [ ] Clean expired tokens (VerificationTokens, PasswordResetTokens, RefreshTokens) daily
- [ ] Add logging for cleanup operations

### 🟢 Testing & Quality Assurance
- [ ] Add unit tests for `AuthController` endpoints
- [ ] Add unit tests for `TokenService` methods
- [ ] Add unit tests for `UserDetailsServiceImpl`
- [ ] Add integration tests for authentication flow
- [ ] Add security tests for JWT token validation
- [ ] Add performance tests for authentication endpoints
- [ ] Implement test data builders for authentication entities

### 🟢 Documentation
- [ ] Add comprehensive JavaDoc comments to all auth classes
- [ ] Create API documentation for authentication endpoints
- [ ] Document security configuration and JWT flow
- [ ] Add code examples for common authentication scenarios
- [ ] Create troubleshooting guide for authentication issues
- [ ] Document deployment and configuration requirements

---

## Phase 4: Advanced Features

*Timeline: Week 4+*

### Audit & Monitoring
- [ ] Create AuditLog entity for security events
- [ ] Log authentication events (successful/failed logins, lockouts, password changes) with IP, timestamp, user agent
- [ ] Add audit log viewing endpoint for admins
- [ ] Add metrics collection for authentication operations

### Performance Optimization
- [ ] Implement caching for user details lookup
- [ ] Optimize database queries in `UserDetailsServiceImpl`
- [ ] Add performance logging for token generation and validation
- [ ] Implement connection pooling for database operations

### Advanced Authentication
- [ ] Implement OAuth2/OpenID Connect with external providers (Google, GitHub, etc.)
- [ ] Implement single sign-on (SSO) capabilities
- [ ] Add support for API key authentication
- [ ] Implement device-based authentication with tracking and notification
- [ ] Add passwordless authentication methods
- [ ] Add multi-factor authentication (MFA) support
- [ ] Add CAPTCHA for repeated failed attempts

---

## Services Overview

### Receipts Service

#### Purchase Management
- [ ] Prefill last store when submitting new purchase (if there is a registered purchase today)
- [ ] Prefill last purchase date (today if none registered)
- [ ] Add toaster notifications (using shadcn) for purchase registration success/error

#### Receipt Scanning
- [x] Implement OCR preprocessing for receipt images (Tesseract via Tess4J)
- [x] EXIF orientation handling for camera photos
- [x] Dual-path pipeline: OCR→LLM for images, direct LLM vision for PDFs
- [x] Debug image saving for OCR preprocessing inspection
- [ ] Implement product recognition upon scanning
- [ ] Handle conflicts via user prompts when products aren't recognized
- [ ] Prompt for missing products with AI-suggested photos
- [ ] Handle discounts - allow marking multiple products for common discount application

#### UI Improvements
- [ ] Read auth status from local storage instead of calling API for every request

### Tools & Appliances Service

#### Information Management
- [ ] Store and display manual PDFs
- [ ] Track product information (model number, serial number, links, etc.)

#### Sharing Features
- [ ] Implement ability to share appliance info with other people

### Outfit Wear Service (Mobile Companion Only)

#### Wardrobe Tracking
- [ ] Snap outfit photos when going out
- [ ] Track wear count for each clothing item
- [ ] Image recognition to build up wardrobe
- [ ] User-configurable wear counts for different clothing types

---

## Infrastructure & DevOps

### Certificates & SSL
- [ ] Better structure configurations for prod and development
- [ ] SSL configuration management
- [ ] SMTP configuration management

### Configuration Updates
- [ ] Email templates for password reset
- [ ] Logging configuration for audit events
- [ ] Cron expressions for cleanup jobs
- [ ] Add health check endpoints for authentication services

---

## Completed Items ✅

### Authentication Features
- [x] Email/Password Auth with JWT
  - JWT token generation with RSA signing
  - HTTP-only secure cookies
  - Different token scopes for active/inactive users
  - Token expiration handling (1hr active, 15min inactive)

- [x] Email Verification
  - VerificationToken entity with 24hr expiry
  - Email sending service with JavaMailSender
  - Verification endpoint (/verify)
  - Resend verification endpoint
  - Proper error handling

- [x] Rate Limiting
  - IP-based rate limiting on public endpoints
  - User-based rate limiting on protected endpoints
  - Auth endpoint: 5 attempts/minute per IP
  - Registration: 3 attempts/hour per IP
  - Verification: 10 attempts/hour per IP
  - Protected API: 100 requests/minute per user
  - Different limits for inactive users
  - Custom error messages for rate limit violations

### Security Configuration
- [x] Environment Security - All sensitive configurations properly externalized
- [x] HTTPS Configuration - SSL/TLS properly configured
- [x] JWT Security - RSA signing used instead of HMAC
- [x] Cookie Security - HTTP-only, secure, SameSite flags set
- [x] Database Security - MongoDB authentication configured
- [x] PBKDF2 password encoding implemented
- [x] Stateless session management for REST APIs

---

## Implementation Guidelines

### Code Standards
- Follow consistent naming conventions throughout all packages
- Use proper exception handling with specific exception types
- Implement proper logging at appropriate levels (DEBUG, INFO, WARN, ERROR)
- Follow Spring Security best practices and conventions
- Use dependency injection consistently across all components

### Security Best Practices
- Never log sensitive information (passwords, tokens)
- Use secure random generators for token generation
- Implement proper input sanitization and validation
- Follow OWASP security guidelines for authentication
- Regular security audits and dependency updates

### Testing Strategy
- Unit tests should cover all business logic
- Integration tests should verify end-to-end flows
- Security tests should validate authentication and authorization
- Performance tests should ensure scalability requirements are met

---


## Notes

- Tasks should be completed in order of priority
- Each completed task should be checked off and reviewed before moving to the next priority level
- **Estimated Total Development Time**: 3-4 weeks for full implementation
- **Current Assessment Score**: 7/10 - Good foundation with room for important security and functionality improvements

---

*This roadmap is a living document and should be updated as features are completed and new requirements emerge.*