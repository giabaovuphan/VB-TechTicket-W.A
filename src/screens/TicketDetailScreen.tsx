import React, { useEffect, useState } from 'react';
import {
  ArrowLeft,
  CheckCircle2,
  Loader2,
  Play,
  ShieldAlert,
  Trash2,
  UserPlus,
  X,
} from 'lucide-react';
import { PriorityBadge, StatusBadge, StatusProgressStepper } from '../components/Badges';
import { Base64PhotoPreview, PhotoCapturePickerCard } from '../components/PhotoControls';
import { RepairInvoiceCard } from '../components/RepairInvoiceCard';
import { useToast } from '../context/ToastContext';
import { markInvoiceAsPaid, subscribeInvoice } from '../services/invoiceService';
import {
  adminForceStatus,
  assignTicket,
  completeTicket,
  deleteTicketAndInvoice,
  startProcessingTicket,
  subscribeTicket,
  updateInProgressWork,
} from '../services/ticketService';
import {
  formatTimestampVi,
  InventoryItem,
  InvoicePaymentMethod,
  parseTicketPriority,
  parseTicketStatus,
  ProductItem,
  RepairInvoice,
  SupportTicket,
  TICKET_PRIORITY_META,
  TICKET_STATUS_META,
  TicketPriority,
  TicketStatus,
  UserAccount,
  UserRole,
} from '../types/models';
import { toFriendlyVietnameseError } from '../utils/firestoreErrors';
import { InvoiceEditorModal } from './InvoiceEditorModal';

