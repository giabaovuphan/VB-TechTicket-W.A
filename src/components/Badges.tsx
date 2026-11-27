import React from 'react';
import {
  AlertCircle,
  CheckCircle2,
  Clock,
  Flame,
  Inbox,
  ShieldCheck,
  User as UserIcon,
  Users,
  Wrench,
} from 'lucide-react';
import {
  parseTicketPriority,
  parseTicketStatus,
  parseUserRole,
  STOCK_ALERT_LABELS,
  StockAlertLevel,
  TICKET_PRIORITY_META,
  TICKET_STATUS_META,
  TicketPriority,
  TicketStatus,
  USER_ROLE_LABELS,
  UserRole,
} from '../types/models';

export const StatusBadge: React.FC<{ statusRaw: string; compact?: boolean }> = ({
  statusRaw,
  compact = false,
}) => {
  const status = parseTicketStatus(statusRaw);
  const meta = TICKET_STATUS_META[status];

  const palette: Record<TicketStatus, { cls: string; Icon: React.ComponentType<{ className?: string }> }> = {
    [TicketStatus.OPEN]: {
      cls: 'bg-amber-50 text-amber-800 border-amber-200 dark:bg-amber-950/40 dark:text-amber-300 dark:border-amber-500/40',
      Icon: Inbox,
    },
    [TicketStatus.ACCEPTED]: {
      cls: 'bg-sky-50 text-sky-800 border-sky-200 dark:bg-sky-950/40 dark:text-sky-300 dark:border-sky-500/40',
      Icon: Clock,
    },
    [TicketStatus.IN_PROGRESS]: {
      cls: 'bg-violet-50 text-violet-800 border-violet-200 dark:bg-violet-950/40 dark:text-violet-300 dark:border-violet-500/40',
      Icon: Wrench,
    },
    [TicketStatus.COMPLETED]: {
      cls: 'bg-emerald-50 text-emerald-800 border-emerald-200 dark:bg-emerald-950/40 dark:text-emerald-300 dark:border-emerald-500/40',
      Icon: CheckCircle2,
    },
  };

  const { cls, Icon } = palette[status];
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-lg border px-2.5 py-1 text-xs font-semibold leading-none ${cls}`}
    >
      <Icon className="h-3.5 w-3.5 shrink-0" />
      <span>{compact ? meta.shortLabelVi : meta.labelVi}</span>
    </span>
  );
};

export const PriorityBadge: React.FC<{ priorityRaw: string }> = ({ priorityRaw }) => {
  const priority = parseTicketPriority(priorityRaw);
  const meta = TICKET_PRIORITY_META[priority];

  const palette: Record<TicketPriority, { cls: string; urgent?: boolean }> = {
    [TicketPriority.LOW]: {
      cls: 'bg-zinc-100 text-zinc-700 border-zinc-200 dark:bg-zinc-800 dark:text-zinc-300 dark:border-zinc-700',
    },
    [TicketPriority.MEDIUM]: {
      cls: 'bg-blue-50 text-blue-800 border-blue-200 dark:bg-blue-950/40 dark:text-blue-300 dark:border-blue-500/40',
    },
    [TicketPriority.HIGH]: {
      cls: 'bg-orange-50 text-orange-800 border-orange-200 dark:bg-orange-950/40 dark:text-orange-300 dark:border-orange-500/40',
    },
    [TicketPriority.URGENT]: {
      cls: 'bg-red-50 text-red-700 border-red-200 dark:bg-red-950/40 dark:text-red-300 dark:border-red-500/40',
      urgent: true,
    },
  };

  const { cls, urgent } = palette[priority];
  return (
    <span
      className={`inline-flex items-center gap-1 rounded-lg border px-2 py-1 text-xs font-semibold leading-none ${cls}`}
    >
      {urgent ? <Flame className="h-3.5 w-3.5 shrink-0" /> : null}
      <span>{meta.labelVi}</span>
    </span>
  );
};

export const RoleBadge: React.FC<{ roleRaw: string; short?: boolean }> = ({ roleRaw, short = false }) => {
  const role = parseUserRole(roleRaw);
  const meta = USER_ROLE_LABELS[role];

  const palette: Record<UserRole, { cls: string; Icon: React.ComponentType<{ className?: string }> }> = {
    [UserRole.PENDING]: {
      cls: 'bg-zinc-100 text-zinc-700 border-zinc-200 dark:bg-zinc-800 dark:text-zinc-300 dark:border-zinc-700',
      Icon: Clock,
    },
    [UserRole.USER]: {
      cls: 'bg-sky-50 text-sky-800 border-sky-200 dark:bg-sky-950/40 dark:text-sky-300 dark:border-sky-500/40',
      Icon: UserIcon,
    },
    [UserRole.TECHNICIAN]: {
      cls: 'bg-violet-50 text-violet-800 border-violet-200 dark:bg-violet-950/40 dark:text-violet-300 dark:border-violet-500/40',
      Icon: Wrench,
    },
    [UserRole.TEAM_LEAD]: {
      cls: 'bg-orange-50 text-orange-800 border-orange-200 dark:bg-orange-950/40 dark:text-orange-300 dark:border-orange-500/40',
      Icon: Users,
    },
    [UserRole.ADMIN]: {
      cls: 'bg-emerald-50 text-emerald-800 border-emerald-200 dark:bg-emerald-950/40 dark:text-emerald-300 dark:border-emerald-500/40',
      Icon: ShieldCheck,
    },
  };

  const { cls, Icon } = palette[role];
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-lg border px-2.5 py-1 text-xs font-semibold leading-none ${cls}`}
    >
      <Icon className="h-3.5 w-3.5 shrink-0" />
      <span>{short ? meta.shortLabelVi : meta.labelVi}</span>
    </span>
  );
};

