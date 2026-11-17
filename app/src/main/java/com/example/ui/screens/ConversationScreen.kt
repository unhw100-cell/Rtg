package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.model.ChatMessage
import com.example.data.model.ChatRoom
import com.example.data.repository.GeminiModels
import com.example.media.MediaAttachmentHelper
import com.example.media.VoiceNotePlayer
import com.example.media.VoiceNoteRecorder
import com.example.rust.RustBridge
import com.example.ui.components.AvatarBadge
import com.example.ui.components.FullScreenPhotoDialog
import com.example.ui.components.MessageBubble
import com.example.ui.i18n.AppStrings
import com.example.ui.viewmodel.UiState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ConversationScreen(
  chat: ChatRoom,
  allChats: List<ChatRoom>,
  currentUserId: String,
  messagesState: UiState<List<ChatMessage>>,
  streamingBotReply: String?,
  isBotThinking: Boolean,
  replyingTo: ChatMessage?,
  editingMessage: ChatMessage?,
  forwardingMessage: ChatMessage?,
  silentMode: Boolean,
  initialDraft: String,
  strings: AppStrings,
  showBackButton: Boolean,
  onBack: () -> Unit,
  onSendMessage: (
    text: String,
    attachmentType: String,
    attachmentName: String,
    attachmentData: String,
    attachedBitmap: Bitmap?,
  ) -> Unit,
  onSaveDraft: (String) -> Unit,
  onSetReplyingTo: (ChatMessage?) -> Unit,
  onSetEditingMessage: (ChatMessage?) -> Unit,
  onSetForwardingMessage: (ChatMessage?) -> Unit,
  onForwardToChat: (ChatRoom) -> Unit,
  onToggleReaction: (ChatMessage, String) -> Unit,
  onPinOrUnpin: (ChatMessage?) -> Unit,
  onDeleteMessage: (String) -> Unit,
  onToggleSilentMode: () -> Unit,
  onUpdateBotConfig: (model: String, systemPrompt: String) -> Unit,
  onAddMemberByHandle: (rawUsername: String, onAdded: () -> Unit) -> Unit = { _, _ -> },
  onLeaveChannel: () -> Unit = {},
  onDeleteChat: () -> Unit = {},
) {
  BackHandler(enabled = showBackButton) { onBack() }

  val context = LocalContext.current
  val clipboardManager = LocalClipboardManager.current
  val listState = rememberLazyListState()

  var composerText by remember(chat.id) { mutableStateOf(initialDraft) }
  var showSearchInChat by remember(chat.id) { mutableStateOf(false) }
  var inChatQuery by remember(chat.id) { mutableStateOf("") }
  var selectedMessageForActions by remember { mutableStateOf<ChatMessage?>(null) }
  var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
  var showBotConfigDialog by remember { mutableStateOf(false) }
  var showRoomInfoDialog by remember { mutableStateOf(false) }

  var isRecordingVoice by remember { mutableStateOf(false) }
  var playingVoiceId by remember { mutableStateOf<String?>(null) }
  val recorder = remember(context) { VoiceNoteRecorder(context) }
  val player = remember(context) { VoiceNotePlayer(context) }

  DisposableEffect(Unit) {
    onDispose {
      player.stop()
    }
  }

  LaunchedEffect(editingMessage?.id) {
    if (editingMessage != null) {
      composerText = editingMessage.text
    }
  }

  val photoPickerLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) {
        val encoded = MediaAttachmentHelper.encodeImageToBase64(context, uri)
        if (encoded != null) {
          val bmp = MediaAttachmentHelper.decodeBase64Bitmap(encoded.second)
          onSendMessage(composerText, "image", encoded.first, encoded.second, bmp)
          composerText = ""
        }
      }
    }

  val documentPickerLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        val (docName, docSize) = MediaAttachmentHelper.queryDocumentSummary(context, uri)
        onSendMessage(composerText, "document", docName, docSize, null)
        composerText = ""
      }
    }

  val audioPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      if (granted && recorder.startRecording()) {
        isRecordingVoice = true
      }
    }

  val messagesList = (messagesState as? UiState.Success)?.data.orEmpty()
  LaunchedEffect(messagesList.size, streamingBotReply) {
    val totalCount = messagesList.size + (if (streamingBotReply != null) 1 else 0)
    if (totalCount > 0) {
      listState.animateScrollToItem(totalCount - 1)
    }
  }

  Scaffold(
    modifier = Modifier.fillMaxSize().imePadding(),
    topBar = {
      Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.statusBarsPadding()) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            if (showBackButton) {
              IconButton(
                onClick = {
                  onSaveDraft(composerText)
                  onBack()
                },
                modifier = Modifier.testTag("chat_back_button"),
              ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
              }
            }
            Row(
              modifier =
                Modifier.weight(1f)
                  .clip(RoundedCornerShape(12.dp))
                  .clickable {
                    if (chat.chatType == "ai_bot") showBotConfigDialog = true
                    else showRoomInfoDialog = true
                  }
                  .padding(4.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              AvatarBadge(
                title = chat.title,
                colorHex = chat.avatarColorHex,
                size = 40.dp,
                chatType = chat.chatType,
              )
              Spacer(modifier = Modifier.width(10.dp))
              Column {
                Text(
                  text = chat.title,
                  style = MaterialTheme.typography.titleMedium,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                val subtitle =
                  when {
                    isBotThinking -> strings.aiStreaming
                    chat.chatType == "ai_bot" ->
                      chat.botModelName.ifBlank { GeminiModels.GENERAL_FLASH }
                    else -> "${chat.memberUids.size} ${strings.membersCount}"
                  }
                Text(
                  text = subtitle,
                  style = MaterialTheme.typography.labelSmall,
                  color =
                    if (isBotThinking) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
            IconButton(
              onClick = {
                showSearchInChat = !showSearchInChat
                if (!showSearchInChat) inChatQuery = ""
              },
            ) {
              Icon(Icons.Filled.Search, contentDescription = strings.searchHint)
            }
            if (chat.chatType == "ai_bot") {
              IconButton(
                onClick = { showBotConfigDialog = true },
                modifier = Modifier.testTag("bot_config_button"),
              ) {
                Icon(Icons.Filled.Tune, contentDescription = strings.modelLabel)
              }
            }
          }

          AnimatedVisibility(visible = showSearchInChat) {
            OutlinedTextField(
              value = inChatQuery,
              onValueChange = { inChatQuery = it },
              placeholder = { Text(strings.searchHint) },
              singleLine = true,
              trailingIcon = {
                IconButton(onClick = {
                  inChatQuery = ""
                  showSearchInChat = false
                }) {
                  Icon(Icons.Filled.Close, contentDescription = strings.cancel)
                }
              },
              modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            )
          }

          if (chat.pinnedPreview.isNotBlank()) {
            Surface(
              color = MaterialTheme.colorScheme.surfaceVariant,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(
                  imageVector = Icons.Filled.PushPin,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = strings.pinnedLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                  )
                  Text(
                    text = chat.pinnedPreview,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
                IconButton(onClick = { onPinOrUnpin(null) }, modifier = Modifier.size(28.dp)) {
                  Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = strings.unpin,
                    modifier = Modifier.size(16.dp),
                  )
                }
              }
            }
          }
          HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        }
      }
    },
    bottomBar = {
      Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
          if (replyingTo != null || editingMessage != null) {
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .background(MaterialTheme.colorScheme.surfaceVariant)
                  .padding(horizontal = 14.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector =
                  if (editingMessage != null) Icons.Filled.Edit
                  else Icons.AutoMirrored.Filled.Reply,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text =
                    if (editingMessage != null) strings.edit
                    else "${strings.reply}: ${replyingTo?.senderName.orEmpty()}",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.primary,
                )
                Text(
                  text = (editingMessage?.text ?: replyingTo?.text).orEmpty(),
                  style = MaterialTheme.typography.bodyMedium,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              IconButton(
                onClick = {
                  onSetReplyingTo(null)
                  onSetEditingMessage(null)
                },
                modifier = Modifier.size(30.dp),
              ) {
                Icon(Icons.Filled.Close, contentDescription = strings.cancel)
              }
            }
          }

          if (isRecordingVoice) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                  modifier =
                    Modifier.size(10.dp)
                      .clip(CircleShape)
                      .background(MaterialTheme.colorScheme.error)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = strings.recordingVoice,
                  style = MaterialTheme.typography.titleMedium,
                  color = MaterialTheme.colorScheme.error,
                )
              }
              Button(
                onClick = {
                  isRecordingVoice = false
                  val recorded = recorder.stopAndEncode()
                  if (recorded != null) {
                    onSendMessage("", "audio", recorded.first, recorded.second, null)
                  }
                }
              ) {
                Icon(Icons.Filled.Stop, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(strings.stopRecording)
              }
            }
          } else {
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
              verticalAlignment = Alignment.Bottom,
            ) {
              IconButton(
                onClick = {
                  photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                  )
                },
                modifier = Modifier.testTag("attach_photo_button"),
              ) {
                Icon(
                  imageVector = Icons.Filled.Image,
                  contentDescription = strings.photoAttachment,
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              IconButton(
                onClick = { documentPickerLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier.testTag("attach_file_button"),
              ) {
                Icon(
                  imageVector = Icons.Filled.AttachFile,
                  contentDescription = strings.fileAttachment,
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              OutlinedTextField(
                value = composerText,
                onValueChange = {
                  composerText = it
                  onSaveDraft(it)
                },
                placeholder = { Text(strings.messagePlaceholder) },
                maxLines = 4,
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.weight(1f).testTag("message_input"),
              )
              IconButton(
                onClick = onToggleSilentMode,
                modifier = Modifier.testTag("silent_mode_button"),
              ) {
                Icon(
                  imageVector =
                    if (silentMode) Icons.Filled.NotificationsOff
                    else Icons.Filled.NotificationsActive,
                  contentDescription = strings.silentMode,
                  tint =
                    if (silentMode) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              if (composerText.isBlank() && editingMessage == null) {
                IconButton(
                  onClick = {
                    val granted =
                      ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO,
                      ) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                      if (recorder.startRecording()) isRecordingVoice = true
                    } else {
                      audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                  },
                  modifier = Modifier.testTag("record_voice_button"),
                ) {
                  Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = strings.voiceNote,
                    tint = MaterialTheme.colorScheme.primary,
                  )
                }
              } else {
                IconButton(
                  onClick = {
                    val textToSend = composerText
                    composerText = ""
                    onSendMessage(textToSend, "none", "", "", null)
                  },
                  modifier = Modifier.testTag("send_message_button"),
                ) {
                  Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = MaterialTheme.colorScheme.primary,
                  )
                }
              }
            }
          }
        }
      }
    },
  ) { innerPadding ->
    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(MaterialTheme.colorScheme.background)
          .padding(innerPadding)
    ) {
      when (messagesState) {
        is UiState.Loading -> {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
          }
        }
        is UiState.Error -> {
          Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center,
          ) {
            Text(text = messagesState.message, color = MaterialTheme.colorScheme.error)
          }
        }
        is UiState.Success -> {
          val filtered =
            remember(messagesState.data, inChatQuery) {
              RustBridge.searchMessages(messagesState.data, inChatQuery)
            }

          LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
          ) {
            items(filtered, key = { it.id }) { msg ->
              val isMine = msg.authorId == currentUserId && msg.senderType == "user"
              MessageBubble(
                message = msg,
                isMine = isMine,
                currentUserId = currentUserId,
                isChannel = chat.chatType == "channel",
                isPinned = chat.pinnedMessageId == msg.id,
                strings = strings,
                playingVoiceId = playingVoiceId,
                onToggleVoice = {
                  player.playOrToggle(msg.id, msg.attachmentData) {
                    playingVoiceId = player.playingMessageId
                  }
                  playingVoiceId = player.playingMessageId
                },
                onOpenImage = { bmp -> previewBitmap = bmp },
                onOpenActions = { selectedMessageForActions = msg },
                onToggleReaction = { emoji -> onToggleReaction(msg, emoji) },
              )
            }

            if (streamingBotReply != null) {
              item(key = "streaming_bot_reply") {
                Column(
                  modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                  horizontalAlignment = Alignment.Start,
                ) {
                  Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp),
                    modifier = Modifier.widthIn(max = 320.dp),
                  ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                      Text(
                        text = chat.title,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                      )
                      Spacer(modifier = Modifier.height(4.dp))
                      Text(
                        text = streamingBotReply.ifBlank { strings.aiStreaming },
                        style = MaterialTheme.typography.bodyLarge,
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  selectedMessageForActions?.let { msg ->
    ModalBottomSheet(onDismissRequest = { selectedMessageForActions = null }) {
      Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        FlowRow(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          listOf("👍", "🔥", "❤️", "🚀", "👏", "🤔").forEach { emoji ->
            Surface(
              shape = CircleShape,
              color = MaterialTheme.colorScheme.surfaceVariant,
              modifier =
                Modifier.clickable {
                  onToggleReaction(msg, emoji)
                  selectedMessageForActions = null
                },
            ) {
              Text(
                text = emoji,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
              )
            }
          }
        }
        HorizontalDivider()
        ListItem(
          headlineContent = { Text(strings.reply) },
          leadingContent = { Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null) },
          modifier =
            Modifier.clickable {
              onSetReplyingTo(msg)
              selectedMessageForActions = null
            },
        )
        ListItem(
          headlineContent = { Text(strings.forward) },
          leadingContent = { Icon(Icons.AutoMirrored.Filled.Forward, contentDescription = null) },
          modifier =
            Modifier.clickable {
              onSetForwardingMessage(msg)
              selectedMessageForActions = null
            },
        )
        ListItem(
          headlineContent = { Text(strings.copy) },
          leadingContent = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
          modifier =
            Modifier.clickable {
              clipboardManager.setText(AnnotatedString(msg.text))
              selectedMessageForActions = null
            },
        )
        ListItem(
          headlineContent = {
            Text(if (chat.pinnedMessageId == msg.id) strings.unpin else strings.pin)
          },
          leadingContent = { Icon(Icons.Filled.PushPin, contentDescription = null) },
          modifier =
            Modifier.clickable {
              onPinOrUnpin(msg)
              selectedMessageForActions = null
            },
        )
        if (msg.authorId == currentUserId && msg.senderType == "user") {
          ListItem(
            headlineContent = { Text(strings.edit) },
            leadingContent = { Icon(Icons.Filled.Edit, contentDescription = null) },
            modifier =
              Modifier.clickable {
                onSetEditingMessage(msg)
                selectedMessageForActions = null
              },
          )
          ListItem(
            headlineContent = { Text(strings.delete, color = MaterialTheme.colorScheme.error) },
            leadingContent = {
              Icon(
                Icons.Filled.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
              )
            },
            modifier =
              Modifier.clickable {
                onDeleteMessage(msg.id)
                selectedMessageForActions = null
              },
          )
        }
      }
    }
  }

  if (forwardingMessage != null) {
    AlertDialog(
      onDismissRequest = { onSetForwardingMessage(null) },
      title = { Text(strings.forward) },
      text = {
        LazyColumn(modifier = Modifier.height(260.dp)) {
          items(allChats, key = { it.id }) { target ->
            ListItem(
              headlineContent = { Text(target.title) },
              leadingContent = {
                AvatarBadge(
                  title = target.title,
                  colorHex = target.avatarColorHex,
                  size = 36.dp,
                  chatType = target.chatType,
                )
              },
              modifier = Modifier.clickable { onForwardToChat(target) },
            )
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { onSetForwardingMessage(null) }) { Text(strings.cancel) }
      },
    )
  }

  if (showBotConfigDialog) {
    var selectedModel by remember {
      mutableStateOf(chat.botModelName.ifBlank { GeminiModels.GENERAL_FLASH })
    }
    var promptText by remember { mutableStateOf(chat.botSystemPrompt) }

    AlertDialog(
      onDismissRequest = { showBotConfigDialog = false },
      title = { Text(chat.title) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
            value = promptText,
            onValueChange = { promptText = it },
            label = { Text(strings.systemInstructionLabel) },
            minLines = 3,
            maxLines = 6,
            modifier = Modifier.fillMaxWidth(),
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            onUpdateBotConfig(selectedModel, promptText)
            showBotConfigDialog = false
          }
        ) {
          Text(strings.save)
        }
      },
      dismissButton = {
        TextButton(onClick = { showBotConfigDialog = false }) { Text(strings.cancel) }
      },
    )
  }

  if (showRoomInfoDialog) {
    var memberHandleInput by remember(chat.id) { mutableStateOf("") }
    val isAdmin = currentUserId in chat.adminUids || chat.ownerId == currentUserId
    val canAddMembers = (chat.chatType == "group" || chat.chatType == "channel") && isAdmin
    val canLeavePublicChannel =
      chat.isPublic && chat.ownerId != currentUserId && currentUserId in chat.memberUids
    val canDeleteRoom = chat.ownerId == currentUserId && !chat.id.startsWith("saved_")

    AlertDialog(
      onDismissRequest = { showRoomInfoDialog = false },
      title = { Text(chat.title) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          if (chat.description.isNotBlank()) {
            Text(text = chat.description, style = MaterialTheme.typography.bodyMedium)
          }
          Text(
            text = "${chat.memberUids.size} ${strings.membersCount}",
            style = MaterialTheme.typography.labelMedium,
          )
          if (chat.inviteCode.isNotBlank()) {
            Text(
              text = "${strings.inviteCodeLabel}: ${chat.inviteCode}",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.primary,
            )
          }
          if (canAddMembers) {
            HorizontalDivider()
            OutlinedTextField(
              value = memberHandleInput,
              onValueChange = { memberHandleInput = it },
              label = { Text(strings.lookupHandleHint) },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("add_member_handle_input"),
            )
            Button(
              onClick = {
                val handle = memberHandleInput.trim()
                if (handle.isNotBlank()) {
                  onAddMemberByHandle(handle) {
                    memberHandleInput = ""
                  }
                }
              },
              enabled = memberHandleInput.trim().length >= 3,
              modifier = Modifier.fillMaxWidth().testTag("add_member_confirm_button"),
            ) {
              Text(strings.addContact)
            }
          }
          if (canLeavePublicChannel) {
            OutlinedButton(
              onClick = {
                showRoomInfoDialog = false
                onLeaveChannel()
              },
              modifier = Modifier.fillMaxWidth().testTag("leave_channel_button"),
            ) {
              Text(strings.unarchive.takeIf { false } ?: "Unsubscribe / Выйти")
            }
          }
          if (canDeleteRoom) {
            OutlinedButton(
              onClick = {
                showRoomInfoDialog = false
                onDeleteChat()
              },
              colors =
                ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
              modifier = Modifier.fillMaxWidth().testTag("delete_chat_room_button"),
            ) {
              Text(strings.delete)
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showRoomInfoDialog = false }) { Text(strings.cancel) }
      },
    )
  }

  previewBitmap?.let { bmp ->
    FullScreenPhotoDialog(bitmap = bmp, onDismiss = { previewBitmap = null })
  }
}
