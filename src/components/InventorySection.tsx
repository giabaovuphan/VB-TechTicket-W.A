import React, { useMemo, useState } from 'react';
import {
  AlertTriangle,
  Database,
  Edit2,
  Loader2,
  MapPin,
  Minus,
  Package,
  PackagePlus,
  Plus,
  Search,
  Trash2,
  X,
} from 'lucide-react';
import { StockAlertBadge } from '../components/Badges';
import { useToast } from '../context/ToastContext';
import {
  adjustInventoryStock,
  deleteInventoryItem,
  seedStarterDatabase,
  toggleInventoryActive,
  upsertInventoryItem,
} from '../services/inventoryService';
import {
  formatVnd,
  getAlertLevel,
  InventoryItem,
  ProductUnits,
  StockAlertLevel,
  TicketCategories,
  UserAccount,
  UserRole,
} from '../types/models';
import { toFriendlyVietnameseError } from '../utils/firestoreErrors';

export const InventorySection: React.FC<{
  items: InventoryItem[];
  loading: boolean;
  currentUser: UserAccount;
  currentRole: UserRole;
}> = ({ items, loading, currentUser, currentRole }) => {
  const { showToast } = useToast();
  const isLeadOrAdmin = currentRole === UserRole.TEAM_LEAD || currentRole === UserRole.ADMIN;
  const isAdmin = currentRole === UserRole.ADMIN;

  const [search, setSearch] = useState('');
  const [alertFilter, setAlertFilter] = useState<StockAlertLevel | null>(null);
  const [editorItem, setEditorItem] = useState<InventoryItem | null>(null);
  const [editorOpen, setEditorOpen] = useState(false);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [seeding, setSeeding] = useState(false);

  // Modal fields
  const [name, setName] = useState('');
  const [sku, setSku] = useState('');
  const [category, setCategory] = useState<string>(TicketCategories[0]);
  const [unit, setUnit] = useState<string>(ProductUnits[0]);
  const [unitPrice, setUnitPrice] = useState('0');
  const [qtyOnHand, setQtyOnHand] = useState('0');
  const [minThreshold, setMinThreshold] = useState('2');
  const [location, setLocation] = useState('');
  const [note, setNote] = useState('');
  const [active, setActive] = useState(true);
  const [saving, setSaving] = useState(false);

  const stats = useMemo(() => {
    let low = 0;
    let out = 0;
    for (const item of items) {
      const level = getAlertLevel(item);
      if (level === StockAlertLevel.LOW) low += 1;
      if (level === StockAlertLevel.OUT) out += 1;
    }
    return { total: items.length, low, out };
  }, [items]);

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return items.filter((item) => {
      const level = getAlertLevel(item);
      if (alertFilter && level !== alertFilter) return false;
      if (q) {
        const hay = `${item.name} ${item.sku ?? ''} ${item.category} ${item.location ?? ''}`.toLowerCase();
        if (!hay.includes(q)) return false;
      }
      return true;
    });
  }, [alertFilter, items, search]);

  const openCreateModal = () => {
    setEditorItem(null);
    setName('');
    setSku('');
    setCategory(TicketCategories[0]);
    setUnit(ProductUnits[0]);
    setUnitPrice('0');
    setQtyOnHand('0');
    setMinThreshold('2');
    setLocation('');
    setNote('');
    setActive(true);
    setEditorOpen(true);
  };

  const openEditModal = (item: InventoryItem) => {
    setEditorItem(item);
    setName(item.name);
    setSku(item.sku ?? '');
    setCategory(item.category);
    setUnit(item.unit);
    setUnitPrice(String(item.unitPrice));
    setQtyOnHand(String(item.quantityOnHand));
    setMinThreshold(String(item.minThreshold));
    setLocation(item.location ?? '');
    setNote(item.note ?? '');
    setActive(item.active);
    setEditorOpen(true);
  };

  const handleSaveModal = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    try {
      await upsertInventoryItem({
        id: editorItem?.id,
        name,
        sku,
        category,
        unit,
        unitPrice: Math.max(0, Number(unitPrice) || 0),
        quantityOnHand: Math.max(0, Math.floor(Number(qtyOnHand) || 0)),
        minThreshold: Math.max(0, Math.floor(Number(minThreshold) || 0)),
        location,
        note,
        active,
        actorUid: currentUser.uid,
      });
      showToast(editorItem ? 'Đã cập nhật vật tư.' : 'Đã thêm vật tư vào kho.');
      setEditorOpen(false);
    } catch (err) {
      showToast(toFriendlyVietnameseError(err, 'Không thể lưu vật tư.'), true);
    } finally {
      setSaving(false);
    }
  };

  const handleAdjustStock = async (item: InventoryItem, delta: number) => {
    setBusyId(item.id);
    try {
      await adjustInventoryStock({
        inventoryId: item.id,
        delta,
        actorUid: currentUser.uid,
      });
    } catch (err) {
      showToast(toFriendlyVietnameseError(err, 'Không thể cập nhật số lượng tồn.'), true);
    } finally {
      setBusyId(null);
    }
  };

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
          ? `Đã khởi tạo ${createdCount} mục dữ liệu mẫu thành công.`
          : 'Dữ liệu mẫu đã tồn tại đầy đủ trên hệ thống.',
      );
    } catch (err) {
      showToast(toFriendlyVietnameseError(err, 'Không thể tạo dữ liệu mẫu.'), true);
    } finally {
      setSeeding(false);
    }
  };

  return (
    <div className="space-y-4">
      {/* KPI Cards */}
      <div className="grid grid-cols-3 gap-3">
        <button
          type="button"
          onClick={() => setAlertFilter(null)}
          className={`rounded-2xl border bg-white dark:bg-zinc-900 p-4 text-left transition-colors cursor-pointer ${
            alertFilter === null
              ? 'border-orange-600 ring-2 ring-orange-500/20'
              : 'border-zinc-200 dark:border-zinc-800 hover:border-zinc-400 dark:hover:border-zinc-600'
          }`}
        >
          <p className="text-xs font-semibold text-zinc-500 dark:text-zinc-400">Tổng vật tư</p>
          <p className="mt-1 font-mono-tabular text-2xl font-bold text-zinc-900 dark:text-zinc-100">
            {stats.total}
          </p>
        </button>
        <button
          type="button"
          onClick={() =>
            setAlertFilter(alertFilter === StockAlertLevel.LOW ? null : StockAlertLevel.LOW)
          }
          className={`rounded-2xl border bg-white dark:bg-zinc-900 p-4 text-left transition-colors cursor-pointer ${
            alertFilter === StockAlertLevel.LOW
              ? 'border-amber-500 ring-2 ring-amber-500/20'
              : 'border-zinc-200 dark:border-zinc-800 hover:border-zinc-400 dark:hover:border-zinc-600'
          }`}
        >
          <p className="text-xs font-semibold text-amber-700 dark:text-amber-300">Sắp hết</p>
          <p className="mt-1 font-mono-tabular text-2xl font-bold text-amber-600 dark:text-amber-400">
            {stats.low}
          </p>
        </button>
        <button
          type="button"
          onClick={() =>
            setAlertFilter(alertFilter === StockAlertLevel.OUT ? null : StockAlertLevel.OUT)
          }
          className={`rounded-2xl border bg-white dark:bg-zinc-900 p-4 text-left transition-colors cursor-pointer ${
            alertFilter === StockAlertLevel.OUT
              ? 'border-red-500 ring-2 ring-red-500/20'
              : 'border-zinc-200 dark:border-zinc-800 hover:border-zinc-400 dark:hover:border-zinc-600'
          }`}
        >
          <p className="text-xs font-semibold text-red-700 dark:text-red-300">Hết hàng</p>
          <p className="mt-1 font-mono-tabular text-2xl font-bold text-red-600 dark:text-red-400">
            {stats.out}
          </p>
        </button>
      </div>

      {/* Action Bar */}
      <div className="flex flex-col gap-3 rounded-2xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 p-3.5 sm:p-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="relative flex-1">
          <Search className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-zinc-400" />
          <input
            type="text"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Tìm vật tư theo tên, mã SKU hoặc vị trí kệ..."
            className="h-10 w-full rounded-xl border border-zinc-300 dark:border-zinc-700 bg-zinc-50 dark:bg-zinc-950 pl-10 pr-3 text-sm text-zinc-900 dark:text-zinc-100 placeholder:text-zinc-400 focus:outline-none focus:border-orange-600"
          />
        </div>

        <div className="flex flex-wrap gap-2">
          {isAdmin && items.length === 0 && (
            <button
              type="button"
              disabled={seeding}
              onClick={() => void handleSeedStarter()}
              className="inline-flex h-10 items-center gap-2 rounded-xl border border-zinc-200 dark:border-zinc-700 bg-white dark:bg-zinc-800 hover:bg-zinc-50 dark:hover:bg-zinc-700 px-3.5 text-xs font-semibold text-zinc-800 dark:text-zinc-100 cursor-pointer"
            >
              {seeding ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : (
                <Database className="h-4 w-4 text-orange-600 dark:text-orange-400" />
              )}
              Tạo dữ liệu mẫu
            </button>
          )}
          {isLeadOrAdmin && (
            <button
              type="button"
              onClick={openCreateModal}
              className="inline-flex h-10 items-center gap-2 rounded-xl bg-orange-600 hover:bg-orange-700 px-4 text-xs font-semibold text-white transition-colors cursor-pointer"
            >
              <PackagePlus className="h-4 w-4" />
              Nhập vật tư mới
            </button>
          )}
        </div>
      </div>

      {loading ? (
        <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
          {[1, 2, 3, 4].map((n) => (
            <div
              key={n}
              className="h-32 animate-pulse rounded-2xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900"
            />
          ))}
        </div>
      ) : filtered.length === 0 ? (
        <div className="rounded-2xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 p-8 text-center">
          <AlertTriangle className="mx-auto h-8 w-8 text-zinc-400" />
          <p className="mt-3 text-sm font-bold text-zinc-900 dark:text-zinc-100">
            Không có vật tư nào trong danh sách
          </p>
          <p className="mt-1 text-xs text-zinc-500 dark:text-zinc-400">
            {isLeadOrAdmin
              ? 'Nhấn Nhập vật tư mới để thêm linh kiện vào kho.'
              : 'Kho vật tư hiện đang trống.'}
          </p>
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
          {filtered.map((item) => {
            const level = getAlertLevel(item);
            return (
              <div
                key={item.id}
                className={`flex flex-col justify-between rounded-2xl border bg-white dark:bg-zinc-900 p-4 shadow-sm ${
                  item.active
                    ? 'border-zinc-200 dark:border-zinc-800'
                    : 'border-dashed border-zinc-300 dark:border-zinc-700 opacity-70'
                }`}
              >
                <div>
                  <div className="flex flex-wrap items-start justify-between gap-2">
                    <div className="min-w-0">
                      <h3 className="truncate text-sm font-bold text-zinc-900 dark:text-zinc-100">
                        {item.name}
                      </h3>
                      <p className="mt-0.5 text-xs text-zinc-500 dark:text-zinc-400">
                        {item.sku ? `SKU: ${item.sku} · ` : ''}
                        {item.category}
                      </p>
                    </div>
                    <StockAlertBadge level={level} />
                  </div>

                  <div className="mt-3 flex flex-wrap items-center justify-between gap-2 text-xs">
                    <span className="font-mono-tabular font-bold text-orange-600 dark:text-orange-400">
                      {formatVnd(item.unitPrice)} / {item.unit}
                    </span>
                    {item.location && (
                      <span className="inline-flex items-center gap-1 text-zinc-500 dark:text-zinc-400">
                        <MapPin className="h-3.5 w-3.5" />
                        {item.location}
                      </span>
                    )}
                  </div>
                </div>

                <div className="mt-4 flex flex-wrap items-center justify-between gap-2 border-t border-zinc-100 dark:border-zinc-800 pt-3">
                  <div className="flex items-center gap-2">
                    <span className="text-xs text-zinc-500 dark:text-zinc-400">Tồn kho:</span>
                    {isLeadOrAdmin ? (
                      <div className="flex items-center gap-1.5">
                        <button
                          type="button"
                          disabled={busyId === item.id || item.quantityOnHand <= 0}
                          onClick={() => void handleAdjustStock(item, -1)}
                          aria-label="Giảm 1"
                          className="flex h-8 w-8 items-center justify-center rounded-lg border border-zinc-200 dark:border-zinc-700 bg-zinc-50 dark:bg-zinc-950 text-zinc-800 dark:text-zinc-200 disabled:opacity-40 cursor-pointer"
                        >
                          <Minus className="h-3.5 w-3.5" />
                        </button>
                        <span className="min-w-[36px] text-center font-mono-tabular text-sm font-bold text-zinc-900 dark:text-zinc-100">
                          {item.quantityOnHand}
                        </span>
                        <button
                          type="button"
                          disabled={busyId === item.id}
                          onClick={() => void handleAdjustStock(item, 1)}
                          aria-label="Tăng 1"
                          className="flex h-8 w-8 items-center justify-center rounded-lg border border-zinc-200 dark:border-zinc-700 bg-zinc-50 dark:bg-zinc-950 text-zinc-800 dark:text-zinc-200 disabled:opacity-40 cursor-pointer"
                        >
                          <Plus className="h-3.5 w-3.5" />
                        </button>
                      </div>
                    ) : (
                      <span className="font-mono-tabular text-sm font-bold text-zinc-900 dark:text-zinc-100">
                        {item.quantityOnHand} {item.unit}
                      </span>
                    )}
                  </div>

                  {isLeadOrAdmin && (
                    <div className="flex items-center gap-1.5">
                      <button
                        type="button"
                        onClick={() =>
                          void toggleInventoryActive({
                            inventoryId: item.id,
                            active: !item.active,
                            actorUid: currentUser.uid,
                          })
                        }
                        className="rounded-lg border border-zinc-200 dark:border-zinc-700 bg-zinc-50 dark:bg-zinc-800 px-2.5 py-1 text-xs font-medium text-zinc-700 dark:text-zinc-300 hover:text-zinc-900 dark:hover:text-white cursor-pointer"
                      >
                        {item.active ? 'Đang bật' : 'Đã ẩn'}
                      </button>
                      <button
                        type="button"
                        onClick={() => openEditModal(item)}
                        aria-label="Sửa vật tư"
                        className="flex h-8 w-8 items-center justify-center rounded-lg border border-zinc-200 dark:border-zinc-700 bg-white dark:bg-zinc-800 text-zinc-700 dark:text-zinc-200 hover:border-orange-600 cursor-pointer"
                      >
                        <Edit2 className="h-3.5 w-3.5" />
                      </button>
                      <button
                        type="button"
                        onClick={() => void deleteInventoryItem(item.id)}
                        aria-label="Xóa vật tư"
                        className="flex h-8 w-8 items-center justify-center rounded-lg border border-zinc-200 dark:border-zinc-700 bg-white dark:bg-zinc-800 text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-950/40 cursor-pointer"
                      >
                        <Trash2 className="h-3.5 w-3.5" />
                      </button>
                    </div>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Upsert Modal */}
      {editorOpen && (
        <div
          role="dialog"
          aria-modal="true"
          className="fixed inset-0 z-50 flex items-end justify-center bg-zinc-950/60 backdrop-blur-[2px] sm:items-center sm:p-4"
        >
          <div className="flex max-h-[92vh] w-full max-w-lg flex-col overflow-hidden rounded-t-2xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 shadow-xl sm:rounded-2xl">
            <div className="bg-zinc-950 px-5 py-4 flex items-center justify-between border-b border-zinc-800 shrink-0">
              <div className="flex items-center gap-2.5 min-w-0">
                <div className="w-8 h-8 rounded-lg bg-orange-600 text-white flex items-center justify-center shrink-0">
                  <Package className="w-4 h-4" />
                </div>
                <h2 className="text-sm sm:text-base font-bold text-white truncate">
                  {editorItem ? 'Cập nhật vật tư kho' : 'Thêm vật tư vào kho'}
                </h2>
              </div>
              <button
                type="button"
                onClick={() => setEditorOpen(false)}
                className="rounded-lg p-1.5 text-zinc-400 hover:bg-zinc-800 hover:text-white cursor-pointer"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            <form
              onSubmit={(e) => void handleSaveModal(e)}
              className="flex-1 space-y-3.5 overflow-y-auto p-5"
            >
              <div>
                <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                  Tên linh kiện / vật tư *
                </label>
                <input
                  required
                  type="text"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  className="mt-1 h-10 w-full rounded-xl border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-950 px-3 text-sm text-zinc-900 dark:text-zinc-100 focus:outline-none focus:border-orange-600"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                    Mã SKU
                  </label>
                  <input
                    type="text"
                    value={sku}
                    onChange={(e) => setSku(e.target.value)}
                    className="mt-1 h-10 w-full rounded-xl border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-950 px-3 text-sm text-zinc-900 dark:text-zinc-100 focus:outline-none focus:border-orange-600"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                    Đơn vị
                  </label>
                  <select
                    value={unit}
                    onChange={(e) => setUnit(e.target.value)}
                    className="mt-1 h-10 w-full rounded-xl border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-950 px-3 text-sm text-zinc-900 dark:text-zinc-100 focus:outline-none focus:border-orange-600"
                  >
                    {ProductUnits.map((u) => (
                      <option key={u} value={u}>
                        {u}
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                    Danh mục
                  </label>
                  <select
                    value={category}
                    onChange={(e) => setCategory(e.target.value)}
                    className="mt-1 h-10 w-full rounded-xl border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-950 px-3 text-sm text-zinc-900 dark:text-zinc-100 focus:outline-none focus:border-orange-600"
                  >
                    {TicketCategories.map((c) => (
                      <option key={c} value={c}>
                        {c}
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                    Đơn giá (đ)
                  </label>
                  <input
                    type="number"
                    min={0}
                    step={1000}
                    value={unitPrice}
                    onChange={(e) => setUnitPrice(e.target.value)}
                    className="mt-1 h-10 w-full rounded-xl border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-950 px-3 text-sm font-mono-tabular text-zinc-900 dark:text-zinc-100 focus:outline-none focus:border-orange-600"
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                    Số lượng tồn
                  </label>
                  <input
                    type="number"
                    min={0}
                    value={qtyOnHand}
                    onChange={(e) => setQtyOnHand(e.target.value)}
                    className="mt-1 h-10 w-full rounded-xl border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-950 px-3 text-sm font-mono-tabular text-zinc-900 dark:text-zinc-100 focus:outline-none focus:border-orange-600"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                    Ngưỡng cảnh báo sắp hết
                  </label>
                  <input
                    type="number"
                    min={0}
                    value={minThreshold}
                    onChange={(e) => setMinThreshold(e.target.value)}
                    className="mt-1 h-10 w-full rounded-xl border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-950 px-3 text-sm font-mono-tabular text-zinc-900 dark:text-zinc-100 focus:outline-none focus:border-orange-600"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                  Vị trí lưu kho
                </label>
                <input
                  type="text"
                  value={location}
                  onChange={(e) => setLocation(e.target.value)}
                  placeholder="VD: Kệ A1 - Kho kỹ thuật"
                  className="mt-1 h-10 w-full rounded-xl border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-950 px-3 text-sm text-zinc-900 dark:text-zinc-100 focus:outline-none focus:border-orange-600"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                  Ghi chú
                </label>
                <input
                  type="text"
                  value={note}
                  onChange={(e) => setNote(e.target.value)}
                  className="mt-1 h-10 w-full rounded-xl border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-950 px-3 text-sm text-zinc-900 dark:text-zinc-100 focus:outline-none focus:border-orange-600"
                />
              </div>

              <label className="flex items-center gap-2 text-xs font-semibold text-zinc-800 dark:text-zinc-200 cursor-pointer">
                <input
                  type="checkbox"
                  checked={active}
                  onChange={(e) => setActive(e.target.checked)}
                  className="h-4 w-4 accent-orange-600"
                />
                Hiển thị để kỹ thuật viên chọn vào hóa đơn
              </label>

              <div className="flex gap-2.5 pt-2 border-t border-zinc-200 dark:border-zinc-800">
                <button
                  type="button"
                  disabled={saving}
                  onClick={() => setEditorOpen(false)}
                  className="h-11 flex-1 rounded-xl border border-zinc-200 dark:border-zinc-700 bg-white dark:bg-zinc-800 hover:bg-zinc-50 dark:hover:bg-zinc-700 px-4 text-xs font-semibold text-zinc-800 dark:text-zinc-100 cursor-pointer"
                >
                  Hủy
                </button>
                <button
                  type="submit"
                  disabled={saving}
                  className="h-11 flex-1 rounded-xl bg-orange-600 hover:bg-orange-700 disabled:bg-orange-400 px-4 text-xs font-semibold text-white cursor-pointer"
                >
                  {saving ? 'Đang lưu...' : 'Lưu vật tư'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
