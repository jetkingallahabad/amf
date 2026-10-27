package com.example.util

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
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
  exception: Exception,
  operationType: OperationType,
  path: String?,
): String {
  val auth = FirebaseAuth.getInstance()
  val currentUser = auth.currentUser

  val providerInfoList = currentUser?.providerData?.map { provider ->
    JSONObject().apply {
      put("providerId", provider.providerId)
      put("email", provider.email)
    }
  } ?: emptyList()

  val authInfoJson = JSONObject().apply {
    put("userId", currentUser?.uid)
    put("email", currentUser?.email)
    put("emailVerified", currentUser?.isEmailVerified)
    put("tenantId", currentUser?.tenantId)
    put("providerInfo", JSONArray(providerInfoList))
  }

  val errorInfoJson = JSONObject().apply {
    put("error", exception.message ?: exception.toString())
    put("operationType", operationType.value)
    put("path", path)
    put("authInfo", authInfoJson)
  }

  val jsonString = errorInfoJson.toString()
  Log.e("FirestoreError", "Firestore Error: $jsonString")
  return jsonString
}

fun toUserFriendlyFirestoreMessage(error: Throwable, fallback: String = "Database operation failed."): String {
  var current: Throwable? = error
  while (current != null) {
    if (current is FirebaseFirestoreException) {
      return when (current.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED ->
          "Access denied by Firestore Security Rules. Verify your assigned role and branch permissions."
        FirebaseFirestoreException.Code.UNAVAILABLE ->
          "Cloud Firestore is currently unreachable. Check your internet connection and retry."
        FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED ->
          "Quota exceeded. Daily free-tier Firestore quota resets tomorrow."
        FirebaseFirestoreException.Code.UNAUTHENTICATED ->
          "Your Google session expired. Please sign in again."
        FirebaseFirestoreException.Code.NOT_FOUND ->
          "Requested financial record was not found in Cloud Firestore."
        else -> current.localizedMessage ?: fallback
      }
    }
    current = current.cause
  }
  return error.localizedMessage ?: fallback
}
