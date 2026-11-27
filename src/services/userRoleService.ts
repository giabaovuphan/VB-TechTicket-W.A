import type { User } from 'firebase/auth';
import {
  collection,
  deleteDoc,
  deleteField,
  doc,
  getDoc,
  onSnapshot,
  orderBy,
  query,
  serverTimestamp,
  setDoc,
  updateDoc,
  where,
  type DocumentData,
} from 'firebase/firestore';
import { db } from '../firebase';
import {
  AccountApprovalStatus,
  isRootAdminEmail,
  parseApprovalStatus,
  parseUserRole,
  RoleAssignment,
  UserAccount,
  UserRole,
} from '../types/models';
import { handleFirestoreError, OperationType } from '../utils/firestoreErrors';

const USERS_COLLECTION = 'users';
const ROLE_ASSIGNMENTS_COLLECTION = 'role_assignments';

function mapUserAccount(id: string, data: DocumentData): UserAccount {
  return {
    id,
    uid: String(data.uid ?? id),
    email: String(data.email ?? ''),
    displayName: String(data.displayName ?? ''),
    role: String(data.role ?? UserRole.PENDING),
    active: Boolean(data.active ?? false),
    approvalStatus: typeof data.approvalStatus === 'string' ? data.approvalStatus : undefined,
    rejectionReason: typeof data.rejectionReason === 'string' ? data.rejectionReason : null,
    approvedByUid: typeof data.approvedByUid === 'string' ? data.approvedByUid : null,
    department: typeof data.department === 'string' ? data.department : null,
    createdAt: data.createdAt,
    updatedAt: data.updatedAt,
  };
}

function mapRoleAssignment(id: string, data: DocumentData): RoleAssignment {
  return {
    id,
    email: String(data.email ?? id),
    displayName: String(data.displayName ?? ''),
    role: String(data.role ?? UserRole.USER),
    department: typeof data.department === 'string' ? data.department : null,
    assignedByUid: String(data.assignedByUid ?? ''),
    createdAt: data.createdAt,
    updatedAt: data.updatedAt,
  };
}

