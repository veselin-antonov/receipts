# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)



## [Unreleased]

### Added
- `currency` (`BGN` | `EUR`) stored on every purchase; optional on requests,
  defaulting to `EUR`
- `LegacyCurrencyBackfill`: at startup, tags currency-less purchases `BGN`, and
  fails startup rather than guess for one dated 2026-01-01 or later

### Changed
- **Breaking:** purchase responses carry numeric `priceEur` and
  `discountAmountEur` (full precision, converted at 1 EUR = 1.95583 BGN)
  instead of the formatted `price` string and `discountAmount`
- **Breaking:** every date on the wire is ISO-8601 in both directions. Purchase
  requests (`POST /api/purchases`, `/api/receipts/submit`) now take
  `yyyy-MM-dd` and reject `dd/MM/yyyy` with 400. One global Jackson setting
  replaces the per-DTO `@JsonFormat` patterns
- `POST /api/purchases` resolves product and store the same way as submit: a
  submitted id wins, the name is the fallback
- Verification token expiry is an `Instant`, not a zoneless `LocalDateTime`

### Fixed
- `POST /api/purchases` with ids and no names created a product and a store
  with empty names
- Legacy BGN prices were served as euros, ~2x overstated, after the JDK's
  bg-BG locale switched to EUR (D11)

### Removed
- `Formatter`; formatting belongs to the UI (D2)
- `ResParsedPurchase` and `PurchaseMapper.toResParsedPurchases`, unused

## [0.0.6] - 19.01.2026

### Added
- New GitHub Actions workflows for CI/CD:
  - `build-image.yml` reusable workflow for building Docker images
  - `dev.yml` workflow for development environment
  - `release.yml` workflow for production releases

### Changed
- Replaced `docker-image.yml` workflow with new modular CI/CD workflows

### Removed
- SSL configuration from `application.yaml` (SSL termination now handled externally)
- SSL-related environment variables from `example.env`

## [0.0.5] - 30.12.2024

### Added
- Authentication system with JWT tokens and RSA key signing
- HTTP-only secure cookies with SameSite protection
- User registration and login endpoints
- Email verification with 24-hour expiry tokens
- Event-driven email sending on registration
- Rate limiting on public and protected endpoints
- `application-dev.yaml` for development environment
- `ROADMAP.md` consolidating all project tasks

### Changed
- Replaced `openjdk:21-jdk-slim` with `eclipse-temurin:21-jre-alpine` in Dockerfile
- Moved application entry point to root `homeapp` package
- Refactored business classes to service layer pattern
- Updated `settings.gradle` with new project name
- Extended `application.yaml` with security, mail, and rate limiting config
- Added new environment variables to `example.env`

### Fixed
- GitHub Actions workflow failing due to missing repository read permissions

## [0.0.4] - 17.09.2024

### Added
- VERSION file
  - All mentions of the project version will now look for it in this file in order to avoid mismatches in the future.
- Github Actions
  - Setup a github workflow to automatically build and publish a docker image with every version
- New build.gradle tasks
  - buildImage and publishImage tasks were added to be used by the GitHub Actions workflow

### Fixed
-  CORS configuration was only setup for root path. Now works for every endpoint

## [0.0.3] - 15.08.2024

### Added

- This changelog, in hopes to make me stricter.
- Dockerfile, so the app can be deployed as a container.
- doeker-compose.yml file for app deployment.
- WebConfiguration class for CORS configuration

### Changed

- application.properties is now application.yml, because I like it better.
- Restructured example.env file, added comments and new values.
- Modified package structure to reflect the new domain name