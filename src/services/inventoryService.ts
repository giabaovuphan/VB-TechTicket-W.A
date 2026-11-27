import {
  addDoc,
  collection,
  deleteDoc,
  deleteField,
  doc,
  getDoc,
  onSnapshot,
  orderBy,
  query,
  runTransaction,
  serverTimestamp,
  setDoc,
  updateDoc,
  where,
  type DocumentData,
} from 'firebase/firestore';
import { db } from '../firebase';
import {
  InventoryItem,
  ProductUnits,
  TicketPriority,
  TicketStatus,
  UserRole,
} from '../types/models';
import { handleFirestoreError, OperationType } from '../utils/firestoreErrors';

const INVENTORY_COLLECTION = 'inventory';
const PRODUCTS_COLLECTION = 'products';
const TICKETS_COLLECTION = 'tickets';

function mapInventoryItem(id: string, data: DocumentData): InventoryItem {
  return {
    id,
    name: String(data.name ?? ''),
    sku: typeof data.sku === 'string' ? data.sku : null,
    productId: typeof data.productId === 'string' ? data.productId : null,
    category: String(data.category ?? 'Phần cứng'),
    unit: String(data.unit ?? ProductUnits[0]),
    unitPrice: Number(data.unitPrice ?? 0),
    quantityOnHand: Number(data.quantityOnHand ?? 0),
    minThreshold: Number(data.minThreshold ?? 0),
    location: typeof data.location === 'string' ? data.location : null,
    note: typeof data.note === 'string' ? data.note : null,
    active: Boolean(data.active ?? true),
    updatedByUid: String(data.updatedByUid ?? ''),
    createdAt: data.createdAt,
    updatedAt: data.updatedAt,
  };
}

export function subscribeInventory(
  role: UserRole,
  onData: (items: InventoryItem[]) => void,
  onError?: (err: unknown) => void,
): () => void {
  const q =
    role === UserRole.TEAM_LEAD || role === UserRole.ADMIN
      ? query(collection(db, INVENTORY_COLLECTION), orderBy('name', 'asc'))
      : query(
          collection(db, INVENTORY_COLLECTION),
          where('active', '==', true),
          orderBy('name', 'asc'),
        );

  return onSnapshot(
    q,
    (snap) => {
      onData(snap.docs.map((d) => mapInventoryItem(d.id, d.data())));
    },
    (error) => {
      try {
        handleFirestoreError(error, OperationType.LIST, INVENTORY_COLLECTION);
      } catch (e) {
        onError?.(e);
      }
    },
  );
}

export async function upsertInventoryItem(params: {
  id?: string;
  name: string;
  sku?: string;
  category: string;
  unit: string;
  unitPrice: number;
  quantityOnHand: number;
  minThreshold: number;
  location?: string;
  note?: string;
  active: boolean;
  actorUid: string;
}): Promise<void> {
  const cleanName = params.name.trim();
  if (cleanName.length < 2 || cleanName.length > 120) {
    throw new Error('Tên vật tư phải từ 2 đến 120 ký tự.');
  }
  if (!Number.isFinite(params.unitPrice) || params.unitPrice < 0 || params.unitPrice > 1_000_000_000) {
    throw new Error('Đơn giá không hợp lệ.');
  }
  if (!Number.isInteger(params.quantityOnHand) || params.quantityOnHand < 0 || params.quantityOnHand > 1_000_000) {
    throw new Error('Số lượng tồn phải từ 0 đến 1.000.000.');
  }
  if (!Number.isInteger(params.minThreshold) || params.minThreshold < 0 || params.minThreshold > 100_000) {
    throw new Error('Ngưỡng cảnh báo tối thiểu không hợp lệ.');
  }

  const cleanSku = params.sku?.trim().slice(0, 40);
  const cleanLocation = params.location?.trim().slice(0, 80);
  const cleanNote = params.note?.trim().slice(0, 300);

  if (!params.id) {
    const payload: Record<string, unknown> = {
      name: cleanName,
      category: params.category.trim().slice(0, 60) || 'Phần cứng',
      unit: params.unit.trim().slice(0, 30) || ProductUnits[0],
      unitPrice: params.unitPrice,
      quantityOnHand: params.quantityOnHand,
      minThreshold: params.minThreshold,
      active: params.active,
      updatedByUid: params.actorUid,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    };
    if (cleanSku) payload.sku = cleanSku;
    if (cleanLocation) payload.location = cleanLocation;
    if (cleanNote) payload.note = cleanNote;
    try {
      await addDoc(collection(db, INVENTORY_COLLECTION), payload);
    } catch (error) {
      handleFirestoreError(error, OperationType.CREATE, INVENTORY_COLLECTION);
    }
  } else {
    const path = `${INVENTORY_COLLECTION}/${params.id}`;
    const updates: Record<string, unknown> = {
      name: cleanName,
      category: params.category.trim().slice(0, 60) || 'Phần cứng',
      unit: params.unit.trim().slice(0, 30) || ProductUnits[0],
      unitPrice: params.unitPrice,
      quantityOnHand: params.quantityOnHand,
      minThreshold: params.minThreshold,
      active: params.active,
      updatedByUid: params.actorUid,
      updatedAt: serverTimestamp(),
      sku: cleanSku ? cleanSku : deleteField(),
      location: cleanLocation ? cleanLocation : deleteField(),
      note: cleanNote ? cleanNote : deleteField(),
    };
    try {
      await updateDoc(doc(db, INVENTORY_COLLECTION, params.id), updates);
    } catch (error) {
      handleFirestoreError(error, OperationType.UPDATE, path);
    }
  }
}

