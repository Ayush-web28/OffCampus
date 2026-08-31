// Populates Firestore with dummy riders and sample lobbies for local development, and does a
// read-back check to confirm the service account can actually read what it just wrote —
// that read-back is the Phase 1 "Firebase read/write confirmed" check.
//
// Usage: see seed/README.md (needs serviceAccountKey.json next to this file, not committed).

import { initializeApp, cert } from "firebase-admin/app";
import { getFirestore, Timestamp } from "firebase-admin/firestore";
import { readFileSync } from "fs";

const serviceAccount = JSON.parse(
  readFileSync(new URL("./serviceAccountKey.json", import.meta.url))
);

initializeApp({ credential: cert(serviceAccount) });
const db = getFirestore();

function hoursFromNow(hours) {
  return Timestamp.fromDate(new Date(Date.now() + hours * 60 * 60 * 1000));
}

// 9 dummy riders, a couple of them already friends with each other so Phase 4
// (friend system) has real data to work against later.
const riders = [
  { id: "rider_aditi", name: "Aditi Rao", email: "aditi.rao@college.edu", avatarId: "avatar_1", ratingAverage: 4.8, ratingCount: 12, friendIds: ["rider_kabir"] },
  { id: "rider_kabir", name: "Kabir Mehta", email: "kabir.mehta@college.edu", avatarId: "avatar_2", ratingAverage: 4.5, ratingCount: 9, friendIds: ["rider_aditi"] },
  { id: "rider_sana", name: "Sana Iqbal", email: "sana.iqbal@college.edu", avatarId: "avatar_3", ratingAverage: 4.9, ratingCount: 20, friendIds: [] },
  { id: "rider_dev", name: "Dev Prakash", email: "dev.prakash@college.edu", avatarId: "avatar_4", ratingAverage: 4.2, ratingCount: 6, friendIds: [] },
  { id: "rider_meera", name: "Meera Nair", email: "meera.nair@college.edu", avatarId: "avatar_5", ratingAverage: 4.7, ratingCount: 15, friendIds: ["rider_yusuf"] },
  { id: "rider_yusuf", name: "Yusuf Khan", email: "yusuf.khan@college.edu", avatarId: "avatar_6", ratingAverage: 4.4, ratingCount: 8, friendIds: ["rider_meera"] },
  { id: "rider_priya", name: "Priya Suresh", email: "priya.suresh@college.edu", avatarId: "avatar_1", ratingAverage: 4.6, ratingCount: 11, friendIds: [] },
  { id: "rider_arjun", name: "Arjun Bose", email: "arjun.bose@college.edu", avatarId: "avatar_2", ratingAverage: 4.3, ratingCount: 7, friendIds: [] },
  { id: "rider_zara", name: "Zara Fernandes", email: "zara.fernandes@college.edu", avatarId: "avatar_3", ratingAverage: 5.0, ratingCount: 4, friendIds: [] }
];

// 5 lobbies spread across 3 checkpoints/gates, mixing OPEN/LOCKED status and AUTO/CAB ride
// types so Phase 3's filters/sort have something real to filter and sort.
const lobbies = [
  { id: "lobby_gate2_hsr", checkpoint: "Main Campus", gate: "Gate 2", destination: "HSR Layout", departureTime: hoursFromNow(1), rideType: "AUTO", maxSize: 4, memberIds: ["rider_aditi", "rider_kabir"], status: "OPEN", createdBy: "rider_aditi", createdAt: Timestamp.now() },
  { id: "lobby_gate2_koramangala", checkpoint: "Main Campus", gate: "Gate 2", destination: "Koramangala", departureTime: hoursFromNow(2), rideType: "CAB", maxSize: 4, memberIds: ["rider_sana"], status: "OPEN", createdBy: "rider_sana", createdAt: Timestamp.now() },
  { id: "lobby_gate5_whitefield", checkpoint: "Main Campus", gate: "Gate 5", destination: "Whitefield", departureTime: hoursFromNow(1.5), rideType: "AUTO", maxSize: 3, memberIds: ["rider_dev", "rider_meera", "rider_yusuf"], status: "LOCKED", createdBy: "rider_dev", createdAt: Timestamp.now() },
  { id: "lobby_gate5_marathahalli", checkpoint: "Main Campus", gate: "Gate 5", destination: "Marathahalli", departureTime: hoursFromNow(3), rideType: "AUTO", maxSize: 4, memberIds: ["rider_priya"], status: "OPEN", createdBy: "rider_priya", createdAt: Timestamp.now() },
  { id: "lobby_backgate_silkboard", checkpoint: "North Campus", gate: "Back Gate", destination: "Silk Board", departureTime: hoursFromNow(0.5), rideType: "CAB", maxSize: 4, memberIds: ["rider_arjun", "rider_zara"], status: "OPEN", createdBy: "rider_arjun", createdAt: Timestamp.now() }
];

async function seed() {
  const batch = db.batch();

  for (const { id, ...data } of riders) {
    batch.set(db.collection("riders").doc(id), data);
  }
  for (const { id, ...data } of lobbies) {
    batch.set(db.collection("lobbies").doc(id), data);
  }

  await batch.commit();
  console.log(`Wrote ${riders.length} riders and ${lobbies.length} lobbies.`);

  const check = await db.collection("riders").doc(riders[0].id).get();
  if (!check.exists) {
    throw new Error("Read-back check failed: just-written rider document doesn't exist.");
  }
  console.log("Read-back check OK:", JSON.stringify(check.data()));
}

seed().catch((err) => {
  console.error("Seed failed:", err);
  process.exit(1);
});
