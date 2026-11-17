package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.repository.GeminiBotRepository
import com.example.data.repository.MessagingRepository
import com.example.rust.RustNativeStatus
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.authStateFlow
import com.example.ui.auth.signOut
import com.example.ui.i18n.StringsProvider
import com.example.ui.screens.AiBotsTabScreen
import com.example.ui.screens.CallsTabScreen
import com.example.ui.screens.ChatsTabScreen
import com.example.ui.screens.ContactsTabScreen
import com.example.ui.screens.ConversationScreen
import com.example.ui.screens.SettingsTabScreen
import com.example.ui.theme.RustgramTheme
import com.example.ui.viewmodel.MessengerViewModel
import com.example.ui.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

private enum class MainTab(val icon: ImageVector) {
  CHATS(Icons.Filled.Forum),
  CONTACTS(Icons.Filled.Contacts),
  AI_BOTS(Icons.Filled.AutoAwesome),
  CALLS(Icons.Filled.Call),
  SETTINGS(Icons.Filled.Settings),
}

@Composable
fun RustgramApp() {
  val auth = Firebase.auth
  val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
  var authLanguageCode by remember { mutableStateOf("ru") }

  val user = currentUser
  if (user == null) {
    RustgramTheme(darkTheme = true) {
      AuthScreen(
        languageCode = authLanguageCode,
        onLanguageChange = { authLanguageCode = it },
        onAuthSuccess = {},
      )
    }
  } else {
    val uid = user.uid
    key(uid) {
      val viewModel: MessengerViewModel =
        viewModel(
          key = uid,
          factory =
            viewModelFactory {
              initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) { "APPLICATION_KEY missing" }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                MessengerViewModel(
                  messagingRepository = MessagingRepository(db),
                  geminiBotRepository = GeminiBotRepository(),
                  currentUserId = uid,
                  initialDisplayName = user.displayName ?: "Rustgram User",
                )
              }
            },
        )
      MainMessengerContent(viewModel = viewModel)
    }
  }
}

