package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class InvoiceRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("Vui lòng đăng nhập trước khi thao tác hóa đơn.")

    fun observeInvoice(ticketId: String): Flow<RepairInvoice?> =
        db.collection("invoices").document(ticketId)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(RepairInvoice::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else {
                    null
                }
            }

    suspend fun saveInvoice(
        ticket: SupportTicket,
        existingInvoice: RepairInvoice?,
        items: List<InvoiceLineItem>,
        laborFee: Long,
        paymentStatus: InvoicePaymentStatus,
        paymentMethod: InvoicePaymentMethod,
        note: String?,
        actor: UserAccount
    ): Result<Unit> {
        val docPath = "invoices/${ticket.id}"
        return try {
            val actorUid = requireUserId()
            require(ticket.id.isNotBlank()) { "Mã ticket không hợp lệ." }
            require(items.size <= 50) { "Hóa đơn tối đa 50 dòng linh kiện." }
            require(laborFee in 0L..10_000_000_000L) { "Phí công sửa chữa không hợp lệ." }

            val normalizedItems = items.map { item ->
                val cleanName = item.name.trim()
                val cleanUnit = item.unit.trim().ifBlank { ProductUnits.first() }.take(30)
                require(cleanName.length in 1..120) { "Tên linh kiện trong hóa đơn phải từ 1 đến 120 ký tự." }
                require(item.quantity in 1..9999) { "Số lượng linh kiện '$cleanName' phải từ 1 trở lên." }
                require(item.unitPrice in 0L..1_000_000_000L) { "Đơn giá linh kiện '$cleanName' không hợp lệ." }
                val lineTotal = item.unitPrice * item.quantity
                item.copy(
                    name = cleanName,
                    unit = cleanUnit,
                    lineTotal = lineTotal
                )
            }

            require(normalizedItems.isNotEmpty() || laborFee > 0L || paymentMethod == InvoicePaymentMethod.INTERNAL) {
                "Vui lòng thêm ít nhất 1 linh kiện hoặc nhập phí công sửa chữa."
            }

            val partsSubtotal = normalizedItems.sumOf { it.lineTotal }
            val totalAmount = partsSubtotal + laborFee
            val cleanNote = note?.trim()?.takeIf { it.isNotBlank() }?.take(500)
            val actorName = actor.displayName.ifBlank { "Nhân viên kỹ thuật" }.take(100)

            val itemMaps = normalizedItems.map { item ->
                mapOf(
                    "productId" to item.productId?.takeIf { it.isNotBlank() },
                    "inventoryId" to item.inventoryId?.takeIf { it.isNotBlank() },
                    "name" to item.name,
                    "unit" to item.unit,
                    "quantity" to item.quantity,
                    "unitPrice" to item.unitPrice,
                    "lineTotal" to item.lineTotal,
                    "customEntry" to item.customEntry
                ).filterValues { it != null }
            }

            val oldQtyByInv = mutableMapOf<String, Int>()
            existingInvoice?.items?.forEach { item ->
                val invId = item.inventoryId?.takeIf { it.isNotBlank() }
                if (invId != null) {
                    oldQtyByInv[invId] = (oldQtyByInv[invId] ?: 0) + item.quantity
                }
            }
            val newQtyByInv = mutableMapOf<String, Int>()
            normalizedItems.forEach { item ->
                val invId = item.inventoryId?.takeIf { it.isNotBlank() }
                if (invId != null) {
                    newQtyByInv[invId] = (newQtyByInv[invId] ?: 0) + item.quantity
                }
            }
            val inventoryDeltas = (oldQtyByInv.keys + newQtyByInv.keys).mapNotNull { invId ->
                val delta = (newQtyByInv[invId] ?: 0) - (oldQtyByInv[invId] ?: 0)
                if (delta != 0) invId to delta.toLong() else null
            }.toMap()

            if (actor.parsedRole == UserRole.TECHNICIAN && inventoryDeltas.values.any { it < 0L }) {
                throw IllegalStateException(
                    "Chỉ Trưởng nhóm hoặc Quản trị viên mới có quyền hoàn trả số lượng linh kiện về kho khi giảm mục trên hóa đơn."
                )
            }

            val docRef = db.collection("invoices").document(ticket.id)

            if (existingInvoice == null) {
                val suffix = ticket.ticketCode.removePrefix("TK-").takeIf { it.length >= 2 }
                    ?: (1000..9999).random().toString()
                val invoiceCode = "HD-$suffix".take(24)

                @Suppress("UNCHECKED_CAST")
                val payload = mapOf(
                    "invoiceCode" to invoiceCode,
                    "ticketId" to ticket.id,
                    "ticketCode" to ticket.ticketCode,
                    "creatorUid" to ticket.creatorUid,
                    "assigneeUid" to ticket.assigneeUid?.takeIf { it.isNotBlank() },
                    "items" to itemMaps,
                    "partsSubtotal" to partsSubtotal,
                    "laborFee" to laborFee,
                    "totalAmount" to totalAmount,
                    "paymentStatus" to paymentStatus.name,
                    "paymentMethod" to paymentMethod.name,
                    "note" to cleanNote,
                    "updatedByUid" to actorUid,
                    "updatedByName" to actorName,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "paidAt" to if (paymentStatus == InvoicePaymentStatus.PAID) FieldValue.serverTimestamp() else null
                ).filterValues { it != null } as Map<String, Any>

                if (inventoryDeltas.isEmpty()) {
                    docRef.set(payload).await()
                } else {
                    db.runTransaction { transaction ->
                        val invRefsAndNext = inventoryDeltas.map { (invId, delta) ->
                            val invRef = db.collection("inventory").document(invId)
                            val invSnap = transaction.get(invRef)
                            if (!invSnap.exists()) {
                                throw IllegalStateException("Vật tư trong kho không còn tồn tại.")
                            }
                            val invName = invSnap.getString("name") ?: invId
                            val currentQty = (invSnap.get("quantityOnHand") as? Number)?.toLong() ?: 0L
                            val nextQty = currentQty - delta
                            if (nextQty < 0L) {
                                throw IllegalStateException("Tồn kho \"$invName\" chỉ còn $currentQty, không đủ xuất $delta.")
                            }
                            invRef to nextQty
                        }
                        transaction.set(docRef, payload)
                        invRefsAndNext.forEach { (invRef, nextQty) ->
                            transaction.update(
                                invRef,
                                mapOf(
                                    "quantityOnHand" to nextQty,
                                    "updatedByUid" to actorUid,
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            )
                        }
                    }.await()
                }

                if (ticket.creatorUid != actorUid) {
                    sendInvoiceNotification(
                        recipientUid = ticket.creatorUid,
                        senderUid = actorUid,
                        ticket = ticket,
                        title = "Hóa đơn ${ticket.ticketCode} ($invoiceCode)",
                        message = if (paymentStatus == InvoicePaymentStatus.PAID) {
                            "Đã lập và xác nhận thanh toán hóa đơn sửa chữa ${formatVnd(totalAmount)}."
                        } else {
                            "Đã lập hóa đơn chi phí sửa chữa ${formatVnd(totalAmount)} (${paymentStatus.labelVi})."
                        }
                    )
                }
            } else {
                val updates = mutableMapOf<String, Any>(
                    "items" to itemMaps,
                    "partsSubtotal" to partsSubtotal,
                    "laborFee" to laborFee,
                    "totalAmount" to totalAmount,
                    "paymentStatus" to paymentStatus.name,
                    "paymentMethod" to paymentMethod.name,
                    "updatedByUid" to actorUid,
                    "updatedByName" to actorName,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (!ticket.assigneeUid.isNullOrBlank()) {
                    updates["assigneeUid"] = ticket.assigneeUid
                }
                if (cleanNote != null) {
                    updates["note"] = cleanNote
                } else if (existingInvoice.note != null) {
                    updates["note"] = FieldValue.delete()
                }
                if (paymentStatus == InvoicePaymentStatus.PAID) {
                    updates["paidAt"] = existingInvoice.paidAt ?: FieldValue.serverTimestamp()
                } else if (existingInvoice.paidAt != null) {
                    updates["paidAt"] = FieldValue.delete()
                }

                if (inventoryDeltas.isEmpty()) {
                    docRef.update(updates).await()
                } else {
                    db.runTransaction { transaction ->
                        val invRefsAndNext = inventoryDeltas.map { (invId, delta) ->
                            val invRef = db.collection("inventory").document(invId)
                            val invSnap = transaction.get(invRef)
                            if (!invSnap.exists()) {
                                throw IllegalStateException("Vật tư trong kho không còn tồn tại.")
                            }
                            val invName = invSnap.getString("name") ?: invId
                            val currentQty = (invSnap.get("quantityOnHand") as? Number)?.toLong() ?: 0L
                            val nextQty = currentQty - delta
                            if (nextQty < 0L) {
                                throw IllegalStateException("Tồn kho \"$invName\" chỉ còn $currentQty, không đủ xuất thêm $delta.")
                            }
                            invRef to nextQty
                        }
                        transaction.update(docRef, updates)
                        invRefsAndNext.forEach { (invRef, nextQty) ->
                            transaction.update(
                                invRef,
                                mapOf(
                                    "quantityOnHand" to nextQty,
                                    "updatedByUid" to actorUid,
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            )
                        }
                    }.await()
                }

                val newlyPaid = existingInvoice.parsedPaymentStatus != InvoicePaymentStatus.PAID &&
                    paymentStatus == InvoicePaymentStatus.PAID
                if (ticket.creatorUid != actorUid && (newlyPaid || existingInvoice.totalAmount != totalAmount)) {
                    sendInvoiceNotification(
                        recipientUid = ticket.creatorUid,
                        senderUid = actorUid,
                        ticket = ticket,
                        title = if (newlyPaid) {
                            "Đã thanh toán ${existingInvoice.invoiceCode}"
                        } else {
                            "Cập nhật hóa đơn ${existingInvoice.invoiceCode}"
                        },
                        message = if (newlyPaid) {
                            "Hóa đơn ${existingInvoice.invoiceCode} (${formatVnd(totalAmount)}) đã thanh toán thành công."
                        } else {
                            "Tổng chi phí sửa chữa ticket ${ticket.ticketCode} cập nhật thành ${formatVnd(totalAmount)}."
                        }
                    )
                }
            }

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(
                e,
                if (existingInvoice == null) OperationType.CREATE else OperationType.UPDATE,
                docPath
            )
            Result.failure(e)
        }
    }

    suspend fun markAsPaid(
        ticket: SupportTicket,
        invoice: RepairInvoice,
        paymentMethod: InvoicePaymentMethod,
        actor: UserAccount
    ): Result<Unit> {
        val docPath = "invoices/${ticket.id}"
        return try {
            val actorUid = requireUserId()
            require(ticket.id.isNotBlank()) { "Mã ticket không hợp lệ." }
            val actorName = actor.displayName.ifBlank { "Nhân viên kỹ thuật" }.take(100)

            val updates = mapOf(
                "paymentStatus" to InvoicePaymentStatus.PAID.name,
                "paymentMethod" to paymentMethod.name,
                "paidAt" to FieldValue.serverTimestamp(),
                "updatedByUid" to actorUid,
                "updatedByName" to actorName,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("invoices").document(ticket.id).update(updates).await()

            if (ticket.creatorUid != actorUid) {
                sendInvoiceNotification(
                    recipientUid = ticket.creatorUid,
                    senderUid = actorUid,
                    ticket = ticket,
                    title = "Đã thanh toán ${invoice.invoiceCode}",
                    message = "Hóa đơn sửa chữa ${formatVnd(invoice.totalAmount)} (${paymentMethod.labelVi}) đã được xác nhận thanh toán."
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    private suspend fun sendInvoiceNotification(
        recipientUid: String,
        senderUid: String,
        ticket: SupportTicket,
        title: String,
        message: String
    ) {
        if (recipientUid.isBlank() || ticket.id.isBlank()) return
        val notifRef = db.collection("notifications").document()
        val payload = mapOf(
            "recipientUid" to recipientUid,
            "senderUid" to senderUid,
            "ticketId" to ticket.id,
            "ticketCode" to ticket.ticketCode,
            "title" to title.take(120),
            "message" to message.take(300),
            "newStatus" to ticket.status,
            "readFlag" to false,
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            notifRef.set(payload).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, notifRef.path)
        }
    }
}
