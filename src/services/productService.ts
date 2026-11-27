import {
  addDoc,
  collection,
  deleteDoc,
  deleteField,
  doc,
  onSnapshot,
  orderBy,
  query,
  serverTimestamp,
  updateDoc,
  where,
  type DocumentData,
} from 'firebase/firestore';
import { db } from '../firebase';
import { ProductItem, ProductUnits, UserRole } from '../types/models';
import { handleFirestoreError, OperationType } from '../utils/firestoreErrors';

const PRODUCTS_COLLECTION = 'products';

function mapProduct(id: string, data: DocumentData): ProductItem {
  return {
    id,
    name: String(data.name ?? ''),
    sku: typeof data.sku === 'string' ? data.sku : null,
    category: String(data.category ?? 'Phần cứng'),
    unit: String(data.unit ?? ProductUnits[0]),
    unitPrice: Number(data.unitPrice ?? 0),
    note: typeof data.note === 'string' ? data.note : null,
    active: Boolean(data.active ?? true),
    createdByUid: String(data.createdByUid ?? ''),
    createdAt: data.createdAt,
    updatedAt: data.updatedAt,
  };
}

export function subscribeProducts(
  role: UserRole,
  onData: (items: ProductItem[]) => void,
  onError?: (err: unknown) => void,
): () => void {
  const q =
    role === UserRole.TEAM_LEAD || role === UserRole.ADMIN
      ? query(collection(db, PRODUCTS_COLLECTION), orderBy('name', 'asc'))
      : query(
          collection(db, PRODUCTS_COLLECTION),
          where('active', '==', true),
          orderBy('name', 'asc'),
        );

  return onSnapshot(
    q,
    (snap) => {
      onData(snap.docs.map((d) => mapProduct(d.id, d.data())));
    },
    (error) => {
      try {
        handleFirestoreError(error, OperationType.LIST, PRODUCTS_COLLECTION);
      } catch (e) {
        onError?.(e);
      }
    },
  );
}

export async function upsertProduct(params: {
  id?: string;
  name: string;
  sku?: string;
  category: string;
  unit: string;
  unitPrice: number;
  note?: string;
  active: boolean;
  actorUid: string;
}): Promise<void> {
  const cleanName = params.name.trim();
  if (cleanName.length < 2 || cleanName.length > 120) {
    throw new Error('Tên sản phẩm/dịch vụ phải từ 2 đến 120 ký tự.');
  }
  if (!Number.isFinite(params.unitPrice) || params.unitPrice < 0 || params.unitPrice > 1_000_000_000) {
    throw new Error('Đơn giá không hợp lệ.');
  }

  const cleanSku = params.sku?.trim().slice(0, 40);
  const cleanNote = params.note?.trim().slice(0, 300);

  if (!params.id) {
    const payload: Record<string, unknown> = {
      name: cleanName,
      category: params.category.trim().slice(0, 60) || 'Phần cứng',
      unit: params.unit.trim().slice(0, 30) || ProductUnits[0],
      unitPrice: params.unitPrice,
      active: params.active,
      createdByUid: params.actorUid,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    };
    if (cleanSku) payload.sku = cleanSku;
    if (cleanNote) payload.note = cleanNote;
    try {
      await addDoc(collection(db, PRODUCTS_COLLECTION), payload);
    } catch (error) {
      handleFirestoreError(error, OperationType.CREATE, PRODUCTS_COLLECTION);
    }
  } else {
    const path = `${PRODUCTS_COLLECTION}/${params.id}`;
    const updates: Record<string, unknown> = {
      name: cleanName,
      category: params.category.trim().slice(0, 60) || 'Phần cứng',
      unit: params.unit.trim().slice(0, 30) || ProductUnits[0],
      unitPrice: params.unitPrice,
      active: params.active,
      updatedAt: serverTimestamp(),
      sku: cleanSku ? cleanSku : deleteField(),
      note: cleanNote ? cleanNote : deleteField(),
    };
    try {
      await updateDoc(doc(db, PRODUCTS_COLLECTION, params.id), updates);
    } catch (error) {
      handleFirestoreError(error, OperationType.UPDATE, path);
    }
  }
}

export async function toggleProductActive(params: {
  productId: string;
  active: boolean;
}): Promise<void> {
  const path = `${PRODUCTS_COLLECTION}/${params.productId}`;
  try {
    await updateDoc(doc(db, PRODUCTS_COLLECTION, params.productId), {
      active: params.active,
      updatedAt: serverTimestamp(),
    });
  } catch (error) {
    handleFirestoreError(error, OperationType.UPDATE, path);
  }
}

export async function deleteProduct(productId: string): Promise<void> {
  const path = `${PRODUCTS_COLLECTION}/${productId}`;
  try {
    await deleteDoc(doc(db, PRODUCTS_COLLECTION, productId));
  } catch (error) {
    handleFirestoreError(error, OperationType.DELETE, path);
  }
}