@Composable
private fun MainMessengerContent(viewModel: MessengerViewModel) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }

  val profile by viewModel.userProfile.collectAsStateWithLifecycle()
  val chatsState by viewModel.chatsState.collectAsStateWithLifecycle()
  val publicChannelsState by viewModel.publicChannelsState.collectAsStateWithLifecycle()
  val contactsState by viewModel.contactsState.collectAsStateWithLifecycle()
  val draftsMap by viewModel.draftsMap.collectAsStateWithLifecycle()
  val callLogsState by viewModel.callLogsState.collectAsStateWithLifecycle()
  val selectedChatId by viewModel.selectedChatId.collectAsStateWithLifecycle()
  val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
  val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
  val activeMessagesState by viewModel.activeMessagesState.collectAsStateWithLifecycle()
  val streamingBotReply by viewModel.streamingBotReply.collectAsStateWithLifecycle()
  val isBotThinking by viewModel.isBotThinking.collectAsStateWithLifecycle()
  val replyingTo by viewModel.replyingTo.collectAsStateWithLifecycle()
  val editingMessage by viewModel.editingMessage.collectAsStateWithLifecycle()
  val forwardingMessage by viewModel.forwardingMessage.collectAsStateWithLifecycle()
  val silentMode by viewModel.silentMode.collectAsStateWithLifecycle()
  val handleLookupResult by viewModel.handleLookupResult.collectAsStateWithLifecycle()
  val handleLookupNotFound by viewModel.handleLookupNotFound.collectAsStateWithLifecycle()
  val actionError by viewModel.actionError.collectAsStateWithLifecycle()
  val incomingNotification by viewModel.incomingNotification.collectAsStateWithLifecycle()

  val languageCode = profile?.languagePref ?: "ru"
  val darkMode = profile?.darkModePref ?: true
  val strings = StringsProvider.forLang(languageCode)

  var currentTab by remember { mutableStateOf(MainTab.CHATS) }
  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(actionError) {
    val err = actionError
    if (!err.isNullOrBlank()) {
      snackbarHostState.showSnackbar(err)
      viewModel.clearError()
    }
  }

  LaunchedEffect(incomingNotification) {
    val notif = incomingNotification
    if (notif != null && notif.shouldNotify) {
      snackbarHostState.showSnackbar("${notif.title}: ${notif.preview}")
      viewModel.dismissIncomingNotification()
    }
  }

  val allChats = (chatsState as? UiState.Success)?.data.orEmpty()
  val selectedChat = allChats.firstOrNull { it.id == selectedChatId }

  RustgramTheme(darkTheme = darkMode) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
      val isExpandedLayout = maxWidth >= 840.dp

      if (!isExpandedLayout && selectedChat != null) {
        ConversationScreen(
          chat = selectedChat,
          allChats = allChats,
          currentUserId = viewModel.currentUserId,
          messagesState = activeMessagesState,
          streamingBotReply = streamingBotReply,
          isBotThinking = isBotThinking,
          replyingTo = replyingTo,
          editingMessage = editingMessage,
          forwardingMessage = forwardingMessage,
          silentMode = silentMode,
          initialDraft = draftsMap[selectedChat.id].orEmpty(),
          strings = strings,
          showBackButton = true,
          onBack = { viewModel.selectChat(null) },
          onSendMessage = { text, type, name, data, bmp ->
            viewModel.sendOrEditMessage(selectedChat, text, type, name, data, bmp)
          },
          onSaveDraft = { text -> viewModel.saveDraft(selectedChat.id, text) },
          onSetReplyingTo = { viewModel.setReplyingTo(it) },
          onSetEditingMessage = { viewModel.setEditingMessage(it) },
          onSetForwardingMessage = { viewModel.setForwardingMessage(it) },
          onForwardToChat = { target -> viewModel.forwardMessageToChat(target) },
          onToggleReaction = { msg, emoji -> viewModel.toggleReaction(selectedChat.id, msg, emoji) },
          onPinOrUnpin = { msg -> viewModel.pinOrUnpinMessage(selectedChat, msg) },
          onDeleteMessage = { msgId -> viewModel.deleteMessage(selectedChat.id, msgId) },
          onToggleSilentMode = { viewModel.toggleSilentMode() },
          onUpdateBotConfig = { model, prompt ->
            viewModel.updateBotSettings(selectedChat.id, model, prompt)
          },
          onAddMemberByHandle = { handle, onAdded ->
            viewModel.addGroupMemberByHandle(selectedChat, handle, onAdded)
          },
          onLeaveChannel = { viewModel.leavePublicChannel(selectedChat) },
          onDeleteChat = { viewModel.deleteChatRoom(selectedChat) },
        )
      } else {
        val tabItems =
          listOf(
            MainTab.CHATS to strings.tabChats,
            MainTab.CONTACTS to strings.tabContacts,
            MainTab.AI_BOTS to strings.tabAiBots,
            MainTab.CALLS to strings.tabCalls,
            MainTab.SETTINGS to strings.tabSettings,
          )

        Scaffold(
          snackbarHost = { SnackbarHost(snackbarHostState) },
          bottomBar = {
            if (!isExpandedLayout) {
              NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                tabItems.forEach { (tab, label) ->
                  NavigationBarItem(
                    selected = currentTab == tab,
                    onClick = { currentTab = tab },
                    icon = { Icon(tab.icon, contentDescription = label) },
                    label = { Text(label, maxLines = 1) },
                    modifier = Modifier.testTag("nav_${tab.name.lowercase()}"),
                  )
                }
              }
            }
          },
        ) { innerPadding ->
          Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (isExpandedLayout) {
              NavigationRail(
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxHeight(),
              ) {
                tabItems.forEach { (tab, label) ->
                  NavigationRailItem(
                    selected = currentTab == tab,
                    onClick = { currentTab = tab },
                    icon = { Icon(tab.icon, contentDescription = label) },
                    label = { Text(label) },
                    modifier = Modifier.testTag("rail_${tab.name.lowercase()}"),
                  )
                }
              }
              VerticalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            }

            Column(
              modifier =
                if (isExpandedLayout) Modifier.width(380.dp).fillMaxHeight()
                else Modifier.fillMaxSize()
            ) {
              Surface(
                color = MaterialTheme.colorScheme.surface,
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Text(
                    text = "RUSTGRAM",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                  )
                }
              }

              val nativeErr = (viewModel.rustNativeStatus as? RustNativeStatus.Unavailable)?.reason
              AnimatedVisibility(visible = nativeErr != null) {
                Surface(
                  color = MaterialTheme.colorScheme.errorContainer,
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Filled.Warning,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Text(
                      text = "Rust JNI: ${nativeErr.orEmpty()}",
                      style = MaterialTheme.typography.labelMedium,
                      color = MaterialTheme.colorScheme.onErrorContainer,
                      modifier = Modifier.weight(1f),
                    )
                  }
                }
              }

              when (currentTab) {
                MainTab.CHATS ->
                  ChatsTabScreen(
                    currentUserId = viewModel.currentUserId,
                    selectedChatId = selectedChatId,
                    selectedFilter = selectedFilter,
                    searchQuery = searchQuery,
                    chatsState = chatsState,
                    publicChannelsState = publicChannelsState,
                    draftsMap = draftsMap,
                    strings = strings,
                    onSelectFilter = { viewModel.setFilter(it) },
                    onSearchQueryChange = { viewModel.setSearchQuery(it) },
                    onSelectChat = { viewModel.selectChat(it) },
                    onToggleArchive = { viewModel.toggleArchive(it) },
                    onJoinPublicChannel = { viewModel.joinPublicChannel(it) },
                    onCreateGroupOrChannel = { type, title, desc, isPublic ->
                      viewModel.createGroupOrChannel(type, title, desc, isPublic)
                    },
                    onCreateCustomBot = { title, model, prompt ->
                      viewModel.createCustomBotChat(title, model, prompt)
                    },
                  )
                MainTab.CONTACTS ->
                  ContactsTabScreen(
                    contactsState = contactsState,
                    handleLookupResult = handleLookupResult,
                    handleLookupNotFound = handleLookupNotFound,
                    strings = strings,
                    onLookupHandle = { viewModel.lookupHandle(it) },
                    onClearLookup = { viewModel.clearHandleLookup() },
                    onAddContactAndChat = { entry ->
                      viewModel.addContactAndMaybeOpenChat(
                        contactUid = entry.ownerUid,
                        displayName = entry.displayName,
                        username = entry.username,
                        bio = entry.bio,
                        avatarColorHex = entry.avatarColorHex,
                        openChatImmediately = true,
                      )
                    },
                    onStartChatWithContact = { viewModel.startDirectChatFromContact(it) },
                    onToggleBlock = { viewModel.toggleContactBlocked(it) },
                  )
                MainTab.AI_BOTS ->
                  AiBotsTabScreen(
                    languageCode = languageCode,
                    strings = strings,
                    onOpenPresetBot = { preset ->
                      viewModel.openOrCreateBotChat(preset, languageCode)
                    },
                    onCreateCustomBot = { title, model, prompt ->
                      viewModel.createCustomBotChat(title, model, prompt)
                    },
                  )
                MainTab.CALLS ->
                  CallsTabScreen(
                    callLogsState = callLogsState,
                    strings = strings,
                    onLogCall = { peer, type, dur -> viewModel.logCallSession(peer, type, dur) },
                  )
                MainTab.SETTINGS ->
                  SettingsTabScreen(
                    profile = profile,
                    rustCoreInfo = viewModel.rustCoreInfo,
                    languageCode = languageCode,
                    darkMode = darkMode,
                    strings = strings,
                    onSaveProfile = { name, handle, bio, color, lang, dark ->
                      viewModel.updateProfile(name, handle, bio, color, lang, dark)
                    },
                    onSignOut = {
                      signOut(credentialManager = credentialManager, scope = scope)
                    },
                  )
              }
            }

            if (isExpandedLayout) {
              VerticalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
              Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                if (selectedChat != null) {
                  ConversationScreen(
                    chat = selectedChat,
                    allChats = allChats,
                    currentUserId = viewModel.currentUserId,
                    messagesState = activeMessagesState,
                    streamingBotReply = streamingBotReply,
                    isBotThinking = isBotThinking,
                    replyingTo = replyingTo,
                    editingMessage = editingMessage,
                    forwardingMessage = forwardingMessage,
                    silentMode = silentMode,
                    initialDraft = draftsMap[selectedChat.id].orEmpty(),
                    strings = strings,
                    showBackButton = false,
                    onBack = { viewModel.selectChat(null) },
                    onSendMessage = { text, type, name, data, bmp ->
                      viewModel.sendOrEditMessage(selectedChat, text, type, name, data, bmp)
                    },
                    onSaveDraft = { text -> viewModel.saveDraft(selectedChat.id, text) },
                    onSetReplyingTo = { viewModel.setReplyingTo(it) },
                    onSetEditingMessage = { viewModel.setEditingMessage(it) },
                    onSetForwardingMessage = { viewModel.setForwardingMessage(it) },
                    onForwardToChat = { target -> viewModel.forwardMessageToChat(target) },
                    onToggleReaction = { msg, emoji ->
                      viewModel.toggleReaction(selectedChat.id, msg, emoji)
                    },
                    onPinOrUnpin = { msg -> viewModel.pinOrUnpinMessage(selectedChat, msg) },
                    onDeleteMessage = { msgId -> viewModel.deleteMessage(selectedChat.id, msgId) },
                    onToggleSilentMode = { viewModel.toggleSilentMode() },
                    onUpdateBotConfig = { model, prompt ->
                      viewModel.updateBotSettings(selectedChat.id, model, prompt)
                    },
                    onAddMemberByHandle = { handle, onAdded ->
                      viewModel.addGroupMemberByHandle(selectedChat, handle, onAdded)
                    },
                    onLeaveChannel = { viewModel.leavePublicChannel(selectedChat) },
                    onDeleteChat = { viewModel.deleteChatRoom(selectedChat) },
                  )
                } else {
                  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                      text = strings.emptyChatsBody,
                      style = MaterialTheme.typography.bodyLarge,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
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
