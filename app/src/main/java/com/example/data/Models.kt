package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.text.NumberFormat
import java.util.Locale

const val ROOT_ADMIN_EMAIL = "giabao.vuphan@gmail.com"

enum class UserRole(val labelVi: String, val shortLabelVi: String) {
    PENDING("Chờ cấp quyền", "Chờ duyệt"),
    USER("Người dùng", "Người dùng"),
    TECHNICIAN("Nhân viên kỹ thuật", "Kỹ thuật viên"),
    TEAM_LEAD("Trưởng nhóm kỹ thuật", "Trưởng nhóm"),
    ADMIN("Quản trị viên", "Quản trị viên");

    companion object {
        fun fromRaw(raw: String?): UserRole =
            entries.firstOrNull { it.name == raw } ?: PENDING

        fun assignableRoles(isRootAdmin: Boolean): List<UserRole> =
            if (isRootAdmin) {
                listOf(USER, TECHNICIAN, TEAM_LEAD, ADMIN)
            } else {
                listOf(USER, TECHNICIAN, TEAM_LEAD)
            }
    }
}

enum class AccountApprovalStatus(val labelVi: String) {
    PENDING("Chờ cấp quyền"),
    APPROVED("Đã kích hoạt"),
    REJECTED("Đã từ chối");

    companion object {
        fun fromRaw(raw: String?, currentRole: UserRole, active: Boolean): AccountApprovalStatus =
            when (raw) {
                REJECTED.name -> REJECTED
                APPROVED.name -> APPROVED
                PENDING.name -> PENDING
                else -> if (currentRole == UserRole.PENDING) PENDING else APPROVED
            }
    }
}

enum class TicketPriority(val labelVi: String, val sortOrder: Int) {
    LOW("Thấp", 1),
    MEDIUM("Trung bình", 2),
    HIGH("Cao", 3),
    URGENT("Khẩn cấp", 4);

    companion object {
        fun fromRaw(raw: String?): TicketPriority =
            entries.firstOrNull { it.name == raw } ?: MEDIUM
    }
}

enum class TicketStatus(val labelVi: String, val stepIndex: Int) {
    OPEN("Mở", 0),
    ACCEPTED("Đã tiếp nhận", 1),
    IN_PROGRESS("Đang xử lý", 2),
    COMPLETED("Đã hoàn thành", 3);

    companion object {
        fun fromRaw(raw: String?): TicketStatus =
            entries.firstOrNull { it.name == raw } ?: OPEN
    }
}

enum class InvoicePaymentStatus(val labelVi: String) {
    UNPAID("Chưa thanh toán"),
    PAID("Đã thanh toán");

    companion object {
        fun fromRaw(raw: String?): InvoicePaymentStatus =
            entries.firstOrNull { it.name == raw } ?: UNPAID
    }
}

enum class InvoicePaymentMethod(val labelVi: String) {
    CASH("Tiền mặt"),
    TRANSFER("Chuyển khoản"),
    INTERNAL("Nội bộ / Bảo hành");

    companion object {
        fun fromRaw(raw: String?): InvoicePaymentMethod =
            entries.firstOrNull { it.name == raw } ?: CASH
    }
}

val TicketCategories = listOf(
    "Phần cứng",
    "Phần mềm",
    "Mạng & Kết nối",
    "Thiết bị ngoại vi",
    "Hệ thống máy chủ",
    "Khác"
)

val ProductUnits = listOf("Cái", "Bộ", "Thanh", "Mét", "Hộp", "Lần")

private val vndFormatter = NumberFormat.getNumberInstance(Locale("vi", "VN"))

fun formatVnd(amount: Long): String = "${vndFormatter.format(amount)} đ"

@Keep
@IgnoreExtraProperties
data class UserAccount(
    @DocumentId val id: String = "",
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val role: String = UserRole.PENDING.name,
    val active: Boolean = false,
    val approvalStatus: String = "",
    val rejectionReason: String? = null,
    val approvedByUid: String? = null,
    val department: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    @get:Exclude
    val parsedRole: UserRole get() = UserRole.fromRaw(role)

    @get:Exclude
    val parsedApprovalStatus: AccountApprovalStatus
        get() = AccountApprovalStatus.fromRaw(approvalStatus, parsedRole, active)

    @get:Exclude
    val isRootAdmin: Boolean
        get() = email.trim().lowercase() == ROOT_ADMIN_EMAIL

    @get:Exclude
    val isApprovedAndActive: Boolean
        get() = active && parsedRole != UserRole.PENDING && parsedApprovalStatus == AccountApprovalStatus.APPROVED
}

