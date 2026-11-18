package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.InventoryRepository
import com.example.data.RoleAssignment
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class AdminViewModel(
    private val userRoleRepository: UserRoleRepository,
    private val inventoryRepository: InventoryRepository
) : ViewModel() {

    private val activeRole = MutableStateFlow<UserRole?>(null)

    private val _feedback = MutableStateFlow<DetailActionFeedback?>(null)
    val feedback: StateFlow<DetailActionFeedback?> = _feedback.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    val allUsersState: StateFlow<UiState<List<UserAccount>>> =
        activeRole
            .flatMapLatest { role ->
                if (role == UserRole.ADMIN || role == UserRole.TEAM_LEAD) {
                    userRoleRepository.observeAllUsers()
                        .map<List<UserAccount>, UiState<List<UserAccount>>> { UiState.Success(it) }
                        .catch { error ->
                            val firestoreError = generateSequence(error) { it.cause }
                                .filterIsInstance<FirebaseFirestoreException>()
                                .firstOrNull()
                            if (firestoreError == null && error is CancellationException) throw error
                            val rootError = firestoreError ?: error
                            Log.w(TAG, "Error observing users", rootError)
                            emit(UiState.Error(toFriendlyVietnameseError(rootError, "Không thể tải danh sách người dùng.")))
                        }
                } else {
                    flowOf(UiState.Success(emptyList()))
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    val roleAssignmentsState: StateFlow<UiState<List<RoleAssignment>>> =
        activeRole
            .flatMapLatest { role ->
                if (role == UserRole.ADMIN) {
                    userRoleRepository.observeRoleAssignments()
                        .map<List<RoleAssignment>, UiState<List<RoleAssignment>>> { UiState.Success(it) }
                        .catch { error ->
                            val firestoreError = generateSequence(error) { it.cause }
                                .filterIsInstance<FirebaseFirestoreException>()
                                .firstOrNull()
                            if (firestoreError == null && error is CancellationException) throw error
                            val rootError = firestoreError ?: error
                            Log.w(TAG, "Error observing role assignments", rootError)
                            emit(UiState.Error(toFriendlyVietnameseError(rootError, "Không thể tải danh sách phân quyền.")))
                        }
                } else {
                    flowOf(UiState.Success(emptyList()))
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

    fun assignRoleByEmail(email: String, displayName: String, role: UserRole, department: String?) {
        if (_isSaving.value) return
        viewModelScope.launch {
            _isSaving.value = true
            userRoleRepository.assignRoleByEmail(email, displayName, role, department)
                .onSuccess {
                    _feedback.value = DetailActionFeedback(
                        message = "Đã cấp quyền ${role.labelVi} cho $email.",
                        isError = false
                    )
                }
                .onFailure { err ->
                    _feedback.value = DetailActionFeedback(
                        message = toFriendlyVietnameseError(err, "Không thể gán vai trò."),
                        isError = true
                    )
                }
            _isSaving.value = false
        }
    }

    fun updateUserRoleAndActive(targetUser: UserAccount, role: UserRole, active: Boolean, department: String?) {
        if (_isSaving.value) return
        viewModelScope.launch {
            _isSaving.value = true
            userRoleRepository.updateUserRoleAndActive(targetUser, role, active, department)
                .onSuccess {
                    _feedback.value = DetailActionFeedback(
                        message = "Đã cập nhật quyền ${role.labelVi} cho ${targetUser.displayName}.",
                        isError = false
                    )
                }
                .onFailure { err ->
                    _feedback.value = DetailActionFeedback(
                        message = toFriendlyVietnameseError(err, "Không thể cập nhật tài khoản."),
                        isError = true
                    )
                }
            _isSaving.value = false
        }
    }

    fun approvePendingUser(targetUser: UserAccount, role: UserRole) {
        if (_isSaving.value) return
        viewModelScope.launch {
            _isSaving.value = true
            userRoleRepository.approvePendingUser(targetUser, role)
                .onSuccess {
                    _feedback.value = DetailActionFeedback(
                        message = "Đã duyệt và cấp quyền ${role.labelVi} cho ${targetUser.displayName}.",
                        isError = false
                    )
                }
                .onFailure { err ->
                    _feedback.value = DetailActionFeedback(
                        message = toFriendlyVietnameseError(err, "Không thể duyệt yêu cầu cấp quyền."),
                        isError = true
                    )
                }
            _isSaving.value = false
        }
    }

    fun rejectPendingUser(targetUser: UserAccount, reason: String?) {
        if (_isSaving.value) return
        viewModelScope.launch {
            _isSaving.value = true
            userRoleRepository.rejectPendingUser(targetUser, reason)
                .onSuccess {
                    _feedback.value = DetailActionFeedback(
                        message = "Đã từ chối yêu cầu truy cập của ${targetUser.displayName}.",
                        isError = false
                    )
                }
                .onFailure { err ->
                    _feedback.value = DetailActionFeedback(
                        message = toFriendlyVietnameseError(err, "Không thể từ chối yêu cầu."),
                        isError = true
                    )
                }
            _isSaving.value = false
        }
    }

    fun removeAssignment(email: String) {
        if (_isSaving.value) return
        viewModelScope.launch {
            _isSaving.value = true
            userRoleRepository.removeRoleAssignment(email)
                .onSuccess {
                    _feedback.value = DetailActionFeedback(
                        message = "Đã xóa bản ghi phân quyền của $email.",
                        isError = false
                    )
                }
                .onFailure { err ->
                    _feedback.value = DetailActionFeedback(
                        message = toFriendlyVietnameseError(err, "Không thể xóa phân quyền."),
                        isError = true
                    )
                }
            _isSaving.value = false
        }
    }

    fun seedStarterDatabase(admin: UserAccount) {
        if (_isSaving.value) return
        viewModelScope.launch {
            _isSaving.value = true
            inventoryRepository.seedStarterDatabase(admin)
                .onSuccess { summary ->
                    val total = summary.inventoryCreated + summary.productsCreated + summary.ticketsCreated
                    _feedback.value = DetailActionFeedback(
                        message = if (total == 0) {
                            "Dữ liệu mẫu Firestore đã được khởi tạo trước đó."
                        } else {
                            "Đã khởi tạo dữ liệu mẫu: +${summary.inventoryCreated} kho, +${summary.productsCreated} bảng giá, +${summary.ticketsCreated} ticket."
                        },
                        isError = false
                    )
                }
                .onFailure { err ->
                    _feedback.value = DetailActionFeedback(
                        message = toFriendlyVietnameseError(err, "Không thể khởi tạo dữ liệu mẫu."),
                        isError = true
                    )
                }
            _isSaving.value = false
        }
    }

    fun clearFeedback() {
        _feedback.value = null
    }

    private companion object {
        const val TAG = "AdminVM"
    }
}
