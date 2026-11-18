package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.InventoryItem
import com.example.data.InventoryRepository
import com.example.data.InvoiceLineItem
import com.example.data.InvoicePaymentMethod
import com.example.data.InvoicePaymentStatus
import com.example.data.InvoiceRepository
import com.example.data.ProductItem
import com.example.data.ProductRepository
import com.example.data.RepairInvoice
import com.example.data.SupportTicket
import com.example.data.TicketPriority
import com.example.data.TicketRepository
import com.example.data.TicketStatus
import com.example.data.UiState
import com.example.data.UserAccount
import com.example.data.UserRole
import com.example.data.UserRoleRepository
import com.example.data.toFriendlyVietnameseError
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class TicketDetailViewModel(
    private val ticketRepository: TicketRepository,
    private val userRoleRepository: UserRoleRepository,
    private val invoiceRepository: InvoiceRepository,
    private val productRepository: ProductRepository,
    private val inventoryRepository: InventoryRepository,
    private val ticketId: String
) : ViewModel() {

    private val roleTrigger = MutableStateFlow<UserRole?>(null)

    private val _actionFeedback = MutableStateFlow<DetailActionFeedback?>(null)
    val actionFeedback: StateFlow<DetailActionFeedback?> = _actionFeedback.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _ticketDeleted = MutableStateFlow(false)
    val ticketDeleted: StateFlow<Boolean> = _ticketDeleted.asStateFlow()

    val ticketState: StateFlow<UiState<SupportTicket>> =
        ticketRepository.observeTicket(ticketId)
            .map<SupportTicket?, UiState<SupportTicket>> { ticket ->
                if (ticket != null) UiState.Success(ticket)
                else UiState.Error("Ticket không tồn tại hoặc đã bị xóa.")
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing ticket $ticketId", rootError)
                emit(UiState.Error(toFriendlyVietnameseError(rootError, "Không thể tải chi tiết ticket.")))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    val invoiceState: StateFlow<UiState<RepairInvoice?>> =
        invoiceRepository.observeInvoice(ticketId)
            .map<RepairInvoice?, UiState<RepairInvoice?>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing invoice $ticketId", rootError)
                emit(UiState.Error(toFriendlyVietnameseError(rootError, "Không thể tải hóa đơn sửa chữa.")))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    val techniciansState: StateFlow<List<UserAccount>> =
        roleTrigger
            .flatMapLatest { role ->
                if (role == UserRole.TEAM_LEAD || role == UserRole.ADMIN) {
                    userRoleRepository.observeTechnicians()
                        .catch { error ->
                            val firestoreError = generateSequence(error) { it.cause }
                                .filterIsInstance<FirebaseFirestoreException>()
                                .firstOrNull()
                            if (firestoreError == null && error is CancellationException) throw error
                            Log.w(TAG, "Error observing technicians", error)
                            emit(emptyList())
                        }
                } else {
                    flowOf(emptyList())
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = emptyList()
            )

    val activeProductsState: StateFlow<List<ProductItem>> =
        roleTrigger
            .flatMapLatest { role ->
                if (role == UserRole.TECHNICIAN || role == UserRole.TEAM_LEAD || role == UserRole.ADMIN) {
                    productRepository.observeProducts(activeOnly = true)
                        .catch { error ->
                            val firestoreError = generateSequence(error) { it.cause }
                                .filterIsInstance<FirebaseFirestoreException>()
                                .firstOrNull()
                            if (firestoreError == null && error is CancellationException) throw error
                            Log.w(TAG, "Error observing active products", error)
                            emit(emptyList())
                        }
                } else {
                    flowOf(emptyList())
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = emptyList()
            )

    val activeInventoryState: StateFlow<List<InventoryItem>> =
        roleTrigger
            .flatMapLatest { role ->
                if (role == UserRole.TECHNICIAN || role == UserRole.TEAM_LEAD || role == UserRole.ADMIN) {
                    inventoryRepository.observeInventory(activeOnly = true)
                        .catch { error ->
                            val firestoreError = generateSequence(error) { it.cause }
                                .filterIsInstance<FirebaseFirestoreException>()
                                .firstOrNull()
                            if (firestoreError == null && error is CancellationException) throw error
                            Log.w(TAG, "Error observing active inventory", error)
                            emit(emptyList())
                        }
                } else {
                    flowOf(emptyList())
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = emptyList()
            )

    fun setCurrentUserRole(role: UserRole) {
        roleTrigger.value = role
    }

    fun assignTicket(ticket: SupportTicket, technician: UserAccount, priority: TicketPriority) {
        if (_isProcessing.value) return
        viewModelScope.launch {
            _isProcessing.value = true
            ticketRepository.assignTicket(ticket, technician, priority)
                .onSuccess {
                    _actionFeedback.value = DetailActionFeedback(
                        message = "Đã phân công cho ${technician.displayName} và cập nhật mức ${priority.labelVi}.",
                        isError = false
                    )
                }
                .onFailure { err ->
                    _actionFeedback.value = DetailActionFeedback(
                        message = toFriendlyVietnameseError(err, "Không thể phân công ticket."),
                        isError = true
                    )
                }
            _isProcessing.value = false
        }
    }

    fun startProgress(ticket: SupportTicket, actor: UserAccount, note: String? = null) {
        if (_isProcessing.value) return
        viewModelScope.launch {
            _isProcessing.value = true
            ticketRepository.startProcessing(ticket, actor, note)
                .onSuccess {
                    _actionFeedback.value = DetailActionFeedback(
                        message = "Đã chuyển trạng thái sang Đang xử lý.",
                        isError = false
                    )
                }
                .onFailure { err ->
                    _actionFeedback.value = DetailActionFeedback(
                        message = toFriendlyVietnameseError(err, "Không thể cập nhật trạng thái."),
                        isError = true
                    )
                }
            _isProcessing.value = false
        }
    }

    fun completeTicket(ticket: SupportTicket, actor: UserAccount, resolvedPhotoBase64: String, note: String) {
        if (_isProcessing.value) return
        viewModelScope.launch {
            _isProcessing.value = true
            ticketRepository.completeTicket(ticket, actor, resolvedPhotoBase64, note)
                .onSuccess {
                    _actionFeedback.value = DetailActionFeedback(
                        message = "Đã hoàn thành ticket và lưu ảnh đối soát.",
                        isError = false
                    )
                }
                .onFailure { err ->
                    _actionFeedback.value = DetailActionFeedback(
                        message = toFriendlyVietnameseError(err, "Không thể hoàn thành ticket."),
                        isError = true
                    )
                }
            _isProcessing.value = false
        }
    }

    fun adminOverrideStatus(ticket: SupportTicket, status: TicketStatus, priority: TicketPriority) {
        if (_isProcessing.value) return
        viewModelScope.launch {
            _isProcessing.value = true
            ticketRepository.adminForceStatus(ticket, status, priority)
                .onSuccess {
                    _actionFeedback.value = DetailActionFeedback(
                        message = "Đã cập nhật trạng thái thành ${status.labelVi}.",
                        isError = false
                    )
                }
                .onFailure { err ->
                    _actionFeedback.value = DetailActionFeedback(
                        message = toFriendlyVietnameseError(err, "Không thể điều chỉnh trạng thái."),
                        isError = true
                    )
                }
            _isProcessing.value = false
        }
    }

    fun deleteTicket(ticket: SupportTicket) {
        if (_isProcessing.value) return
        viewModelScope.launch {
            _isProcessing.value = true
            ticketRepository.deleteTicket(ticket)
                .onSuccess {
                    _ticketDeleted.value = true
                }
                .onFailure { err ->
                    _actionFeedback.value = DetailActionFeedback(
                        message = toFriendlyVietnameseError(err, "Không thể xóa ticket."),
                        isError = true
                    )
                }
            _isProcessing.value = false
        }
    }

    fun saveInvoice(
        ticket: SupportTicket,
        existingInvoice: RepairInvoice?,
        items: List<InvoiceLineItem>,
        laborFee: Long,
        paymentStatus: InvoicePaymentStatus,
        paymentMethod: InvoicePaymentMethod,
        note: String?,
        actor: UserAccount
    ) {
        if (_isProcessing.value) return
        viewModelScope.launch {
            _isProcessing.value = true
            invoiceRepository.saveInvoice(
                ticket = ticket,
                existingInvoice = existingInvoice,
                items = items,
                laborFee = laborFee,
                paymentStatus = paymentStatus,
                paymentMethod = paymentMethod,
                note = note,
                actor = actor
            )
                .onSuccess {
                    _actionFeedback.value = DetailActionFeedback(
                        message = if (existingInvoice == null) {
                            "Đã lập hóa đơn sửa chữa cho ticket #${ticket.ticketCode}."
                        } else {
                            "Đã cập nhật hóa đơn ${existingInvoice.invoiceCode}."
                        },
                        isError = false
                    )
                }
                .onFailure { err ->
                    _actionFeedback.value = DetailActionFeedback(
                        message = toFriendlyVietnameseError(err, "Không thể lưu hóa đơn."),
                        isError = true
                    )
                }
            _isProcessing.value = false
        }
    }

    fun markInvoicePaid(
        ticket: SupportTicket,
        invoice: RepairInvoice,
        paymentMethod: InvoicePaymentMethod,
        actor: UserAccount
    ) {
        if (_isProcessing.value) return
        viewModelScope.launch {
            _isProcessing.value = true
            invoiceRepository.markAsPaid(ticket, invoice, paymentMethod, actor)
                .onSuccess {
                    _actionFeedback.value = DetailActionFeedback(
                        message = "Đã xác nhận thanh toán hóa đơn ${invoice.invoiceCode}.",
                        isError = false
                    )
                }
                .onFailure { err ->
                    _actionFeedback.value = DetailActionFeedback(
                        message = toFriendlyVietnameseError(err, "Không thể xác nhận thanh toán."),
                        isError = true
                    )
                }
            _isProcessing.value = false
        }
    }

    fun clearFeedback() {
        _actionFeedback.value = null
    }

    private companion object {
        const val TAG = "TicketDetailVM"
    }
}

data class DetailActionFeedback(
    val message: String,
    val isError: Boolean
)
