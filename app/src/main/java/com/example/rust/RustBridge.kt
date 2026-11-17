package com.example.rust

import com.example.data.model.ChatMessage
import com.example.data.model.ChatRoom
import com.example.data.model.ContactEntry
import com.example.data.model.DraftEntry
import com.example.data.model.GeminiBotPreset
import com.example.ui.i18n.AppStrings
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

data class RustCoreInfo(
  val engine: String,
  val version: String,
  val targetArch: String,
  val cratesCount: Int,
  val abiBridge: String,
)

data class RustErrorReport(
  val error: String,
  val operationType: String,
  val path: String?,
  val userId: String?,
  val isPermissionDenied: Boolean,
  val isNetworkError: Boolean,
)

data class RustProfilePlan(
  val uid: String,
  val username: String,
  val displayName: String,
  val bio: String,
  val avatarColorHex: String,
  val languagePref: String,
  val darkModePref: Boolean,
  val blockedUids: List<String>,
)

data class RustContactPlan(
  val id: String,
  val userId: String,
  val contactUid: String,
  val displayName: String,
  val username: String,
  val bio: String,
  val avatarColorHex: String,
  val isFavorite: Boolean,
  val isBlocked: Boolean,
)

data class RustOutgoingMessagePlan(
  val messageId: String,
  val chatId: String,
  val authorId: String,
  val senderType: String,
  val senderName: String,
  val memberUids: List<String>,
  val isPublic: Boolean,
  val text: String,
  val replyToMessageId: String,
  val replyPreviewText: String,
  val forwardedFromName: String,
  val attachmentType: String,
  val attachmentName: String,
  val attachmentData: String,
  val isSilent: Boolean,
  val lastMessagePreview: String,
  val lastMessageSender: String,
  val integritySha256: String,
)

data class RustChatRoomPlan(
  val id: String,
  val ownerId: String,
  val chatType: String,
  val title: String,
  val description: String,
  val avatarColorHex: String,
  val memberUids: List<String>,
  val adminUids: List<String>,
  val isPublic: Boolean,
  val inviteCode: String,
  val botRoleId: String,
  val botModelName: String,
  val botSystemPrompt: String,
  val lastMessagePreview: String,
  val lastMessageSender: String,
)

data class RustChatFilterResult(
  val filteredChats: List<ChatRoom>,
  val discoverableChannels: List<ChatRoom>,
)

sealed interface RustDraftMutation {
  data class Delete(val userId: String, val chatId: String) : RustDraftMutation

  data class Upsert(val userId: String, val chatId: String, val text: String) : RustDraftMutation
}

data class RustCallLogPlan(
  val id: String,
  val userId: String,
  val peerName: String,
  val callType: String,
  val status: String,
  val durationSeconds: Long,
)

data class RustValidatedAttachment(
  val attachmentType: String,
  val attachmentName: String,
  val attachmentData: String,
)

data class RustGeminiTurn(
  val role: String,
  val text: String,
)

data class RustPreparedGeminiRequest(
  val modelName: String,
  val systemInstruction: String,
  val temperature: Float,
  val history: List<RustGeminiTurn>,
  val userPrompt: String,
)

data class RustDeterministicIds(
  val savedChatId: String?,
  val directChatId: String?,
  val botChatId: String?,
  val inviteSample: String,
)

data class RustNotificationDecision(
  val shouldNotify: Boolean,
  val suppressReason: String?,
  val title: String,
  val preview: String,
)

data class RustCryptoDigest(
  val sha256: String,
  val integrity: String,
)

data class RustReadReceiptUpdate(
  val messageId: String,
  val readByUids: List<String>,
  val viewCount: Long,
)

sealed interface RustNativeStatus {
  data class Ready(val info: RustCoreInfo) : RustNativeStatus

  data class Unavailable(val reason: String) : RustNativeStatus
}

/**
 * Direct JNI bridge into `librustgram_bridge.so` (15-crate Rust workspace).
 * Automatically resolves either Android `jniLibs` or host JVM test shared library paths.
 */
object RustBridge {
  private val loaded = AtomicBoolean(false)
  @Volatile private var lastLoadError: String? = null

