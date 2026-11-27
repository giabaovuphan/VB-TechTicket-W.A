import React, { useEffect, useMemo, useState } from 'react';
import { Loader2, Minus, Plus, Receipt, Search, Trash2, X } from 'lucide-react';
import { saveInvoice } from '../services/invoiceService';
import {
  formatVnd,
  InventoryItem,
  INVOICE_PAYMENT_METHOD_LABELS,
  INVOICE_PAYMENT_STATUS_LABELS,
  InvoiceLineItem,
  InvoicePaymentMethod,
  InvoicePaymentStatus,
  parseInvoicePaymentMethod,
  parseInvoicePaymentStatus,
  ProductItem,
  ProductUnits,
  RepairInvoice,
  SupportTicket,
  UserAccount,
  UserRole,
} from '../types/models';
import { toFriendlyVietnameseError } from '../utils/firestoreErrors';
import { useToast } from '../context/ToastContext';

export const InvoiceEditorModal: React.FC<{
  open: boolean;
  ticket: SupportTicket;
  existingInvoice: RepairInvoice | null;
  inventoryItems: InventoryItem[];
  productItems: ProductItem[];
  actor: UserAccount;
  actorRole: UserRole;
  onClose: () => void;
}> = ({
  open,
  ticket,
  existingInvoice,
  inventoryItems,
  productItems,
  actor,
  actorRole,
  onClose,
}) => {
  const { showToast } = useToast();
  const [items, setItems] = useState<InvoiceLineItem[]>([]);
  const [laborFeeStr, setLaborFeeStr] = useState('0');
  const [paymentMethod, setPaymentMethod] = useState<InvoicePaymentMethod>(
    InvoicePaymentMethod.CASH,
  );
  const [paymentStatus, setPaymentStatus] = useState<InvoicePaymentStatus>(
    InvoicePaymentStatus.UNPAID,
  );
  const [note, setNote] = useState('');
  const [pickerTab, setPickerTab] = useState<'inventory' | 'products' | 'custom'>('inventory');
  const [pickerSearch, setPickerSearch] = useState('');

  // Custom entry state
  const [customName, setCustomName] = useState('');
  const [customUnit, setCustomUnit] = useState<string>(ProductUnits[0]);
  const [customPrice, setCustomPrice] = useState('0');
  const [customQty, setCustomQty] = useState('1');

  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    setItems(existingInvoice?.items ? existingInvoice.items.map((i) => ({ ...i })) : []);
    setLaborFeeStr(String(existingInvoice?.laborFee ?? 0));
    setPaymentMethod(
      existingInvoice
        ? parseInvoicePaymentMethod(existingInvoice.paymentMethod)
        : InvoicePaymentMethod.CASH,
    );
    setPaymentStatus(
      existingInvoice
        ? parseInvoicePaymentStatus(existingInvoice.paymentStatus)
        : InvoicePaymentStatus.UNPAID,
    );
    setNote(existingInvoice?.note ?? '');
    setError(null);
  }, [existingInvoice, open]);

  const activeInventory = useMemo(
    () =>
      inventoryItems.filter(
        (item) =>
          item.active &&
          (pickerSearch.trim() === '' ||
            item.name.toLowerCase().includes(pickerSearch.trim().toLowerCase()) ||
            (item.sku ?? '').toLowerCase().includes(pickerSearch.trim().toLowerCase())),
      ),
    [inventoryItems, pickerSearch],
  );

  const activeProducts = useMemo(
    () =>
      productItems.filter(
        (item) =>
          item.active &&
          (pickerSearch.trim() === '' ||
            item.name.toLowerCase().includes(pickerSearch.trim().toLowerCase()) ||
            (item.sku ?? '').toLowerCase().includes(pickerSearch.trim().toLowerCase())),
      ),
    [pickerSearch, productItems],
  );

  const partsSubtotal = useMemo(
    () => items.reduce((sum, item) => sum + item.unitPrice * item.quantity, 0),
    [items],
  );
  const laborFee = Math.max(0, Number(laborFeeStr) || 0);
  const totalAmount = partsSubtotal + laborFee;

  if (!open) return null;

  const addInventoryLine = (inv: InventoryItem) => {
    setItems((prev) => {
      const existingIdx = prev.findIndex((x) => x.inventoryId === inv.id);
      if (existingIdx >= 0) {
        return prev.map((x, idx) =>
          idx === existingIdx
            ? {
                ...x,
                quantity: x.quantity + 1,
                lineTotal: (x.quantity + 1) * x.unitPrice,
              }
            : x,
        );
      }
      return [
        ...prev,
        {
          inventoryId: inv.id,
          productId: inv.productId ?? null,
          name: inv.name,
          unit: inv.unit,
          quantity: 1,
          unitPrice: inv.unitPrice,
          lineTotal: inv.unitPrice,
          customEntry: false,
        },
      ];
    });
  };

  const addProductLine = (prod: ProductItem) => {
    setItems((prev) => {
      const existingIdx = prev.findIndex((x) => x.productId === prod.id && !x.inventoryId);
      if (existingIdx >= 0) {
        return prev.map((x, idx) =>
          idx === existingIdx
            ? {
                ...x,
                quantity: x.quantity + 1,
                lineTotal: (x.quantity + 1) * x.unitPrice,
              }
            : x,
        );
      }
      return [
        ...prev,
        {
          productId: prod.id,
          inventoryId: null,
          name: prod.name,
          unit: prod.unit,
          quantity: 1,
          unitPrice: prod.unitPrice,
          lineTotal: prod.unitPrice,
          customEntry: false,
        },
      ];
    });
  };

  const addCustomLine = () => {
    const clean = customName.trim();
    const qty = Math.max(1, Math.floor(Number(customQty) || 1));
    const price = Math.max(0, Number(customPrice) || 0);
    if (!clean) {
      setError('Vui lòng nhập tên linh kiện/dịch vụ ngoài danh mục.');
      return;
    }
    setItems((prev) => [
      ...prev,
      {
        productId: null,
        inventoryId: null,
        name: clean,
        unit: customUnit,
        quantity: qty,
        unitPrice: price,
        lineTotal: qty * price,
        customEntry: true,
      },
    ]);
    setCustomName('');
    setCustomPrice('0');
    setCustomQty('1');
    setError(null);
  };

  const updateItemQty = (index: number, delta: number) => {
    setItems((prev) =>
      prev
        .map((item, idx) => {
          if (idx !== index) return item;
          const nextQty = item.quantity + delta;
          if (nextQty <= 0) return null;
          return {
            ...item,
            quantity: nextQty,
            lineTotal: nextQty * item.unitPrice,
          };
        })
        .filter((x): x is InvoiceLineItem => x !== null),
    );
  };

  const handleSave = async () => {
    setError(null);
    setSaving(true);
    try {
      await saveInvoice({
        ticket,
        existingInvoice,
        items,
        laborFee,
        paymentMethod,
        paymentStatus,
        note,
        actor,
        actorRole,
      });
      showToast('Đã lưu hóa đơn và cập nhật tồn kho.');
      onClose();
    } catch (err) {
      setError(toFriendlyVietnameseError(err, 'Không thể lưu hóa đơn.'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label="Biên tập hóa đơn sửa chữa"
      className="fixed inset-0 z-50 flex items-end justify-center bg-zinc-950/60 backdrop-blur-[2px] sm:items-center sm:p-4"
    >
      <div className="flex max-h-[94vh] w-full max-w-3xl flex-col overflow-hidden rounded-t-2xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 shadow-xl sm:rounded-2xl">
        {/* Dark Obsidian Modal Header */}
        <div className="bg-zinc-950 px-5 py-4 flex items-center justify-between border-b border-zinc-800 shrink-0">
          <div className="flex items-center gap-3 min-w-0">
            <div className="w-8 h-8 rounded-lg bg-orange-600 text-white flex items-center justify-center shrink-0">
              <Receipt className="w-4 h-4" />
            </div>
            <div className="min-w-0">
              <h2 className="text-sm sm:text-base font-bold text-white truncate">
                Hóa đơn Ticket <span className="font-mono">#{ticket.ticketCode}</span>
              </h2>
              <p className="text-[11px] text-zinc-400 truncate">
                Chọn vật tư từ kho sẽ tự động trừ tồn kho chính xác khi lưu.
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

        <div className="flex-1 space-y-5 overflow-y-auto p-5">
          {error && (
            <div className="rounded-xl border border-red-200 dark:border-red-500/40 bg-red-50 dark:bg-red-950/40 p-3 text-xs font-medium text-red-700 dark:text-red-300">
              {error}
            </div>
          )}

          {/* Picker Tabs */}
          <div className="rounded-xl border border-zinc-200 dark:border-zinc-800 bg-zinc-50 dark:bg-zinc-950/60 p-4">
            <div className="flex flex-wrap gap-2">
              {[
                { id: 'inventory' as const, label: 'Từ kho tồn' },
                { id: 'products' as const, label: 'Từ bảng giá' },
                { id: 'custom' as const, label: 'Nhập ngoài danh mục' },
              ].map((tab) => (
                <button
                  key={tab.id}
                  type="button"
                  onClick={() => setPickerTab(tab.id)}
                  className={`h-9 rounded-lg border px-3 text-xs font-semibold transition-colors cursor-pointer ${
                    pickerTab === tab.id
                      ? 'border-orange-600 bg-orange-600 text-white'
                      : 'border-zinc-200 dark:border-zinc-700 bg-white dark:bg-zinc-900 text-zinc-700 dark:text-zinc-300 hover:border-zinc-400 dark:hover:border-zinc-600'
                  }`}
                >
                  {tab.label}
                </button>
              ))}
            </div>

            {pickerTab !== 'custom' ? (
              <div className="mt-3 space-y-2.5">
                <div className="relative">
                  <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-zinc-400" />
                  <input
                    type="text"
                    value={pickerSearch}
                    onChange={(e) => setPickerSearch(e.target.value)}
                    placeholder="Tìm tên linh kiện hoặc mã SKU..."
                    className="h-10 w-full rounded-xl border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-950 pl-9 pr-3 text-xs text-zinc-900 dark:text-zinc-100 placeholder:text-zinc-400 focus:outline-none focus:border-orange-600"
                  />
                </div>

                <div className="max-h-44 space-y-1.5 overflow-y-auto pr-1">
                  {pickerTab === 'inventory' ? (
                    activeInventory.length === 0 ? (
                      <p className="py-4 text-center text-xs text-zinc-500 dark:text-zinc-400">
                        Không có vật tư kho phù hợp.
                      </p>
                    ) : (
                      activeInventory.map((inv) => (
                        <div
                          key={inv.id}
                          className="flex items-center justify-between gap-2 rounded-xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 px-3 py-2"
                        >
                          <div className="min-w-0">
                            <p className="truncate text-xs font-bold text-zinc-900 dark:text-zinc-100">
                              {inv.name}
                            </p>
                            <p className="text-[11px] text-zinc-500 dark:text-zinc-400">
                              Tồn:{' '}
                              <strong className="font-mono-tabular text-zinc-800 dark:text-zinc-200">
                                {inv.quantityOnHand}
                              </strong>{' '}
                              {inv.unit} · {formatVnd(inv.unitPrice)}
                            </p>
                          </div>
                          <button
                            type="button"
                            disabled={inv.quantityOnHand <= 0}
                            onClick={() => addInventoryLine(inv)}
                            className="inline-flex h-8 shrink-0 items-center gap-1 rounded-lg border border-orange-200 dark:border-orange-500/40 bg-orange-50 dark:bg-orange-950/40 px-2.5 text-xs font-semibold text-orange-700 dark:text-orange-300 hover:bg-orange-100 dark:hover:bg-orange-950/70 disabled:opacity-40 cursor-pointer"
                          >
                            <Plus className="h-3.5 w-3.5" />
                            Thêm
                          </button>
                        </div>
                      ))
                    )
                  ) : activeProducts.length === 0 ? (
                    <p className="py-4 text-center text-xs text-zinc-500 dark:text-zinc-400">
                      Không có sản phẩm/dịch vụ phù hợp.
                    </p>
                  ) : (
                    activeProducts.map((prod) => (
                      <div
                        key={prod.id}
                        className="flex items-center justify-between gap-2 rounded-xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 px-3 py-2"
                      >
                        <div className="min-w-0">
                          <p className="truncate text-xs font-bold text-zinc-900 dark:text-zinc-100">
                            {prod.name}
                          </p>
                          <p className="text-[11px] text-zinc-500 dark:text-zinc-400">
                            {prod.unit} · {formatVnd(prod.unitPrice)}
                          </p>
                        </div>
                        <button
                          type="button"
                          onClick={() => addProductLine(prod)}
                          className="inline-flex h-8 shrink-0 items-center gap-1 rounded-lg border border-orange-200 dark:border-orange-500/40 bg-orange-50 dark:bg-orange-950/40 px-2.5 text-xs font-semibold text-orange-700 dark:text-orange-300 hover:bg-orange-100 dark:hover:bg-orange-950/70 cursor-pointer"
                        >
                          <Plus className="h-3.5 w-3.5" />
                          Thêm
                        </button>
                      </div>
                    ))
                  )}
                </div>
              </div>
            ) : (
              <div className="mt-3 grid grid-cols-1 gap-2.5 sm:grid-cols-12">
                <input
                  type="text"
                  value={customName}
                  onChange={(e) => setCustomName(e.target.value)}
                  placeholder="Tên linh kiện / hạng mục"
                  className="h-10 rounded-xl border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-950 px-3 text-xs text-zinc-900 dark:text-zinc-100 focus:outline-none focus:border-orange-600 sm:col-span-5"
                />
                <select
                  value={customUnit}
                  onChange={(e) => setCustomUnit(e.target.value)}
                  className="h-10 rounded-xl border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-950 px-2.5 text-xs text-zinc-900 dark:text-zinc-100 focus:outline-none focus:border-orange-600 sm:col-span-2"
                >
                  {ProductUnits.map((u) => (
                    <option key={u} value={u}>
                      {u}
                    </option>
                  ))}
                </select>
                <input
                  type="number"
                  min={1}
                  value={customQty}
                  onChange={(e) => setCustomQty(e.target.value)}
                  placeholder="SL"
                  className="h-10 rounded-xl border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-950 px-2.5 text-xs font-mono-tabular text-zinc-900 dark:text-zinc-100 focus:outline-none focus:border-orange-600 sm:col-span-2"
                />
                <input
                  type="number"
                  min={0}
                  step={1000}
                  value={customPrice}
                  onChange={(e) => setCustomPrice(e.target.value)}
                  placeholder="Đơn giá"
                  className="h-10 rounded-xl border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-950 px-2.5 text-xs font-mono-tabular text-zinc-900 dark:text-zinc-100 focus:outline-none focus:border-orange-600 sm:col-span-3"
                />
                <button
                  type="button"
                  onClick={addCustomLine}
                  className="inline-flex h-10 items-center justify-center gap-1 rounded-xl bg-orange-600 hover:bg-orange-700 px-3 text-xs font-semibold text-white cursor-pointer sm:col-span-12"
                >
                  <Plus className="h-3.5 w-3.5" />
                  Thêm dòng ngoài danh mục
                </button>
              </div>
            )}
          </div>

          {/* Selected Items */}
          <div>
            <p className="mb-2 text-xs font-bold uppercase tracking-wider text-zinc-500 dark:text-zinc-400">
              Danh sách hạng mục ({items.length})
            </p>
            {items.length === 0 ? (
              <p className="rounded-xl border border-dashed border-zinc-300 dark:border-zinc-700 p-4 text-center text-xs text-zinc-500 dark:text-zinc-400">
                Chưa chọn linh kiện nào. Có thể chỉ nhập phí công sửa chữa bên dưới.
              </p>
            ) : (
              <div className="space-y-2">
                {items.map((item, index) => (
                  <div
                    key={`${item.name}-${index}`}
                    className="flex flex-wrap items-center justify-between gap-2 rounded-xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-950/60 px-3 py-2.5"
                  >
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-xs font-bold text-zinc-900 dark:text-zinc-100">
                        {item.name}
                      </p>
                      <p className="text-[11px] text-zinc-500 dark:text-zinc-400">
                        {formatVnd(item.unitPrice)} / {item.unit} · Thành tiền:{' '}
                        <strong className="font-mono-tabular text-zinc-900 dark:text-zinc-100">
                          {formatVnd(item.lineTotal)}
                        </strong>
                      </p>
                    </div>
                    <div className="flex items-center gap-1.5">
                      <button
                        type="button"
                        onClick={() => updateItemQty(index, -1)}
                        aria-label="Giảm số lượng"
                        className="flex h-8 w-8 items-center justify-center rounded-lg border border-zinc-200 dark:border-zinc-700 bg-zinc-50 dark:bg-zinc-900 text-zinc-800 dark:text-zinc-200 cursor-pointer"
                      >
                        <Minus className="h-3.5 w-3.5" />
                      </button>
                      <span className="w-7 text-center font-mono-tabular text-xs font-bold text-zinc-900 dark:text-zinc-100">
                        {item.quantity}
                      </span>
                      <button
                        type="button"
                        onClick={() => updateItemQty(index, 1)}
                        aria-label="Tăng số lượng"
                        className="flex h-8 w-8 items-center justify-center rounded-lg border border-zinc-200 dark:border-zinc-700 bg-zinc-50 dark:bg-zinc-900 text-zinc-800 dark:text-zinc-200 cursor-pointer"
                      >
                        <Plus className="h-3.5 w-3.5" />
                      </button>
                      <button
                        type="button"
                        onClick={() => updateItemQty(index, -item.quantity)}
                        aria-label="Xóa dòng"
                        className="ml-1 flex h-8 w-8 items-center justify-center rounded-lg text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-950/40 cursor-pointer"
                      >
                        <Trash2 className="h-4 w-4" />
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* Fees & Payment */}
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
            <div>
              <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                Phí công sửa chữa (đ)
              </label>
              <input
                type="number"
                min={0}
                step={10000}
                value={laborFeeStr}
                onChange={(e) => setLaborFeeStr(e.target.value)}
                className="mt-1.5 h-11 w-full rounded-xl border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-950 px-3 text-sm font-mono-tabular text-zinc-900 dark:text-zinc-100 focus:outline-none focus:border-orange-600"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                Phương thức
              </label>
              <select
                value={paymentMethod}
                onChange={(e) => setPaymentMethod(e.target.value as InvoicePaymentMethod)}
                className="mt-1.5 h-11 w-full rounded-xl border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-950 px-3 text-sm text-zinc-900 dark:text-zinc-100 focus:outline-none focus:border-orange-600"
              >
                {Object.values(InvoicePaymentMethod).map((m) => (
                  <option key={m} value={m}>
                    {INVOICE_PAYMENT_METHOD_LABELS[m]}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                Trạng thái thu tiền
              </label>
              <select
                value={paymentStatus}
                onChange={(e) => setPaymentStatus(e.target.value as InvoicePaymentStatus)}
                className="mt-1.5 h-11 w-full rounded-xl border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-950 px-3 text-sm text-zinc-900 dark:text-zinc-100 focus:outline-none focus:border-orange-600"
              >
                {Object.values(InvoicePaymentStatus).map((st) => (
                  <option key={st} value={st}>
                    {INVOICE_PAYMENT_STATUS_LABELS[st]}
                  </option>
                ))}
              </select>
            </div>
          </div>

          <div>
            <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
              Ghi chú hóa đơn (tùy chọn)
            </label>
            <input
              type="text"
              maxLength={500}
              value={note}
              onChange={(e) => setNote(e.target.value)}
              placeholder="Ghi chú bảo hành, xuất VAT hoặc thông tin bàn giao..."
              className="mt-1.5 h-11 w-full rounded-xl border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-950 px-3 text-sm text-zinc-900 dark:text-zinc-100 placeholder:text-zinc-400 focus:outline-none focus:border-orange-600"
            />
          </div>

          {/* Summary Box */}
          <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-orange-200 dark:border-orange-500/40 bg-orange-50 dark:bg-orange-950/40 px-4 py-3 text-orange-900 dark:text-orange-200">
            <div className="text-xs">
              Tiền linh kiện:{' '}
              <strong className="font-mono-tabular">{formatVnd(partsSubtotal)}</strong> + Công:{' '}
              <strong className="font-mono-tabular">{formatVnd(laborFee)}</strong>
            </div>
            <div className="text-base font-bold font-mono-tabular text-orange-700 dark:text-orange-300">
              Tổng: {formatVnd(totalAmount)}
            </div>
          </div>
        </div>

        <div className="flex gap-2.5 border-t border-zinc-200 dark:border-zinc-800 px-5 py-4 shrink-0">
          <button
            type="button"
            disabled={saving}
            onClick={onClose}
            className="h-11 flex-1 rounded-xl border border-zinc-200 dark:border-zinc-700 bg-white dark:bg-zinc-800 hover:bg-zinc-50 dark:hover:bg-zinc-700 px-4 text-xs font-semibold text-zinc-800 dark:text-zinc-100 cursor-pointer"
          >
            Hủy
          </button>
          <button
            type="button"
            disabled={saving}
            onClick={() => void handleSave()}
            className="inline-flex h-11 flex-1 items-center justify-center gap-2 rounded-xl bg-orange-600 hover:bg-orange-700 disabled:bg-orange-400 px-4 text-xs font-semibold text-white cursor-pointer"
          >
            {saving && <Loader2 className="h-4 w-4 animate-spin" />}
            Lưu hóa đơn
          </button>
        </div>
      </div>
    </div>
  );
};
