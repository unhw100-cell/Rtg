package com.example.rust

import com.example.data.model.ChatMessage
import com.example.data.model.ChatRoom
import com.example.data.model.ContactEntry
import com.example.data.model.DraftEntry
import com.example.data.repository.GeminiBotPresets
import com.example.data.repository.GeminiModels
import com.example.ui.i18n.StringsProvider
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Real JNI integration tests verifying Kotlin <-> Rust Core (`librustgram_bridge.so`)
 * round-trips across all 15 Rust workspace crates without mocks or fallbacks.
 */
class RustBridgeIntegrationTest {

  @Test
  fun nativeCoreInfo_andLocalizedStrings_comeDirectlyFromRust() {
    assertTrue(RustBridge.isNativeLibraryLoaded())
    val info = RustBridge.coreInfo()
    assertEquals("rustgram-core", info.engine)
    assertEquals(15, info.cratesCount)
    assertEquals("direct-jni-cdylib", info.abiBridge)

    val ru = StringsProvider.forLang("ru")
    val en = StringsProvider.forLang("en")
    assertEquals("Rustgram", ru.appName)
    assertEquals("Войти через Google", ru.signInWithGoogle)
    assertEquals("Sign in with Google", en.signInWithGoogle)
    assertTrue(ru.currentDeviceLabel.contains("Rust Core"))
  }

  @Test
  fun profileAndContactAndDeterministicIds_enforcedInRust() {
    val profile =
      RustBridge.buildProfileUpdate(
        uid = "uid_alice_001",
        displayName = "  Алиса Rust  ",
        username = "@al",
        bio = "Kernel & Tokio",
        avatarColorHex = "#d96b27",
        languagePref = "ru",
        darkModePref = true,
      )
    assertEquals("Алиса Rust", profile.displayName)
    assertTrue(profile.username.startsWith("user_"))
    assertEquals("#D96B27", profile.avatarColorHex)

    assertEquals("rust_dev", RustBridge.normalizeLookupHandle(" @Rust_Dev "))
    assertNull(RustBridge.normalizeLookupHandle("@x"))

    val blocked =
      ContactEntry(
        id = "c2",
        userId = "uid_alice_001",
        contactUid = "uid_bob_002",
        displayName = "Bob",
        username = "bob",
        isBlocked = true,
      )
    var rejected = false
    try {
      RustBridge.canMessageContact(listOf(blocked), "uid_bob_002")
    } catch (e: IllegalStateException) {
      rejected = e.message?.contains("blocked", ignoreCase = true) == true
    }
    assertTrue("Blocked contact must be rejected by Rust Core", rejected)

    val ab = RustBridge.deterministicIds("uid_a", peerUid = "uid_b", roleId = "rust_architect")
    val ba = RustBridge.deterministicIds("uid_b", peerUid = "uid_a", roleId = "rust_architect")
    assertEquals(ab.directChatId, ba.directChatId)
    assertNotNull(ab.savedChatId)
    assertNotNull(ab.botChatId)
  }

  @Test
  fun messagingAttachmentsReactionsAndSearch_workAcrossJni() {
    val b64 = Base64.getEncoder().encodeToString("hello-jpeg".toByteArray())
    val validatedImg = RustBridge.validateAttachment("image", "photo.jpg", b64)
    assertEquals("image", validatedImg.attachmentType)
    assertEquals("1 KB", RustBridge.formatDocumentSizeKb(500L))
    assertEquals("voice_15s.m4a", RustBridge.formatVoiceFilename(15L))

    val plan =
      RustBridge.prepareSendMessage(
        uid = "uid_alice",
        chatId = "room_1",
        messageId = "msg_1",
        text = "  Привет из Rust Core!  ",
        senderName = "Алиса",
        senderType = "user",
        memberUids = listOf("uid_bob"),
        isPublic = false,
        replyToMessageId = "",
        replyPreviewText = "",
        forwardedFromName = "",
        attachmentType = "image",
        attachmentName = "arch.png",
        attachmentData = b64,
        isSilent = true,
      )
    assertEquals("Привет из Rust Core!", plan.text)
    assertEquals(listOf("uid_bob", "uid_alice"), plan.memberUids)
    assertEquals(64, plan.integritySha256.length)
    assertTrue(plan.isSilent)

    val r1 = RustBridge.toggleReaction("uid_alice", emptyMap(), "🚀")
    assertEquals(listOf("uid_alice"), r1["🚀"])
    val r2 = RustBridge.toggleReaction("uid_alice", r1, "🚀")
    assertFalse(r2.containsKey("🚀"))

    val msg1 = ChatMessage(id = "m1", senderName = "Алиса", text = "Привет, Мир Rust!", attachmentType = "none")
    val msg2 = ChatMessage(id = "m2", senderName = "Bob", text = "Unrelated note", attachmentType = "none")
    val found = RustBridge.searchMessages(listOf(msg1, msg2), "привет")
    assertEquals(listOf("m1"), found.map { it.id })
  }

