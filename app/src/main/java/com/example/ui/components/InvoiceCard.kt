package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.InvoicePaymentStatus
import com.example.data.RepairInvoice
import com.example.data.UiState
import com.example.data.formatVnd
import com.example.ui.theme.LocalSemanticColors

@Composable
fun RepairInvoiceCard(
    invoiceState: UiState<RepairInvoice?>,
    canManageInvoice: Boolean,
    isProcessing: Boolean,
    onOpenEditor: (RepairInvoice?) -> Unit,
    onMarkPaid: (RepairInvoice) -> Unit,
    onShareInvoice: (RepairInvoice) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalSemanticColors.current

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.ReceiptLong,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Hóa đơn & Chi phí sửa chữa",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                val existing = (invoiceState as? UiState.Success)?.data
                if (existing != null) {
                    val isPaid = existing.parsedPaymentStatus == InvoicePaymentStatus.PAID
                    val fg = if (isPaid) palette.completedFg else palette.progressFg
                    val bg = if (isPaid) palette.completedBg else palette.progressBg
                    Surface(
                        color = bg,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = existing.parsedPaymentStatus.labelVi,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = fg,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            when (invoiceState) {
                is UiState.Loading -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Đang tải thông tin hóa đơn...", style = MaterialTheme.typography.bodySmall)
                    }
                }

                is UiState.Error -> {
                    Text(
                        text = invoiceState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                is UiState.Success -> {
                    val invoice = invoiceState.data
                    if (invoice == null) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "Chưa có hóa đơn linh kiện hoặc chi phí sửa chữa.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (canManageInvoice) {
                                        "Thêm linh kiện thay thế từ danh mục hoặc nhập tay để tạo thanh toán cho ticket này."
                                    } else {
                                        "Kỹ thuật viên hoặc Trưởng nhóm sẽ cập nhật hóa đơn nếu có thay thế linh kiện hoặc chi phí."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (canManageInvoice) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { onOpenEditor(null) },
                                enabled = !isProcessing,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("create_invoice_button")
                            ) {
                                Icon(Icons.Filled.AddCard, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Tạo hóa đơn & Thêm linh kiện")
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Mã hóa đơn: ${invoice.invoiceCode}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = invoice.parsedPaymentMethod.labelVi,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (invoice.items.isNotEmpty()) {
                            Text(
                                text = "Linh kiện / Sản phẩm thay thế (${invoice.items.size})",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    invoice.items.forEachIndexed { index, item ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "${index + 1}. ${item.name}",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "${item.quantity} ${item.unit} × ${formatVnd(item.unitPrice)}" +
                                                        if (item.customEntry) " • Nhập tay" else "",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = formatVnd(item.lineTotal),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        if (index < invoice.items.lastIndex) {
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        InvoiceSummaryRow("Tổng tiền linh kiện", formatVnd(invoice.partsSubtotal))
                        InvoiceSummaryRow("Phí công sửa chữa", formatVnd(invoice.laborFee))
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Tổng thanh toán",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = formatVnd(invoice.totalAmount),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (!invoice.note.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Ghi chú: ${invoice.note}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Cập nhật bởi ${invoice.updatedByName} • ${formatTimestampVi(invoice.updatedAt ?: invoice.createdAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        if (canManageInvoice && invoice.parsedPaymentStatus != InvoicePaymentStatus.PAID) {
                            Button(
                                onClick = { onMarkPaid(invoice) },
                                enabled = !isProcessing,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = palette.completedFg
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("mark_invoice_paid_button")
                            ) {
                                Icon(Icons.Filled.Payments, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Xác nhận đã thanh toán (${formatVnd(invoice.totalAmount)})")
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        } else if (invoice.parsedPaymentStatus == InvoicePaymentStatus.PAID) {
                            Surface(
                                color = palette.completedBg.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = palette.completedFg,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Đã thanh toán (${invoice.parsedPaymentMethod.labelVi})" +
                                            if (invoice.paidAt != null) " lúc ${formatTimestampVi(invoice.paidAt)}" else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = palette.completedFg
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (canManageInvoice) {
                                OutlinedButton(
                                    onClick = { onOpenEditor(invoice) },
                                    enabled = !isProcessing,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("edit_invoice_button")
                                ) {
                                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Sửa hóa đơn")
                                }
                            }
                            OutlinedButton(
                                onClick = { onShareInvoice(invoice) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("share_invoice_button")
                            ) {
                                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Chia sẻ")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InvoiceSummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
