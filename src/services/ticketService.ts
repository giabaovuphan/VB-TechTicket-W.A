import {
  addDoc,
  collection,
  doc,
  onSnapshot,
  orderBy,
  query,
  serverTimestamp,
  updateDoc,
  where,
  writeBatch,
  type DocumentData,
} from 'firebase/firestore';
import { db } from '../firebase';
import {
  SupportTicket,
  TICKET_STATUS_META,
  TicketPriority,
  TicketStatus,
  timestampToMillis,
  UserAccount,
  UserRole,
} from '../types/models';
import { handleFirestoreError, OperationType } from '../utils/firestoreErrors';
import { createNotificationSafe } from './notificationService';

const TICKETS_COLLECTION = 'tickets';
const INVOICES_COLLECTION = 'invoices';

function mapTicket(id: string, data: DocumentData): SupportTicket {
  return {
    id,
    ticketCode: String(data.ticketCode ?? ''),
    title: String(data.title ?? ''),
    description: String(data.description ?? ''),
    category: String(data.category ?? 'Khác'),
    priority: String(data.priority ?? TicketPriority.MEDIUM),
    status: String(data.status ?? TicketStatus.OPEN),
    creatorUid: String(data.creatorUid ?? ''),
    creatorName: String(data.creatorName ?? ''),
    creatorEmail: String(data.creatorEmail ?? ''),
    assigneeUid: typeof data.assigneeUid === 'string' ? data.assigneeUid : null,
    assigneeName: typeof data.assigneeName === 'string' ? data.assigneeName : null,
    teamLeadUid: typeof data.teamLeadUid === 'string' ? data.teamLeadUid : null,
    defectPhotoBase64: typeof data.defectPhotoBase64 === 'string' ? data.defectPhotoBase64 : null,
    resolvedPhotoBase64: typeof data.resolvedPhotoBase64 === 'string' ? data.resolvedPhotoBase64 : null,
    resolutionNote: typeof data.resolutionNote === 'string' ? data.resolutionNote : null,
    createdAt: data.createdAt,
    updatedAt: data.updatedAt,
    completedAt: data.completedAt,
  };
}

function generateTicketCode(): string {
  const num = Math.floor(1000 + Math.random() * 9000);
  return `TK-${num}`;
}

export function subscribeTicketsForRole(
  uid: string,
  role: UserRole,
  onData: (tickets: SupportTicket[]) => void,
  onError?: (err: unknown) => void,
): () => void {
  if (role === UserRole.PENDING) {
    onData([]);
    return () => {};
  }

  if (role === UserRole.TEAM_LEAD || role === UserRole.ADMIN) {
    const q = query(collection(db, TICKETS_COLLECTION), orderBy('updatedAt', 'desc'));
    return onSnapshot(
      q,
      (snap) => {
        onData(snap.docs.map((d) => mapTicket(d.id, d.data())));
      },
      (error) => {
        try {
          handleFirestoreError(error, OperationType.LIST, TICKETS_COLLECTION);
        } catch (e) {
          onError?.(e);
        }
      },
    );
  }

  if (role === UserRole.USER) {
    const q = query(collection(db, TICKETS_COLLECTION), where('creatorUid', '==', uid));
    return onSnapshot(
      q,
      (snap) => {
        const list = snap.docs
          .map((d) => mapTicket(d.id, d.data()))
          .sort((a, b) => (timestampToMillis(b.updatedAt) ?? 0) - (timestampToMillis(a.updatedAt) ?? 0));
        onData(list);
      },
      (error) => {
        try {
          handleFirestoreError(error, OperationType.LIST, TICKETS_COLLECTION);
        } catch (e) {
          onError?.(e);
        }
      },
    );
  }

  // TECHNICIAN sees assigned tickets + tickets they created
  let assignedList: SupportTicket[] = [];
  let createdList: SupportTicket[] = [];

  const emitMerged = () => {
    const mergedMap = new Map<string, SupportTicket>();
    for (const t of [...assignedList, ...createdList]) {
      mergedMap.set(t.id, t);
    }
    const sorted = Array.from(mergedMap.values()).sort(
      (a, b) => (timestampToMillis(b.updatedAt) ?? 0) - (timestampToMillis(a.updatedAt) ?? 0),
    );
    onData(sorted);
  };

  const unsubAssigned = onSnapshot(
    query(collection(db, TICKETS_COLLECTION), where('assigneeUid', '==', uid)),
    (snap) => {
      assignedList = snap.docs.map((d) => mapTicket(d.id, d.data()));
      emitMerged();
    },
    (error) => {
      try {
        handleFirestoreError(error, OperationType.LIST, TICKETS_COLLECTION);
      } catch (e) {
        onError?.(e);
      }
    },
  );

  const unsubCreated = onSnapshot(
    query(collection(db, TICKETS_COLLECTION), where('creatorUid', '==', uid)),
    (snap) => {
      createdList = snap.docs.map((d) => mapTicket(d.id, d.data()));
      emitMerged();
    },
    (error) => {
      try {
        handleFirestoreError(error, OperationType.LIST, TICKETS_COLLECTION);
      } catch (e) {
        onError?.(e);
      }
    },
  );

  return () => {
    unsubAssigned();
    unsubCreated();
  };
}

