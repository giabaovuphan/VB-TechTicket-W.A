import {
  doc,
  onSnapshot,
  runTransaction,
  serverTimestamp,
  updateDoc,
  type DocumentData,
} from 'firebase/firestore';
import { db } from '../firebase';
import {
  InvoiceLineItem,
  InvoicePaymentMethod,
  InvoicePaymentStatus,
  RepairInvoice,
  SupportTicket,
  TicketStatus,
  UserAccount,
  UserRole,
} from '../types/models';
import { handleFirestoreError, OperationType } from '../utils/firestoreErrors';
import {
  canApplyInventoryDeltasForRole,
  computeInventoryDeltas,
  normalizeInvoiceLineItems,
  validateInvoiceSave,
} from '../utils/invoiceUtils';
import { createNotificationSafe } from './notificationService';

const INVOICES_COLLECTION = 'invoices';
const INVENTORY_COLLECTION = 'inventory';

function mapLineItem(raw: DocumentData): InvoiceLineItem {
  const quantity = Number(raw.quantity ?? 1);
  const unitPrice = Number(raw.unitPrice ?? 0);
  return {
    productId: typeof raw.productId === 'string' ? raw.productId : null,
    inventoryId: typeof raw.inventoryId === 'string' ? raw.inventoryId : null,
    name: String(raw.name ?? ''),
    unit: String(raw.unit ?? 'Cái'),
    quantity,
    unitPrice,
    lineTotal: Number(raw.lineTotal ?? quantity * unitPrice),
    customEntry: Boolean(raw.customEntry ?? false),
  };
}

function serializeLineItem(item: InvoiceLineItem): Record<string, unknown> {
  const mapped: Record<string, unknown> = {
    name: item.name,
    unit: item.unit,
    quantity: item.quantity,
    unitPrice: item.unitPrice,
    lineTotal: item.lineTotal,
    customEntry: item.customEntry,
  };
  if (item.productId) mapped.productId = item.productId;
  if (item.inventoryId) mapped.inventoryId = item.inventoryId;
  return mapped;
}

function mapInvoice(id: string, data: DocumentData): RepairInvoice {
  const items = Array.isArray(data.items) ? data.items.map((item) => mapLineItem(item)) : [];
  return {
    id,
    invoiceCode: String(data.invoiceCode ?? `HD-${id.slice(0, 6).toUpperCase()}`),
    ticketId: String(data.ticketId ?? id),
    ticketCode: String(data.ticketCode ?? ''),
    creatorUid: String(data.creatorUid ?? ''),
    assigneeUid: typeof data.assigneeUid === 'string' ? data.assigneeUid : null,
    items,
    partsSubtotal: Number(data.partsSubtotal ?? 0),
    laborFee: Number(data.laborFee ?? 0),
    totalAmount: Number(data.totalAmount ?? 0),
    paymentStatus: String(data.paymentStatus ?? InvoicePaymentStatus.UNPAID),
    paymentMethod: String(data.paymentMethod ?? InvoicePaymentMethod.CASH),
    note: typeof data.note === 'string' ? data.note : null,
    updatedByUid: String(data.updatedByUid ?? ''),
    updatedByName: String(data.updatedByName ?? ''),
    createdAt: data.createdAt,
    updatedAt: data.updatedAt,
    paidAt: data.paidAt,
  };
}

export function subscribeInvoice(
  ticketId: string,
  onData: (invoice: RepairInvoice | null) => void,
  onError?: (err: unknown) => void,
): () => void {
  const path = `${INVOICES_COLLECTION}/${ticketId}`;
  return onSnapshot(
    doc(db, INVOICES_COLLECTION, ticketId),
    (snap) => {
      if (!snap.exists()) {
        onData(null);
        return;
      }
      onData(mapInvoice(snap.id, snap.data()));
    },
    (error) => {
      try {
        handleFirestoreError(error, OperationType.GET, path);
      } catch (e) {
        onError?.(e);
      }
    },
  );
}

