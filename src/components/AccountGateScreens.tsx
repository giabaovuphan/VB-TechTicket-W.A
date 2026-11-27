import React from 'react';
import {
  AlertTriangle,
  Clock,
  Loader2,
  LogOut,
  RefreshCw,
  ShieldAlert,
  UserX,
  Wrench,
} from 'lucide-react';
import { UserAccount } from '../types/models';

const GateShell: React.FC<{
  subtitle: string;
  children: React.ReactNode;
}> = ({ subtitle, children }) => (
  <div className="min-h-[100dvh] pt-safe pb-safe bg-zinc-50 dark:bg-zinc-950 flex items-center justify-center p-4 sm:p-6">
    <div className="w-full max-w-md bg-white dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 rounded-2xl overflow-hidden shadow-sm">
      <div className="bg-zinc-950 px-6 py-4 flex items-center gap-3 border-b border-zinc-800">
        <div className="w-9 h-9 rounded-lg bg-orange-600 text-white flex items-center justify-center shrink-0">
          <Wrench className="w-4 h-4" />
        </div>
        <div className="min-w-0">
          <p className="font-display text-base font-bold tracking-tight text-white leading-tight">
            HCN Support
          </p>
          <p className="text-[11px] text-zinc-400 truncate mt-0.5">{subtitle}</p>
        </div>
      </div>
      <div className="p-6 sm:p-7">{children}</div>
    </div>
  </div>
);

export const LoadingSyncScreen: React.FC<{ title?: string; subtitle?: string }> = ({
  title = 'Đang đồng bộ tài khoản',
  subtitle = 'Đang kiểm tra vai trò và quyền truy cập trên hệ thống HCN Support...',
}) => (
  <GateShell subtitle="Kiểm tra phiên làm việc">
    <div className="py-4 text-center">
      <div className="mx-auto w-12 h-12 rounded-xl bg-orange-50 dark:bg-orange-950/40 border border-orange-200 dark:border-orange-800/50 text-orange-600 dark:text-orange-400 flex items-center justify-center">
        <Loader2 className="h-6 w-6 animate-spin" />
      </div>
      <h1 className="mt-4 text-base font-bold text-zinc-900 dark:text-zinc-100">{title}</h1>
      <p className="mt-1.5 text-xs text-zinc-600 dark:text-zinc-400 leading-relaxed">{subtitle}</p>
    </div>
  </GateShell>
);

export const SyncErrorScreen: React.FC<{
  email?: string | null;
  message: string;
  onRetry: () => void;
  onSignOut: () => void;
}> = ({ email, message, onRetry, onSignOut }) => (
  <GateShell subtitle="Lỗi đồng bộ tài khoản">
    <div className="space-y-4">
      <div className="flex items-start gap-3">
        <div className="w-10 h-10 rounded-xl bg-red-50 dark:bg-red-950/40 border border-red-200 dark:border-red-500/40 text-red-600 dark:text-red-400 flex items-center justify-center shrink-0">
          <AlertTriangle className="h-5 w-5" />
        </div>
        <div className="min-w-0">
          <h1 className="text-base font-bold text-zinc-900 dark:text-zinc-100">
            Không thể đồng bộ hồ sơ
          </h1>
          {email && (
            <p className="mt-0.5 text-xs font-mono text-zinc-500 dark:text-zinc-400 truncate">
              {email}
            </p>
          )}
        </div>
      </div>

      <p className="rounded-xl border border-red-200 dark:border-red-500/40 bg-red-50 dark:bg-red-950/40 p-3.5 text-xs text-red-700 dark:text-red-300 leading-relaxed">
        {message}
      </p>

      <div className="flex flex-col gap-2 sm:flex-row pt-1">
        <button
          type="button"
          onClick={onRetry}
          className="h-11 flex-1 bg-orange-600 hover:bg-orange-700 text-white text-xs font-semibold rounded-xl inline-flex items-center justify-center gap-2 transition-colors cursor-pointer"
        >
          <RefreshCw className="h-4 w-4" />
          Thử lại
        </button>
        <button
          type="button"
          onClick={onSignOut}
          className="h-11 flex-1 bg-white dark:bg-zinc-800 border border-zinc-200 dark:border-zinc-700 hover:bg-zinc-50 dark:hover:bg-zinc-700 text-zinc-800 dark:text-zinc-100 text-xs font-semibold rounded-xl inline-flex items-center justify-center gap-2 transition-colors cursor-pointer"
        >
          <LogOut className="h-4 w-4" />
          Đổi tài khoản
        </button>
      </div>
    </div>
  </GateShell>
);