export async function adjustInventoryStock(params: {
  inventoryId: string;
  delta: number;
  actorUid: string;
}): Promise<void> {
  const path = `${INVENTORY_COLLECTION}/${params.inventoryId}`;
  const ref = doc(db, INVENTORY_COLLECTION, params.inventoryId);
  try {
    await runTransaction(db, async (tx) => {
      const snap = await tx.get(ref);
      if (!snap.exists()) {
        throw new Error('Không tìm thấy vật tư trong kho.');
      }
      const current = Number(snap.data().quantityOnHand ?? 0);
      const next = current + params.delta;
      if (next < 0) {
        throw new Error('Số lượng tồn kho không thể nhỏ hơn 0.');
      }
      if (next > 1_000_000) {
        throw new Error('Số lượng tồn kho vượt quá giới hạn.');
      }
      tx.update(ref, {
        quantityOnHand: next,
        updatedByUid: params.actorUid,
        updatedAt: serverTimestamp(),
      });
    });
  } catch (error) {
    if (error instanceof Error && !error.message.toLowerCase().includes('permission')) {
      throw error;
    }
    handleFirestoreError(error, OperationType.UPDATE, path);
  }
}

export async function toggleInventoryActive(params: {
  inventoryId: string;
  active: boolean;
  actorUid: string;
}): Promise<void> {
  const path = `${INVENTORY_COLLECTION}/${params.inventoryId}`;
  try {
    await updateDoc(doc(db, INVENTORY_COLLECTION, params.inventoryId), {
      active: params.active,
      updatedByUid: params.actorUid,
      updatedAt: serverTimestamp(),
    });
  } catch (error) {
    handleFirestoreError(error, OperationType.UPDATE, path);
  }
}

export async function deleteInventoryItem(inventoryId: string): Promise<void> {
  const path = `${INVENTORY_COLLECTION}/${inventoryId}`;
  try {
    await deleteDoc(doc(db, INVENTORY_COLLECTION, inventoryId));
  } catch (error) {
    handleFirestoreError(error, OperationType.DELETE, path);
  }
}

