package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.SupportTicket
import com.example.data.TicketPriority
import com.example.data.TicketStatus
import com.example.data.UiState
import com.example.data.UserAccount
import com.example.data.UserRole
import com.example.ui.components.TicketSummaryCard
import com.example.ui.components.statusColors

@Composable
fun TicketListScreen(
    currentUser: UserAccount,
    ticketsState: UiState<List<SupportTicket>>,
    selectedStatus: TicketStatus?,
    selectedPriority: TicketPriority?,
    searchQuery: String,
    onStatusSelect: (TicketStatus?) -> Unit,
    onPrioritySelect: (TicketPriority?) -> Unit,
    onSearchChange: (String) -> Unit,
    onTicketClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val roleTitle = when (currentUser.parsedRole) {
        UserRole.PENDING -> "Chờ cấp quyền"
        UserRole.USER -> "Yêu cầu của tôi"
        UserRole.TECHNICIAN -> "Việc được phân công"
        UserRole.TEAM_LEAD -> "Điều phối & Hàng đợi đội"
        UserRole.ADMIN -> "Toàn bộ Ticket hệ thống"
    }

    Column(modifier = modifier.fillMaxSize()) {
        when (ticketsState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Đang đồng bộ danh sách ticket...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            is UiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            text = ticketsState.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }
            }

            is UiState.Success -> {
                val allTickets = ticketsState.data
                val filtered = remember(allTickets, selectedStatus, selectedPriority, searchQuery) {
                    val q = searchQuery.trim().lowercase()
                    allTickets.filter { t ->
                        val matchStatus = selectedStatus == null || t.parsedStatus == selectedStatus
                        val matchPriority = selectedPriority == null || t.parsedPriority == selectedPriority
                        val matchQuery = q.isBlank() ||
                            t.title.lowercase().contains(q) ||
                            t.ticketCode.lowercase().contains(q) ||
                            t.description.lowercase().contains(q) ||
                            (t.assigneeName?.lowercase()?.contains(q) == true) ||
                            t.creatorName.lowercase().contains(q)
                        matchStatus && matchPriority && matchQuery
                    }
                }

                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        Text(
                            text = roleTitle,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        // Interactive 4-Status Summary Strip
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TicketStatus.entries.forEach { status ->
                                val count = allTickets.count { it.parsedStatus == status }
                                val isSelected = selectedStatus == status
                                val (fg, bg) = statusColors(status)
                                Card(
                                    onClick = { onStatusSelect(if (isSelected) null else status) },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) bg else MaterialTheme.colorScheme.surface
                                    ),
                                    border = BorderStroke(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) fg else MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("status_kpi_${status.name}")
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "$count",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = fg
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = when (status) {
                                                TicketStatus.OPEN -> "Mở"
                                                TicketStatus.ACCEPTED -> "Tiếp nhận"
                                                TicketStatus.IN_PROGRESS -> "Đang xử lý"
                                                TicketStatus.COMPLETED -> "Hoàn thành"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isSelected) fg else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = onSearchChange,
                            placeholder = { Text("Tìm mã #TK, tiêu đề, kỹ thuật viên...") },
                            leadingIcon = {
                                Icon(imageVector = Icons.Filled.Search, contentDescription = null)
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { onSearchChange("") }) {
                                        Icon(imageVector = Icons.Filled.Close, contentDescription = "Xóa tìm kiếm")
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ticket_search_input")
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = selectedPriority == null,
                                onClick = { onPrioritySelect(null) },
                                label = { Text("Tất cả ưu tiên") }
                            )
                            TicketPriority.entries.forEach { priority ->
                                FilterChip(
                                    selected = selectedPriority == priority,
                                    onClick = {
                                        onPrioritySelect(if (selectedPriority == priority) null else priority)
                                    },
                                    label = { Text(priority.labelVi) }
                                )
                            }
                        }
                    }

                    if (filtered.isEmpty()) {
                        item {
                            AnimatedVisibility(visible = true) {
                                Card(
                                    shape = MaterialTheme.shapes.large,
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Image(
                                            painter = painterResource(id = R.drawable.img_empty_tickets),
                                            contentDescription = "Chưa có ticket phù hợp",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(160.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                        )
                                        Spacer(modifier = Modifier.height(14.dp))
                                        Text(
                                            text = if (allTickets.isEmpty()) "Chưa có ticket nào" else "Không tìm thấy ticket phù hợp",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = if (allTickets.isEmpty()) {
                                                "Nhấn nút + Tạo Ticket bên dưới để gửi yêu cầu hỗ trợ kỹ thuật kèm ảnh chụp lỗi."
                                            } else {
                                                "Thử bỏ bớt bộ lọc trạng thái hoặc từ khóa tìm kiếm."
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        items(filtered, key = { it.id }) { ticket ->
                            TicketSummaryCard(
                                ticket = ticket,
                                onClick = { onTicketClick(ticket.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}