  private fun ensureLoaded() {
    if (loaded.get()) return
    synchronized(this) {
      if (loaded.get()) return
      try {
        System.loadLibrary("rustgram_bridge")
        loaded.set(true)
        lastLoadError = null
        return
      } catch (primaryErr: Throwable) {
        val candidates =
          listOf(
            File("app/build/rust-host-jni/librustgram_bridge.so"),
            File("build/rust-host-jni/librustgram_bridge.so"),
            File("../rustgram/target/release/librustgram_bridge.so"),
            File("rustgram/target/release/librustgram_bridge.so"),
          )
        for (candidate in candidates) {
          if (candidate.exists()) {
            try {
              System.load(candidate.absolutePath)
              loaded.set(true)
              lastLoadError = null
              return
            } catch (candidateErr: Throwable) {
              lastLoadError = candidateErr.localizedMessage ?: candidateErr.javaClass.simpleName
              throw candidateErr
            }
          }
        }
        lastLoadError = primaryErr.localizedMessage ?: primaryErr.javaClass.simpleName
        throw primaryErr
      }
    }
  }

  private external fun nativeCoreInfoJson(): String

  private external fun nativeDispatchJson(commandJson: String): String

  private external fun nativeSanitizeId(raw: String, fallback: String): String

  private external fun nativeLocalizedStringsJson(lang: String): String

  fun isNativeLibraryLoaded(): Boolean {
    return try {
      ensureLoaded()
      loaded.get()
    } catch (_: Throwable) {
      false
    }
  }

  fun probeNativeLibrary(): RustNativeStatus {
    return try {
      RustNativeStatus.Ready(coreInfo())
    } catch (t: Throwable) {
      val reason = t.localizedMessage ?: lastLoadError ?: t.javaClass.simpleName
      lastLoadError = reason
      RustNativeStatus.Unavailable(reason)
    }
  }

  fun lastNativeError(): String? = lastLoadError

  fun coreInfo(): RustCoreInfo {
    ensureLoaded()
    val obj = RustJson.parseObject(nativeCoreInfoJson())
    return RustCoreInfo(
      engine = obj["engine"].asStr("rustgram-core"),
      version = obj["version"].asStr("0.1.0"),
      targetArch = obj["targetArch"].asStr(System.getProperty("os.arch").orEmpty()),
      cratesCount = obj["cratesCount"].asInt(15),
      abiBridge = obj["abiBridge"].asStr("direct-jni-cdylib"),
    )
  }

  fun sanitizeId(raw: String, fallback: String = "item"): String {
    ensureLoaded()
    return nativeSanitizeId(raw, fallback)
  }

