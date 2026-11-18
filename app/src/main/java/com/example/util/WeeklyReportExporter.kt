package com.example.util

import android.content.Context
import android.content.Intent
import com.example.data.SupportTicket
import com.example.data.TicketPriority
import com.example.data.TicketStatus
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class WeeklyReportMetrics(
    val weekLabel: String,
    val totalTickets: Int,
    val openCount: Int,
    val acceptedCount: Int,
    val inProgressCount: Int,
    val completedCount: Int,
    val completionRatePercent: Int,
    val avgResolutionHours: Double?,
    val urgentOpenCount: Int,
    val dailyCounts: List<Pair<String, Int>>,
    val techLeaderboard: List<TechnicianPerformance>,
    val filteredTickets: List<SupportTicket>
)

data class TechnicianPerformance(
    val uid: String,
    val name: String,
    val assignedCount: Int,
    val completedCount: Int
)

enum class ReportWindow(val labelVi: String, val daysBack: Int) {
    THIS_WEEK("7 ngày qua", 7),
    TWO_WEEKS("14 ngày qua", 14),
    FOUR_WEEKS("28 ngày qua", 28),
    ALL_TIME("Toàn bộ", 3650)
}

object WeeklyReportCalculator {
    private val dayLabelFormat = SimpleDateFormat("dd/MM", Locale("vi", "VN"))
    private val fullDateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("vi", "VN"))

    fun compute(tickets: List<SupportTicket>, window: ReportWindow): WeeklyReportMetrics {
        val nowMillis = System.currentTimeMillis()
        val cutoffMillis = if (window == ReportWindow.ALL_TIME) 0L else nowMillis - window.daysBack * 86_400_000L

        val scoped = tickets.filter { ticket ->
            val createdMs = ticket.createdAt?.toDate()?.time ?: nowMillis
            createdMs >= cutoffMillis
        }

        val openCount = scoped.count { it.parsedStatus == TicketStatus.OPEN }
        val acceptedCount = scoped.count { it.parsedStatus == TicketStatus.ACCEPTED }
        val inProgressCount = scoped.count { it.parsedStatus == TicketStatus.IN_PROGRESS }
        val completedCount = scoped.count { it.parsedStatus == TicketStatus.COMPLETED }
        val total = scoped.size
        val rate = if (total > 0) ((completedCount * 100.0) / total).toInt() else 0

        val resolutionDurationsHours = scoped.mapNotNull { t ->
            if (t.parsedStatus == TicketStatus.COMPLETED && t.createdAt != null && t.completedAt != null) {
                val diffMs = (t.completedAt.toDate().time - t.createdAt.toDate().time).coerceAtLeast(0L)
                diffMs / 3_600_000.0
            } else null
        }
        val avgHours = if (resolutionDurationsHours.isNotEmpty()) {
            resolutionDurationsHours.average()
        } else null

        val urgentOpen = scoped.count {
            it.parsedPriority == TicketPriority.URGENT && it.parsedStatus != TicketStatus.COMPLETED
        }

        // Last 7 days trend
        val dailyCounts = buildDailyBuckets(tickets)

        val techMap = linkedMapOf<String, MutableList<SupportTicket>>()
        for (ticket in scoped) {
            val techUid = ticket.assigneeUid?.takeIf { it.isNotBlank() } ?: continue
            techMap.getOrPut(techUid) { mutableListOf() }.add(ticket)
        }
        val leaderboard = techMap.map { (uid, list) ->
            val name = list.firstNotNullOfOrNull { it.assigneeName?.takeIf { n -> n.isNotBlank() } } ?: "Kỹ thuật viên"
            TechnicianPerformance(
                uid = uid,
                name = name,
                assignedCount = list.size,
                completedCount = list.count { it.parsedStatus == TicketStatus.COMPLETED }
            )
        }.sortedByDescending { it.completedCount * 10 + it.assignedCount }

        return WeeklyReportMetrics(
            weekLabel = window.labelVi,
            totalTickets = total,
            openCount = openCount,
            acceptedCount = acceptedCount,
            inProgressCount = inProgressCount,
            completedCount = completedCount,
            completionRatePercent = rate,
            avgResolutionHours = avgHours,
            urgentOpenCount = urgentOpen,
            dailyCounts = dailyCounts,
            techLeaderboard = leaderboard,
            filteredTickets = scoped
        )
    }

    private fun buildDailyBuckets(tickets: List<SupportTicket>): List<Pair<String, Int>> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        val buckets = mutableListOf<Pair<String, Int>>()
        for (offset in 6 downTo 0) {
            val dayStart = Calendar.getInstance().apply {
                timeInMillis = cal.timeInMillis
                add(Calendar.DAY_OF_YEAR, -offset)
            }
            val startMs = dayStart.timeInMillis
            val endMs = startMs + 86_400_000L
            val count = tickets.count { t ->
                val ms = t.createdAt?.toDate()?.time ?: return@count false
                ms in startMs until endMs
            }
            buckets.add(dayLabelFormat.format(Date(startMs)) to count)
        }
        return buckets
    }

    fun buildTextSummary(metrics: WeeklyReportMetrics): String {
        val avgStr = metrics.avgResolutionHours?.let { String.format(Locale.US, "%.1f giờ", it) } ?: "Chưa có"
        val builder = StringBuilder()
        builder.appendLine("=== BÁO CÁO TỔNG HỢP HCN SUPPORT (${metrics.weekLabel}) ===")
        builder.appendLine("Ngày xuất: ${fullDateFormat.format(Date())}")
        builder.appendLine("Tổng số ticket: ${metrics.totalTickets}")
        builder.appendLine("- Mở: ${metrics.openCount}")
        builder.appendLine("- Đã tiếp nhận: ${metrics.acceptedCount}")
        builder.appendLine("- Đang xử lý: ${metrics.inProgressCount}")
        builder.appendLine("- Đã hoàn thành: ${metrics.completedCount} (${metrics.completionRatePercent}%)")
        builder.appendLine("Thời gian xử lý trung bình: $avgStr")
        builder.appendLine("Ticket khẩn cấp chưa đóng: ${metrics.urgentOpenCount}")
        if (metrics.techLeaderboard.isNotEmpty()) {
            builder.appendLine()
            builder.appendLine("Hiệu suất kỹ thuật viên:")
            metrics.techLeaderboard.forEachIndexed { idx, perf ->
                builder.appendLine("${idx + 1}. ${perf.name}: Hoàn thành ${perf.completedCount}/${perf.assignedCount}")
            }
        }
        return builder.toString()
    }

    fun buildCsvContent(metrics: WeeklyReportMetrics): String {
        val sb = StringBuilder()
        // UTF-8 BOM for Vietnamese Excel compatibility
        sb.append('\uFEFF')
        sb.appendLine("Mã Ticket,Tiêu đề,Danh mục,Ưu tiên,Trạng thái,Người tạo,Kỹ thuật viên,Ngày tạo,Ngày hoàn thành")
        for (t in metrics.filteredTickets) {
            val created = t.createdAt?.toDate()?.let { fullDateFormat.format(it) } ?: ""
            val completed = t.completedAt?.toDate()?.let { fullDateFormat.format(it) } ?: ""
            val row = listOf(
                t.ticketCode,
                t.title,
                t.category,
                t.parsedPriority.labelVi,
                t.parsedStatus.labelVi,
                t.creatorName,
                t.assigneeName ?: "Chưa gán",
                created,
                completed
            ).joinToString(",") { cell ->
                "\"${cell.replace("\"", "\"\"")}\""
            }
            sb.appendLine(row)
        }
        return sb.toString()
    }

    fun shareSummaryText(context: Context, metrics: WeeklyReportMetrics) {
        val text = buildTextSummary(metrics)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Báo cáo HCN Support - ${metrics.weekLabel}")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Chia sẻ báo cáo tuần"))
    }

    fun shareCsvReport(context: Context, metrics: WeeklyReportMetrics) {
        val csv = buildCsvContent(metrics)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "HCN_Support_${metrics.weekLabel}.csv")
            putExtra(Intent.EXTRA_TEXT, csv)
        }
        context.startActivity(Intent.createChooser(intent, "Xuất & chia sẻ CSV"))
    }
}
