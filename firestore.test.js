const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_student_1";
const BOB_UID = "bob_student_2";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validEntry(uid, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    studentName: "Sofía Martínez",
    course: "6° A",
    school: "Escuela República de Chile",
    bestScore: 1240,
    bestLevel: 3,
    linesCleared: 11,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: cannot read, list, or write leaderboard", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb
      .collection("leaderboard")
      .where("bestScore", ">", 0)
      .orderBy("bestScore", "desc")
      .get()
  );
  await assertFails(unauthDb.collection("leaderboard").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("leaderboard").doc(ALICE_UID).set(validEntry(ALICE_UID))
  );
});

test("Authenticated student: valid Kotlin-mirrored create and partial update succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    aliceDb.collection("leaderboard").doc(ALICE_UID).set(
      validEntry(ALICE_UID, {
        bestScore: 0,
        bestLevel: 1,
        linesCleared: 0,
        createdAt: created,
        updatedAt: created,
      })
    )
  );

  // Student improves score and updates profile
  await assertSucceeds(
    aliceDb.collection("leaderboard").doc(ALICE_UID).update({
      studentName: "Sofía M.",
      course: "6° B",
      school: "Colegio San Martín",
      bestScore: 2880,
      bestLevel: 4,
      linesCleared: 15,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Security bounds: reject cross-user writes, shadow fields, score regression, and immutable tampering", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    aliceDb
      .collection("leaderboard")
      .doc(ALICE_UID)
      .set(validEntry(ALICE_UID, { bestScore: 2000, createdAt: created, updatedAt: created }))
  );

  // Cross-user create/update/delete denied
  await assertFails(
    bobDb.collection("leaderboard").doc(ALICE_UID).update({ bestScore: 99999 })
  );
  await assertFails(
    bobDb.collection("leaderboard").doc(ALICE_UID).delete()
  );
  await assertFails(
    bobDb.collection("leaderboard").doc(ALICE_UID).set(validEntry(ALICE_UID))
  );

  // Identity spoofing inside own doc denied
  await assertFails(
    bobDb.collection("leaderboard").doc(BOB_UID).set(validEntry(ALICE_UID))
  );

  // Shadow / @DocumentId `id` or ghost field denied
  await assertFails(
    bobDb
      .collection("leaderboard")
      .doc(BOB_UID)
      .set({ ...validEntry(BOB_UID), id: BOB_UID })
  );
  await assertFails(
    bobDb
      .collection("leaderboard")
      .doc(BOB_UID)
      .set({ ...validEntry(BOB_UID), isAdmin: true })
  );

  // Score regression denied
  await assertFails(
    aliceDb.collection("leaderboard").doc(ALICE_UID).update({
      bestScore: 500,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Immutable createdAt mutation denied
  await assertFails(
    aliceDb.collection("leaderboard").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Future timestamp rejected
  await assertFails(
    bobDb
      .collection("leaderboard")
      .doc(BOB_UID)
      .set(validEntry(BOB_UID, { createdAt: new Date(Date.now() + 60000) }))
  );

  // Oversized / too short fields rejected
  await assertFails(
    bobDb
      .collection("leaderboard")
      .doc(BOB_UID)
      .set(validEntry(BOB_UID, { studentName: "A" }))
  );
  await assertFails(
    bobDb
      .collection("leaderboard")
      .doc(BOB_UID)
      .set(validEntry(BOB_UID, { school: "X".repeat(81) }))
  );
});

test("Client query alignment: filtered leaderboard query succeeds; unfiltered query fails", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db
      .collection("leaderboard")
      .doc(ALICE_UID)
      .set(validEntry(ALICE_UID, { bestScore: 1800 }));
    await db
      .collection("leaderboard")
      .doc(BOB_UID)
      .set(validEntry(BOB_UID, { studentName: "Lucas Pérez", bestScore: 2400 }));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // Matches Kotlin repository query: .whereGreaterThan("bestScore", 0L).orderBy("bestScore", DESCENDING).limit(50)
  const snap = await assertSucceeds(
    aliceDb
      .collection("leaderboard")
      .where("bestScore", ">", 0)
      .orderBy("bestScore", "desc")
      .limit(50)
      .get()
  );
  if (snap.size !== 2) {
    throw new Error(`Expected 2 leaderboard rows, got ${snap.size}`);
  }

  // Unfiltered query without bestScore > 0 predicate must fail
  await assertFails(aliceDb.collection("leaderboard").get());
});

// --- REAL-TIME VERSUS MATCHES & ROUNDS TESTS ---

function validVersusMatchPayload(hostUid, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    hostUserId: hostUid,
    hostName: "Sofía Martínez",
    memberUids: [hostUid],
    status: "waiting",
    currentRound: 1,
    totalRounds: 5,
    hostWins: 0,
    guestWins: 0,
    roundSeed: 123456,
    targetArea: 12,
    targetWidth: 6,
    targetHeight: 2,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

test("Versus matches: unauthenticated access and unfiltered queries are denied", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("versus_matches").where("status", "==", "waiting").get());
  await assertFails(
    unauthDb.collection("versus_matches").doc("AB12CD").set(validVersusMatchPayload(ALICE_UID))
  );

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("versus_matches").get());
});

test("Versus matches: create, join, atomic round result transaction, and terminal state lock", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext("charlie_789").firestore();

  const matchId = "MD4K9P";
  const created = new Date(Date.now() - 10000);

  // 1. Host creates waiting room
  await assertSucceeds(
    aliceDb
      .collection("versus_matches")
      .doc(matchId)
      .set(validVersusMatchPayload(ALICE_UID, { createdAt: created, updatedAt: created }))
  );

  // Open rooms query matches Kotlin observeOpenMatches()
  const openSnap = await assertSucceeds(
    bobDb.collection("versus_matches").where("status", "==", "waiting").limit(20).get()
  );
  if (openSnap.size !== 1) {
    throw new Error(`Expected 1 open room, got ${openSnap.size}`);
  }

  // 2. Guest joins match atomically
  await assertSucceeds(
    bobDb.collection("versus_matches").doc(matchId).update({
      guestUserId: BOB_UID,
      guestName: "Lucas Contreras",
      memberUids: [ALICE_UID, BOB_UID],
      status: "active",
      updatedAt: new Date(Date.now() - 3000),
    })
  );

  // Outsider (Charlie) cannot read or join an already active match
  await assertFails(charlieDb.collection("versus_matches").doc(matchId).get());
  await assertFails(
    charlieDb.collection("versus_matches").doc(matchId).update({
      guestUserId: "charlie_789",
      guestName: "Intruder",
      memberUids: [ALICE_UID, "charlie_789"],
      status: "active",
      updatedAt: new Date(Date.now() - 2000),
    })
  );

  // 3. Non-atomic round update (without creating rounds/round_1) MUST fail
  await assertFails(
    aliceDb.collection("versus_matches").doc(matchId).update({
      hostWins: 1,
      currentRound: 2,
      targetArea: 18,
      targetWidth: 6,
      targetHeight: 3,
      lastRoundWinnerUid: ALICE_UID,
      lastRoundWinnerName: "Sofía Martínez",
      lastRoundSolveMs: 2450,
      updatedAt: new Date(Date.now() - 1500),
    })
  );

  // Standalone round creation (without updating parent match) MUST fail
  await assertFails(
    aliceDb
      .collection("versus_matches")
      .doc(matchId)
      .collection("rounds")
      .doc("round_1")
      .set({
        matchId,
        roundNumber: 1,
        targetArea: 12,
        targetWidth: 6,
        targetHeight: 2,
        winnerUserId: ALICE_UID,
        winnerName: "Sofía Martínez",
        solveTimeMs: 2450,
        attempts: 1,
        memberUids: [ALICE_UID, BOB_UID],
        createdAt: new Date(Date.now() - 1500),
      })
  );

  // 4. Atomic batch (mirrors Kotlin runTransaction): creates round_1 AND updates versus_matches
  const batch1 = aliceDb.batch();
  const matchRefAlice = aliceDb.collection("versus_matches").doc(matchId);
  const round1RefAlice = matchRefAlice.collection("rounds").doc("round_1");
  const t1 = new Date(Date.now() - 1200);
  batch1.set(round1RefAlice, {
    matchId,
    roundNumber: 1,
    targetArea: 12,
    targetWidth: 6,
    targetHeight: 2,
    winnerUserId: ALICE_UID,
    winnerName: "Sofía Martínez",
    solveTimeMs: 2450,
    attempts: 1,
    memberUids: [ALICE_UID, BOB_UID],
    createdAt: t1,
  });
  batch1.update(matchRefAlice, {
    hostWins: 1,
    guestWins: 0,
    currentRound: 2,
    status: "active",
    targetArea: 18,
    targetWidth: 6,
    targetHeight: 3,
    lastRoundWinnerUid: ALICE_UID,
    lastRoundWinnerName: "Sofía Martínez",
    lastRoundSolveMs: 2450,
    updatedAt: t1,
  });
  await assertSucceeds(batch1.commit());

  // Both participants can read rounds via filtered memberUids query (unfiltered fails)
  await assertSucceeds(
    bobDb
      .collection("versus_matches")
      .doc(matchId)
      .collection("rounds")
      .where("memberUids", "array-contains", BOB_UID)
      .get()
  );
  await assertFails(
    charlieDb.collection("versus_matches").doc(matchId).collection("rounds").get()
  );
  await assertFails(
    charlieDb
      .collection("versus_matches")
      .doc(matchId)
      .collection("rounds")
      .doc("round_1")
      .get()
  );
  await assertFails(
    charlieDb
      .collection("versus_matches")
      .doc(matchId)
      .collection("rounds")
      .where("memberUids", "array-contains", ALICE_UID)
      .get()
  );

  // Immutable round result cannot be overwritten or deleted
  await assertFails(round1RefAlice.update({ solveTimeMs: 100 }));
  await assertFails(round1RefAlice.delete());

  // 5. Bob forfeits active match -> transitions cleanly to finished with Alice as winner, then terminal lock applies
  await assertSucceeds(
    bobDb.collection("versus_matches").doc(matchId).update({
      status: "finished",
      winnerUserId: ALICE_UID,
      winnerName: "Sofía Martínez",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertFails(
    aliceDb.collection("versus_matches").doc(matchId).update({
      hostWins: 3,
      updatedAt: new Date(Date.now() - 200),
    })
  );
});