export const PendingApprovalScreen: React.FC<{
  account: UserAccount;
  onRefresh: () => void;
  onSignOut: () => void;
}> = ({ account, onRefresh, onSignOut }) => (
  <GateShell subtitle="Chờ phê duyệt truy cập">
    <div className="space-y-4">
      <div className="flex items-start gap-3">
        <div className="w-10 h-10 rounded-xl bg-amber-50 dark:bg-amber-950/40 border border-amber-200 dark:border-amber-500/40 text-amber-600 dark:text-amber-400 flex items-center justify-center shrink-0">
          <Clock className="h-5 w-5" />
        </div>
        <div className="min-w-0">
          <h1 className="text-base font-bold text-zinc-900 dark:text-zinc-100">
            Tài khoản đang chờ cấp quyền
          </h1>
          <p className="mt-0.5 text-xs font-mono text-zinc-500 dark:text-zinc-400 truncate">
            {account.email}
          </p>
        </div>
      </div>

      <p className="text-xs leading-relaxed text-zinc-600 dark:text-zinc-400">
        Xin chào <strong className="text-zinc-900 dark:text-zinc-100">{account.displayName}</strong>,
        tài khoản của bạn đã đăng nhập thành công nhưng chưa được Quản trị viên kích hoạt vai trò
        truy cập (Người dùng, Kỹ thuật viên hoặc Trưởng nhóm).
      </p>

      <div className="rounded-xl border border-amber-200 dark:border-amber-500/40 bg-amber-50 dark:bg-amber-950/40 p-3.5 text-xs text-amber-800 dark:text-amber-200 leading-relaxed">
        Vui lòng liên hệ Quản trị viên để phê duyệt tài khoản của bạn, sau đó nhấn{' '}
        <strong>Kiểm tra lại trạng thái</strong>.
      </div>

      <div className="flex flex-col gap-2 sm:flex-row pt-1">
        <button
          type="button"
          onClick={onRefresh}
          className="h-11 flex-1 bg-orange-600 hover:bg-orange-700 text-white text-xs font-semibold rounded-xl inline-flex items-center justify-center gap-2 transition-colors cursor-pointer"
        >
          <RefreshCw className="h-4 w-4" />
          Kiểm tra lại trạng thái
        </button>
        <button
          type="button"
          onClick={onSignOut}
          className="h-11 flex-1 bg-white dark:bg-zinc-800 border border-zinc-200 dark:border-zinc-700 hover:bg-zinc-50 dark:hover:bg-zinc-700 text-zinc-800 dark:text-zinc-100 text-xs font-semibold rounded-xl inline-flex items-center justify-center gap-2 transition-colors cursor-pointer"
        >
          <LogOut className="h-4 w-4" />
          Đăng xuất
        </button>
      </div>
    </div>
  </GateShell>
);

export const RejectedAccountScreen: React.FC<{
  account: UserAccount;
  onSignOut: () => void;
}> = ({ account, onSignOut }) => (
  <GateShell subtitle="Từ chối truy cập">
    <div className="space-y-4">
      <div className="flex items-start gap-3">
        <div className="w-10 h-10 rounded-xl bg-red-50 dark:bg-red-950/40 border border-red-200 dark:border-red-500/40 text-red-600 dark:text-red-400 flex items-center justify-center shrink-0">
          <UserX className="h-5 w-5" />
        </div>
        <div className="min-w-0">
          <h1 className="text-base font-bold text-zinc-900 dark:text-zinc-100">
            Yêu cầu truy cập đã bị từ chối
          </h1>
          <p className="mt-0.5 text-xs font-mono text-zinc-500 dark:text-zinc-400 truncate">
            {account.email}
          </p>
        </div>
      </div>

      <p className="text-xs leading-relaxed text-zinc-600 dark:text-zinc-400">
        Quản trị viên đã từ chối yêu cầu kích hoạt cho tài khoản này.
      </p>

      {account.rejectionReason && (
        <div className="rounded-xl border border-red-200 dark:border-red-500/40 bg-red-50 dark:bg-red-950/40 p-3.5 text-xs text-red-700 dark:text-red-300">
          <strong>Lý do:</strong> {account.rejectionReason}
        </div>
      )}

      <button
        type="button"
        onClick={onSignOut}
        className="h-11 w-full bg-white dark:bg-zinc-800 border border-zinc-200 dark:border-zinc-700 hover:bg-zinc-50 dark:hover:bg-zinc-700 text-zinc-800 dark:text-zinc-100 text-xs font-semibold rounded-xl inline-flex items-center justify-center gap-2 transition-colors cursor-pointer"
      >
        <LogOut className="h-4 w-4" />
        Đăng xuất / Đổi tài khoản
      </button>
    </div>
  </GateShell>
);

export const DisabledAccountScreen: React.FC<{
  account: UserAccount;
  onRefresh: () => void;
  onSignOut: () => void;
}> = ({ account, onRefresh, onSignOut }) => (
  <GateShell subtitle="Tài khoản tạm khóa">
    <div className="space-y-4">
      <div className="flex items-start gap-3">
        <div className="w-10 h-10 rounded-xl bg-amber-50 dark:bg-amber-950/40 border border-amber-200 dark:border-amber-500/40 text-amber-600 dark:text-amber-400 flex items-center justify-center shrink-0">
          <ShieldAlert className="h-5 w-5" />
        </div>
        <div className="min-w-0">
          <h1 className="text-base font-bold text-zinc-900 dark:text-zinc-100">
            Tài khoản tạm thời bị vô hiệu hóa
          </h1>
          <p className="mt-0.5 text-xs font-mono text-zinc-500 dark:text-zinc-400 truncate">
            {account.email}
          </p>
        </div>
      </div>

      <p className="text-xs leading-relaxed text-zinc-600 dark:text-zinc-400">
        Tài khoản của bạn hiện đang bị tạm khóa. Vui lòng liên hệ Quản trị viên để được mở khóa lại
        quyền truy cập.
      </p>

      <div className="flex flex-col gap-2 sm:flex-row pt-1">
        <button
          type="button"
          onClick={onRefresh}
          className="h-11 flex-1 bg-orange-600 hover:bg-orange-700 text-white text-xs font-semibold rounded-xl inline-flex items-center justify-center gap-2 transition-colors cursor-pointer"
        >
          <RefreshCw className="h-4 w-4" />
          Kiểm tra lại
        </button>
        <button
          type="button"
          onClick={onSignOut}
          className="h-11 flex-1 bg-white dark:bg-zinc-800 border border-zinc-200 dark:border-zinc-700 hover:bg-zinc-50 dark:hover:bg-zinc-700 text-zinc-800 dark:text-zinc-100 text-xs font-semibold rounded-xl inline-flex items-center justify-center gap-2 transition-colors cursor-pointer"
        >
          <LogOut className="h-4 w-4" />
          Đăng xuất
        </button>
      </div>
    </div>
  </GateShell>
);