  fun getLocalizedStrings(lang: String): AppStrings {
    ensureLoaded()
    val m = RustJson.parseObject(nativeLocalizedStringsJson(lang))
    return AppStrings(
      appName = m["appName"].asStr("Rustgram"),
      tagline = m["tagline"].asStr(),
      signInSubtitle = m["signInSubtitle"].asStr(),
      signInWithGoogle = m["signInWithGoogle"].asStr(),
      checkingSession = m["checkingSession"].asStr(),
      tabChats = m["tabChats"].asStr(),
      tabContacts = m["tabContacts"].asStr(),
      tabAiBots = m["tabAiBots"].asStr(),
      tabCalls = m["tabCalls"].asStr(),
      tabSettings = m["tabSettings"].asStr(),
      filterAll = m["filterAll"].asStr(),
      filterDirect = m["filterDirect"].asStr(),
      filterGroups = m["filterGroups"].asStr(),
      filterChannels = m["filterChannels"].asStr(),
      filterBots = m["filterBots"].asStr(),
      filterArchived = m["filterArchived"].asStr(),
      searchHint = m["searchHint"].asStr(),
      newChat = m["newChat"].asStr(),
      newGroup = m["newGroup"].asStr(),
      newChannel = m["newChannel"].asStr(),
      newAiBot = m["newAiBot"].asStr(),
      emptyChatsTitle = m["emptyChatsTitle"].asStr(),
      emptyChatsBody = m["emptyChatsBody"].asStr(),
      messagePlaceholder = m["messagePlaceholder"].asStr(),
      reply = m["reply"].asStr(),
      react = m["react"].asStr(),
      forward = m["forward"].asStr(),
      pin = m["pin"].asStr(),
      unpin = m["unpin"].asStr(),
      edit = m["edit"].asStr(),
      delete = m["delete"].asStr(),
      copy = m["copy"].asStr(),
      pinnedLabel = m["pinnedLabel"].asStr(),
      draftPrefix = m["draftPrefix"].asStr(),
      silentMode = m["silentMode"].asStr(),
      editedLabel = m["editedLabel"].asStr(),
      aiStreaming = m["aiStreaming"].asStr(),
      modelLabel = m["modelLabel"].asStr(),
      systemInstructionLabel = m["systemInstructionLabel"].asStr(),
      save = m["save"].asStr(),
      cancel = m["cancel"].asStr(),
      create = m["create"].asStr(),
      titleLabel = m["titleLabel"].asStr(),
      descriptionLabel = m["descriptionLabel"].asStr(),
      publicChannelLabel = m["publicChannelLabel"].asStr(),
      contactsTitle = m["contactsTitle"].asStr(),
      addContact = m["addContact"].asStr(),
      lookupHandleHint = m["lookupHandleHint"].asStr(),
      findByHandle = m["findByHandle"].asStr(),
      handleNotFound = m["handleNotFound"].asStr(),
      startChat = m["startChat"].asStr(),
      block = m["block"].asStr(),
      unblock = m["unblock"].asStr(),
      blockedBadge = m["blockedBadge"].asStr(),
      callsTitle = m["callsTitle"].asStr(),
      audioCall = m["audioCall"].asStr(),
      videoCall = m["videoCall"].asStr(),
      localPreviewNotice = m["localPreviewNotice"].asStr(),
      endCall = m["endCall"].asStr(),
      micMuted = m["micMuted"].asStr(),
      micActive = m["micActive"].asStr(),
      speakerOn = m["speakerOn"].asStr(),
      settingsTitle = m["settingsTitle"].asStr(),
      profileSection = m["profileSection"].asStr(),
      displayNameLabel = m["displayNameLabel"].asStr(),
      usernameLabel = m["usernameLabel"].asStr(),
      bioLabel = m["bioLabel"].asStr(),
      appearanceSection = m["appearanceSection"].asStr(),
      darkModeLabel = m["darkModeLabel"].asStr(),
      languageLabel = m["languageLabel"].asStr(),
      sessionsSection = m["sessionsSection"].asStr(),
      currentDeviceLabel = m["currentDeviceLabel"].asStr(),
      signOut = m["signOut"].asStr(),
      archive = m["archive"].asStr(),
      unarchive = m["unarchive"].asStr(),
      photoAttachment = m["photoAttachment"].asStr(),
      fileAttachment = m["fileAttachment"].asStr(),
      voiceNote = m["voiceNote"].asStr(),
      recordingVoice = m["recordingVoice"].asStr(),
      stopRecording = m["stopRecording"].asStr(),
      publicChannelsDiscover = m["publicChannelsDiscover"].asStr(),
      joinChannel = m["joinChannel"].asStr(),
      joined = m["joined"].asStr(),
      membersCount = m["membersCount"].asStr(),
      inviteCodeLabel = m["inviteCodeLabel"].asStr(),
      savedMessagesTitle = m["savedMessagesTitle"].asStr(),
      retry = m["retry"].asStr(),
    )
  }

  private fun dispatch(op: String, params: Map<String, Any?> = emptyMap()): Any? {
    ensureLoaded()
    val payload = LinkedHashMap<String, Any?>()
    payload["op"] = op
    payload.putAll(params)
    val rawResponse = nativeDispatchJson(RustJson.encode(payload))
    val envelope = RustJson.parseObject(rawResponse)
    val ok = envelope["ok"] as? Boolean ?: false
    if (!ok) {
      val err = (envelope["error"] as? String).orEmpty().ifBlank { "Rust Core dispatch error" }
      throw IllegalStateException(err)
    }
    return envelope["data"]
  }

  fun formatStructuredError(
    rawMessage: String,
    opType: String,
    path: String?,
    userId: String?,
  ): RustErrorReport {
    val data =
      dispatch(
        "format_error",
        mapOf(
          "raw_message" to rawMessage,
          "op_type" to opType.lowercase(),
          "path" to path,
          "user_id" to userId,
        ),
      )
        .asObj()
    return RustErrorReport(
      error = data["error"].asStr(rawMessage),
      operationType = data["operationType"].asStr(opType),
      path = data["path"] as? String,
      userId = data["userId"] as? String,
      isPermissionDenied = data["isPermissionDenied"] as? Boolean ?: false,
      isNetworkError = data["isNetworkError"] as? Boolean ?: false,
    )
  }

  fun buildInitialProfile(
    uid: String,
    defaultDisplayName: String,
    defaultLanguage: String,
  ): RustProfilePlan {
    val data =
      dispatch(
        "build_initial_profile",
        mapOf(
          "uid" to uid,
          "default_display_name" to defaultDisplayName,
          "default_language" to defaultLanguage,
        ),
      )
        .asObj()
    return parseProfilePlan(data)
  }

