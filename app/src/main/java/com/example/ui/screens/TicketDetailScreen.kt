package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AssignmentInd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.InventoryItem
import com.example.data.InvoiceLineItem
import com.example.data.InvoicePaymentMethod
import com.example.data.InvoicePaymentStatus
import com.example.data.ProductItem
import com.example.data.RepairInvoice
import com.example.data.SupportTicket
import com.example.data.TicketPriority
import com.example.data.TicketStatus
import com.example.data.UiState
import com.example.data.UserAccount
import com.example.data.UserRole
import com.example.data.formatVnd
import com.example.ui.components.Base64PhotoPreview
import com.example.ui.components.PhotoCapturePickerCard
import com.example.ui.components.PriorityBadge
import com.example.ui.components.RepairInvoiceCard
import com.example.ui.components.StatusBadge
import com.example.ui.components.StatusProgressStepper
import com.example.ui.components.formatTimestampVi
import com.example.ui.viewmodel.DetailActionFeedback
import com.example.util.InvoiceExporter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketDetailScreen(
    currentUser: UserAccount,
    ticketState: UiState<SupportTicket>,
    invoiceState: UiState<RepairInvoice?>,
    technicians: List<UserAccount>,
    activeProducts: List<ProductItem>,
    activeInventory: List<InventoryItem>,
    isProcessing: Boolean,
    ticketDeleted: Boolean,
    feedback: DetailActionFeedback?,
    onBack: () -> Unit,
    onTicketDeleted: () -> Unit,
    onAssignTicket: (SupportTicket, UserAccount, TicketPriority) -> Unit,
    onStartProgress: (SupportTicket, UserAccount, String?) -> Unit,
    onCompleteTicket: (SupportTicket, UserAccount, String, String) -> Unit,
    onAdminOverride: (SupportTicket, TicketStatus, TicketPriority) -> Unit,
    onDeleteTicket: (SupportTicket) -> Unit,
    onSaveInvoice: (
        ticket: SupportTicket,
        existingInvoice: RepairInvoice?,
        items: List<InvoiceLineItem>,
        laborFee: Long,
        paymentStatus: InvoicePaymentStatus,
        paymentMethod: InvoicePaymentMethod,
        note: String?,
        actor: UserAccount
    ) -> Unit,
    onMarkInvoicePaid: (SupportTicket, RepairInvoice, InvoicePaymentMethod, UserAccount) -> Unit,
    onClearFeedback: () -> Unit
) {
    LaunchedEffect(ticketDeleted) {
        if (ticketDeleted) {
            onTicketDeleted()
        }
    }

    var showDeleteDialog by remember { mutableStateOf(false) }
    val loadedTicket = (ticketState as? UiState.Success)?.data
    val canDeleteTicket = loadedTicket != null &&
        (currentUser.parsedRole == UserRole.ADMIN || currentUser.parsedRole == UserRole.TEAM_LEAD)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val code = loadedTicket?.ticketCode ?: "Chi tiết Ticket"
                    Text("Ticket #$code")
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Quay lại"
                        )
                    }
                },
                actions = {
                    if (canDeleteTicket) {
                        IconButton(
                            onClick = { showDeleteDialog = true },
                            enabled = !isProcessing,
                            modifier = Modifier.testTag("delete_ticket_topbar_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = "Xóa ticket",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        when (ticketState) {
            is UiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is UiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = ticketState.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            is UiState.Success -> {
                val ticket = ticketState.data
                TicketDetailBody(
                    ticket = ticket,
                    currentUser = currentUser,
                    invoiceState = invoiceState,
                    technicians = technicians,
                    activeProducts = activeProducts,
                    activeInventory = activeInventory,
                    isProcessing = isProcessing,
                    feedback = feedback,
                    onAssignTicket = onAssignTicket,
                    onStartProgress = onStartProgress,
                    onCompleteTicket = onCompleteTicket,
                    onAdminOverride = onAdminOverride,
                    onRequestDelete = { showDeleteDialog = true },
                    onSaveInvoice = onSaveInvoice,
                    onMarkInvoicePaid = onMarkInvoicePaid,
                    onClearFeedback = onClearFeedback,
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }

    if (showDeleteDialog && loadedTicket != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Xác nhận xóa Ticket #${loadedTicket.ticketCode}") },
            text = {
                Text(
                    "Bạn có chắc chắn muốn xóa vĩnh viễn ticket \"${loadedTicket.title}\"? " +
                        "Hóa đơn chi phí sửa chữa gắn với ticket này (nếu có) cũng sẽ bị xóa và không thể khôi phục."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        onDeleteTicket(loadedTicket)
                    },
                    enabled = !isProcessing,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_ticket_button")
                ) {
                    Text("Xóa vĩnh viễn")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Hủy")
                }
            }
        )
    }
}

@Composable
private fun TicketDetailBody(
    ticket: SupportTicket,
    currentUser: UserAccount,
    invoiceState: UiState<RepairInvoice?>,
    technicians: List<UserAccount>,
    activeProducts: List<ProductItem>,
    activeInventory: List<InventoryItem>,
    isProcessing: Boolean,
    feedback: DetailActionFeedback?,
    onAssignTicket: (SupportTicket, UserAccount, TicketPriority) -> Unit,
    onStartProgress: (SupportTicket, UserAccount, String?) -> Unit,
    onCompleteTicket: (SupportTicket, UserAccount, String, String) -> Unit,
    onAdminOverride: (SupportTicket, TicketStatus, TicketPriority) -> Unit,
    onRequestDelete: () -> Unit,
    onSaveInvoice: (
        ticket: SupportTicket,
        existingInvoice: RepairInvoice?,
        items: List<InvoiceLineItem>,
        laborFee: Long,
        paymentStatus: InvoicePaymentStatus,
        paymentMethod: InvoicePaymentMethod,
        note: String?,
        actor: UserAccount
    ) -> Unit,
    onMarkInvoicePaid: (SupportTicket, RepairInvoice, InvoicePaymentMethod, UserAccount) -> Unit,
    onClearFeedback: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedTechUid by remember(ticket.id, ticket.assigneeUid) { mutableStateOf(ticket.assigneeUid) }
    var selectedPriority by remember(ticket.id, ticket.priority) { mutableStateOf(ticket.parsedPriority) }
    var resolutionNote by remember(ticket.id, ticket.resolutionNote) { mutableStateOf(ticket.resolutionNote ?: "") }
    var resolvedPhotoBase64 by remember(ticket.id, ticket.resolvedPhotoBase64) { mutableStateOf(ticket.resolvedPhotoBase64) }
    var validationError by remember { mutableStateOf<String?>(null) }

    var showInvoiceEditor by remember { mutableStateOf(false) }
    var editorInitialInvoice by remember { mutableStateOf<RepairInvoice?>(null) }
    var payingInvoice by remember { mutableStateOf<RepairInvoice?>(null) }

    LaunchedEffect(ticket.status) {
        validationError = null
    }

    val role = currentUser.parsedRole
    val status = ticket.parsedStatus
    val canManageInvoice = role == UserRole.ADMIN ||
        role == UserRole.TEAM_LEAD ||
        (role == UserRole.TECHNICIAN && ticket.assigneeUid == currentUser.uid)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (feedback != null) {
            Surface(
                color = if (feedback.isError) MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = feedback.message,
                        color = if (feedback.isError) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Overview & Stepper Card
        Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PriorityBadge(priority = ticket.parsedPriority)
                    StatusBadge(status = ticket.parsedStatus)
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = ticket.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = ticket.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))
                StatusProgressStepper(currentStatus = ticket.parsedStatus)

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(12.dp))

                InfoRow("Danh mục", ticket.category)
                InfoRow("Người gửi", "${ticket.creatorName} (${ticket.creatorEmail})")
                InfoRow("Người xử lý", ticket.assigneeName ?: "Chưa phân công")
                InfoRow("Thời gian tạo", formatTimestampVi(ticket.createdAt))
                if (ticket.completedAt != null) {
                    InfoRow("Hoàn thành lúc", formatTimestampVi(ticket.completedAt))
                }
                if (!ticket.resolutionNote.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Ghi chú kỹ thuật",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = ticket.resolutionNote,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // Before & After Photo Comparison Card
        Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Hình ảnh đối soát Trước & Sau xử lý",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Chạm vào ảnh để phóng to kiểm tra chi tiết.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PhotoComparisonSlot(
                        label = "Ảnh lỗi ban đầu",
                        base64 = ticket.defectPhotoBase64,
                        emptyText = "Người dùng không đính kèm ảnh",
                        modifier = Modifier.weight(1f)
                    )
                    PhotoComparisonSlot(
                        label = "Ảnh sau xử lý",
                        base64 = ticket.resolvedPhotoBase64,
                        emptyText = "Chờ kỹ thuật viên cập nhật",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Repair Invoice & Parts Replacement Card
        RepairInvoiceCard(
            invoiceState = invoiceState,
            canManageInvoice = canManageInvoice,
            isProcessing = isProcessing,
            onOpenEditor = { existing ->
                onClearFeedback()
                editorInitialInvoice = existing
                showInvoiceEditor = true
            },
            onMarkPaid = { invoice ->
                onClearFeedback()
                payingInvoice = invoice
            },
            onShareInvoice = { invoice ->
                InvoiceExporter.shareInvoice(context, ticket, invoice)
            }
        )

        // Team Lead / Admin Assignment Panel
        if ((role == UserRole.TEAM_LEAD || role == UserRole.ADMIN) && status != TicketStatus.COMPLETED) {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.AssignmentInd,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Phân công & Điều chỉnh ưu tiên",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Chọn mức ưu tiên",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TicketPriority.entries.forEach { prio ->
                            FilterChip(
                                selected = selectedPriority == prio,
                                onClick = { selectedPriority = prio },
                                label = { Text(prio.labelVi) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Chọn nhân sự xử lý (KTV / Trưởng nhóm / Quản trị)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    if (technicians.isEmpty()) {
                        Text(
                            text = "Chưa có nhân sự kỹ thuật hoạt động. Quản trị viên có thể cấp quyền trong mục Phân quyền.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            technicians.forEach { staff ->
                                FilterChip(
                                    selected = selectedTechUid == staff.uid,
                                    onClick = { selectedTechUid = staff.uid },
                                    label = { Text("${staff.displayName} (${staff.parsedRole.shortLabelVi})") }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        val chosenStaff = technicians.firstOrNull { it.uid == selectedTechUid }
                        Button(
                            onClick = {
                                onClearFeedback()
                                if (chosenStaff != null) {
                                    onAssignTicket(ticket, chosenStaff, selectedPriority)
                                } else {
                                    validationError = "Vui lòng chọn nhân sự phụ trách."
                                }
                            },
                            enabled = !isProcessing && chosenStaff != null,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("assign_ticket_button")
                        ) {
                            Text(
                                if (status == TicketStatus.OPEN) "Tiếp nhận & Giao việc"
                                else "Cập nhật phân công"
                            )
                        }
                    }
                }
            }
        }

        // Technical Execution Workflow
        val canExecuteTechnical = status != TicketStatus.COMPLETED && (
            role == UserRole.ADMIN ||
                ((role == UserRole.TECHNICIAN || role == UserRole.TEAM_LEAD) && ticket.assigneeUid == currentUser.uid)
            )

        if (canExecuteTechnical) {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Engineering,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Thao tác kỹ thuật & Nghiệm thu",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = resolutionNote,
                        onValueChange = { resolutionNote = it },
                        label = { Text("Ghi chú kiểm tra / sửa chữa") },
                        placeholder = { Text("Mô tả linh kiện đã thay hoặc kết quả kiểm tra...") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("resolution_note_input")
                    )

                    if (status == TicketStatus.ACCEPTED || (role == UserRole.ADMIN && status == TicketStatus.OPEN)) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = {
                                onClearFeedback()
                                onStartProgress(ticket, currentUser, resolutionNote)
                            },
                            enabled = !isProcessing,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("start_progress_button")
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Bắt đầu xử lý (Đang xử lý)")
                        }
                    }

                    if (status == TicketStatus.IN_PROGRESS || role == UserRole.ADMIN) {
                        Spacer(modifier = Modifier.height(14.dp))
                        PhotoCapturePickerCard(
                            title = "Ảnh sau khi xử lý lỗi *",
                            subtitle = "Bắt buộc đính kèm ảnh chụp thiết bị sau khi sửa xong để đối soát.",
                            photoBase64 = resolvedPhotoBase64,
                            onPhotoReady = {
                                resolvedPhotoBase64 = it
                                validationError = null
                            },
                            testTagPrefix = "resolved_photo"
                        )

                        if (validationError != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = validationError!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = {
                                onClearFeedback()
                                val photo = resolvedPhotoBase64
                                if (photo.isNullOrBlank()) {
                                    validationError = "Vui lòng chụp hoặc chọn ảnh sau khi xử lý trước khi hoàn thành."
                                } else {
                                    onCompleteTicket(ticket, currentUser, photo, resolutionNote)
                                }
                            },
                            enabled = !isProcessing,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("complete_ticket_button")
                        ) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Hoàn thành & Gửi đối soát")
                        }
                    }
                }
            }
        }

        if (status == TicketStatus.COMPLETED) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Ticket đã hoàn thành và lưu ảnh đối soát thành công.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        if (role == UserRole.ADMIN) {
            var overrideStatus by remember(ticket.status) { mutableStateOf(ticket.parsedStatus) }
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Quản trị viên: Điều chỉnh trạng thái nhanh",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TicketStatus.entries.forEach { st ->
                            FilterChip(
                                selected = overrideStatus == st,
                                onClick = { overrideStatus = st },
                                label = { Text(st.labelVi) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            onClearFeedback()
                            onAdminOverride(ticket, overrideStatus, selectedPriority)
                        },
                        enabled = !isProcessing && (overrideStatus != ticket.parsedStatus || selectedPriority != ticket.parsedPriority),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Lưu điều chỉnh quản trị")
                    }
                }
            }
        }

        // Ticket Deletion Card for Admin & Team Lead (All Statuses)
        if (role == UserRole.ADMIN || role == UserRole.TEAM_LEAD) {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Quản lý Ticket tạo nhầm / sai sót",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Chỉ Quản trị viên và Trưởng nhóm có quyền xóa vĩnh viễn ticket này khỏi hệ thống.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onRequestDelete,
                        enabled = !isProcessing,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("delete_ticket_button")
                    ) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Xóa Ticket #${ticket.ticketCode}")
                    }
                }
            }
        }
    }

    if (showInvoiceEditor) {
        InvoiceEditorSheet(
            ticket = ticket,
            existingInvoice = editorInitialInvoice,
            activeProducts = activeProducts,
            activeInventory = activeInventory,
            isProcessing = isProcessing,
            onDismiss = { showInvoiceEditor = false },
            onSave = { items, laborFee, paymentStatus, paymentMethod, note ->
                onSaveInvoice(
                    ticket,
                    editorInitialInvoice,
                    items,
                    laborFee,
                    paymentStatus,
                    paymentMethod,
                    note,
                    currentUser
                )
                showInvoiceEditor = false
            }
        )
    }

    val targetPay = payingInvoice
    if (targetPay != null) {
        var selectedMethod by remember(targetPay.id) { mutableStateOf(targetPay.parsedPaymentMethod) }
        AlertDialog(
            onDismissRequest = { payingInvoice = null },
            title = { Text("Xác nhận thanh toán ${targetPay.invoiceCode}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Tổng số tiền cần thanh toán: ${formatVnd(targetPay.totalAmount)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text("Chọn phương thức thanh toán:", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        InvoicePaymentMethod.entries.forEach { method ->
                            FilterChip(
                                selected = selectedMethod == method,
                                onClick = { selectedMethod = method },
                                label = { Text(method.labelVi) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onMarkInvoicePaid(ticket, targetPay, selectedMethod, currentUser)
                        payingInvoice = null
                    },
                    enabled = !isProcessing,
                    modifier = Modifier.testTag("confirm_mark_paid_button")
                ) {
                    Text("Đã thanh toán")
                }
            },
            dismissButton = {
                TextButton(onClick = { payingInvoice = null }) {
                    Text("Hủy")
                }
            }
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(110.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PhotoComparisonSlot(
    label: String,
    base64: String?,
    emptyText: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        if (!base64.isNullOrBlank()) {
            Base64PhotoPreview(
                base64 = base64,
                contentDescription = label,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(145.dp)
            )
        } else {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(145.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = emptyText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
