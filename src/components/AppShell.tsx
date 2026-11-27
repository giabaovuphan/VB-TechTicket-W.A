import React from 'react';
import {
  BarChart3,
  Bell,
  LogOut,
  Monitor,
  Moon,
  Package,
  Plus,
  Shield,
  Sun,
  Ticket,
  Wrench,
} from 'lucide-react';
import { ThemeMode, useTheme } from '../context/ThemeContext';
import { USER_ROLE_LABELS, UserAccount, UserRole } from '../types/models';

export type AppRoute =
  | 'tickets'
  | 'catalog'
  | 'notifications'
  | 'reports'
  | 'admin';

interface NavTabItem {
  id: AppRoute;
  label: string;
  shortLabel: string;
  Icon: React.ComponentType<{ className?: string }>;
  badge?: number;
}

const THEME_OPTIONS: Array<{
  mode: ThemeMode;
  label: string;
  Icon: React.ComponentType<{ className?: string }>;
}> = [
  { mode: 'light', label: 'Sáng', Icon: Sun },
  { mode: 'dark', label: 'Tối', Icon: Moon },
  { mode: 'system', label: 'Thiết bị', Icon: Monitor },
];

export const AppShell: React.FC<{
  account: UserAccount;
  role: UserRole;
  activeRoute: AppRoute;
  onNavigate: (route: AppRoute) => void;
  unreadNotifications: number;
  onCreateTicket: () => void;
  onSignOut: () => void;
  pageTitle: string;
  pageSubtitle?: string;
  showCreateButton?: boolean;
  children: React.ReactNode;
}> = ({
  account,
  role,
  activeRoute,
  onNavigate,
  unreadNotifications,
  onCreateTicket,
  onSignOut,
  pageTitle,
  pageSubtitle,
  showCreateButton = true,
  children,
}) => {
  const { isDark, themeMode, setThemeMode, toggleTheme } = useTheme();

  const isStaff =
    role === UserRole.TECHNICIAN || role === UserRole.TEAM_LEAD || role === UserRole.ADMIN;
  const isLeadOrAdmin = role === UserRole.TEAM_LEAD || role === UserRole.ADMIN;
  const isAdmin = role === UserRole.ADMIN;

  const navItems: NavTabItem[] = [
    { id: 'tickets', label: 'Phiếu hỗ trợ', shortLabel: 'Ticket', Icon: Ticket },
    ...(isStaff
      ? [{ id: 'catalog' as AppRoute, label: 'Kho & Bảng giá', shortLabel: 'Kho/Giá', Icon: Package }]
      : []),
    {
      id: 'notifications',
      label: 'Thông báo',
      shortLabel: 'Thông báo',
      Icon: Bell,
      badge: unreadNotifications > 0 ? unreadNotifications : undefined,
    },
    ...(isLeadOrAdmin
      ? [{ id: 'reports' as AppRoute, label: 'Báo cáo thống kê', shortLabel: 'Báo cáo', Icon: BarChart3 }]
      : []),
    ...(isAdmin
      ? [{ id: 'admin' as AppRoute, label: 'Phân quyền hệ thống', shortLabel: 'Quyền', Icon: Shield }]
      : []),
  ];

  const roleMeta = USER_ROLE_LABELS[role];

  return (
    <div className="min-h-[100dvh] bg-zinc-50 dark:bg-zinc-950 text-zinc-900 dark:text-zinc-100 flex">
      {/* Desktop Left Sidebar — Dark Obsidian Signature */}
      <aside className="hidden lg:flex w-64 shrink-0 bg-zinc-950 text-white border-r border-zinc-800 flex-col justify-between sticky top-0 h-[100dvh]">
        <div className="flex flex-col min-h-0 flex-1">
          <div className="h-14 px-5 border-b border-zinc-800 flex items-center gap-2.5 shrink-0">
            <div className="w-8 h-8 rounded-lg bg-orange-600 text-white flex items-center justify-center shrink-0">
              <Wrench className="w-4 h-4" />
            </div>
            <div className="min-w-0">
              <p className="font-display text-base font-bold tracking-tight text-white truncate leading-tight">
                HCN Support
              </p>
            </div>
          </div>

          <nav aria-label="Điều hướng chính" className="flex-1 overflow-y-auto p-3 space-y-1">
            {navItems.map((item) => {
              const Icon = item.Icon;
              const active = activeRoute === item.id;
              return (
                <button
                  key={item.id}
                  type="button"
                  onClick={() => onNavigate(item.id)}
                  className={`w-full h-10 px-3 rounded-xl flex items-center justify-between text-sm transition-colors cursor-pointer ${
                    active
                      ? 'bg-orange-600 text-white font-semibold'
                      : 'font-medium text-zinc-300 hover:bg-zinc-900 hover:text-white'
                  }`}
                >
                  <span className="flex items-center gap-2.5 min-w-0">
                    <Icon className="w-4 h-4 shrink-0" />
                    <span className="truncate">{item.label}</span>
                  </span>
                  {item.badge ? (
                    <span
                      className={`text-xs font-mono font-semibold tabular-nums px-1.5 py-0.5 rounded-md ${
                        active ? 'bg-white text-orange-700' : 'bg-orange-600 text-white'
                      }`}
                    >
                      {item.badge > 99 ? '99+' : item.badge}
                    </span>
                  ) : null}
                </button>
              );
            })}
          </nav>
        </div>

        <div className="p-3.5 border-t border-zinc-800 space-y-3 shrink-0">
          <div className="px-2.5 py-2 rounded-xl bg-zinc-900 border border-zinc-800">
            <p className="text-xs font-semibold text-white truncate">{account.displayName}</p>
            <p className="text-[11px] text-zinc-400 truncate mt-0.5">{account.email}</p>
            <p className="mt-1 flex items-center gap-1.5 text-[11px]">
              <span className="text-orange-400 font-medium">{roleMeta.shortLabelVi}</span>
              {account.department ? (
                <span className="font-mono text-zinc-400 truncate">· {account.department}</span>
              ) : null}
            </p>
          </div>

          <div
            role="group"
            aria-label="Chế độ hiển thị"
            className="grid grid-cols-3 gap-1 p-1 rounded-xl bg-zinc-900 border border-zinc-800"
          >
            {THEME_OPTIONS.map((opt) => {
              const Icon = opt.Icon;
              const selected = themeMode === opt.mode;
              return (
                <button
                  key={opt.mode}
                  type="button"
                  onClick={() => setThemeMode(opt.mode)}
                  className={`h-8 rounded-lg text-[11px] inline-flex items-center justify-center gap-1 transition-colors cursor-pointer ${
                    selected
                      ? 'bg-orange-600 text-white font-semibold'
                      : 'text-zinc-400 hover:text-zinc-100 hover:bg-zinc-800/70 font-medium'
                  }`}
                >
                  <Icon className="w-3.5 h-3.5" />
                  <span>{opt.label}</span>
                </button>
              );
            })}
          </div>

          <button
            type="button"
            onClick={onSignOut}
            aria-label="Đăng xuất"
            className="w-full h-9 px-3 rounded-xl border border-zinc-800 hover:bg-zinc-900 text-zinc-200 text-xs font-medium inline-flex items-center justify-center gap-2 transition-colors cursor-pointer"
          >
            <LogOut className="w-3.5 h-3.5" />
            <span>Đăng xuất</span>
          </button>
        </div>
      </aside>

      {/* Main Column */}
      <div className="flex-1 flex flex-col min-w-0">
        {/* Sticky Dark Top Header */}
        <header className="bg-zinc-950 text-white border-b border-zinc-800 pt-safe sticky top-0 z-30">
          <div className="h-14 px-3 sm:px-4 lg:px-6 flex items-center justify-between gap-2 sm:gap-4 max-w-[1600px] w-full mx-auto">
            <div className="flex items-center gap-2.5 min-w-0">
              <div className="lg:hidden w-7 h-7 rounded-lg bg-orange-600 text-white flex items-center justify-center shrink-0">
                <Wrench className="w-4 h-4" />
              </div>
              <div className="min-w-0 flex items-baseline gap-3">
                <h1 className="text-sm lg:text-base font-bold text-white truncate">
                  {pageTitle}
                </h1>
                {pageSubtitle && (
                  <p className="hidden xl:block truncate text-xs text-zinc-400">
                    {pageSubtitle}
                  </p>
                )}
              </div>
            </div>

            <div className="flex items-center gap-1.5 sm:gap-2 shrink-0">
              {showCreateButton && (
                <button
                  type="button"
                  onClick={onCreateTicket}
                  className="min-h-[44px] h-11 lg:h-9 lg:min-h-0 px-3 sm:px-3.5 bg-orange-600 hover:bg-orange-700 text-white text-xs font-semibold rounded-xl inline-flex items-center gap-1.5 whitespace-nowrap cursor-pointer transition-colors touch-manipulation"
                >
                  <Plus className="w-4 h-4" />
                  <span>Tạo ticket</span>
                </button>
              )}

              <button
                type="button"
                onClick={toggleTheme}
                aria-label={isDark ? 'Chuyển sang giao diện Sáng' : 'Chuyển sang giao diện Tối'}
                className="lg:hidden min-w-[44px] min-h-[44px] w-11 h-11 rounded-xl border border-zinc-800 bg-zinc-900 hover:bg-zinc-800 flex items-center justify-center text-zinc-200 cursor-pointer touch-manipulation shrink-0"
              >
                {isDark ? <Sun className="w-4 h-4 text-orange-400" /> : <Moon className="w-4 h-4" />}
              </button>

              <button
                type="button"
                onClick={onSignOut}
                aria-label="Đăng xuất"
                className="lg:hidden min-w-[44px] min-h-[44px] w-11 h-11 rounded-xl border border-zinc-800 bg-zinc-900 hover:bg-zinc-800 flex items-center justify-center text-zinc-200 cursor-pointer touch-manipulation shrink-0"
              >
                <LogOut className="w-4 h-4" />
              </button>
            </div>
          </div>
        </header>

        {/* Main Content Canvas */}
        <main className="flex-1 p-4 lg:p-6 pb-24 lg:pb-8 max-w-[1600px] w-full mx-auto space-y-4">
          {children}
        </main>
      </div>

      {/* Mobile Fixed Bottom Navigation Bar */}
      <nav
        aria-label="Điều hướng di động"
        className="lg:hidden fixed bottom-0 left-0 right-0 z-40 bg-zinc-950/95 backdrop-blur-md border-t border-zinc-800 pb-safe"
      >
        <div
          className="grid h-15"
          style={{ gridTemplateColumns: `repeat(${navItems.length}, minmax(0, 1fr))` }}
        >
          {navItems.map((item) => {
            const Icon = item.Icon;
            const active = activeRoute === item.id;
            return (
              <button
                key={item.id}
                type="button"
                onClick={() => onNavigate(item.id)}
                className={`relative min-h-[44px] flex flex-col items-center justify-center gap-0.5 px-1 transition-colors cursor-pointer touch-manipulation ${
                  active ? 'text-orange-500' : 'text-zinc-400 hover:text-zinc-200'
                }`}
              >
                <div className="relative">
                  <Icon className="w-5 h-5" />
                  {item.badge ? (
                    <span className="absolute -top-1.5 -right-2.5 min-w-[16px] h-4 px-1 rounded-full bg-orange-600 text-white text-[10px] font-mono font-bold flex items-center justify-center tabular-nums">
                      {item.badge > 9 ? '9+' : item.badge}
                    </span>
                  ) : null}
                </div>
                <span className="text-[10px] font-medium max-w-full truncate">
                  {item.shortLabel}
                </span>
              </button>
            );
          })}
        </div>
      </nav>
    </div>
  );
};