export async function saveInvoice(params: {
  ticket: SupportTicket;
  existingInvoice: RepairInvoice | null;
  items: InvoiceLineItem[];
  laborFee: number;
  paymentMethod: InvoicePaymentMethod;
  paymentStatus: InvoicePaymentStatus;
  note?: string;
  actor: UserAccount;
  actorRole: UserRole;
}): Promise<void> {
  const cleanItems = normalizeInvoiceLineItems(params.items);
  validateInvoiceSave(cleanItems, params.laborFee, params.paymentMethod);

  const deltas = computeInventoryDeltas(params.existingInvoice?.items, cleanItems);
  if (!canApplyInventoryDeltasForRole(params.actorRole, deltas)) {
    throw new Error(
      'Kỹ thuật viên chỉ có thể xuất thêm vật tư từ kho. Vui lòng liên hệ Trưởng nhóm hoặc Quản trị viên để hoàn trả vật tư về kho.',
    );
  }

  const partsSubtotal = cleanItems.reduce((sum, item) => sum + item.lineTotal, 0);
  const totalAmount = partsSubtotal + params.laborFee;
  const cleanNote = params.note?.trim().slice(0, 500);
  const invoiceRef = doc(db, INVOICES_COLLECTION, params.ticket.id);
  const invoicePath = `${INVOICES_COLLECTION}/${params.ticket.id}`;
  const invoiceCode =
    params.existingInvoice?.invoiceCode ??
    `HD-${params.ticket.ticketCode.replace(/^TK-/, '')}`.slice(0, 24);

  try {
    await runTransaction(db, async (tx) => {
      // Read phase first: inspect invoice & all affected inventory documents
      const currentInvoiceSnap = await tx.get(invoiceRef);
      const deltaEntries = Object.entries(deltas);
      const invSnaps = await Promise.all(
        deltaEntries.map(async ([invId, delta]) => ({
          invId,
          delta,
          ref: doc(db, INVENTORY_COLLECTION, invId),
          snap: await tx.get(doc(db, INVENTORY_COLLECTION, invId)),
        })),
      );

      for (const entry of invSnaps) {
        if (!entry.snap.exists()) {
          throw new Error('Không tìm thấy vật tư trong kho để cập nhật tồn kho.');
        }
        const data = entry.snap.data();
        const currentQty = Number(data.quantityOnHand ?? 0);
        const nextQty = currentQty - entry.delta;
        if (nextQty < 0) {
          throw new Error(
            `Vật tư "${String(data.name ?? entry.invId)}" chỉ còn ${currentQty} ${String(data.unit ?? 'cái')} trong kho.`,
          );
        }
        tx.update(entry.ref, {
          quantityOnHand: nextQty,
          updatedByUid: params.actor.uid,
          updatedAt: serverTimestamp(),
        });
      }

      const serializedItems = cleanItems.map(serializeLineItem);
      const isPaid = params.paymentStatus === InvoicePaymentStatus.PAID;

      if (!currentInvoiceSnap.exists()) {
        const createPayload: Record<string, unknown> = {
          invoiceCode,
          ticketId: params.ticket.id,
          ticketCode: params.ticket.ticketCode,
          creatorUid: params.ticket.creatorUid,
          items: serializedItems,
          partsSubtotal,
          laborFee: params.laborFee,
          totalAmount,
          paymentStatus: params.paymentStatus,
          paymentMethod: params.paymentMethod,
          updatedByUid: params.actor.uid,
          updatedByName: params.actor.displayName.trim().slice(0, 100) || params.actor.email,
          createdAt: serverTimestamp(),
          updatedAt: serverTimestamp(),
        };
        if (params.ticket.assigneeUid) createPayload.assigneeUid = params.ticket.assigneeUid;
        if (cleanNote) createPayload.note = cleanNote;
        if (isPaid) createPayload.paidAt = serverTimestamp();
        tx.set(invoiceRef, createPayload);
      } else {
        const updatePayload: Record<string, unknown> = {
          items: serializedItems,
          partsSubtotal,
          laborFee: params.laborFee,
          totalAmount,
          paymentStatus: params.paymentStatus,
          paymentMethod: params.paymentMethod,
          updatedByUid: params.actor.uid,
          updatedByName: params.actor.displayName.trim().slice(0, 100) || params.actor.email,
          updatedAt: serverTimestamp(),
        };
        if (params.ticket.assigneeUid) updatePayload.assigneeUid = params.ticket.assigneeUid;
        if (cleanNote) updatePayload.note = cleanNote;
        if (isPaid && !currentInvoiceSnap.data()?.paidAt) {
          updatePayload.paidAt = serverTimestamp();
        }
        tx.update(invoiceRef, updatePayload);
      }
    });
  } catch (error) {
    if (error instanceof Error && !error.message.toLowerCase().includes('permission')) {
      throw error;
    }
    handleFirestoreError(error, OperationType.WRITE, invoicePath);
  }

  await createNotificationSafe({
    recipientUid: params.ticket.creatorUid,
    senderUid: params.actor.uid,
    ticketId: params.ticket.id,
    ticketCode: params.ticket.ticketCode,
    title: `Hóa đơn Ticket #${params.ticket.ticketCode}`,
    message: `Tổng chi phí sửa chữa đã cập nhật: ${new Intl.NumberFormat('vi-VN').format(Math.round(totalAmount))} đ.`,
    newStatus: (params.ticket.status as TicketStatus) || TicketStatus.IN_PROGRESS,
  });
}

export async function markInvoiceAsPaid(params: {
  ticket: SupportTicket;
  invoice: RepairInvoice;
  paymentMethod: InvoicePaymentMethod;
  actor: UserAccount;
}): Promise<void> {
  const path = `${INVOICES_COLLECTION}/${params.invoice.id}`;
  try {
    await updateDoc(doc(db, INVOICES_COLLECTION, params.invoice.id), {
      paymentStatus: InvoicePaymentStatus.PAID,
      paymentMethod: params.paymentMethod,
      paidAt: serverTimestamp(),
      updatedByUid: params.actor.uid,
      updatedByName: params.actor.displayName.trim().slice(0, 100) || params.actor.email,
      updatedAt: serverTimestamp(),
    });
  } catch (error) {
    handleFirestoreError(error, OperationType.UPDATE, path);
  }

  await createNotificationSafe({
    recipientUid: params.ticket.creatorUid,
    senderUid: params.actor.uid,
    ticketId: params.ticket.id,
    ticketCode: params.ticket.ticketCode,
    title: `Thanh toán hóa đơn #${params.invoice.invoiceCode}`,
    message: 'Hóa đơn sửa chữa đã được xác nhận thanh toán.',
    newStatus: (params.ticket.status as TicketStatus) || TicketStatus.COMPLETED,
  });
}