  @Test
  fun groupsWorkspaceFilterDraftsNotificationsAndAi_workEndToEnd() {
    val groupPlan =
      RustBridge.buildChatRoom(
        uid = "uid_alice",
        chatType = "group",
        title = "Rust Engine",
        description = "Core devs",
        avatarColorHex = "#D96B27",
        extraMemberUids = listOf("uid_bob"),
        isPublic = true,
        botRoleId = "",
        botModelName = "",
        botSystemPrompt = "",
        initialPreview = "Init",
        initialSender = "Alice",
        chatId = "grp_1",
      )
    assertFalse("Groups cannot be marked public", groupPlan.isPublic)
    assertEquals(listOf("uid_alice", "uid_bob"), groupPlan.memberUids)

    val groupRoom =
      ChatRoom(
        id = "grp_1",
        ownerId = "uid_alice",
        chatType = "group",
        title = "Rust Engine",
        memberUids = listOf("uid_alice"),
        isPublic = false,
      )
    val channelRoom =
      ChatRoom(
        id = "chan_1",
        ownerId = "uid_other",
        chatType = "channel",
        title = "Rustgram Releases",
        memberUids = listOf("uid_other"),
        isPublic = true,
      )
    val workspace =
      RustBridge.filterChatWorkspace(
        chats = listOf(groupRoom),
        publicChannels = listOf(channelRoom),
        currentUid = "uid_alice",
        filter = "ALL",
        query = "releases",
      )
    assertTrue(workspace.filteredChats.isEmpty())
    assertEquals(listOf("chan_1"), workspace.discoverableChannels.map { it.id })

    val draftUpsert = RustBridge.planDraftMutation("uid_alice", "grp_1", "wip message")
    assertTrue(draftUpsert is RustDraftMutation.Upsert)
    val draftsMap = RustBridge.buildDraftsMap(listOf(DraftEntry(id = "grp_1", chatId = "grp_1", text = "wip message")))
    assertEquals("wip message", draftsMap["grp_1"])

    val notif =
      RustBridge.evaluateNotification(
        currentUid = "uid_alice",
        authorId = "uid_bob",
        senderName = "Bob",
        chatTitle = "Rust Engine",
        messageText = "Pinned release",
        isSilent = false,
        blockedUids = emptyList(),
      )
    assertTrue(notif.shouldNotify)
    assertEquals("Rust Engine", notif.title)

    assertEquals(3, GeminiModels.availableModels.size)
    assertEquals(4, GeminiBotPresets.all.size)
    val aiReq =
      RustBridge.prepareGeminiRequest(
        requestedModel = "gemini-4-argon",
        systemPrompt = "Rust Architect",
        historyMessages = listOf(ChatMessage(id = "h1", senderType = "user", text = "Hi")),
        userPrompt = "",
        hasImage = true,
      )
    assertEquals("gemini-4-argon", aiReq.modelName)
    assertEquals("Please analyze the attached image.", aiReq.userPrompt)
    assertEquals(1, aiReq.history.size)
  }
}
