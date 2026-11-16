package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.ChatMessage
import com.example.data.model.ChatRoom
import com.example.data.repository.GeminiModels
import com.example.media.MediaAttachmentHelper
import com.example.ui.i18n.AppStrings
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Locale

fun parseColorHex(hex: String, fallback: Color = Color(0xFFD96B27)): Color {
  return try {
    val clean = hex.removePrefix("#")
    val colorInt = clean.toLong(16)
    if (clean.length == 6) Color(colorInt or 0xFF000000L) else Color(colorInt)
  } catch (_: Exception) {
    fallback
  }
}

fun formatTimeShort(timestamp: Timestamp?): String {
  if (timestamp == null) return ""
  val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
  return sdf.format(timestamp.toDate())
}

@Composable
fun AvatarBadge(
  title: String,
  colorHex: String,
  size: Dp = 48.dp,
  chatType: String = "direct",
) {
  val bg = parseColorHex(colorHex)
  Box(
    modifier =
      Modifier.size(size)
        .clip(CircleShape)
        .background(bg.copy(alpha = 0.22f))
        .border(1.5.dp, bg, CircleShape),
    contentAlignment = Alignment.Center,
  ) {
    when (chatType) {
      "ai_bot" ->
        Icon(
          imageVector = Icons.Filled.AutoAwesome,
          contentDescription = null,
          tint = bg,
          modifier = Modifier.size(size * 0.48f),
        )
      "channel" ->
        Icon(
          imageVector = Icons.Filled.Campaign,
          contentDescription = null,
          tint = bg,
          modifier = Modifier.size(size * 0.5f),
        )
      "group" ->
        Icon(
          imageVector = Icons.Filled.Group,
          contentDescription = null,
          tint = bg,
          modifier = Modifier.size(size * 0.5f),
        )
      else -> {
        val initials =
          title.trim().split(" ").filter { it.isNotBlank() }.take(2).joinToString("") {
            it.first().uppercaseChar().toString()
          }.ifBlank { "R" }
        Text(
          text = initials,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = bg,
        )
      }
    }
  }
}

