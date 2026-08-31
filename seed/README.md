# Seeding Firestore

One-time script that writes the Phase 1 dummy data (9 riders, 5 lobbies across 3 gates) into
your Firebase project's Firestore, and reads one document back to confirm it worked.

## Setup

1. `cd seed && npm install`
2. Firebase Console → your project → gear icon → **Project settings** → **Service accounts** tab
   → **Generate new private key**. Save the downloaded file as `seed/serviceAccountKey.json`
   (this filename is already in `.gitignore` — never commit it, it grants full admin access
   to your Firebase project).
3. `npm run seed`

You should see `Wrote 9 riders and 5 lobbies.` followed by a `Read-back check OK: ...` line.
Then check the Firestore tab in the Firebase Console — the `riders` and `lobbies` collections
should be populated.