@Keep
@IgnoreExtraProperties
data class RoleAssignment(
    @DocumentId val id: String = "",
    val email: String = "",
    val displayName: String = "",
    val role: String = UserRole.USER.name,
    val department: String? = null,
    val assignedByUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val parsedRole: UserRole get() = UserRole.fromRaw(role)
}

@Keep
@IgnoreExtraProperties
data class SupportTicket(
    @DocumentId val id: String = "",
    val ticketCode: String = "",
    val title: String = "",
    val description: String = "",
    val category: String = TicketCategories.first(),
    val priority: String = TicketPriority.MEDIUM.name,
    val status: String = TicketStatus.OPEN.name,
    val creatorUid: String = "",
    val creatorName: String = "",
    val creatorEmail: String = "",
    val assigneeUid: String? = null,
    val assigneeName: String? = null,
    val teamLeadUid: String? = null,
    val defectPhotoBase64: String? = null,
    val resolvedPhotoBase64: String? = null,
    val resolutionNote: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
    val completedAt: Timestamp? = null
) {
    val parsedPriority: TicketPriority get() = TicketPriority.fromRaw(priority)
    val parsedStatus: TicketStatus get() = TicketStatus.fromRaw(status)
}

@Keep
@IgnoreExtraProperties
data class TicketNotification(
    @DocumentId val id: String = "",
    val recipientUid: String = "",
    val senderUid: String = "",
    val ticketId: String = "",
    val ticketCode: String = "",
    val title: String = "",
    val message: String = "",
    val newStatus: String = TicketStatus.OPEN.name,
    val readFlag: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    val parsedStatus: TicketStatus get() = TicketStatus.fromRaw(newStatus)
}

@Keep
@IgnoreExtraProperties
data class ProductItem(
    @DocumentId val id: String = "",
    val name: String = "",
    val sku: String? = null,
    val category: String = TicketCategories.first(),
    val unit: String = ProductUnits.first(),
    val unitPrice: Long = 0L,
    val note: String? = null,
    val active: Boolean = true,
    val createdByUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

enum class StockAlertLevel(val labelVi: String) {
    OK("Đủ hàng"),
    LOW("Sắp hết"),
    OUT("Hết hàng")
}

@Keep
@IgnoreExtraProperties
data class InventoryItem(
    @DocumentId val id: String = "",
    val name: String = "",
    val sku: String? = null,
    val productId: String? = null,
    val category: String = TicketCategories.first(),
    val unit: String = ProductUnits.first(),
    val unitPrice: Long = 0L,
    val quantityOnHand: Long = 0L,
    val minThreshold: Long = 3L,
    val location: String? = null,
    val note: String? = null,
    val active: Boolean = true,
    val updatedByUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    @get:Exclude
    val alertLevel: StockAlertLevel
        get() = when {
            quantityOnHand <= 0L -> StockAlertLevel.OUT
            quantityOnHand <= minThreshold -> StockAlertLevel.LOW
            else -> StockAlertLevel.OK
        }
}

@Keep
@IgnoreExtraProperties
data class InvoiceLineItem(
    val productId: String? = null,
    val inventoryId: String? = null,
    val name: String = "",
    val unit: String = ProductUnits.first(),
    val quantity: Int = 1,
    val unitPrice: Long = 0L,
    val lineTotal: Long = 0L,
    val customEntry: Boolean = false
)

@Keep
@IgnoreExtraProperties
data class RepairInvoice(
    @DocumentId val id: String = "",
    val invoiceCode: String = "",
    val ticketId: String = "",
    val ticketCode: String = "",
    val creatorUid: String = "",
    val assigneeUid: String? = null,
    val items: List<InvoiceLineItem> = emptyList(),
    val partsSubtotal: Long = 0L,
    val laborFee: Long = 0L,
    val totalAmount: Long = 0L,
    val paymentStatus: String = InvoicePaymentStatus.UNPAID.name,
    val paymentMethod: String = InvoicePaymentMethod.CASH.name,
    val note: String? = null,
    val updatedByUid: String = "",
    val updatedByName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
    val paidAt: Timestamp? = null
) {
    val parsedPaymentStatus: InvoicePaymentStatus get() = InvoicePaymentStatus.fromRaw(paymentStatus)
    val parsedPaymentMethod: InvoicePaymentMethod get() = InvoicePaymentMethod.fromRaw(paymentMethod)
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
