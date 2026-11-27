export const ROOT_ADMIN_EMAIL = 'giabao.vuphan@gmail.com';

export enum UserRole {
  PENDING = 'PENDING',
  USER = 'USER',
  TECHNICIAN = 'TECHNICIAN',
  TEAM_LEAD = 'TEAM_LEAD',
  ADMIN = 'ADMIN',
}

export const USER_ROLE_LABELS: Record<UserRole, { labelVi: string; shortLabelVi: string }> = {
  [UserRole.PENDING]: { labelVi: 'Chờ cấp quyền', shortLabelVi: 'Chờ duyệt' },
  [UserRole.USER]: { labelVi: 'Người dùng', shortLabelVi: 'Người dùng' },
  [UserRole.TECHNICIAN]: { labelVi: 'Nhân viên kỹ thuật', shortLabelVi: 'Kỹ thuật viên' },
  [UserRole.TEAM_LEAD]: { labelVi: 'Trưởng nhóm kỹ thuật', shortLabelVi: 'Trưởng nhóm' },
  [UserRole.ADMIN]: { labelVi: 'Quản trị viên', shortLabelVi: 'Quản trị viên' },
};

export function parseUserRole(raw?: string | null): UserRole {
  if (raw && Object.values(UserRole).includes(raw as UserRole)) {
    return raw as UserRole;
  }
  return UserRole.PENDING;
}

export function getAssignableRoles(isRootAdmin: boolean): UserRole[] {
  return isRootAdmin
    ? [UserRole.USER, UserRole.TECHNICIAN, UserRole.TEAM_LEAD, UserRole.ADMIN]
    : [UserRole.USER, UserRole.TECHNICIAN, UserRole.TEAM_LEAD];
}

export enum AccountApprovalStatus {
  PENDING = 'PENDING',
  APPROVED = 'APPROVED',
  REJECTED = 'REJECTED',
}

export const APPROVAL_STATUS_LABELS: Record<AccountApprovalStatus, string> = {
  [AccountApprovalStatus.PENDING]: 'Chờ cấp quyền',
  [AccountApprovalStatus.APPROVED]: 'Đã kích hoạt',
  [AccountApprovalStatus.REJECTED]: 'Đã từ chối',
};

export function parseApprovalStatus(
  raw?: string | null,
  currentRole: UserRole = UserRole.PENDING,
): AccountApprovalStatus {
  if (raw === AccountApprovalStatus.REJECTED) return AccountApprovalStatus.REJECTED;
  if (raw === AccountApprovalStatus.APPROVED) return AccountApprovalStatus.APPROVED;
  if (raw === AccountApprovalStatus.PENDING) return AccountApprovalStatus.PENDING;
  return currentRole === UserRole.PENDING
    ? AccountApprovalStatus.PENDING
    : AccountApprovalStatus.APPROVED;
}

export enum TicketPriority {
  LOW = 'LOW',
  MEDIUM = 'MEDIUM',
  HIGH = 'HIGH',
  URGENT = 'URGENT',
}

export const TICKET_PRIORITY_META: Record<TicketPriority, { labelVi: string; sortOrder: number }> = {
  [TicketPriority.LOW]: { labelVi: 'Thấp', sortOrder: 1 },
  [TicketPriority.MEDIUM]: { labelVi: 'Trung bình', sortOrder: 2 },
  [TicketPriority.HIGH]: { labelVi: 'Cao', sortOrder: 3 },
  [TicketPriority.URGENT]: { labelVi: 'Khẩn cấp', sortOrder: 4 },
};

export function parseTicketPriority(raw?: string | null): TicketPriority {
  if (raw && Object.values(TicketPriority).includes(raw as TicketPriority)) {
    return raw as TicketPriority;
  }
  return TicketPriority.MEDIUM;
}

export enum TicketStatus {
  OPEN = 'OPEN',
  ACCEPTED = 'ACCEPTED',
  IN_PROGRESS = 'IN_PROGRESS',
  COMPLETED = 'COMPLETED',
}

