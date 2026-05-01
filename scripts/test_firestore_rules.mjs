import fs from "node:fs";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment
} from "@firebase/rules-unit-testing";
import {
  deleteDoc,
  doc,
  getDoc,
  setDoc,
  updateDoc
} from "firebase/firestore";

const projectId = "eduspecial-rules-test";
const rules = fs.readFileSync("firestore.rules", "utf8");

const testEnv = await initializeTestEnvironment({
  projectId,
  firestore: { rules }
});

async function run() {
  const aliceAuth = {
    sub: "alice",
    email: "alice@example.com",
    email_verified: false
  };
  const bobAuth = {
    sub: "bob",
    email: "bob@example.com",
    email_verified: false
  };
  const adminAuth = {
    sub: "admin-user",
    email: "mahmoudnabihsaleh@gmail.com",
    email_verified: true
  };

  const aliceDb = testEnv.authenticatedContext("alice", aliceAuth).firestore();
  const bobDb = testEnv.authenticatedContext("bob", bobAuth).firestore();
  const adminDb = testEnv.authenticatedContext("admin-user", adminAuth).firestore();
  const guestDb = testEnv.unauthenticatedContext().firestore();

  const now = Date.now();

  await assertSucceeds(
    setDoc(doc(aliceDb, "users/alice"), {
      email: "alice@example.com",
      displayName: "Alice",
      role: "USER",
      accountStatus: "PENDING_VERIFICATION",
      emailVerified: false,
      phoneVerified: false,
      twoFactorEnabled: false,
      createdAt: now,
      lastLoginAt: now,
      points: 0,
      contributionCount: 0,
      moderationScore: 0.5,
      preferences: {
        language: "en",
        theme: "system",
        themePalette: "qusasa",
        notificationsEnabled: true,
        studyRemindersEnabled: true,
        emailNotificationsEnabled: true,
        soundEnabled: true,
        vibrationEnabled: true,
        autoPlayTTS: false,
        dailyGoal: 20,
        reminderTime: "19:00"
      }
    })
  );

  await assertSucceeds(
    setDoc(doc(bobDb, "users/bob"), {
      email: "bob@example.com",
      displayName: "Bob",
      role: "USER",
      accountStatus: "PENDING_VERIFICATION",
      emailVerified: false,
      phoneVerified: false,
      twoFactorEnabled: false,
      createdAt: now,
      lastLoginAt: now,
      points: 0,
      contributionCount: 0,
      moderationScore: 0.5,
      preferences: {
        language: "en",
        theme: "system",
        themePalette: "qusasa",
        notificationsEnabled: true,
        studyRemindersEnabled: true,
        emailNotificationsEnabled: true,
        soundEnabled: true,
        vibrationEnabled: true,
        autoPlayTTS: false,
        dailyGoal: 20,
        reminderTime: "19:00"
      }
    })
  );

  await assertSucceeds(
    setDoc(doc(adminDb, "users/admin-user"), {
      email: "mahmoudnabihsaleh@gmail.com",
      displayName: "Admin",
      role: "ADMIN",
      accountStatus: "ACTIVE",
      emailVerified: true,
      phoneVerified: false,
      twoFactorEnabled: false,
      createdAt: now,
      lastLoginAt: now,
      points: 0,
      contributionCount: 0,
      moderationScore: 0.5,
      preferences: {
        language: "ar",
        theme: "system",
        themePalette: "qusasa",
        notificationsEnabled: true,
        studyRemindersEnabled: true,
        emailNotificationsEnabled: true,
        soundEnabled: true,
        vibrationEnabled: true,
        autoPlayTTS: false,
        dailyGoal: 20,
        reminderTime: "19:00"
      }
    })
  );

  await assertSucceeds(getDoc(doc(aliceDb, "users/alice")));
  await assertFails(getDoc(doc(bobDb, "users/alice")));
  await assertSucceeds(getDoc(doc(adminDb, "users/alice")));

  await assertSucceeds(
    updateDoc(doc(aliceDb, "users/alice"), {
      displayName: "Alice Updated",
      lastLoginAt: now + 1,
      updatedAt: now + 1,
      preferences: {
        language: "ar",
        theme: "dark",
        themePalette: "qusasa",
        notificationsEnabled: true,
        studyRemindersEnabled: true,
        emailNotificationsEnabled: true,
        soundEnabled: true,
        vibrationEnabled: true,
        autoPlayTTS: false,
        dailyGoal: 20,
        reminderTime: "19:00"
      }
    })
  );

  await assertFails(
    updateDoc(doc(aliceDb, "users/alice"), {
      role: "ADMIN"
    })
  );

  await assertSucceeds(
    setDoc(doc(aliceDb, "qa_questions/question-1"), {
      question: "What is latency?",
      category: "",
      contributor: "alice",
      contributorName: "Alice Updated",
      contributorVerified: false,
      contributorAvatarUrl: null,
      upvotes: 0,
      createdAt: now,
      isAnswered: false,
      hashtags: ["network", "performance"]
    })
  );

  await assertSucceeds(getDoc(doc(guestDb, "qa_questions/question-1")));
  await assertFails(
    setDoc(doc(guestDb, "qa_questions/question-guest"), {
      question: "Guest question",
      contributor: "guest",
      contributorName: "Guest",
      contributorVerified: false,
      upvotes: 0,
      createdAt: now,
      isAnswered: false
    })
  );

  await assertFails(
    updateDoc(doc(bobDb, "qa_questions/question-1"), {
      question: "Bob hijack"
    })
  );

  await assertSucceeds(
    updateDoc(doc(aliceDb, "qa_questions/question-1"), {
      question: "What is network latency?",
      updatedAt: now + 2
    })
  );

  await assertSucceeds(
    setDoc(doc(bobDb, "qa_answers/answer-1"), {
      questionId: "question-1",
      content: "Latency is the delay before data transfer starts.",
      contributor: "bob",
      contributorName: "Bob",
      contributorVerified: false,
      contributorAvatarUrl: null,
      upvotes: 0,
      isAccepted: false,
      createdAt: now
    })
  );

  await assertFails(
    updateDoc(doc(bobDb, "qa_answers/answer-1"), {
      isAccepted: true
    })
  );

  await assertSucceeds(
    updateDoc(doc(aliceDb, "qa_answers/answer-1"), {
      isAccepted: true,
      updatedAt: now + 3
    })
  );

  await assertSucceeds(
    updateDoc(doc(aliceDb, "qa_questions/question-1"), {
      isAnswered: true,
      acceptedAnswerId: "answer-1",
      updatedAt: now + 4
    })
  );

  await assertSucceeds(
    setDoc(doc(aliceDb, "security_logs/log-1"), {
      userId: "alice",
      event: "LOGIN",
      timestamp: now,
      details: { source: "test" },
      success: true
    })
  );

  await assertSucceeds(getDoc(doc(aliceDb, "security_logs/log-1")));
  await assertFails(getDoc(doc(bobDb, "security_logs/log-1")));
  await assertSucceeds(deleteDoc(doc(aliceDb, "security_logs/log-1")));

  console.log("RULES_TEST_PASS");
}

try {
  await run();
} finally {
  await testEnv.cleanup();
}
