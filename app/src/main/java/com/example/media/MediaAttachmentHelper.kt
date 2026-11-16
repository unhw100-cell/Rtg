package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Log
import com.example.rust.RustBridge
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

object MediaAttachmentHelper {
  private const val TAG = "MediaAttachmentHelper"

  fun encodeImageToBase64(context: Context, uri: Uri): Pair<String, String>? {
    return try {
      val inputStream = context.contentResolver.openInputStream(uri) ?: return null
      val original = inputStream.use { BitmapFactory.decodeStream(it) } ?: return null
      val maxDim = 640
      val scale = minOf(maxDim.toFloat() / original.width, maxDim.toFloat() / original.height, 1f)
      val targetW = (original.width * scale).toInt().coerceAtLeast(1)
      val targetH = (original.height * scale).toInt().coerceAtLeast(1)
      val resized =
        if (scale < 1f) Bitmap.createScaledBitmap(original, targetW, targetH, true) else original

      val baos = ByteArrayOutputStream()
      resized.compress(Bitmap.CompressFormat.JPEG, 68, baos)
      val bytes = baos.toByteArray()
      if (resized !== original) resized.recycle()
      original.recycle()

      val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP).take(380_000)
      val rawName = queryDisplayName(context, uri) ?: "photo_${System.currentTimeMillis()}.jpg"
      val validated = RustBridge.validateAttachment("image", rawName, base64)
      validated.attachmentName to validated.attachmentData
    } catch (e: Exception) {
      Log.e(TAG, "Failed to encode image", e)
      null
    }
  }

  fun decodeBase64Bitmap(base64: String): Bitmap? {
    if (base64.isBlank()) return null
    return try {
      val bytes = Base64.decode(base64, Base64.DEFAULT)
      BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to decode bitmap", e)
      null
    }
  }

  fun queryDisplayName(context: Context, uri: Uri): String? {
    return try {
      context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIdx >= 0 && cursor.moveToFirst()) cursor.getString(nameIdx) else null
      }
    } catch (_: Exception) {
      null
    }
  }

  fun queryDocumentSummary(context: Context, uri: Uri): Pair<String, String> {
    var name = "document"
    var sizeBytes = 0L
    try {
      context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (cursor.moveToFirst()) {
          if (nameIdx >= 0) name = cursor.getString(nameIdx) ?: name
          if (sizeIdx >= 0) sizeBytes = cursor.getLong(sizeIdx)
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to query document metadata", e)
    }
    val formattedSize = RustBridge.formatDocumentSizeKb(sizeBytes.coerceAtLeast(0L))
    val validated = RustBridge.validateAttachment("document", name, formattedSize)
    return validated.attachmentName to validated.attachmentData
  }
}

class VoiceNoteRecorder(private val context: Context) {
  private var recorder: MediaRecorder? = null
  private var outputFile: File? = null
  private var startedAtMs: Long = 0L

  fun startRecording(): Boolean {
    return try {
      stopRecordingInternal()
      val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
      val mr =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          MediaRecorder(context)
        } else {
          @Suppress("DEPRECATION")
          MediaRecorder()
        }
      mr.apply {
        setAudioSource(MediaRecorder.AudioSource.MIC)
        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        setAudioEncodingBitRate(32000)
        setAudioSamplingRate(16000)
        setOutputFile(file.absolutePath)
        prepare()
        start()
      }
      recorder = mr
      outputFile = file
      startedAtMs = System.currentTimeMillis()
      true
    } catch (e: Exception) {
      Log.e("VoiceNoteRecorder", "Failed to start recording", e)
      stopRecordingInternal()
      false
    }
  }

  fun stopAndEncode(): Pair<String, String>? {
    val file = outputFile ?: return null
    val durationSec = ((System.currentTimeMillis() - startedAtMs) / 1000L).coerceAtLeast(1L)
    stopRecordingInternal()
    return try {
      if (!file.exists()) return null
      val bytes = file.readBytes()
      file.delete()
      if (bytes.isEmpty()) return null
      val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP).take(380_000)
      val fileName = RustBridge.formatVoiceFilename(durationSec)
      val validated = RustBridge.validateAttachment("audio", fileName, base64)
      validated.attachmentName to validated.attachmentData
    } catch (e: Exception) {
      Log.e("VoiceNoteRecorder", "Failed to encode voice note", e)
      null
    }
  }

  private fun stopRecordingInternal() {
    try {
      recorder?.apply {
        stop()
        release()
      }
    } catch (_: Exception) {
      try {
        recorder?.release()
      } catch (_: Exception) {
      }
    }
    recorder = null
  }
}

class VoiceNotePlayer(private val context: Context) {
  private var player: MediaPlayer? = null
  var playingMessageId: String? = null
    private set

  fun playOrToggle(messageId: String, base64Audio: String, onFinished: () -> Unit) {
    if (playingMessageId == messageId) {
      stop()
      onFinished()
      return
    }
    stop()
    if (base64Audio.isBlank()) {
      onFinished()
      return
    }
    try {
      val bytes = Base64.decode(base64Audio, Base64.DEFAULT)
      val temp = File(context.cacheDir, "play_$messageId.m4a")
      FileOutputStream(temp).use { it.write(bytes) }
      val mp = MediaPlayer()
      mp.setDataSource(temp.absolutePath)
      mp.setOnCompletionListener {
        stop()
        temp.delete()
        onFinished()
      }
      mp.prepare()
      mp.start()
      player = mp
      playingMessageId = messageId
    } catch (e: Exception) {
      Log.e("VoiceNotePlayer", "Failed to play voice note", e)
      stop()
      onFinished()
    }
  }

  fun stop() {
    try {
      player?.stop()
      player?.release()
    } catch (_: Exception) {
    }
    player = null
    playingMessageId = null
  }
}