  fun buildProfileUpdate(
    uid: String,
    displayName: String,
    username: String,
    bio: String,
    avatarColorHex: String,
    languagePref: String,
    darkModePref: Boolean,
    blockedUids: List<String> = emptyList(),
  ): RustProfilePlan {
    val data =
      dispatch(
        "build_profile_update",
        mapOf(
          "uid" to uid,
          "display_name" to displayName,
          "username" to username,
          "bio" to bio,
          "avatar_color_hex" to avatarColorHex,
          "language_pref" to languagePref,
          "dark_mode_pref" to darkModePref,
          "blocked_uids" to blockedUids,
        ),
      )
        .asObj()
    return parseProfilePlan(data)
  }

  private fun parseProfilePlan(data: Map<String, Any?>): RustProfilePlan {
    val p = data["profile"].asObj()
    return RustProfilePlan(
      uid = p["uid"].asStr(),
      username = p["username"].asStr(),
      displayName = p["displayName"].asStr(),
      bio = p["bio"].asStr(),
      avatarColorHex = p["avatarColorHex"].asStr("#D96B27"),
      languagePref = p["languagePref"].asStr("ru"),
      darkModePref = p["darkModePref"] as? Boolean ?: true,
      blockedUids = p["blockedUids"].asStrList(),
    )
  }

  fun normalizeLookupHandle(rawUsername: String): String? {
    return dispatch(
      "normalize_lookup_handle",
      mapOf("raw_username" to rawUsername),
    )
      as? String
  }

  fun buildContactEntry(
    ownerUid: String,
    contactUid: String,
    displayName: String,
    username: String,
    bio: String,
    avatarColorHex: String,
    isFavorite: Boolean,
    isBlocked: Boolean = false,
  ): RustContactPlan {
    val data =
      dispatch(
        "build_contact_entry",
        mapOf(
          "owner_uid" to ownerUid,
          "contact_uid" to contactUid,
          "display_name" to displayName,
          "username" to username,
          "bio" to bio,
          "avatar_color_hex" to avatarColorHex,
          "is_favorite" to isFavorite,
          "is_blocked" to isBlocked,
        ),
      )
        .asObj()
    return RustContactPlan(
      id = data["id"].asStr(),
      userId = data["userId"].asStr(),
      contactUid = data["contactUid"].asStr(),
      displayName = data["displayName"].asStr(),
      username = data["username"].asStr(),
      bio = data["bio"].asStr(),
      avatarColorHex = data["avatarColorHex"].asStr("#1D4E89"),
      isFavorite = data["isFavorite"] as? Boolean ?: false,
      isBlocked = data["isBlocked"] as? Boolean ?: false,
    )
  }

  fun sortContacts(contacts: List<ContactEntry>): List<ContactEntry> {
    if (contacts.size <= 1) return contacts
    val byId = contacts.associateBy { it.id.ifBlank { it.contactUid } }
    val wire =
      contacts.map { c ->
        mapOf(
          "id" to c.id.ifBlank { c.contactUid },
          "userId" to c.userId,
          "contactUid" to c.contactUid,
          "displayName" to c.displayName,
          "username" to c.username,
          "bio" to c.bio,
          "avatarColorHex" to c.avatarColorHex,
          "isFavorite" to c.isFavorite,
          "isBlocked" to c.isBlocked,
        )
      }
    val sortedWire = dispatch("sort_contacts", mapOf("contacts" to wire)).asObjList()
    return sortedWire.mapNotNull { item -> byId[item["id"].asStr()] }
  }

  fun canMessageContact(contacts: List<ContactEntry>, targetUid: String) {
    val wire =
      contacts.map { c ->
        mapOf(
          "id" to c.id,
          "userId" to c.userId,
          "contactUid" to c.contactUid,
          "displayName" to c.displayName,
          "username" to c.username,
          "bio" to c.bio,
          "avatarColorHex" to c.avatarColorHex,
          "isFavorite" to c.isFavorite,
          "isBlocked" to c.isBlocked,
        )
      }
    dispatch(
      "can_message_contact",
      mapOf("contacts" to wire, "target_uid" to targetUid),
    )
  }