@Composable
fun ChatListItemCard(
  chat: ChatRoom,
  isSelected: Boolean,
  isArchived: Boolean,
  draftText: String,
  strings: AppStrings,
  onClick: () -> Unit,
  onToggleArchive: () -> Unit,
) {
  Surface(
    color =
      if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
      else MaterialTheme.colorScheme.surface,
    shape = RoundedCornerShape(16.dp),
    tonalElevation = if (isSelected) 2.dp else 0.dp,
    modifier =
      Modifier.fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 4.dp)
        .clip(RoundedCornerShape(16.dp))
        .clickable(onClick = onClick)
        .testTag("chat_card_${chat.id}"),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      AvatarBadge(
        title = chat.title,
        colorHex = chat.avatarColorHex,
        size = 50.dp,
        chatType = chat.chatType,
      )
      Spacer(modifier = Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = chat.title,
              style = MaterialTheme.typography.titleMedium,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f, fill = false),
            )
            if (chat.chatType == "ai_bot") {
              Spacer(modifier = Modifier.width(6.dp))
              Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(6.dp),
              ) {
                Text(
                  text = "AI",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                )
              }
            }
          }
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = formatTimeShort(chat.lastMessageAt),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (draftText.isNotBlank()) {
            Text(
              text = "${strings.draftPrefix} $draftText",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.primary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f),
            )
          } else {
            Text(
              text = chat.lastMessagePreview.ifBlank { chat.description },
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f),
            )
          }
          if (chat.pinnedMessageId.isNotBlank()) {
            Icon(
              imageVector = Icons.Filled.PushPin,
              contentDescription = strings.pinnedLabel,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(16.dp).padding(start = 2.dp),
            )
          }
          IconButton(
            onClick = onToggleArchive,
            modifier = Modifier.size(32.dp),
          ) {
            Icon(
              imageVector = if (isArchived) Icons.Filled.Unarchive else Icons.Filled.Archive,
              contentDescription = if (isArchived) strings.unarchive else strings.archive,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun MessageBubble(
  message: ChatMessage,
  isMine: Boolean,
  isChannel: Boolean,
  isPinned: Boolean,
  strings: AppStrings,
  playingVoiceId: String?,
  onToggleVoice: () -> Unit,
  onOpenImage: (Bitmap) -> Unit,
  onOpenActions: () -> Unit,
  onToggleReaction: (String) -> Unit,
) {
  val bubbleColor =
    when {
      message.senderType == "ai_bot" -> MaterialTheme.colorScheme.surfaceVariant
      isMine -> MaterialTheme.colorScheme.secondaryContainer
      else -> MaterialTheme.colorScheme.surface
    }
  val alignment = if (isMine) Alignment.End else Alignment.Start
  val bubbleShape =
    RoundedCornerShape(
      topStart = 18.dp,
      topEnd = 18.dp,
      bottomStart = if (isMine) 18.dp else 4.dp,
      bottomEnd = if (isMine) 4.dp else 18.dp,
    )

  val decodedBitmap =
    remember(message.id, message.attachmentType, message.attachmentData) {
      if (message.attachmentType == "image" && message.attachmentData.isNotBlank()) {
        MediaAttachmentHelper.decodeBase64Bitmap(message.attachmentData)
      } else null
    }

  Column(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
    horizontalAlignment = alignment,
  ) {
    Surface(
      color = bubbleColor,
      shape = bubbleShape,
      tonalElevation = 1.dp,
      modifier =
        Modifier.widthIn(max = 320.dp)
          .clip(bubbleShape)
          .combinedClickable(
            onClick = onOpenActions,
            onLongClick = onOpenActions,
          )
          .testTag("message_bubble_${message.id}"),
    ) {
      Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        if (!isMine || message.senderType == "ai_bot" || isChannel) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = message.senderName,
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.primary,
              fontWeight = FontWeight.SemiBold,
            )
            if (isPinned) {
              Spacer(modifier = Modifier.width(6.dp))
              Icon(
                imageVector = Icons.Filled.PushPin,
                contentDescription = strings.pinnedLabel,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(12.dp),
              )
            }
          }
          Spacer(modifier = Modifier.height(2.dp))
        }

        if (message.forwardedFromName.isNotBlank()) {
          Text(
            text = "${strings.forward}: ${message.forwardedFromName}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
          )
          Spacer(modifier = Modifier.height(3.dp))
        }

        if (message.replyPreviewText.isNotBlank()) {
          Surface(
            color = MaterialTheme.colorScheme.background.copy(alpha = 0.45f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
          ) {
            Row(modifier = Modifier.padding(6.dp)) {
              Box(
                modifier =
                  Modifier.width(3.dp)
                    .height(28.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = message.replyPreviewText,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }

        if (decodedBitmap != null) {
          Image(
            bitmap = decodedBitmap.asImageBitmap(),
            contentDescription = message.attachmentName,
            contentScale = ContentScale.Crop,
            modifier =
              Modifier.fillMaxWidth()
                .heightIn(max = 220.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable { onOpenImage(decodedBitmap) },
          )
          Spacer(modifier = Modifier.height(6.dp))
        }

        if (message.attachmentType == "document") {
          Surface(
            color = MaterialTheme.colorScheme.background.copy(alpha = 0.4f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
          ) {
            Row(
              modifier = Modifier.padding(8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = Icons.Filled.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
              )
              Spacer(modifier = Modifier.width(8.dp))
              Column {
                Text(
                  text = message.attachmentName,
                  style = MaterialTheme.typography.bodyMedium,
                  fontWeight = FontWeight.Medium,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                if (message.attachmentData.isNotBlank()) {
                  Text(
                    text = message.attachmentData,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          }
        }

        if (message.attachmentType == "audio") {
          val isPlaying = playingVoiceId == message.id
          Surface(
            color = MaterialTheme.colorScheme.background.copy(alpha = 0.45f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              IconButton(
                onClick = onToggleVoice,
                modifier = Modifier.size(36.dp),
              ) {
                Icon(
                  imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                  contentDescription = strings.voiceNote,
                  tint = MaterialTheme.colorScheme.primary,
                )
              }
              Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = message.attachmentName.ifBlank { strings.voiceNote },
                style = MaterialTheme.typography.labelMedium,
              )
            }
          }
        }

        if (message.text.isNotBlank()) {
          Text(
            text = message.text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
          )
        }

        if (message.reactions.isNotEmpty()) {
          Spacer(modifier = Modifier.height(6.dp))
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            message.reactions.forEach { (emoji, users) ->
              if (users.isNotEmpty()) {
                Surface(
                  color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier.clickable { onToggleReaction(emoji) },
                ) {
                  Text(
                    text = "$emoji ${users.size}",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                  )
                }
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(3.dp))
        Row(
          modifier = Modifier.align(Alignment.End),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          if (message.isSilent) {
            Icon(
              imageVector = Icons.Filled.NotificationsOff,
              contentDescription = strings.silentMode,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(12.dp),
            )
          }
          if (isChannel) {
            Icon(
              imageVector = Icons.Filled.Visibility,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(12.dp),
            )
            Text(
              text = message.viewCount.toString(),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          if (message.isEdited) {
            Text(
              text = strings.editedLabel,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Text(
            text = formatTimeShort(message.createdAt),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          if (isMine) {
            Icon(
              imageVector = Icons.Filled.DoneAll,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(14.dp),
            )
          }
        }
      }
    }
  }
}

@Composable
fun FullScreenPhotoDialog(
  bitmap: Bitmap,
  onDismiss: () -> Unit,
) {
  var scale by remember { mutableFloatStateOf(1f) }
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(Color.Black)
          .pointerInput(Unit) {
            detectTransformGestures { _, _, zoom, _ ->
              scale = (scale * zoom).coerceIn(1f, 4f)
            }
          }
    ) {
      Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "Photo preview",
        contentScale = ContentScale.Fit,
        modifier =
          Modifier.fillMaxSize()
            .graphicsLayer(scaleX = scale, scaleY = scale),
      )
      IconButton(
        onClick = onDismiss,
        modifier = Modifier.align(Alignment.TopEnd).padding(20.dp),
      ) {
        Icon(
          imageVector = Icons.Filled.Close,
          contentDescription = "Close",
          tint = Color.White,
        )
      }
    }
  }
}

@Composable
fun CreateRoomDialog(
  chatType: String,
  strings: AppStrings,
  onDismiss: () -> Unit,
  onCreate: (title: String, description: String, isPublic: Boolean) -> Unit,
) {
  var title by remember { mutableStateOf("") }
  var description by remember { mutableStateOf("") }
  var isPublic by remember { mutableStateOf(chatType == "channel") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text =
          when (chatType) {
            "channel" -> strings.newChannel
            "group" -> strings.newGroup
            else -> strings.newChat
          }
      )
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = title,
          onValueChange = { title = it },
          label = { Text(strings.titleLabel) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("create_room_title"),
        )
        OutlinedTextField(
          value = description,
          onValueChange = { description = it },
          label = { Text(strings.descriptionLabel) },
          maxLines = 3,
          modifier = Modifier.fillMaxWidth(),
        )
        if (chatType == "channel") {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { isPublic = !isPublic },
          ) {
            Checkbox(checked = isPublic, onCheckedChange = { isPublic = it })
            Text(
              text = strings.publicChannelLabel,
              style = MaterialTheme.typography.bodyMedium,
            )
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { if (title.isNotBlank()) onCreate(title.trim(), description.trim(), isPublic) },
        enabled = title.isNotBlank(),
        modifier = Modifier.testTag("create_room_confirm"),
      ) {
        Text(strings.create)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(strings.cancel) }
    },
  )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateBotDialog(
  strings: AppStrings,
  onDismiss: () -> Unit,
  onCreate: (title: String, model: String, systemPrompt: String) -> Unit,
) {
  var title by remember { mutableStateOf("") }
  var selectedModel by remember { mutableStateOf(GeminiModels.GENERAL_FLASH) }
  var systemPrompt by remember {
    mutableStateOf(
      "You are a concise, deeply technical Rustgram AI assistant. Respond in the user's language."
    )
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(strings.newAiBot) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = title,
          onValueChange = { title = it },
          label = { Text(strings.titleLabel) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("create_bot_title"),
        )
        Text(text = strings.modelLabel, style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          GeminiModels.availableModels.forEach { model ->
            FilterChip(
              selected = selectedModel == model,
              onClick = { selectedModel = model },
              label = { Text(model, style = MaterialTheme.typography.labelSmall) },
            )
          }
        }
        OutlinedTextField(
          value = systemPrompt,
          onValueChange = { systemPrompt = it },
          label = { Text(strings.systemInstructionLabel) },
          minLines = 3,
          maxLines = 5,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (title.isNotBlank()) {
            onCreate(title.trim(), selectedModel, systemPrompt.trim())
          }
        },
        enabled = title.isNotBlank(),
        modifier = Modifier.testTag("create_bot_confirm"),
      ) {
        Text(strings.create)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(strings.cancel) }
    },
  )
}
