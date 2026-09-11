/**
 * Phase 9 Cloud Functions.
 *
 * STATUS: written for the viva report, NOT deployed to the project. Deploying real Cloud
 * Functions needs the Firebase project on the Blaze (pay-as-you-go) plan, even if actual usage
 * stays inside the free quota — that's a real billing decision, not something to flip on
 * silently mid-project. Until it's deployed, the app keeps working exactly as it does today:
 *   - Accepting a friend request is still a two-client-write (see FriendsViewModel.accept()),
 *     which firestore.rules has a narrow carve-out to allow.
 *   - "New lobby from a friend" and "you owe money" notifications stay the in-app Snackbars
 *     built in Phases 4 and 7 (PaymentNotificationViewModel etc.), not real push notifications.
 *
 * To actually deploy this:
 *   1. Enable the Blaze plan on the Firebase project.
 *   2. cd functions && npm install
 *   3. firebase deploy --only functions
 *
 * One more gap even after deploying: notifyFriendsOfNewLobby and notifyPaymentOwed need an FCM
 * registration token on each rider's document (a field like `fcmToken`) to know where to send
 * a push to. The Android app doesn't request notification permission or register a token
 * anywhere yet — that's its own chunk of work (a permission prompt, an FCM SDK dependency, a
 * token-refresh listener writing to Firestore) that wasn't built in this phase, since without a
 * deployed function there was nothing for a token to be used by. Both functions below no-op
 * gracefully when a rider has no token, so deploying this today is safe — it just won't send
 * anything until that registration step exists too.
 */

const { onDocumentUpdated, onDocumentCreated } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();
const db = getFirestore();

/**
 * Replaces the two-client-write in FriendsViewModel.accept(): once a friendRequest flips to
 * ACCEPTED, this writes BOTH riders' friendIds with admin privileges in one place, instead of
 * trusting whichever client happened to tap "accept" to correctly write to a document that
 * isn't even its own. firestore.rules' isAddingSelfToFriendIds() carve-out exists only because
 * this function isn't deployed yet — once it is, that carve-out can be deleted and riders'
 * update rule can go back to "only ever your own document".
 */
exports.onFriendRequestAccepted = onDocumentUpdated("friendRequests/{requestId}", async (event) => {
  const before = event.data.before.data();
  const after = event.data.after.data();
  if (before.status === after.status || after.status !== "ACCEPTED") return;

  const { fromUserId, toUserId } = after;
  const batch = db.batch();
  batch.update(db.collection("riders").doc(fromUserId), {
    friendIds: FieldValue.arrayUnion(toUserId),
  });
  batch.update(db.collection("riders").doc(toUserId), {
    friendIds: FieldValue.arrayUnion(fromUserId),
  });
  await batch.commit();
});

/**
 * Real push notification for the "a friend posted a lobby" case — PaymentNotificationViewModel
 * and the Phase 4 friend-lobby Snackbar are both explicitly client-observed stand-ins for this,
 * only firing while the app happens to be open. See the fcmToken caveat in the file header.
 */
exports.notifyFriendsOfNewLobby = onDocumentCreated("lobbies/{lobbyId}", async (event) => {
  const lobby = event.data.data();
  const posterDoc = await db.collection("riders").doc(lobby.createdBy).get();
  const poster = posterDoc.data();
  if (!poster || !poster.friendIds || poster.friendIds.length === 0) return;

  const friendDocs = await db.getAll(
    ...poster.friendIds.map((id) => db.collection("riders").doc(id))
  );
  const tokens = friendDocs.map((doc) => doc.data() && doc.data().fcmToken).filter(Boolean);
  if (tokens.length === 0) return; // nobody has a registered token yet — see file header

  await getMessaging().sendEachForMulticast({
    tokens,
    notification: {
      title: `${poster.name} posted a new trip`,
      body: `${lobby.gate} → ${lobby.destination}`,
    },
  });
});

/**
 * Real push notification for "you owe money on a trip" — the counterpart to
 * PaymentNotificationViewModel's in-app Snackbar, which only fires while the app is open.
 */
exports.notifyPaymentOwed = onDocumentCreated("paymentSplits/{splitId}", async (event) => {
  const split = event.data.data();
  const participantDocs = await db.getAll(
    ...split.participants.map((p) => db.collection("riders").doc(p.userId))
  );

  const messages = split.participants
    .map((participant, i) => {
      const token = participantDocs[i].data() && participantDocs[i].data().fcmToken;
      if (!token) return null;
      return {
        token,
        notification: {
          title: "You owe money for a trip",
          body: `₹${participant.owedAmount.toFixed(2)} for this trip's fare split`,
        },
      };
    })
    .filter(Boolean);

  if (messages.length === 0) return; // same fcmToken caveat as above

  await Promise.all(messages.map((message) => getMessaging().send(message)));
});
