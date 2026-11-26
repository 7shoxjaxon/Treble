# Treble

Treble is a football match organization platform designed to help players discover local games, reserve places, and communicate with other participants. It brings match schedules, venue information, bookings and organizer tools into one application. The interface is primarily in Uzbek.

## 📲 Download Treble — Beta Access

Treble is currently in a 14-day closed beta testing phase on Google Play. The app is not yet available for general public installation.

[![Join the tester group](https://img.shields.io/badge/Step_1-Join_Tester_Group-4285F4?style=for-the-badge&logo=google&logoColor=white)](https://groups.google.com/g/treble_tester)
[![Join the Google Play beta](https://img.shields.io/badge/Step_2-Google_Play_Beta-34A853?style=for-the-badge&logo=googleplay&logoColor=white)](https://play.google.com/apps/testing/com.shoxjaxon.treble)

### How to install

1. Join the [Treble Testers Google Group](https://groups.google.com/g/treble_tester).
2. Open the [Google Play beta testing link](https://play.google.com/apps/testing/com.shoxjaxon.treble) and opt in to the test.
3. Follow the Google Play instructions to download and install Treble on your Android device.
4. Explore the app and share your feedback.

Use the same Google account for joining the group, opting in and installing the app. Access is subject to the closed testing requirements.

Your feedback helps improve Treble before its public release.

**Find. Play. Connect.** ⚽

## Features in the source code

- Browse matches and venues; reserve or cancel a place and view personal bookings.
- User registration, profile management and phone verification.
- Group chat, direct messages, comments and notifications.
- Organizer and staff tools for match management, attendance and payment records.
- Coupon and player rating features.
- Android notifications and reminders, with optional Firebase Cloud Messaging.
- Telegram Mini App interface and optional Telegram/SMS verification integrations.

## Project structure

| Path | Purpose |
| --- | --- |
| `app/` | Android application (Kotlin and Jetpack Compose) |
| `backend/server.js` | HTTP API, authentication, match logic and SQLite storage |
| `backend/miniapp/` | Telegram Mini App (HTML, CSS and JavaScript) |
| `backend/pages/` | Legal page templates |
| `gradle/` | Gradle wrapper and build configuration |

## Local development

1. Install Node.js 22.13 or later (Node.js 22 LTS recommended) and Android Studio with Android SDK 36. The backend uses built-in `node:sqlite` and has no third-party npm dependencies.
2. Open `backend/` and copy `.env.example` to `.env`. Leave `DEV_MODE=true` and `HOST=127.0.0.1` for local testing. Optionally set a unique administrator login and strong password (12–64 characters) before the first launch.
3. Run `node server.js`. Visit `http://127.0.0.1:3000/health` to check the backend. Verification codes in development mode are printed to the local server console; use test data only.
4. Open the repository root in Android Studio, sync Gradle, and run the debug build on an Android emulator. The default API address is `http://10.0.2.2:3000`. Release builds require a separately configured HTTPS endpoint.
5. To enable Firebase, supply your own `app/google-services.json` locally and a separate private server-side service account file. To enable Telegram Mini App sharing, set your own bot username in `backend/miniapp/index.html` and configure the backend environment.

See [backend setup](backend/README.md) for more information.

## Project status

This repository is an initial public-source snapshot. The Android app is currently in closed beta testing on Google Play. No download counts, active-user figures, or external adoption metrics are claimed here. The original development and hosting configuration is not included. The Android build and external integrations need validation in the developer's own environment.

## Security and privacy

Never commit real credentials, signing keys, user databases, uploads or private deployment settings. Use a dedicated local database for development. Do not expose the development server to the internet. For deployment, perform an independent security review, configure HTTPS, a strong OTP secret, production verification providers, backups and access controls. The legal-page templates require review before operating a service.

## License

No open-source license has been selected in this snapshot. Public visibility does not itself grant permission to reuse the code. The project owner should select a license after confirming rights to all included assets and code.
[Official website — treble.uz](https://treble.uz)

## Roadmap

- [ ] Improve user interface
- [ ] Add match notifications
- [ ] Improve match discovery
- [ ] Add player ratings
- [ ] Official Google Play release