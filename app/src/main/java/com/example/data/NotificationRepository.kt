package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class NotificationRepository(
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
            ?: throw IllegalStateException("Vui lòng đăng nhập để xem thông báo.")

    fun observeNotifications(uid: String): Flow<List<TicketNotification>> =
        db.collection("notifications")
            .whereEqualTo("recipientUid", uid)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(TicketNotification::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }

    suspend fun markAsRead(notificationId: String): Result<Unit> {
        val path = "notifications/$notificationId"
        return try {
            requireUserId()
            db.collection("notifications").document(notificationId)
                .update(mapOf("readFlag" to true))
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun markAllAsRead(notifications: List<TicketNotification>): Result<Unit> {
        return try {
            requireUserId()
            val unread = notifications.filter { !it.readFlag && it.id.isNotBlank() }
            if (unread.isEmpty()) return Result.success(Unit)
            val batch = db.batch()
            for (item in unread) {
                batch.update(db.collection("notifications").document(item.id), mapOf("readFlag" to true))
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "notifications")
            Result.failure(e)
        }
    }
}
