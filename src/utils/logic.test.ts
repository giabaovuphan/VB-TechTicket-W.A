import test from 'node:test';
import assert from 'node:assert/strict';
import {
  AccountApprovalStatus,
  formatVnd,
  getAlertLevel,
  getAssignableRoles,
  InvoicePaymentMethod,
  isApprovedAndActive,
  parseApprovalStatus,
  parseUserRole,
  StockAlertLevel,
  SupportTicket,
  TicketPriority,
  TicketStatus,
  UserAccount,
  UserRole,
} from '../types/models';
import {
  buildWeeklyCsvContent,
  buildWeeklyTextSummary,
  computeWeeklyReport,
  ReportWindow,
} from './reportCalculator';
import {
  buildInvoiceSummary,
  canApplyInventoryDeltasForRole,
  computeInventoryDeltas,
  normalizeInvoiceLineItems,
} from './invoiceUtils';

test('formatVnd formats Vietnamese currency with dot separators', () => {
  assert.equal(formatVnd(650000), '650.000 đ');
  assert.equal(formatVnd(0), '0 đ');
});

test('getAlertLevel computes OUT, LOW, OK stock thresholds accurately', () => {
  assert.equal(getAlertLevel({ quantityOnHand: 0, minThreshold: 2 }), StockAlertLevel.OUT);
  assert.equal(getAlertLevel({ quantityOnHand: 2, minThreshold: 2 }), StockAlertLevel.LOW);
  assert.equal(getAlertLevel({ quantityOnHand: 3, minThreshold: 2 }), StockAlertLevel.OK);
});

test('Role and approval status parsing and assignableRoles guard root admin properly', () => {
  assert.equal(parseUserRole('TEAM_LEAD'), UserRole.TEAM_LEAD);
  assert.equal(parseUserRole('UNKNOWN'), UserRole.PENDING);
  assert.equal(parseApprovalStatus(undefined, UserRole.PENDING), AccountApprovalStatus.PENDING);
  assert.equal(parseApprovalStatus(undefined, UserRole.USER), AccountApprovalStatus.APPROVED);
  assert.equal(parseApprovalStatus('REJECTED', UserRole.USER), AccountApprovalStatus.REJECTED);

  assert.deepEqual(getAssignableRoles(false), [UserRole.USER, UserRole.TECHNICIAN, UserRole.TEAM_LEAD]);
  assert.deepEqual(getAssignableRoles(true), [
    UserRole.USER,
    UserRole.TECHNICIAN,
    UserRole.TEAM_LEAD,
    UserRole.ADMIN,
  ]);

  const approvedTech: UserAccount = {
    id: 'u1',
    uid: 'u1',
    email: 'tech@example.com',
    displayName: 'KTV',
    role: UserRole.TECHNICIAN,
    active: true,
    approvalStatus: AccountApprovalStatus.APPROVED,
  };
  assert.equal(isApprovedAndActive(approvedTech), true);
  assert.equal(isApprovedAndActive({ ...approvedTech, active: false }), false);
  assert.equal(isApprovedAndActive({ ...approvedTech, role: UserRole.PENDING }), false);
});

test('WeeklyReportCalculator computes counts, completion rate, average hours, and CSV export', () => {
  const now = new Date('2026-11-17T12:00:00Z').getTime();
  const oneHour = 3_600_000;
  const tickets: SupportTicket[] = [
    {
      id: 't1',
      ticketCode: 'TK-1001',
      title: 'Máy pha "lỗi", áp yếu',
      description: 'Chi tiết',
      category: 'Phần cứng',
      priority: TicketPriority.URGENT,
      status: TicketStatus.OPEN,
      creatorUid: 'u1',
      creatorName: 'An',
      creatorEmail: 'an@test.com',
      createdAt: now - 2 * oneHour,
    },
    {
      id: 't2',
      ticketCode: 'TK-1002',
      title: 'Máy in POS',
      description: 'Chi tiết',
      category: 'Thiết bị ngoại vi',
      priority: TicketPriority.HIGH,
      status: TicketStatus.COMPLETED,
      creatorUid: 'u1',
      creatorName: 'An',
      creatorEmail: 'an@test.com',
      assigneeUid: 'tech1',
      assigneeName: 'Bình KTV',
      createdAt: now - 5 * oneHour,
      completedAt: now - 1 * oneHour,
    },
  ];

  const metrics = computeWeeklyReport(tickets, ReportWindow.THIS_WEEK, now);
  assert.equal(metrics.totalTickets, 2);
  assert.equal(metrics.openCount, 1);
  assert.equal(metrics.completedCount, 1);
  assert.equal(metrics.completionRatePercent, 50);
  assert.equal(metrics.urgentOpenCount, 1);
  assert.equal(metrics.avgResolutionHours, 4);
  assert.equal(metrics.techLeaderboard.length, 1);
  assert.equal(metrics.techLeaderboard[0].completedCount, 1);
  assert.equal(metrics.dailyCounts.length, 7);

  const summary = buildWeeklyTextSummary(metrics, new Date(now));
  assert.match(summary, /BÁO CÁO TỔNG HỢP HCN SUPPORT/);
  assert.match(summary, /Bình KTV: Hoàn thành 1\/1/);

  const csv = buildWeeklyCsvContent(metrics);
  assert.equal(csv.startsWith('\uFEFF'), true);
  assert.match(csv, /"Máy pha ""lỗi"", áp yếu"/);
});

test('Invoice delta math enforces Technician non-return rule and formats invoice summary', () => {
  const existing = [
    {
      productId: 'p1',
      inventoryId: 'inv1',
      name: 'Bơm Ulka',
      unit: 'Cái',
      quantity: 2,
      unitPrice: 650000,
      lineTotal: 1300000,
      customEntry: false,
    },
  ];
  const reduced = [{ ...existing[0], quantity: 1, lineTotal: 650000 }];
  const deltas = computeInventoryDeltas(existing, reduced);
  assert.deepEqual(deltas, { inv1: -1 });
  assert.equal(canApplyInventoryDeltasForRole(UserRole.TECHNICIAN, deltas), false);
  assert.equal(canApplyInventoryDeltasForRole(UserRole.TEAM_LEAD, deltas), true);

  const normalized = normalizeInvoiceLineItems(reduced);
  assert.equal(normalized[0].lineTotal, 650000);

  const summary = buildInvoiceSummary(
    {
      id: 't2',
      ticketCode: 'TK-1002',
      title: 'Thay bơm',
      description: 'Lỗi bơm',
      category: 'Phần cứng',
      priority: TicketPriority.HIGH,
      status: TicketStatus.COMPLETED,
      creatorUid: 'u1',
      creatorName: 'An',
      creatorEmail: 'an@test.com',
      assigneeName: 'Bình KTV',
    },
    {
      id: 't2',
      invoiceCode: 'HD-1002',
      ticketId: 't2',
      ticketCode: 'TK-1002',
      creatorUid: 'u1',
      items: normalized,
      partsSubtotal: 650000,
      laborFee: 150000,
      totalAmount: 800000,
      paymentStatus: 'PAID',
      paymentMethod: InvoicePaymentMethod.TRANSFER,
      updatedByUid: 'tech1',
      updatedByName: 'Bình KTV',
    },
  );
  assert.match(summary, /HD-1002/);
  assert.match(summary, /800\.000 đ/);
  assert.match(summary, /Chuyển khoản/);
});
