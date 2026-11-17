package com.example.ui.i18n

import com.example.rust.RustBridge

data class AppStrings(
  val appName: String = "Rustgram",
  val tagline: String,
  val signInSubtitle: String,
  val signInWithGoogle: String,
  val checkingSession: String,
  val tabChats: String,
  val tabContacts: String,
  val tabAiBots: String,
  val tabCalls: String,
  val tabSettings: String,
  val filterAll: String,
  val filterDirect: String,
  val filterGroups: String,
  val filterChannels: String,
  val filterBots: String,
  val filterArchived: String,
  val searchHint: String,
  val newChat: String,
  val newGroup: String,
  val newChannel: String,
  val newAiBot: String,
  val emptyChatsTitle: String,
  val emptyChatsBody: String,
  val messagePlaceholder: String,
  val reply: String,
  val react: String,
  val forward: String,
  val pin: String,
  val unpin: String,
  val edit: String,
  val delete: String,
  val copy: String,
  val pinnedLabel: String,
  val draftPrefix: String,
  val silentMode: String,
  val editedLabel: String,
  val aiStreaming: String,
  val modelLabel: String,
  val systemInstructionLabel: String,
  val save: String,
  val cancel: String,
  val create: String,
  val titleLabel: String,
  val descriptionLabel: String,
  val publicChannelLabel: String,
  val contactsTitle: String,
  val addContact: String,
  val lookupHandleHint: String,
  val findByHandle: String,
  val handleNotFound: String,
  val startChat: String,
  val block: String,
  val unblock: String,
  val blockedBadge: String,
  val callsTitle: String,
  val audioCall: String,
  val videoCall: String,
  val localPreviewNotice: String,
  val endCall: String,
  val micMuted: String,
  val micActive: String,
  val speakerOn: String,
  val settingsTitle: String,
  val profileSection: String,
  val displayNameLabel: String,
  val usernameLabel: String,
  val bioLabel: String,
  val appearanceSection: String,
  val darkModeLabel: String,
  val languageLabel: String,
  val sessionsSection: String,
  val currentDeviceLabel: String,
  val signOut: String,
  val archive: String,
  val unarchive: String,
  val photoAttachment: String,
  val fileAttachment: String,
  val voiceNote: String,
  val recordingVoice: String,
  val stopRecording: String,
  val publicChannelsDiscover: String,
  val joinChannel: String,
  val joined: String,
  val membersCount: String,
  val inviteCodeLabel: String,
  val savedMessagesTitle: String,
  val retry: String,
)

object StringsProvider {
  @Volatile var lastLoadError: String? = null
    private set

  private val cachedRu: AppStrings? by lazy {
    try {
      RustBridge.getLocalizedStrings("ru")
    } catch (t: Throwable) {
      lastLoadError = t.localizedMessage ?: t.javaClass.simpleName
      null
    }
  }

  private val cachedEn: AppStrings? by lazy {
    try {
      RustBridge.getLocalizedStrings("en")
    } catch (t: Throwable) {
      lastLoadError = t.localizedMessage ?: t.javaClass.simpleName
      null
    }
  }

  val ru: AppStrings
    get() = cachedRu ?: emergencyStrings("ru")

  val en: AppStrings
    get() = cachedEn ?: emergencyStrings("en")

  fun forLang(lang: String): AppStrings =
    if (lang.trim().equals("en", ignoreCase = true)) en else ru

