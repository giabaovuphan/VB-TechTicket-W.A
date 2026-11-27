import { auth } from '../firebase';

export enum OperationType {
  CREATE = 'create',
  UPDATE = 'update',
  DELETE = 'delete',
  LIST = 'list',
  GET = 'get',
  WRITE = 'write',
}

export interface FirestoreErrorInfo {
  error: string;
  operationType: OperationType;
  path: string | null;
  authInfo: {
    userId?: string | null;
    email?: string | null;
    emailVerified?: boolean | null;
    isAnonymous?: boolean | null;
    tenantId?: string | null;
    providerInfo?: Array<{
      providerId?: string | null;
      email?: string | null;
    }>;
  };
}

export function buildFirestoreErrorInfo(
  error: unknown,
  operationType: OperationType,
  path: string | null,
): FirestoreErrorInfo {
  const user = auth.currentUser;
  return {
    error: error instanceof Error ? error.message : String(error),
    operationType,
    path,
    authInfo: {
      userId: user?.uid ?? null,
      email: user?.email ?? null,
      emailVerified: user?.emailVerified ?? null,
      isAnonymous: user?.isAnonymous ?? null,
      tenantId: user?.tenantId ?? null,
      providerInfo:
        user?.providerData?.map((provider) => ({
          providerId: provider.providerId,
          email: provider.email,
        })) ?? [],
    },
  };
}

export function handleFirestoreError(
  error: unknown,
  operationType: OperationType,
  path: string | null,
): never {
  const errInfo = buildFirestoreErrorInfo(error, operationType, path);
  const json = JSON.stringify(errInfo);
  console.error('Firestore Error:', json);
  throw new Error(json);
}

export function toFriendlyVietnameseError(error: unknown, fallback: string): string {
  const rawMessage = error instanceof Error ? error.message : String(error ?? '');
  let extracted = rawMessage;
  if (rawMessage.startsWith('{') && rawMessage.includes('"error"')) {
    try {
      const parsed = JSON.parse(rawMessage) as { error?: string };
      if (parsed.error) extracted = parsed.error;
    } catch {
      // keep raw
    }
  }
  const lower = extracted.toLowerCase();
  if (lower.includes('permission-denied') || lower.includes('missing or insufficient permissions')) {
    return 'Bạn không có quyền thực hiện thao tác này hoặc dữ liệu không hợp lệ.';
  }
  if (lower.includes('unavailable') || lower.includes('offline') || lower.includes('network')) {
    return 'Không thể kết nối máy chủ. Vui lòng kiểm tra mạng và thử lại.';
  }
  if (lower.includes('not-found')) {
    return 'Không tìm thấy dữ liệu yêu cầu.';
  }
  if (lower.includes('quota exceeded')) {
    return 'Hệ thống đã chạm giới hạn sử dụng Firestore trong ngày (Quota exceeded).';
  }
  return extracted.trim() || fallback;
}
