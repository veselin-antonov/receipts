# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)

## [Unreleased]

### Added

- `src/lib/format.js`: `formatEur` and `formatDate`. The API now sends numbers
  and ISO dates, so the UI owns rounding and localisation

### Changed

- The purchases table renders `priceEur` / `discountAmountEur` and ISO dates
  from the API's new wire format (requires the matching API change)
- Price inputs in the manual form and the scan review are labelled `(€)`

### Fixed

- The discount icon read `purchase.discount`, which the API never sent, so it
  was always off; it now reads `discountAmountEur`

## [0.0.6] - 03.01.2026

### Added

- Docker-based build with multi-stage Node builder and nginx runtime serving the Vite `dist` output
- Nginx config template with SPA routing, `/api` proxying, and runtime `BACKEND_HOST` substitution
- `.dockerignore` to slim Docker build contexts

### Changed

- Release workflow now builds and pushes Docker images to GHCR with `{version}` and `latest` tags
- GitHub releases now point to container images (tarball artifact removed)

## [0.0.5] - 31.12.2025

### Added

- Tailwind CSS v4 upgrade with modernized styling
- Enhanced form components with improved validation and error handling
- New UI components (Separator, Field, Form) for better composition
- Custom hooks for resource fetching and pagination (`useFetchResource`, `usePaginatedResource`)
- API error handling with proper status code management
- Improved pagination logic and pagination selector
- Page loader component with loading state indicator

### Changed

- Migrated from Tailwind v3 to v4 with new theming system
- Updated color scheme to use OKLch color space
- Refactored component structure with data-slot attributes
- Enhanced input and label components with better accessibility
- Improved table component with better responsiveness
- Updated pagination component with lucide-react icons
- Code formatting and import organization improvements

### Fixed

- Infinite auth status fetching issue
- Missing rate limiting error handling
- Component accessibility and semantic HTML improvements

## [0.0.4] - 18.09.2024

### Fixed

- Missing site icon

## [0.0.3] - 18.09.2024

### Fixed

- Added missing API url configuration to the workflow build step

## [0.0.2] - 18.09.2024

### Added

- GitHub workflow for automatic deployment of the site to the server

### Fixed

- Bumped all dependencies to latest versions

## [0.0.1] - 17.09.2024

- Initial version of the project