  fun prepareSendMessage(
    uid: String,
    chatId: String,
    messageId: String,
    text: String,
    senderName: String,
    senderType: String,
    memberUids: List<String>,
    isPublic: Boolean,
    replyToMessageId: String,
    replyPreviewText: String,
    forwardedFromName: String,
    attachmentType: String,
    attachmentName: String,
    attachmentData: String,
    isSilent: Boolean,
  ): RustOutgoingMessagePlan {
    val data =
      dispatch(
        "prepare_send_message",
        mapOf(
          "uid" to uid,
          "chat_id" to chatId,
          "message_id" to messageId,
          "text" to text,
          "sender_name" to senderName,
          "sender_type" to senderType,
          "member_uids" to memberUids,
          "is_public" to isPublic,
          "reply_to_message_id" to replyToMessageId,
          "reply_preview_text" to replyPreviewText,
          "forwarded_from_name" to forwardedFromName,
          "attachment_type" to attachmentType,
          "attachment_name" to attachmentName,
          "attachment_data" to attachmentData,
          "is_silent" to isSilent,
        ),
      )
        .asObj()
    val msg = data["message"].asObj()
    return RustOutgoingMessagePlan(
      messageId = msg["id"].asStr(messageId),
      chatId = msg["chatId"].asStr(chatId),
      authorId = msg["authorId"].asStr(uid),
      senderType = msg["senderType"].asStr("user"),
      senderName = msg["senderName"].asStr(),
      memberUids = msg["memberUids"].asStrList(),
      isPublic = msg["isPublic"] as? Boolean ?: isPublic,
      text = msg["text"].asStr(),
      replyToMessageId = msg["replyToMessageId"].asStr(),
      replyPreviewText = msg["replyPreviewText"].asStr(),
      forwardedFromName = msg["forwardedFromName"].asStr(),
      attachmentType = msg["attachmentType"].asStr("none"),
      attachmentName = msg["attachmentName"].asStr(),
      attachmentData = msg["attachmentData"].asStr(),
      isSilent = msg["isSilent"] as? Boolean ?: false,
      lastMessagePreview = data["lastMessagePreview"].asStr(),
      lastMessageSender = data["lastMessageSender"].asStr(),
      integritySha256 = data["integritySha256"].asStr(),
    )
  }

  fun prepareEditMessage(uid: String, authorId: String, newText: String): String {
    return dispatch(
      "prepare_edit_message",
      mapOf("uid" to uid, "author_id" to authorId, "new_text" to newText),
    )
      .asStr()
  }

  fun toggleReaction(
    uid: String,
    reactions: Map<String, List<String>>,
    emoji: String,
  ): Map<String, List<String>> {
    val data =
      dispatch(
        "toggle_reaction",
        mapOf("uid" to uid, "reactions" to reactions, "emoji" to emoji),
      )
        .asObj()
    val result = LinkedHashMap<String, List<String>>()
    for ((k, v) in data) {
      result[k] = v.asStrList()
    }
    return result
  }

  fun buildChatRoom(
    uid: String,
    chatType: String,
    title: String,
    description: String,
    avatarColorHex: String,
    extraMemberUids: List<String>,
    isPublic: Boolean,
    botRoleId: String,
    botModelName: String,
    botSystemPrompt: String,
    initialPreview: String,
    initialSender: String,
    chatId: String,
  ): RustChatRoomPlan {
    val data =
      dispatch(
        "build_chat_room",
        mapOf(
          "uid" to uid,
          "chat_type" to chatType,
          "title" to title,
          "description" to description,
          "avatar_color_hex" to avatarColorHex,
          "extra_member_uids" to extraMemberUids,
          "is_public" to isPublic,
          "bot_role_id" to botRoleId,
          "bot_model_name" to botModelName,
          "bot_system_prompt" to botSystemPrompt,
          "initial_preview" to initialPreview,
          "initial_sender" to initialSender,
          "chat_id" to chatId,
        ),
      )
        .asObj()
    return RustChatRoomPlan(
      id = data["id"].asStr(chatId),
      ownerId = data["ownerId"].asStr(uid),
      chatType = data["chatType"].asStr(chatType),
      title = data["title"].asStr(),
      description = data["description"].asStr(),
      avatarColorHex = data["avatarColorHex"].asStr(avatarColorHex),
      memberUids = data["memberUids"].asStrList(),
      adminUids = data["adminUids"].asStrList(),
      isPublic = data["isPublic"] as? Boolean ?: false,
      inviteCode = data["inviteCode"].asStr(),
      botRoleId = data["botRoleId"].asStr(),
      botModelName = data["botModelName"].asStr(),
      botSystemPrompt = data["botSystemPrompt"].asStr(),
      lastMessagePreview = data["lastMessagePreview"].asStr(),
      lastMessageSender = data["lastMessageSender"].asStr(),
    )
  }

