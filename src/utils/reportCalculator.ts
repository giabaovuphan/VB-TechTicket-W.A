import {
  formatTimestampVi,
  parseTicketPriority,
  parseTicketStatus,
  SupportTicket,
  TICKET_PRIORITY_META,
  TICKET_STATUS_META,
  TicketPriority,
  TicketStatus,
  timestampToMillis,
} from '../types/models';

export interface TechnicianPerformance {
  uid: string;
  name: string;
  assignedCount: number;
  completedCount: number;
}

export interface DailyCountBucket {
  label: string;
  count: number;
}

export interface WeeklyReportMetrics {
  weekLabel: string;
  totalTickets: number;
  openCount: number;
  acceptedCount: number;
  inProgressCount: number;
  completedCount: number;
  completionRatePercent: number;
  avgResolutionHours: number | null;
  urgentOpenCount: number;
  dailyCounts: DailyCountBucket[];
  techLeaderboard: TechnicianPerformance[];
  filteredTickets: SupportTicket[];
}

export enum ReportWindow {
  THIS_WEEK = 'THIS_WEEK',
  TWO_WEEKS = 'TWO_WEEKS',
  FOUR_WEEKS = 'FOUR_WEEKS',
  ALL_TIME = 'ALL_TIME',
}

export const REPORT_WINDOWS: Array<{ value: ReportWindow; labelVi: string; daysBack: number }> = [
  { value: ReportWindow.THIS_WEEK, labelVi: '7 ngày qua', daysBack: 7 },
  { value: ReportWindow.TWO_WEEKS, labelVi: '14 ngày qua', daysBack: 14 },
  { value: ReportWindow.FOUR_WEEKS, labelVi: '28 ngày qua', daysBack: 28 },
  { value: ReportWindow.ALL_TIME, labelVi: 'Toàn bộ', daysBack: 3650 },
];

export function getReportWindowMeta(windowValue: ReportWindow) {
  return REPORT_WINDOWS.find((w) => w.value === windowValue) ?? REPORT_WINDOWS[0];
}

function buildDailyBuckets(tickets: SupportTicket[], nowMillis: number): DailyCountBucket[] {
  const todayStart = new Date(nowMillis);
  todayStart.setHours(0, 0, 0, 0);
  const baseMs = todayStart.getTime();

  const buckets: DailyCountBucket[] = [];
  for (let offset = 6; offset >= 0; offset -= 1) {
    const startMs = baseMs - offset * 86_400_000;
    const endMs = startMs + 86_400_000;
    const dateObj = new Date(startMs);
    const label = `${String(dateObj.getDate()).padStart(2, '0')}/${String(dateObj.getMonth() + 1).padStart(2, '0')}`;
    const count = tickets.filter((t) => {
      const ms = timestampToMillis(t.createdAt);
      return ms != null && ms >= startMs && ms < endMs;
    }).length;
    buckets.push({ label, count });
  }
  return buckets;
}

export function computeWeeklyReport(
  tickets: SupportTicket[],
  windowValue: ReportWindow,
  nowMillis: number = Date.now(),
): WeeklyReportMetrics {
  const meta = getReportWindowMeta(windowValue);
  const cutoffMillis = windowValue === ReportWindow.ALL_TIME ? 0 : nowMillis - meta.daysBack * 86_400_000;

  const scoped = tickets.filter((ticket) => {
    const createdMs = timestampToMillis(ticket.createdAt) ?? nowMillis;
    return createdMs >= cutoffMillis;
  });

  const openCount = scoped.filter((t) => parseTicketStatus(t.status) === TicketStatus.OPEN).length;
  const acceptedCount = scoped.filter((t) => parseTicketStatus(t.status) === TicketStatus.ACCEPTED).length;
  const inProgressCount = scoped.filter((t) => parseTicketStatus(t.status) === TicketStatus.IN_PROGRESS).length;
  const completedCount = scoped.filter((t) => parseTicketStatus(t.status) === TicketStatus.COMPLETED).length;
  const totalTickets = scoped.length;
  const completionRatePercent = totalTickets > 0 ? Math.floor((completedCount * 100) / totalTickets) : 0;

  const durations: number[] = [];
  for (const t of scoped) {
    if (parseTicketStatus(t.status) === TicketStatus.COMPLETED) {
      const createdMs = timestampToMillis(t.createdAt);
      const completedMs = timestampToMillis(t.completedAt);
      if (createdMs != null && completedMs != null) {
        durations.push(Math.max(0, completedMs - createdMs) / 3_600_000);
      }
    }
  }
  const avgResolutionHours =
    durations.length > 0 ? durations.reduce((acc, val) => acc + val, 0) / durations.length : null;

  const urgentOpenCount = scoped.filter(
    (t) =>
      parseTicketPriority(t.priority) === TicketPriority.URGENT &&
      parseTicketStatus(t.status) !== TicketStatus.COMPLETED,
  ).length;

  const dailyCounts = buildDailyBuckets(tickets, nowMillis);

  const techMap = new Map<string, SupportTicket[]>();
  for (const ticket of scoped) {
    const uid = ticket.assigneeUid?.trim();
    if (!uid) continue;
    const list = techMap.get(uid) ?? [];
    list.push(ticket);
    techMap.set(uid, list);
  }

  const techLeaderboard: TechnicianPerformance[] = Array.from(techMap.entries())
    .map(([uid, list]) => {
      const name = list.find((t) => t.assigneeName && t.assigneeName.trim())?.assigneeName ?? 'Kỹ thuật viên';
      const completed = list.filter((t) => parseTicketStatus(t.status) === TicketStatus.COMPLETED).length;
      return {
        uid,
        name,
        assignedCount: list.length,
        completedCount: completed,
      };
    })
    .sort((a, b) => b.completedCount * 10 + b.assignedCount - (a.completedCount * 10 + a.assignedCount));

  return {
    weekLabel: meta.labelVi,
    totalTickets,
    openCount,
    acceptedCount,
    inProgressCount,
    completedCount,
    completionRatePercent,
    avgResolutionHours,
    urgentOpenCount,
    dailyCounts,
    techLeaderboard,
    filteredTickets: scoped,
  };
}

