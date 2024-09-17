# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)

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