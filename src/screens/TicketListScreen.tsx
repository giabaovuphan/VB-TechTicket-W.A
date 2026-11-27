import React, { useMemo, useState } from 'react';
import {
  Camera,
  CheckCircle2,
  ChevronRight,
  Clock,
  Inbox,
  Search,
  UserCheck,
  Wrench,
} from 'lucide-react';
import { PriorityBadge, StatusBadge } from '../components/Badges';
import {
  formatTimestampVi,
  parseTicketPriority,
  parseTicketStatus,
  SupportTicket,
  TICKET_PRIORITY_META,
  TICKET_STATUS_META,
  TicketPriority,
  TicketStatus,
  UserRole,
} from '../types/models';

export const TicketListScreen: React.FC<{
  tickets: SupportTicket[];
  loading: boolean;
  error: string | null;
  role: UserRole;
  onSelectTicket: (ticketId: string) => void;
  onCreateTicket: () => void;
}> = ({ tickets, loading, error, role, onSelectTicket, onCreateTicket }) => {
  const [statusFilter, setStatusFilter] = useState<TicketStatus | null>(null);
  const [priorityFilter, setPriorityFilter] = useState<TicketPriority | null>(null);
  const [searchQuery, setSearchQuery] = useState('');

  const counts = useMemo(() => {
    const map: Record<TicketStatus, number> = {
      [TicketStatus.OPEN]: 0,
      [TicketStatus.ACCEPTED]: 0,
      [TicketStatus.IN_PROGRESS]: 0,
      [TicketStatus.COMPLETED]: 0,
    };
    for (const t of tickets) {
      map[parseTicketStatus(t.status)] += 1;
    }
    return map;
  }, [tickets]);

  const filteredTickets = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    return tickets.filter((ticket) => {
      if (statusFilter && parseTicketStatus(ticket.status) !== statusFilter) return false;
      if (priorityFilter && parseTicketPriority(ticket.priority) !== priorityFilter) return false;
      if (q) {
        const hay = [
          ticket.ticketCode,
          ticket.title,
          ticket.description,
          ticket.category,
          ticket.creatorName,
          ticket.assigneeName ?? '',
        ]
          .join(' ')
          .toLowerCase();
        if (!hay.includes(q)) return false;
      }
      return true;
    });
  }, [priorityFilter, searchQuery, statusFilter, tickets]);

  const roleHint = useMemo(() => {
    switch (role) {
      case UserRole.USER:
        return 'Theo dõi các phiếu hỗ trợ bạn đã gửi và kết quả nghiệm thu.';
      case UserRole.TECHNICIAN:
        return 'Danh sách sự cố bạn được phân công hoặc tự tạo.';
      case UserRole.TEAM_LEAD:
        return 'Điều phối toàn bộ sự cố kỹ thuật và phân công kỹ thuật viên.';
      case UserRole.ADMIN:
        return 'Giám sát toàn hệ thống ticket, hóa đơn và tiến độ xử lý.';
      default:
        return '';
    }
  }, [role]);

  const kpiCards: Array<{
    status: TicketStatus;
    label: string;
    count: number;
    Icon: React.ComponentType<{ className?: string }>;
    iconCls: string;
    valueCls: string;
  }> = [
    {
      status: TicketStatus.OPEN,
      label: TICKET_STATUS_META[TicketStatus.OPEN].labelVi,
      count: counts[TicketStatus.OPEN],
      Icon: Inbox,
      iconCls: 'bg-amber-50 text-amber-600 border-amber-200 dark:bg-amber-950/40 dark:text-amber-400 dark:border-amber-500/40',
      valueCls: 'text-amber-600 dark:text-amber-400',
    },
    {
      status: TicketStatus.ACCEPTED,
      label: TICKET_STATUS_META[TicketStatus.ACCEPTED].labelVi,
      count: counts[TicketStatus.ACCEPTED],
      Icon: Clock,
      iconCls: 'bg-sky-50 text-sky-600 border-sky-200 dark:bg-sky-950/40 dark:text-sky-400 dark:border-sky-500/40',
      valueCls: 'text-sky-600 dark:text-sky-400',
    },
    {
      status: TicketStatus.IN_PROGRESS,
      label: TICKET_STATUS_META[TicketStatus.IN_PROGRESS].labelVi,
      count: counts[TicketStatus.IN_PROGRESS],
      Icon: Wrench,
      iconCls: 'bg-violet-50 text-violet-600 border-violet-200 dark:bg-violet-950/40 dark:text-violet-400 dark:border-violet-500/40',
      valueCls: 'text-violet-600 dark:text-violet-400',
    },
    {
      status: TicketStatus.COMPLETED,
      label: TICKET_STATUS_META[TicketStatus.COMPLETED].labelVi,
      count: counts[TicketStatus.COMPLETED],
      Icon: CheckCircle2,
      iconCls: 'bg-emerald-50 text-emerald-600 border-emerald-200 dark:bg-emerald-950/40 dark:text-emerald-400 dark:border-emerald-500/40',
      valueCls: 'text-emerald-600 dark:text-emerald-400',
    },
  ];

  return (
    <div className="space-y-4">
      {roleHint && (
        <p className="text-xs sm:text-sm text-zinc-600 dark:text-zinc-400">{roleHint}</p>
      )}

      {/* 4 Interactive Status KPI Cards */}
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        {kpiCards.map((card) => {
          const active = statusFilter === card.status;
          const Icon = card.Icon;
          return (
            <button
              key={card.status}
              type="button"
              onClick={() => setStatusFilter(active ? null : card.status)}
              className={`rounded-2xl border bg-white dark:bg-zinc-900 p-4 text-left transition-colors cursor-pointer ${
                active
                  ? 'border-orange-600 ring-2 ring-orange-500/20'
                  : 'border-zinc-200 dark:border-zinc-800 hover:border-zinc-400 dark:hover:border-zinc-600'
              }`}
            >
              <div className="flex items-center justify-between gap-2">
                <p className="text-xs font-semibold text-zinc-600 dark:text-zinc-400 truncate">
                  {card.label}
                </p>
                <span
                  className={`w-7 h-7 rounded-lg border flex items-center justify-center shrink-0 ${card.iconCls}`}
                >
                  <Icon className="w-3.5 h-3.5" />
                </span>
              </div>
              <p className={`mt-2 font-mono-tabular text-2xl font-bold ${card.valueCls}`}>
                {card.count}
              </p>
            </button>
          );
        })}
      </div>

      {/* Search & Priority Filters */}
      <div className="flex flex-col gap-3 rounded-2xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 p-3.5 sm:p-4 lg:flex-row lg:items-center lg:justify-between">
        <div className="relative flex-1">
          <Search className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-zinc-400" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Tìm theo mã TK, tiêu đề, người gửi, kỹ thuật viên..."
            className="h-10 w-full rounded-xl border border-zinc-300 dark:border-zinc-700 bg-zinc-50 dark:bg-zinc-950 pl-10 pr-3.5 text-sm text-zinc-900 dark:text-zinc-100 placeholder:text-zinc-400 dark:placeholder:text-zinc-500 focus:outline-none focus:border-orange-600"
          />
        </div>

        <div className="flex flex-wrap items-center gap-1.5">
          <button
            type="button"
            onClick={() => setPriorityFilter(null)}
            className={`h-9 rounded-lg border px-3 text-xs font-semibold transition-colors cursor-pointer ${
              priorityFilter === null
                ? 'border-orange-600 bg-orange-600 text-white'
                : 'border-zinc-200 dark:border-zinc-700 bg-zinc-50 dark:bg-zinc-950 text-zinc-700 dark:text-zinc-300 hover:border-zinc-400 dark:hover:border-zinc-600'
            }`}
          >
            Tất cả
          </button>
          {Object.values(TicketPriority).map((p) => {
            const active = priorityFilter === p;
            return (
              <button
                key={p}
                type="button"
                onClick={() => setPriorityFilter(active ? null : p)}
                className={`h-9 rounded-lg border px-3 text-xs font-semibold transition-colors cursor-pointer ${
                  active
                    ? 'border-orange-600 bg-orange-600 text-white'
                    : 'border-zinc-200 dark:border-zinc-700 bg-zinc-50 dark:bg-zinc-950 text-zinc-700 dark:text-zinc-300 hover:border-zinc-400 dark:hover:border-zinc-600'
                }`}
              >
                {TICKET_PRIORITY_META[p].labelVi}
              </button>
            );
          })}
        </div>
      </div>

      {error && (
        <div className="rounded-2xl border border-red-200 dark:border-red-500/40 bg-red-50 dark:bg-red-950/40 p-4 text-xs font-medium text-red-700 dark:text-red-300">
          {error}
        </div>
      )}

      {loading ? (
        <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
          {[1, 2, 3, 4].map((n) => (
            <div
              key={n}
              className="h-40 animate-pulse rounded-2xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900"
            />
          ))}
        </div>
      ) : filteredTickets.length === 0 ? (
        <div className="flex flex-col items-center rounded-2xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 p-8 sm:p-10 text-center">
          <img
            src="/assets/images/img_empty_tickets.jpg"
            alt="Không có phiếu hỗ trợ"
            className="h-40 w-auto rounded-xl border border-zinc-200 dark:border-zinc-800 object-cover shadow-xs"
          />
          <h2 className="mt-5 text-base font-bold text-zinc-900 dark:text-zinc-100">
            {tickets.length === 0
              ? 'Chưa có phiếu hỗ trợ nào'
              : 'Không tìm thấy phiếu phù hợp bộ lọc'}
          </h2>
          <p className="mt-1 max-w-md text-xs text-zinc-500 dark:text-zinc-400 leading-relaxed">
            {tickets.length === 0
              ? 'Nhấn nút Tạo ticket để gửi yêu cầu hỗ trợ kỹ thuật mới.'
              : 'Thử xóa từ khóa tìm kiếm hoặc bỏ chọn trạng thái / mức ưu tiên đang lọc.'}
          </p>
          {(statusFilter || priorityFilter || searchQuery) ? (
            <button
              type="button"
              onClick={() => {
                setStatusFilter(null);
                setPriorityFilter(null);
                setSearchQuery('');
              }}
              className="mt-4 h-10 rounded-xl border border-zinc-200 dark:border-zinc-700 bg-white dark:bg-zinc-800 hover:bg-zinc-50 dark:hover:bg-zinc-700 px-4 text-xs font-semibold text-zinc-800 dark:text-zinc-100 transition-colors cursor-pointer"
            >
              Xóa bộ lọc
            </button>
          ) : (
            <button
              type="button"
              onClick={onCreateTicket}
              className="mt-4 h-10 rounded-xl bg-orange-600 hover:bg-orange-700 px-4 text-xs font-semibold text-white transition-colors cursor-pointer"
            >
              Tạo phiếu hỗ trợ đầu tiên
            </button>
          )}
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
          {filteredTickets.map((ticket) => (
            <button
              key={ticket.id}
              type="button"
              onClick={() => onSelectTicket(ticket.id)}
              className="group flex flex-col justify-between gap-3 rounded-2xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 p-4 text-left transition-colors hover:border-zinc-400 dark:hover:border-zinc-600 cursor-pointer"
            >
              <div>
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <span className="rounded-md bg-zinc-100 dark:bg-zinc-800 px-2 py-0.5 font-mono-tabular text-xs font-semibold text-zinc-900 dark:text-zinc-100">
                      #{ticket.ticketCode}
                    </span>
                    <PriorityBadge priorityRaw={ticket.priority} />
                  </div>
                  <StatusBadge statusRaw={ticket.status} compact />
                </div>

                <h3 className="mt-3 line-clamp-2 text-sm sm:text-base font-bold text-zinc-900 dark:text-zinc-100 group-hover:text-orange-600 dark:group-hover:text-orange-400 transition-colors">
                  {ticket.title}
                </h3>
                <p className="mt-1 line-clamp-2 text-xs leading-relaxed text-zinc-600 dark:text-zinc-400">
                  {ticket.description}
                </p>
              </div>

              <div className="flex flex-wrap items-center justify-between gap-2 border-t border-zinc-100 dark:border-zinc-800 pt-3 text-xs text-zinc-500 dark:text-zinc-400">
                <div className="flex flex-wrap items-center gap-3">
                  <span className="inline-flex items-center gap-1">
                    <Inbox className="h-3.5 w-3.5" />
                    {ticket.category}
                  </span>
                  <span className="inline-flex items-center gap-1">
                    <UserCheck className="h-3.5 w-3.5" />
                    {ticket.assigneeName || 'Chưa gán KTV'}
                  </span>
                  {ticket.defectPhotoBase64 && (
                    <span className="inline-flex items-center gap-1 font-medium text-orange-600 dark:text-orange-400">
                      <Camera className="h-3.5 w-3.5" />
                      Có ảnh
                    </span>
                  )}
                </div>

                <div className="flex items-center gap-1 font-mono-tabular">
                  <Clock className="h-3.5 w-3.5" />
                  <span>{formatTimestampVi(ticket.updatedAt ?? ticket.createdAt)}</span>
                  <ChevronRight className="h-4 w-4 text-zinc-400 group-hover:translate-x-0.5 group-hover:text-orange-600 dark:group-hover:text-orange-400 transition-transform" />
                </div>
              </div>
            </button>
          ))}
        </div>
      )}

      {!loading && filteredTickets.length > 0 && (
        <p className="flex items-center gap-1.5 text-xs text-zinc-500 dark:text-zinc-400">
          <Wrench className="h-3.5 w-3.5" />
          Đang hiển thị{' '}
          <span className="font-mono-tabular font-semibold text-zinc-700 dark:text-zinc-300">
            {filteredTickets.length} / {tickets.length}
          </span>{' '}
          phiếu hỗ trợ.
        </p>
      )}
    </div>
  );
};
