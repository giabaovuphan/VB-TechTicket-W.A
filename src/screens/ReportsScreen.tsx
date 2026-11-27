import React, { useMemo, useState } from 'react';
import { Award, Clock, Download, Flame, Share2, Ticket } from 'lucide-react';
import { useToast } from '../context/ToastContext';
import { SupportTicket } from '../types/models';
import {
  computeWeeklyReport,
  downloadCsvReport,
  REPORT_WINDOWS,
  ReportWindow,
  shareOrCopySummaryText,
} from '../utils/reportCalculator';

export const ReportsScreen: React.FC<{ tickets: SupportTicket[] }> = ({ tickets }) => {
  const { showToast } = useToast();
  const [windowValue, setWindowValue] = useState<ReportWindow>(ReportWindow.THIS_WEEK);

  const metrics = useMemo(
    () => computeWeeklyReport(tickets, windowValue),
    [tickets, windowValue],
  );

  const maxDaily = Math.max(1, ...metrics.dailyCounts.map((d) => d.count));

  const statusRows = [
    { label: 'Mở', count: metrics.openCount, color: 'bg-amber-500' },
    { label: 'Đã tiếp nhận', count: metrics.acceptedCount, color: 'bg-sky-500' },
    { label: 'Đang xử lý', count: metrics.inProgressCount, color: 'bg-violet-500' },
    { label: 'Đã hoàn thành', count: metrics.completedCount, color: 'bg-emerald-500' },
  ];

  const handleShareSummary = async () => {
    try {
      const mode = await shareOrCopySummaryText(metrics);
      showToast(
        mode === 'shared'
          ? 'Đã mở chia sẻ báo cáo tổng hợp.'
          : 'Đã sao chép báo cáo tổng hợp vào khay nhớ tạm.',
      );
    } catch {
      showToast('Không thể chia sẻ báo cáo.', true);
    }
  };

  const handleDownloadCsv = () => {
    try {
      downloadCsvReport(metrics);
      showToast(`Đã tải xuống báo cáo CSV (${metrics.weekLabel}).`);
    } catch {
      showToast('Không thể xuất tệp CSV.', true);
    }
  };

  return (
    <div className="space-y-4">
      {/* Filter & Export Bar */}
      <div className="flex flex-col gap-3 rounded-2xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 p-3.5 sm:p-4 lg:flex-row lg:items-center lg:justify-between">
        <div className="flex flex-wrap gap-1.5">
          {REPORT_WINDOWS.map((win) => {
            const active = windowValue === win.value;
            return (
              <button
                key={win.value}
                type="button"
                onClick={() => setWindowValue(win.value)}
                className={`h-9 rounded-lg border px-3.5 text-xs font-semibold transition-colors cursor-pointer ${
                  active
                    ? 'border-orange-600 bg-orange-600 text-white'
                    : 'border-zinc-200 dark:border-zinc-700 bg-zinc-50 dark:bg-zinc-950 text-zinc-700 dark:text-zinc-300 hover:border-zinc-400 dark:hover:border-zinc-600'
                }`}
              >
                {win.labelVi}
              </button>
            );
          })}
        </div>

        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            onClick={() => void handleShareSummary()}
            className="inline-flex h-10 items-center gap-1.5 rounded-xl border border-zinc-200 dark:border-zinc-700 bg-white dark:bg-zinc-800 hover:bg-zinc-50 dark:hover:bg-zinc-700 px-3.5 text-xs font-semibold text-zinc-800 dark:text-zinc-100 transition-colors cursor-pointer"
          >
            <Share2 className="h-4 w-4" />
            Tóm tắt
          </button>
          <button
            type="button"
            onClick={handleDownloadCsv}
            className="inline-flex h-10 items-center gap-1.5 rounded-xl bg-orange-600 hover:bg-orange-700 px-4 text-xs font-semibold text-white transition-colors cursor-pointer"
          >
            <Download className="h-4 w-4" />
            Xuất CSV
          </button>
        </div>
      </div>

      {/* 4 KPI Summary Cards */}
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <div className="rounded-2xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 p-4 shadow-sm">
          <div className="flex items-center justify-between text-xs font-semibold text-zinc-500 dark:text-zinc-400">
            <span>Tổng ticket</span>
            <Ticket className="h-4 w-4 text-orange-600 dark:text-orange-400" />
          </div>
          <p className="mt-2 font-mono-tabular text-2xl font-bold text-zinc-900 dark:text-zinc-100">
            {metrics.totalTickets}
          </p>
        </div>

        <div className="rounded-2xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 p-4 shadow-sm">
          <div className="flex items-center justify-between text-xs font-semibold text-zinc-500 dark:text-zinc-400">
            <span>Tỷ lệ hoàn thành</span>
            <Award className="h-4 w-4 text-emerald-600 dark:text-emerald-400" />
          </div>
          <p className="mt-2 font-mono-tabular text-2xl font-bold text-emerald-600 dark:text-emerald-400">
            {metrics.completionRatePercent}%
          </p>
        </div>

        <div className="rounded-2xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 p-4 shadow-sm">
          <div className="flex items-center justify-between text-xs font-semibold text-zinc-500 dark:text-zinc-400">
            <span>TB xử lý xong</span>
            <Clock className="h-4 w-4 text-sky-600 dark:text-sky-400" />
          </div>
          <p className="mt-2 font-mono-tabular text-2xl font-bold text-zinc-900 dark:text-zinc-100">
            {metrics.avgResolutionHours != null
              ? `${metrics.avgResolutionHours.toFixed(1)}h`
              : '--'}
          </p>
        </div>

        <div className="rounded-2xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 p-4 shadow-sm">
          <div className="flex items-center justify-between text-xs font-semibold text-zinc-500 dark:text-zinc-400">
            <span>Khẩn cấp đang mở</span>
            <Flame className="h-4 w-4 text-red-600 dark:text-red-400" />
          </div>
          <p className="mt-2 font-mono-tabular text-2xl font-bold text-red-600 dark:text-red-400">
            {metrics.urgentOpenCount}
          </p>
        </div>
      </div>

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        {/* Status Breakdown */}
        <section className="rounded-2xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 p-5 shadow-sm">
          <h2 className="text-sm font-bold text-zinc-900 dark:text-zinc-100">
            Phân bổ trạng thái ({metrics.weekLabel})
          </h2>
          <div className="mt-4 space-y-3">
            {statusRows.map((row) => {
              const pct =
                metrics.totalTickets > 0 ? Math.round((row.count * 100) / metrics.totalTickets) : 0;
              return (
                <div key={row.label} className="space-y-1">
                  <div className="flex justify-between text-xs">
                    <span className="font-semibold text-zinc-800 dark:text-zinc-200">{row.label}</span>
                    <span className="font-mono-tabular text-zinc-500 dark:text-zinc-400">
                      {row.count} ({pct}%)
                    </span>
                  </div>
                  <div className="h-2.5 w-full overflow-hidden rounded-full bg-zinc-100 dark:bg-zinc-800">
                    <div
                      className={`h-full rounded-full transition-all ${row.color}`}
                      style={{ width: `${pct}%` }}
                    />
                  </div>
                </div>
              );
            })}
          </div>
        </section>

        {/* 7-Day Activity Bar Chart */}
        <section className="rounded-2xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 p-5 shadow-sm">
          <h2 className="text-sm font-bold text-zinc-900 dark:text-zinc-100">
            Ticket mới trong 7 ngày gần nhất
          </h2>
          <div className="mt-5 grid h-44 grid-cols-7 items-end gap-2">
            {metrics.dailyCounts.map((bucket) => {
              const heightPct = Math.max(8, Math.round((bucket.count / maxDaily) * 100));
              return (
                <div key={bucket.label} className="flex h-full flex-col items-center justify-end gap-1.5">
                  <span className="font-mono-tabular text-xs font-bold text-zinc-900 dark:text-zinc-100">
                    {bucket.count}
                  </span>
                  <div className="flex w-full flex-1 items-end justify-center">
                    <div
                      className={`w-full max-w-[28px] rounded-t-lg transition-all ${
                        bucket.count > 0 ? 'bg-orange-600' : 'bg-zinc-200 dark:bg-zinc-800'
                      }`}
                      style={{ height: `${bucket.count > 0 ? heightPct : 6}%` }}
                    />
                  </div>
                  <span className="font-mono-tabular text-[10px] text-zinc-500 dark:text-zinc-400">
                    {bucket.label}
                  </span>
                </div>
              );
            })}
          </div>
        </section>
      </div>

      {/* Technician Leaderboard */}
      <section className="rounded-2xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 p-5 shadow-sm">
        <h2 className="text-sm font-bold text-zinc-900 dark:text-zinc-100">
          Hiệu suất kỹ thuật viên ({metrics.weekLabel})
        </h2>
        {metrics.techLeaderboard.length === 0 ? (
          <p className="mt-3 text-xs text-zinc-500 dark:text-zinc-400">
            Chưa có dữ liệu phân công kỹ thuật viên trong khoảng thời gian này.
          </p>
        ) : (
          <div className="mt-4 space-y-2.5">
            {metrics.techLeaderboard.map((perf, idx) => {
              const pct =
                perf.assignedCount > 0
                  ? Math.round((perf.completedCount * 100) / perf.assignedCount)
                  : 0;
              return (
                <div
                  key={perf.uid}
                  className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-zinc-200 dark:border-zinc-800 bg-zinc-50 dark:bg-zinc-950/60 px-4 py-3"
                >
                  <div className="flex items-center gap-3">
                    <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-orange-50 dark:bg-orange-950/40 border border-orange-200 dark:border-orange-800/50 font-mono-tabular text-xs font-bold text-orange-700 dark:text-orange-300">
                      {idx + 1}
                    </span>
                    <div>
                      <p className="text-sm font-bold text-zinc-900 dark:text-zinc-100">{perf.name}</p>
                      <p className="text-xs text-zinc-500 dark:text-zinc-400">
                        Hoàn thành{' '}
                        <span className="font-mono-tabular font-semibold text-zinc-800 dark:text-zinc-200">
                          {perf.completedCount} / {perf.assignedCount}
                        </span>{' '}
                        phiếu ({pct}%)
                      </p>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </section>
    </div>
  );
};