export const TICKET_STATUS_META: Record<TicketStatus, { labelVi: string; shortLabelVi: string; stepIndex: number }> = {
  [TicketStatus.OPEN]: { labelVi: 'Mở', shortLabelVi: 'Mở', stepIndex: 0 },
  [TicketStatus.ACCEPTED]: { labelVi: 'Đã tiếp nhận', shortLabelVi: 'Tiếp nhận', stepIndex: 1 },
  [TicketStatus.IN_PROGRESS]: { labelVi: 'Đang xử lý', shortLabelVi: 'Đang xử lý', stepIndex: 2 },
  [TicketStatus.COMPLETED]: { labelVi: 'Đã hoàn thành', shortLabelVi: 'Hoàn thành', stepIndex: 3 },
};

export function parseTicketStatus(raw?: string | null): TicketStatus {
  if (raw && Object.values(TicketStatus).includes(raw as TicketStatus)) {
    return raw as TicketStatus;
  }
  return TicketStatus.OPEN;
}

export enum InvoicePaymentStatus {
  UNPAID = 'UNPAID',
  PAID = 'PAID',
}

export const INVOICE_PAYMENT_STATUS_LABELS: Record<InvoicePaymentStatus, string> = {
  [InvoicePaymentStatus.UNPAID]: 'Chưa thanh toán',
  [InvoicePaymentStatus.PAID]: 'Đã thanh toán',
};

export function parseInvoicePaymentStatus(raw?: string | null): InvoicePaymentStatus {
  return raw === InvoicePaymentStatus.PAID
    ? InvoicePaymentStatus.PAID
    : InvoicePaymentStatus.UNPAID;
}

export enum InvoicePaymentMethod {
  CASH = 'CASH',
  TRANSFER = 'TRANSFER',
  INTERNAL = 'INTERNAL',
}

export const INVOICE_PAYMENT_METHOD_LABELS: Record<InvoicePaymentMethod, string> = {
  [InvoicePaymentMethod.CASH]: 'Tiền mặt',
  [InvoicePaymentMethod.TRANSFER]: 'Chuyển khoản',
  [InvoicePaymentMethod.INTERNAL]: 'Nội bộ / Bảo hành',
};

export function parseInvoicePaymentMethod(raw?: string | null): InvoicePaymentMethod {
  if (raw && Object.values(InvoicePaymentMethod).includes(raw as InvoicePaymentMethod)) {
    return raw as InvoicePaymentMethod;
  }
  return InvoicePaymentMethod.CASH;
}

export enum StockAlertLevel {
  OK = 'OK',
  LOW = 'LOW',
  OUT = 'OUT',
}

export const STOCK_ALERT_LABELS: Record<StockAlertLevel, string> = {
  [StockAlertLevel.OK]: 'Đủ hàng',
  [StockAlertLevel.LOW]: 'Sắp hết',
  [StockAlertLevel.OUT]: 'Hết hàng',
};

export const TicketCategories = [
  'Phần cứng',
  'Phần mềm',
  'Mạng & Kết nối',
  'Thiết bị ngoại vi',
  'Hệ thống máy chủ',
  'Khác',
] as const;

export const ProductUnits = ['Cái', 'Bộ', 'Thanh', 'Mét', 'Hộp', 'Lần'] as const;

export interface FirestoreTimestampLike {
  toDate: () => Date;
  toMillis: () => number;
  seconds: number;
  nanoseconds: number;
}

export type TimestampValue = FirestoreTimestampLike | Date | number | null | undefined;

export function timestampToMillis(ts: TimestampValue): number | null {
  if (ts == null) return null;
  if (typeof ts === 'number') return Number.isFinite(ts) ? ts : null;
  if (ts instanceof Date) return ts.getTime();
  if (typeof ts === 'object') {
    if (typeof ts.toMillis === 'function') return ts.toMillis();
    if (typeof ts.toDate === 'function') return ts.toDate().getTime();
    if (typeof ts.seconds === 'number') return ts.seconds * 1000;
  }
  return null;
}

export function formatTimestampVi(ts: TimestampValue): string {
  const ms = timestampToMillis(ts);
  const date = ms != null ? new Date(ms) : new Date();
  const dd = String(date.getDate()).padStart(2, '0');
  const mm = String(date.getMonth() + 1).padStart(2, '0');
  const yyyy = date.getFullYear();
  const hh = String(date.getHours()).padStart(2, '0');
  const min = String(date.getMinutes()).padStart(2, '0');
  return `${dd}/${mm}/${yyyy} ${hh}:${min}`;
}

