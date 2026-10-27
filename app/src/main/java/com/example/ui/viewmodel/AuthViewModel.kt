package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.LoanFinanceApplication
import com.example.data.local.EmployeeEntity
import com.example.data.remote.FirestoreEmployee
import com.example.data.repository.AuthAndOrgRepository
import com.example.data.repository.CloudAuthResolution
import com.example.util.OperationType
import com.example.util.handleFirestoreError
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

sealed interface AuthSessionState {
  data object Loading : AuthSessionState
  data object Unauthenticated : AuthSessionState
  data class NeedsOwnerSetup(
    val uid: String,
    val email: String,
    val displayName: String,
  ) : AuthSessionState
  data class AccessNotProvisioned(
    val uid: String,
    val email: String,
  ) : AuthSessionState
  data class AccountDeactivated(val employee: EmployeeEntity) : AuthSessionState
  data class Authenticated(val employee: EmployeeEntity) : AuthSessionState
}

class AuthViewModel(
  private val authRepository: AuthAndOrgRepository,
  private val firebaseAuth: FirebaseAuth,
  private val firestore: FirebaseFirestore,
) : ViewModel() {

  private val _sessionState = MutableStateFlow<AuthSessionState>(AuthSessionState.Loading)
  val sessionState: StateFlow<AuthSessionState> = _sessionState.asStateFlow()

  private val _isBusy = MutableStateFlow(false)
  val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

  private val _authError = MutableStateFlow<String?>(null)
  val authError: StateFlow<String?> = _authError.asStateFlow()

  private var liveProfileJob: Job? = null
  private var lastResolvedUid: String? = null

  private val authStateListener = FirebaseAuth.AuthStateListener { auth ->
    val currentUser = auth.currentUser
    if (currentUser == null) {
      liveProfileJob?.cancel()
      liveProfileJob = null
      lastResolvedUid = null
      _isBusy.value = false
      _sessionState.value = AuthSessionState.Unauthenticated
    } else if (lastResolvedUid != currentUser.uid) {
      lastResolvedUid = currentUser.uid
      refreshCloudSession()
    }
  }

  init {
    firebaseAuth.addAuthStateListener(authStateListener)
  }

  override fun onCleared() {
    firebaseAuth.removeAuthStateListener(authStateListener)
    liveProfileJob?.cancel()
    super.onCleared()
  }

  fun clearError() {
    _authError.value = null
  }

  fun setBusy(busy: Boolean) {
    _isBusy.value = busy
  }

  fun reportAuthError(message: String) {
    _isBusy.value = false
    _authError.value = message
  }

  fun refreshCloudSession() {
    val user = firebaseAuth.currentUser
    if (user == null) {
      _sessionState.value = AuthSessionState.Unauthenticated
      return
    }
    _isBusy.value = true
    _authError.value = null
    if (_sessionState.value is AuthSessionState.Unauthenticated) {
      _sessionState.value = AuthSessionState.Loading
    }

    viewModelScope.launch {
      val result = authRepository.resolveAuthenticatedSession()
      _isBusy.value = false
      result.onSuccess { resolution ->
        applyResolution(resolution)
      }.onFailure { err ->
        _authError.value = err.message ?: "Unable to verify employee profile in Cloud Firestore."
        if (_sessionState.value is AuthSessionState.Loading) {
          _sessionState.value = AuthSessionState.Unauthenticated
        }
      }
    }
  }

  private fun applyResolution(resolution: CloudAuthResolution) {
    liveProfileJob?.cancel()
    when (resolution) {
      is CloudAuthResolution.NeedsOwnerSetup -> {
        _sessionState.value = AuthSessionState.NeedsOwnerSetup(
          uid = resolution.uid,
          email = resolution.email,
          displayName = resolution.displayName,
        )
      }
      is CloudAuthResolution.AccessNotProvisioned -> {
        _sessionState.value = AuthSessionState.AccessNotProvisioned(
          uid = resolution.uid,
          email = resolution.email,
        )
      }
      is CloudAuthResolution.AccountDeactivated -> {
        _sessionState.value = AuthSessionState.AccountDeactivated(resolution.employee)
      }
      is CloudAuthResolution.Authenticated -> {
        _sessionState.value = AuthSessionState.Authenticated(resolution.employee)
        observeLiveEmployeeProfile(resolution.employee.id)
      }
    }
  }

  private fun observeLiveEmployeeProfile(uid: String) {
    liveProfileJob?.cancel()
    liveProfileJob = viewModelScope.launch {
      firestore.collection("employees")
        .document(uid)
        .snapshots()
        .catch { e ->
          if (e is Exception) handleFirestoreError(e, OperationType.GET, "employees/$uid")
        }
        .collect { snap ->
          val updated = snap.toObject(FirestoreEmployee::class.java)?.toEntity() ?: return@collect
          if (!updated.isActive) {
            _sessionState.value = AuthSessionState.AccountDeactivated(updated)
          } else {
            _sessionState.value = AuthSessionState.Authenticated(updated)
          }
        }
    }
  }

  fun initializeOwner(
    orgName: String,
    fullName: String,
    mobile: String,
    email: String,
    branchName: String,
    branchCity: String,
    branchAddress: String,
  ) {
    _isBusy.value = true
    _authError.value = null
    viewModelScope.launch {
      val result = authRepository.initializeOwnerAccount(
        ownerFullName = fullName,
        mobile = mobile,
        email = email,
        orgName = orgName,
        headOfficeBranchName = branchName,
        headOfficeCity = branchCity,
        headOfficeAddress = branchAddress,
      )
      _isBusy.value = false
      result.onSuccess { owner ->
        _sessionState.value = AuthSessionState.Authenticated(owner)
        observeLiveEmployeeProfile(owner.id)
      }.onFailure { err ->
        _authError.value = err.message ?: "Failed to initialize Organization & Owner account."
      }
    }
  }

  fun signOut(
    currentActor: EmployeeEntity?,
    onReadyToSignOutAuth: () -> Unit,
  ) {
    liveProfileJob?.cancel()
    liveProfileJob = null
    lastResolvedUid = null
    viewModelScope.launch {
      runCatching { authRepository.logout(currentActor) }
      onReadyToSignOutAuth()
    }
  }

  companion object {
    val Factory: ViewModelProvider.Factory = viewModelFactory {
      initializer {
        val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as LoanFinanceApplication
        AuthViewModel(
          authRepository = app.container.authOrgRepository,
          firebaseAuth = app.container.firebaseAuth,
          firestore = app.container.firestore,
        )
      }
    }
  }
}