  fun joinPublicChannel(uid: String, chat: ChatRoom): List<String> {
    return dispatch(
      "join_public_channel",
      mapOf("uid" to uid, "chat" to chat.toWire()),
    )
      .asStrList()
  }

  fun leavePublicChannel(uid: String, chat: ChatRoom): List<String> {
    return dispatch(
      "leave_public_channel",
      mapOf("uid" to uid, "chat" to chat.toWire()),
    )
      .asStrList()
  }

  fun sortChats(chats: List<ChatRoom>): List<ChatRoom> {
    if (chats.size <= 1) return chats
    val byId = chats.associateBy { it.id }
    val result =
      dispatch(
        "sort_chats",
        mapOf("chats" to chats.map { it.toWire() }),
      )
        .asObjList()
    return result.mapNotNull { item -> byId[item["id"].asStr()] }
  }

  fun planReadReceipts(uid: String, messages: List<ChatMessage>): List<RustReadReceiptUpdate> {
    if (messages.isEmpty()) return emptyList()
    return dispatch(
      "plan_read_receipts",
      mapOf("uid" to uid, "messages" to messages.map { it.toWire() }),
    )
      .asObjList()
      .map { item ->
        RustReadReceiptUpdate(
          messageId = item["messageId"].asStr(),
          readByUids = item["readByUids"].asStrList(),
          viewCount = (item["viewCount"] as? Number)?.toLong() ?: 1L,
        )
      }
  }

  fun addGroupMember(uid: String, chat: ChatRoom, newMemberUid: String): List<String> {
    return dispatch(
      "add_group_member",
      mapOf(
        "uid" to uid,
        "chat" to chat.toWire(),
        "new_member_uid" to newMemberUid,
      ),
    )
      .asStrList()
  }

  fun toggleArchiveChat(uid: String, archivedByUids: List<String>, archive: Boolean): List<String> {
    return dispatch(
      "toggle_archive_chat",
      mapOf(
        "uid" to uid,
        "archived_by_uids" to archivedByUids,
        "archive" to archive,
      ),
    )
      .asStrList()
  }

  fun filterChatWorkspace(
    chats: List<ChatRoom>,
    publicChannels: List<ChatRoom>,
    currentUid: String,
    filter: String,
    query: String,
  ): RustChatFilterResult {
    val chatsById = chats.associateBy { it.id }
    val publicById = publicChannels.associateBy { it.id }
    val data =
      dispatch(
        "filter_chat_workspace",
        mapOf(
          "chats" to chats.map { it.toWire() },
          "public_channels" to publicChannels.map { it.toWire() },
          "current_uid" to currentUid,
          "filter" to filter,
          "query" to query,
        ),
      )
        .asObj()
    val filtered =
      data["filteredChats"].asObjList().mapNotNull { item -> chatsById[item["id"].asStr()] }
    val discoverable =
      data["discoverableChannels"].asObjList().mapNotNull { item ->
        publicById[item["id"].asStr()]
      }
    return RustChatFilterResult(filteredChats = filtered, discoverableChannels = discoverable)
  }

  fun searchMessages(messages: List<ChatMessage>, query: String): List<ChatMessage> {
    if (query.isBlank()) return messages
    val byId = messages.associateBy { it.id }
    val result =
      dispatch(
        "search_messages",
        mapOf(
          "messages" to messages.map { it.toWire() },
          "query" to query,
        ),
      )
        .asObjList()
    return result.mapNotNull { item -> byId[item["id"].asStr()] }
  }

  fun orderMessages(messages: List<ChatMessage>): List<ChatMessage> {
    if (messages.size <= 1) return messages
    val byId = messages.associateBy { it.id }
    val result =
      dispatch(
        "order_messages",
        mapOf("messages" to messages.map { it.toWire() }),
      )
        .asObjList()
    return result.mapNotNull { item -> byId[item["id"].asStr()] }
  }

  fun buildDraftsMap(entries: List<DraftEntry>): Map<String, String> {
    if (entries.isEmpty()) return emptyMap()
    val wire =
      entries.map { d ->
        mapOf(
          "id" to d.id,
          "userId" to d.userId,
          "chatId" to d.chatId,
          "text" to d.text,
          "updatedAtEpochMs" to d.updatedAt?.toDate()?.time,
        )
      }
    val data = dispatch("build_drafts_map", mapOf("entries" to wire)).asObj()
    val map = LinkedHashMap<String, String>()
    for ((k, v) in data) {
      map[k] = v.asStr()
    }
    return map
  }

