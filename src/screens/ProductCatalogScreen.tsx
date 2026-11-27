import React, { useMemo, useState } from 'react';
import { Edit2, Package, Plus, Search, Tag, Trash2, X } from 'lucide-react';
import { InventorySection } from '../components/InventorySection';
import { useToast } from '../context/ToastContext';
import { deleteProduct, toggleProductActive, upsertProduct } from '../services/productService';
import {
  formatVnd,
  InventoryItem,
  ProductItem,
  ProductUnits,
  TicketCategories,
  UserAccount,
  UserRole,
} from '../types/models';
import { toFriendlyVietnameseError } from '../utils/firestoreErrors';

export const ProductCatalogScreen: React.FC<{
  inventoryItems: InventoryItem[];
  inventoryLoading: boolean;
  productItems: ProductItem[];
  productsLoading: boolean;
  currentUser: UserAccount;
  currentRole: UserRole;
}> = ({
  inventoryItems,
  inventoryLoading,
  productItems,
  productsLoading,
  currentUser,
  currentRole,
}) => {
  const { showToast } = useToast();
  const isLeadOrAdmin = currentRole === UserRole.TEAM_LEAD || currentRole === UserRole.ADMIN;

  const [tab, setTab] = useState<'inventory' | 'products'>('inventory');
  const [search, setSearch] = useState('');
  const [editorProduct, setEditorProduct] = useState<ProductItem | null>(null);
  const [editorOpen, setEditorOpen] = useState(false);

  const [name, setName] = useState('');
  const [sku, setSku] = useState('');
  const [category, setCategory] = useState<string>(TicketCategories[0]);
  const [unit, setUnit] = useState<string>(ProductUnits[0]);
  const [unitPrice, setUnitPrice] = useState('0');
  const [note, setNote] = useState('');
  const [active, setActive] = useState(true);
  const [saving, setSaving] = useState(false);

  const filteredProducts = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return productItems;
    return productItems.filter((p) =>
      `${p.name} ${p.sku ?? ''} ${p.category}`.toLowerCase().includes(q),
    );
  }, [productItems, search]);

  const openCreateModal = () => {
    setEditorProduct(null);
    setName('');
    setSku('');
    setCategory(TicketCategories[0]);
    setUnit(ProductUnits[0]);
    setUnitPrice('0');
    setNote('');
    setActive(true);
    setEditorOpen(true);
  };

  const openEditModal = (prod: ProductItem) => {
    setEditorProduct(prod);
    setName(prod.name);
    setSku(prod.sku ?? '');
    setCategory(prod.category);
    setUnit(prod.unit);
    setUnitPrice(String(prod.unitPrice));
    setNote(prod.note ?? '');
    setActive(prod.active);
    setEditorOpen(true);
  };

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    try {
      await upsertProduct({
        id: editorProduct?.id,
        name,
        sku,
        category,
        unit,
        unitPrice: Math.max(0, Number(unitPrice) || 0),
        note,
        active,
        actorUid: currentUser.uid,
      });
      showToast(editorProduct ? 'Đã cập nhật bảng giá.' : 'Đã thêm sản phẩm/dịch vụ mới.');
      setEditorOpen(false);
    } catch (err) {
      showToast(toFriendlyVietnameseError(err, 'Không thể lưu sản phẩm.'), true);
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="space-y-4">
      {/* Segment Switch */}
      <div className="inline-flex w-full max-w-md rounded-xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 p-1">
        <button
          type="button"
          onClick={() => setTab('inventory')}
          className={`flex h-10 flex-1 items-center justify-center gap-2 rounded-lg px-4 text-xs font-semibold transition-colors cursor-pointer sm:text-sm ${
            tab === 'inventory'
              ? 'bg-orange-600 text-white'
              : 'text-zinc-600 dark:text-zinc-400 hover:text-zinc-900 dark:hover:text-zinc-100'
          }`}
        >
          <Package className="h-4 w-4" />
          Kho tồn ({inventoryItems.length})
        </button>
        <button
          type="button"
          onClick={() => setTab('products')}
          className={`flex h-10 flex-1 items-center justify-center gap-2 rounded-lg px-4 text-xs font-semibold transition-colors cursor-pointer sm:text-sm ${
            tab === 'products'
              ? 'bg-orange-600 text-white'
              : 'text-zinc-600 dark:text-zinc-400 hover:text-zinc-900 dark:hover:text-zinc-100'
          }`}
        >
          <Tag className="h-4 w-4" />
          Bảng giá ({productItems.length})
        </button>
      </div>

      {tab === 'inventory' ? (
        <InventorySection
          items={inventoryItems}
          loading={inventoryLoading}
          currentUser={currentUser}
          currentRole={currentRole}
        />
      ) : (
        <div className="space-y-4">
          <div className="flex flex-col gap-3 rounded-2xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 p-3.5 sm:p-4 sm:flex-row sm:items-center sm:justify-between">
            <div className="relative flex-1">
              <Search className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-zinc-400" />
              <input
                type="text"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder="Tìm sản phẩm, linh kiện hoặc dịch vụ..."
                className="h-10 w-full rounded-xl border border-zinc-300 dark:border-zinc-700 bg-zinc-50 dark:bg-zinc-950 pl-10 pr-3 text-sm text-zinc-900 dark:text-zinc-100 placeholder:text-zinc-400 focus:outline-none focus:border-orange-600"
              />
            </div>

            {isLeadOrAdmin && (
              <button
                type="button"
                onClick={openCreateModal}
                className="inline-flex h-10 items-center gap-2 rounded-xl bg-orange-600 hover:bg-orange-700 px-4 text-xs font-semibold text-white transition-colors cursor-pointer"
              >
                <Plus className="h-4 w-4" />
                Thêm vào bảng giá
              </button>
            )}
          </div>

          {productsLoading ? (
            <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
              {[1, 2, 3].map((n) => (
                <div
                  key={n}
                  className="h-28 animate-pulse rounded-2xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900"
                />
              ))}
            </div>
          ) : filteredProducts.length === 0 ? (
            <div className="rounded-2xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 p-8 text-center text-xs text-zinc-500 dark:text-zinc-400">
              Chưa có mục nào trong bảng giá.
            </div>
          ) : (
            <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
              {filteredProducts.map((prod) => (
                <div
                  key={prod.id}
                  className={`flex flex-col justify-between rounded-2xl border bg-white dark:bg-zinc-900 p-4 shadow-sm ${
                    prod.active
                      ? 'border-zinc-200 dark:border-zinc-800'
                      : 'border-dashed border-zinc-300 dark:border-zinc-700 opacity-70'
                  }`}
                >
                  <div>
                    <div className="flex items-start justify-between gap-2">
                      <div className="min-w-0">
                        <h3 className="text-sm font-bold text-zinc-900 dark:text-zinc-100 truncate">
                          {prod.name}
                        </h3>
                        <p className="mt-0.5 text-xs text-zinc-500 dark:text-zinc-400">
                          {prod.sku ? `SKU: ${prod.sku} · ` : ''}
                          {prod.category}
                        </p>
                      </div>
                      <span className="font-mono-tabular text-sm font-bold text-orange-600 dark:text-orange-400 shrink-0">
                        {formatVnd(prod.unitPrice)} / {prod.unit}
                      </span>
                    </div>
                    {prod.note && (
                      <p className="mt-2 text-xs text-zinc-600 dark:text-zinc-400">{prod.note}</p>
                    )}
                  </div>

                  {isLeadOrAdmin && (
                    <div className="mt-3 flex items-center justify-end gap-1.5 border-t border-zinc-100 dark:border-zinc-800 pt-3">
                      <button
                        type="button"
                        onClick={() =>
                          void toggleProductActive({
                            productId: prod.id,
                            active: !prod.active,
                          })
                        }
                        className="rounded-lg border border-zinc-200 dark:border-zinc-700 bg-zinc-50 dark:bg-zinc-800 px-2.5 py-1 text-xs font-medium text-zinc-700 dark:text-zinc-300 cursor-pointer"
                      >
                        {prod.active ? 'Đang áp dụng' : 'Đã ẩn'}
                      </button>
                      <button
                        type="button"
                        onClick={() => openEditModal(prod)}
                        aria-label="Sửa bảng giá"
                        className="flex h-8 w-8 items-center justify-center rounded-lg border border-zinc-200 dark:border-zinc-700 bg-white dark:bg-zinc-800 text-zinc-700 dark:text-zinc-200 hover:border-orange-600 cursor-pointer"
                      >
                        <Edit2 className="h-3.5 w-3.5" />
                      </button>
                      <button
                        type="button"
                        onClick={() => void deleteProduct(prod.id)}
                        aria-label="Xóa khỏi bảng giá"
                        className="flex h-8 w-8 items-center justify-center rounded-lg border border-zinc-200 dark:border-zinc-700 bg-white dark:bg-zinc-800 text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-950/40 cursor-pointer"
                      >
                        <Trash2 className="h-3.5 w-3.5" />
                      </button>
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Product Upsert Modal */}
      {editorOpen && (
        <div
          role="dialog"
          aria-modal="true"
          className="fixed inset-0 z-50 flex items-end justify-center bg-zinc-950/60 backdrop-blur-[2px] sm:items-center sm:p-4"
        >
          <div className="flex max-h-[90vh] w-full max-w-lg flex-col overflow-hidden rounded-t-2xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 shadow-xl sm:rounded-2xl">
            <div className="bg-zinc-950 px-5 py-4 flex items-center justify-between border-b border-zinc-800 shrink-0">
              <div className="flex items-center gap-2.5 min-w-0">
                <div className="w-8 h-8 rounded-lg bg-orange-600 text-white flex items-center justify-center shrink-0">
                  <Tag className="w-4 h-4" />
                </div>
                <h2 className="text-sm sm:text-base font-bold text-white truncate">
                  {editorProduct ? 'Chỉnh sửa bảng giá' : 'Thêm sản phẩm / dịch vụ'}
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

            <form onSubmit={(e) => void handleSave(e)} className="space-y-3.5 overflow-y-auto p-5">
              <div>
                <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                  Tên sản phẩm / dịch vụ *
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
                    Đơn giá niêm yết (đ)
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
                Hiển thị trên bảng giá hóa đơn
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
                  {saving ? 'Đang lưu...' : 'Lưu bảng giá'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