export const TicketDetailScreen: React.FC<{
  ticketId: string;
  currentUser: UserAccount;
  currentRole: UserRole;
  technicians: UserAccount[];
  inventoryItems: InventoryItem[];
  productItems: ProductItem[];
  onBack: () => void;
}> = ({
  ticketId,
  currentUser,
  currentRole,
  technicians,
  inventoryItems,
  productItems,
  onBack,
}) => {
  const { showToast } = useToast();
  const [ticket, setTicket] = useState<SupportTicket | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [invoice, setInvoice] = useState<RepairInvoice | null>(null);

  // Form states
  const [selectedTechUid, setSelectedTechUid] = useState('');
  const [selectedPriority, setSelectedPriority] = useState<TicketPriority>(TicketPriority.MEDIUM);
  const [resolutionNote, setResolutionNote] = useState('');
  const [resolvedPhotoDraft, setResolvedPhotoDraft] = useState<string | null>(null);

  const [busyAction, setBusyAction] = useState<string | null>(null);
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false);
  const [invoiceEditorOpen, setInvoiceEditorOpen] = useState(false);

  useEffect(() => {
    setLoading(true);
    setError(null);
    const unsubTicket = subscribeTicket(
      ticketId,
      (nextTicket) => {
        setTicket(nextTicket);
        setLoading(false);
        if (nextTicket) {
          setSelectedTechUid((prev) => prev || nextTicket.assigneeUid || '');
          setSelectedPriority(parseTicketPriority(nextTicket.priority));
          setResolutionNote(nextTicket.resolutionNote ?? '');
          setResolvedPhotoDraft(nextTicket.resolvedPhotoBase64 ?? null);
        }
      },
      (err) => {
        setError(toFriendlyVietnameseError(err, 'Không thể tải chi tiết ticket.'));
        setLoading(false);
      },
    );

    const unsubInvoice = subscribeInvoice(
      ticketId,
      (nextInv) => setInvoice(nextInv),
      () => setInvoice(null),
    );

    return () => {
      unsubTicket();
      unsubInvoice();
    };
  }, [ticketId]);

  if (loading) {
    return (
      <div className="flex min-h-[320px] items-center justify-center rounded-2xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900">
        <Loader2 className="h-6 w-6 animate-spin text-orange-600" />
      </div>
    );
  }

  if (error || !ticket) {
    return (
      <div className="rounded-2xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 p-6 text-center">
        <p className="text-sm font-semibold text-red-600 dark:text-red-400">
          {error || 'Ticket không còn tồn tại hoặc đã bị xóa.'}
        </p>
        <button
          type="button"
          onClick={onBack}
          className="mt-4 inline-flex h-10 items-center gap-2 rounded-xl border border-zinc-200 dark:border-zinc-700 bg-white dark:bg-zinc-800 px-4 text-xs font-semibold text-zinc-800 dark:text-zinc-100 cursor-pointer"
        >
          <ArrowLeft className="h-4 w-4" />
          Quay lại danh sách
        </button>
      </div>
    );
  }

  const status = parseTicketStatus(ticket.status);
  const isCompleted = status === TicketStatus.COMPLETED;
  const isLeadOrAdmin = currentRole === UserRole.TEAM_LEAD || currentRole === UserRole.ADMIN;
  const isAdmin = currentRole === UserRole.ADMIN;
  const isAssignee = Boolean(ticket.assigneeUid && ticket.assigneeUid === currentUser.uid);
  const canDoTechWork =
    !isCompleted &&
    (isAdmin ||
      (isAssignee &&
        (currentRole === UserRole.TECHNICIAN || currentRole === UserRole.TEAM_LEAD)));
  const canEditInvoice =
    isLeadOrAdmin || (currentRole === UserRole.TECHNICIAN && isAssignee);
  const canMarkPaid = isLeadOrAdmin || (currentRole === UserRole.TECHNICIAN && isAssignee);

  const handleAssign = async () => {
    const assignee = technicians.find((t) => t.uid === selectedTechUid);
    if (!assignee) {
      showToast('Vui lòng chọn kỹ thuật viên để phân công.', true);
      return;
    }
    setBusyAction('assign');
    try {
      await assignTicket({
        ticket,
        assignee,
        priority: selectedPriority,
        teamLeadUid: currentUser.uid,
      });
      showToast(`Đã phân công ticket cho ${assignee.displayName}.`);
    } catch (err) {
      showToast(toFriendlyVietnameseError(err, 'Không thể phân công ticket.'), true);
    } finally {
      setBusyAction(null);
    }
  };

  const handleStartProcessing = async () => {
    setBusyAction('start');
    try {
      await startProcessingTicket({
        ticket,
        actorUid: currentUser.uid,
        resolutionNote,
      });
      showToast('Đã chuyển ticket sang trạng thái Đang xử lý.');
    } catch (err) {
      showToast(toFriendlyVietnameseError(err, 'Không thể bắt đầu xử lý.'), true);
    } finally {
      setBusyAction(null);
    }
  };

  const handleSaveProgress = async () => {
    setBusyAction('save_progress');
    try {
      await updateInProgressWork({
        ticket,
        resolutionNote,
        resolvedPhotoBase64: resolvedPhotoDraft,
      });
      showToast('Đã lưu ghi chú xử lý.');
    } catch (err) {
      showToast(toFriendlyVietnameseError(err, 'Không thể lưu tiến độ.'), true);
    } finally {
      setBusyAction(null);
    }
  };

  const handleComplete = async () => {
    if (!resolvedPhotoDraft || resolvedPhotoDraft.length < 10) {
      showToast('Vui lòng chụp hoặc chọn ảnh sau khi sửa xong trước khi hoàn thành.', true);
      return;
    }
    setBusyAction('complete');
    try {
      await completeTicket({
        ticket,
        actorUid: currentUser.uid,
        resolvedPhotoBase64: resolvedPhotoDraft,
        resolutionNote,
      });
      showToast('Ticket đã được hoàn thành!');
    } catch (err) {
      showToast(toFriendlyVietnameseError(err, 'Không thể hoàn thành ticket.'), true);
    } finally {
      setBusyAction(null);
    }
  };

  const handleAdminForceStatus = async (nextStatus: TicketStatus) => {
    setBusyAction(`force_${nextStatus}`);
    try {
      await adminForceStatus({
        ticket,
        actorUid: currentUser.uid,
        newStatus: nextStatus,
      });
      showToast(`Đã cập nhật trạng thái thành ${TICKET_STATUS_META[nextStatus].labelVi}.`);
    } catch (err) {
      showToast(toFriendlyVietnameseError(err, 'Không thể chuyển trạng thái.'), true);
    } finally {
      setBusyAction(null);
    }
  };

  const handleDeleteTicket = async () => {
    setBusyAction('delete');
    try {
      await deleteTicketAndInvoice(ticket.id);
      showToast(`Đã xóa ticket #${ticket.ticketCode}.`);
      onBack();
    } catch (err) {
      showToast(toFriendlyVietnameseError(err, 'Không thể xóa ticket.'), true);
      setBusyAction(null);
    }
  };

  return (
    <div className="space-y-4">
      {/* Header Toolbar */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <button
          type="button"
          onClick={onBack}
          className="inline-flex h-10 items-center gap-2 rounded-xl border border-zinc-200 dark:border-zinc-700 bg-white dark:bg-zinc-900 hover:bg-zinc-50 dark:hover:bg-zinc-800 px-3.5 text-xs font-semibold text-zinc-800 dark:text-zinc-100 transition-colors cursor-pointer"
        >
          <ArrowLeft className="h-4 w-4" />
          Danh sách ticket
        </button>

        {isLeadOrAdmin && (
          <button
            type="button"
            onClick={() => setDeleteConfirmOpen(true)}
            className="inline-flex h-10 items-center gap-1.5 rounded-xl border border-red-200 dark:border-red-500/40 bg-red-50 dark:bg-red-950/40 hover:bg-red-100 dark:hover:bg-red-950/70 px-3.5 text-xs font-semibold text-red-700 dark:text-red-300 transition-colors cursor-pointer"
          >
            <Trash2 className="h-4 w-4" />
            Xóa ticket
          </button>
        )}
      </div>

      {/* Stepper */}
      <StatusProgressStepper currentStatusRaw={ticket.status} />

      {/* 2-Column Desktop Detail Grid */}
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-12">
        {/* Left Main Info */}
        <div className="space-y-4 lg:col-span-7">
          <section className="rounded-2xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 p-5 shadow-sm">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div className="flex items-center gap-2">
                <span className="rounded-md bg-zinc-100 dark:bg-zinc-800 px-2.5 py-1 font-mono-tabular text-xs font-semibold text-zinc-900 dark:text-zinc-100">
                  #{ticket.ticketCode}
                </span>
                <PriorityBadge priorityRaw={ticket.priority} />
              </div>
              <StatusBadge statusRaw={ticket.status} />
            </div>

            <h2 className="mt-3 text-lg sm:text-xl font-bold leading-snug text-zinc-900 dark:text-zinc-100">
              {ticket.title}
            </h2>
            <p className="mt-2 whitespace-pre-line text-sm leading-relaxed text-zinc-600 dark:text-zinc-400">
              {ticket.description}
            </p>

            <dl className="mt-5 grid grid-cols-1 gap-3 rounded-xl border border-zinc-200 dark:border-zinc-800 bg-zinc-50 dark:bg-zinc-950/60 p-4 text-xs sm:grid-cols-2">
              <div>
                <dt className="text-zinc-500 dark:text-zinc-400">Danh mục</dt>
                <dd className="mt-0.5 font-semibold text-zinc-900 dark:text-zinc-100">
                  {ticket.category}
                </dd>
              </div>
              <div>
                <dt className="text-zinc-500 dark:text-zinc-400">Người gửi yêu cầu</dt>
                <dd className="mt-0.5 font-semibold text-zinc-900 dark:text-zinc-100">
                  {ticket.creatorName}
                </dd>
              </div>
              <div>
                <dt className="text-zinc-500 dark:text-zinc-400">Kỹ thuật viên phụ trách</dt>
                <dd className="mt-0.5 font-semibold text-zinc-900 dark:text-zinc-100">
                  {ticket.assigneeName || 'Chưa phân công'}
                </dd>
              </div>
              <div>
                <dt className="text-zinc-500 dark:text-zinc-400">Ngày tạo / Cập nhật</dt>
                <dd className="mt-0.5 font-mono-tabular font-semibold text-zinc-900 dark:text-zinc-100">
                  {formatTimestampVi(ticket.createdAt)} · {formatTimestampVi(ticket.updatedAt)}
                </dd>
              </div>
              {ticket.completedAt && (
                <div className="sm:col-span-2">
                  <dt className="text-zinc-500 dark:text-zinc-400">Hoàn thành lúc</dt>
                  <dd className="mt-0.5 font-mono-tabular font-semibold text-emerald-600 dark:text-emerald-400">
                    {formatTimestampVi(ticket.completedAt)}
                  </dd>
                </div>
              )}
            </dl>
          </section>

          {/* Before / After Photos */}
          <section className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <Base64PhotoPreview
              base64={ticket.defectPhotoBase64}
              label="Ảnh lỗi ban đầu (Trước)"
              emptyText="Người gửi không đính kèm ảnh lỗi"
            />
            <Base64PhotoPreview
              base64={ticket.resolvedPhotoBase64}
              label="Ảnh nghiệm thu (Sau)"
              emptyText="Chưa có ảnh xác nhận hoàn thành"
            />
          </section>

          {ticket.resolutionNote && (
            <section className="rounded-2xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 p-5 shadow-sm">
              <p className="text-xs font-bold uppercase tracking-wider text-zinc-500 dark:text-zinc-400">
                Ghi chú xử lý kỹ thuật
              </p>
              <p className="mt-2 whitespace-pre-line text-sm text-zinc-800 dark:text-zinc-200">
                {ticket.resolutionNote}
              </p>
            </section>
          )}
        </div>

        {/* Right Operations & Invoice Column */}
        <div className="space-y-4 lg:col-span-5">
          {/* Repair Invoice Card */}
          <RepairInvoiceCard
            ticket={ticket}
            invoice={invoice}
            canEdit={canEditInvoice}
            canMarkPaid={canMarkPaid}
            onOpenEditor={() => setInvoiceEditorOpen(true)}
            onMarkPaid={async (method: InvoicePaymentMethod) => {
              if (!invoice) return;
              await markInvoiceAsPaid({
                ticket,
                invoice,
                paymentMethod: method,
                actor: currentUser,
              });
            }}
          />

          {/* Lead / Admin Assignment Panel */}
          {isLeadOrAdmin && (!isCompleted || isAdmin) && (
            <section className="rounded-2xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 p-5 shadow-sm">
              <div className="flex items-center gap-2.5">
                <div className="w-8 h-8 rounded-lg bg-orange-50 dark:bg-orange-950/40 border border-orange-200 dark:border-orange-800/50 text-orange-600 dark:text-orange-400 flex items-center justify-center shrink-0">
                  <UserPlus className="h-4 w-4" />
                </div>
                <h2 className="text-sm font-bold text-zinc-900 dark:text-zinc-100">
                  Điều phối &amp; phân công kỹ thuật viên
                </h2>
              </div>

              <div className="mt-4 space-y-3">
                <div>
                  <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                    Chọn kỹ thuật viên
                  </label>
                  <select
                    value={selectedTechUid}
                    onChange={(e) => setSelectedTechUid(e.target.value)}
                    className="mt-1.5 h-11 w-full rounded-xl border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-950 px-3 text-sm text-zinc-900 dark:text-zinc-100 focus:outline-none focus:border-orange-600"
                  >
                    <option value="">-- Chọn kỹ thuật viên --</option>
                    {technicians.map((tech) => (
                      <option key={tech.uid} value={tech.uid}>
                        {tech.displayName} ({tech.email})
                      </option>
                    ))}
                  </select>
                </div>

                <div>
                  <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                    Cập nhật mức ưu tiên
                  </label>
                  <select
                    value={selectedPriority}
                    onChange={(e) => setSelectedPriority(e.target.value as TicketPriority)}
                    className="mt-1.5 h-11 w-full rounded-xl border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-950 px-3 text-sm text-zinc-900 dark:text-zinc-100 focus:outline-none focus:border-orange-600"
                  >
                    {Object.values(TicketPriority).map((p) => (
                      <option key={p} value={p}>
                        {TICKET_PRIORITY_META[p].labelVi}
                      </option>
                    ))}
                  </select>
                </div>

                <button
                  type="button"
                  disabled={busyAction === 'assign'}
                  onClick={() => void handleAssign()}
                  className="inline-flex h-11 w-full items-center justify-center gap-2 rounded-xl bg-orange-600 hover:bg-orange-700 disabled:bg-orange-400 px-4 text-xs font-semibold text-white transition-colors cursor-pointer"
                >
                  {busyAction === 'assign' && <Loader2 className="h-4 w-4 animate-spin" />}
                  {ticket.assigneeUid ? 'Cập nhật phân công' : 'Phân công xử lý'}
                </button>
              </div>
            </section>
          )}

          {/* Technician / Lead Technical Workflow */}
          {canDoTechWork && (status === TicketStatus.ACCEPTED || status === TicketStatus.IN_PROGRESS) && (
            <section className="rounded-2xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 p-5 shadow-sm">
              <h2 className="text-sm font-bold text-zinc-900 dark:text-zinc-100">
                Thao tác xử lý kỹ thuật
              </h2>

              <div className="mt-3 space-y-4">
                <div>
                  <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                    Ghi chú kiểm tra / phương án sửa chữa
                  </label>
                  <textarea
                    rows={3}
                    maxLength={1000}
                    value={resolutionNote}
                    onChange={(e) => setResolutionNote(e.target.value)}
                    placeholder="Nhập nguyên nhân, linh kiện đã thay hoặc hướng dẫn vận hành..."
                    className="mt-1.5 w-full rounded-xl border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-950 p-3 text-sm text-zinc-900 dark:text-zinc-100 placeholder:text-zinc-400 focus:outline-none focus:border-orange-600"
                  />
                </div>

                {status === TicketStatus.ACCEPTED && (
                  <button
                    type="button"
                    disabled={busyAction === 'start'}
                    onClick={() => void handleStartProcessing()}
                    className="inline-flex h-11 w-full items-center justify-center gap-2 rounded-xl bg-violet-600 hover:bg-violet-700 disabled:opacity-60 px-4 text-xs font-semibold text-white transition-colors cursor-pointer"
                  >
                    {busyAction === 'start' ? (
                      <Loader2 className="h-4 w-4 animate-spin" />
                    ) : (
                      <Play className="h-4 w-4" />
                    )}
                    Bắt đầu xử lý
                  </button>
                )}

                {status === TicketStatus.IN_PROGRESS && (
                  <>
                    <PhotoCapturePickerCard
                      label="Ảnh nghiệm thu sau khi sửa xong"
                      helperText="Bắt buộc có ảnh xác nhận trước khi bấm Hoàn thành."
                      required
                      valueBase64={resolvedPhotoDraft}
                      onChange={setResolvedPhotoDraft}
                      onError={(msg) => showToast(msg, true)}
                    />

                    <div className="flex flex-col gap-2 sm:flex-row">
                      <button
                        type="button"
                        disabled={busyAction !== null}
                        onClick={() => void handleSaveProgress()}
                        className="h-11 flex-1 rounded-xl border border-zinc-200 dark:border-zinc-700 bg-white dark:bg-zinc-800 hover:bg-zinc-50 dark:hover:bg-zinc-700 px-4 text-xs font-semibold text-zinc-800 dark:text-zinc-100 cursor-pointer"
                      >
                        {busyAction === 'save_progress' ? 'Đang lưu...' : 'Lưu ghi chú'}
                      </button>
                      <button
                        type="button"
                        disabled={busyAction !== null}
                        onClick={() => void handleComplete()}
                        className="inline-flex h-11 flex-1 items-center justify-center gap-2 rounded-xl bg-emerald-600 hover:bg-emerald-700 disabled:opacity-60 px-4 text-xs font-semibold text-white transition-colors cursor-pointer"
                      >
                        {busyAction === 'complete' ? (
                          <Loader2 className="h-4 w-4 animate-spin" />
                        ) : (
                          <CheckCircle2 className="h-4 w-4" />
                        )}
                        Hoàn thành
                      </button>
                    </div>
                  </>
                )}
              </div>
            </section>
          )}

          {/* Admin Quick Override */}
          {isAdmin && (
            <section className="rounded-2xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 p-5 shadow-sm">
              <div className="flex items-center gap-2.5">
                <div className="w-8 h-8 rounded-lg bg-orange-50 dark:bg-orange-950/40 border border-orange-200 dark:border-orange-800/50 text-orange-600 dark:text-orange-400 flex items-center justify-center shrink-0">
                  <ShieldAlert className="h-4 w-4" />
                </div>
                <h2 className="text-sm font-bold text-zinc-900 dark:text-zinc-100">
                  Quyền Admin: Chuyển nhanh trạng thái
                </h2>
              </div>
              <div className="mt-3 grid grid-cols-2 gap-2">
                {Object.values(TicketStatus).map((st) => (
                  <button
                    key={st}
                    type="button"
                    disabled={status === st || busyAction !== null}
                    onClick={() => void handleAdminForceStatus(st)}
                    className={`h-9 rounded-lg border px-3 text-xs font-semibold transition-colors cursor-pointer ${
                      status === st
                        ? 'border-orange-600 bg-orange-600 text-white'
                        : 'border-zinc-200 dark:border-zinc-700 bg-zinc-50 dark:bg-zinc-950 text-zinc-800 dark:text-zinc-200 hover:border-orange-600'
                    }`}
                  >
                    {TICKET_STATUS_META[st].labelVi}
                  </button>
                ))}
              </div>
            </section>
          )}
        </div>
      </div>

      {/* Delete Confirmation Modal */}
      {deleteConfirmOpen && (
        <div
          role="dialog"
          aria-modal="true"
          className="fixed inset-0 z-50 flex items-center justify-center bg-zinc-950/60 p-4 backdrop-blur-[2px]"
        >
          <div className="w-full max-w-md rounded-2xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 shadow-xl overflow-hidden">
            <div className="bg-zinc-950 px-5 py-4 flex items-center justify-between border-b border-zinc-800">
              <div className="flex items-center gap-2.5 min-w-0">
                <div className="w-8 h-8 rounded-lg bg-red-600 text-white flex items-center justify-center shrink-0">
                  <Trash2 className="w-4 h-4" />
                </div>
                <h3 className="text-sm font-bold text-white truncate">
                  Xác nhận xóa Ticket #{ticket.ticketCode}?
                </h3>
              </div>
              <button
                type="button"
                onClick={() => setDeleteConfirmOpen(false)}
                className="rounded-lg p-1.5 text-zinc-400 hover:bg-zinc-800 hover:text-white cursor-pointer"
              >
                <X className="h-4 w-4" />
              </button>
            </div>
            <div className="p-5">
              <p className="text-xs leading-relaxed text-zinc-600 dark:text-zinc-400">
                Thao tác này sẽ xóa vĩnh viễn phiếu hỗ trợ và hóa đơn liên quan (nếu có). Không thể
                hoàn tác.
              </p>
              <div className="mt-5 flex gap-2.5">
                <button
                  type="button"
                  disabled={busyAction === 'delete'}
                  onClick={() => setDeleteConfirmOpen(false)}
                  className="h-11 flex-1 rounded-xl border border-zinc-200 dark:border-zinc-700 bg-white dark:bg-zinc-800 hover:bg-zinc-50 dark:hover:bg-zinc-700 px-4 text-xs font-semibold text-zinc-800 dark:text-zinc-100 cursor-pointer"
                >
                  Hủy
                </button>
                <button
                  type="button"
                  disabled={busyAction === 'delete'}
                  onClick={() => void handleDeleteTicket()}
                  className="h-11 flex-1 rounded-xl bg-red-600 hover:bg-red-700 px-4 text-xs font-semibold text-white disabled:opacity-60 cursor-pointer"
                >
                  {busyAction === 'delete' ? 'Đang xóa...' : 'Xóa vĩnh viễn'}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      <InvoiceEditorModal
        open={invoiceEditorOpen}
        ticket={ticket}
        existingInvoice={invoice}
        inventoryItems={inventoryItems}
        productItems={productItems}
        actor={currentUser}
        actorRole={currentRole}
        onClose={() => setInvoiceEditorOpen(false)}
      />
    </div>
  );
};
