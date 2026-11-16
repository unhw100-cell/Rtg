package com.example.ui.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.CallLogEntry
import com.example.data.model.ChatMessage
import com.example.data.model.ChatRoom
import com.example.data.model.ContactEntry
import com.example.data.model.GeminiBotPreset
import com.example.data.model.HandleEntry
import com.example.data.model.UserProfile
import com.example.data.remote.unwrapFirestoreException
import com.example.data.repository.BotStreamEvent
import com.example.data.repository.GeminiBotPresets
import com.example.data.repository.GeminiBotRepository
import com.example.data.repository.GeminiModels
import com.example.data.repository.MessagingRepository
import com.example.rust.RustBridge
import com.example.rust.RustCoreInfo
import com.example.rust.RustNotificationDecision
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>

  data class Success<T>(val data: T) : UiState<T>

  data class Error(val message: String) : UiState<Nothing>
}

enum class ChatFilter {
  ALL,
  DIRECT,
  GROUPS,
  CHANNELS,
  BOTS,
  ARCHIVED,
}

class MessengerViewModel(
  private val messagingRepository: MessagingRepository,
  private val geminiBotRepository: GeminiBotRepository,
  val currentUserId: String,
  private val initialDisplayName: String,
) : ViewModel() {

  val rustCoreInfo: RustCoreInfo by lazy { RustBridge.coreInfo() }

  private val _selectedChatId = MutableStateFlow<String?>(null)
  val selectedChatId: StateFlow<String?> = _selectedChatId.asStateFlow()

  private val _selectedFilter = MutableStateFlow(ChatFilter.ALL)
  val selectedFilter: StateFlow<ChatFilter> = _selectedFilter.asStateFlow()

  private val _searchQuery = MutableStateFlow("")
  val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

  private val _streamingBotReply = MutableStateFlow<String?>(null)
  val streamingBotReply: StateFlow<String?> = _streamingBotReply.asStateFlow()

  private val _isBotThinking = MutableStateFlow(false)
  val isBotThinking: StateFlow<Boolean> = _isBotThinking.asStateFlow()

  private val _actionError = MutableStateFlow<String?>(null)
  val actionError: StateFlow<String?> = _actionError.asStateFlow()

  private val _replyingTo = MutableStateFlow<ChatMessage?>(null)
  val replyingTo: StateFlow<ChatMessage?> = _replyingTo.asStateFlow()

  private val _editingMessage = MutableStateFlow<ChatMessage?>(null)
  val editingMessage: StateFlow<ChatMessage?> = _editingMessage.asStateFlow()

  private val _forwardingMessage = MutableStateFlow<ChatMessage?>(null)
  val forwardingMessage: StateFlow<ChatMessage?> = _forwardingMessage.asStateFlow()

  private val _silentMode = MutableStateFlow(false)
  val silentMode: StateFlow<Boolean> = _silentMode.asStateFlow()

  private val _handleLookupResult = MutableStateFlow<HandleEntry?>(null)
  val handleLookupResult: StateFlow<HandleEntry?> = _handleLookupResult.asStateFlow()

  private val _handleLookupNotFound = MutableStateFlow(false)
  val handleLookupNotFound: StateFlow<Boolean> = _handleLookupNotFound.asStateFlow()

  private var botStreamJob: Job? = null

  val userProfile: StateFlow<UserProfile?> =
    messagingRepository
      .observeUserProfile(currentUserId)
      .catch { error ->
        val fsErr = error.unwrapFirestoreException()
        if (fsErr == null && error is CancellationException) throw error
        Log.w(TAG, "Error observing profile", fsErr ?: error)
        _actionError.value = (fsErr ?: error).localizedMessage
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

  val chatsState: StateFlow<UiState<List<ChatRoom>>> =
    messagingRepository
      .observeUserChats(currentUserId)
      .map<List<ChatRoom>, UiState<List<ChatRoom>>> { UiState.Success(it) }
      .catch { error ->
        val fsErr = error.unwrapFirestoreException()
        if (fsErr == null && error is CancellationException) throw error
        val root = fsErr ?: error
        Log.w(TAG, "Error observing chats", root)
        emit(UiState.Error(root.localizedMessage ?: "Failed to load chats"))
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

  val publicChannelsState: StateFlow<UiState<List<ChatRoom>>> =
    messagingRepository
      .observePublicChannels(currentUserId)
      .map<List<ChatRoom>, UiState<List<ChatRoom>>> { UiState.Success(it) }
      .catch { error ->
        val fsErr = error.unwrapFirestoreException()
        if (fsErr == null && error is CancellationException) throw error
        val root = fsErr ?: error
        Log.w(TAG, "Error observing public channels", root)
        emit(UiState.Error(root.localizedMessage ?: "Failed to load channels"))
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

  val contactsState: StateFlow<UiState<List<ContactEntry>>> =
    messagingRepository
      .observeContacts(currentUserId)
      .map<List<ContactEntry>, UiState<List<ContactEntry>>> { UiState.Success(it) }
      .catch { error ->
        val fsErr = error.unwrapFirestoreException()
        if (fsErr == null && error is CancellationException) throw error
        val root = fsErr ?: error
        Log.w(TAG, "Error observing contacts", root)
        emit(UiState.Error(root.localizedMessage ?: "Failed to load contacts"))
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

  val draftsMap: StateFlow<Map<String, String>> =
    messagingRepository
      .observeDrafts(currentUserId)
      .catch { error ->
        val fsErr = error.unwrapFirestoreException()
        if (fsErr == null && error is CancellationException) throw error
        Log.w(TAG, "Error observing drafts", fsErr ?: error)
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyMap())

  val callLogsState: StateFlow<UiState<List<CallLogEntry>>> =
    messagingRepository
      .observeCallLogs(currentUserId)
      .map<List<CallLogEntry>, UiState<List<CallLogEntry>>> { UiState.Success(it) }
      .catch { error ->
        val fsErr = error.unwrapFirestoreException()
        if (fsErr == null && error is CancellationException) throw error
        val root = fsErr ?: error
        Log.w(TAG, "Error observing call logs", root)
        emit(UiState.Error(root.localizedMessage ?: "Failed to load call logs"))
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

  @OptIn(ExperimentalCoroutinesApi::class)
  val activeMessagesState: StateFlow<UiState<List<ChatMessage>>> =
    _selectedChatId
      .flatMapLatest { chatId ->
        if (chatId == null) {
          flowOf(UiState.Success(emptyList()))
        } else {
          val activeChat = (chatsState.value as? UiState.Success)?.data?.firstOrNull { it.id == chatId }
            ?: (publicChannelsState.value as? UiState.Success)?.data?.firstOrNull { it.id == chatId }
          val isPublic = activeChat?.isPublic == true
          messagingRepository
            .observeMessages(chatId, currentUserId, isPublic)
            .map<List<ChatMessage>, UiState<List<ChatMessage>>> { UiState.Success(it) }
            .catch { error ->
              val fsErr = error.unwrapFirestoreException()
              if (fsErr == null && error is CancellationException) throw error
              val root = fsErr ?: error
              Log.w(TAG, "Error observing messages for $chatId", root)
              emit(UiState.Error(root.localizedMessage ?: "Failed to load messages"))
            }
        }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

  init {
    bootstrapUserWorkspace()
  }

  private fun bootstrapUserWorkspace() {
    viewModelScope.launch {
      try {
        val profile =
          messagingRepository.ensureUserProfileInitialized(
            uid = currentUserId,
            defaultDisplayName = initialDisplayName,
            defaultLanguage = "ru",
          )
        val starterPreset = GeminiBotPresets.all.first()
        val ids = RustBridge.deterministicIds(uid = currentUserId, roleId = starterPreset.roleId)
        val savedId = ids.savedChatId ?: "saved_${currentUserId.take(24)}"
        messagingRepository.createChatRoom(
          chatType = "direct",
          title = if (profile.languagePref == "en") "Saved Messages" else "Избранное",
          description = "Personal cloud storage & notes",
          avatarColorHex = "#1D4E89",
          initialWelcomeMessage =
            if (profile.languagePref == "en") {
              "Welcome to Rustgram Saved Messages. Pin notes, media, and forwarded messages here."
            } else {
              "Добро пожаловать в Избранное Rustgram. Сохраняйте заметки, файлы и пересланные сообщения."
            },
          initialSenderName = profile.displayName,
          fixedChatId = savedId,
        )
        val botChatId = ids.botChatId ?: "bot_${starterPreset.roleId}_${currentUserId.take(20)}"
        messagingRepository.createChatRoom(
          chatType = "ai_bot",
          title =
            if (profile.languagePref == "en") starterPreset.titleEn else starterPreset.titleRu,
          description =
            if (profile.languagePref == "en") starterPreset.subtitleEn else starterPreset.subtitleRu,
          avatarColorHex = starterPreset.avatarColorHex,
          botRoleId = starterPreset.roleId,
          botModelName = starterPreset.defaultModel,
          botSystemPrompt = starterPreset.systemInstruction,
          initialWelcomeMessage =
            if (profile.languagePref == "en") {
              "Hello! I am ${starterPreset.titleEn} running on ${starterPreset.defaultModel}. Ask me anything about architecture, Rust, or system design."
            } else {
              "Привет! Я ${starterPreset.titleRu} на базе ${starterPreset.defaultModel}. Задайте вопрос по архитектуре, Rust или разработке."
            },
          initialSenderName = starterPreset.titleRu,
          initialSenderType = "ai_bot",
          fixedChatId = botChatId,
        )
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Failed to bootstrap user workspace", e)
        _actionError.value = e.localizedMessage
      }
    }
  }

  fun evaluateNotification(chat: ChatRoom, message: ChatMessage): RustNotificationDecision {
    val blockedUids =
      (contactsState.value as? UiState.Success)?.data
        .orEmpty()
        .filter { it.isBlocked }
        .map { it.contactUid }
    return RustBridge.evaluateNotification(
      currentUid = currentUserId,
      authorId = message.authorId,
      senderName = message.senderName,
      chatTitle = chat.title,
      messageText = message.text,
      isSilent = message.isSilent,
      blockedUids = blockedUids,
    )
  }

  fun selectChat(chatId: String?) {
    if (_selectedChatId.value != chatId) {
      botStreamJob?.cancel()
      _streamingBotReply.value = null
      _isBotThinking.value = false
      _replyingTo.value = null
      _editingMessage.value = null
      _selectedChatId.value = chatId
    }
  }

  fun setFilter(filter: ChatFilter) {
    _selectedFilter.value = filter
  }

  fun setSearchQuery(query: String) {
    _searchQuery.value = query
  }

  fun toggleSilentMode() {
    _silentMode.value = !_silentMode.value
  }

  fun clearError() {
    _actionError.value = null
  }

  fun setReplyingTo(message: ChatMessage?) {
    _editingMessage.value = null
    _replyingTo.value = message
  }

  fun setEditingMessage(message: ChatMessage?) {
    _replyingTo.value = null
    _editingMessage.value = message
  }

  fun setForwardingMessage(message: ChatMessage?) {
    _forwardingMessage.value = message
  }

  fun sendOrEditMessage(
    chat: ChatRoom,
    text: String,
    attachmentType: String = "none",
    attachmentName: String = "",
    attachmentData: String = "",
    attachedBitmapForAi: Bitmap? = null,
  ) {
    val cleanText = text.trim()
    val editing = _editingMessage.value
    if (editing != null) {
      if (cleanText.isBlank()) return
      _editingMessage.value = null
      viewModelScope.launch {
        try {
          messagingRepository.editMessage(chat.id, editing.id, cleanText, editing.authorId)
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          _actionError.value = e.localizedMessage ?: "Failed to edit message"
        }
      }
      return
    }

    if (cleanText.isBlank() && attachmentType == "none") return

    val replyTarget = _replyingTo.value
    _replyingTo.value = null
    val myName = userProfile.value?.displayName ?: initialDisplayName
    val currentHistory = (activeMessagesState.value as? UiState.Success)?.data.orEmpty()

    viewModelScope.launch {
      try {
        messagingRepository.sendMessage(
          chatId = chat.id,
          text = cleanText,
          senderName = myName,
          senderType = "user",
          memberUids = chat.memberUids,
          isPublic = chat.isPublic,
          replyToMessageId = replyTarget?.id.orEmpty(),
          replyPreviewText = replyTarget?.text?.take(120).orEmpty(),
          attachmentType = attachmentType,
          attachmentName = attachmentName,
          attachmentData = attachmentData,
          isSilent = _silentMode.value,
        )
        messagingRepository.saveDraft(chat.id, "")

        if (chat.chatType == "ai_bot") {
          triggerGeminiBotReply(
            chat = chat,
            history = currentHistory,
            userPrompt = cleanText,
            attachedBitmap = attachedBitmapForAi,
          )
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _actionError.value = e.localizedMessage ?: "Failed to send message"
      }
    }
  }

  private fun triggerGeminiBotReply(
    chat: ChatRoom,
    history: List<ChatMessage>,
    userPrompt: String,
    attachedBitmap: Bitmap? = null,
  ) {
    botStreamJob?.cancel()
    botStreamJob =
      viewModelScope.launch {
        _isBotThinking.value = true
        _streamingBotReply.value = ""
        val model = chat.botModelName.ifBlank { GeminiModels.GENERAL_FLASH }
        val systemPrompt =
          chat.botSystemPrompt.ifBlank {
            "You are a helpful AI assistant inside Rustgram messenger."
          }

        geminiBotRepository
          .streamReply(
            modelName = model,
            systemPrompt = systemPrompt,
            historyMessages = history,
            userPrompt = userPrompt,
            attachedBitmap = attachedBitmap,
          )
          .collect { event ->
            when (event) {
              is BotStreamEvent.Chunk -> {
                _streamingBotReply.value = event.accumulatedText
              }
              is BotStreamEvent.Completed -> {
                _streamingBotReply.value = null
                _isBotThinking.value = false
                try {
                  messagingRepository.sendMessage(
                    chatId = chat.id,
                    text = event.fullText,
                    senderName = chat.title,
                    senderType = "ai_bot",
                    memberUids = chat.memberUids,
                    isPublic = false,
                  )
                } catch (e: CancellationException) {
                  throw e
                } catch (e: Exception) {
                  _actionError.value = e.localizedMessage ?: "Failed to save AI response"
                }
              }
              is BotStreamEvent.Error -> {
                _streamingBotReply.value = null
                _isBotThinking.value = false
                _actionError.value = event.message
              }
            }
          }
      }
  }

  fun forwardMessageToChat(targetChat: ChatRoom) {
    val msg = _forwardingMessage.value ?: return
    _forwardingMessage.value = null
    viewModelScope.launch {
      try {
        messagingRepository.sendMessage(
          chatId = targetChat.id,
          text = msg.text,
          senderName = userProfile.value?.displayName ?: initialDisplayName,
          senderType = "user",
          memberUids = targetChat.memberUids,
          isPublic = targetChat.isPublic,
          forwardedFromName = msg.senderName,
          attachmentType = msg.attachmentType,
          attachmentName = msg.attachmentName,
          attachmentData = msg.attachmentData,
        )
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _actionError.value = e.localizedMessage ?: "Failed to forward message"
      }
    }
  }

  fun toggleReaction(chatId: String, message: ChatMessage, emoji: String) {
    viewModelScope.launch {
      try {
        messagingRepository.toggleReaction(chatId, message, emoji)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _actionError.value = e.localizedMessage
      }
    }
  }

  fun pinOrUnpinMessage(chat: ChatRoom, message: ChatMessage?) {
    viewModelScope.launch {
      try {
        if (message == null || chat.pinnedMessageId == message.id) {
          messagingRepository.pinMessage(chat.id, "", "")
        } else {
          messagingRepository.pinMessage(
            chat.id,
            message.id,
            message.text.ifBlank { message.attachmentName },
          )
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _actionError.value = e.localizedMessage
      }
    }
  }

  fun deleteMessage(chatId: String, messageId: String) {
    viewModelScope.launch {
      try {
        messagingRepository.deleteMessage(chatId, messageId)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _actionError.value = e.localizedMessage
      }
    }
  }

  fun saveDraft(chatId: String, text: String) {
    viewModelScope.launch {
      try {
        messagingRepository.saveDraft(chatId, text)
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
      }
    }
  }

  fun createGroupOrChannel(
    chatType: String,
    title: String,
    description: String,
    isPublic: Boolean,
    selectedMemberUids: List<String> = emptyList(),
  ) {
    viewModelScope.launch {
      try {
        val color = if (chatType == "channel") "#0EA5E9" else "#D96B27"
        val myName = userProfile.value?.displayName ?: initialDisplayName
        val id =
          messagingRepository.createChatRoom(
            chatType = chatType,
            title = title,
            description = description,
            avatarColorHex = color,
            extraMemberUids = selectedMemberUids,
            isPublic = isPublic,
            initialWelcomeMessage = "$title created by $myName",
            initialSenderName = myName,
          )
        _selectedChatId.value = id
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _actionError.value = e.localizedMessage ?: "Failed to create room"
      }
    }
  }

  fun openOrCreateBotChat(preset: GeminiBotPreset, lang: String) {
    viewModelScope.launch {
      try {
        val ids = RustBridge.deterministicIds(uid = currentUserId, roleId = preset.roleId)
        val botChatId = ids.botChatId ?: "bot_${preset.roleId}_${currentUserId.take(20)}"
        val title = if (lang == "en") preset.titleEn else preset.titleRu
        val subtitle = if (lang == "en") preset.subtitleEn else preset.subtitleRu
        val welcome =
          if (lang == "en") {
            "Hello! I am $title (${preset.defaultModel}). How can I assist you?"
          } else {
            "Привет! Я $title (${preset.defaultModel}). Чем помочь?"
          }
        val id =
          messagingRepository.createChatRoom(
            chatType = "ai_bot",
            title = title,
            description = subtitle,
            avatarColorHex = preset.avatarColorHex,
            botRoleId = preset.roleId,
            botModelName = preset.defaultModel,
            botSystemPrompt = preset.systemInstruction,
            initialWelcomeMessage = welcome,
            initialSenderName = title,
            initialSenderType = "ai_bot",
            fixedChatId = botChatId,
          )
        _selectedChatId.value = id
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _actionError.value = e.localizedMessage ?: "Failed to open AI bot"
      }
    }
  }

  fun createCustomBotChat(title: String, modelName: String, systemPrompt: String) {
    viewModelScope.launch {
      try {
        val id =
          messagingRepository.createChatRoom(
            chatType = "ai_bot",
            title = title,
            description = "Custom Gemini Assistant • $modelName",
            avatarColorHex = "#6D4AFF",
            botRoleId = "custom",
            botModelName = modelName,
            botSystemPrompt = systemPrompt,
            initialWelcomeMessage = "Ready ($modelName). System role configured.",
            initialSenderName = title,
            initialSenderType = "ai_bot",
          )
        _selectedChatId.value = id
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _actionError.value = e.localizedMessage
      }
    }
  }

  fun updateBotSettings(chatId: String, modelName: String, systemPrompt: String) {
    viewModelScope.launch {
      try {
        messagingRepository.updateBotConfiguration(chatId, modelName, systemPrompt)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _actionError.value = e.localizedMessage
      }
    }
  }

  fun joinPublicChannel(chat: ChatRoom) {
    viewModelScope.launch {
      try {
        messagingRepository.joinPublicChannel(chat)
        _selectedChatId.value = chat.id
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _actionError.value = e.localizedMessage
      }
    }
  }

  fun toggleArchive(chat: ChatRoom) {
    val archived = currentUserId in chat.archivedByUids
    viewModelScope.launch {
      try {
        messagingRepository.toggleArchiveChat(chat, !archived)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _actionError.value = e.localizedMessage
      }
    }
  }

  fun lookupHandle(username: String) {
    viewModelScope.launch {
      _handleLookupNotFound.value = false
      val entry = messagingRepository.lookupHandle(username)
      _handleLookupResult.value = entry
      _handleLookupNotFound.value = (entry == null)
    }
  }

  fun clearHandleLookup() {
    _handleLookupResult.value = null
    _handleLookupNotFound.value = false
  }

  fun addContactAndMaybeOpenChat(
    contactUid: String,
    displayName: String,
    username: String,
    bio: String,
    avatarColorHex: String,
    openChatImmediately: Boolean,
  ) {
    viewModelScope.launch {
      try {
        if (openChatImmediately) {
          val existingContacts = (contactsState.value as? UiState.Success)?.data.orEmpty()
          RustBridge.canMessageContact(existingContacts, contactUid)
        }
        messagingRepository.upsertContact(contactUid, displayName, username, bio, avatarColorHex)
        if (openChatImmediately) {
          val ids = RustBridge.deterministicIds(uid = currentUserId, peerUid = contactUid)
          val chatId =
            ids.directChatId
              ?: run {
                val pairIds = listOf(currentUserId, contactUid).sorted()
                "dm_${pairIds[0].take(14)}_${pairIds[1].take(14)}"
              }
          val myName = userProfile.value?.displayName ?: initialDisplayName
          messagingRepository.createChatRoom(
            chatType = "direct",
            title = displayName,
            description = "@$username",
            avatarColorHex = avatarColorHex,
            extraMemberUids = listOf(contactUid),
            initialWelcomeMessage = "Chat started with $displayName",
            initialSenderName = myName,
            fixedChatId = chatId,
          )
          _selectedChatId.value = chatId
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _actionError.value = e.localizedMessage
      }
    }
  }

  fun startDirectChatFromContact(contact: ContactEntry) {
    addContactAndMaybeOpenChat(
      contactUid = contact.contactUid,
      displayName = contact.displayName,
      username = contact.username,
      bio = contact.bio,
      avatarColorHex = contact.avatarColorHex,
      openChatImmediately = true,
    )
  }

  fun toggleContactBlocked(contact: ContactEntry) {
    viewModelScope.launch {
      try {
        messagingRepository.toggleContactBlocked(contact.id, !contact.isBlocked)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _actionError.value = e.localizedMessage
      }
    }
  }

  fun updateProfile(
    displayName: String,
    username: String,
    bio: String,
    avatarColorHex: String,
    languagePref: String,
    darkModePref: Boolean,
  ) {
    viewModelScope.launch {
      try {
        messagingRepository.updateProfile(
          displayName = displayName,
          username = username,
          bio = bio,
          avatarColorHex = avatarColorHex,
          languagePref = languagePref,
          darkModePref = darkModePref,
        )
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _actionError.value = e.localizedMessage
      }
    }
  }

  fun logCallSession(peerName: String, callType: String, durationSeconds: Long) {
    viewModelScope.launch {
      try {
        messagingRepository.logCallSession(
          peerName = peerName,
          callType = callType,
          status = "preview",
          durationSeconds = durationSeconds,
        )
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _actionError.value = e.localizedMessage
      }
    }
  }

  private companion object {
    const val TAG = "MessengerViewModel"
  }
}
