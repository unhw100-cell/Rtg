package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ChatRoom
import com.example.rust.RustBridge
import com.example.ui.components.AvatarBadge
import com.example.ui.components.ChatListItemCard
import com.example.ui.components.CreateBotDialog
import com.example.ui.components.CreateRoomDialog
import com.example.ui.i18n.AppStrings
import com.example.ui.viewmodel.ChatFilter
import com.example.ui.viewmodel.UiState

@Composable
fun ChatsTabScreen(
  currentUserId: String,
  selectedChatId: String?,
  selectedFilter: ChatFilter,
  searchQuery: String,
  chatsState: UiState<List<ChatRoom>>,
  publicChannelsState: UiState<List<ChatRoom>>,
  draftsMap: Map<String, String>,
  strings: AppStrings,
  onSelectFilter: (ChatFilter) -> Unit,
  onSearchQueryChange: (String) -> Unit,
  onSelectChat: (String) -> Unit,
  onToggleArchive: (ChatRoom) -> Unit,
  onJoinPublicChannel: (ChatRoom) -> Unit,
  onCreateGroupOrChannel: (chatType: String, title: String, description: String, isPublic: Boolean) -> Unit,
  onCreateCustomBot: (title: String, model: String, systemPrompt: String) -> Unit,
) {
  var showFabMenu by remember { mutableStateOf(false) }
  var createDialogType by remember { mutableStateOf<String?>(null) }

  Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
      OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchQueryChange,
        placeholder = { Text(strings.searchHint) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
          if (searchQuery.isNotBlank()) {
            IconButton(onClick = { onSearchQueryChange("") }) {
              Icon(Icons.Filled.Close, contentDescription = strings.cancel)
            }
          }
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        modifier =
          Modifier.fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("chats_search_input"),
      )

      LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        val filters =
          listOf(
            ChatFilter.ALL to strings.filterAll,
            ChatFilter.DIRECT to strings.filterDirect,
            ChatFilter.GROUPS to strings.filterGroups,
            ChatFilter.CHANNELS to strings.filterChannels,
            ChatFilter.BOTS to strings.filterBots,
            ChatFilter.ARCHIVED to strings.filterArchived,
          )
        items(filters) { (filter, label) ->
          FilterChip(
            selected = selectedFilter == filter,
            onClick = { onSelectFilter(filter) },
            label = { Text(label) },
            modifier = Modifier.testTag("filter_${filter.name.lowercase()}"),
          )
        }
      }

      when (chatsState) {
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
            Text(text = chatsState.message, color = MaterialTheme.colorScheme.error)
          }
        }
        is UiState.Success -> {
          val publicList = (publicChannelsState as? UiState.Success)?.data.orEmpty()
          val workspaceResult =
            remember(chatsState.data, publicList, currentUserId, selectedFilter, searchQuery) {
              RustBridge.filterChatWorkspace(
                chats = chatsState.data,
                publicChannels = publicList,
                currentUid = currentUserId,
                filter = selectedFilter.name,
                query = searchQuery,
              )
            }
          val filteredChats = workspaceResult.filteredChats
          val discoverableChannels = workspaceResult.discoverableChannels

          LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 88.dp),
          ) {
            if (filteredChats.isEmpty()) {
              item {
                Column(
                  modifier = Modifier.fillMaxWidth().padding(32.dp),
                  horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                  Icon(
                    imageVector = Icons.Filled.Forum,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp),
                  )
                  Spacer(modifier = Modifier.height(12.dp))
                  Text(
                    text = strings.emptyChatsTitle,
                    style = MaterialTheme.typography.titleMedium,
                  )
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                    text = strings.emptyChatsBody,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            } else {
              items(filteredChats, key = { it.id }) { chat ->
                ChatListItemCard(
                  chat = chat,
                  isSelected = chat.id == selectedChatId,
                  isArchived = currentUserId in chat.archivedByUids,
                  draftText = draftsMap[chat.id].orEmpty(),
                  strings = strings,
                  onClick = { onSelectChat(chat.id) },
                  onToggleArchive = { onToggleArchive(chat) },
                )
              }
            }

            if (discoverableChannels.isNotEmpty() &&
              (selectedFilter == ChatFilter.ALL || selectedFilter == ChatFilter.CHANNELS)
            ) {
              item {
                Text(
                  text = strings.publicChannelsDiscover,
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
              }
              items(discoverableChannels, key = { "pub_${it.id}" }) { channel ->
                Surface(
                  color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                  shape = RoundedCornerShape(16.dp),
                  modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                  Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    AvatarBadge(
                      title = channel.title,
                      colorHex = channel.avatarColorHex,
                      size = 44.dp,
                      chatType = "channel",
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = channel.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                      Text(
                        text = "${channel.memberUids.size} ${strings.membersCount}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                    OutlinedButton(onClick = { onJoinPublicChannel(channel) }) {
                      Text(strings.joinChannel)
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

    Box(modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
      FloatingActionButton(
        onClick = { showFabMenu = true },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.testTag("new_chat_fab"),
      ) {
        Icon(Icons.Filled.Add, contentDescription = strings.newChat)
      }
      DropdownMenu(expanded = showFabMenu, onDismissRequest = { showFabMenu = false }) {
        DropdownMenuItem(
          text = { Text(strings.newGroup) },
          leadingIcon = { Icon(Icons.Filled.GroupAdd, contentDescription = null) },
          onClick = {
            showFabMenu = false
            createDialogType = "group"
          },
        )
        DropdownMenuItem(
          text = { Text(strings.newChannel) },
          leadingIcon = { Icon(Icons.Filled.Campaign, contentDescription = null) },
          onClick = {
            showFabMenu = false
            createDialogType = "channel"
          },
        )
        DropdownMenuItem(
          text = { Text(strings.newAiBot) },
          leadingIcon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null) },
          onClick = {
            showFabMenu = false
            createDialogType = "ai_bot"
          },
        )
      }
    }
  }

  when (val type = createDialogType) {
    "group", "channel" -> {
      CreateRoomDialog(
        chatType = type,
        strings = strings,
        onDismiss = { createDialogType = null },
        onCreate = { title, desc, isPublic ->
          onCreateGroupOrChannel(type, title, desc, isPublic)
          createDialogType = null
        },
      )
    }
    "ai_bot" -> {
      CreateBotDialog(
        strings = strings,
        onDismiss = { createDialogType = null },
        onCreate = { title, model, prompt ->
          onCreateCustomBot(title, model, prompt)
          createDialogType = null
        },
      )
    }
  }
}
