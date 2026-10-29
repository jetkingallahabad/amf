package com.example

import android.app.Application
import com.example.data.local.LoanFinanceDatabase
import com.example.data.repository.AuthAndOrgRepository
import com.example.data.repository.CustomerLoanRepository
import com.example.data.session.DataStoreSessionPreferences
import com.example.data.session.SessionStore
import com.example.data.sync.SyncEngine
import com.example.util.ConnectivityObserver
import com.example.util.NetworkConnectivityObserver
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class AppContainer(private val application: Application) {
  val database: LoanFinanceDatabase by lazy {
    LoanFinanceDatabase.getInstance(application)
  }
  val sessionStore: SessionStore by lazy {
    DataStoreSessionPreferences(application)
  }
  val connectivityObserver: ConnectivityObserver by lazy {
    NetworkConnectivityObserver(application)
  }

  private fun ensureFirebaseInitialized() {
    if (FirebaseApp.getApps(application).isEmpty()) {
      FirebaseApp.initializeApp(application)
    }
  }

  val firestore: FirebaseFirestore by lazy {
    ensureFirebaseInitialized()
    FirebaseFirestore.getInstance()
  }

  val firebaseAuth: FirebaseAuth by lazy {
    ensureFirebaseInitialized()
    Firebase.auth
  }

  val syncEngine: SyncEngine by lazy {
    SyncEngine(
      dao = database.dao(),
      firestore = firestore,
    )
  }

  val authOrgRepository: AuthAndOrgRepository by lazy {
    AuthAndOrgRepository(
      dao = database.dao(),
      sessionStore = sessionStore,
      firestore = firestore,
      firebaseAuth = firebaseAuth,
    )
  }

  val customerLoanRepository: CustomerLoanRepository by lazy {
    CustomerLoanRepository(
      dao = database.dao(),
      syncEngine = syncEngine,
      firestore = firestore,
      firebaseAuth = firebaseAuth,
    )
  }
}

class LoanFinanceApplication : Application() {
  lateinit var container: AppContainer
    private set

  override fun onCreate() {
    super.onCreate()
    container = AppContainer(this)
  }
}
