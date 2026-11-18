package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.TicketCategories
import com.example.data.TicketPriority
import com.example.data.UserAccount
import com.example.ui.components.PhotoCapturePickerCard
import com.example.ui.viewmodel.CreateTicketUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTicketSheet(
    currentUser: UserAccount,
    createState: CreateTicketUiState,
    onDismiss: () -> Unit,
    onSubmit: (title: String, description: String, category: String, priority: TicketPriority, photoBase64: String?) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(TicketCategories.first()) }
    var selectedPriority by remember { mutableStateOf(TicketPriority.MEDIUM) }
    var defectPhotoBase64 by remember { mutableStateOf<String?>(null) }
    var localError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(createState) {
        if (createState is CreateTicketUiState.Created) {
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Gửi yêu cầu sửa chữa mới",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Mô tả rõ lỗi gặp phải và đính kèm ảnh chụp sản phẩm để kỹ thuật viên đối soát.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    localError = null
                },
                label = { Text("Tiêu đề sự cố *") },
                placeholder = { Text("VD: Máy in tầng 2 không kéo giấy") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("create_ticket_title_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Danh mục thiết bị",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TicketCategories.forEach { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = { Text(category) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Mức độ ưu tiên đề xuất",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TicketPriority.entries.forEach { priority ->
                    FilterChip(
                        selected = selectedPriority == priority,
                        onClick = { selectedPriority = priority },
                        label = { Text(priority.labelVi) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = description,
                onValueChange = {
                    description = it
                    localError = null
                },
                label = { Text("Mô tả chi tiết lỗi *") },
                placeholder = { Text("Nêu rõ biểu hiện lỗi, mã thiết bị, vị trí...") },
                minLines = 3,
                maxLines = 5,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("create_ticket_desc_input")
            )

            Spacer(modifier = Modifier.height(14.dp))

            PhotoCapturePickerCard(
                title = "Ảnh lỗi sản phẩm (Trước sửa chữa)",
                subtitle = "Chụp trực tiếp hoặc chọn ảnh để kỹ thuật viên kiểm tra đối soát.",
                photoBase64 = defectPhotoBase64,
                onPhotoReady = { defectPhotoBase64 = it },
                testTagPrefix = "defect_photo"
            )

            val displayedError = localError ?: (createState as? CreateTicketUiState.Error)?.message
            if (displayedError != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = displayedError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            val isSubmitting = createState is CreateTicketUiState.Submitting
            Button(
                onClick = {
                    val cleanTitle = title.trim()
                    val cleanDesc = description.trim()
                    when {
                        cleanTitle.length < 3 -> localError = "Tiêu đề cần tối thiểu 3 ký tự."
                        cleanTitle.length > 120 -> localError = "Tiêu đề tối đa 120 ký tự."
                        cleanDesc.length < 5 -> localError = "Mô tả chi tiết cần tối thiểu 5 ký tự."
                        else -> onSubmit(cleanTitle, cleanDesc, selectedCategory, selectedPriority, defectPhotoBase64)
                    }
                },
                enabled = !isSubmitting,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("submit_ticket_button")
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Đang gửi Ticket...")
                } else {
                    Icon(
                        imageVector = Icons.Filled.Send,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Gửi Ticket (${currentUser.displayName})")
                }
            }
        }
    }
}
