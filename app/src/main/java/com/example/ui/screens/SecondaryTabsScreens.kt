package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.model.CallLogEntry
import com.example.data.model.ContactEntry
import com.example.data.model.GeminiBotPreset
import com.example.data.model.HandleEntry
import com.example.data.model.UserProfile
import com.example.data.repository.GeminiBotPresets
import com.example.rust.RustCoreInfo
import com.example.ui.components.AvatarBadge
import com.example.ui.components.CreateBotDialog
import com.example.ui.components.formatTimeShort
import com.example.ui.components.parseColorHex
import com.example.ui.i18n.AppStrings
import com.example.ui.viewmodel.UiState
import kotlinx.coroutines.delay

@Composable
fun ContactsTabScreen(
  contactsState: UiState<List<ContactEntry>>,
  handleLookupResult: HandleEntry?,
  handleLookupNotFound: Boolean,
  strings: AppStrings,
  onLookupHandle: (String) -> Unit,
  onClearLookup: () -> Unit,
  onAddContactAndChat: (HandleEntry) -> Unit,
  onStartChatWithContact: (ContactEntry) -> Unit,
  onToggleBlock: (ContactEntry) -> Unit,
) {
  var handleQuery by remember { mutableStateOf("") }

  Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
    Text(
      text = strings.contactsTitle,
      style = MaterialTheme.typography.headlineSmall,
      modifier = Modifier.padding(vertical = 8.dp),
    )

    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      OutlinedTextField(
        value = handleQuery,
        onValueChange = {
          handleQuery = it
          if (it.isBlank()) onClearLookup()
        },
        placeholder = { Text(strings.lookupHandleHint) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.weight(1f).testTag("handle_search_input"),
      )
      Spacer(modifier = Modifier.width(8.dp))
      Button(
        onClick = { onLookupHandle(handleQuery) },
        enabled = handleQuery.trim().length >= 3,
        modifier = Modifier.height(52.dp).testTag("lookup_handle_button"),
      ) {
        Text(strings.findByHandle)
      }
    }

    if (handleLookupResult != null) {
      Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
      ) {
        Row(
          modifier = Modifier.padding(12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          AvatarBadge(
            title = handleLookupResult.displayName,
            colorHex = handleLookupResult.avatarColorHex,
            size = 44.dp,
          )
          Spacer(modifier = Modifier.width(10.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(text = handleLookupResult.displayName, style = MaterialTheme.typography.titleMedium)
            Text(
              text = "@${handleLookupResult.username}",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.primary,
            )
          }
          Button(onClick = { onAddContactAndChat(handleLookupResult) }) {
            Icon(Icons.Filled.PersonAdd, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text(strings.startChat)
          }
        }
      }
    } else if (handleLookupNotFound) {
      Text(
        text = strings.handleNotFound,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp),
      )
    }

    Spacer(modifier = Modifier.height(8.dp))

    when (contactsState) {
      is UiState.Loading -> {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator()
        }
      }
      is UiState.Error -> {
        Text(text = contactsState.message, color = MaterialTheme.colorScheme.error)
      }
      is UiState.Success -> {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(bottom = 80.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(contactsState.data, key = { it.id }) { contact ->
            Surface(
              color = MaterialTheme.colorScheme.surface,
              shape = RoundedCornerShape(16.dp),
              tonalElevation = 1.dp,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                AvatarBadge(
                  title = contact.displayName,
                  colorHex = contact.avatarColorHex,
                  size = 46.dp,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(text = contact.displayName, style = MaterialTheme.typography.titleMedium)
                  Text(
                    text =
                      if (contact.isBlocked) strings.blockedBadge
                      else "@${contact.username.ifBlank { contact.contactUid.take(8) }}",
                    style = MaterialTheme.typography.labelSmall,
                    color =
                      if (contact.isBlocked) MaterialTheme.colorScheme.error
                      else MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                IconButton(onClick = { onToggleBlock(contact) }) {
                  Icon(
                    imageVector = Icons.Filled.Block,
                    contentDescription =
                      if (contact.isBlocked) strings.unblock else strings.block,
                    tint =
                      if (contact.isBlocked) MaterialTheme.colorScheme.error
                      else MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                IconButton(onClick = { onStartChatWithContact(contact) }) {
                  Icon(
                    imageVector = Icons.AutoMirrored.Filled.Chat,
                    contentDescription = strings.startChat,
                    tint = MaterialTheme.colorScheme.primary,
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

@Composable
fun AiBotsTabScreen(
  languageCode: String,
  strings: AppStrings,
  onOpenPresetBot: (GeminiBotPreset) -> Unit,
  onCreateCustomBot: (title: String, model: String, systemPrompt: String) -> Unit,
) {
  var showCreateDialog by remember { mutableStateOf(false) }

  Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(text = strings.tabAiBots, style = MaterialTheme.typography.headlineSmall)
      Button(
        onClick = { showCreateDialog = true },
        modifier = Modifier.testTag("create_custom_bot_button"),
      ) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Spacer(modifier = Modifier.width(6.dp))
        Text(strings.newAiBot)
      }
    }

    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(bottom = 80.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      items(GeminiBotPresets.all, key = { it.roleId }) { preset ->
        val title = if (languageCode == "en") preset.titleEn else preset.titleRu
        val subtitle = if (languageCode == "en") preset.subtitleEn else preset.subtitleRu
        Surface(
          color = MaterialTheme.colorScheme.surface,
          shape = RoundedCornerShape(18.dp),
          tonalElevation = 1.dp,
          modifier =
            Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(18.dp))
              .clickable { onOpenPresetBot(preset) }
              .testTag("preset_bot_${preset.roleId}"),
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            AvatarBadge(
              title = title,
              colorHex = preset.avatarColorHex,
              size = 52.dp,
              chatType = "ai_bot",
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = title,
                  style = MaterialTheme.typography.titleMedium,
                  modifier = Modifier.weight(1f),
                )
                Surface(
                  color = MaterialTheme.colorScheme.primaryContainer,
                  shape = RoundedCornerShape(8.dp),
                ) {
                  Text(
                    text = preset.defaultModel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                  )
                }
              }
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }
  }

  if (showCreateDialog) {
    CreateBotDialog(
      strings = strings,
      onDismiss = { showCreateDialog = false },
      onCreate = { title, model, prompt ->
        onCreateCustomBot(title, model, prompt)
        showCreateDialog = false
      },
    )
  }
}

@Composable
fun CallsTabScreen(
  callLogsState: UiState<List<CallLogEntry>>,
  strings: AppStrings,
  onLogCall: (peerName: String, callType: String, durationSeconds: Long) -> Unit,
) {
  val context = LocalContext.current
  var activeCallType by remember { mutableStateOf<String?>(null) }
  var callElapsedSeconds by remember { mutableLongStateOf(0L) }
  var micMuted by remember { mutableStateOf(false) }
  var speakerOn by remember { mutableStateOf(false) }

  val audioPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      if (granted) {
        callElapsedSeconds = 0L
        activeCallType = "audio"
      }
    }

  LaunchedEffect(activeCallType) {
    if (activeCallType != null) {
      while (true) {
        delay(1000L)
        callElapsedSeconds += 1L
      }
    }
  }

  Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
    Text(
      text = strings.callsTitle,
      style = MaterialTheme.typography.headlineSmall,
      modifier = Modifier.padding(vertical = 8.dp),
    )

    Surface(
      color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
      shape = RoundedCornerShape(16.dp),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = strings.localPreviewNotice,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          Button(
            onClick = {
              val granted =
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                  PackageManager.PERMISSION_GRANTED
              if (granted) {
                callElapsedSeconds = 0L
                activeCallType = "audio"
              } else {
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
              }
            },
            modifier = Modifier.weight(1f).testTag("start_audio_check_button"),
          ) {
            Icon(Icons.Filled.Call, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text(strings.audioCall)
          }
          OutlinedButton(
            onClick = {
              callElapsedSeconds = 0L
              activeCallType = "video"
            },
            modifier = Modifier.weight(1f),
          ) {
            Icon(Icons.Filled.Videocam, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text(strings.videoCall)
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(12.dp))

    when (callLogsState) {
      is UiState.Loading -> {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator()
        }
      }
      is UiState.Error -> {
        Text(text = callLogsState.message, color = MaterialTheme.colorScheme.error)
      }
      is UiState.Success -> {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(bottom = 80.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(callLogsState.data, key = { it.id }) { log ->
            Surface(
              color = MaterialTheme.colorScheme.surface,
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(
                  imageVector =
                    if (log.callType == "video") Icons.Filled.Videocam else Icons.Filled.Call,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(text = log.peerName, style = MaterialTheme.typography.titleMedium)
                  Text(
                    text = "${log.status} • ${log.durationSeconds}s",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                Text(
                  text = formatTimeShort(log.createdAt),
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      }
    }
  }

  activeCallType?.let { callType ->
    AlertDialog(
      onDismissRequest = {},
      title = {
        Text(if (callType == "video") strings.videoCall else strings.audioCall)
      },
      text = {
        Column(
          modifier = Modifier.fillMaxWidth(),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text(
            text = String.format("%02d:%02d", callElapsedSeconds / 60, callElapsedSeconds % 60),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
          )
          Text(
            text = if (micMuted) strings.micMuted else strings.micActive,
            style = MaterialTheme.typography.bodyMedium,
          )
          Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            IconButton(
              onClick = { micMuted = !micMuted },
              modifier =
                Modifier.size(48.dp)
                  .clip(CircleShape)
                  .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
              Icon(
                imageVector = if (micMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                contentDescription = strings.micMuted,
              )
            }
            IconButton(
              onClick = { speakerOn = !speakerOn },
              modifier =
                Modifier.size(48.dp)
                  .clip(CircleShape)
                  .background(
                    if (speakerOn) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                  ),
            ) {
              Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = strings.speakerOn)
            }
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            val type = callType
            val dur = callElapsedSeconds
            activeCallType = null
            onLogCall("Rustgram Audio Check", type, dur)
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) {
          Icon(Icons.Filled.CallEnd, contentDescription = null)
          Spacer(modifier = Modifier.width(6.dp))
          Text(strings.endCall)
        }
      },
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsTabScreen(
  profile: UserProfile?,
  rustCoreInfo: RustCoreInfo?,
  languageCode: String,
  darkMode: Boolean,
  strings: AppStrings,
  onSaveProfile: (
    displayName: String,
    username: String,
    bio: String,
    avatarColorHex: String,
    languagePref: String,
    darkModePref: Boolean,
  ) -> Unit,
  onSignOut: () -> Unit,
) {
  var displayName by remember(profile?.displayName) {
    mutableStateOf(profile?.displayName.orEmpty())
  }
  var username by remember(profile?.username) { mutableStateOf(profile?.username.orEmpty()) }
  var bio by remember(profile?.bio) { mutableStateOf(profile?.bio.orEmpty()) }
  var avatarColor by remember(profile?.avatarColorHex) {
    mutableStateOf(profile?.avatarColorHex ?: "#D96B27")
  }

  Column(
    modifier =
      Modifier.fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Text(text = strings.settingsTitle, style = MaterialTheme.typography.headlineSmall)

    Surface(
      color = MaterialTheme.colorScheme.surface,
      shape = RoundedCornerShape(18.dp),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          AvatarBadge(
            title = displayName.ifBlank { "R" },
            colorHex = avatarColor,
            size = 56.dp,
          )
          Spacer(modifier = Modifier.width(12.dp))
          Column {
            Text(text = strings.profileSection, style = MaterialTheme.typography.titleMedium)
            Text(
              text = "@${username.ifBlank { "rustgram" }}",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.primary,
            )
          }
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf("#D96B27", "#1D4E89", "#6D4AFF", "#0EA5E9", "#10B981").forEach { hex ->
            Box(
              modifier =
                Modifier.size(30.dp)
                  .clip(CircleShape)
                  .background(parseColorHex(hex))
                  .clickable { avatarColor = hex }
            )
          }
        }

        OutlinedTextField(
          value = displayName,
          onValueChange = { displayName = it },
          label = { Text(strings.displayNameLabel) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = username,
          onValueChange = { username = it },
          label = { Text(strings.usernameLabel) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = bio,
          onValueChange = { bio = it },
          label = { Text(strings.bioLabel) },
          maxLines = 3,
          modifier = Modifier.fillMaxWidth(),
        )
        Button(
          onClick = {
            onSaveProfile(displayName, username, bio, avatarColor, languageCode, darkMode)
          },
          modifier = Modifier.align(Alignment.End).testTag("save_profile_button"),
        ) {
          Text(strings.save)
        }
      }
    }

    Surface(
      color = MaterialTheme.colorScheme.surface,
      shape = RoundedCornerShape(18.dp),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = strings.appearanceSection, style = MaterialTheme.typography.titleMedium)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(text = strings.darkModeLabel, style = MaterialTheme.typography.bodyLarge)
          Switch(
            checked = darkMode,
            onCheckedChange = { newDark ->
              onSaveProfile(displayName, username, bio, avatarColor, languageCode, newDark)
            },
            modifier = Modifier.testTag("dark_mode_switch"),
          )
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(text = strings.languageLabel, style = MaterialTheme.typography.bodyLarge)
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
              selected = languageCode == "ru",
              onClick = {
                onSaveProfile(displayName, username, bio, avatarColor, "ru", darkMode)
              },
              label = { Text("RU") },
            )
            FilterChip(
              selected = languageCode == "en",
              onClick = {
                onSaveProfile(displayName, username, bio, avatarColor, "en", darkMode)
              },
              label = { Text("EN") },
            )
          }
        }
      }
    }

    Surface(
      color = MaterialTheme.colorScheme.surface,
      shape = RoundedCornerShape(18.dp),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = strings.sessionsSection, style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Filled.PhoneAndroid,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
          )
          Spacer(modifier = Modifier.width(10.dp))
          Text(text = strings.currentDeviceLabel, style = MaterialTheme.typography.bodyMedium)
        }
        if (rustCoreInfo != null) {
          Text(
            text = "${rustCoreInfo.engine} v${rustCoreInfo.version} • ${rustCoreInfo.cratesCount} crates • ${rustCoreInfo.abiBridge} (${rustCoreInfo.targetArch})",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.testTag("rust_core_info_label"),
          )
        }
      }
    }

    OutlinedButton(
      onClick = onSignOut,
      colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
      modifier = Modifier.fillMaxWidth().height(48.dp).testTag("sign_out_button"),
    ) {
      Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
      Spacer(modifier = Modifier.width(8.dp))
      Text(strings.signOut, fontWeight = FontWeight.SemiBold)
    }

    Spacer(modifier = Modifier.height(32.dp))
  }
}
