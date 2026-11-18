package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.AccountApprovalStatus
import com.example.data.ROOT_ADMIN_EMAIL
import com.example.data.RoleAssignment
import com.example.data.UiState
import com.example.data.UserAccount
import com.example.data.UserRole
import com.example.ui.components.RoleBadge
import com.example.ui.components.formatTimestampVi
import com.example.ui.viewmodel.DetailActionFeedback

@Composable
fun AdminRolesScreen(
    currentUser: UserAccount,
    usersState: UiState<List<UserAccount>>,
    assignmentsState: UiState<List<RoleAssignment>>,
    isSaving: Boolean,
    feedback: DetailActionFeedback?,
    onAssignByEmail: (email: String, displayName: String, role: UserRole, department: String?) -> Unit,
    onUpdateUser: (user: UserAccount, role: UserRole, active: Boolean, department: String?) -> Unit,
    onApprovePending: (user: UserAccount, role: UserRole) -> Unit,
    onRejectPending: (user: UserAccount, reason: String?) -> Unit,
    onRemoveAssignment: (email: String) -> Unit,
    onSeedStarterData: () -> Unit,
    onClearFeedback: () -> Unit,
    modifier: Modifier = Modifier
) {
    val callerIsRootAdmin = currentUser.isRootAdmin
    val assignableRoles = remember(callerIsRootAdmin) {
        UserRole.assignableRoles(callerIsRootAdmin)
    }

    var showCreateDialog by remember { mutableStateOf(false) }
    var editingUser by remember { mutableStateOf<UserAccount?>(null) }
    var rejectingUser by remember { mutableStateOf<UserAccount?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize()
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Quản trị tài khoản & Phân quyền",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = if (callerIsRootAdmin) {
                            "Quản trị viên gốc: toàn quyền duyệt tài khoản và thêm Quản trị viên."
                        } else {
                            "Duyệt tài khoản mới và gán vai trò Người dùng, Kỹ thuật viên, Trưởng nhóm."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        onClearFeedback()
                        showCreateDialog = true
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("assign_role_button")
                ) {
                    Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Gán quyền")
                }
            }
        }

        if (feedback != null) {
            item {
                Surface(
                    color = if (feedback.isError) MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                    else MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = feedback.message,
                        color = if (feedback.isError) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        item {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Khởi tạo dữ liệu mẫu Firestore",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tạo sẵn dữ liệu mẫu cho các collection inventory, products và tickets (bỏ qua nếu đã có, không ghi đè dữ liệu hiện tại).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            onClearFeedback()
                            onSeedStarterData()
                        },
                        enabled = !isSaving,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("seed_starter_data_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Khởi tạo dữ liệu mẫu")
                    }
                }
            }
        }

        when (usersState) {
            is UiState.Loading -> {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }

            is UiState.Error -> {
                item {
                    Text(usersState.message, color = MaterialTheme.colorScheme.error)
                }
            }

            is UiState.Success -> {
                val allUsers = usersState.data
                val pendingUsers = allUsers.filter {
                    it.parsedApprovalStatus == AccountApprovalStatus.PENDING ||
                        (it.parsedApprovalStatus != AccountApprovalStatus.REJECTED && it.parsedRole == UserRole.PENDING)
                }
                val approvedUsers = allUsers.filter {
                    it.parsedApprovalStatus == AccountApprovalStatus.APPROVED && it.parsedRole != UserRole.PENDING
                }
                val rejectedUsers = allUsers.filter {
                    it.parsedApprovalStatus == AccountApprovalStatus.REJECTED
                }

                // 1. Pending Google Account Requests
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Yêu cầu chờ cấp quyền",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        if (pendingUsers.isNotEmpty()) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(999.dp)
                            ) {
                                Text(
                                    text = "${pendingUsers.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                if (pendingUsers.isEmpty()) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Không có tài khoản Google mới nào đang chờ cấp quyền.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                } else {
                    items(pendingUsers, key = { "pending_${it.uid}" }) { pendingAccount ->
                        PendingUserApprovalCard(
                            account = pendingAccount,
                            assignableRoles = assignableRoles,
                            isSaving = isSaving,
                            onApprove = { role ->
                                onClearFeedback()
                                onApprovePending(pendingAccount, role)
                            },
                            onReject = {
                                onClearFeedback()
                                rejectingUser = pendingAccount
                            }
                        )
                    }
                }

                // 2. Approved & Active/Locked Accounts
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tài khoản đã kích hoạt (${approvedUsers.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                items(approvedUsers, key = { "approved_${it.uid}" }) { account ->
                    val canEditAccount = account.uid != currentUser.uid &&
                        !account.isRootAdmin &&
                        (callerIsRootAdmin || account.parsedRole != UserRole.ADMIN)

                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (account.isRootAdmin) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = account.displayName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (!account.active) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "(Đã khóa)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                                Text(
                                    text = account.email,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                account.department?.takeIf { it.isNotBlank() }?.let { dept ->
                                    Text(
                                        text = "Bộ phận: $dept",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    RoleBadge(role = account.parsedRole)
                                    if (account.isRootAdmin) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.VerifiedUser,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "Quản trị viên gốc",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            if (canEditAccount) {
                                IconButton(
                                    onClick = {
                                        onClearFeedback()
                                        editingUser = account
                                    },
                                    modifier = Modifier.testTag("edit_user_${account.uid}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Edit,
                                        contentDescription = "Chỉnh sửa vai trò",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Rejected Requests (Admin can re-approve if needed)
                if (rejectedUsers.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Yêu cầu đã từ chối (${rejectedUsers.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    items(rejectedUsers, key = { "rejected_${it.uid}" }) { rejectedAccount ->
                        Card(
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = rejectedAccount.displayName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = rejectedAccount.email,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    rejectedAccount.rejectionReason?.takeIf { it.isNotBlank() }?.let { reason ->
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Lý do: $reason",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                OutlinedButton(
                                    onClick = {
                                        onClearFeedback()
                                        editingUser = rejectedAccount
                                    },
                                    enabled = !isSaving,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Cấp lại")
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Danh sách phân quyền theo Email",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Khi người dùng đăng nhập bằng Google lần đầu với email bên dưới, hệ thống tự động cấp quyền ngay.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        when (assignmentsState) {
            is UiState.Loading -> {
                item { CircularProgressIndicator(modifier = Modifier.size(24.dp)) }
            }
            is UiState.Error -> {
                item { Text(assignmentsState.message, color = MaterialTheme.colorScheme.error) }
            }
            is UiState.Success -> {
                val assignments = assignmentsState.data
                if (assignments.isEmpty()) {
                    item {
                        Text(
                            text = "Chưa có bản ghi phân quyền nào được tạo trước.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(assignments, key = { it.email }) { assignment ->
                        val isTargetRoot = assignment.email.trim().lowercase() == ROOT_ADMIN_EMAIL
                        val canDeleteAssignment = !isTargetRoot &&
                            (callerIsRootAdmin || assignment.parsedRole != UserRole.ADMIN)
                        Card(
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = assignment.displayName,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = assignment.email,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    RoleBadge(role = assignment.parsedRole)
                                }
                                if (canDeleteAssignment) {
                                    IconButton(
                                        onClick = { onRemoveAssignment(assignment.email) },
                                        enabled = !isSaving
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Delete,
                                            contentDescription = "Xóa bản ghi phân quyền",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        AssignRoleByEmailDialog(
            assignableRoles = assignableRoles,
            isSaving = isSaving,
            onDismiss = { showCreateDialog = false },
            onConfirm = { email, name, role, dept ->
                onAssignByEmail(email, name, role, dept)
                showCreateDialog = false
            }
        )
    }

    val targetEdit = editingUser
    if (targetEdit != null) {
        EditExistingUserDialog(
            user = targetEdit,
            assignableRoles = assignableRoles,
            isSaving = isSaving,
            onDismiss = { editingUser = null },
            onConfirm = { role, active, dept ->
                onUpdateUser(targetEdit, role, active, dept)
                editingUser = null
            }
        )
    }

    val targetReject = rejectingUser
    if (targetReject != null) {
        RejectPendingUserDialog(
            user = targetReject,
            isSaving = isSaving,
            onDismiss = { rejectingUser = null },
            onConfirm = { reason ->
                onRejectPending(targetReject, reason)
                rejectingUser = null
            }
        )
    }
}

@Composable
private fun PendingUserApprovalCard(
    account: UserAccount,
    assignableRoles: List<UserRole>,
    isSaving: Boolean,
    onApprove: (UserRole) -> Unit,
    onReject: () -> Unit
) {
    var selectedRole by remember(account.uid) { mutableStateOf(UserRole.USER) }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("pending_user_card_${account.uid}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = account.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = account.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (account.createdAt != null) {
                        Text(
                            text = "Đăng nhập lúc: ${formatTimestampVi(account.createdAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                RoleBadge(role = UserRole.PENDING)
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Chọn vai trò cấp phép:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                assignableRoles.forEach { role ->
                    FilterChip(
                        selected = selectedRole == role,
                        onClick = { selectedRole = role },
                        label = { Text(role.shortLabelVi) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onReject,
                    enabled = !isSaving,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("reject_user_${account.uid}")
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Từ chối")
                }

                Button(
                    onClick = { onApprove(selectedRole) },
                    enabled = !isSaving,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1.4f)
                        .height(48.dp)
                        .testTag("approve_user_${account.uid}")
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Duyệt & Cấp quyền")
                }
            }
        }
    }
}

@Composable
private fun AssignRoleByEmailDialog(
    assignableRoles: List<UserRole>,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (email: String, displayName: String, role: UserRole, department: String?) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var department by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf(UserRole.TECHNICIAN) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tạo & Gán vai trò tài khoản") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; errorText = null },
                    label = { Text("Email Google *") },
                    placeholder = { Text("ten@gmail.com") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("assign_email_input")
                )
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it; errorText = null },
                    label = { Text("Họ tên hiển thị *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("assign_name_input")
                )
                OutlinedTextField(
                    value = department,
                    onValueChange = { department = it },
                    label = { Text("Bộ phận (tùy chọn)") },
                    placeholder = { Text("VD: Đội bảo trì Phần cứng") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Chọn vai trò", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    assignableRoles.forEach { role ->
                        FilterChip(
                            selected = selectedRole == role,
                            onClick = { selectedRole = role },
                            label = { Text(role.shortLabelVi) }
                        )
                    }
                }
                if (errorText != null) {
                    Text(errorText!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanEmail = email.trim().lowercase()
                    val cleanName = displayName.trim()
                    when {
                        !cleanEmail.matches(Regex("^[a-z0-9._%+\\-]+@[a-z0-9.\\-]+\\.[a-z]{2,}$")) ->
                            errorText = "Email Google không hợp lệ."
                        cleanEmail == ROOT_ADMIN_EMAIL ->
                            errorText = "Không thể ghi đè tài khoản Quản trị viên gốc."
                        cleanName.isBlank() ->
                            errorText = "Vui lòng nhập họ tên hiển thị."
                        else ->
                            onConfirm(cleanEmail, cleanName, selectedRole, department.trim().ifBlank { null })
                    }
                },
                enabled = !isSaving,
                modifier = Modifier.testTag("confirm_assign_role_button")
            ) {
                Text("Lưu phân quyền")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Hủy") }
        }
    )
}

@Composable
private fun EditExistingUserDialog(
    user: UserAccount,
    assignableRoles: List<UserRole>,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (role: UserRole, active: Boolean, department: String?) -> Unit
) {
    val initialRole = if (user.parsedRole in assignableRoles) user.parsedRole else UserRole.USER
    val initialActive = if (user.parsedApprovalStatus == AccountApprovalStatus.REJECTED) true else user.active

    var selectedRole by remember(user.uid) { mutableStateOf(initialRole) }
    var active by remember(user.uid) { mutableStateOf(initialActive) }
    var department by remember(user.uid) { mutableStateOf(user.department ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cập nhật quyền: ${user.displayName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = user.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("Vai trò", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    assignableRoles.forEach { role ->
                        FilterChip(
                            selected = selectedRole == role,
                            onClick = { selectedRole = role },
                            label = { Text(role.shortLabelVi) }
                        )
                    }
                }
                OutlinedTextField(
                    value = department,
                    onValueChange = { department = it },
                    label = { Text("Bộ phận") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Trạng thái hoạt động", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = active, onCheckedChange = { active = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedRole, active, department.trim().ifBlank { null }) },
                enabled = !isSaving
            ) {
                Text("Cập nhật")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Đóng") }
        }
    )
}

@Composable
private fun RejectPendingUserDialog(
    user: UserAccount,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (reason: String?) -> Unit
) {
    var reason by remember(user.uid) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Từ chối yêu cầu truy cập") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Bạn có chắc muốn từ chối tài khoản ${user.displayName} (${user.email})?",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Lý do từ chối (tùy chọn)") },
                    placeholder = { Text("VD: Tài khoản không thuộc danh sách nhân sự") },
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reject_reason_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(reason.trim().ifBlank { null }) },
                enabled = !isSaving,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier.testTag("confirm_reject_button")
            ) {
                Text("Xác nhận từ chối")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Hủy") }
        }
    )
}
