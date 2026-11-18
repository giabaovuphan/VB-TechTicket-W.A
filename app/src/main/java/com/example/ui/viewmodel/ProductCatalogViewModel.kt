package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.InventoryItem
import com.example.data.InventoryRepository
import com.example.data.ProductItem
import com.example.data.ProductRepository
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ProductCatalogViewModel(
    private val productRepository: ProductRepository,
    private val inventoryRepository: InventoryRepository
) : ViewModel() {

    private val roleTrigger = MutableStateFlow<UserRole?>(null)

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _feedback = MutableStateFlow<DetailActionFeedback?>(null)
    val feedback: StateFlow<DetailActionFeedback?> = _feedback.asStateFlow()

    val inventoryState: StateFlow<UiState<List<InventoryItem>>> =
        roleTrigger
            .flatMapLatest { role ->
                if (role == UserRole.TEAM_LEAD || role == UserRole.ADMIN) {
                    inventoryRepository.observeInventory(activeOnly = false)
                        .map<List<InventoryItem>, UiState<List<InventoryItem>>> { UiState.Success(it) }
                        .catch { error ->
                            val firestoreError = generateSequence(error) { it.cause }
                                .filterIsInstance<FirebaseFirestoreException>()
                                .firstOrNull()
                            if (firestoreError == null && error is CancellationException) throw error
                            val rootError = firestoreError ?: error
                            Log.w(TAG, "Error observing inventory", rootError)
                            emit(UiState.Error(toFriendlyVietnameseError(rootError, "Không thể tải danh sách kho linh kiện.")))
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

    val productsState: StateFlow<UiState<List<ProductItem>>> =
        roleTrigger
            .flatMapLatest { role ->
                if (role == UserRole.TEAM_LEAD || role == UserRole.ADMIN) {
                    productRepository.observeProducts(activeOnly = false)
                        .map<List<ProductItem>, UiState<List<ProductItem>>> { UiState.Success(it) }
                        .catch { error ->
                            val firestoreError = generateSequence(error) { it.cause }
                                .filterIsInstance<FirebaseFirestoreException>()
                                .firstOrNull()
                            if (firestoreError == null && error is CancellationException) throw error
                            val rootError = firestoreError ?: error
                            Log.w(TAG, "Error observing products", rootError)
                            emit(UiState.Error(toFriendlyVietnameseError(rootError, "Không thể tải danh mục linh kiện.")))
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
        roleTrigger.value = role
    }

    fun saveInventoryItem(
        existing: InventoryItem?,
        name: String,
        sku: String?,
        productId: String?,
        category: String,
        unit: String,
        unitPrice: Long,
        quantityOnHand: Long,
        minThreshold: Long,
        location: String?,
        note: String?,
        active: Boolean
    ) {
        if (_isSaving.value) return
        viewModelScope.launch {
            _isSaving.value = true
            inventoryRepository.upsertInventoryItem(
                existing = existing,
                name = name,
                sku = sku,
                productId = productId,
                category = category,
                unit = unit,
                unitPrice = unitPrice,
                quantityOnHand = quantityOnHand,
                minThreshold = minThreshold,
                location = location,
                note = note,
                active = active
            )
                .onSuccess {
                    _feedback.value = DetailActionFeedback(
                        message = if (existing == null) {
                            "Đã thêm vật tư \"$name\" vào kho."
                        } else {
                            "Đã cập nhật tồn kho \"$name\"."
                        },
                        isError = false
                    )
                }
                .onFailure { err ->
                    _feedback.value = DetailActionFeedback(
                        message = toFriendlyVietnameseError(err, "Không thể lưu vật tư kho."),
                        isError = true
                    )
                }
            _isSaving.value = false
        }
    }

    fun adjustInventoryStock(item: InventoryItem, delta: Long) {
        if (_isSaving.value) return
        viewModelScope.launch {
            _isSaving.value = true
            inventoryRepository.adjustStock(item, delta)
                .onSuccess { newQty ->
                    _feedback.value = DetailActionFeedback(
                        message = "Đã cập nhật tồn kho \"${item.name}\": còn $newQty ${item.unit}.",
                        isError = false
                    )
                }
                .onFailure { err ->
                    _feedback.value = DetailActionFeedback(
                        message = toFriendlyVietnameseError(err, "Không thể điều chỉnh tồn kho."),
                        isError = true
                    )
                }
            _isSaving.value = false
        }
    }

    fun toggleInventoryActive(item: InventoryItem, active: Boolean) {
        if (_isSaving.value) return
        viewModelScope.launch {
            _isSaving.value = true
            inventoryRepository.toggleActive(item, active)
                .onSuccess {
                    _feedback.value = DetailActionFeedback(
                        message = if (active) "Đã bật lại \"${item.name}\" trong kho." else "Đã tạm khóa xuất \"${item.name}\".",
                        isError = false
                    )
                }
                .onFailure { err ->
                    _feedback.value = DetailActionFeedback(
                        message = toFriendlyVietnameseError(err, "Không thể cập nhật trạng thái vật tư."),
                        isError = true
                    )
                }
            _isSaving.value = false
        }
    }

    fun deleteInventoryItem(item: InventoryItem) {
        if (_isSaving.value) return
        viewModelScope.launch {
            _isSaving.value = true
            inventoryRepository.deleteInventoryItem(item.id)
                .onSuccess {
                    _feedback.value = DetailActionFeedback(
                        message = "Đã xóa \"${item.name}\" khỏi kho.",
                        isError = false
                    )
                }
                .onFailure { err ->
                    _feedback.value = DetailActionFeedback(
                        message = toFriendlyVietnameseError(err, "Không thể xóa vật tư kho."),
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

    fun saveProduct(
        existingProduct: ProductItem?,
        name: String,
        sku: String?,
        category: String,
        unit: String,
        unitPrice: Long,
        note: String?,
        active: Boolean
    ) {
        if (_isSaving.value) return
        viewModelScope.launch {
            _isSaving.value = true
            productRepository.upsertProduct(
                existingProduct = existingProduct,
                name = name,
                sku = sku,
                category = category,
                unit = unit,
                unitPrice = unitPrice,
                note = note,
                active = active
            )
                .onSuccess {
                    _feedback.value = DetailActionFeedback(
                        message = if (existingProduct == null) {
                            "Đã thêm linh kiện \"$name\" vào danh mục."
                        } else {
                            "Đã cập nhật linh kiện \"$name\"."
                        },
                        isError = false
                    )
                }
                .onFailure { err ->
                    _feedback.value = DetailActionFeedback(
                        message = toFriendlyVietnameseError(err, "Không thể lưu thông tin linh kiện."),
                        isError = true
                    )
                }
            _isSaving.value = false
        }
    }

    fun toggleActive(product: ProductItem, active: Boolean) {
        if (_isSaving.value) return
        viewModelScope.launch {
            _isSaving.value = true
            productRepository.toggleProductActive(product, active)
                .onSuccess {
                    _feedback.value = DetailActionFeedback(
                        message = if (active) "Đã kích hoạt lại \"${product.name}\"." else "Đã tạm ẩn \"${product.name}\".",
                        isError = false
                    )
                }
                .onFailure { err ->
                    _feedback.value = DetailActionFeedback(
                        message = toFriendlyVietnameseError(err, "Không thể cập nhật trạng thái linh kiện."),
                        isError = true
                    )
                }
            _isSaving.value = false
        }
    }

    fun deleteProduct(product: ProductItem) {
        if (_isSaving.value) return
        viewModelScope.launch {
            _isSaving.value = true
            productRepository.deleteProduct(product.id)
                .onSuccess {
                    _feedback.value = DetailActionFeedback(
                        message = "Đã xóa linh kiện \"${product.name}\".",
                        isError = false
                    )
                }
                .onFailure { err ->
                    _feedback.value = DetailActionFeedback(
                        message = toFriendlyVietnameseError(err, "Không thể xóa linh kiện."),
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
        const val TAG = "ProductCatalogVM"
    }
}