export function formatVnd(amount: number): string {
  const safe = Number.isFinite(amount) ? Math.round(amount) : 0;
  return `${new Intl.NumberFormat('vi-VN').format(safe)} đ`;
}

export interface UserAccount {
  id: string;
  uid: string;
  email: string;
  displayName: string;
  role: string;
  active: boolean;
  approvalStatus?: string;
  rejectionReason?: string | null;
  approvedByUid?: string | null;
  department?: string | null;
  createdAt?: TimestampValue;
  updatedAt?: TimestampValue;
}

export function isRootAdminEmail(email?: string | null): boolean {
  return (email ?? '').trim().toLowerCase() === ROOT_ADMIN_EMAIL;
}

export function isApprovedAndActive(account: UserAccount): boolean {
  const role = parseUserRole(account.role);
  const approval = parseApprovalStatus(account.approvalStatus, role);
  return account.active === true && role !== UserRole.PENDING && approval === AccountApprovalStatus.APPROVED;
}

export interface RoleAssignment {
  id: string;
  email: string;
  displayName: string;
  role: string;
  department?: string | null;
  assignedByUid: string;
  createdAt?: TimestampValue;
  updatedAt?: TimestampValue;
}

export interface SupportTicket {
  id: string;
  ticketCode: string;
  title: string;
  description: string;
  category: string;
  priority: string;
  status: string;
  creatorUid: string;
  creatorName: string;
  creatorEmail: string;
  assigneeUid?: string | null;
  assigneeName?: string | null;
  teamLeadUid?: string | null;
  defectPhotoBase64?: string | null;
  resolvedPhotoBase64?: string | null;
  resolutionNote?: string | null;
  createdAt?: TimestampValue;
  updatedAt?: TimestampValue;
  completedAt?: TimestampValue;
}

export interface TicketNotification {
  id: string;
  recipientUid: string;
  senderUid: string;
  ticketId: string;
  ticketCode: string;
  title: string;
  message: string;
  newStatus: string;
  readFlag: boolean;
  createdAt?: TimestampValue;
}

export interface ProductItem {
  id: string;
  name: string;
  sku?: string | null;
  category: string;
  unit: string;
  unitPrice: number;
  note?: string | null;
  active: boolean;
  createdByUid: string;
  createdAt?: TimestampValue;
  updatedAt?: TimestampValue;
}

export interface InventoryItem {
  id: string;
  name: string;
  sku?: string | null;
  productId?: string | null;
  category: string;
  unit: string;
  unitPrice: number;
  quantityOnHand: number;
  minThreshold: number;
  location?: string | null;
  note?: string | null;
  active: boolean;
  updatedByUid: string;
  createdAt?: TimestampValue;
  updatedAt?: TimestampValue;
}

export function getAlertLevel(item: Pick<InventoryItem, 'quantityOnHand' | 'minThreshold'>): StockAlertLevel {
  if (item.quantityOnHand <= 0) return StockAlertLevel.OUT;
  if (item.quantityOnHand <= item.minThreshold) return StockAlertLevel.LOW;
  return StockAlertLevel.OK;
}

export interface InvoiceLineItem {
  productId?: string | null;
  inventoryId?: string | null;
  name: string;
  unit: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
  customEntry: boolean;
}

export interface RepairInvoice {
  id: string;
  invoiceCode: string;
  ticketId: string;
  ticketCode: string;
  creatorUid: string;
  assigneeUid?: string | null;
  items: InvoiceLineItem[];
  partsSubtotal: number;
  laborFee: number;
  totalAmount: number;
  paymentStatus: string;
  paymentMethod: string;
  note?: string | null;
  updatedByUid: string;
  updatedByName: string;
  createdAt?: TimestampValue;
  updatedAt?: TimestampValue;
  paidAt?: TimestampValue;
}

export type UiState<T> =
  | { status: 'loading' }
  | { status: 'success'; data: T }
  | { status: 'error'; message: string };

export interface ActionFeedback {
  message: string;
  isError: boolean;
}
