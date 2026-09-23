# OffCampus photo Worker

A Cloudflare Worker that stores riders' profile photos in Workers KV. The app can't hold storage
credentials (anyone can extract them from an APK), so it uploads through this Worker, which checks
the rider's Firebase login token first and only ever writes to that rider's own key.

| Route | Auth | What it does |
|---|---|---|
| `POST /photo` | Firebase ID token | Stores the request body (a JPEG) as the caller's photo, returns `{ "url": ... }` |
| `DELETE /photo` | Firebase ID token | Deletes the caller's photo |
| `GET /photo/<uid>` | none | Returns that rider's photo, or 404 |

The uid always comes from the **verified token**, never from the request, so nobody can overwrite
someone else's photo. Uploads must be JPEG and at most 300 KB.

## Deploy (one time)

You need a free Cloudflare account. Workers and KV should not need a payment method, but confirm on
Cloudflare's signup and pricing pages before relying on that.

```bash
cd worker
npm install
npx wrangler login                      # opens a browser to authorize your account
npx wrangler kv namespace create PHOTOS # prints an id
```

Paste that id into `wrangler.toml` in place of `REPLACE_WITH_YOUR_KV_NAMESPACE_ID`, then:

```bash
npx wrangler deploy
```

Wrangler prints the Worker's URL, something like `https://offcampus-photos.<you>.workers.dev`.
Put it in `app/build.gradle.kts` as the default for `photoWorkerUrl` (replacing the
`REPLACE-ME` placeholder), rebuild and reinstall the app.

## Run locally

```bash
cd worker && npm run dev        # http://localhost:8787, KV is simulated, no login needed
adb reverse tcp:8787 tcp:8787   # so an emulator or phone can reach it
```

Add `photoWorkerUrl=http://localhost:8787` to the git-ignored `local.properties` and reinstall a
**debug** build (only debug builds are allowed to use plain http to localhost).

## Things to know

- KV is eventually consistent. A new photo can take up to about a minute to appear from other
  locations. Every upload gets a fresh `?v=` in its URL so caches never serve a stale one.
- `GET /photo/<uid>` is public: anyone who knows a rider's uid can fetch their photo.
- There is no rate limiting. Fine for a college project, worth adding before real launch.
- The free KV plan limits writes per day. Only uploads and deletes count, not views.