export function subscribeCurrentUserProfile(
  uid: string,
  onData: (account: UserAccount | null) => void,
  onError?: (err: unknown) => void,
): () => void {
  const path = `${USERS_COLLECTION}/${uid}`;
  return onSnapshot(
    doc(db, USERS_COLLECTION, uid),
    (snap) => {
      if (!snap.exists()) {
        onData(null);
        return;
      }
      onData(mapUserAccount(snap.id, snap.data()));
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

export function subscribeAllUsers(
  onData: (users: UserAccount[]) => void,
  onError?: (err: unknown) => void,
): () => void {
  const q = query(collection(db, USERS_COLLECTION), orderBy('displayName', 'asc'));
  return onSnapshot(
    q,
    (snap) => {
      onData(snap.docs.map((d) => mapUserAccount(d.id, d.data())));
    },
    (error) => {
      try {
        handleFirestoreError(error, OperationType.LIST, USERS_COLLECTION);
      } catch (e) {
        onError?.(e);
      }
    },
  );
}

export function subscribeTechnicians(
  onData: (technicians: UserAccount[]) => void,
  onError?: (err: unknown) => void,
): () => void {
  const q = query(collection(db, USERS_COLLECTION), where('active', '==', true));
  return onSnapshot(
    q,
    (snap) => {
      const staff = snap.docs
        .map((d) => mapUserAccount(d.id, d.data()))
        .filter((u) => {
          const role = parseUserRole(u.role);
          const approval = parseApprovalStatus(u.approvalStatus, role);
          return (
            approval === AccountApprovalStatus.APPROVED &&
            (role === UserRole.TECHNICIAN || role === UserRole.TEAM_LEAD)
          );
        })
        .sort((a, b) => a.displayName.localeCompare(b.displayName, 'vi'));
      onData(staff);
    },
    (error) => {
      try {
        handleFirestoreError(error, OperationType.LIST, USERS_COLLECTION);
      } catch (e) {
        onError?.(e);
      }
    },
  );
}

export function subscribeRoleAssignments(
  onData: (assignments: RoleAssignment[]) => void,
  onError?: (err: unknown) => void,
): () => void {
  const q = query(collection(db, ROLE_ASSIGNMENTS_COLLECTION), orderBy('email', 'asc'));
  return onSnapshot(
    q,
    (snap) => {
      onData(snap.docs.map((d) => mapRoleAssignment(d.id, d.data())));
    },
    (error) => {
      try {
        handleFirestoreError(error, OperationType.LIST, ROLE_ASSIGNMENTS_COLLECTION);
      } catch (e) {
        onError?.(e);
      }
    },
  );
}

export async function ensureUserProfileAndSyncRole(firebaseUser: User): Promise<UserAccount> {
  const uid = firebaseUser.uid;
  const email = (firebaseUser.email ?? '').trim().toLowerCase();
  const displayName = (firebaseUser.displayName ?? email.split('@')[0] ?? 'Người dùng HCN').trim().slice(0, 100);
  const emailVerified = Boolean(firebaseUser.emailVerified);
  const userRef = doc(db, USERS_COLLECTION, uid);
  const userPath = `${USERS_COLLECTION}/${uid}`;

  let assignment: RoleAssignment | null = null;
  if (email && emailVerified) {
    try {
      const assignmentSnap = await getDoc(doc(db, ROLE_ASSIGNMENTS_COLLECTION, email));
      if (assignmentSnap.exists()) {
        assignment = mapRoleAssignment(assignmentSnap.id, assignmentSnap.data());
      }
    } catch {
      assignment = null;
    }
  }

  let existingSnap;
  try {
    existingSnap = await getDoc(userRef);
  } catch (error) {
    handleFirestoreError(error, OperationType.GET, userPath);
  }

  const isRootAdmin = emailVerified && isRootAdminEmail(email);

  if (!existingSnap.exists()) {
    if (isRootAdmin) {
      const payload = {
        uid,
        email,
        displayName,
        role: UserRole.ADMIN,
        active: true,
        approvalStatus: AccountApprovalStatus.APPROVED,
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      };
      try {
        await setDoc(userRef, payload);
      } catch (error) {
        handleFirestoreError(error, OperationType.CREATE, userPath);
      }
      return {
        id: uid,
        uid,
        email,
        displayName,
        role: UserRole.ADMIN,
        active: true,
        approvalStatus: AccountApprovalStatus.APPROVED,
      };
    }

    if (assignment) {
      const assignedRole = parseUserRole(assignment.role);
      const payload: Record<string, unknown> = {
        uid,
        email,
        displayName: assignment.displayName.trim() || displayName,
        role: assignedRole === UserRole.PENDING ? UserRole.USER : assignedRole,
        active: true,
        approvalStatus: AccountApprovalStatus.APPROVED,
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      };
      if (assignment.department?.trim()) {
        payload.department = assignment.department.trim().slice(0, 80);
      }
      try {
        await setDoc(userRef, payload);
      } catch (error) {
        handleFirestoreError(error, OperationType.CREATE, userPath);
      }
      return {
        id: uid,
        uid,
        email,
        displayName: String(payload.displayName),
        role: String(payload.role),
        active: true,
        approvalStatus: AccountApprovalStatus.APPROVED,
        department: assignment.department ?? null,
      };
    }

    const pendingPayload = {
      uid,
      email,
      displayName,
      role: UserRole.PENDING,
      active: false,
      approvalStatus: AccountApprovalStatus.PENDING,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    };
    try {
      await setDoc(userRef, pendingPayload);
    } catch (error) {
      handleFirestoreError(error, OperationType.CREATE, userPath);
    }
    return {
      id: uid,
      uid,
      email,
      displayName,
      role: UserRole.PENDING,
      active: false,
      approvalStatus: AccountApprovalStatus.PENDING,
    };
  }

  const current = mapUserAccount(existingSnap.id, existingSnap.data());
  const currentRole = parseUserRole(current.role);
  const currentApproval = parseApprovalStatus(current.approvalStatus, currentRole);

  if (isRootAdmin && (currentRole !== UserRole.ADMIN || !current.active || currentApproval !== AccountApprovalStatus.APPROVED)) {
    try {
      await updateDoc(userRef, {
        role: UserRole.ADMIN,
        active: true,
        approvalStatus: AccountApprovalStatus.APPROVED,
        displayName,
        updatedAt: serverTimestamp(),
      });
    } catch (error) {
      handleFirestoreError(error, OperationType.UPDATE, userPath);
    }
    return {
      ...current,
      role: UserRole.ADMIN,
      active: true,
      approvalStatus: AccountApprovalStatus.APPROVED,
      displayName,
    };
  }

  if (
    assignment &&
    currentApproval !== AccountApprovalStatus.REJECTED &&
    (currentRole !== parseUserRole(assignment.role) ||
      !current.active ||
      currentApproval !== AccountApprovalStatus.APPROVED)
  ) {
    const nextRole = parseUserRole(assignment.role);
    const updates: Record<string, unknown> = {
      role: nextRole,
      active: true,
      approvalStatus: AccountApprovalStatus.APPROVED,
      displayName: assignment.displayName.trim() || current.displayName || displayName,
      updatedAt: serverTimestamp(),
    };
    if (assignment.department?.trim()) {
      updates.department = assignment.department.trim().slice(0, 80);
    }
    try {
      await updateDoc(userRef, updates);
    } catch (error) {
      handleFirestoreError(error, OperationType.UPDATE, userPath);
    }
    return {
      ...current,
      role: nextRole,
      active: true,
      approvalStatus: AccountApprovalStatus.APPROVED,
      displayName: String(updates.displayName),
      department: assignment.department ?? current.department,
    };
  }

  return current;
}

export async function assignRoleByEmail(params: {
  email: string;
  displayName: string;
  role: UserRole;
  department?: string;
  assignedByUid: string;
}): Promise<void> {
  const emailLower = params.email.trim().toLowerCase();
  if (!emailLower || !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(emailLower)) {
    throw new Error('Email Google không hợp lệ.');
  }
  if (isRootAdminEmail(emailLower)) {
    throw new Error('Không thể thay đổi quyền của tài khoản Quản trị viên gốc.');
  }
  if (params.role === UserRole.PENDING) {
    throw new Error('Vui lòng chọn vai trò hợp lệ.');
  }
  const cleanName = params.displayName.trim();
  if (!cleanName || cleanName.length > 100) {
    throw new Error('Tên hiển thị phải từ 1 đến 100 ký tự.');
  }

  const ref = doc(db, ROLE_ASSIGNMENTS_COLLECTION, emailLower);
  const path = `${ROLE_ASSIGNMENTS_COLLECTION}/${emailLower}`;

  try {
    const existing = await getDoc(ref);
    const cleanDept = params.department?.trim().slice(0, 80);
    if (!existing.exists()) {
      const payload: Record<string, unknown> = {
        email: emailLower,
        displayName: cleanName,
        role: params.role,
        assignedByUid: params.assignedByUid,
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      };
      if (cleanDept) {
        payload.department = cleanDept;
      }
      await setDoc(ref, payload);
    } else {
      const updates: Record<string, unknown> = {
        displayName: cleanName,
        role: params.role,
        assignedByUid: params.assignedByUid,
        updatedAt: serverTimestamp(),
      };
      if (cleanDept) {
        updates.department = cleanDept;
      } else {
        updates.department = deleteField();
      }
      await updateDoc(ref, updates);
    }
  } catch (error) {
    handleFirestoreError(error, OperationType.WRITE, path);
  }
}

export async function approvePendingUser(params: {
  uid: string;
  role: UserRole;
  department?: string;
  approvedByUid: string;
}): Promise<void> {
  if (params.role === UserRole.PENDING) {
    throw new Error('Vui lòng chọn vai trò hợp lệ để kích hoạt.');
  }
  const ref = doc(db, USERS_COLLECTION, params.uid);
  const path = `${USERS_COLLECTION}/${params.uid}`;
  const cleanDept = params.department?.trim().slice(0, 80);
  const updates: Record<string, unknown> = {
    role: params.role,
    active: true,
    approvalStatus: AccountApprovalStatus.APPROVED,
    approvedByUid: params.approvedByUid,
    rejectionReason: deleteField(),
    updatedAt: serverTimestamp(),
  };
  if (cleanDept) {
    updates.department = cleanDept;
  }
  try {
    await updateDoc(ref, updates);
  } catch (error) {
    handleFirestoreError(error, OperationType.UPDATE, path);
  }
}

export async function rejectPendingUser(params: {
  uid: string;
  reason?: string;
  rejectedByUid: string;
}): Promise<void> {
  const ref = doc(db, USERS_COLLECTION, params.uid);
  const path = `${USERS_COLLECTION}/${params.uid}`;
  const cleanReason = params.reason?.trim().slice(0, 300);
  const updates: Record<string, unknown> = {
    role: UserRole.PENDING,
    active: false,
    approvalStatus: AccountApprovalStatus.REJECTED,
    approvedByUid: params.rejectedByUid,
    updatedAt: serverTimestamp(),
  };
  if (cleanReason) {
    updates.rejectionReason = cleanReason;
  } else {
    updates.rejectionReason = deleteField();
  }
  try {
    await updateDoc(ref, updates);
  } catch (error) {
    handleFirestoreError(error, OperationType.UPDATE, path);
  }
}

export async function updateUserRoleAndActive(params: {
  uid: string;
  role: UserRole;
  active: boolean;
  department?: string;
  actorUid: string;
}): Promise<void> {
  const ref = doc(db, USERS_COLLECTION, params.uid);
  const path = `${USERS_COLLECTION}/${params.uid}`;
  const cleanDept = params.department?.trim().slice(0, 80);
  const updates: Record<string, unknown> = {
    role: params.role,
    active: params.role === UserRole.PENDING ? false : params.active,
    approvalStatus:
      params.role === UserRole.PENDING ? AccountApprovalStatus.PENDING : AccountApprovalStatus.APPROVED,
    approvedByUid: params.actorUid,
    rejectionReason: deleteField(),
    updatedAt: serverTimestamp(),
  };
  if (cleanDept) {
    updates.department = cleanDept;
  } else {
    updates.department = deleteField();
  }
  try {
    await updateDoc(ref, updates);
  } catch (error) {
    handleFirestoreError(error, OperationType.UPDATE, path);
  }
}

export async function removeRoleAssignment(emailId: string): Promise<void> {
  const cleanEmail = emailId.trim().toLowerCase();
  if (isRootAdminEmail(cleanEmail)) {
    throw new Error('Không thể xóa quyền Quản trị viên gốc.');
  }
  const path = `${ROLE_ASSIGNMENTS_COLLECTION}/${cleanEmail}`;
  try {
    await deleteDoc(doc(db, ROLE_ASSIGNMENTS_COLLECTION, cleanEmail));
  } catch (error) {
    handleFirestoreError(error, OperationType.DELETE, path);
  }
}
