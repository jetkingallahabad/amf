package com.example.base

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import java.net.HttpURLConnection
import java.net.Socket
import java.net.InetSocketAddress
import java.net.URL

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
abstract class FirestoreEmulatorTestBase {

  protected lateinit var firestore: FirebaseFirestore
  protected lateinit var auth: FirebaseAuth
  protected lateinit var firebaseApp: FirebaseApp
  protected lateinit var databaseId: String

  @Before
  open fun setUpFirebase() {
    Assume.assumeTrue(
      "Skipping FirestoreEmulatorTestBase because Firestore Emulator is not listening on 127.0.0.1:8085",
      isPortListening("127.0.0.1", 8085),
    )
    ShadowLog.stream = System.out

    val context = ApplicationProvider.getApplicationContext<Context>()
    val options = FirebaseOptions.Builder()
      .setProjectId("demo-no-project")
      .setApplicationId("1:1234567890:android:abcdef")
      .setApiKey("fake-api-key-for-emulator")
      .build()

    firebaseApp = if (FirebaseApp.getApps(context).isEmpty()) {
      FirebaseApp.initializeApp(context, options)
    } else {
      FirebaseApp.getInstance()
    }

    databaseId = context.getString(R.string.firestore_database_id)
    clearFirestoreEmulatorDatabase(databaseId)
    clearAuthEmulatorAccounts()

    firestore = FirebaseFirestore.getInstance(firebaseApp, databaseId)
    try {
      firestore.useEmulator("127.0.0.1", 8085)
      firestore.firestoreSettings = FirebaseFirestoreSettings.Builder()
        .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
        .build()
    } catch (_: IllegalStateException) {
      // Emulator already attached to singleton instance
    }

    auth = FirebaseAuth.getInstance(firebaseApp)
    try {
      auth.useEmulator("127.0.0.1", 9099)
    } catch (_: IllegalStateException) {
      // Emulator already attached
    }
    auth.signOut()
    shadowOf(Looper.getMainLooper()).idle()
  }

  /**
   * Executes a suspend block on Dispatchers.IO while pumping Robolectric's Main Looper so
   * Google Play Services TaskExecutors.MAIN_THREAD callbacks (.await() and .snapshots()) complete immediately.
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  protected fun runEmulatorTest(
    timeoutMs: Long = 25_000L,
    block: suspend CoroutineScope.() -> Unit,
  ) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    try {
      val deferred = scope.async {
        withTimeout(timeoutMs) {
          block()
        }
      }
      while (!deferred.isCompleted) {
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(15)
      }
      shadowOf(Looper.getMainLooper()).idle()
      deferred.getCompletionExceptionOrNull()?.let { throw it }
    } finally {
      scope.cancel()
    }
  }

  protected suspend fun signInTestUser(
    email: String = "test@example.com",
    password: String = "password123",
    markEmailVerified: Boolean = true,
  ): String {
    val authResult = try {
      auth.createUserWithEmailAndPassword(email, password).await()
    } catch (_: FirebaseAuthUserCollisionException) {
      auth.signInWithEmailAndPassword(email, password).await()
    }
    val user = checkNotNull(authResult.user)
    if (markEmailVerified) {
      setEmulatorEmailVerified(user.uid)
      user.reload().await()
    }
    user.getIdToken(true).await()
    kotlinx.coroutines.delay(50)
    return user.uid
  }

  private fun clearFirestoreEmulatorDatabase(dbId: String) {
    val endpoint =
      "http://127.0.0.1:8085/emulator/v1/projects/demo-no-project/databases/$dbId/documents"
    val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
      requestMethod = "DELETE"
      connectTimeout = 5_000
      readTimeout = 5_000
    }
    try {
      val code = conn.responseCode
      val stream = if (code in 200..299) conn.inputStream else conn.errorStream
      stream?.use { it.readBytes() }
    } finally {
      conn.disconnect()
    }
  }

  private fun clearAuthEmulatorAccounts() {
    val endpoint =
      "http://127.0.0.1:9099/emulator/v1/projects/demo-no-project/accounts"
    val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
      requestMethod = "DELETE"
      setRequestProperty("Authorization", "Bearer owner")
      connectTimeout = 5_000
      readTimeout = 5_000
    }
    try {
      val code = conn.responseCode
      val stream = if (code in 200..299) conn.inputStream else conn.errorStream
      stream?.use { it.readBytes() }
    } finally {
      conn.disconnect()
    }
  }

  private fun setEmulatorEmailVerified(localId: String) {
    val endpoint =
      "http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:update?key=fake-api-key-for-emulator"
    val payload = """{"localId":"$localId","emailVerified":true}"""
    val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
      requestMethod = "POST"
      doOutput = true
      setRequestProperty("Content-Type", "application/json")
      setRequestProperty("Authorization", "Bearer owner")
      connectTimeout = 3_000
      readTimeout = 3_000
    }
    try {
      conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
      conn.responseCode
    } finally {
      conn.disconnect()
    }
  }

  @After
  open fun tearDownFirebase() {
    if (::auth.isInitialized) {
      runCatching { auth.signOut() }
    }
    if (::firestore.isInitialized) {
      runCatching {
        val termTask = firestore.terminate()
        var spins = 0
        while (!termTask.isComplete && spins < 50) {
          shadowOf(Looper.getMainLooper()).idle()
          Thread.sleep(10)
          spins++
        }
      }
    }
    FirebaseApp.clearInstancesForTest()
    shadowOf(Looper.getMainLooper()).idle()
  }

  private fun isPortListening(host: String, port: Int): Boolean {
    return try {
      Socket().use { socket ->
        socket.connect(InetSocketAddress(host, port), 400)
        true
      }
    } catch (_: Exception) {
      false
    }
  }
}
