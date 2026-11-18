package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.NotificationRepository
import com.example.data.TicketNotification
import com.example.data.UiState
import com.example.data.UserAccount
import com.example.data.UserRoleRepository
import com.example.data.toFriendlyVietnameseError
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class AppSessionViewModel(
    private val userRoleRepository: UserRoleRepository,
    private val notificationRepository: NotificationRepository,
    private val currentUserId: String
) : ViewModel() {

    private var initialNotifBatchSeen = false
    private val seenNotificationIds = mutableSetOf<String>()

    private val _incomingNotification = MutableSharedFlow<TicketNotification>(extraBufferCapacity = 8)
    val incomingNotification: SharedFlow<TicketNotification> = _incomingNotification.asSharedFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    init {
        val currentUser = Firebase.auth.currentUser
        if (currentUser != null && currentUser.uid == currentUserId) {
            viewModelScope.launch {
                userRoleRepository.ensureUserProfileAndSyncRole(currentUser)
                    .onFailure { err ->
                        _syncMessage.value = toFriendlyVietnameseError(
                            err,
                            "Không thể đồng bộ hồ sơ tài khoản."
                        )
                    }
            }
        }
    }

    val currentUserProfile: StateFlow<UiState<UserAccount>> =
        userRoleRepository.observeCurrentUserProfile(currentUserId)
            .map<UserAccount?, UiState<UserAccount>> { account ->
                if (account != null) {
                    UiState.Success(account)
                } else {
                    UiState.Loading
                }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing user profile", rootError)
                emit(UiState.Error(toFriendlyVietnameseError(rootError, "Không thể tải hồ sơ người dùng.")))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    val notificationsState: StateFlow<UiState<List<TicketNotification>>> =
        currentUserProfile
            .map { state -> (state as? UiState.Success)?.data?.isApprovedAndActive == true }
            .distinctUntilChanged()
            .flatMapLatest { canObserve ->
                if (!canObserve) {
                    flowOf<UiState<List<TicketNotification>>>(UiState.Success(emptyList()))
                } else {
                    notificationRepository.observeNotifications(currentUserId)
                        .onEach { list ->
                            if (!initialNotifBatchSeen) {
                                initialNotifBatchSeen = true
                                list.forEach { if (it.id.isNotBlank()) seenNotificationIds.add(it.id) }
                            } else {
                                list.filter { !it.readFlag && it.id.isNotBlank() && seenNotificationIds.add(it.id) }
                                    .forEach { _incomingNotification.tryEmit(it) }
                            }
                        }
                        .map<List<TicketNotification>, UiState<List<TicketNotification>>> { UiState.Success(it) }
                        .catch { error ->
                            val firestoreError = generateSequence(error) { it.cause }
                                .filterIsInstance<FirebaseFirestoreException>()
                                .firstOrNull()
                            if (firestoreError == null && error is CancellationException) throw error
                            val rootError = firestoreError ?: error
                            Log.w(TAG, "Error observing notifications", rootError)
                            emit(UiState.Error(toFriendlyVietnameseError(rootError, "Không thể tải thông báo.")))
                        }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    fun markNotificationRead(notificationId: String) {
        viewModelScope.launch {
            notificationRepository.markAsRead(notificationId)
        }
    }

    fun markAllNotificationsRead(list: List<TicketNotification>) {
        viewModelScope.launch {
            notificationRepository.markAllAsRead(list)
        }
    }

    fun retryProfileSync() {
        val currentUser = Firebase.auth.currentUser ?: return
        viewModelScope.launch {
            _syncMessage.value = null
            userRoleRepository.ensureUserProfileAndSyncRole(currentUser)
                .onFailure { err ->
                    _syncMessage.value = toFriendlyVietnameseError(err, "Không thể đồng bộ hồ sơ.")
                }
        }
    }

    fun clearSyncMessage() {
        _syncMessage.value = null
    }

    private companion object {
        const val TAG = "AppSessionVM"
    }
}
