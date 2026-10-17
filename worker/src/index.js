// OffCampus profile-photo service: a Cloudflare Worker in front of a Workers KV namespace.
//
//   POST   /photo        upload (or replace) MY photo   — needs a Firebase ID token
//   DELETE /photo        remove MY photo                — needs a Firebase ID token
//   GET    /photo/<uid>  fetch anyone's photo           — public, so image loaders can use it
//
// Why a Worker at all: the Android app can't be trusted with storage credentials (anyone can pull
// them out of an APK), so this is the trusted middleman. It stores each photo under a key built
// from the uid inside the VERIFIED token, never from anything the caller typed — so a rider can
// only ever write their own photo.

const JWKS_URL =
  "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";

const MAX_PHOTO_BYTES = 300_000; // the app sends ~10-40 KB; this is just a safety ceiling
const UID_PATTERN = /^[A-Za-z0-9]{1,128}$/; // Firebase uids; also keeps KV keys predictable

export default {
  async fetch(request, env) {
    try {
      return await route(request, env);
    } catch (err) {
      if (err instanceof HttpError) return json({ error: err.message }, err.status);
      console.error(err);
      return json({ error: "Internal error" }, 500);
    }
  },
};

async function route(request, env) {
  const url = new URL(request.url);
  const { pathname } = url;

  if (pathname === "/photo" && request.method === "POST") {
    const uid = await authenticate(request, env);
    return uploadPhoto(request, env, uid, url.origin);
  }
  if (pathname === "/photo" && request.method === "DELETE") {
    const uid = await authenticate(request, env);
    await env.PHOTOS.delete(keyFor(uid));
    return json({ ok: true });
  }
  if (pathname.startsWith("/photo/") && request.method === "GET") {
    return getPhoto(env, pathname.slice("/photo/".length));
  }
  if (pathname === "/notify" && request.method === "POST") {
    await authenticate(request, env);
    return sendNotification(request, env);
  }
  throw new HttpError(404, "Not found");
}

async function uploadPhoto(request, env, uid, origin) {
  const declared = Number(request.headers.get("Content-Length") || 0);
  if (declared > MAX_PHOTO_BYTES) throw new HttpError(413, "Photo too large");

  const bytes = await request.arrayBuffer();
  if (bytes.byteLength === 0) throw new HttpError(400, "Empty body");
  if (bytes.byteLength > MAX_PHOTO_BYTES) throw new HttpError(413, "Photo too large");
  if (!looksLikeJpeg(bytes)) throw new HttpError(415, "Only JPEG photos are accepted");

  await env.PHOTOS.put(keyFor(uid), bytes);

  // The ?v= value changes on every upload so image loaders/caches treat a new photo as a new URL
  // instead of serving the old one from cache.
  return json({ url: `${origin}/photo/${uid}?v=${Date.now()}` });
}

async function getPhoto(env, uid) {
  if (!UID_PATTERN.test(uid)) throw new HttpError(404, "Not found");
  const bytes = await env.PHOTOS.get(keyFor(uid), { type: "arrayBuffer" });
  if (bytes === null) throw new HttpError(404, "No photo");
  return new Response(bytes, {
    headers: {
      "Content-Type": "image/jpeg",
      // Safe to cache hard: an updated photo gets a new ?v= URL, so a stale copy is never re-asked for.
      "Cache-Control": "public, max-age=31536000, immutable",
    },
  });
}

const keyFor = (uid) => `photo:${uid}`;

// JPEG files always start with the bytes FF D8 FF — cheap protection against someone uploading
// something else through the endpoint.
function looksLikeJpeg(buffer) {
  const b = new Uint8Array(buffer, 0, 3);
  return b[0] === 0xff && b[1] === 0xd8 && b[2] === 0xff;
}

// ---------------------------------------------------------------------------------------------
// FCM Push Notification dispatcher using service account OAuth 2.0 (HTTP v1 API)
// ---------------------------------------------------------------------------------------------

let cachedFcmToken = null;
let cachedFcmTokenExpiresAt = 0;

function bytesToBase64Url(bytes) {
  let binary = "";
  for (let i = 0; i < bytes.byteLength; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary)
    .replace(/=/g, "")
    .replace(/\+/g, "-")
    .replace(/\//g, "_");
}

async function getFcmAccessToken(env) {
  const now = Math.floor(Date.now() / 1000);
  if (cachedFcmToken && now < cachedFcmTokenExpiresAt - 300) {
    return cachedFcmToken;
  }

  const saRaw = await env.PHOTOS.get("config:sa");
  if (!saRaw) throw new HttpError(500, "Service account not configured in KV");
  const sa = JSON.parse(saRaw);

  const pem = sa.private_key;
  const binaryDerString = pem
    .replace(/-----BEGIN PRIVATE KEY-----/, "")
    .replace(/-----END PRIVATE KEY-----/, "")
    .replace(/\s+/g, "");
  const binary = atob(binaryDerString);
  const binaryDer = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) binaryDer[i] = binary.charCodeAt(i);

  const privateKey = await crypto.subtle.importKey(
    "pkcs8",
    binaryDer.buffer,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"]
  );

  const header = bytesToBase64Url(new TextEncoder().encode(JSON.stringify({ alg: "RS256", typ: "JWT" })));
  const payload = bytesToBase64Url(new TextEncoder().encode(JSON.stringify({
    iss: sa.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    exp: now + 3600,
    iat: now
  })));

  const unsignedToken = `${header}.${payload}`;
  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    privateKey,
    new TextEncoder().encode(unsignedToken)
  );

  const signedJwt = `${unsignedToken}.${bytesToBase64Url(new Uint8Array(signature))}`;

  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: signedJwt
    })
  });

  const data = await res.json();
  if (!res.ok || !data.access_token) {
    console.error("OAuth token fetch failed:", data);
    throw new HttpError(500, "Failed to get FCM access token");
  }

  cachedFcmToken = data.access_token;
  cachedFcmTokenExpiresAt = now + (data.expires_in || 3600);
  return cachedFcmToken;
}