export const StockAlertBadge: React.FC<{ level: StockAlertLevel }> = ({ level }) => {
  const palette: Record<StockAlertLevel, string> = {
    [StockAlertLevel.OK]:
      'bg-emerald-50 text-emerald-800 border-emerald-200 dark:bg-emerald-950/40 dark:text-emerald-300 dark:border-emerald-500/40',
    [StockAlertLevel.LOW]:
      'bg-amber-50 text-amber-800 border-amber-200 dark:bg-amber-950/40 dark:text-amber-300 dark:border-amber-500/40',
    [StockAlertLevel.OUT]:
      'bg-red-50 text-red-700 border-red-200 dark:bg-red-950/40 dark:text-red-300 dark:border-red-500/40',
  };
  return (
    <span
      className={`inline-flex items-center gap-1 rounded-lg border px-2.5 py-1 text-xs font-semibold leading-none ${palette[level]}`}
    >
      {level !== StockAlertLevel.OK ? <AlertCircle className="h-3.5 w-3.5 shrink-0" /> : null}
      <span>{STOCK_ALERT_LABELS[level]}</span>
    </span>
  );
};

const STEPS: TicketStatus[] = [
  TicketStatus.OPEN,
  TicketStatus.ACCEPTED,
  TicketStatus.IN_PROGRESS,
  TicketStatus.COMPLETED,
];

export const StatusProgressStepper: React.FC<{ currentStatusRaw: string }> = ({ currentStatusRaw }) => {
  const current = parseTicketStatus(currentStatusRaw);
  const currentIdx = TICKET_STATUS_META[current].stepIndex;

  return (
    <div className="rounded-2xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 p-4">
      <p className="mb-3 text-xs font-semibold uppercase tracking-wider text-zinc-500 dark:text-zinc-400">
        Tiến trình xử lý
      </p>
      <div className="grid grid-cols-4 gap-2">
        {STEPS.map((step, index) => {
          const isReached = index <= currentIdx;
          const isCurrent = index === currentIdx;
          const meta = TICKET_STATUS_META[step];
          return (
            <div key={step} className="flex flex-col items-center text-center">
              <div className="relative flex w-full items-center justify-center">
                {index > 0 && (
                  <div
                    className={`absolute left-0 right-1/2 top-1/2 h-0.5 -translate-y-1/2 ${
                      index <= currentIdx
                        ? 'bg-orange-600'
                        : 'bg-zinc-200 dark:bg-zinc-800'
                    }`}
                  />
                )}
                {index < STEPS.length - 1 && (
                  <div
                    className={`absolute left-1/2 right-0 top-1/2 h-0.5 -translate-y-1/2 ${
                      index < currentIdx
                        ? 'bg-orange-600'
                        : 'bg-zinc-200 dark:bg-zinc-800'
                    }`}
                  />
                )}
                <div
                  className={`relative z-10 flex h-7 w-7 items-center justify-center rounded-full border font-mono text-xs font-bold tabular-nums transition ${
                    isReached
                      ? 'border-orange-600 bg-orange-600 text-white'
                      : 'border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-900 text-zinc-500 dark:text-zinc-400'
                  } ${isCurrent ? 'ring-4 ring-orange-500/20' : ''}`}
                >
                  {index + 1}
                </div>
              </div>
              <span
                className={`mt-2 text-[11px] leading-tight sm:text-xs ${
                  isCurrent
                    ? 'font-bold text-zinc-900 dark:text-zinc-100'
                    : isReached
                      ? 'font-semibold text-zinc-800 dark:text-zinc-200'
                      : 'font-medium text-zinc-500 dark:text-zinc-400'
                }`}
              >
                {meta.shortLabelVi}
              </span>
            </div>
          );
        })}
      </div>
    </div>
  );
};
