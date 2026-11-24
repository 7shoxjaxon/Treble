# Treble backend

The Node.js HTTP server uses SQLite for accounts, bookings, matches, chat, coupons and notifications. Use Node.js 22.13+; no npm dependencies are required.

## Local setup

Copy `.env.example` to `.env` (`copy .env.example .env` on Windows Command Prompt, `cp .env.example .env` on Linux/macOS). Configure optional `ADMIN_LOGIN`, `ADMIN_NAME` and `ADMIN_PASSWORD` locally. No default administrator is shipped. Bootstrap creates an account only if the configured login is absent, and does not overwrite existing accounts. Change the password after initial setup and remove the bootstrap credentials from the local environment once no longer needed.

Run `node server.js` and check `http://127.0.0.1:3000/health`. Development mode prints verification codes to the console and is restricted to loopback. Use synthetic test data. SQLite data and uploaded images are stored under `data/` by default; keep them private.

## Optional integrations

- `TELEGRAM_GATEWAY_TOKEN` or `TELEGRAM_BOT_TOKEN` for Telegram verification; `MINIAPP_URL` and your own bot configuration for the Mini App.
- `ESKIZ_EMAIL`, `ESKIZ_PASSWORD`, `ESKIZ_FROM` and `SMS_TEMPLATE` for SMS delivery.
- `FIREBASE_SERVICE_ACCOUNT` points to a private service account JSON file for push delivery. Without it, backend push delivery is disabled.

For non-development mode set `DEV_MODE=false`, `OTP_REQUIRED=true` and a unique random `OTP_SECRET` of at least 32 characters. Generate it locally with `node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"`. Never commit it. Configure a verification provider before deployment. Keep the bind address local behind a correctly configured HTTPS reverse proxy unless your deployment requires another address. Review proxy trust, access controls, logging, backups and retention before publishing a live service.

This public-source snapshot removes the original hard-coded bootstrap credentials and legacy account migration, private deployment files, Firebase project configuration and personal test account script. It is intended for a fresh development database, not an in-place production upgrade.