export function buildWeeklyTextSummary(metrics: WeeklyReportMetrics, now: Date = new Date()): string {
  const avgStr = metrics.avgResolutionHours != null ? `${metrics.avgResolutionHours.toFixed(1)} giờ` : 'Chưa có';
  const lines: string[] = [
    `=== BÁO CÁO TỔNG HỢP HCN SUPPORT (${metrics.weekLabel}) ===`,
    `Ngày xuất: ${formatTimestampVi(now)}`,
    `Tổng số ticket: ${metrics.totalTickets}`,
    `- Mở: ${metrics.openCount}`,
    `- Đã tiếp nhận: ${metrics.acceptedCount}`,
    `- Đang xử lý: ${metrics.inProgressCount}`,
    `- Đã hoàn thành: ${metrics.completedCount} (${metrics.completionRatePercent}%)`,
    `Thời gian xử lý trung bình: ${avgStr}`,
    `Ticket khẩn cấp chưa đóng: ${metrics.urgentOpenCount}`,
  ];

  if (metrics.techLeaderboard.length > 0) {
    lines.push('');
    lines.push('Hiệu suất kỹ thuật viên:');
    metrics.techLeaderboard.forEach((perf, idx) => {
      lines.push(`${idx + 1}. ${perf.name}: Hoàn thành ${perf.completedCount}/${perf.assignedCount}`);
    });
  }
  return `${lines.join('\n')}\n`;
}

export function buildWeeklyCsvContent(metrics: WeeklyReportMetrics): string {
  const header = 'Mã Ticket,Tiêu đề,Danh mục,Ưu tiên,Trạng thái,Người tạo,Kỹ thuật viên,Ngày tạo,Ngày hoàn thành';
  const rows = metrics.filteredTickets.map((t) => {
    const created = t.createdAt ? formatTimestampVi(t.createdAt) : '';
    const completed = t.completedAt ? formatTimestampVi(t.completedAt) : '';
    const cells = [
      t.ticketCode,
      t.title,
      t.category,
      TICKET_PRIORITY_META[parseTicketPriority(t.priority)].labelVi,
      TICKET_STATUS_META[parseTicketStatus(t.status)].labelVi,
      t.creatorName,
      t.assigneeName || 'Chưa gán',
      created,
      completed,
    ];
    return cells.map((cell) => `"${String(cell).replace(/"/g, '""')}"`).join(',');
  });
  return `\uFEFF${[header, ...rows].join('\n')}\n`;
}

export async function shareOrCopySummaryText(metrics: WeeklyReportMetrics): Promise<'shared' | 'copied'> {
  const text = buildWeeklyTextSummary(metrics);
  if (typeof navigator !== 'undefined' && typeof navigator.share === 'function') {
    try {
      await navigator.share({
        title: `Báo cáo HCN Support - ${metrics.weekLabel}`,
        text,
      });
      return 'shared';
    } catch {
      // Fallback to clipboard copy if user cancels share dialog or share not allowed in iframe
    }
  }
  if (typeof navigator !== 'undefined' && navigator.clipboard) {
    await navigator.clipboard.writeText(text);
    return 'copied';
  }
  return 'copied';
}

export function downloadCsvReport(metrics: WeeklyReportMetrics): void {
  const csv = buildWeeklyCsvContent(metrics);
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  const safeSlug = metrics.weekLabel.replace(/\s+/g, '_');
  a.href = url;
  a.download = `HCN_Support_${safeSlug}.csv`;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}
