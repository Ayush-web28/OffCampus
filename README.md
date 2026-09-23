# OffCampus

A last-mile ride-share Android app for college students. Riders heading the same way from a
station or gate at a similar time form a **lobby**, coordinate in an in-app chat, book the actual
ride through Uber / Ola / Rapido, and split the fare with a two-way payment acknowledgement.

There is no payment gateway, no live GPS and no in-house driver network. Booking hands off to the
ride apps through deep links, and money is settled between riders outside the app. The app only
records who owes what and who has confirmed it.

## Features

- **Sign in** with a college email and password, or with a passwordless **email link** (magic link).
- **Profile** with a preset animal avatar or your own uploaded photo (stored by a Cloudflare
  Worker, see `worker/`), plus a star rating that is
  the average of what other riders gave you.
- **Lobbies**: post a trip, browse, filter (ride type, friends only) and sort, join, leave and
  lock. Auto lobbies cap at 3 riders and Cab lobbies at 6. Everyone in a lobby is shown by name
  and avatar before you join.
- **Lobby master controls**: only the rider who created a lobby can lock it, open the
  Uber / Ola / Rapido links, and end the ride by posting the fare split.
- **Friends**: search by name or email prefix, send, accept or decline requests, a badge for
  pending requests, and 1:1 friend chat.
- **Chat**: group chat per lobby and persistent chat between friends.
- **Payment splitting**: equal or custom per-rider amounts, with a two-way acknowledgement
  (rider marks "sent", payer confirms "received") and a trip history.
- **Ratings and reports**: rate a fellow rider from 1 to 5 stars after a trip, or report a
  payment dispute (no-show, didn't pay, inappropriate behavior, other).
- **In-app notifications** (Snackbars) when a friend posts a lobby, tappable to open it, and when
  you owe money.

## Tech stack

- Kotlin, Jetpack Compose (Material 3), no XML layouts
- MVVM: `ViewModel` + `StateFlow` feeding composables, no business logic in composables
- Firebase Authentication, Cloud Firestore (live snapshot listeners), Firebase Hosting
- Cloudflare Worker + Workers KV for profile photos (`worker/`), image loading with Coil
- Firestore security rules with per-collection ownership rules (`firestore.rules`)
- `minSdk 24`, `compileSdk` / `targetSdk` 34

Design system ("streetlight & meter"): violet / amber / green / coral accents on quiet neutral
surfaces, Bricolage Grotesque for headlines, Manrope for body text, JetBrains Mono for numbers.
The full spec is in [CLAUDE.md](CLAUDE.md).

## Project structure

```
app/src/main/java/com/offcampus/app/
  data/            Firestore refs and models (Rider, Lobby, Rating, ...)
  ui/auth/         Sign in, sign up and email-link sign-in
  ui/avatar/       AvatarView, the avatar catalog, photo compression
  ui/profile/      Profile and the avatar / photo picker
  ui/lobby/        Browse, post trip, lobby detail, ride booking links
  ui/friends/      Friend search, requests and list
  ui/chat/         Lobby chat and friend chat
  ui/payment/      Post fare, payment split, trip history
  ui/rating/       Rate a rider
  ui/report/       Report a rider
  ui/navigation/   Routes and the single NavHost
  ui/theme/        Colors, typography, shapes
firestore.rules    Security rules (deployed to the live project)
functions/         Cloud Functions, written but not deployed (see limitations)
worker/            Cloudflare Worker that stores profile photos (deploy steps in worker/README.md)
public/            Firebase Hosting: email-link landing page and assetlinks.json
seed/              Script that seeds demo riders and lobbies
```

## Getting started

### Prerequisites

- Android Studio, or the Android SDK plus a **JDK between 17 and 21**. The project uses
  Gradle 8.9, which cannot run on JDK 22 or newer (JDK 25 fails with a bare version-string error).
- Node.js, only needed for the seed script, the photo Worker and Firebase deploys.

### Run the app

1. Clone the repository.
2. `app/google-services.json` is already committed and points at the project's Firebase backend,
   so no Firebase setup is needed just to run the app.
3. Open the project in Android Studio and run it, or from a terminal:

   ```bash
   ./gradlew installDebug
   ```

   To use a specific JDK, set `JAVA_HOME` for that command.

### Profile photos

Photos upload through the Worker in `worker/`, already deployed at
`https://offcampus-photos.offcampus.workers.dev` and set as the app's default. To run your own copy
instead, see [worker/README.md](worker/README.md).

### Seed demo data

The seeded riders (`rider_aditi`, `rider_kabir`, ...) exist only as Firestore documents. They have
no login, so they show up as lobby members and chat senders but cannot be signed in as. See
[seed/README.md](seed/README.md) for the service-account setup, then:

```bash
cd seed && npm install && npm run seed
```

## Firebase

Deploy commands, run from the repository root:

```bash
npx firebase-tools@latest deploy --only firestore:rules --project offcampus-5f370
npx firebase-tools@latest deploy --only hosting --project offcampus-5f370
```

Firestore rules changes go live immediately for every client, old or new. The Auto seat cap and
the master-only "complete lobby" rule are enforced there as well as in the UI.

### Email-link sign-in and App Links

Firebase Dynamic Links shut down in 2025, so the emailed link is handed to the app with plain
Android **App Links**:

- The manifest declares an `autoVerify` intent-filter for `offcampus-5f370.web.app/finishSignIn`.
- `public/.well-known/assetlinks.json` lists the SHA-256 fingerprint of the certificate that signs
  the app. Android checks it when the app is installed or updated and, if it matches, opens the
  link in the app instead of the browser.
- That fingerprint differs per signing key. Each developer machine generates its own debug
  keystore, and a release build uses a different one. Get yours with `./gradlew signingReport`,
  add it as another entry in `sha256_cert_fingerprints`, redeploy hosting, then reinstall the app.
- A link only completes sign-in on the device and app install that requested it.

## Testing tips

- Start the emulator with `emulator -avd <name> -no-snapshot-load`. A plain launch can restore an
  old saved snapshot that still has an old build of the app installed.
- The emulator can show a "System UI isn't responding" dialog after a cold boot. Tap **Wait**.
- On a real phone, enable USB debugging and check `adb devices` shows `device`, not
  `unauthorized`.

## Known limitations

These are deliberate scope decisions rather than oversights:

- **No push notifications outside the app.** Cloud Functions and FCM need the paid Blaze plan.
  `functions/index.js` is written but not deployed, so notifications only appear while the app
  process is alive.
- **Profile photos live in Cloudflare KV**, not Firebase Storage (which needs Blaze). They show in
  most places but not in chat bubbles, the report screen or the rate-rider screen, which still
  show the preset avatar. Photo URLs are public to anyone who knows a rider's uid.
- **Friend search matches prefixes only** (typing "Ay" finds "Ayush", but "ush" does not).
  Firestore has no substring search.
- **Rating and payment-ack arithmetic runs on the client.** The rules can check that results are
  plausible but not that the maths was honest. A Cloud Function would fix that.
- Out of scope for the whole project: real payments, live GPS, driver KYC and real ride-hailing
  API booking.

## Team

Ayush Trivedi, Akshat Vidyarthi and Harsh Thakkar.

More detail on every phase and post-launch fix, including the reasoning behind each trade-off,
is in [CLAUDE.md](CLAUDE.md).
