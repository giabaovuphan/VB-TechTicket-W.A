package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class UserRoleRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    companion object {
        private const val BOOTSTRAP_ADMIN_EMAIL = "giabao.vuphan@gmail.com"
    }

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("Vui lòng đăng nhập bằng Google trước khi thao tác.")

    private fun isRootAdminEmail(email: String?): Boolean =
        (email ?: "").trim().lowercase() == ROOT_ADMIN_EMAIL

    private fun isCallerRootAdmin(): Boolean {
        val current = auth.currentUser ?: return false
        return current.isEmailVerified && isRootAdminEmail(current.email)
    }

    fun observeCurrentUserProfile(uid: String): Flow<UserAccount?> =
        db.collection("users").document(uid)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(UserAccount::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else {
                    null
                }
            }

    fun observeAllUsers(): Flow<List<UserAccount>> =
        db.collection("users")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(UserAccount::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.displayName.lowercase() }
            }

    fun observeTechnicians(): Flow<List<UserAccount>> =
        db.collection("users")
            .whereIn("role", listOf(UserRole.TECHNICIAN.name, UserRole.TEAM_LEAD.name, UserRole.ADMIN.name))
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(UserAccount::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .filter { it.isApprovedAndActive }
                    .sortedWith(
                        compareBy<UserAccount>(
                            {
                                when (it.parsedRole) {
                                    UserRole.TECHNICIAN -> 0
                                    UserRole.TEAM_LEAD -> 1
                                    UserRole.ADMIN -> 2
                                    UserRole.USER -> 3
                                    UserRole.PENDING -> 4
                                }
                            },
                            { it.displayName.lowercase() }
                        )
                    )
            }

    fun observeRoleAssignments(): Flow<List<RoleAssignment>> =
        db.collection("role_assignments")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(RoleAssignment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.email }
            }

    suspend fun ensureUserProfileAndSyncRole(user: FirebaseUser): Result<UserAccount> {
        val uid = user.uid
        val docPath = "users/$uid"
        return try {
            val emailLower = (user.email ?: "").trim().lowercase()
            require(emailLower.isNotBlank()) { "Tài khoản Google không có địa chỉ email hợp lệ." }

            val displayName = user.displayName?.trim()?.takeIf { it.isNotBlank() }
                ?: emailLower.substringBefore("@").replaceFirstChar { it.uppercase() }

            val assignment = if (user.isEmailVerified) {
                try {
                    val snap = db.collection("role_assignments").document(emailLower).get().await()
                    if (snap.exists()) {
                        snap.toObject(RoleAssignment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                } catch (_: Exception) {
                    null
                }
            } else null

            val isBootstrapAdmin = user.isEmailVerified && isRootAdminEmail(emailLower)
            val resolvedRole = when {
                isBootstrapAdmin -> UserRole.ADMIN.name
                assignment != null && assignment.parsedRole != UserRole.PENDING -> assignment.role
                else -> UserRole.PENDING.name
            }
            val resolvedApprovalStatus = if (resolvedRole == UserRole.PENDING.name) {
                AccountApprovalStatus.PENDING.name
            } else {
                AccountApprovalStatus.APPROVED.name
            }
            val resolvedActive = resolvedRole != UserRole.PENDING.name

            val userRef = db.collection("users").document(uid)
            val existingSnap = userRef.get().await()

            if (!existingSnap.exists()) {
                val payload = mapOf(
                    "uid" to uid,
                    "email" to emailLower,
                    "displayName" to (assignment?.displayName?.takeIf { it.isNotBlank() } ?: displayName).take(100),
                    "role" to resolvedRole,
                    "active" to resolvedActive,
                    "approvalStatus" to resolvedApprovalStatus,
                    "department" to assignment?.department?.takeIf { it.isNotBlank() }?.take(80),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null } as Map<String, Any>

                userRef.set(payload).await()
            } else {
                val existing = existingSnap.toObject(
                    UserAccount::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                if (existing != null) {
                    val shouldPromoteBootstrap = isBootstrapAdmin &&
                        (existing.role != UserRole.ADMIN.name ||
                            !existing.active ||
                            existing.parsedApprovalStatus != AccountApprovalStatus.APPROVED)
                    val shouldSyncAssignment = assignment != null &&
                        assignment.parsedRole != UserRole.PENDING &&
                        existing.parsedApprovalStatus != AccountApprovalStatus.REJECTED &&
                        (existing.role != assignment.role ||
                            existing.parsedApprovalStatus != AccountApprovalStatus.APPROVED ||
                            !existing.active)

                    if (shouldPromoteBootstrap || shouldSyncAssignment) {
                        val updates = mutableMapOf<String, Any>(
                            "role" to resolvedRole,
                            "active" to true,
                            "approvalStatus" to AccountApprovalStatus.APPROVED.name,
                            "displayName" to (assignment?.displayName?.takeIf { it.isNotBlank() } ?: existing.displayName).take(100),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                        assignment?.department?.takeIf { it.isNotBlank() }?.let {
                            updates["department"] = it.take(80)
                        }
                        userRef.update(updates).await()
                    }
                }
            }

            val updatedSnap = userRef.get().await()
            val account = updatedSnap.toObject(
                UserAccount::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: error("Không thể tải hồ sơ người dùng.")
            Result.success(account)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun assignRoleByEmail(
        email: String,
        displayName: String,
        role: UserRole,
        department: String?
    ): Result<Unit> {
        val cleanEmail = email.trim().lowercase()
        val path = "role_assignments/$cleanEmail"
        return try {
            val adminUid = requireUserId()
            require(cleanEmail.matches(Regex("^[a-z0-9._%+\\-]+@[a-z0-9.\\-]+\\.[a-z]{2,}$"))) {
                "Email Google không hợp lệ."
            }
            require(role != UserRole.PENDING) {
                "Vui lòng chọn vai trò hợp lệ để cấp quyền."
            }
            require(!isRootAdminEmail(cleanEmail)) {
                "Không thể thay đổi hoặc ghi đè vai trò Quản trị viên gốc."
            }
            val cleanName = displayName.trim()
            require(cleanName.length in 1..100) { "Tên hiển thị phải từ 1 đến 100 ký tự." }
            val cleanDept = department?.trim()?.takeIf { it.isNotBlank() }?.take(80)

            val callerIsRoot = isCallerRootAdmin()
            if (role == UserRole.ADMIN && !callerIsRoot) {
                throw IllegalArgumentException("Chỉ Quản trị viên gốc mới có quyền thêm Quản trị viên.")
            }

            val docRef = db.collection("role_assignments").document(cleanEmail)
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val payload = mapOf(
                    "email" to cleanEmail,
                    "displayName" to cleanName,
                    "role" to role.name,
                    "department" to cleanDept,
                    "assignedByUid" to adminUid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null } as Map<String, Any>
                docRef.set(payload).await()
            } else {
                val existingRoleRaw = existing.getString("role")
                if (existingRoleRaw == UserRole.ADMIN.name && !callerIsRoot) {
                    throw IllegalArgumentException("Chỉ Quản trị viên gốc mới có quyền chỉnh sửa vai trò Quản trị viên.")
                }
                val updates = mutableMapOf<String, Any>(
                    "displayName" to cleanName,
                    "role" to role.name,
                    "assignedByUid" to adminUid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (cleanDept != null) updates["department"] = cleanDept
                docRef.update(updates).await()
            }

            // Sync matching signed-in user accounts immediately & approve them
            val matchingUsers = db.collection("users")
                .whereEqualTo("email", cleanEmail)
                .get()
                .await()
            for (userDoc in matchingUsers.documents) {
                val currentRoleRaw = userDoc.getString("role")
                if (currentRoleRaw == UserRole.ADMIN.name && !callerIsRoot) continue
                val userUpdates = mutableMapOf<String, Any>(
                    "role" to role.name,
                    "active" to true,
                    "approvalStatus" to AccountApprovalStatus.APPROVED.name,
                    "approvedByUid" to adminUid,
                    "rejectionReason" to FieldValue.delete(),
                    "displayName" to cleanName,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (cleanDept != null) userUpdates["department"] = cleanDept
                userDoc.reference.update(userUpdates).await()
            }

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun approvePendingUser(
        targetUser: UserAccount,
        role: UserRole,
        department: String? = targetUser.department
    ): Result<Unit> = updateUserRoleAndActive(
        targetUser = targetUser,
        newRole = role,
        newActive = true,
        department = department
    )

    suspend fun rejectPendingUser(
        targetUser: UserAccount,
        reason: String?
    ): Result<Unit> {
        val path = "users/${targetUser.uid}"
        return try {
            val adminUid = requireUserId()
            require(!targetUser.isRootAdmin) {
                "Không thể từ chối hoặc khóa Quản trị viên gốc."
            }
            val callerIsRoot = isCallerRootAdmin()
            if (targetUser.parsedRole == UserRole.ADMIN && !callerIsRoot) {
                throw IllegalArgumentException("Chỉ Quản trị viên gốc mới có quyền thao tác trên Quản trị viên.")
            }
            val cleanReason = reason?.trim()?.takeIf { it.isNotBlank() }?.take(300)
            val updates = mutableMapOf<String, Any>(
                "role" to UserRole.PENDING.name,
                "active" to false,
                "approvalStatus" to AccountApprovalStatus.REJECTED.name,
                "approvedByUid" to adminUid,
                "rejectionReason" to (cleanReason ?: FieldValue.delete()),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("users").document(targetUser.uid).update(updates).await()

            val emailLower = targetUser.email.trim().lowercase()
            if (emailLower.isNotBlank()) {
                val assignRef = db.collection("role_assignments").document(emailLower)
                val assignSnap = assignRef.get().await()
                if (assignSnap.exists()) {
                    assignRef.delete().await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateUserRoleAndActive(
        targetUser: UserAccount,
        newRole: UserRole,
        newActive: Boolean,
        department: String?
    ): Result<Unit> {
        val path = "users/${targetUser.uid}"
        return try {
            val adminUid = requireUserId()
            require(newRole != UserRole.PENDING) {
                "Vui lòng chọn vai trò hợp lệ (Người dùng, Kỹ thuật viên, Trưởng nhóm hoặc Quản trị viên)."
            }
            require(!targetUser.isRootAdmin) {
                "Không thể chỉnh sửa vai trò hoặc khóa Quản trị viên gốc."
            }
            val callerIsRoot = isCallerRootAdmin()
            if ((targetUser.parsedRole == UserRole.ADMIN || newRole == UserRole.ADMIN) && !callerIsRoot) {
                throw IllegalArgumentException("Chỉ Quản trị viên gốc mới có quyền thêm hoặc điều chỉnh Quản trị viên.")
            }

            val updates = mutableMapOf<String, Any>(
                "role" to newRole.name,
                "active" to newActive,
                "approvalStatus" to AccountApprovalStatus.APPROVED.name,
                "approvedByUid" to adminUid,
                "rejectionReason" to FieldValue.delete(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val cleanDept = department?.trim()?.takeIf { it.isNotBlank() }?.take(80)
            if (cleanDept != null) {
                updates["department"] = cleanDept
            }
            db.collection("users").document(targetUser.uid).update(updates).await()

            // Mirror to role_assignments so re-auth stays consistent
            val emailLower = targetUser.email.trim().lowercase()
            if (emailLower.isNotBlank()) {
                val assignRef = db.collection("role_assignments").document(emailLower)
                val assignSnap = assignRef.get().await()
                if (!assignSnap.exists()) {
                    val payload = mapOf(
                        "email" to emailLower,
                        "displayName" to targetUser.displayName.take(100),
                        "role" to newRole.name,
                        "department" to cleanDept,
                        "assignedByUid" to adminUid,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ).filterValues { it != null } as Map<String, Any>
                    assignRef.set(payload).await()
                } else {
                    val assignUpdates = mutableMapOf<String, Any>(
                        "role" to newRole.name,
                        "assignedByUid" to adminUid,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    if (cleanDept != null) assignUpdates["department"] = cleanDept
                    assignRef.update(assignUpdates).await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun removeRoleAssignment(email: String): Result<Unit> {
        val cleanEmail = email.trim().lowercase()
        val path = "role_assignments/$cleanEmail"
        return try {
            requireUserId()
            require(!isRootAdminEmail(cleanEmail)) {
                "Không thể xóa quyền của Quản trị viên gốc."
            }
            val assignRef = db.collection("role_assignments").document(cleanEmail)
            val snap = assignRef.get().await()
            if (snap.exists() && snap.getString("role") == UserRole.ADMIN.name && !isCallerRootAdmin()) {
                throw IllegalArgumentException("Chỉ Quản trị viên gốc mới có quyền thu hồi vai trò Quản trị viên.")
            }
            assignRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }
}
