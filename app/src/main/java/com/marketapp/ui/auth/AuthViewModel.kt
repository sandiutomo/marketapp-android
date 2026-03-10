package com.marketapp.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import com.marketapp.analytics.AnalyticsEvent
import com.marketapp.analytics.AnalyticsManager
import com.marketapp.analytics.PerformanceMonitor
import com.marketapp.analytics.UserProperties
import com.marketapp.data.repository.AuthRepository
import com.marketapp.data.repository.DeviceInfoRepository
import com.marketapp.data.repository.UserStatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val analyticsManager: AnalyticsManager,
    private val userStats: UserStatsRepository,
    private val deviceInfo: DeviceInfoRepository,
    private val perf: PerformanceMonitor
) : ViewModel() {

    val authState: StateFlow<FirebaseUser?> = authRepository.currentUser
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    // Epoch ms when the current session started; 0 when no session is active.
    private var sessionStartMs: Long = 0L

    init {
        // Identify the user across all trackers whenever auth state changes to logged-in.
        // This covers app restarts where Firebase restores an existing session automatically.
        viewModelScope.launch {
            // Fetch device IDs once before processing auth state so identify() always
            // includes deviceId / appSetId / advertisingId from the first call onward.
            deviceInfo.fetchOnce()
            authState.collect { user ->
                if (user != null) {
                    if (sessionStartMs == 0L) sessionStartMs = System.currentTimeMillis()
                    analyticsManager.identify(
                        userId = user.uid,
                        properties = UserProperties(
                            userId        = user.uid,
                            email         = user.email,
                            name          = user.displayName,
                            deviceId      = deviceInfo.deviceId,
                            appSetId      = deviceInfo.appSetId,
                            advertisingId = deviceInfo.advertisingId
                        )
                    )
                }
            }
        }
    }

    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            perf.trace("auth_sign_in") { trace ->
                trace.putAttribute("method", "google")
                authRepository.signInWithGoogle(idToken)
                    .onSuccess { user ->
                        analyticsManager.track(AnalyticsEvent.UserSignedIn("google"))
                        identifyUser(user, loginMethod = "google")
                        _uiState.value = AuthUiState.Idle
                    }
                    .onFailure { _uiState.value = AuthUiState.Error(it.message ?: "Sign-in failed") }
            }
        }
    }

    fun signInWithEmail(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            perf.trace("auth_sign_in") { trace ->
                trace.putAttribute("method", "email")
                authRepository.signInWithEmail(email, password)
                    .onSuccess { user ->
                        analyticsManager.track(AnalyticsEvent.UserSignedIn("email"))
                        identifyUser(user, loginMethod = "email")
                        _uiState.value = AuthUiState.Idle
                    }
                    .onFailure { _uiState.value = AuthUiState.Error(it.message ?: "Sign-in failed") }
            }
        }
    }

    fun registerWithEmail(email: String, password: String, name: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            perf.trace("auth_register") { trace ->
                trace.putAttribute("method", "email")
                authRepository.registerWithEmail(email, password, name)
                    .onSuccess { user ->
                        analyticsManager.track(AnalyticsEvent.UserRegistered("email"))
                        identifyUser(user, loginMethod = "email")
                        _uiState.value = AuthUiState.Idle
                    }
                    .onFailure { _uiState.value = AuthUiState.Error(it.message ?: "Registration failed") }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            val durationMs = if (sessionStartMs > 0L) System.currentTimeMillis() - sessionStartMs else 0L
            analyticsManager.track(AnalyticsEvent.UserSignedOut(durationMs))
            sessionStartMs = 0L
            authRepository.signOut()
            userStats.clear()
            analyticsManager.reset()
        }
    }

    private fun identifyUser(user: FirebaseUser, loginMethod: String? = null) {
        analyticsManager.identify(
            userId = user.uid,
            properties = UserProperties(
                userId        = user.uid,
                email         = user.email,
                name          = user.displayName,
                loginMethod   = loginMethod,
                deviceId      = deviceInfo.deviceId,
                appSetId      = deviceInfo.appSetId,
                advertisingId = deviceInfo.advertisingId
            )
        )
    }
}
