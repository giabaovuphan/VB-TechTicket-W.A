import React from 'react';
import { AlertCircle, Loader2, Moon, Sun, Wrench } from 'lucide-react';
import { useAuthSession } from '../context/AuthSessionContext';
import { useTheme } from '../context/ThemeContext';

export const AuthScreen: React.FC = () => {
  const { signInWithGoogle, authBusy, authError } = useAuthSession();
  const { isDark, toggleTheme } = useTheme();

  return (
    <div className="min-h-[100dvh] pt-safe pb-safe bg-zinc-50 dark:bg-zinc-950 flex flex-col justify-center items-center px-4 py-10 transition-colors">
      <div className="w-full max-w-md bg-white dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 rounded-2xl overflow-hidden shadow-sm">
        {/* Dark Obsidian Brand Header Band */}
        <div className="bg-zinc-950 px-5 sm:px-7 py-4 sm:py-5 flex items-center justify-between gap-3 border-b border-zinc-800">
          <div className="flex min-w-0 items-center gap-3">
            <div className="w-10 h-10 sm:w-11 sm:h-11 rounded-xl bg-orange-600 text-white flex items-center justify-center shrink-0">
              <Wrench className="w-5 h-5" />
            </div>
            <div className="min-w-0">
              <h1 className="text-lg sm:text-xl font-bold tracking-tight text-white leading-tight truncate">
                HCN Support
              </h1>
              <p className="text-[11px] sm:text-xs text-zinc-400 mt-0.5 truncate">
                Hương Cao Nguyên · Kỹ thuật
              </p>
            </div>
          </div>

          <button
            type="button"
            onClick={toggleTheme}
            aria-label={isDark ? 'Chuyển sang giao diện Sáng' : 'Chuyển sang giao diện Tối'}
            className="w-10 h-10 rounded-xl border border-zinc-800 bg-zinc-900 hover:bg-zinc-800 flex items-center justify-center text-zinc-200 transition-colors cursor-pointer shrink-0"
          >
            {isDark ? <Sun className="w-4 h-4 text-orange-400" /> : <Moon className="w-4 h-4" />}
          </button>
        </div>

        {/* Sign-in Card Body */}
        <div className="p-7 sm:p-8 space-y-5">
          <div>
            <h2 className="text-base font-bold text-zinc-900 dark:text-zinc-100">
              Đăng nhập hệ thống
            </h2>
            <p className="text-xs text-zinc-600 dark:text-zinc-400 mt-1.5 leading-relaxed">
              Sử dụng tài khoản Google công việc để gửi phiếu hỗ trợ, tiếp nhận xử lý sự cố,
              lập hóa đơn sửa chữa và theo dõi tồn kho linh kiện đồng bộ thời gian thực.
            </p>
          </div>

          {authError && (
            <div
              role="alert"
              className="flex items-start gap-2.5 rounded-xl border border-red-200 dark:border-red-500/40 bg-red-50 dark:bg-red-950/40 p-3.5 text-xs font-medium text-red-700 dark:text-red-300"
            >
              <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
              <span>{authError}</span>
            </div>
          )}

          <button
            type="button"
            disabled={authBusy}
            onClick={() => void signInWithGoogle()}
            className="w-full h-11 bg-orange-600 hover:bg-orange-700 disabled:bg-orange-400 text-white text-sm font-semibold rounded-xl inline-flex items-center justify-center gap-2.5 transition-colors cursor-pointer disabled:cursor-not-allowed"
          >
            {authBusy ? (
              <>
                <Loader2 className="w-4 h-4 animate-spin" />
                <span>Đang xác thực với Google...</span>
              </>
            ) : (
              <>
                <svg
                  className="w-4 h-4 shrink-0 rounded-full bg-white p-0.5"
                  viewBox="0 0 24 24"
                  aria-hidden="true"
                >
                  <path
                    fill="#EA4335"
                    d="M12 10.2v3.9h5.5c-.2 1.2-1.4 3.6-5.5 3.6-3.3 0-6-2.7-6-6s2.7-6 6-6c1.9 0 3.1.8 3.8 1.5l2.6-2.5C16.8 3.2 14.6 2.3 12 2.3 6.6 2.3 2.3 6.6 2.3 12S6.6 21.7 12 21.7c6.9 0 9.6-4.8 9.6-7.3 0-.5-.1-.9-.1-1.2H12z"
                  />
                </svg>
                <span>Đăng nhập bằng Google</span>
              </>
            )}
          </button>

          <div className="rounded-xl border border-zinc-200 dark:border-zinc-800 bg-zinc-50 dark:bg-zinc-950/60 p-3.5 text-xs leading-relaxed text-zinc-600 dark:text-zinc-400">
            <p className="font-semibold text-zinc-900 dark:text-zinc-100">
              Lưu ý bảo mật &amp; phân quyền
            </p>
            <p className="mt-1">
              Tài khoản đăng nhập lần đầu sẽ ở trạng thái{' '}
              <strong className="text-zinc-900 dark:text-zinc-200">Chờ cấp quyền</strong> cho đến
              khi Quản trị viên phê duyệt hoặc khớp email đã gán sẵn trong hệ thống.
            </p>
          </div>
        </div>
      </div>
    </div>
  );
};
