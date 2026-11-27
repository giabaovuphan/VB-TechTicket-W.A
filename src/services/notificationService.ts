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
import { TicketNotification, TicketStatus } from '../types/models';
import { handleFirestoreError, OperationType } from '../utils/firestoreErrors';

const NOTIFICATIONS_COLLECTION = 'notifications';

function mapNotification(id: string, data: DocumentData): TicketNotification {
  return {
    id,
    recipientUid: String(data.recipientUid ?? ''),
    senderUid: String(data.senderUid ?? ''),
    ticketId: String(data.ticketId ?? ''),
    ticketCode: String(data.ticketCode ?? ''),
    title: String(data.title ?? ''),
    message: String(data.message ?? ''),
    newStatus: String(data.newStatus ?? TicketStatus.OPEN),
    readFlag: Boolean(data.readFlag ?? false),
    createdAt: data.createdAt,
  };
}

export function subscribeNotifications(
  uid: string,
  onData: (items: TicketNotification[]) => void,
  onError?: (err: unknown) => void,
): () => void {
  const q = query(
    collection(db, NOTIFICATIONS_COLLECTION),
    where('recipientUid', '==', uid),
    orderBy('createdAt', 'desc'),
  );
  return onSnapshot(
    q,
    (snap) => {
      onData(snap.docs.map((d) => mapNotification(d.id, d.data())));
    },
    (error) => {
      try {
        handleFirestoreError(error, OperationType.LIST, NOTIFICATIONS_COLLECTION);
      } catch (e) {
        onError?.(e);
      }
    },
  );
}

export async function createNotificationSafe(params: {
  recipientUid: string;
  senderUid: string;
  ticketId: string;
  ticketCode: string;
  title: string;
  message: string;
  newStatus: TicketStatus;
}): Promise<void> {
  if (!params.recipientUid || params.recipientUid === params.senderUid) {
    return;
  }
  try {
    await addDoc(collection(db, NOTIFICATIONS_COLLECTION), {
      recipientUid: params.recipientUid,
      senderUid: params.senderUid,
      ticketId: params.ticketId,
      ticketCode: params.ticketCode.slice(0, 20),
      title: params.title.slice(0, 120),
      message: params.message.slice(0, 300),
      newStatus: params.newStatus,
      readFlag: false,
      createdAt: serverTimestamp(),
    });
  } catch (error) {
    console.warn('Notification creation failed:', error);
  }
}

export async function markNotificationRead(notificationId: string): Promise<void> {
  const path = `${NOTIFICATIONS_COLLECTION}/${notificationId}`;
  try {
    await updateDoc(doc(db, NOTIFICATIONS_COLLECTION, notificationId), {
      readFlag: true,
    });
  } catch (error) {
    handleFirestoreError(error, OperationType.UPDATE, path);
  }
}

export async function markAllNotificationsRead(notifications: TicketNotification[]): Promise<void> {
  const unread = notifications.filter((n) => !n.readFlag);
  if (unread.length === 0) return;
  try {
    const batch = writeBatch(db);
    for (const item of unread) {
      batch.update(doc(db, NOTIFICATIONS_COLLECTION, item.id), {
        readFlag: true,
      });
    }
    await batch.commit();
  } catch (error) {
    handleFirestoreError(error, OperationType.WRITE, NOTIFICATIONS_COLLECTION);
  }
}