  fun planDraftMutation(uid: String, chatId: String, text: String): RustDraftMutation {
    val data =
      dispatch(
        "plan_draft_mutation",
        mapOf("uid" to uid, "chat_id" to chatId, "text" to text),
      )
        .asObj()
    return when (data["action"].asStr()) {
      "delete" ->
        RustDraftMutation.Delete(
          userId = data["user_id"].asStr(uid),
          chatId = data["chat_id"].asStr(sanitizeId(chatId, "draft")),
        )
      else -> {
        val entry = data["entry"].asObj()
        RustDraftMutation.Upsert(
          userId = entry["userId"].asStr(uid),
          chatId = entry["chatId"].asStr(sanitizeId(chatId, "draft")),
          text = entry["text"].asStr(),
        )
      }
    }
  }

  fun buildCallLog(
    uid: String,
    callId: String,
    peerName: String,
    callType: String,
    status: String,
    durationSeconds: Long,
  ): RustCallLogPlan {
    val data =
      dispatch(
        "build_call_log",
        mapOf(
          "uid" to uid,
          "call_id" to callId,
          "peer_name" to peerName,
          "call_type" to callType,
          "status" to status,
          "duration_seconds" to durationSeconds,
        ),
      )
        .asObj()
    return RustCallLogPlan(
      id = data["id"].asStr(callId),
      userId = data["userId"].asStr(uid),
      peerName = data["peerName"].asStr("Rustgram Peer"),
      callType = data["callType"].asStr("audio"),
      status = data["status"].asStr("preview"),
      durationSeconds = (data["durationSeconds"] as? Number)?.toLong() ?: 0L,
    )
  }

  fun validateAttachment(
    attachmentType: String,
    attachmentName: String,
    attachmentData: String,
  ): RustValidatedAttachment {
    val data =
      dispatch(
        "validate_attachment",
        mapOf(
          "attachment_type" to attachmentType,
          "attachment_name" to attachmentName,
          "attachment_data" to attachmentData,
        ),
      )
        .asObj()
    return RustValidatedAttachment(
      attachmentType = data["attachmentType"].asStr("none"),
      attachmentName = data["attachmentName"].asStr(),
      attachmentData = data["attachmentData"].asStr(),
    )
  }

  fun formatDocumentSizeKb(sizeBytes: Long): String {
    return dispatch(
      "format_document_size",
      mapOf("size_bytes" to sizeBytes.coerceAtLeast(0L)),
    )
      .asStr("1 KB")
  }

  fun formatVoiceFilename(durationSecs: Long): String {
    return dispatch(
      "format_voice_filename",
      mapOf("duration_secs" to durationSecs.coerceAtLeast(0L)),
    )
      .asStr("voice_1s.m4a")
  }

  fun prepareGeminiRequest(
    requestedModel: String,
    systemPrompt: String,
    historyMessages: List<ChatMessage>,
    userPrompt: String,
    hasImage: Boolean,
  ): RustPreparedGeminiRequest {
    val data =
      dispatch(
        "prepare_gemini_request",
        mapOf(
          "requested_model" to requestedModel,
          "system_prompt" to systemPrompt,
          "history_messages" to historyMessages.map { it.toWire() },
          "user_prompt" to userPrompt,
          "has_image" to hasImage,
        ),
      )
        .asObj()
    val turns =
      data["history"].asObjList().map { t ->
        RustGeminiTurn(role = t["role"].asStr("user"), text = t["text"].asStr())
      }
    return RustPreparedGeminiRequest(
      modelName = data["modelName"].asStr("gemini-3.8-flash"),
      systemInstruction = data["systemInstruction"].asStr(),
      temperature = (data["temperature"] as? Number)?.toFloat() ?: 0.7f,
      history = turns,
      userPrompt = data["userPrompt"].asStr(),
    )
  }

  fun finalizeGeminiStream(accumulated: String): String {
    return dispatch(
      "finalize_gemini_stream",
      mapOf("accumulated" to accumulated),
    )
      .asStr()
  }

  fun getGeminiPresets(): List<GeminiBotPreset> {
    return dispatch("get_gemini_presets").asObjList().map { p ->
      GeminiBotPreset(
        roleId = p["roleId"].asStr(),
        titleRu = p["titleRu"].asStr(),
        titleEn = p["titleEn"].asStr(),
        subtitleRu = p["subtitleRu"].asStr(),
        subtitleEn = p["subtitleEn"].asStr(),
        defaultModel = p["defaultModel"].asStr("gemini-3.8-flash"),
        avatarColorHex = p["avatarColorHex"].asStr("#D96B27"),
        systemInstruction = p["systemInstruction"].asStr(),
      )
    }
  }

