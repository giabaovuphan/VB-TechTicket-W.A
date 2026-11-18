package com.example.util

import android.content.Context
import android.content.Intent
import com.example.data.RepairInvoice
import com.example.data.SupportTicket
import com.example.data.formatVnd
import com.example.ui.components.formatTimestampVi

object InvoiceExporter {
    fun buildInvoiceSummary(ticket: SupportTicket, invoice: RepairInvoice): String {
        val sb = StringBuilder()
        sb.appendLine("=== HÓA ĐƠN SỬA CHỮA HCN SUPPORT ===")
        sb.appendLine("Mã hóa đơn: ${invoice.invoiceCode}")
        sb.appendLine("Mã ticket: #${ticket.ticketCode} - ${ticket.title}")
        sb.appendLine("Khách hàng: ${ticket.creatorName} (${ticket.creatorEmail})")
        if (!ticket.assigneeName.isNullOrBlank()) {
            sb.appendLine("Kỹ thuật viên: ${ticket.assigneeName}")
        }
        sb.appendLine("Ngày cập nhật: ${formatTimestampVi(invoice.updatedAt ?: invoice.createdAt)}")
        sb.appendLine("----------------------------------------")
        if (invoice.items.isNotEmpty()) {
            sb.appendLine("Linh kiện / Sản phẩm thay thế:")
            invoice.items.forEachIndexed { index, item ->
                sb.appendLine(
                    "${index + 1}. ${item.name} (${item.quantity} ${item.unit} x ${formatVnd(item.unitPrice)}) = ${formatVnd(item.lineTotal)}"
                )
            }
            sb.appendLine("Tổng linh kiện: ${formatVnd(invoice.partsSubtotal)}")
        } else {
            sb.appendLine("Linh kiện thay thế: Không phát sinh")
        }
        sb.appendLine("Phí công sửa chữa: ${formatVnd(invoice.laborFee)}")
        sb.appendLine("TỔNG THANH TOÁN: ${formatVnd(invoice.totalAmount)}")
        sb.appendLine("----------------------------------------")
        sb.appendLine("Phương thức: ${invoice.parsedPaymentMethod.labelVi}")
        sb.appendLine("Trạng thái: ${invoice.parsedPaymentStatus.labelVi}")
        if (!invoice.note.isNullOrBlank()) {
            sb.appendLine("Ghi chú: ${invoice.note}")
        }
        return sb.toString()
    }

    fun shareInvoice(context: Context, ticket: SupportTicket, invoice: RepairInvoice) {
        val summary = buildInvoiceSummary(ticket, invoice)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Hóa đơn ${invoice.invoiceCode} - Ticket #${ticket.ticketCode}")
            putExtra(Intent.EXTRA_TEXT, summary)
        }
        context.startActivity(Intent.createChooser(intent, "Chia sẻ hóa đơn sửa chữa"))
    }
}
