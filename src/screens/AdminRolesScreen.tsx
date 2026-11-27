import React, { useEffect, useMemo, useState } from 'react';
import {
  Check,
  Database,
  Edit2,
  Loader2,
  MailPlus,
  ShieldCheck,
  Trash2,
  UserCheck,
  UserX,
  X,
} from 'lucide-react';
import { RoleBadge } from '../components/Badges';
import { useToast } from '../context/ToastContext';
import { seedStarterDatabase } from '../services/inventoryService';
import {
  approvePendingUser,
  assignRoleByEmail,
  rejectPendingUser,
  removeRoleAssignment,
  subscribeAllUsers,
  subscribeRoleAssignments,
  updateUserRoleAndActive,
} from '../services/userRoleService';
import {
  AccountApprovalStatus,
  getAssignableRoles,
  isRootAdminEmail,
  parseApprovalStatus,
  parseUserRole,
  RoleAssignment,
  USER_ROLE_LABELS,
  UserAccount,
  UserRole,
} from '../types/models';
import { toFriendlyVietnameseError } from '../utils/firestoreErrors';

export const AdminRolesScreen: React.FC<{
  currentUser: UserAccount;
  isRootAdmin: boolean;
}> = ({ currentUser, isRootAdmin }) => {
  const { showToast } = useToast();
  const [users, setUsers] = useState<UserAccount[]>([]);
  const [assignments, setAssignments] = useState<RoleAssignment[]>([]);
  const [loading, setLoading] = useState(true);
  const [seeding, setSeeding] = useState(false);

  // Approve / Re-approve Modal
  const [approveTarget, setApproveTarget] = useState<UserAccount | null>(null);
  const [approveRole, setApproveRole] = useState<UserRole>(UserRole.USER);
  const [approveDept, setApproveDept] = useState('');

  // Reject Modal
  const [rejectTarget, setRejectTarget] = useState<UserAccount | null>(null);
  const [rejectReason, setRejectReason] = useState('');

  // Edit User Modal
  const [editTarget, setEditTarget] = useState<UserAccount | null>(null);
  const [editRole, setEditRole] = useState<UserRole>(UserRole.USER);
  const [editDept, setEditDept] = useState('');
  const [editActive, setEditActive] = useState(true);

  // Assign Role by Email Modal
  const [assignModalOpen, setAssignModalOpen] = useState(false);
  const [assignEmail, setAssignEmail] = useState('');
  const [assignName, setAssignName] = useState('');
  const [assignRole, setAssignRole] = useState<UserRole>(UserRole.TECHNICIAN);
  const [assignDept, setAssignDept] = useState('');

  const [busy, setBusy] = useState(false);
  const assignableRoles = useMemo(() => getAssignableRoles(isRootAdmin), [isRootAdmin]);

  useEffect(() => {
    setLoading(true);
    const unsubUsers = subscribeAllUsers(
      (list) => {
        setUsers(list);
        setLoading(false);
      },
      (err) => {
        showToast(toFriendlyVietnameseError(err, 'Không thể tải danh sách tài khoản.'), true);
        setLoading(false);
      },
    );
    const unsubAssignments = subscribeRoleAssignments(
      (list) => setAssignments(list),
      () => setAssignments([]),
    );
    return () => {
      unsubUsers();
      unsubAssignments();
    };
  }, [showToast]);

  const { pendingUsers, approvedUsers, rejectedUsers } = useMemo(() => {
    const pending: UserAccount[] = [];
    const approved: UserAccount[] = [];
    const rejected: UserAccount[] = [];
    for (const u of users) {
      const role = parseUserRole(u.role);
      const approval = parseApprovalStatus(u.approvalStatus, role);
      if (approval === AccountApprovalStatus.REJECTED) {
        rejected.push(u);
      } else if (role === UserRole.PENDING || approval === AccountApprovalStatus.PENDING) {
        pending.push(u);
      } else {
        approved.push(u);
      }
    }
    return { pendingUsers: pending, approvedUsers: approved, rejectedUsers: rejected };
  }, [users]);

  const handleSeedStarter = async () => {
    setSeeding(true);
    try {
      const { createdCount } = await seedStarterDatabase({
        actorUid: currentUser.uid,
        actorName: currentUser.displayName,
        actorEmail: currentUser.email,
      });
      showToast(
        createdCount > 0
          ? `Đã khởi tạo ${createdCount} mục dữ liệu mẫu (sản phẩm, kho, ticket).`
          : 'Dữ liệu mẫu đã tồn tại sẵn trong Firestore.',
      );
    } catch (err) {
      showToast(toFriendlyVietnameseError(err, 'Không thể tạo dữ liệu mẫu.'), true);
    } finally {
      setSeeding(false);
    }
  };

  const handleConfirmApprove = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!approveTarget) return;
    setBusy(true);
    try {
      await approvePendingUser({
        uid: approveTarget.uid,
        role: approveRole,
        department: approveDept,
        approvedByUid: currentUser.uid,
      });
      showToast(`Đã cấp quyền ${USER_ROLE_LABELS[approveRole].labelVi} cho ${approveTarget.displayName}.`);
      setApproveTarget(null);
    } catch (err) {
      showToast(toFriendlyVietnameseError(err, 'Không thể phê duyệt tài khoản.'), true);
    } finally {
      setBusy(false);
    }
  };

  const handleConfirmReject = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!rejectTarget) return;
    setBusy(true);
    try {
      await rejectPendingUser({
        uid: rejectTarget.uid,
        reason: rejectReason,
        rejectedByUid: currentUser.uid,
      });
      showToast(`Đã từ chối tài khoản ${rejectTarget.displayName}.`);
      setRejectTarget(null);
      setRejectReason('');
    } catch (err) {
      showToast(toFriendlyVietnameseError(err, 'Không thể từ chối tài khoản.'), true);
    } finally {
      setBusy(false);
    }
  };

  const handleConfirmEditUser = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!editTarget) return;
    setBusy(true);
    try {
      await updateUserRoleAndActive({
        uid: editTarget.uid,
        role: editRole,
        active: editActive,
        department: editDept,
        actorUid: currentUser.uid,
      });
      showToast(`Đã cập nhật quyền cho ${editTarget.displayName}.`);
      setEditTarget(null);
    } catch (err) {
      showToast(toFriendlyVietnameseError(err, 'Không thể cập nhật tài khoản.'), true);
    } finally {
      setBusy(false);
    }
  };

  const handleAssignByEmail = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    try {
      await assignRoleByEmail({
        email: assignEmail,
        displayName: assignName,
        role: assignRole,
        department: assignDept,
        assignedByUid: currentUser.uid,
      });
      showToast(`Đã gán sẵn quyền cho ${assignEmail.trim().toLowerCase()}.`);
      setAssignModalOpen(false);
      setAssignEmail('');
      setAssignName('');
      setAssignDept('');
    } catch (err) {
      showToast(toFriendlyVietnameseError(err, 'Không thể gán quyền qua email.'), true);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="space-y-4">
      {/* Top Admin Utilities */}
      <div className="flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 p-4">
        <div>
          <h2 className="text-sm font-bold text-zinc-900 dark:text-zinc-100">
            Quản lý phân quyền &amp; người dùng
          </h2>
          <p className="text-xs text-zinc-500 dark:text-zinc-400">
            Phê duyệt tài khoản Google mới, phân công vai trò hoặc gán sẵn quyền theo email.
          </p>
        </div>

        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            disabled={seeding}
            onClick={() => void handleSeedStarter()}
            className="inline-flex h-10 items-center gap-1.5 rounded-xl border border-zinc-200 dark:border-zinc-700 bg-white dark:bg-zinc-800 hover:bg-zinc-50 dark:hover:bg-zinc-700 px-3.5 text-xs font-semibold text-zinc-800 dark:text-zinc-100 transition-colors cursor-pointer"
          >
            {seeding ? (
              <Loader2 className="h-4 w-4 animate-spin" />
            ) : (
              <Database className="h-4 w-4 text-orange-600 dark:text-orange-400" />
            )}
            Tạo dữ liệu mẫu
          </button>

          <button
            type="button"
            onClick={() => setAssignModalOpen(true)}
            className="inline-flex h-10 items-center gap-1.5 rounded-xl bg-orange-600 hover:bg-orange-700 px-4 text-xs font-semibold text-white transition-colors cursor-pointer"
          >
            <MailPlus className="h-4 w-4" />
            Gán quyền trước theo Email
          </button>
        </div>
      </div>

      {loading ? (
        <div className="flex min-h-[200px] items-center justify-center rounded-2xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900">
          <Loader2 className="h-6 w-6 animate-spin text-orange-600" />
        </div>
      ) : (
        <>
          {/* Pending Users */}
          <section className="rounded-2xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 p-5 shadow-sm">
            <h3 className="text-sm font-bold text-zinc-900 dark:text-zinc-100">
              Chờ cấp quyền ({pendingUsers.length})
            </h3>
            {pendingUsers.length === 0 ? (
              <p className="mt-2 text-xs text-zinc-500 dark:text-zinc-400">
                Không có tài khoản nào đang chờ phê duyệt.
              </p>
            ) : (
              <div className="mt-3 space-y-2.5">
                {pendingUsers.map((u) => (
                  <div
                    key={u.uid}
                    className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-amber-200 dark:border-amber-500/40 bg-amber-50/70 dark:bg-amber-950/30 p-3.5"
                  >
                    <div className="min-w-0">
                      <p className="truncate text-sm font-bold text-zinc-900 dark:text-zinc-100">
                        {u.displayName}
                      </p>
                      <p className="truncate text-xs font-mono text-zinc-600 dark:text-zinc-400">
                        {u.email}
                      </p>
                    </div>
                    <div className="flex gap-2">
                      <button
                        type="button"
                        onClick={() => {
                          setApproveTarget(u);
                          setApproveRole(UserRole.USER);
                          setApproveDept(u.department ?? '');
                        }}
                        className="inline-flex h-9 items-center gap-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-700 px-3 text-xs font-semibold text-white cursor-pointer"
                      >
                        <Check className="h-3.5 w-3.5" />
                        Phê duyệt
                      </button>
                      <button
                        type="button"
                        onClick={() => {
                          setRejectTarget(u);
                          setRejectReason('');
                        }}
                        className="inline-flex h-9 items-center gap-1.5 rounded-lg border border-red-200 dark:border-red-500/40 bg-red-50 dark:bg-red-950/40 px-3 text-xs font-semibold text-red-700 dark:text-red-300 cursor-pointer"
                      >
                        <UserX className="h-3.5 w-3.5" />
                        Từ chối
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </section>

          {/* Approved Users */}
          <section className="rounded-2xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 p-5 shadow-sm">
            <h3 className="text-sm font-bold text-zinc-900 dark:text-zinc-100">
              Tài khoản đã kích hoạt ({approvedUsers.length})
            </h3>
            <div className="mt-3 grid grid-cols-1 gap-2.5 md:grid-cols-2">
              {approvedUsers.map((u) => {
                const targetIsRoot = isRootAdminEmail(u.email);
                const targetIsAdmin = parseUserRole(u.role) === UserRole.ADMIN;
                const canEditTarget = !targetIsRoot && (isRootAdmin || !targetIsAdmin);
                return (
                  <div
                    key={u.uid}
                    className={`flex items-center justify-between gap-3 rounded-xl border p-3.5 ${
                      u.active
                        ? 'border-zinc-200 dark:border-zinc-800 bg-zinc-50 dark:bg-zinc-950/60'
                        : 'border-dashed border-zinc-300 dark:border-zinc-700 opacity-65'
                    }`}
                  >
                    <div className="min-w-0">
                      <div className="flex flex-wrap items-center gap-2">
                        <p className="truncate text-sm font-bold text-zinc-900 dark:text-zinc-100">
                          {u.displayName}
                        </p>
                        {targetIsRoot && (
                          <span className="inline-flex items-center gap-1 rounded-md border border-orange-200 dark:border-orange-500/40 bg-orange-50 dark:bg-orange-950/40 px-1.5 py-0.5 text-[10px] font-bold text-orange-700 dark:text-orange-300">
                            <ShieldCheck className="h-3 w-3" />
                            Gốc
                          </span>
                        )}
                      </div>
                      <p className="truncate text-xs font-mono text-zinc-500 dark:text-zinc-400">
                        {u.email}
                      </p>
                      <div className="mt-2 flex flex-wrap items-center gap-2">
                        <RoleBadge roleRaw={u.role} short />
                        {u.department && (
                          <span className="text-[11px] text-zinc-500 dark:text-zinc-400">
                            · {u.department}
                          </span>
                        )}
                        {!u.active && (
                          <span className="text-[11px] font-bold text-red-600 dark:text-red-400">
                            (Đã khóa)
                          </span>
                        )}
                      </div>
                    </div>

                    {canEditTarget && (
                      <button
                        type="button"
                        onClick={() => {
                          setEditTarget(u);
                          setEditRole(parseUserRole(u.role));
                          setEditDept(u.department ?? '');
                          setEditActive(u.active);
                        }}
                        aria-label="Chỉnh sửa quyền"
                        className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl border border-zinc-200 dark:border-zinc-700 bg-white dark:bg-zinc-800 text-zinc-700 dark:text-zinc-200 hover:border-orange-600 cursor-pointer"
                      >
                        <Edit2 className="h-4 w-4" />
                      </button>
                    )}
                  </div>
                );
              })}
            </div>
          </section>

          {/* Rejected Users */}
          {rejectedUsers.length > 0 && (
            <section className="rounded-2xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 p-5 shadow-sm">
              <h3 className="text-sm font-bold text-zinc-900 dark:text-zinc-100">
                Đã từ chối ({rejectedUsers.length})
              </h3>
              <div className="mt-3 space-y-2.5">
                {rejectedUsers.map((u) => (
                  <div
                    key={u.uid}
                    className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-red-200 dark:border-red-500/40 bg-red-50/60 dark:bg-red-950/30 p-3.5"
                  >
                    <div className="min-w-0">
                      <p className="truncate text-sm font-bold text-zinc-900 dark:text-zinc-100">
                        {u.displayName}
                      </p>
                      <p className="truncate text-xs font-mono text-zinc-500 dark:text-zinc-400">
                        {u.email}
                      </p>
                      {u.rejectionReason && (
                        <p className="mt-1 text-xs text-red-700 dark:text-red-300">
                          Lý do: {u.rejectionReason}
                        </p>
                      )}
                    </div>
                    <button
                      type="button"
                      onClick={() => {
                        setApproveTarget(u);
                        setApproveRole(UserRole.USER);
                        setApproveDept(u.department ?? '');
                      }}
                      className="inline-flex h-9 items-center gap-1.5 rounded-lg bg-orange-600 hover:bg-orange-700 px-3 text-xs font-semibold text-white cursor-pointer"
                    >
                      <UserCheck className="h-3.5 w-3.5" />
                      Cấp lại quyền
                    </button>
                  </div>
                ))}
              </div>
            </section>
          )}

          {/* Pre-assigned Emails */}
          <section className="rounded-2xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 p-5 shadow-sm">
            <h3 className="text-sm font-bold text-zinc-900 dark:text-zinc-100">
              Danh sách gán quyền sẵn theo Email ({assignments.length})
            </h3>
            {assignments.length === 0 ? (
              <p className="mt-2 text-xs text-zinc-500 dark:text-zinc-400">
                Chưa có email nào được gán sẵn vai trò trước khi đăng nhập.
              </p>
            ) : (
              <div className="mt-3 space-y-2">
                {assignments.map((a) => {
                  const protectedRow =
                    isRootAdminEmail(a.email) ||
                    (!isRootAdmin && parseUserRole(a.role) === UserRole.ADMIN);
                  return (
                    <div
                      key={a.id}
                      className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-zinc-200 dark:border-zinc-800 bg-zinc-50 dark:bg-zinc-950/60 px-4 py-3"
                    >
                      <div className="min-w-0">
                        <p className="truncate text-sm font-bold text-zinc-900 dark:text-zinc-100">
                          {a.displayName}
                        </p>
                        <p className="truncate text-xs text-zinc-500 dark:text-zinc-400">
                          <span className="font-mono">{a.email}</span>
                          {a.department ? ` · ${a.department}` : ''}
                        </p>
                      </div>
                      <div className="flex items-center gap-2">
                        <RoleBadge roleRaw={a.role} short />
                        {!protectedRow && (
                          <button
                            type="button"
                            onClick={() => void removeRoleAssignment(a.email)}
                            aria-label="Xóa phân quyền trước"
                            className="flex h-8 w-8 items-center justify-center rounded-lg text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-950/40 cursor-pointer"
                          >
                            <Trash2 className="h-4 w-4" />
                          </button>
                        )}
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </section>
        </>
      )}

      {/* Approve Modal */}
      {approveTarget && (
        <div
          role="dialog"
          aria-modal="true"
          className="fixed inset-0 z-50 flex items-center justify-center bg-zinc-950/60 p-4 backdrop-blur-[2px]"
        >
          <form
            onSubmit={(e) => void handleConfirmApprove(e)}
            className="w-full max-w-md rounded-2xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 shadow-xl overflow-hidden"
          >
            <div className="bg-zinc-950 px-5 py-4 flex items-center justify-between border-b border-zinc-800">
              <div className="flex items-center gap-2.5 min-w-0">
                <div className="w-8 h-8 rounded-lg bg-emerald-600 text-white flex items-center justify-center shrink-0">
                  <UserCheck className="w-4 h-4" />
                </div>
                <h3 className="text-sm font-bold text-white truncate">Cấp quyền truy cập</h3>
              </div>
              <button
                type="button"
                onClick={() => setApproveTarget(null)}
                className="rounded-lg p-1.5 text-zinc-400 hover:bg-zinc-800 hover:text-white cursor-pointer"
              >
                <X className="h-4 w-4" />
              </button>
            </div>

            <div className="p-5 space-y-4">
              <p className="text-xs text-zinc-600 dark:text-zinc-400">
                <strong className="text-zinc-900 dark:text-zinc-100">{approveTarget.displayName}</strong>{' '}
                (<span className="font-mono">{approveTarget.email}</span>)
              </p>

              <div>
                <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                  Chọn vai trò
                </label>
                <select
                  value={approveRole}
                  onChange={(e) => setApproveRole(e.target.value as UserRole)}
                  className="mt-1.5 h-11 w-full rounded-xl border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-950 px-3 text-sm text-zinc-900 dark:text-zinc-100 focus:outline-none focus:border-orange-600"
                >
                  {assignableRoles.map((r) => (
                    <option key={r} value={r}>
                      {USER_ROLE_LABELS[r].labelVi}
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                  Bộ phận / Chi nhánh (tùy chọn)
                </label>
                <input
                  type="text"
                  maxLength={80}
                  value={approveDept}
                  onChange={(e) => setApproveDept(e.target.value)}
                  placeholder="VD: Kỹ thuật Quận 1"
                  className="mt-1.5 h-11 w-full rounded-xl border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-950 px-3 text-sm text-zinc-900 dark:text-zinc-100 focus:outline-none focus:border-orange-600"
                />
              </div>

              <div className="flex gap-2.5 pt-2">
                <button
                  type="button"
                  disabled={busy}
                  onClick={() => setApproveTarget(null)}
                  className="h-11 flex-1 rounded-xl border border-zinc-200 dark:border-zinc-700 bg-white dark:bg-zinc-800 hover:bg-zinc-50 dark:hover:bg-zinc-700 px-4 text-xs font-semibold text-zinc-800 dark:text-zinc-100 cursor-pointer"
                >
                  Hủy
                </button>
                <button
                  type="submit"
                  disabled={busy}
                  className="h-11 flex-1 rounded-xl bg-emerald-600 hover:bg-emerald-700 px-4 text-xs font-semibold text-white cursor-pointer"
                >
                  {busy ? 'Đang lưu...' : 'Kích hoạt ngay'}
                </button>
              </div>
            </div>
          </form>
        </div>
      )}

      {/* Reject Modal */}
      {rejectTarget && (
        <div
          role="dialog"
          aria-modal="true"
          className="fixed inset-0 z-50 flex items-center justify-center bg-zinc-950/60 p-4 backdrop-blur-[2px]"
        >
          <form
            onSubmit={(e) => void handleConfirmReject(e)}
            className="w-full max-w-md rounded-2xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 shadow-xl overflow-hidden"
          >
            <div className="bg-zinc-950 px-5 py-4 flex items-center justify-between border-b border-zinc-800">
              <div className="flex items-center gap-2.5 min-w-0">
                <div className="w-8 h-8 rounded-lg bg-red-600 text-white flex items-center justify-center shrink-0">
                  <UserX className="w-4 h-4" />
                </div>
                <h3 className="text-sm font-bold text-white truncate">
                  Từ chối tài khoản {rejectTarget.displayName}?
                </h3>
              </div>
              <button
                type="button"
                onClick={() => setRejectTarget(null)}
                className="rounded-lg p-1.5 text-zinc-400 hover:bg-zinc-800 hover:text-white cursor-pointer"
              >
                <X className="h-4 w-4" />
              </button>
            </div>

            <div className="p-5 space-y-4">
              <div>
                <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                  Lý do từ chối (tùy chọn)
                </label>
                <input
                  type="text"
                  maxLength={300}
                  value={rejectReason}
                  onChange={(e) => setRejectReason(e.target.value)}
                  placeholder="VD: Không thuộc danh sách nhân sự công ty"
                  className="mt-1.5 h-11 w-full rounded-xl border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-950 px-3 text-sm text-zinc-900 dark:text-zinc-100 focus:outline-none focus:border-orange-600"
                />
              </div>
              <div className="flex gap-2.5">
                <button
                  type="button"
                  disabled={busy}
                  onClick={() => setRejectTarget(null)}
                  className="h-11 flex-1 rounded-xl border border-zinc-200 dark:border-zinc-700 bg-white dark:bg-zinc-800 hover:bg-zinc-50 dark:hover:bg-zinc-700 px-4 text-xs font-semibold text-zinc-800 dark:text-zinc-100 cursor-pointer"
                >
                  Hủy
                </button>
                <button
                  type="submit"
                  disabled={busy}
                  className="h-11 flex-1 rounded-xl bg-red-600 hover:bg-red-700 px-4 text-xs font-semibold text-white cursor-pointer"
                >
                  {busy ? 'Đang lưu...' : 'Xác nhận từ chối'}
                </button>
              </div>
            </div>
          </form>
        </div>
      )}

      {/* Edit User Modal */}
      {editTarget && (
        <div
          role="dialog"
          aria-modal="true"
          className="fixed inset-0 z-50 flex items-center justify-center bg-zinc-950/60 p-4 backdrop-blur-[2px]"
        >
          <form
            onSubmit={(e) => void handleConfirmEditUser(e)}
            className="w-full max-w-md rounded-2xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 shadow-xl overflow-hidden"
          >
            <div className="bg-zinc-950 px-5 py-4 flex items-center justify-between border-b border-zinc-800">
              <div className="flex items-center gap-2.5 min-w-0">
                <div className="w-8 h-8 rounded-lg bg-orange-600 text-white flex items-center justify-center shrink-0">
                  <Edit2 className="w-4 h-4" />
                </div>
                <h3 className="text-sm font-bold text-white truncate">Chỉnh sửa tài khoản</h3>
              </div>
              <button
                type="button"
                onClick={() => setEditTarget(null)}
                className="rounded-lg p-1.5 text-zinc-400 hover:bg-zinc-800 hover:text-white cursor-pointer"
              >
                <X className="h-4 w-4" />
              </button>
            </div>

            <div className="p-5 space-y-4">
              <p className="text-xs text-zinc-600 dark:text-zinc-400">
                <strong className="text-zinc-900 dark:text-zinc-100">{editTarget.displayName}</strong>{' '}
                (<span className="font-mono">{editTarget.email}</span>)
              </p>

              <div>
                <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                  Vai trò
                </label>
                <select
                  value={editRole}
                  onChange={(e) => setEditRole(e.target.value as UserRole)}
                  className="mt-1.5 h-11 w-full rounded-xl border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-950 px-3 text-sm text-zinc-900 dark:text-zinc-100 focus:outline-none focus:border-orange-600"
                >
                  {assignableRoles.map((r) => (
                    <option key={r} value={r}>
                      {USER_ROLE_LABELS[r].labelVi}
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                  Bộ phận
                </label>
                <input
                  type="text"
                  maxLength={80}
                  value={editDept}
                  onChange={(e) => setEditDept(e.target.value)}
                  className="mt-1.5 h-11 w-full rounded-xl border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-950 px-3 text-sm text-zinc-900 dark:text-zinc-100 focus:outline-none focus:border-orange-600"
                />
              </div>

              <label className="flex items-center gap-2 text-xs font-semibold text-zinc-800 dark:text-zinc-200 cursor-pointer">
                <input
                  type="checkbox"
                  checked={editActive}
                  onChange={(e) => setEditActive(e.target.checked)}
                  className="h-4 w-4 accent-orange-600"
                />
                Đang hoạt động (bỏ chọn để tạm khóa tài khoản)
              </label>

              <div className="flex gap-2.5 pt-2">
                <button
                  type="button"
                  disabled={busy}
                  onClick={() => setEditTarget(null)}
                  className="h-11 flex-1 rounded-xl border border-zinc-200 dark:border-zinc-700 bg-white dark:bg-zinc-800 hover:bg-zinc-50 dark:hover:bg-zinc-700 px-4 text-xs font-semibold text-zinc-800 dark:text-zinc-100 cursor-pointer"
                >
                  Hủy
                </button>
                <button
                  type="submit"
                  disabled={busy}
                  className="h-11 flex-1 rounded-xl bg-orange-600 hover:bg-orange-700 disabled:bg-orange-400 px-4 text-xs font-semibold text-white cursor-pointer"
                >
                  {busy ? 'Đang lưu...' : 'Lưu thay đổi'}
                </button>
              </div>
            </div>
          </form>
        </div>
      )}

      {/* Assign by Email Modal */}
      {assignModalOpen && (
        <div
          role="dialog"
          aria-modal="true"
          className="fixed inset-0 z-50 flex items-center justify-center bg-zinc-950/60 p-4 backdrop-blur-[2px]"
        >
          <form
            onSubmit={(e) => void handleAssignByEmail(e)}
            className="w-full max-w-md rounded-2xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 shadow-xl overflow-hidden"
          >
            <div className="bg-zinc-950 px-5 py-4 flex items-center justify-between border-b border-zinc-800">
              <div className="flex items-center gap-2.5 min-w-0">
                <div className="w-8 h-8 rounded-lg bg-orange-600 text-white flex items-center justify-center shrink-0">
                  <MailPlus className="w-4 h-4" />
                </div>
                <h3 className="text-sm font-bold text-white truncate">
                  Gán sẵn quyền theo Email Google
                </h3>
              </div>
              <button
                type="button"
                onClick={() => setAssignModalOpen(false)}
                className="rounded-lg p-1.5 text-zinc-400 hover:bg-zinc-800 hover:text-white cursor-pointer"
              >
                <X className="h-4 w-4" />
              </button>
            </div>

            <div className="p-5 space-y-4">
              <div>
                <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                  Email Google *
                </label>
                <input
                  required
                  type="email"
                  value={assignEmail}
                  onChange={(e) => setAssignEmail(e.target.value)}
                  placeholder="nhanvien@gmail.com"
                  className="mt-1.5 h-11 w-full rounded-xl border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-950 px-3 text-sm text-zinc-900 dark:text-zinc-100 focus:outline-none focus:border-orange-600"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                  Họ tên hiển thị *
                </label>
                <input
                  required
                  type="text"
                  value={assignName}
                  onChange={(e) => setAssignName(e.target.value)}
                  placeholder="Nguyễn Văn A"
                  className="mt-1.5 h-11 w-full rounded-xl border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-950 px-3 text-sm text-zinc-900 dark:text-zinc-100 focus:outline-none focus:border-orange-600"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                  Vai trò được cấp
                </label>
                <select
                  value={assignRole}
                  onChange={(e) => setAssignRole(e.target.value as UserRole)}
                  className="mt-1.5 h-11 w-full rounded-xl border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-950 px-3 text-sm text-zinc-900 dark:text-zinc-100 focus:outline-none focus:border-orange-600"
                >
                  {assignableRoles.map((r) => (
                    <option key={r} value={r}>
                      {USER_ROLE_LABELS[r].labelVi}
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                  Bộ phận (tùy chọn)
                </label>
                <input
                  type="text"
                  value={assignDept}
                  onChange={(e) => setAssignDept(e.target.value)}
                  className="mt-1.5 h-11 w-full rounded-xl border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-950 px-3 text-sm text-zinc-900 dark:text-zinc-100 focus:outline-none focus:border-orange-600"
                />
              </div>

              <div className="flex gap-2.5 pt-2">
                <button
                  type="button"
                  disabled={busy}
                  onClick={() => setAssignModalOpen(false)}
                  className="h-11 flex-1 rounded-xl border border-zinc-200 dark:border-zinc-700 bg-white dark:bg-zinc-800 hover:bg-zinc-50 dark:hover:bg-zinc-700 px-4 text-xs font-semibold text-zinc-800 dark:text-zinc-100 cursor-pointer"
                >
                  Hủy
                </button>
                <button
                  type="submit"
                  disabled={busy}
                  className="h-11 flex-1 rounded-xl bg-orange-600 hover:bg-orange-700 disabled:bg-orange-400 px-4 text-xs font-semibold text-white cursor-pointer"
                >
                  {busy ? 'Đang lưu...' : 'Lưu phân quyền'}
                </button>
              </div>
            </div>
          </form>
        </div>
      )}
    </div>
  );
};
