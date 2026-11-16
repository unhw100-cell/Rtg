package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MessagingRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun profileAndChats_authenticatedOwner_createsAndObservesRealtime() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val repo = MessagingRepository(firestore)

    val profile = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.ensureUserProfileInitialized(aliceUid, "Alice Rust", "ru")
    }
    assertEquals(aliceUid, profile.uid)

    val chatId = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.createChatRoom(
        chatType = "group",
        title = "Rust Core ${UUID.randomUUID().toString().take(4)}",
        description = "Systems discussion",
        initialWelcomeMessage = "Welcome to Rustgram!",
        initialSenderName = "Alice Rust",
      )
    }
    assertTrue(chatId.isNotBlank())

    val chats = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeUserChats(aliceUid).first { list -> list.any { it.id == chatId } }
    }
    assertTrue(chats.any { it.id == chatId })

    val messages = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeMessages(chatId, aliceUid, isPublicChannel = false).first { it.isNotEmpty() }
    }
    assertTrue(messages.any { it.text == "Welcome to Rustgram!" })
  }

  @Test
  fun messageAndDraft_crossUserIsolation_enforcedBySecurityRules() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val repo = MessagingRepository(firestore)

    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.ensureUserProfileInitialized(aliceUid, "Alice", "ru")
    }
    val chatId = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.createChatRoom(
        chatType = "direct",
        title = "Alice Private Notes",
        initialWelcomeMessage = "Secret draft note",
        initialSenderName = "Alice",
      )
    }
    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.saveDraft(chatId, "Alice unsent draft")
    }

    val bobUid = signInTestUser(BOB_EMAIL)
    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.ensureUserProfileInitialized(bobUid, "Bob", "en")
    }

    // Bob cannot read Alice's private chat messages
    try {
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeMessages(chatId, aliceUid, isPublicChannel = false).first()
      }
      fail("Expected PERMISSION_DENIED when Bob reads Alice's private messages")
    } catch (e: Exception) {
      val firestoreError = e.unwrapFirestoreException()
      assertNotNull("Expected nested FirebaseFirestoreException, got $e", firestoreError)
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreError!!.code)
    }

    // Bob cannot read Alice's drafts
    try {
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeDrafts(aliceUid).first()
      }
      fail("Expected PERMISSION_DENIED when Bob reads Alice's drafts")
    } catch (e: Exception) {
      val firestoreError = e.unwrapFirestoreException()
      assertNotNull("Expected nested FirebaseFirestoreException, got $e", firestoreError)
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreError!!.code)
    }
  }

  @Test
  fun observeUserChats_unauthenticatedCaller_failsWithPermissionDenied() = runBlocking {
    auth.signOut()
    val repo = MessagingRepository(firestore)

    try {
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeUserChats("some_uid").first()
      }
      fail("Expected PERMISSION_DENIED for unauthenticated caller")
    } catch (e: Exception) {
      val firestoreError = e.unwrapFirestoreException()
      assertNotNull("Expected nested FirebaseFirestoreException, got $e", firestoreError)
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreError!!.code)
    }
  }

  private companion object {
    const val ALICE_EMAIL = "alice_rust@test.com"
    const val BOB_EMAIL = "bob_rust@test.com"
    const val DEFAULT_TIMEOUT_MS = 15_000L
    const val FLOW_TIMEOUT_MS = 15_000L
  }
}
