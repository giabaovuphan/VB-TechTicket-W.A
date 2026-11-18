package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.SupportTicket
import com.example.data.TicketStatus
import com.example.data.UiState
import com.example.ui.components.statusColors
import com.example.util.ReportWindow
import com.example.util.WeeklyReportCalculator
import java.util.Locale

@Composable
fun ReportsScreen(
    ticketsState: UiState<List<SupportTicket>>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedWindow by remember { mutableStateOf(ReportWindow.THIS_WEEK) }

    when (ticketsState) {
        is UiState.Loading -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is UiState.Error -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = ticketsState.message,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
        }

        is UiState.Success -> {
            val metrics = remember(ticketsState.data, selectedWindow) {
                WeeklyReportCalculator.compute(ticketsState.data, selectedWindow)
            }

            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = modifier.fillMaxSize()
            ) {
                item {
                    Text(
                        text = "Báo cáo & Thống kê tuần",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ReportWindow.entries.forEach { window ->
                            FilterChip(
                                selected = selectedWindow == window,
                                onClick = { selectedWindow = window },
                                label = { Text(window.labelVi) }
                            )
                        }
                    }
                }

                // 4 KPI Cards (2x2 Grid)
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            KpiCard(
                                title = "Tổng Ticket",
                                value = "${metrics.totalTickets}",
                                subtitle = selectedWindow.labelVi,
                                accent = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            KpiCard(
                                title = "Tỷ lệ hoàn thành",
                                value = "${metrics.completionRatePercent}%",
                                subtitle = "${metrics.completedCount}/${metrics.totalTickets} ticket",
                                accent = statusColors(TicketStatus.COMPLETED).first,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            KpiCard(
                                title = "Đang xử lý / Mở",
                                value = "${metrics.openCount + metrics.acceptedCount + metrics.inProgressCount}",
                                subtitle = "${metrics.urgentOpenCount} khẩn cấp",
                                accent = statusColors(TicketStatus.IN_PROGRESS).first,
                                modifier = Modifier.weight(1f)
                            )
                            val avgStr = metrics.avgResolutionHours?.let {
                                String.format(Locale.US, "%.1f giờ", it)
                            } ?: "—"
                            KpiCard(
                                title = "TG xử lý TB",
                                value = avgStr,
                                subtitle = "Tính đến khi hoàn thành",
                                accent = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Status Breakdown Horizontal Bar Chart
                item {
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Phân bố theo trạng thái",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            val maxCount = maxOf(
                                1,
                                metrics.openCount,
                                metrics.acceptedCount,
                                metrics.inProgressCount,
                                metrics.completedCount
                            )
                            StatusBarRow(TicketStatus.OPEN, metrics.openCount, maxCount)
                            StatusBarRow(TicketStatus.ACCEPTED, metrics.acceptedCount, maxCount)
                            StatusBarRow(TicketStatus.IN_PROGRESS, metrics.inProgressCount, maxCount)
                            StatusBarRow(TicketStatus.COMPLETED, metrics.completedCount, maxCount)
                        }
                    }
                }

                // 7-Day Trend Canvas Bar Chart
                item {
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Xu hướng Ticket 7 ngày qua",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Số lượng ticket mới tạo theo từng ngày",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(14.dp))

                            val barColor = MaterialTheme.colorScheme.primary
                            val gridColor = MaterialTheme.colorScheme.outlineVariant
                            val maxDaily = (metrics.dailyCounts.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)

                            Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                            ) {
                                val count = metrics.dailyCounts.size.coerceAtLeast(1)
                                val slotW = size.width / count
                                val barW = (slotW * 0.5f).coerceAtMost(36.dp.toPx())

                                // Horizontal baseline & mid-grid
                                drawLine(
                                    color = gridColor,
                                    start = Offset(0f, size.height),
                                    end = Offset(size.width, size.height),
                                    strokeWidth = 1.dp.toPx()
                                )
                                drawLine(
                                    color = gridColor.copy(alpha = 0.5f),
                                    start = Offset(0f, size.height * 0.5f),
                                    end = Offset(size.width, size.height * 0.5f),
                                    strokeWidth = 1.dp.toPx()
                                )

                                metrics.dailyCounts.forEachIndexed { idx, (_, value) ->
                                    val ratio = value.toFloat() / maxDaily.toFloat()
                                    val barH = (size.height - 12.dp.toPx()) * ratio
                                    val left = idx * slotW + (slotW - barW) / 2f
                                    val top = size.height - barH
                                    if (barH > 0f) {
                                        drawRoundRect(
                                            color = barColor,
                                            topLeft = Offset(left, top),
                                            size = Size(barW, barH),
                                            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth()) {
                                metrics.dailyCounts.forEach { (dayLabel, value) ->
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "$value",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = dayLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Technician Performance Leaderboard
                item {
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Hiệu suất nhân viên kỹ thuật",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            if (metrics.techLeaderboard.isEmpty()) {
                                Text(
                                    text = "Chưa có ticket được phân công trong khoảng thời gian này.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                metrics.techLeaderboard.forEach { perf ->
                                    val progress = if (perf.assignedCount > 0) {
                                        perf.completedCount.toFloat() / perf.assignedCount.toFloat()
                                    } else 0f
                                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = perf.name,
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                            Text(
                                                text = "${perf.completedCount}/${perf.assignedCount} hoàn thành",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        LinearProgressIndicator(
                                            progress = { progress },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(8.dp),
                                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Share Weekly Summary & CSV Export
                item {
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Xuất & chia sẻ báo cáo tuần",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Gửi bản tóm tắt tuần cho quản lý hoặc xuất bảng CSV tương thích Excel.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { WeeklyReportCalculator.shareSummaryText(context, metrics) },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("share_summary_button")
                                ) {
                                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Tóm tắt")
                                }
                                OutlinedButton(
                                    onClick = { WeeklyReportCalculator.shareCsvReport(context, metrics) },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("export_csv_button")
                                ) {
                                    Icon(Icons.Filled.TableChart, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Xuất CSV")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KpiCard(
    title: String,
    value: String,
    subtitle: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = accent
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatusBarRow(status: TicketStatus, count: Int, maxCount: Int) {
    val (fg, _) = statusColors(status)
    val fraction = count.toFloat() / maxCount.toFloat()
    Column(modifier = Modifier.padding(vertical = 5.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = status.labelVi, style = MaterialTheme.typography.bodySmall)
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = fg
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { fraction },
            color = fg,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
        )
    }
}
