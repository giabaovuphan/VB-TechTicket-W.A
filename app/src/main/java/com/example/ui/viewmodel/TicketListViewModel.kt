package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.SupportTicket
import com.example.data.TicketCategories
import com.example.data.TicketPriority
import com.example.data.TicketRepository
import com.example.data.TicketStatus
import com.example.data.UiState
import com.example.data.UserAccount
import com.example.data.UserRole
import com.example.data.toFriendlyVietnameseError
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class TicketListViewModel(
    private val ticketRepository: TicketRepository,
    private val currentUserId: String
) : ViewModel() {

    private val activeRole = MutableStateFlow<UserRole?>(null)

    private val _selectedStatusFilter = MutableStateFlow<TicketStatus?>(null)
    val selectedStatusFilter: StateFlow<TicketStatus?> = _selectedStatusFilter.asStateFlow()

    private val _selectedPriorityFilter = MutableStateFlow<TicketPriority?>(null)
    val selectedPriorityFilter: StateFlow<TicketPriority?> = _selectedPriorityFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _createActionState = MutableStateFlow<CreateTicketUiState>(CreateTicketUiState.Idle)
    val createActionState: StateFlow<CreateTicketUiState> = _createActionState.asStateFlow()

    val ticketsState: StateFlow<UiState<List<SupportTicket>>> =
        activeRole
            .flatMapLatest { role ->
                if (role == null) {
                    kotlinx.coroutines.flow.flowOf<UiState<List<SupportTicket>>>(UiState.Loading)
                } else {
                    ticketRepository.observeTicketsForRole(currentUserId, role)
                        .map<List<SupportTicket>, UiState<List<SupportTicket>>> { UiState.Success(it) }
                        .catch { error ->
                            val firestoreError = generateSequence(error) { it.cause }
                                .filterIsInstance<FirebaseFirestoreException>()
                                .firstOrNull()
                            if (firestoreError == null && error is CancellationException) throw error
                            val rootError = firestoreError ?: error
                            Log.w(TAG, "Error observing tickets", rootError)
                            emit(UiState.Error(toFriendlyVietnameseError(rootError, "Không thể tải danh sách ticket.")))
                        }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    fun setRole(role: UserRole) {
        activeRole.value = role
    }

    fun setStatusFilter(status: TicketStatus?) {
        _selectedStatusFilter.value = status
    }

    fun setPriorityFilter(priority: TicketPriority?) {
        _selectedPriorityFilter.value = priority
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun createTicket(
        title: String,
        description: String,
        category: String = TicketCategories.first(),
        priority: TicketPriority = TicketPriority.MEDIUM,
        creator: UserAccount,
        defectPhotoBase64: String?
    ) {
        if (_createActionState.value is CreateTicketUiState.Submitting) return
        viewModelScope.launch {
            _createActionState.value = CreateTicketUiState.Submitting
            ticketRepository.createTicket(
                title = title,
                description = description,
                category = category,
                priority = priority,
                creator = creator,
                defectPhotoBase64 = defectPhotoBase64
            ).onSuccess { newId ->
                _createActionState.value = CreateTicketUiState.Created(newId)
            }.onFailure { err ->
                _createActionState.value = CreateTicketUiState.Error(
                    toFriendlyVietnameseError(err, "Không thể tạo ticket mới.")
                )
            }
        }
    }

    fun resetCreateState() {
        _createActionState.value = CreateTicketUiState.Idle
    }

    private companion object {
        const val TAG = "TicketListVM"
    }
}

sealed interface CreateTicketUiState {
    data object Idle : CreateTicketUiState
    data object Submitting : CreateTicketUiState
    data class Created(val ticketId: String) : CreateTicketUiState
    data class Error(val message: String) : CreateTicketUiState
}