  fun getGeminiModels(): List<String> {
    return dispatch("get_gemini_models").asStrList()
  }

  fun deterministicIds(
    uid: String,
    peerUid: String? = null,
    roleId: String? = null,
  ): RustDeterministicIds {
    val data =
      dispatch(
        "deterministic_ids",
        mapOf("uid" to uid, "peer_uid" to peerUid, "role_id" to roleId),
      )
        .asObj()
    return RustDeterministicIds(
      savedChatId = data["savedChatId"] as? String,
      directChatId = data["directChatId"] as? String,
      botChatId = data["botChatId"] as? String,
      inviteSample = data["inviteSample"].asStr(),
    )
  }

  fun evaluateNotification(
    currentUid: String,
    authorId: String,
    senderName: String,
    chatTitle: String,
    messageText: String,
    isSilent: Boolean,
    blockedUids: List<String>,
  ): RustNotificationDecision {
    val data =
      dispatch(
        "evaluate_notification",
        mapOf(
          "current_uid" to currentUid,
          "author_id" to authorId,
          "sender_name" to senderName,
          "chat_title" to chatTitle,
          "message_text" to messageText,
          "is_silent" to isSilent,
          "blocked_uids" to blockedUids,
        ),
      )
        .asObj()
    return RustNotificationDecision(
      shouldNotify = data["shouldNotify"] as? Boolean ?: false,
      suppressReason = data["suppressReason"] as? String,
      title = data["title"].asStr(),
      preview = data["preview"].asStr(),
    )
  }

  fun cryptoDigest(payload: String): RustCryptoDigest {
    val data = dispatch("crypto_digest", mapOf("payload" to payload)).asObj()
    return RustCryptoDigest(
      sha256 = data["sha256"].asStr(),
      integrity = data["integrity"].asStr(),
    )
  }

  private fun ChatRoom.toWire(): Map<String, Any?> =
    mapOf(
      "id" to id,
      "ownerId" to ownerId,
      "chatType" to chatType,
      "title" to title,
      "description" to description,
      "avatarColorHex" to avatarColorHex,
      "memberUids" to memberUids,
      "adminUids" to adminUids,
      "isPublic" to isPublic,
      "inviteCode" to inviteCode,
      "pinnedMessageId" to pinnedMessageId,
      "pinnedPreview" to pinnedPreview,
      "archivedByUids" to archivedByUids,
      "botRoleId" to botRoleId,
      "botModelName" to botModelName,
      "botSystemPrompt" to botSystemPrompt,
      "lastMessagePreview" to lastMessagePreview,
      "lastMessageSender" to lastMessageSender,
      "lastMessageAtEpochMs" to lastMessageAt?.toDate()?.time,
      "createdAtEpochMs" to createdAt?.toDate()?.time,
    )

  private fun ChatMessage.toWire(): Map<String, Any?> =
    mapOf(
      "id" to id,
      "chatId" to chatId,
      "authorId" to authorId,
      "senderType" to senderType,
      "senderName" to senderName,
      "memberUids" to memberUids,
      "isPublic" to isPublic,
      "text" to text,
      "replyToMessageId" to replyToMessageId,
      "replyPreviewText" to replyPreviewText,
      "forwardedFromName" to forwardedFromName,
      "reactions" to reactions,
      "attachmentType" to attachmentType,
      "attachmentName" to attachmentName,
      "attachmentData" to "",
      "isSilent" to isSilent,
      "isEdited" to isEdited,
      "viewCount" to viewCount,
      "readByUids" to readByUids,
      "createdAtEpochMs" to createdAt?.toDate()?.time,
    )

  @Suppress("UNCHECKED_CAST")
  private fun Any?.asObj(): Map<String, Any?> =
    this as? Map<String, Any?> ?: emptyMap()

  private fun Any?.asStr(default: String = ""): String =
    this as? String ?: default

  private fun Any?.asInt(default: Int = 0): Int =
    (this as? Number)?.toInt() ?: default

  private fun Any?.asStrList(): List<String> =
    (this as? Iterable<*>)?.mapNotNull { it as? String } ?: emptyList()

  private fun Any?.asObjList(): List<Map<String, Any?>> =
    (this as? Iterable<*>)?.mapNotNull { item ->
      @Suppress("UNCHECKED_CAST")
      item as? Map<String, Any?>
    } ?: emptyList()
}
