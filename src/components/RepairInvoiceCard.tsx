import React, { useState } from 'react';
import { CheckCircle2, CreditCard, Edit3, Receipt, Share2, X } from 'lucide-react';
import {
  formatTimestampVi,
  formatVnd,
  INVOICE_PAYMENT_METHOD_LABELS,
  INVOICE_PAYMENT_STATUS_LABELS,
  InvoicePaymentMethod,
  InvoicePaymentStatus,
  parseInvoicePaymentMethod,
  parseInvoicePaymentStatus,
  RepairInvoice,
  SupportTicket,
} from '../types/models';
import { shareOrCopyInvoice } from '../utils/invoiceUtils';
import { useToast } from '../context/ToastContext';

export const RepairInvoiceCard: React.FC<{
  ticket: SupportTicket;
  invoice: RepairInvoice | null;
  canEdit: boolean;
  canMarkPaid: boolean;
  onOpenEditor: () => void;
  onMarkPaid: (method: InvoicePaymentMethod) => Promise<void>;
}> = ({ ticket, invoice, canEdit, canMarkPaid, onOpenEditor, onMarkPaid }) => {
  const { showToast } = useToast();
  const [payingModalOpen, setPayingModalOpen] = useState(false);
  const [selectedMethod, setSelectedMethod] = useState<InvoicePaymentMethod>(
    InvoicePaymentMethod.TRANSFER,
  );
  const [busy, setBusy] = useState(false);

  const handleShare = async () => {
    if (!invoice) return;
    try {
      const mode = await shareOrCopyInvoice(ticket, invoice);
      showToast(
        mode === 'shared'
          ? 'Đã mở chia sẻ hóa đơn.'
          : 'Đã sao chép nội dung hóa đơn vào khay nhớ tạm.',
      );
    } catch {
      showToast('Không thể chia sẻ hóa đơn trên trình duyệt này.', true);
    }
  };

  const handleConfirmPaid = async () => {
    setBusy(true);
    try {
      await onMarkPaid(selectedMethod);
      setPayingModalOpen(false);
      showToast('Đã xác nhận thanh toán hóa đơn.');
    } catch (error) {
      showToast(
        error instanceof Error ? error.message : 'Không thể xác nhận thanh toán.',
        true,
      );
    } finally {
      setBusy(false);
    }
  };

  if (!invoice) {
    return (
      <section className="rounded-2xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 p-5 shadow-sm">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-orange-50 dark:bg-orange-950/40 border border-orange-200 dark:border-orange-800/50 text-orange-600 dark:text-orange-400">
              <Receipt className="h-4 w-4" />
            </div>
            <div>
              <h2 className="text-sm font-bold text-zinc-900 dark:text-zinc-100">
                Hóa đơn sửa chữa
              </h2>
              <p className="text-xs text-zinc-500 dark:text-zinc-400">
                Chưa có hóa đơn linh kiện hoặc phí công cho ticket này.
              </p>
            </div>
          </div>
          {canEdit && (
            <button
              type="button"
              onClick={onOpenEditor}
              className="inline-flex h-10 items-center gap-2 rounded-xl bg-orange-600 hover:bg-orange-700 px-4 text-xs font-semibold text-white transition-colors cursor-pointer"
            >
              <Edit3 className="h-3.5 w-3.5" />
              Lập hóa đơn
            </button>
          )}
        </div>
      </section>
    );
  }

  const paymentStatus = parseInvoicePaymentStatus(invoice.paymentStatus);
  const paymentMethod = parseInvoicePaymentMethod(invoice.paymentMethod);
  const isPaid = paymentStatus === InvoicePaymentStatus.PAID;

  return (
    <>
      <section className="rounded-2xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 p-5 shadow-sm">
        <div className="flex flex-wrap items-start justify-between gap-3 border-b border-zinc-200 dark:border-zinc-800 pb-4">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-orange-50 dark:bg-orange-950/40 border border-orange-200 dark:border-orange-800/50 text-orange-600 dark:text-orange-400">
              <Receipt className="h-5 w-5" />
            </div>
            <div>
              <div className="flex flex-wrap items-center gap-2">
                <h2 className="text-base font-bold text-zinc-900 dark:text-zinc-100">
                  Hóa đơn <span className="font-mono">#{invoice.invoiceCode}</span>
                </h2>
                <span
                  className={`inline-flex items-center gap-1 rounded-lg border px-2 py-0.5 text-xs font-semibold ${
                    isPaid
                      ? 'border-emerald-200 bg-emerald-50 text-emerald-800 dark:border-emerald-500/40 dark:bg-emerald-950/40 dark:text-emerald-300'
                      : 'border-amber-200 bg-amber-50 text-amber-800 dark:border-amber-500/40 dark:bg-amber-950/40 dark:text-amber-300'
                  }`}
                >
                  {isPaid && <CheckCircle2 className="h-3 w-3" />}
                  {INVOICE_PAYMENT_STATUS_LABELS[paymentStatus]}
                </span>
              </div>
              <p className="mt-0.5 text-xs text-zinc-500 dark:text-zinc-400">
                Cập nhật bởi {invoice.updatedByName} · {formatTimestampVi(invoice.updatedAt)}
              </p>
            </div>
          </div>

          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              onClick={() => void handleShare()}
              className="inline-flex h-9 items-center gap-1.5 rounded-lg border border-zinc-200 dark:border-zinc-700 bg-white dark:bg-zinc-800 hover:bg-zinc-50 dark:hover:bg-zinc-700 px-3 text-xs font-medium text-zinc-800 dark:text-zinc-100 transition-colors cursor-pointer"
            >
              <Share2 className="h-3.5 w-3.5" />
              Sao chép / Chia sẻ
            </button>
            {canEdit && (
              <button
                type="button"
                onClick={onOpenEditor}
                className="inline-flex h-9 items-center gap-1.5 rounded-lg border border-orange-200 dark:border-orange-500/40 bg-orange-50 dark:bg-orange-950/40 px-3 text-xs font-semibold text-orange-700 dark:text-orange-300 transition-colors hover:bg-orange-100 dark:hover:bg-orange-950/70 cursor-pointer"
              >
                <Edit3 className="h-3.5 w-3.5" />
                Chỉnh sửa
              </button>
            )}
          </div>
        </div>

        <div className="mt-4 space-y-2">
          {invoice.items.length === 0 ? (
            <p className="text-xs italic text-zinc-500 dark:text-zinc-400">
              Không phát sinh linh kiện thay thế.
            </p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead>
                  <tr className="border-b border-zinc-200 dark:border-zinc-800 text-zinc-500 dark:text-zinc-400">
                    <th className="pb-2 font-semibold">Linh kiện / Vật tư</th>
                    <th className="pb-2 text-right font-semibold">SL</th>
                    <th className="pb-2 text-right font-semibold">Đơn giá</th>
                    <th className="pb-2 text-right font-semibold">Thành tiền</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-zinc-100 dark:divide-zinc-800">
                  {invoice.items.map((item, idx) => (
                    <tr key={`${item.name}-${idx}`}>
                      <td className="py-2 pr-2 font-medium text-zinc-900 dark:text-zinc-100">
                        {item.name}
                        <span className="ml-1 text-[11px] text-zinc-500 dark:text-zinc-400">
                          ({item.unit})
                        </span>
                      </td>
                      <td className="py-2 text-right font-mono-tabular text-zinc-700 dark:text-zinc-300">
                        {item.quantity}
                      </td>
                      <td className="py-2 text-right font-mono-tabular text-zinc-700 dark:text-zinc-300">
                        {formatVnd(item.unitPrice)}
                      </td>
                      <td className="py-2 text-right font-mono-tabular font-semibold text-zinc-900 dark:text-zinc-100">
                        {formatVnd(item.lineTotal)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>

        <div className="mt-4 space-y-1.5 rounded-xl border border-zinc-200 dark:border-zinc-800 bg-zinc-50 dark:bg-zinc-950/60 p-3.5 text-xs">
          <div className="flex justify-between text-zinc-600 dark:text-zinc-400">
            <span>Tiền linh kiện</span>
            <span className="font-mono-tabular font-semibold text-zinc-900 dark:text-zinc-100">
              {formatVnd(invoice.partsSubtotal)}
            </span>
          </div>
          <div className="flex justify-between text-zinc-600 dark:text-zinc-400">
            <span>Phí công sửa chữa</span>
            <span className="font-mono-tabular font-semibold text-zinc-900 dark:text-zinc-100">
              {formatVnd(invoice.laborFee)}
            </span>
          </div>
          <div className="flex justify-between text-zinc-600 dark:text-zinc-400">
            <span>Phương thức thanh toán</span>
            <span className="font-semibold text-zinc-900 dark:text-zinc-100">
              {INVOICE_PAYMENT_METHOD_LABELS[paymentMethod]}
            </span>
          </div>
          <div className="flex justify-between border-t border-zinc-200 dark:border-zinc-800 pt-2 text-sm font-bold text-zinc-900 dark:text-zinc-100">
            <span>TỔNG THANH TOÁN</span>
            <span className="font-mono-tabular text-orange-600 dark:text-orange-400">
              {formatVnd(invoice.totalAmount)}
            </span>
          </div>
        </div>

        {invoice.note && (
          <p className="mt-3 text-xs text-zinc-600 dark:text-zinc-400">
            <strong className="text-zinc-900 dark:text-zinc-200">Ghi chú:</strong> {invoice.note}
          </p>
        )}

        {canMarkPaid && !isPaid && (
          <button
            type="button"
            onClick={() => {
              setSelectedMethod(paymentMethod);
              setPayingModalOpen(true);
            }}
            className="mt-4 inline-flex h-11 w-full items-center justify-center gap-2 rounded-xl bg-emerald-600 hover:bg-emerald-700 px-4 text-xs font-semibold text-white transition-colors cursor-pointer"
          >
            <CreditCard className="h-4 w-4" />
            Xác nhận đã thanh toán
          </button>
        )}
      </section>

      {payingModalOpen && (
        <div
          role="dialog"
          aria-modal="true"
          className="fixed inset-0 z-50 flex items-center justify-center bg-zinc-950/60 p-4 backdrop-blur-[2px]"
        >
          <div className="w-full max-w-md rounded-2xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 shadow-xl overflow-hidden">
            <div className="bg-zinc-950 px-5 py-4 flex items-center justify-between border-b border-zinc-800">
              <div className="flex items-center gap-2.5 min-w-0">
                <div className="w-8 h-8 rounded-lg bg-orange-600 text-white flex items-center justify-center shrink-0">
                  <CreditCard className="w-4 h-4" />
                </div>
                <h3 className="text-sm font-bold text-white truncate">
                  Xác nhận thanh toán #{invoice.invoiceCode}
                </h3>
              </div>
              <button
                type="button"
                onClick={() => setPayingModalOpen(false)}
                className="rounded-lg p-1.5 text-zinc-400 hover:bg-zinc-800 hover:text-white cursor-pointer"
              >
                <X className="h-4 w-4" />
              </button>
            </div>

            <div className="p-5 space-y-4">
              <p className="text-xs text-zinc-600 dark:text-zinc-400">
                Tổng số tiền:{' '}
                <strong className="font-mono-tabular text-sm text-orange-600 dark:text-orange-400">
                  {formatVnd(invoice.totalAmount)}
                </strong>
              </p>

              <div className="space-y-2">
                <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                  Phương thức thanh toán
                </label>
                <div className="grid grid-cols-1 gap-2">
                  {Object.values(InvoicePaymentMethod).map((method) => (
                    <button
                      key={method}
                      type="button"
                      onClick={() => setSelectedMethod(method)}
                      className={`flex h-11 items-center justify-between rounded-xl border px-3.5 text-xs font-semibold transition cursor-pointer ${
                        selectedMethod === method
                          ? 'border-orange-600 bg-orange-50 dark:bg-orange-950/40 text-orange-800 dark:text-orange-200'
                          : 'border-zinc-200 dark:border-zinc-700 bg-white dark:bg-zinc-950 text-zinc-800 dark:text-zinc-200 hover:border-zinc-400 dark:hover:border-zinc-600'
                      }`}
                    >
                      <span>{INVOICE_PAYMENT_METHOD_LABELS[method]}</span>
                    </button>
                  ))}
                </div>
              </div>

              <div className="flex gap-2.5 pt-2">
                <button
                  type="button"
                  disabled={busy}
                  onClick={() => setPayingModalOpen(false)}
                  className="h-11 flex-1 rounded-xl border border-zinc-200 dark:border-zinc-700 bg-white dark:bg-zinc-800 hover:bg-zinc-50 dark:hover:bg-zinc-700 px-4 text-xs font-semibold text-zinc-800 dark:text-zinc-100 cursor-pointer"
                >
                  Hủy
                </button>
                <button
                  type="button"
                  disabled={busy}
                  onClick={() => void handleConfirmPaid()}
                  className="h-11 flex-1 rounded-xl bg-emerald-600 hover:bg-emerald-700 px-4 text-xs font-semibold text-white disabled:opacity-60 cursor-pointer"
                >
                  {busy ? 'Đang lưu...' : 'Xác nhận đã thu'}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </>
  );
};
