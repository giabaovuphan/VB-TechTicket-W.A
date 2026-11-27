import React, { useState } from 'react';
import { BellOff, CheckCheck, ChevronRight, CircleDot } from 'lucide-react';
import { StatusBadge } from '../components/Badges';
import { useToast } from '../context/ToastContext';
import { markAllNotificationsRead, markNotificationRead } from '../services/notificationService';
import { formatTimestampVi, TicketNotification } from '../types/models';
import { toFriendlyVietnameseError } from '../utils/firestoreErrors';

export const NotificationsScreen: React.FC<{
  notifications: TicketNotification[];
  unreadCount: number;
  onOpenTicket: (ticketId: string) => void;
}> = ({ notifications, unreadCount, onOpenTicket }) => {
  const { showToast } = useToast();
  const [busyAll, setBusyAll] = useState(false);

  const handleMarkAll = async () => {
    setBusyAll(true);
    try {
      await markAllNotificationsRead(notifications);
      showToast('Đã đánh dấu tất cả thông báo là đã đọc.');
    } catch (err) {
      showToast(toFriendlyVietnameseError(err, 'Không thể cập nhật thông báo.'), true);
    } finally {
      setBusyAll(false);
    }
  };

  const handleClickItem = async (item: TicketNotification) => {
    if (!item.readFlag) {
      void markNotificationRead(item.id).catch(() => {});
    }
    onOpenTicket(item.ticketId);
  };

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 p-4">
        <div>
          <p className="text-sm font-bold text-zinc-900 dark:text-zinc-100">
            Hộp thông báo ({notifications.length})
          </p>
          <p className="text-xs text-zinc-500 dark:text-zinc-400">
            {unreadCount > 0
              ? `Bạn có ${unreadCount} thông báo chưa đọc.`
              : 'Tất cả thông báo đã được đọc.'}
          </p>
        </div>

        {unreadCount > 0 && (
          <button
            type="button"
            disabled={busyAll}
            onClick={() => void handleMarkAll()}
            className="inline-flex h-10 items-center gap-1.5 rounded-xl border border-zinc-200 dark:border-zinc-700 bg-white dark:bg-zinc-800 hover:bg-zinc-50 dark:hover:bg-zinc-700 px-3.5 text-xs font-semibold text-zinc-800 dark:text-zinc-100 transition-colors disabled:opacity-60 cursor-pointer"
          >
            <CheckCheck className="h-4 w-4 text-orange-600 dark:text-orange-400" />
            Đọc tất cả
          </button>
        )}
      </div>

      {notifications.length === 0 ? (
        <div className="rounded-2xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 p-10 text-center">
          <BellOff className="mx-auto h-9 w-9 text-zinc-400" />
          <p className="mt-3 text-sm font-bold text-zinc-900 dark:text-zinc-100">
            Chưa có thông báo nào
          </p>
          <p className="mt-1 text-xs text-zinc-500 dark:text-zinc-400">
            Khi có phân công mới, cập nhật tiến độ hoặc hóa đơn sửa chữa, thông báo sẽ hiện tại đây.
          </p>
        </div>
      ) : (
        <div className="space-y-2.5">
          {notifications.map((item) => (
            <button
              key={item.id}
              type="button"
              onClick={() => void handleClickItem(item)}
              className={`flex w-full items-start justify-between gap-3 rounded-2xl border p-4 text-left transition-colors cursor-pointer ${
                item.readFlag
                  ? 'border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 hover:border-zinc-400 dark:hover:border-zinc-600'
                  : 'border-orange-300 dark:border-orange-500/50 bg-orange-50/70 dark:bg-orange-950/25 hover:border-orange-500'
              }`}
            >
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-2">
                  {!item.readFlag && (
                    <CircleDot className="h-3.5 w-3.5 shrink-0 text-orange-600 dark:text-orange-400" />
                  )}
                  <span className="rounded-md bg-zinc-100 dark:bg-zinc-800 px-2 py-0.5 font-mono-tabular text-xs font-semibold text-zinc-900 dark:text-zinc-100">
                    #{item.ticketCode}
                  </span>
                  <StatusBadge statusRaw={item.newStatus} compact />
                  <span className="font-mono-tabular text-[11px] text-zinc-500 dark:text-zinc-400">
                    {formatTimestampVi(item.createdAt)}
                  </span>
                </div>
                <p className="mt-2 text-sm font-bold text-zinc-900 dark:text-zinc-100">
                  {item.title}
                </p>
                <p className="mt-0.5 text-xs text-zinc-600 dark:text-zinc-400">{item.message}</p>
              </div>
              <ChevronRight className="mt-1 h-4 w-4 shrink-0 text-zinc-400" />
            </button>
          ))}
        </div>
      )}
    </div>
  );
};