async function sendNotification(request, env) {
  const body = await request.json();
  const { tokens, title, body: msgBody, data = {} } = body;
  if (!tokens || !Array.isArray(tokens) || tokens.length === 0) {
    return json({ ok: true, sent: 0 });
  }

  const accessToken = await getFcmAccessToken(env);
  const projectId = env.FIREBASE_PROJECT_ID || "offcampus-5f370";
  const fcmUrl = `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`;

  let sentCount = 0;
  await Promise.all(
    tokens.filter(t => typeof t === "string" && t.length > 10).map(async (token) => {
      try {
        const payload = {
          message: {
            token: token,
            notification: {
              title: title || "OffCampus",
              body: msgBody || ""
            },
            data: Object.fromEntries(
              Object.entries(data).map(([k, v]) => [k, String(v ?? "")])
            )
          }
        };

        const res = await fetch(fcmUrl, {
          method: "POST",
          headers: {
            Authorization: `Bearer ${accessToken}`,
            "Content-Type": "application/json"
          },
          body: JSON.stringify(payload)
        });

        if (res.ok) sentCount++;
      } catch (e) {
        console.error("FCM send error:", e);
      }
    })
  );

  return json({ ok: true, sent: sentCount });
}

// ---------------------------------------------------------------------------------------------
// Firebase ID token verification (RS256 JWT), done by hand with WebCrypto so the Worker has no
// dependencies. Returns the rider's uid, or throws a 401.
// ---------------------------------------------------------------------------------------------

async function authenticate(request, env) {
  const header = request.headers.get("Authorization") || "";
  if (!header.startsWith("Bearer ")) throw new HttpError(401, "Missing token");
  try {
    return await verifyFirebaseToken(header.slice("Bearer ".length), env.FIREBASE_PROJECT_ID);
  } catch (err) {
    throw new HttpError(401, "Invalid token");
  }
}

async function verifyFirebaseToken(token, projectId) {
  const parts = token.split(".");
  if (parts.length !== 3) throw new Error("malformed");
  const [encodedHeader, encodedPayload, encodedSignature] = parts;

  const header = JSON.parse(decodeUtf8(base64UrlToBytes(encodedHeader)));
  if (header.alg !== "RS256") throw new Error("unexpected algorithm");

  // Google publishes the public keys Firebase signs tokens with. Look up the one this token names.
  const jwks = await (await fetch(JWKS_URL, { cf: { cacheTtl: 3600, cacheEverything: true } })).json();
  const jwk = jwks.keys.find((k) => k.kid === header.kid);
  if (!jwk) throw new Error("unknown signing key");

  const key = await crypto.subtle.importKey(
    "jwk",
    jwk,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["verify"]
  );
  const validSignature = await crypto.subtle.verify(
    "RSASSA-PKCS1-v1_5",
    key,
    base64UrlToBytes(encodedSignature),
    new TextEncoder().encode(`${encodedHeader}.${encodedPayload}`)
  );
  if (!validSignature) throw new Error("bad signature");

  // A valid signature only proves Firebase issued it; these checks prove it's for THIS project,
  // still fresh, and names a real user.
  const payload = JSON.parse(decodeUtf8(base64UrlToBytes(encodedPayload)));
  const now = Math.floor(Date.now() / 1000);
  if (payload.exp <= now) throw new Error("expired");
  if (payload.iat > now + 300) throw new Error("issued in the future");
  if (payload.aud !== projectId) throw new Error("wrong audience");
  if (payload.iss !== `https://securetoken.google.com/${projectId}`) throw new Error("wrong issuer");
  if (typeof payload.sub !== "string" || !UID_PATTERN.test(payload.sub)) throw new Error("bad subject");

  return payload.sub;
}

function base64UrlToBytes(value) {
  const base64 = value.replace(/-/g, "+").replace(/_/g, "/").padEnd(Math.ceil(value.length / 4) * 4, "=");
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return bytes;
}

const decodeUtf8 = (bytes) => new TextDecoder().decode(bytes);

class HttpError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

function json(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}
