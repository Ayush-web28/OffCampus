# OffCampus

Last-mile ride-share Android app for college students: form travel "lobbies" heading the same way from a station/gate at a similar time, coordinate in-app chat, book the actual ride via Uber/Ola/Rapido deep links, split the fare with a two-way payment acknowledgement.

## Tech stack
- Kotlin, Jetpack Compose (Material 3) — no XML layouts
- Firebase: Authentication (college email), Firestore, Cloud Functions (matching/notification/acknowledgement logic)
- MVVM: ViewModel + StateFlow feeding Composables, no business logic inside Composables
- No payment gateway, no live GPS, no in-house driver network — ride booking hands off to Uber/Ola/Rapido via deep links

## Design system (Phase 0 — approved design brief lives at the artifact link in conversation history)
Concept: "streetlight & meter" — the actual moment of waiting at a gate at dusk for a shared auto. Quiet neutral surfaces; amber/violet/green/coral used only for actions and status, not decoration. Cards clip one corner like a ticket stub (`RoundedCornerShape(16,16,16,3)`); buttons stay full pill.

**Color roles (light / dark):**
- Background: `#F7F5FB` / `#171226`
- Surface: `#FFFFFF` / `#201A35`
- Primary (violet): `#6753E8` / `#9384FF`
- Action (amber): `#E8940E` / `#F5A524`
- Success/settled (green): `#1E9463` / `#3FCB90`
- Alert/owed (coral): `#D8492F` / `#FF7A5C`

**Typography:**
- Display/headlines: Bricolage Grotesque (bold, personality — app name, empty states, headers)
- Body/UI: Manrope (lists, chat, buttons)
- Numerals (fares, timestamps, seat counts): JetBrains Mono, tabular

**Spacing:** 4dp base grid — 4/8/12/16/24/32/48/64.

**Motion:** spring-based (damping ~0.8, ~200-300ms). Lobby list uses `AnimatedVisibility` (fade+scale) and `animateContentSize` for seat-count changes. Chat bubbles slide up 12dp + fade in. Payment ack crossfades chip color + morphs a checkmark on settle.

Once `Color.kt` / `Type.kt` / `Theme.kt` exist, every screen must use them — flag any screen that drifts during self-review.

## Status
Phases 0-6 are done and checked off. Phase 0: design brief. Phase 1: project setup + data model. Phase 2 (Auth, Profile, Avatar): email/password auth via Firebase, an 8-avatar catalog (`ui/avatar/AvatarCatalog.kt`) in the 4 brand accent colors, avatar picker + profile screens, MVVM via `AuthViewModel`/`ProfileViewModel` + StateFlow. Phase 3 (Lobby System): post-trip form (`ui/lobby/PostTripScreen.kt`), browse/filter/sort (`ui/lobby/LobbyBrowseScreen.kt` + `LobbyBrowseViewModel.kt`), join/leave/lock (`ui/lobby/LobbyDetailScreen.kt` + `LobbyDetailViewModel.kt`), all live via Firestore snapshot listeners. Phase 4 (Friend System): send/accept/decline via `ui/friends/FriendsViewModel.kt` + `FriendsScreen.kt` (self-request and duplicate-request both blocked), a bottom-nav badge showing pending incoming requests, a "Friends only" lobby filter, and an in-app Snackbar notification when a friend posts a new lobby (client-observed for now — Phase 9's Cloud Function will make this a real push notification that works even when the app isn't open). Phase 5 (Chat): shared `ui/chat/ChatScreen.kt` presentational UI + `ChatViewModel.kt` (generic, works for both chat kinds) driving `LobbyChatScreen.kt` (group chat, chat id == lobby id, sender names shown) and `FriendChatScreen.kt` (1:1, chat id from `friendChatId()` — sorted uid pair, sender names hidden since side implies who). Chat header shows only name + avatar, never email/phone. Bottom nav is Lobbies / Friends / Profile, added in `OffCampusNavHost.kt`. Phase 6 (Ride Booking Deep Links): `ui/lobby/RideBooking.kt` builds the Uber/Ola/Rapido links and replaces the old locked-lobby placeholder with real buttons. No geocoding or live GPS (both out of scope), so pickup relies on each platform's own current-location detection and drop-off is a text hint, not a map pin. Uber uses its documented `m.uber.com/ul/` universal link (real https, falls back to web automatically if the app's missing); Ola/Rapido use undocumented bare app schemes with an explicit `market://` → Play Store web fallback chain, verified for real on this emulator (neither app nor even the Play Store app is installed here, so both fallback tiers fired for real and landed on the correct listings). Firestore rules are currently the interim `firestore.rules` in the repo root (any signed-in user can read/write anything) — Phase 9 replaces this with real per-collection ownership rules. Next up: Phase 7 (Payment Splitting).

## Working method — phased, not linear
Work through phases in order (0 Design Brief → 1 Setup/Data Model → 2 Auth/Profile → 3 Lobby System → 4 Friends → 5 Chat → 6 Ride Booking Deep Links → 7 Payment Splitting → 8 Reporting → 9 Security Rules/Cloud Functions → 10 Aesthetic Polish → 11 Full Regression). After each phase: summarize what was built, run its checklist explicitly (pass/fail per item, not just "done"), fix failures and re-run the *whole* checklist (a fix can break something that previously passed), then wait for the user's go-ahead before starting the next phase. If something is ambiguous (design choice, data-model tradeoff), stop and ask rather than deciding silently.

## Firestore data model
- `riders` — name, avatar, rating, friends array
- `lobbies` — checkpoint, gate, destination, time, rideType, maxSize, members array, status, createdBy
- `friendRequests` — status: pending/accepted
- `chats` — lobby chats + persistent friend-to-friend chats, subcollection of messages (senderId, text, timestamp)
- `paymentSplits` — keyed by lobbyId: totalFare, paidBy, participants array (owedAmount, senderAck, receiverAck, settledStatus)
- `reports` — reportedBy, reportedUser, lobbyId, amount, reason, timestamp, status

## Rules
- Keep code readable and commented — the user needs to explain every part of it in a viva, so comment for a learner, not just non-obvious WHY.
- Prioritize a working, polished app over an ambitious broken one — if a phase is at risk, say which feature to trim rather than shipping something half-working.
- Explain real architectural tradeoffs (e.g. an awkward Firestore query pattern for the two-way ack model) instead of silently picking a workaround.
- Out of scope for this project (final report should list explicitly): real payments, live GPS, driver KYC, real ride-hailing API booking.
