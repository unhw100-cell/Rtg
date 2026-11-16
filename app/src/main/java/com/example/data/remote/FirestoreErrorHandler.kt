package com.example.data.remote

import android.util.Log
import com.example.rust.RustBridge
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
  CREATE("create"),
  UPDATE("update"),
  DELETE("delete"),
  LIST("list"),
  GET("get"),
  WRITE("write"),
}

fun handleFirestoreError(
  exception: Throwable,
  operationType: OperationType,
  path: String?,
): String {
  val causes = generateSequence(exception) { it.cause }
  val rootError =
    causes.firstOrNull { it is FirebaseFirestoreException }
      ?: causes.firstOrNull { it !is CancellationException }
      ?: exception

  val currentUser = try {
    Firebase.auth.currentUser
  } catch (_: Exception) {
    null
  }

  val report =
    RustBridge.formatStructuredError(
      rawMessage = rootError.message ?: rootError.toString(),
      opType = operationType.value,
      path = path,
      userId = currentUser?.uid,
    )

  val providerInfoList =
    currentUser?.providerData?.map { provider ->
      JSONObject().apply {
        put("providerId", provider.providerId)
        put("email", provider.email)
      }
    } ?: emptyList()

  val authInfoJson =
    JSONObject().apply {
      put("userId", report.userId ?: currentUser?.uid)
      put("email", currentUser?.email)
      put("emailVerified", currentUser?.isEmailVerified)
      put("tenantId", currentUser?.tenantId)
      put("providerInfo", JSONArray(providerInfoList))
    }

  val errorInfoJson =
    JSONObject().apply {
      put("error", report.error)
      put("operationType", report.operationType)
      put("path", report.path)
      put("isPermissionDenied", report.isPermissionDenied)
      put("isNetworkError", report.isNetworkError)
      put("authInfo", authInfoJson)
    }

  val jsonString = errorInfoJson.toString()
  Log.e("FirestoreError", "Firestore Error: $jsonString")
  return jsonString
}

fun Throwable.unwrapFirestoreException(): FirebaseFirestoreException? {
  return generateSequence(this) { it.cause }
    .filterIsInstance<FirebaseFirestoreException>()
    .firstOrNull()
}