export function subscribeTicket(
  ticketId: string,
  onData: (ticket: SupportTicket | null) => void,
  onError?: (err: unknown) => void,
): () => void {
  const path = `${TICKETS_COLLECTION}/${ticketId}`;
  return onSnapshot(
    doc(db, TICKETS_COLLECTION, ticketId),
    (snap) => {
      if (!snap.exists()) {
        onData(null);
        return;
      }
      onData(mapTicket(snap.id, snap.data()));
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

export async function createTicket(params: {
  title: string;
  description: string;
  category: string;
  priority: TicketPriority;
  creator: UserAccount;
  defectPhotoBase64?: string | null;
}): Promise<string> {
  const cleanTitle = params.title.trim();
  const cleanDesc = params.description.trim();
  if (cleanTitle.length < 3 || cleanTitle.length > 120) {
    throw new Error('Tiêu đề sự cố phải từ 3 đến 120 ký tự.');
  }
  if (cleanDesc.length < 5 || cleanDesc.length > 2000) {
    throw new Error('Mô tả chi tiết phải từ 5 đến 2000 ký tự.');
  }

  const payload: Record<string, unknown> = {
    ticketCode: generateTicketCode(),
    title: cleanTitle,
    description: cleanDesc,
    category: params.category.trim().slice(0, 60) || 'Khác',
    priority: params.priority,
    status: TicketStatus.OPEN,
    creatorUid: params.creator.uid,
    creatorName: params.creator.displayName.trim().slice(0, 100) || params.creator.email,
    creatorEmail: params.creator.email.trim().slice(0, 150),
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };

  if (params.defectPhotoBase64 && params.defectPhotoBase64.trim().length > 0) {
    if (params.defectPhotoBase64.length > 300_000) {
      throw new Error('Ảnh vượt quá giới hạn 300KB.');
    }
    payload.defectPhotoBase64 = params.defectPhotoBase64;
  }

  try {
    const docRef = await addDoc(collection(db, TICKETS_COLLECTION), payload);
    return docRef.id;
  } catch (error) {
    handleFirestoreError(error, OperationType.CREATE, TICKETS_COLLECTION);
  }
}

export async function assignTicket(params: {
  ticket: SupportTicket;
  assignee: UserAccount;
  priority: TicketPriority;
  teamLeadUid: string;
}): Promise<void> {
  const path = `${TICKETS_COLLECTION}/${params.ticket.id}`;
  const currentStatus = params.ticket.status as TicketStatus;
  const nextStatus = currentStatus === TicketStatus.OPEN ? TicketStatus.ACCEPTED : currentStatus;

  try {
    await updateDoc(doc(db, TICKETS_COLLECTION, params.ticket.id), {
      assigneeUid: params.assignee.uid,
      assigneeName: params.assignee.displayName.trim().slice(0, 100) || params.assignee.email,
      teamLeadUid: params.teamLeadUid,
      priority: params.priority,
      status: nextStatus,
      updatedAt: serverTimestamp(),
    });
  } catch (error) {
    handleFirestoreError(error, OperationType.UPDATE, path);
  }

  await createNotificationSafe({
    recipientUid: params.ticket.creatorUid,
    senderUid: params.teamLeadUid,
    ticketId: params.ticket.id,
    ticketCode: params.ticket.ticketCode,
    title: `Ticket #${params.ticket.ticketCode} đã được phân công`,
    message: `Kỹ thuật viên ${params.assignee.displayName} đã tiếp nhận xử lý.`,
    newStatus: nextStatus,
  });

  await createNotificationSafe({
    recipientUid: params.assignee.uid,
    senderUid: params.teamLeadUid,
    ticketId: params.ticket.id,
    ticketCode: params.ticket.ticketCode,
    title: `Nhiệm vụ mới #${params.ticket.ticketCode}`,
    message: `Bạn được phân công xử lý: ${params.ticket.title}`.slice(0, 300),
    newStatus: nextStatus,
  });
}

export async function startProcessingTicket(params: {
  ticket: SupportTicket;
  actorUid: string;
  resolutionNote?: string;
}): Promise<void> {
  const path = `${TICKETS_COLLECTION}/${params.ticket.id}`;
  const updates: Record<string, unknown> = {
    status: TicketStatus.IN_PROGRESS,
    updatedAt: serverTimestamp(),
  };
  const cleanNote = params.resolutionNote?.trim().slice(0, 1000);
  if (cleanNote) {
    updates.resolutionNote = cleanNote;
  }

  try {
    await updateDoc(doc(db, TICKETS_COLLECTION, params.ticket.id), updates);
  } catch (error) {
    handleFirestoreError(error, OperationType.UPDATE, path);
  }

  await createNotificationSafe({
    recipientUid: params.ticket.creatorUid,
    senderUid: params.actorUid,
    ticketId: params.ticket.id,
    ticketCode: params.ticket.ticketCode,
    title: `Ticket #${params.ticket.ticketCode} đang được xử lý`,
    message: 'Kỹ thuật viên đã bắt đầu kiểm tra và khắc phục sự cố.',
    newStatus: TicketStatus.IN_PROGRESS,
  });
}

export async function updateInProgressWork(params: {
  ticket: SupportTicket;
  resolutionNote?: string;
  resolvedPhotoBase64?: string | null;
}): Promise<void> {
  const path = `${TICKETS_COLLECTION}/${params.ticket.id}`;
  const updates: Record<string, unknown> = {
    updatedAt: serverTimestamp(),
  };
  if (params.resolutionNote !== undefined) {
    const cleanNote = params.resolutionNote.trim().slice(0, 1000);
    if (cleanNote) {
      updates.resolutionNote = cleanNote;
    }
  }
  if (params.resolvedPhotoBase64 && params.resolvedPhotoBase64.trim().length >= 10) {
    updates.resolvedPhotoBase64 = params.resolvedPhotoBase64;
  }

  try {
    await updateDoc(doc(db, TICKETS_COLLECTION, params.ticket.id), updates);
  } catch (error) {
    handleFirestoreError(error, OperationType.UPDATE, path);
  }
}

export async function completeTicket(params: {
  ticket: SupportTicket;
  actorUid: string;
  resolvedPhotoBase64: string;
  resolutionNote?: string;
}): Promise<void> {
  if (!params.resolvedPhotoBase64 || params.resolvedPhotoBase64.trim().length < 10) {
    throw new Error('Bắt buộc phải tải ảnh minh chứng đã xử lý xong trước khi hoàn thành.');
  }
  if (params.resolvedPhotoBase64.length > 300_000) {
    throw new Error('Ảnh minh chứng vượt quá giới hạn 300KB.');
  }

  const path = `${TICKETS_COLLECTION}/${params.ticket.id}`;
  const updates: Record<string, unknown> = {
    status: TicketStatus.COMPLETED,
    resolvedPhotoBase64: params.resolvedPhotoBase64,
    completedAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
  const cleanNote = params.resolutionNote?.trim().slice(0, 1000);
  if (cleanNote) {
    updates.resolutionNote = cleanNote;
  }

  try {
    await updateDoc(doc(db, TICKETS_COLLECTION, params.ticket.id), updates);
  } catch (error) {
    handleFirestoreError(error, OperationType.UPDATE, path);
  }

  await createNotificationSafe({
    recipientUid: params.ticket.creatorUid,
    senderUid: params.actorUid,
    ticketId: params.ticket.id,
    ticketCode: params.ticket.ticketCode,
    title: `Ticket #${params.ticket.ticketCode} đã hoàn thành`,
    message: 'Sự cố đã được xử lý xong kèm ảnh xác nhận nghiệm thu.',
    newStatus: TicketStatus.COMPLETED,
  });
}

export async function adminForceStatus(params: {
  ticket: SupportTicket;
  actorUid: string;
  newStatus: TicketStatus;
}): Promise<void> {
  const path = `${TICKETS_COLLECTION}/${params.ticket.id}`;
  const updates: Record<string, unknown> = {
    status: params.newStatus,
    updatedAt: serverTimestamp(),
  };
  if (params.newStatus === TicketStatus.COMPLETED && !params.ticket.completedAt) {
    updates.completedAt = serverTimestamp();
  }

  try {
    await updateDoc(doc(db, TICKETS_COLLECTION, params.ticket.id), updates);
  } catch (error) {
    handleFirestoreError(error, OperationType.UPDATE, path);
  }

  await createNotificationSafe({
    recipientUid: params.ticket.creatorUid,
    senderUid: params.actorUid,
    ticketId: params.ticket.id,
    ticketCode: params.ticket.ticketCode,
    title: `Admin cập nhật Ticket #${params.ticket.ticketCode}`,
    message: `Trạng thái mới: ${TICKET_STATUS_META[params.newStatus].labelVi}.`,
    newStatus: params.newStatus,
  });
}

export async function deleteTicketAndInvoice(ticketId: string): Promise<void> {
  const path = `${TICKETS_COLLECTION}/${ticketId}`;
  try {
    const batch = writeBatch(db);
    batch.delete(doc(db, TICKETS_COLLECTION, ticketId));
    batch.delete(doc(db, INVOICES_COLLECTION, ticketId));
    await batch.commit();
  } catch (error) {
    handleFirestoreError(error, OperationType.DELETE, path);
  }
}
