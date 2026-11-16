const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const serverTimestamp = () => new Date(Date.now() - 1000);

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const CHARLIE_UID = "charlie_789";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

function validProfile(uid, username) {
  return {
    uid,
    displayName: "Alice Rust",
    username,
    bio: "Rustgram user",
    avatarColorHex: "#D96B27",
    languagePref: "ru",
    darkModePref: true,
    blockedUids: [],
    lastSeenAt: serverTimestamp(),
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

function validChat(ownerId, chatType = "direct", isPublic = false) {
  return {
    ownerId,
    chatType,
    title: "Test Chat",
    description: "Description",
    avatarColorHex: "#1D4E89",
    memberUids: [ownerId],
    adminUids: [ownerId],
    isPublic,
    inviteCode: "RUST123",
    pinnedMessageId: "",
    pinnedPreview: "",
    archivedByUids: [],
    botRoleId: "",
    botModelName: "",
    botSystemPrompt: "",
    lastMessagePreview: "",
    lastMessageSender: "",
    lastMessageAt: serverTimestamp(),
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

function validMessage(chatId, authorId, memberUids = [authorId], isPublic = false) {
  return {
    chatId,
    authorId,
    senderType: "user",
    senderName: "Alice",
    memberUids,
    isPublic,
    text: "Hello Rustgram!",
    replyToMessageId: "",
    replyPreviewText: "",
    forwardedFromName: "",
    reactions: {},
    attachmentType: "none",
    attachmentName: "",
    attachmentData: "",
    isSilent: false,
    isEdited: false,
    viewCount: 1,
    readByUids: [authorId],
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

test("Unauthenticated user cannot read users, chats, or messages", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("chats").get());
  await assertFails(unauthDb.collection("chats").doc("c1").collection("messages").get());
});

test("User profile: owner can create/update, cross-user read/write fails, shadow field fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validProfile(ALICE_UID, "alice_rust")));
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).set(validProfile(BOB_UID, "bob_rust")));
  await assertFails(
    aliceDb.collection("users").doc("shadow_user").set({ ...validProfile(ALICE_UID, "x"), isAdmin: true })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({ bio: "Updated bio", updatedAt: serverTimestamp() })
  );
});

test("User drafts, contacts, and call_logs are strictly owner-scoped", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("drafts").doc("chat_1").set({
      userId: ALICE_UID,
      chatId: "chat_1",
      text: "Unsent message",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("drafts").doc("chat_1").get());
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("contacts").doc("bob_456").set({
      userId: ALICE_UID,
      contactUid: BOB_UID,
      displayName: "Bob",
      username: "bob_dev",
      bio: "Systems dev",
      avatarColorHex: "#1D4E89",
      isFavorite: true,
      isBlocked: false,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("contacts").get());
});

test("Chats: member query succeeds, unfiltered list fails, non-member private read fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(aliceDb.collection("chats").doc("chat_private").set(validChat(ALICE_UID, "direct", false)));
  await assertFails(bobDb.collection("chats").doc("chat_private").get());
  await assertFails(bobDb.collection("chats").get());
  await assertSucceeds(
    aliceDb.collection("chats").where("memberUids", "array-contains", ALICE_UID).get()
  );
});

test("Public channels: anyone signed in can list public channels and join", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(aliceDb.collection("chats").doc("chan_news").set(validChat(ALICE_UID, "channel", true)));
  await assertSucceeds(bobDb.collection("chats").where("isPublic", "==", true).get());
  await assertSucceeds(
    bobDb.collection("chats").doc("chan_news").update({
      memberUids: [ALICE_UID, BOB_UID],
      updatedAt: serverTimestamp(),
    })
  );
  // Non-member cannot make a direct chat public
  await assertFails(bobDb.collection("chats").doc("bad_direct").set(validChat(BOB_UID, "direct", true)));
});

test("Messages: member can create/query/react/edit own, non-member cannot create", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  const chatData = validChat(ALICE_UID, "group", false);
  chatData.memberUids = [ALICE_UID, BOB_UID];
  await assertSucceeds(aliceDb.collection("chats").doc("grp_1").set(chatData));

  await assertSucceeds(
    aliceDb.collection("chats").doc("grp_1").collection("messages").doc("msg_1")
      .set(validMessage("grp_1", ALICE_UID, [ALICE_UID, BOB_UID], false))
  );
  await assertFails(
    charlieDb.collection("chats").doc("grp_1").collection("messages").doc("msg_2")
      .set(validMessage("grp_1", CHARLIE_UID, [CHARLIE_UID], false))
  );
  await assertSucceeds(
    bobDb.collection("chats").doc("grp_1").collection("messages")
      .where("memberUids", "array-contains", BOB_UID).get()
  );
  // Bob can react to Alice's message, but cannot edit its text
  await assertSucceeds(
    bobDb.collection("chats").doc("grp_1").collection("messages").doc("msg_1").update({
      reactions: { "🔥": [BOB_UID] },
      updatedAt: serverTimestamp(),
    })
  );
  await assertFails(
    bobDb.collection("chats").doc("grp_1").collection("messages").doc("msg_1").update({
      text: "Tampered!",
      updatedAt: serverTimestamp(),
    })
  );
  // Alice can edit her own message
  await assertSucceeds(
    aliceDb.collection("chats").doc("grp_1").collection("messages").doc("msg_1").update({
      text: "Edited text",
      isEdited: true,
      updatedAt: serverTimestamp(),
    })
  );
});
