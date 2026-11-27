import React, { useState } from 'react';
import { Loader2, Plus, X } from 'lucide-react';
import { createTicket } from '../services/ticketService';
import {
  TICKET_PRIORITY_META,
  TicketCategories,
  TicketPriority,
  UserAccount,
} from '../types/models';
import { toFriendlyVietnameseError } from '../utils/firestoreErrors';
import { PhotoCapturePickerCard } from '../components/PhotoControls';
import { useToast } from '../context/ToastContext';

export const CreateTicketModal: React.FC<{
  open: boolean;
  creator: UserAccount;
  onClose: () => void;
  onCreated: (ticketId: string) => void;
}> = ({ open, creator, onClose, onCreated }) => {
  const { showToast } = useToast();
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [category, setCategory] = useState<string>(TicketCategories[0]);
  const [priority, setPriority] = useState<TicketPriority>(TicketPriority.MEDIUM);
  const [defectPhotoBase64, setDefectPhotoBase64] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  if (!open) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (title.trim().length < 3) {
      setError('Tiêu đề sự cố phải có ít nhất 3 ký tự.');
      return;
    }
    if (description.trim().length < 5) {
      setError('Mô tả chi tiết phải có ít nhất 5 ký tự.');
      return;
    }

    setSubmitting(true);
    try {
      const id = await createTicket({
        title,
        description,
        category,
        priority,
        creator,
        defectPhotoBase64,
      });
      setTitle('');
      setDescription('');
      setCategory(TicketCategories[0]);
      setPriority(TicketPriority.MEDIUM);
      setDefectPhotoBase64(null);
      showToast('Đã tạo phiếu hỗ trợ thành công.');
      onCreated(id);
    } catch (err) {
      setError(toFriendlyVietnameseError(err, 'Không thể tạo phiếu hỗ trợ.'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label="Tạo ticket hỗ trợ mới"
      className="fixed inset-0 z-50 flex items-end justify-center bg-zinc-950/60 backdrop-blur-[2px] sm:items-center sm:p-4"
    >
      <div className="flex max-h-[92vh] w-full max-w-2xl flex-col overflow-hidden rounded-t-2xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 shadow-xl sm:rounded-2xl">
        {/* Dark Obsidian Modal Header */}
        <div className="bg-zinc-950 px-5 py-4 flex items-center justify-between border-b border-zinc-800 shrink-0">
          <div className="flex items-center gap-3 min-w-0">
            <div className="w-8 h-8 rounded-lg bg-orange-600 text-white flex items-center justify-center shrink-0">
              <Plus className="w-4 h-4" />
            </div>
            <div className="min-w-0">
              <h2 className="text-sm sm:text-base font-bold text-white truncate">
                Tạo phiếu yêu cầu hỗ trợ
              </h2>
              <p className="text-[11px] text-zinc-400 truncate">
                Mô tả rõ sự cố và đính kèm hình ảnh để kỹ thuật viên xử lý nhanh hơn.
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Đóng"
            className="rounded-lg p-1.5 text-zinc-400 transition hover:bg-zinc-800 hover:text-white cursor-pointer"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        <form onSubmit={(e) => void handleSubmit(e)} className="flex-1 space-y-4 overflow-y-auto p-5">
          {error && (
            <div className="rounded-xl border border-red-200 dark:border-red-500/40 bg-red-50 dark:bg-red-950/40 p-3 text-xs font-medium text-red-700 dark:text-red-300">
              {error}
            </div>
          )}

          <div>
            <label
              htmlFor="ticket-title"
              className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300"
            >
              Tiêu đề sự cố <span className="text-red-600 dark:text-red-400">*</span>
            </label>
            <input
              id="ticket-title"
              type="text"
              maxLength={120}
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="VD: Máy pha cà phê tầng 1 báo lỗi áp suất"
              className="mt-1.5 h-11 w-full rounded-xl border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-950 px-3.5 text-sm text-zinc-900 dark:text-zinc-100 placeholder:text-zinc-400 dark:placeholder:text-zinc-500 focus:outline-none focus:border-orange-600"
            />
          </div>

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <div>
              <label
                htmlFor="ticket-category"
                className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300"
              >
                Danh mục thiết bị
              </label>
              <select
                id="ticket-category"
                value={category}
                onChange={(e) => setCategory(e.target.value)}
                className="mt-1.5 h-11 w-full rounded-xl border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-950 px-3 text-sm text-zinc-900 dark:text-zinc-100 focus:outline-none focus:border-orange-600"
              >
                {TicketCategories.map((cat) => (
                  <option key={cat} value={cat}>
                    {cat}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                Mức độ ưu tiên
              </label>
              <div className="mt-1.5 grid grid-cols-2 gap-2">
                {Object.values(TicketPriority).map((p) => (
                  <button
                    key={p}
                    type="button"
                    onClick={() => setPriority(p)}
                    className={`h-10 rounded-xl border px-2.5 text-xs font-semibold transition-colors cursor-pointer ${
                      priority === p
                        ? 'border-orange-600 bg-orange-600 text-white'
                        : 'border-zinc-200 dark:border-zinc-700 bg-zinc-50 dark:bg-zinc-950 text-zinc-800 dark:text-zinc-200 hover:border-zinc-400 dark:hover:border-zinc-600'
                    }`}
                  >
                    {TICKET_PRIORITY_META[p].labelVi}
                  </button>
                ))}
              </div>
            </div>
          </div>

          <div>
            <label
              htmlFor="ticket-desc"
              className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300"
            >
              Mô tả chi tiết tình trạng <span className="text-red-600 dark:text-red-400">*</span>
            </label>
            <textarea
              id="ticket-desc"
              rows={4}
              maxLength={2000}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Ghi rõ hiện tượng, vị trí máy, thời điểm phát sinh sự cố..."
              className="mt-1.5 w-full rounded-xl border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-950 p-3.5 text-sm text-zinc-900 dark:text-zinc-100 placeholder:text-zinc-400 dark:placeholder:text-zinc-500 focus:outline-none focus:border-orange-600"
            />
          </div>

          <PhotoCapturePickerCard
            label="Ảnh chụp tình trạng lỗi (tùy chọn)"
            helperText="Ảnh tự động nén dưới 300KB trước khi lưu lên hệ thống."
            valueBase64={defectPhotoBase64}
            onChange={setDefectPhotoBase64}
            onError={(msg) => setError(msg)}
          />

          <div className="flex flex-col-reverse gap-2.5 border-t border-zinc-200 dark:border-zinc-800 pt-4 sm:flex-row sm:justify-end">
            <button
              type="button"
              disabled={submitting}
              onClick={onClose}
              className="h-11 rounded-xl border border-zinc-200 dark:border-zinc-700 bg-white dark:bg-zinc-800 hover:bg-zinc-50 dark:hover:bg-zinc-700 px-5 text-xs font-semibold text-zinc-800 dark:text-zinc-100 transition-colors cursor-pointer"
            >
              Hủy
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="inline-flex h-11 items-center justify-center gap-2 rounded-xl bg-orange-600 hover:bg-orange-700 disabled:bg-orange-400 px-5 text-xs font-semibold text-white transition-colors cursor-pointer"
            >
              {submitting && <Loader2 className="h-4 w-4 animate-spin" />}
              Gửi yêu cầu hỗ trợ
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