  private fun emergencyStrings(lang: String): AppStrings {
    val isEn = lang == "en"
    return AppStrings(
      appName = "Rustgram",
      tagline = if (isEn) "Rust Core JNI unavailable" else "Ядро Rust JNI недоступно",
      signInSubtitle =
        if (isEn) "Native Rust Core library failed to load."
        else "Не удалось загрузить нативную библиотеку Rust Core.",
      signInWithGoogle = if (isEn) "Sign in with Google" else "Войти через Google",
      checkingSession = if (isEn) "Checking session..." else "Проверка сессии...",
      tabChats = if (isEn) "Chats" else "Чаты",
      tabContacts = if (isEn) "Contacts" else "Контакты",
      tabAiBots = if (isEn) "AI Bots" else "AI Боты",
      tabCalls = if (isEn) "Calls" else "Звонки",
      tabSettings = if (isEn) "Settings" else "Настройки",
      filterAll = if (isEn) "All" else "Все",
      filterDirect = if (isEn) "Direct" else "Личные",
      filterGroups = if (isEn) "Groups" else "Группы",
      filterChannels = if (isEn) "Channels" else "Каналы",
      filterBots = if (isEn) "AI Bots" else "Боты",
      filterArchived = if (isEn) "Archived" else "Архив",
      searchHint = if (isEn) "Search..." else "Поиск...",
      newChat = if (isEn) "New Chat" else "Новый чат",
      newGroup = if (isEn) "Create Group" else "Создать группу",
      newChannel = if (isEn) "Create Channel" else "Создать канал",
      newAiBot = if (isEn) "New AI Bot" else "Новый AI-бот",
      emptyChatsTitle = if (isEn) "No conversations" else "Нет чатов",
      emptyChatsBody = if (isEn) "Native core unavailable." else "Ядро Rust недоступно.",
      messagePlaceholder = if (isEn) "Message..." else "Сообщение...",
      reply = if (isEn) "Reply" else "Ответить",
      react = if (isEn) "React" else "Реакция",
      forward = if (isEn) "Forward" else "Переслать",
      pin = if (isEn) "Pin" else "Закрепить",
      unpin = if (isEn) "Unpin" else "Открепить",
      edit = if (isEn) "Edit" else "Редактировать",
      delete = if (isEn) "Delete" else "Удалить",
      copy = if (isEn) "Copy" else "Копировать",
      pinnedLabel = if (isEn) "Pinned" else "Закреплено",
      draftPrefix = if (isEn) "Draft:" else "Черновик:",
      silentMode = if (isEn) "Silent" else "Без звука",
      editedLabel = if (isEn) "edited" else "изм.",
      aiStreaming = if (isEn) "Gemini..." else "Gemini...",
      modelLabel = "Gemini",
      systemInstructionLabel = "System Instruction",
      save = if (isEn) "Save" else "Сохранить",
      cancel = if (isEn) "Cancel" else "Отмена",
      create = if (isEn) "Create" else "Создать",
      titleLabel = if (isEn) "Title" else "Название",
      descriptionLabel = if (isEn) "Description" else "Описание",
      publicChannelLabel = if (isEn) "Public channel" else "Публичный канал",
      contactsTitle = if (isEn) "Contacts" else "Контакты",
      addContact = if (isEn) "Add Contact" else "Добавить",
      lookupHandleHint = "@username",
      findByHandle = if (isEn) "Lookup" else "Найти",
      handleNotFound = if (isEn) "Not found" else "Не найдено",
      startChat = if (isEn) "Message" else "Написать",
      block = if (isEn) "Block" else "Заблокировать",
      unblock = if (isEn) "Unblock" else "Разблокировать",
      blockedBadge = if (isEn) "Blocked" else "Заблокирован",
      callsTitle = if (isEn) "Calls" else "Звонки",
      audioCall = if (isEn) "Mic Check" else "Проверка микрофона",
      videoCall = if (isEn) "Session Preview" else "Тест сессии",
      localPreviewNotice = if (isEn) "Native Rust Core unavailable." else "Ядро Rust Core недоступно.",
      endCall = if (isEn) "End" else "Завершить",
      micMuted = if (isEn) "Mic Muted" else "Микрофон выкл.",
      micActive = if (isEn) "Mic Active" else "Микрофон вкл.",
      speakerOn = if (isEn) "Speaker" else "Динамик",
      settingsTitle = if (isEn) "Settings" else "Настройки",
      profileSection = if (isEn) "Profile" else "Профиль",
      displayNameLabel = if (isEn) "Display Name" else "Имя",
      usernameLabel = "@username",
      bioLabel = "Bio",
      appearanceSection = if (isEn) "Appearance" else "Оформление",
      darkModeLabel = if (isEn) "Dark Theme" else "Тёмная тема",
      languageLabel = if (isEn) "Language" else "Язык",
      sessionsSection = if (isEn) "Sessions" else "Сессии",
      currentDeviceLabel = "Android",
      signOut = if (isEn) "Sign Out" else "Выйти",
      archive = if (isEn) "Archive" else "В архив",
      unarchive = if (isEn) "Unarchive" else "Из архива",
      photoAttachment = if (isEn) "Photo" else "Фото",
      fileAttachment = if (isEn) "File" else "Файл",
      voiceNote = if (isEn) "Voice" else "Голосовое",
      recordingVoice = if (isEn) "Recording..." else "Запись...",
      stopRecording = if (isEn) "Send" else "Отправить",
      publicChannelsDiscover = if (isEn) "Public Channels" else "Публичные каналы",
      joinChannel = if (isEn) "Subscribe" else "Подписаться",
      joined = if (isEn) "Joined" else "Подписаны",
      membersCount = if (isEn) "members" else "участн.",
      inviteCodeLabel = if (isEn) "Invite" else "Код",
      savedMessagesTitle = if (isEn) "Saved Messages" else "Избранное",
      retry = if (isEn) "Retry" else "Повторить",
    )
  }
}
