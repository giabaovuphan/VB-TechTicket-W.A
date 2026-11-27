import {
  formatTimestampVi,
  formatVnd,
  INVOICE_PAYMENT_METHOD_LABELS,
  INVOICE_PAYMENT_STATUS_LABELS,
  InvoiceLineItem,
  InvoicePaymentMethod,
  parseInvoicePaymentMethod,
  parseInvoicePaymentStatus,
  ProductUnits,
  RepairInvoice,
  SupportTicket,
  UserRole,
} from '../types/models';

export function normalizeInvoiceLineItems(items: InvoiceLineItem[]): InvoiceLineItem[] {
  if (items.length > 50) {
    throw new Error('Hóa đơn tối đa 50 dòng linh kiện.');
  }
  return items.map((item) => {
    const cleanName = item.name.trim();
    const cleanUnit = item.unit.trim() || ProductUnits[0];
    if (cleanName.length < 1 || cleanName.length > 120) {
      throw new Error('Tên linh kiện trong hóa đơn phải từ 1 đến 120 ký tự.');
    }
    if (!Number.isInteger(item.quantity) || item.quantity < 1 || item.quantity > 9999) {
      throw new Error(`Số lượng linh kiện '${cleanName}' phải từ 1 trở lên.`);
    }
    if (!Number.isFinite(item.unitPrice) || item.unitPrice < 0 || item.unitPrice > 1_000_000_000) {
      throw new Error(`Đơn giá linh kiện '${cleanName}' không hợp lệ.`);
    }
    const lineTotal = item.unitPrice * item.quantity;
    return {
      ...item,
      name: cleanName,
      unit: cleanUnit.slice(0, 30),
      lineTotal,
    };
  });
}

export function computeInventoryDeltas(
  existingItems: InvoiceLineItem[] | undefined,
  nextItems: InvoiceLineItem[],
): Record<string, number> {
  const oldQtyByInv: Record<string, number> = {};
  for (const item of existingItems ?? []) {
    const invId = item.inventoryId?.trim();
    if (invId) {
      oldQtyByInv[invId] = (oldQtyByInv[invId] ?? 0) + item.quantity;
    }
  }
  const newQtyByInv: Record<string, number> = {};
  for (const item of nextItems) {
    const invId = item.inventoryId?.trim();
    if (invId) {
      newQtyByInv[invId] = (newQtyByInv[invId] ?? 0) + item.quantity;
    }
  }
  const deltas: Record<string, number> = {};
  const allIds = new Set([...Object.keys(oldQtyByInv), ...Object.keys(newQtyByInv)]);
  for (const invId of allIds) {
    const delta = (newQtyByInv[invId] ?? 0) - (oldQtyByInv[invId] ?? 0);
    if (delta !== 0) {
      deltas[invId] = delta;
    }
  }
  return deltas;
}

export function canApplyInventoryDeltasForRole(role: UserRole, deltas: Record<string, number>): boolean {
  if (role === UserRole.TECHNICIAN && Object.values(deltas).some((d) => d < 0)) {
    return false;
  }
  return true;
}

export function validateInvoiceSave(
  items: InvoiceLineItem[],
  laborFee: number,
  paymentMethod: InvoicePaymentMethod,
): void {
  if (!Number.isFinite(laborFee) || laborFee < 0 || laborFee > 10_000_000_000) {
    throw new Error('Phí công sửa chữa không hợp lệ.');
  }
  if (items.length === 0 && laborFee <= 0 && paymentMethod !== InvoicePaymentMethod.INTERNAL) {
    throw new Error('Vui lòng thêm ít nhất 1 linh kiện hoặc nhập phí công sửa chữa.');
  }
}

export function buildInvoiceSummary(ticket: SupportTicket, invoice: RepairInvoice): string {
  const lines: string[] = [
    '=== HÓA ĐƠN SỬA CHỮA HCN SUPPORT ===',
    `Mã hóa đơn: ${invoice.invoiceCode}`,
    `Mã ticket: #${ticket.ticketCode} - ${ticket.title}`,
    `Khách hàng: ${ticket.creatorName} (${ticket.creatorEmail})`,
  ];
  if (ticket.assigneeName) {
    lines.push(`Kỹ thuật viên: ${ticket.assigneeName}`);
  }
  lines.push(`Ngày cập nhật: ${formatTimestampVi(invoice.updatedAt ?? invoice.createdAt)}`);
  lines.push('----------------------------------------');
  if (invoice.items.length > 0) {
    lines.push('Linh kiện / Sản phẩm thay thế:');
    invoice.items.forEach((item, index) => {
      lines.push(
        `${index + 1}. ${item.name} (${item.quantity} ${item.unit} x ${formatVnd(item.unitPrice)}) = ${formatVnd(item.lineTotal)}`,
      );
    });
    lines.push(`Tổng linh kiện: ${formatVnd(invoice.partsSubtotal)}`);
  } else {
    lines.push('Linh kiện thay thế: Không phát sinh');
  }
  lines.push(`Phí công sửa chữa: ${formatVnd(invoice.laborFee)}`);
  lines.push(`TỔNG THANH TOÁN: ${formatVnd(invoice.totalAmount)}`);
  lines.push('----------------------------------------');
  lines.push(`Phương thức: ${INVOICE_PAYMENT_METHOD_LABELS[parseInvoicePaymentMethod(invoice.paymentMethod)]}`);
  lines.push(`Trạng thái: ${INVOICE_PAYMENT_STATUS_LABELS[parseInvoicePaymentStatus(invoice.paymentStatus)]}`);
  if (invoice.note) {
    lines.push(`Ghi chú: ${invoice.note}`);
  }
  return `${lines.join('\n')}\n`;
}

export async function shareOrCopyInvoice(
  ticket: SupportTicket,
  invoice: RepairInvoice,
): Promise<'shared' | 'copied'> {
  const summary = buildInvoiceSummary(ticket, invoice);
  if (typeof navigator !== 'undefined' && typeof navigator.share === 'function') {
    try {
      await navigator.share({
        title: `Hóa đơn ${invoice.invoiceCode} - Ticket #${ticket.ticketCode}`,
        text: summary,
      });
      return 'shared';
    } catch {
      // Fallback to clipboard copy
    }
  }
  if (typeof navigator !== 'undefined' && navigator.clipboard) {
    await navigator.clipboard.writeText(summary);
    return 'copied';
  }
  return 'copied';
}
