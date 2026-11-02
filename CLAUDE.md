# Working in this repository

This repository is public. Everything written here, in commits, pull request
descriptions and comments, is public.

- Describe code and generic, reusable usage only. Never write anything specific
  to a particular machine or deployment: home-directory paths, `.env` values or
  which credentials and accounts exist, IP addresses, reverse-proxy host IDs,
  data-migration or switch-over commands for a real server, or counts and other
  details of real user data. Steps like those go to the person you are working
  with directly, not into the repository or a pull request.
- Docs describe how things are now. History belongs in `CHANGELOG.md`, the ADRs
  and git; see README.md, "Documentation ownership".
- `master` is protected: every change goes through a pull request.
- Development setup is in `docs/DEV_SETUP.md` (`make setup`, `make dev`,
  `make test`); deployment is in `deploy/README.md`.