export async function seedStarterDatabase(params: {
  actorUid: string;
  actorName: string;
  actorEmail: string;
}): Promise<{ createdCount: number }> {
  let createdCount = 0;

  const starterProducts = [
    {
      id: 'prod_starter_pump',
      name: 'Bơm áp lực ULKA EX5',
      sku: 'LK-PUMP-EX5',
      category: 'Phần cứng',
      unit: 'Cái',
      unitPrice: 450000,
      note: 'Linh kiện thay thế chuẩn cho máy pha cà phê văn phòng',
      active: true,
      createdByUid: params.actorUid,
    },
    {
      id: 'prod_starter_gasket',
      name: 'Gioăng cao su grouphead',
      sku: 'LK-GASK-58',
      category: 'Phần cứng',
      unit: 'Cái',
      unitPrice: 120000,
      note: 'Gioăng chịu nhiệt 58mm',
      active: true,
      createdByUid: params.actorUid,
    },
    {
      id: 'prod_starter_ssd',
      name: 'Ổ cứng SSD NVMe 512GB',
      sku: 'LK-SSD-512',
      category: 'Hệ thống máy chủ',
      unit: 'Cái',
      unitPrice: 1150000,
      note: 'Nâng cấp máy trạm / POS',
      active: true,
      createdByUid: params.actorUid,
    },
  ];

  for (const prod of starterProducts) {
    const ref = doc(db, PRODUCTS_COLLECTION, prod.id);
    const snap = await getDoc(ref);
    if (!snap.exists()) {
      await setDoc(ref, {
        name: prod.name,
        sku: prod.sku,
        category: prod.category,
        unit: prod.unit,
        unitPrice: prod.unitPrice,
        note: prod.note,
        active: prod.active,
        createdByUid: prod.createdByUid,
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      });
      createdCount += 1;
    }
  }

  const starterInventory = [
    {
      id: 'inv_starter_pump',
      name: 'Bơm áp lực ULKA EX5',
      sku: 'LK-PUMP-EX5',
      productId: 'prod_starter_pump',
      category: 'Phần cứng',
      unit: 'Cái',
      unitPrice: 450000,
      quantityOnHand: 8,
      minThreshold: 3,
      location: 'Kệ A1 - Kho kỹ thuật',
      note: 'Hàng mới nhập kho tuần này',
      active: true,
    },
    {
      id: 'inv_starter_gasket',
      name: 'Gioăng cao su grouphead',
      sku: 'LK-GASK-58',
      productId: 'prod_starter_gasket',
      category: 'Phần cứng',
      unit: 'Cái',
      unitPrice: 120000,
      quantityOnHand: 2,
      minThreshold: 5,
      location: 'Kệ A2 - Kho kỹ thuật',
      note: 'Cần nhập thêm trong tuần',
      active: true,
    },
    {
      id: 'inv_starter_ssd',
      name: 'Ổ cứng SSD NVMe 512GB',
      sku: 'LK-SSD-512',
      productId: 'prod_starter_ssd',
      category: 'Hệ thống máy chủ',
      unit: 'Cái',
      unitPrice: 1150000,
      quantityOnHand: 5,
      minThreshold: 2,
      location: 'Tủ B1 - Thiết bị IT',
      active: true,
    },
    {
      id: 'inv_starter_burr',
      name: 'Lưỡi dao máy xay 64mm',
      sku: 'LK-BURR-64',
      category: 'Thiết bị ngoại vi',
      unit: 'Bộ',
      unitPrice: 890000,
      quantityOnHand: 0,
      minThreshold: 2,
      location: 'Kệ A3 - Kho kỹ thuật',
      active: true,
    },
  ];

  for (const inv of starterInventory) {
    const ref = doc(db, INVENTORY_COLLECTION, inv.id);
    const snap = await getDoc(ref);
    if (!snap.exists()) {
      const payload: Record<string, unknown> = {
        name: inv.name,
        sku: inv.sku,
        category: inv.category,
        unit: inv.unit,
        unitPrice: inv.unitPrice,
        quantityOnHand: inv.quantityOnHand,
        minThreshold: inv.minThreshold,
        location: inv.location,
        active: inv.active,
        updatedByUid: params.actorUid,
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      };
      if (inv.productId) payload.productId = inv.productId;
      if (inv.note) payload.note = inv.note;
      await setDoc(ref, payload);
      createdCount += 1;
    }
  }

  const starterTickets = [
    {
      id: 'tk_starter_01',
      ticketCode: 'TK-1001',
      title: 'Máy pha cà phê khu A sụt áp suất khi chiết xuất',
      description: 'Đồng hồ áp suất chỉ đạt 4 bar, nước chảy yếu và có tiếng rung nhẹ ở cụm bơm.',
      category: 'Phần cứng',
      priority: TicketPriority.HIGH,
      status: TicketStatus.OPEN,
    },
    {
      id: 'tk_starter_02',
      ticketCode: 'TK-1002',
      title: 'Máy in hóa đơn quầy thu ngân mất kết nối LAN',
      description: 'Đèn báo mạng trên máy in nhấp nháy đỏ, phần mềm POS không gửi được lệnh in bill.',
      category: 'Mạng & Kết nối',
      priority: TicketPriority.URGENT,
      status: TicketStatus.OPEN,
    },
  ];

  for (const tk of starterTickets) {
    const ref = doc(db, TICKETS_COLLECTION, tk.id);
    const snap = await getDoc(ref);
    if (!snap.exists()) {
      await setDoc(ref, {
        ticketCode: tk.ticketCode,
        title: tk.title,
        description: tk.description,
        category: tk.category,
        priority: tk.priority,
        status: tk.status,
        creatorUid: params.actorUid,
        creatorName: params.actorName.slice(0, 100) || params.actorEmail,
        creatorEmail: params.actorEmail,
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      });
      createdCount += 1;
    }
  }

  return { createdCount };
}
