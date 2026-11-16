package com.example.data.repository

import android.graphics.Bitmap
import android.util.Log
import com.example.data.model.ChatMessage
import com.example.data.model.GeminiBotPreset
import com.example.rust.RustBridge
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

sealed interface BotStreamEvent {
  data class Chunk(val accumulatedText: String) : BotStreamEvent

  data class Completed(val fullText: String) : BotStreamEvent

  data class Error(val message: String) : BotStreamEvent
}

object GeminiModels {
  const val GENERAL_FLASH = "gemini-3.8-flash"
  const val FAST_LITE = "gemini-3.5-flash-lite"
  const val DEEP_REASONING = "gemini-4-argon"

  val availableModels: List<String> by lazy { RustBridge.getGeminiModels() }
}

object GeminiBotPresets {
  val all: List<GeminiBotPreset> by lazy { RustBridge.getGeminiPresets() }
}

class GeminiBotRepository {

  fun streamReply(
    modelName: String,
    systemPrompt: String,
    historyMessages: List<ChatMessage>,
    userPrompt: String,
    attachedBitmap: Bitmap? = null,
  ): Flow<BotStreamEvent> = flow {
    // Request preparation, model validation, system prompt sizing & 24-message context window in Rust (rustgram-ai)
    val prepared =
      RustBridge.prepareGeminiRequest(
        requestedModel = modelName,
        systemPrompt = systemPrompt,
        historyMessages = historyMessages,
        userPrompt = userPrompt,
        hasImage = attachedBitmap != null,
      )

    val generativeModel by lazy {
      Firebase.ai.generativeModel(
        modelName = prepared.modelName,
        generationConfig =
          generationConfig {
            temperature = prepared.temperature
          },
        systemInstruction =
          content {
            text(prepared.systemInstruction)
          },
      )
    }

    val history: List<Content> =
      prepared.history.map { turn ->
        content(turn.role) { text(turn.text) }
      }

    val chat = generativeModel.startChat(history = history)
    val inputContent =
      content("user") {
        if (attachedBitmap != null) {
          image(attachedBitmap)
        }
        text(prepared.userPrompt)
      }

    val accumulated = StringBuilder()
    try {
      withTimeout(60_000L) {
        chat.sendMessageStream(inputContent).collect { chunk ->
          val piece = chunk.text.orEmpty()
          if (piece.isNotEmpty()) {
            accumulated.append(piece)
            emit(BotStreamEvent.Chunk(accumulated.toString()))
          }
        }
      }
      val finalOutput = RustBridge.finalizeGeminiStream(accumulated.toString())
      emit(BotStreamEvent.Completed(finalOutput))
    } catch (e: TimeoutCancellationException) {
      Log.e(TAG, "Gemini streaming timed out after 60s", e)
      emit(BotStreamEvent.Error("Request timed out after 60 seconds. Please try again."))
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Gemini streaming failed for model=${prepared.modelName}", e)
      emit(BotStreamEvent.Error(e.localizedMessage ?: "AI generation failed."))
    }
  }

  private companion object {
    const val TAG = "GeminiBotRepository"
  }
}
