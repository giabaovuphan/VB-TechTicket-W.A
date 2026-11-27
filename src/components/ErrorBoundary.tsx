import React from 'react';
import { AlertTriangle, RotateCcw, Wrench } from 'lucide-react';
import { toFriendlyVietnameseError } from '../utils/firestoreErrors';

interface ErrorBoundaryState {
  hasError: boolean;
  message: string;
}

export class ErrorBoundary extends React.Component<
  { children: React.ReactNode },
  ErrorBoundaryState
> {
  constructor(props: { children: React.ReactNode }) {
    super(props);
    this.state = { hasError: false, message: '' };
  }

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return {
      hasError: true,
      message: toFriendlyVietnameseError(error, 'Đã xảy ra lỗi không mong muốn trong ứng dụng.'),
    };
  }

  componentDidCatch(error: Error, info: React.ErrorInfo): void {
    console.error('App ErrorBoundary caught error:', error, info);
  }

  render() {
    if (this.state.hasError) {
      return (
        <div className="min-h-[100dvh] bg-zinc-50 dark:bg-zinc-950 flex items-center justify-center p-4 sm:p-6">
          <div className="w-full max-w-md rounded-2xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 overflow-hidden shadow-sm">
            <div className="bg-zinc-950 px-6 py-4 flex items-center gap-3 border-b border-zinc-800">
              <div className="w-9 h-9 rounded-lg bg-orange-600 text-white flex items-center justify-center shrink-0">
                <Wrench className="w-4 h-4" />
              </div>
              <p className="font-display text-base font-bold tracking-tight text-white">
                HCN Support
              </p>
            </div>
            <div className="p-6 text-center">
              <div className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-xl bg-red-50 dark:bg-red-950/40 border border-red-200 dark:border-red-500/40 text-red-600 dark:text-red-400">
                <AlertTriangle className="h-6 w-6" />
              </div>
              <h1 className="text-base font-bold text-zinc-900 dark:text-zinc-100">
                Đã xảy ra sự cố hiển thị
              </h1>
              <p className="mt-2 text-xs text-zinc-600 dark:text-zinc-400 leading-relaxed">
                {this.state.message}
              </p>
              <button
                type="button"
                onClick={() => window.location.reload()}
                className="mt-5 inline-flex h-10 items-center justify-center gap-2 rounded-xl bg-orange-600 hover:bg-orange-700 px-4 text-xs font-semibold text-white transition-colors cursor-pointer"
              >
                <RotateCcw className="h-4 w-4" />
                Tải lại ứng dụng
              </button>
            </div>
          </div>
        </div>
      );
    }
    return this.props.children;
  }
}
