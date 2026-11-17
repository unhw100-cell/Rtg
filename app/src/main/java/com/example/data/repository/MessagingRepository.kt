package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.CallLogEntry
import com.example.data.model.ChatMessage
import com.example.data.model.ChatRoom
import com.example.data.model.ContactEntry
import com.example.data.model.DraftEntry
import com.example.data.model.HandleEntry
import com.example.data.model.UserProfile
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.example.data.remote.unwrapFirestoreException
import com.example.rust.RustBridge
import com.example.rust.RustDraftMutation
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class MessagingRepository(
  private val db: FirebaseFirestore,
) {
  constructor(context: Context) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    )
  )

  private val auth = Firebase.auth

  private fun requireUserId(): String {
    return auth.currentUser?.uid
      ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
  }

  private fun sanitizeId(raw: String, fallback: String = UUID.randomUUID().toString()): String {
    return RustBridge.sanitizeId(raw, fallback)
  }

  // --- USER PROFILE & HANDLES ---

  fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
    val path = "users/$userId"
    emitAll(
      db.collection("users")
        .document(userId)
        .snapshots()
        .map { snap -> snap.toObject(UserProfile::class.java, ServerTimestampBehavior.ESTIMATE) }
        .catch { error ->
          val firestoreError = error.unwrapFirestoreException()
          if (firestoreError == null && error is CancellationException) throw error
          val toLog = firestoreError ?: (error as? Exception)
          if (toLog != null && auth.currentUser?.uid == userId) {
            handleFirestoreError(toLog, OperationType.GET, path)
          }
          throw error
        }
    )
  }

  suspend fun ensureUserProfileInitialized(
    uid: String,
    defaultDisplayName: String,
    defaultLanguage: String = "ru",
  ): UserProfile {
    val userRef = db.collection("users").document(uid)
    try {
      val existing = userRef.get().await()
      if (existing.exists()) {
        val profile = existing.toObject(UserProfile::class.java, ServerTimestampBehavior.ESTIMATE)
        if (profile != null) {
          userRef
            .update(
              mapOf(
                "lastSeenAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
              )
            )
            .await()
          return profile
        }
      }
      val plan = RustBridge.buildInitialProfile(uid, defaultDisplayName, defaultLanguage)
      val payload =
        mapOf(
          "uid" to plan.uid,
          "displayName" to plan.displayName,
          "username" to plan.username,
          "bio" to plan.bio,
          "avatarColorHex" to plan.avatarColorHex,
          "languagePref" to plan.languagePref,
          "darkModePref" to plan.darkModePref,
          "blockedUids" to plan.blockedUids,
          "lastSeenAt" to FieldValue.serverTimestamp(),
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      userRef.set(payload).await()
      registerHandle(plan.uid, plan.username, plan.displayName, plan.bio, plan.avatarColorHex)
      return UserProfile(
        id = plan.uid,
        uid = plan.uid,
        displayName = plan.displayName,
        username = plan.username,
        bio = plan.bio,
        avatarColorHex = plan.avatarColorHex,
        languagePref = plan.languagePref,
        darkModePref = plan.darkModePref,
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, userRef.path)
      throw e
    }
  }

  suspend fun updateProfile(
    displayName: String,
    username: String,
    bio: String,
    avatarColorHex: String,
    languagePref: String,
    darkModePref: Boolean,
  ) {
    val uid = requireUserId()
    val userRef = db.collection("users").document(uid)
    try {
      val currentSnap = userRef.get().await()
      val previousUsername = currentSnap.getString("username").orEmpty()
      val existingBlocked =
        (currentSnap.get("blockedUids") as? List<*>)
          ?.mapNotNull { it as? String }
          .orEmpty()
      val plan =
        RustBridge.buildProfileUpdate(
          uid = uid,
          displayName = displayName,
          username = username,
          bio = bio,
          avatarColorHex = avatarColorHex,
          languagePref = languagePref,
          darkModePref = darkModePref,
          blockedUids = existingBlocked,
        )

      val newHandleRef = db.collection("handles").document(plan.username)
      val existingHandle = newHandleRef.get().await()
      if (existingHandle.exists() && existingHandle.getString("ownerUid") != uid) {
        throw IllegalStateException("@${plan.username} is already taken by another user.")
      }

      val payload =
        mapOf(
          "displayName" to plan.displayName,
          "username" to plan.username,
          "bio" to plan.bio,
          "avatarColorHex" to plan.avatarColorHex,
          "languagePref" to plan.languagePref,
          "darkModePref" to plan.darkModePref,
          "lastSeenAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      userRef.update(payload).await()
      registerHandle(plan.uid, plan.username, plan.displayName, plan.bio, plan.avatarColorHex)

      if (previousUsername.isNotBlank() && previousUsername != plan.username) {
        val oldHandleId = sanitizeId(previousUsername, "")
        if (oldHandleId.isNotBlank() && oldHandleId != plan.username) {
          val oldRef = db.collection("handles").document(oldHandleId)
          val oldSnap = oldRef.get().await()
          if (oldSnap.exists() && oldSnap.getString("ownerUid") == uid) {
            oldRef.delete().await()
          }
        }
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, userRef.path)
      throw e
    }
  }

  private suspend fun registerHandle(
    uid: String,
    username: String,
    displayName: String,
    bio: String,
    avatarColorHex: String,
  ) {
    val handleId = sanitizeId(username)
    val ref = db.collection("handles").document(handleId)
    try {
      val snap = ref.get().await()
      if (!snap.exists()) {
        ref
          .set(
            mapOf(
              "ownerUid" to uid,
              "username" to handleId,
              "displayName" to displayName,
              "bio" to bio,
              "avatarColorHex" to avatarColorHex,
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
      } else if (snap.getString("ownerUid") == uid) {
        ref
          .update(
            mapOf(
              "displayName" to displayName,
              "bio" to bio,
              "avatarColorHex" to avatarColorHex,
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, ref.path)
    }
  }

  suspend fun lookupHandle(rawUsername: String): HandleEntry? {
    val handleId = RustBridge.normalizeLookupHandle(rawUsername) ?: return null
    val ref = db.collection("handles").document(handleId)
    return try {
      val snap = ref.get().await()
      if (snap.exists()) snap.toObject(HandleEntry::class.java, ServerTimestampBehavior.ESTIMATE)
      else null
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, ref.path)
      null
    }
  }

  // --- CONTACTS ---

  fun observeContacts(userId: String): Flow<List<ContactEntry>> = flow {
    val path = "users/$userId/contacts"
    emitAll(
      db.collection("users")
        .document(userId)
        .collection("contacts")
        .snapshots()
        .map { snap ->
          val raw = snap.toObjects(ContactEntry::class.java, ServerTimestampBehavior.ESTIMATE)
          RustBridge.sortContacts(raw)
        }
        .catch { error ->
          val firestoreError = error.unwrapFirestoreException()
          if (firestoreError == null && error is CancellationException) throw error
          val toLog = firestoreError ?: (error as? Exception)
          if (toLog != null && auth.currentUser?.uid == userId) {
            handleFirestoreError(toLog, OperationType.LIST, path)
          }
          throw error
        }
    )
  }

  suspend fun upsertContact(
    contactUid: String,
    displayName: String,
    username: String,
    bio: String = "",
    avatarColorHex: String = "#1D4E89",
    isFavorite: Boolean = false,
  ): String {
    val uid = requireUserId()
    val plan =
      RustBridge.buildContactEntry(
        ownerUid = uid,
        contactUid = contactUid,
        displayName = displayName,
        username = username,
        bio = bio,
        avatarColorHex = avatarColorHex,
        isFavorite = isFavorite,
      )
    val ref = db.collection("users").document(uid).collection("contacts").document(plan.id)
    try {
      val existing = ref.get().await()
      if (existing.exists()) {
        ref
          .update(
            mapOf(
              "displayName" to plan.displayName,
              "username" to plan.username,
              "bio" to plan.bio,
              "avatarColorHex" to plan.avatarColorHex,
              "isFavorite" to plan.isFavorite,
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
      } else {
        ref
          .set(
            mapOf(
              "userId" to plan.userId,
              "contactUid" to plan.contactUid,
              "displayName" to plan.displayName,
              "username" to plan.username,
              "bio" to plan.bio,
              "avatarColorHex" to plan.avatarColorHex,
              "isFavorite" to plan.isFavorite,
              "isBlocked" to plan.isBlocked,
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
      }
      return plan.id
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, ref.path)
      throw e
    }
  }

  suspend fun toggleContactBlocked(contactId: String, contactUid: String, blocked: Boolean) {
    val uid = requireUserId()
    val cleanContactId = sanitizeId(contactId, "contact")
    val contactRef = db.collection("users").document(uid).collection("contacts").document(cleanContactId)
    val userRef = db.collection("users").document(uid)
    try {
      contactRef
        .update(mapOf("isBlocked" to blocked, "updatedAt" to FieldValue.serverTimestamp()))
        .await()
      if (contactUid.isNotBlank()) {
        val cleanPeerUid = sanitizeId(contactUid, "")
        if (cleanPeerUid.isNotBlank()) {
          userRef
            .update(
              mapOf(
                "blockedUids" to
                  if (blocked) FieldValue.arrayUnion(cleanPeerUid)
                  else FieldValue.arrayRemove(cleanPeerUid),
                "updatedAt" to FieldValue.serverTimestamp(),
              )
            )
            .await()
        }
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, contactRef.path)
      throw e
    }
  }

  // --- CHATS, GROUPS, CHANNELS, AI BOTS ---

  fun observeUserChats(userId: String): Flow<List<ChatRoom>> = flow {
    val path = "chats"
    emitAll(
      db.collection("chats")
        .whereArrayContains("memberUids", userId)
        .snapshots()
        .map { snap ->
          val rooms = snap.toObjects(ChatRoom::class.java, ServerTimestampBehavior.ESTIMATE)
          RustBridge.sortChats(rooms)
        }
        .catch { error ->
          val firestoreError = error.unwrapFirestoreException()
          if (firestoreError == null && error is CancellationException) throw error
          val toLog = firestoreError ?: (error as? Exception)
          if (toLog != null && auth.currentUser?.uid == userId) {
            handleFirestoreError(toLog, OperationType.LIST, path)
          }
          throw error
        }
    )
  }

  fun observePublicChannels(userId: String): Flow<List<ChatRoom>> = flow {
    val path = "chats"
    emitAll(
      db.collection("chats")
        .whereEqualTo("isPublic", true)
        .snapshots()
        .map { snap ->
          snap.toObjects(ChatRoom::class.java, ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.memberUids.size }
        }
        .catch { error ->
          val firestoreError = error.unwrapFirestoreException()
          if (firestoreError == null && error is CancellationException) throw error
          val toLog = firestoreError ?: (error as? Exception)
          if (toLog != null && auth.currentUser?.uid == userId) {
            handleFirestoreError(toLog, OperationType.LIST, path)
          }
          throw error
        }
    )
  }

  suspend fun createChatRoom(
    chatType: String,
    title: String,
    description: String = "",
    avatarColorHex: String = "#1D4E89",
    extraMemberUids: List<String> = emptyList(),
    isPublic: Boolean = false,
    botRoleId: String = "",
    botModelName: String = "",
    botSystemPrompt: String = "",
    initialWelcomeMessage: String? = null,
    initialSenderName: String? = null,
    initialSenderType: String = "user",
    fixedChatId: String? = null,
  ): String {
    val uid = requireUserId()
    val rawChatId = fixedChatId ?: UUID.randomUUID().toString()
    val plan =
      RustBridge.buildChatRoom(
        uid = uid,
        chatType = chatType,
        title = title,
        description = description,
        avatarColorHex = avatarColorHex,
        extraMemberUids = extraMemberUids,
        isPublic = isPublic,
        botRoleId = botRoleId,
        botModelName = botModelName,
        botSystemPrompt = botSystemPrompt,
        initialPreview = initialWelcomeMessage.orEmpty(),
        initialSender = initialSenderName.orEmpty(),
        chatId = rawChatId,
      )
    val chatRef = db.collection("chats").document(plan.id)
    val payload =
      mapOf(
        "ownerId" to plan.ownerId,
        "chatType" to plan.chatType,
        "title" to plan.title,
        "description" to plan.description,
        "avatarColorHex" to plan.avatarColorHex,
        "memberUids" to plan.memberUids,
        "adminUids" to plan.adminUids,
        "isPublic" to plan.isPublic,
        "inviteCode" to plan.inviteCode,
        "pinnedMessageId" to "",
        "pinnedPreview" to "",
        "archivedByUids" to emptyList<String>(),
        "botRoleId" to plan.botRoleId,
        "botModelName" to plan.botModelName,
        "botSystemPrompt" to plan.botSystemPrompt,
        "lastMessagePreview" to plan.lastMessagePreview,
        "lastMessageSender" to plan.lastMessageSender,
        "lastMessageAt" to FieldValue.serverTimestamp(),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    try {
      val existing = chatRef.get().await()
      if (!existing.exists()) {
        chatRef.set(payload).await()
        if (!initialWelcomeMessage.isNullOrBlank()) {
          sendMessage(
            chatId = plan.id,
            text = initialWelcomeMessage,
            senderName = initialSenderName ?: plan.title,
            senderType = initialSenderType,
            memberUids = plan.memberUids,
            isPublic = plan.isPublic,
          )
        }
      }
      return plan.id
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, chatRef.path)
      throw e
    }
  }

  suspend fun joinPublicChannel(chat: ChatRoom) {
    val uid = requireUserId()
    val updatedMembers = RustBridge.joinPublicChannel(uid, chat)
    if (updatedMembers == chat.memberUids) return
    val ref = db.collection("chats").document(chat.id)
    try {
      ref
        .update(
          mapOf(
            "memberUids" to updatedMembers,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, ref.path)
      throw e
    }
  }

  suspend fun leavePublicChannel(chat: ChatRoom) {
    val uid = requireUserId()
    val updatedMembers = RustBridge.leavePublicChannel(uid, chat)
    if (updatedMembers == chat.memberUids) return
    val ref = db.collection("chats").document(chat.id)
    try {
      ref
        .update(
          mapOf(
            "memberUids" to updatedMembers,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, ref.path)
      throw e
    }
  }

  suspend fun deleteChatRoom(chat: ChatRoom) {
    val uid = requireUserId()
    if (chat.ownerId != uid) {
      throw IllegalStateException("Only the chat owner can delete this room.")
    }
    val ref = db.collection("chats").document(chat.id)
    try {
      ref.delete().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, ref.path)
      throw e
    }
  }

  suspend fun toggleArchiveChat(chat: ChatRoom, archive: Boolean) {
    val uid = requireUserId()
    val updated = RustBridge.toggleArchiveChat(uid, chat.archivedByUids, archive)
    val ref = db.collection("chats").document(chat.id)
    try {
      ref
        .update(
          mapOf(
            "archivedByUids" to updated,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, ref.path)
      throw e
    }
  }

  suspend fun updateBotConfiguration(chatId: String, botModelName: String, botSystemPrompt: String) {
    requireUserId()
    val prepared =
      RustBridge.prepareGeminiRequest(
        requestedModel = botModelName,
        systemPrompt = botSystemPrompt,
        historyMessages = emptyList(),
        userPrompt = "config_check",
        hasImage = false,
      )
    val ref = db.collection("chats").document(chatId)
    try {
      ref
        .update(
          mapOf(
            "botModelName" to prepared.modelName,
            "botSystemPrompt" to prepared.systemInstruction,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, ref.path)
      throw e
    }
  }

  suspend fun pinMessage(chatId: String, messageId: String, preview: String) {
    requireUserId()
    val safeMsgId = if (messageId.isBlank()) "" else sanitizeId(messageId, messageId)
    val ref = db.collection("chats").document(chatId)
    try {
      ref
        .update(
          mapOf(
            "pinnedMessageId" to safeMsgId.take(128),
            "pinnedPreview" to preview.take(200),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, ref.path)
      throw e
    }
  }

  suspend fun addGroupMember(chat: ChatRoom, newMemberUid: String) {
    val uid = requireUserId()
    val updated = RustBridge.addGroupMember(uid, chat, newMemberUid)
    if (updated == chat.memberUids) return
    val ref = db.collection("chats").document(chat.id)
    try {
      ref.update(mapOf("memberUids" to updated, "updatedAt" to FieldValue.serverTimestamp())).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, ref.path)
      throw e
    }
  }

  // --- MESSAGES ---

  fun observeMessages(chatId: String, userId: String, isPublicChannel: Boolean): Flow<List<ChatMessage>> = flow {
    val path = "chats/$chatId/messages"
    val query =
      if (isPublicChannel) {
        db.collection("chats").document(chatId).collection("messages").whereEqualTo("isPublic", true)
      } else {
        db.collection("chats")
          .document(chatId)
          .collection("messages")
          .whereArrayContains("memberUids", userId)
      }
    emitAll(
      query
        .snapshots()
        .map { snap ->
          val msgs = snap.toObjects(ChatMessage::class.java, ServerTimestampBehavior.ESTIMATE)
          RustBridge.orderMessages(msgs)
        }
        .catch { error ->
          val firestoreError = error.unwrapFirestoreException()
          if (firestoreError == null && error is CancellationException) throw error
          val toLog = firestoreError ?: (error as? Exception)
          if (toLog != null && auth.currentUser?.uid == userId) {
            handleFirestoreError(toLog, OperationType.LIST, path)
          }
          throw error
        }
    )
  }

  suspend fun sendMessage(
    chatId: String,
    text: String,
    senderName: String,
    senderType: String = "user",
    memberUids: List<String>,
    isPublic: Boolean = false,
    replyToMessageId: String = "",
    replyPreviewText: String = "",
    forwardedFromName: String = "",
    attachmentType: String = "none",
    attachmentName: String = "",
    attachmentData: String = "",
    isSilent: Boolean = false,
  ): String {
    val uid = requireUserId()
    val rawMessageId = UUID.randomUUID().toString()
    val plan =
      RustBridge.prepareSendMessage(
        uid = uid,
        chatId = chatId,
        messageId = rawMessageId,
        text = text,
        senderName = senderName,
        senderType = senderType,
        memberUids = memberUids,
        isPublic = isPublic,
        replyToMessageId = replyToMessageId,
        replyPreviewText = replyPreviewText,
        forwardedFromName = forwardedFromName,
        attachmentType = attachmentType,
        attachmentName = attachmentName,
        attachmentData = attachmentData,
        isSilent = isSilent,
      )
    val msgRef = db.collection("chats").document(plan.chatId).collection("messages").document(plan.messageId)
    val chatRef = db.collection("chats").document(plan.chatId)
    val payload =
      mapOf(
        "chatId" to plan.chatId,
        "authorId" to plan.authorId,
        "senderType" to plan.senderType,
        "senderName" to plan.senderName,
        "memberUids" to plan.memberUids,
        "isPublic" to plan.isPublic,
        "text" to plan.text,
        "replyToMessageId" to plan.replyToMessageId,
        "replyPreviewText" to plan.replyPreviewText,
        "forwardedFromName" to plan.forwardedFromName,
        "reactions" to emptyMap<String, List<String>>(),
        "attachmentType" to plan.attachmentType,
        "attachmentName" to plan.attachmentName,
        "attachmentData" to plan.attachmentData,
        "isSilent" to plan.isSilent,
        "isEdited" to false,
        "viewCount" to 1L,
        "readByUids" to listOf(plan.authorId),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    try {
      msgRef.set(payload).await()
      chatRef
        .update(
          mapOf(
            "lastMessagePreview" to plan.lastMessagePreview,
            "lastMessageSender" to plan.lastMessageSender,
            "lastMessageAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      return plan.messageId
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, msgRef.path)
      throw e
    }
  }

  suspend fun editMessage(
    chatId: String,
    messageId: String,
    newText: String,
    authorId: String? = null,
  ) {
    val uid = requireUserId()
    val cleanText = RustBridge.prepareEditMessage(uid, authorId ?: uid, newText)
    val msgRef = db.collection("chats").document(chatId).collection("messages").document(messageId)
    try {
      msgRef
        .update(
          mapOf(
            "text" to cleanText,
            "isEdited" to true,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, msgRef.path)
      throw e
    }
  }

  suspend fun toggleReaction(chatId: String, message: ChatMessage, emoji: String) {
    val uid = requireUserId()
    val updatedReactions = RustBridge.toggleReaction(uid, message.reactions, emoji)
    val msgRef = db.collection("chats").document(chatId).collection("messages").document(message.id)
    try {
      msgRef
        .update(
          mapOf(
            "reactions" to updatedReactions,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, msgRef.path)
      throw e
    }
  }

  suspend fun markMessagesRead(chatId: String, messages: List<ChatMessage>) {
    val uid = requireUserId()
    val updates = RustBridge.planReadReceipts(uid, messages)
    if (updates.isEmpty()) return
    val col = db.collection("chats").document(chatId).collection("messages")
    val batch = db.batch()
    for (u in updates.take(25)) {
      batch.update(
        col.document(u.messageId),
        mapOf(
          "readByUids" to u.readByUids,
          "viewCount" to u.viewCount,
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
    }
    try {
      batch.commit().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, col.path)
    }
  }

  suspend fun deleteMessage(chatId: String, messageId: String) {
    requireUserId()
    val msgRef = db.collection("chats").document(chatId).collection("messages").document(messageId)
    try {
      msgRef.delete().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, msgRef.path)
      throw e
    }
  }

  // --- DRAFTS ---

  fun observeDrafts(userId: String): Flow<Map<String, String>> = flow {
    val path = "users/$userId/drafts"
    emitAll(
      db.collection("users")
        .document(userId)
        .collection("drafts")
        .snapshots()
        .map { snap ->
          val entries = snap.toObjects(DraftEntry::class.java, ServerTimestampBehavior.ESTIMATE)
          RustBridge.buildDraftsMap(entries)
        }
        .catch { error ->
          val firestoreError = error.unwrapFirestoreException()
          if (firestoreError == null && error is CancellationException) throw error
          val toLog = firestoreError ?: (error as? Exception)
          if (toLog != null && auth.currentUser?.uid == userId) {
            handleFirestoreError(toLog, OperationType.LIST, path)
          }
          throw error
        }
    )
  }

  suspend fun saveDraft(chatId: String, text: String) {
    val uid = requireUserId()
    val plan = RustBridge.planDraftMutation(uid, chatId, text)
    val targetChatId =
      when (plan) {
        is RustDraftMutation.Delete -> plan.chatId
        is RustDraftMutation.Upsert -> plan.chatId
      }
    val ref = db.collection("users").document(uid).collection("drafts").document(targetChatId)
    try {
      when (plan) {
        is RustDraftMutation.Delete -> {
          val snap = ref.get().await()
          if (snap.exists()) ref.delete().await()
        }
        is RustDraftMutation.Upsert -> {
          val snap = ref.get().await()
          if (snap.exists()) {
            ref
              .update(
                mapOf(
                  "text" to plan.text,
                  "updatedAt" to FieldValue.serverTimestamp(),
                )
              )
              .await()
          } else {
            ref
              .set(
                mapOf(
                  "userId" to plan.userId,
                  "chatId" to plan.chatId,
                  "text" to plan.text,
                  "createdAt" to FieldValue.serverTimestamp(),
                  "updatedAt" to FieldValue.serverTimestamp(),
                )
              )
              .await()
          }
        }
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, ref.path)
    }
  }

  // --- CALL LOGS ---

  fun observeCallLogs(userId: String): Flow<List<CallLogEntry>> = flow {
    val path = "users/$userId/call_logs"
    emitAll(
      db.collection("users")
        .document(userId)
        .collection("call_logs")
        .snapshots()
        .map { snap ->
          snap.toObjects(CallLogEntry::class.java, ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
        }
        .catch { error ->
          val firestoreError = error.unwrapFirestoreException()
          if (firestoreError == null && error is CancellationException) throw error
          val toLog = firestoreError ?: (error as? Exception)
          if (toLog != null && auth.currentUser?.uid == userId) {
            handleFirestoreError(toLog, OperationType.LIST, path)
          }
          throw error
        }
    )
  }

  suspend fun logCallSession(
    peerName: String,
    callType: String,
    status: String,
    durationSeconds: Long,
  ) {
    val uid = requireUserId()
    val rawId = UUID.randomUUID().toString()
    val plan =
      RustBridge.buildCallLog(
        uid = uid,
        callId = rawId,
        peerName = peerName,
        callType = callType,
        status = status,
        durationSeconds = durationSeconds,
      )
    val ref = db.collection("users").document(uid).collection("call_logs").document(plan.id)
    try {
      ref
        .set(
          mapOf(
            "userId" to plan.userId,
            "peerName" to plan.peerName,
            "callType" to plan.callType,
            "status" to plan.status,
            "durationSeconds" to plan.durationSeconds,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, ref.path)
      throw e
    }
  }
}
