package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
  @DocumentId val id: String = "",
  val uid: String = "",
  val displayName: String = "",
  val username: String = "",
  val bio: String = "",
  val avatarColorHex: String = "#D96B27",
  val languagePref: String = "ru",
  val darkModePref: Boolean = true,
  val blockedUids: List<String> = emptyList(),
  @ServerTimestamp val lastSeenAt: Timestamp? = null,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class HandleEntry(
  @DocumentId val id: String = "",
  val ownerUid: String = "",
  val username: String = "",
  val displayName: String = "",
  val bio: String = "",
  val avatarColorHex: String = "#1D4E89",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class ContactEntry(
  @DocumentId val id: String = "",
  val userId: String = "",
  val contactUid: String = "",
  val displayName: String = "",
  val username: String = "",
  val bio: String = "",
  val avatarColorHex: String = "#1D4E89",
  val isFavorite: Boolean = false,
  val isBlocked: Boolean = false,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class DraftEntry(
  @DocumentId val id: String = "",
  val userId: String = "",
  val chatId: String = "",
  val text: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class CallLogEntry(
  @DocumentId val id: String = "",
  val userId: String = "",
  val peerName: String = "",
  val callType: String = "audio",
  val status: String = "completed",
  val durationSeconds: Long = 0L,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class ChatRoom(
  @DocumentId val id: String = "",
  val ownerId: String = "",
  val chatType: String = "direct",
  val title: String = "",
  val description: String = "",
  val avatarColorHex: String = "#1D4E89",
  val memberUids: List<String> = emptyList(),
  val adminUids: List<String> = emptyList(),
  val isPublic: Boolean = false,
  val inviteCode: String = "",
  val pinnedMessageId: String = "",
  val pinnedPreview: String = "",
  val archivedByUids: List<String> = emptyList(),
  val botRoleId: String = "",
  val botModelName: String = "",
  val botSystemPrompt: String = "",
  val lastMessagePreview: String = "",
  val lastMessageSender: String = "",
  @ServerTimestamp val lastMessageAt: Timestamp? = null,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class ChatMessage(
  @DocumentId val id: String = "",
  val chatId: String = "",
  val authorId: String = "",
  val senderType: String = "user",
  val senderName: String = "",
  val memberUids: List<String> = emptyList(),
  val isPublic: Boolean = false,
  val text: String = "",
  val replyToMessageId: String = "",
  val replyPreviewText: String = "",
  val forwardedFromName: String = "",
  val reactions: Map<String, List<String>> = emptyMap(),
  val attachmentType: String = "none",
  val attachmentName: String = "",
  val attachmentData: String = "",
  val isSilent: Boolean = false,
  val isEdited: Boolean = false,
  val viewCount: Long = 1L,
  val readByUids: List<String> = emptyList(),
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

data class GeminiBotPreset(
  val roleId: String,
  val titleRu: String,
  val titleEn: String,
  val subtitleRu: String,
  val subtitleEn: String,
  val defaultModel: String,
  val avatarColorHex: String,
  val systemInstruction: String,
)
