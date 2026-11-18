package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class TicketRepository(
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
            ?: throw IllegalStateException("Vui lòng đăng nhập trước khi thao tác ticket.")

    fun observeTicketsForRole(uid: String, role: UserRole): Flow<List<SupportTicket>> {
        val query = when (role) {
            UserRole.PENDING -> return flowOf(emptyList())
            UserRole.USER -> db.collection("tickets").whereEqualTo("creatorUid", uid)
            UserRole.TECHNICIAN -> db.collection("tickets").whereEqualTo("assigneeUid", uid)
            UserRole.TEAM_LEAD, UserRole.ADMIN -> db.collection("tickets")
        }
        return query.snapshots().map { snapshot ->
            snapshot.toObjects(SupportTicket::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
        }
    }

    fun observeTicket(ticketId: String): Flow<SupportTicket?> =
        db.collection("tickets").document(ticketId)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(SupportTicket::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else {
                    null
                }
            }

    suspend fun createTicket(
        title: String,
        description: String,
        category: String,
        priority: TicketPriority,
        creator: UserAccount,
        defectPhotoBase64: String?
    ): Result<String> {
        var docPath = "tickets"
        return try {
            val uid = requireUserId()
            val cleanTitle = title.trim()
            val cleanDesc = description.trim()
            require(cleanTitle.length in 3..120) { "Tiêu đề phải từ 3 đến 120 ký tự." }
            require(cleanDesc.length in 5..2000) { "Mô tả lỗi phải từ 5 đến 2000 ký tự." }
            if (defectPhotoBase64 != null) {
                require(defectPhotoBase64.length <= 300_000) { "Ảnh đính kèm vượt quá kích thước cho phép." }
            }

            val ticketCode = "TK-${(1000..9999).random()}"
            val docRef = db.collection("tickets").document()
            docPath = docRef.path

            val payload = mapOf(
                "ticketCode" to ticketCode,
                "title" to cleanTitle,
                "description" to cleanDesc,
                "category" to category.take(60),
                "priority" to priority.name,
                "status" to TicketStatus.OPEN.name,
                "creatorUid" to uid,
                "creatorName" to creator.displayName.ifBlank { "Người dùng" }.take(100),
                "creatorEmail" to creator.email.take(150),
                "defectPhotoBase64" to defectPhotoBase64?.takeIf { it.isNotBlank() },
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>

            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun assignTicket(
        ticket: SupportTicket,
        technician: UserAccount,
        priority: TicketPriority
    ): Result<Unit> {
        val docPath = "tickets/${ticket.id}"
        return try {
            val teamLeadUid = requireUserId()
            require(ticket.id.isNotBlank()) { "Mã ticket không hợp lệ." }
            val nextStatus = if (ticket.parsedStatus == TicketStatus.OPEN) {
                TicketStatus.ACCEPTED.name
            } else {
                ticket.status
            }

            val updates = mapOf(
                "assigneeUid" to technician.uid,
                "assigneeName" to technician.displayName.take(100),
                "teamLeadUid" to teamLeadUid,
                "priority" to priority.name,
                "status" to nextStatus,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("tickets").document(ticket.id).update(updates).await()

            // Send real-time notification to assigned technician & ticket creator
            sendNotificationInternal(
                recipientUid = technician.uid,
                senderUid = teamLeadUid,
                ticket = ticket,
                title = "Phân công xử lý ${ticket.ticketCode}",
                message = "Bạn được giao xử lý: ${ticket.title} (Mức ${priority.labelVi})",
                newStatus = nextStatus
            )
            if (ticket.creatorUid != teamLeadUid) {
                sendNotificationInternal(
                    recipientUid = ticket.creatorUid,
                    senderUid = teamLeadUid,
                    ticket = ticket,
                    title = "Ticket ${ticket.ticketCode} đã tiếp nhận",
                    message = "Trưởng nhóm đã phân công KTV ${technician.displayName} xử lý yêu cầu của bạn.",
                    newStatus = nextStatus
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

    suspend fun startProcessing(
        ticket: SupportTicket,
        actor: UserAccount,
        initialNote: String? = null
    ): Result<Unit> {
        val docPath = "tickets/${ticket.id}"
        return try {
            val actorUid = requireUserId()
            val isAdmin = actor.parsedRole == UserRole.ADMIN
            if (!isAdmin) {
                require(ticket.parsedStatus == TicketStatus.ACCEPTED) {
                    "Chỉ có thể bắt đầu xử lý từ trạng thái Đã tiếp nhận."
                }
            } else {
                require(ticket.parsedStatus == TicketStatus.OPEN || ticket.parsedStatus == TicketStatus.ACCEPTED) {
                    "Ticket cần ở trạng thái Mở hoặc Đã tiếp nhận."
                }
            }

            val updates = mutableMapOf<String, Any>(
                "status" to TicketStatus.IN_PROGRESS.name,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (ticket.assigneeUid.isNullOrBlank()) {
                updates["assigneeUid"] = actorUid
                updates["assigneeName"] = actor.displayName.ifBlank { "Quản trị viên" }.take(100)
            }
            val cleanNote = initialNote?.trim()?.takeIf { it.isNotBlank() }?.take(1000)
            if (cleanNote != null) {
                updates["resolutionNote"] = cleanNote
            }
            db.collection("tickets").document(ticket.id).update(updates).await()

            if (ticket.creatorUid != actorUid) {
                sendNotificationInternal(
                    recipientUid = ticket.creatorUid,
                    senderUid = actorUid,
                    ticket = ticket,
                    title = "Ticket ${ticket.ticketCode} đang được xử lý",
                    message = "${actor.displayName} đang kiểm tra và sửa chữa thiết bị của bạn.",
                    newStatus = TicketStatus.IN_PROGRESS.name
                )
            }
            ticket.teamLeadUid?.takeIf { it.isNotBlank() && it != actorUid }?.let { leadUid ->
                sendNotificationInternal(
                    recipientUid = leadUid,
                    senderUid = actorUid,
                    ticket = ticket,
                    title = "${ticket.ticketCode} bắt đầu xử lý",
                    message = "${actor.displayName} đã chuyển trạng thái sang Đang xử lý.",
                    newStatus = TicketStatus.IN_PROGRESS.name
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

    suspend fun completeTicket(
        ticket: SupportTicket,
        actor: UserAccount,
        resolvedPhotoBase64: String,
        resolutionNote: String
    ): Result<Unit> {
        val docPath = "tickets/${ticket.id}"
        return try {
            val actorUid = requireUserId()
            val isAdmin = actor.parsedRole == UserRole.ADMIN
            if (!isAdmin) {
                require(ticket.parsedStatus == TicketStatus.IN_PROGRESS) {
                    "Ticket cần ở trạng thái Đang xử lý trước khi hoàn thành."
                }
            } else {
                require(ticket.parsedStatus != TicketStatus.COMPLETED) {
                    "Ticket đã ở trạng thái Hoàn thành."
                }
            }
            require(resolvedPhotoBase64.length >= 10 && resolvedPhotoBase64.length <= 300_000) {
                "Vui lòng chụp hoặc chọn ảnh sau sửa chữa hợp lệ để đối soát."
            }
            val cleanNote = resolutionNote.trim().take(1000)

            val updates = mutableMapOf<String, Any>(
                "status" to TicketStatus.COMPLETED.name,
                "resolvedPhotoBase64" to resolvedPhotoBase64,
                "completedAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (ticket.assigneeUid.isNullOrBlank()) {
                updates["assigneeUid"] = actorUid
                updates["assigneeName"] = actor.displayName.ifBlank { "Quản trị viên" }.take(100)
            }
            if (cleanNote.isNotBlank()) {
                updates["resolutionNote"] = cleanNote
            }
            db.collection("tickets").document(ticket.id).update(updates).await()

            if (ticket.creatorUid != actorUid) {
                sendNotificationInternal(
                    recipientUid = ticket.creatorUid,
                    senderUid = actorUid,
                    ticket = ticket,
                    title = "Ticket ${ticket.ticketCode} đã hoàn thành",
                    message = "Sửa chữa hoàn tất kèm ảnh đối soát bởi ${actor.displayName}. Vui lòng kiểm tra kết quả.",
                    newStatus = TicketStatus.COMPLETED.name
                )
            }
            ticket.teamLeadUid?.takeIf { it.isNotBlank() && it != actorUid }?.let { leadUid ->
                sendNotificationInternal(
                    recipientUid = leadUid,
                    senderUid = actorUid,
                    ticket = ticket,
                    title = "Hoàn thành ${ticket.ticketCode}",
                    message = "${actor.displayName} đã hoàn thành và đính kèm ảnh nghiệm thu.",
                    newStatus = TicketStatus.COMPLETED.name
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

    suspend fun adminForceStatus(
        ticket: SupportTicket,
        newStatus: TicketStatus,
        newPriority: TicketPriority
    ): Result<Unit> {
        val docPath = "tickets/${ticket.id}"
        return try {
            val adminUid = requireUserId()
            val updates = mutableMapOf<String, Any>(
                "status" to newStatus.name,
                "priority" to newPriority.name,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (newStatus == TicketStatus.COMPLETED && ticket.completedAt == null) {
                updates["completedAt"] = FieldValue.serverTimestamp()
            }
            db.collection("tickets").document(ticket.id).update(updates).await()

            if (ticket.creatorUid != adminUid) {
                sendNotificationInternal(
                    recipientUid = ticket.creatorUid,
                    senderUid = adminUid,
                    ticket = ticket,
                    title = "Cập nhật trạng thái ${ticket.ticketCode}",
                    message = "Quản trị viên đã cập nhật trạng thái thành: ${newStatus.labelVi}.",
                    newStatus = newStatus.name
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

    suspend fun deleteTicket(ticket: SupportTicket): Result<Unit> {
        val docPath = "tickets/${ticket.id}"
        return try {
            requireUserId()
            require(ticket.id.isNotBlank()) { "Mã ticket không hợp lệ để xóa." }
            val batch = db.batch()
            batch.delete(db.collection("tickets").document(ticket.id))
            batch.delete(db.collection("invoices").document(ticket.id))
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    private suspend fun sendNotificationInternal(
        recipientUid: String,
        senderUid: String,
        ticket: SupportTicket,
        title: String,
        message: String,
        newStatus: String
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
            "newStatus" to newStatus,
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
