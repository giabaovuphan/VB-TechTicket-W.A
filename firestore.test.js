const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";

const ADMIN_UID = "admin_001";
const LEAD_UID = "lead_002";
const TECH_UID = "tech_003";
const USER_UID = "user_004";
const OTHER_USER_UID = "user_005";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

async function seedRoles() {
  const past = new Date(Date.now() - 20000);
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("users").doc(ADMIN_UID).set({
      uid: ADMIN_UID,
      email: "giabao.vuphan@gmail.com",
      displayName: "Quản trị viên",
      role: "ADMIN",
      active: true,
      createdAt: past,
      updatedAt: past,
    });
    await db.collection("users").doc(LEAD_UID).set({
      uid: LEAD_UID,
      email: "lead@techticket.vn",
      displayName: "Trưởng nhóm Minh",
      role: "TEAM_LEAD",
      active: true,
      createdAt: past,
      updatedAt: past,
    });
    await db.collection("users").doc(TECH_UID).set({
      uid: TECH_UID,
      email: "tech@techticket.vn",
      displayName: "KTV Tuấn",
      role: "TECHNICIAN",
      active: true,
      createdAt: past,
      updatedAt: past,
    });
    await db.collection("users").doc(USER_UID).set({
      uid: USER_UID,
      email: "user@techticket.vn",
      displayName: "Nguyễn Văn A",
      role: "USER",
      active: true,
      createdAt: past,
      updatedAt: past,
    });
  });
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated users cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("tickets").get());
  await assertFails(unauthDb.collection("users").doc(USER_UID).get());
  await assertFails(unauthDb.collection("role_assignments").get());
  await assertFails(unauthDb.collection("notifications").get());
  await assertFails(unauthDb.collection("inventory").get());
});

test("User self-registration requires PENDING state (no self-USER) and honors Admin role_assignments", async () => {
  const now = new Date(Date.now() - 1000);
  const attackerDb = testEnv.authenticatedContext("new_user_1", {
    email: "attacker@example.com",
    email_verified: true,
  }).firestore();

  // Self-assigning ADMIN, TECHNICIAN, or even USER without role_assignments fails
  await assertFails(
    attackerDb.collection("users").doc("new_user_1").set({
      uid: "new_user_1",
      email: "attacker@example.com",
      displayName: "Attacker",
      role: "ADMIN",
      active: true,
      approvalStatus: "APPROVED",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(
    attackerDb.collection("users").doc("new_user_1").set({
      uid: "new_user_1",
      email: "attacker@example.com",
      displayName: "Self User",
      role: "USER",
      active: true,
      approvalStatus: "APPROVED",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Unassigned Google sign-in creates PENDING inactive account ONLY
  await assertSucceeds(
    attackerDb.collection("users").doc("new_user_1").set({
      uid: "new_user_1",
      email: "attacker@example.com",
      displayName: "Pending User",
      role: "PENDING",
      active: false,
      approvalStatus: "PENDING",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Pending user cannot escalate their own role or activate themselves
  await assertFails(
    attackerDb.collection("users").doc("new_user_1").update({
      role: "USER",
      active: true,
      approvalStatus: "APPROVED",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Pending user CANNOT create or list tickets before Admin approves them
  await assertFails(
    attackerDb.collection("tickets").doc("tk_unapproved").set({
      ticketCode: "TK-9999",
      title: "Unapproved ticket",
      description: "Should fail because user is PENDING",
      category: "Phần cứng",
      priority: "MEDIUM",
      status: "OPEN",
      creatorUid: "new_user_1",
      creatorName: "Pending User",
      creatorEmail: "attacker@example.com",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(attackerDb.collection("tickets").where("creatorUid", "==", "new_user_1").get());

  // Admin creates role_assignment for new_tech@example.com
  await seedRoles();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: "giabao.vuphan@gmail.com",
    email_verified: true,
  }).firestore();

  await assertSucceeds(
    adminDb.collection("role_assignments").doc("new_tech@example.com").set({
      email: "new_tech@example.com",
      displayName: "KTV Mới",
      role: "TECHNICIAN",
      assignedByUid: ADMIN_UID,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Non-admin cannot create role_assignments
  await assertFails(
    attackerDb.collection("role_assignments").doc("attacker@example.com").set({
      email: "attacker@example.com",
      displayName: "Hacker",
      role: "ADMIN",
      assignedByUid: "new_user_1",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Pre-assigned user picks up TECHNICIAN role cleanly on first login
  const newTechDb = testEnv.authenticatedContext("new_tech_uid", {
    email: "new_tech@example.com",
    email_verified: true,
  }).firestore();
  await assertSucceeds(
    newTechDb.collection("users").doc("new_tech_uid").set({
      uid: "new_tech_uid",
      email: "new_tech@example.com",
      displayName: "KTV Mới",
      role: "TECHNICIAN",
      active: true,
      approvalStatus: "APPROVED",
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Admin approval/rejection and Root Admin protection against secondary Admins", async () => {
  await seedRoles();
  const now = new Date(Date.now() - 2000);
  const rootAdminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: "giabao.vuphan@gmail.com",
    email_verified: true,
  }).firestore();

  const SEC_ADMIN_UID = "sec_admin_01";
  const PENDING_UID = "pending_u_01";
  const pendingDb = testEnv.authenticatedContext(PENDING_UID, {
    email: "pending@example.com",
    email_verified: true,
  }).firestore();
  const secAdminDb = testEnv.authenticatedContext(SEC_ADMIN_UID, {
    email: "secadmin@example.com",
    email_verified: true,
  }).firestore();

  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("users").doc(PENDING_UID).set({
      uid: PENDING_UID,
      email: "pending@example.com",
      displayName: "Nhân sự mới",
      role: "PENDING",
      active: false,
      approvalStatus: "PENDING",
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("users").doc(SEC_ADMIN_UID).set({
      uid: SEC_ADMIN_UID,
      email: "secadmin@example.com",
      displayName: "Quản trị viên phụ",
      role: "PENDING",
      active: false,
      approvalStatus: "PENDING",
      createdAt: now,
      updatedAt: now,
    });
  });

  // Secondary/regular user cannot promote SEC_ADMIN_UID to ADMIN; Root Admin CAN
  await assertSucceeds(
    rootAdminDb.collection("users").doc(SEC_ADMIN_UID).update({
      role: "ADMIN",
      active: true,
      approvalStatus: "APPROVED",
      approvedByUid: ADMIN_UID,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Secondary Admin CANNOT demote, lock, or delete Root Admin
  await assertFails(
    secAdminDb.collection("users").doc(ADMIN_UID).update({
      role: "USER",
      active: false,
      updatedAt: new Date(Date.now() - 800),
    })
  );
  await assertFails(secAdminDb.collection("users").doc(ADMIN_UID).delete());
  await assertFails(
    secAdminDb.collection("role_assignments").doc("giabao.vuphan@gmail.com").set({
      email: "giabao.vuphan@gmail.com",
      displayName: "Override Root",
      role: "USER",
      assignedByUid: SEC_ADMIN_UID,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Secondary Admin CANNOT grant ADMIN role to someone else
  await assertFails(
    secAdminDb.collection("users").doc(PENDING_UID).update({
      role: "ADMIN",
      active: true,
      approvalStatus: "APPROVED",
      approvedByUid: SEC_ADMIN_UID,
      updatedAt: new Date(Date.now() - 700),
    })
  );
  await assertFails(
    secAdminDb.collection("role_assignments").doc("other_admin@example.com").set({
      email: "other_admin@example.com",
      displayName: "Illegal Admin",
      role: "ADMIN",
      assignedByUid: SEC_ADMIN_UID,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Secondary Admin CAN reject a pending user, and later approve them as USER
  await assertSucceeds(
    secAdminDb.collection("users").doc(PENDING_UID).update({
      role: "PENDING",
      active: false,
      approvalStatus: "REJECTED",
      rejectionReason: "Chưa thuộc danh sách nhân sự",
      approvedByUid: SEC_ADMIN_UID,
      updatedAt: new Date(Date.now() - 600),
    })
  );
  await assertFails(
    pendingDb.collection("tickets").doc("tk_rejected_try").set({
      ticketCode: "TK-9001",
      title: "Rejected test",
      description: "Cannot submit while rejected",
      category: "Phần cứng",
      priority: "LOW",
      status: "OPEN",
      creatorUid: PENDING_UID,
      creatorName: "Nhân sự mới",
      creatorEmail: "pending@example.com",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    secAdminDb.collection("users").doc(PENDING_UID).update({
      role: "USER",
      active: true,
      approvalStatus: "APPROVED",
      approvedByUid: SEC_ADMIN_UID,
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertSucceeds(
    pendingDb.collection("tickets").doc("tk_approved_ok").set({
      ticketCode: "TK-9002",
      title: "Approved test",
      description: "Can submit after approval",
      category: "Phần cứng",
      priority: "MEDIUM",
      status: "OPEN",
      creatorUid: PENDING_UID,
      creatorName: "Nhân sự mới",
      creatorEmail: "pending@example.com",
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Email spoofing with unverified email cannot gain bootstrap Admin", async () => {
  const now = new Date(Date.now() - 1000);
  const spoofDb = testEnv.authenticatedContext("spoof_uid", {
    email: "giabao.vuphan@gmail.com",
    email_verified: false,
  }).firestore();

  await assertFails(
    spoofDb.collection("users").doc("spoof_uid").set({
      uid: "spoof_uid",
      email: "giabao.vuphan@gmail.com",
      displayName: "Spoof",
      role: "ADMIN",
      active: true,
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Full 4-stage ticket lifecycle, photo verification, and RBAC isolation", async () => {
  await seedRoles();
  const created = new Date(Date.now() - 5000);
  const userDb = testEnv.authenticatedContext(USER_UID).firestore();
  const otherUserDb = testEnv.authenticatedContext(OTHER_USER_UID).firestore();
  const leadDb = testEnv.authenticatedContext(LEAD_UID).firestore();
  const techDb = testEnv.authenticatedContext(TECH_UID).firestore();

  // 1. User creates OPEN ticket with defect photo
  await assertSucceeds(
    userDb.collection("tickets").doc("tk_1001").set({
      ticketCode: "TK-1001",
      title: "Màn hình laptop bị sọc ngang",
      description: "Xuất hiện nhiều sọc xanh sau khi khởi động",
      category: "Phần cứng",
      priority: "MEDIUM",
      status: "OPEN",
      creatorUid: USER_UID,
      creatorName: "Nguyễn Văn A",
      creatorEmail: "user@techticket.vn",
      defectPhotoBase64: "data:image/jpeg;base64,/9j/4AAQSkZJRg==",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Other user cannot read or list USER_UID's ticket
  await assertFails(otherUserDb.collection("tickets").doc("tk_1001").get());
  await assertFails(otherUserDb.collection("tickets").get());
  await assertSucceeds(userDb.collection("tickets").where("creatorUid", "==", USER_UID).get());

  // User cannot assign or complete their own ticket
  await assertFails(
    userDb.collection("tickets").doc("tk_1001").update({
      status: "COMPLETED",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // 2. Team Lead assigns technician and elevates priority -> ACCEPTED
  await assertSucceeds(
    leadDb.collection("tickets").doc("tk_1001").update({
      assigneeUid: TECH_UID,
      assigneeName: "KTV Tuấn",
      teamLeadUid: LEAD_UID,
      priority: "HIGH",
      status: "ACCEPTED",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Team Lead can list all tickets
  await assertSucceeds(leadDb.collection("tickets").get());

  // 3. Assigned Technician moves ACCEPTED -> IN_PROGRESS (cannot skip straight to COMPLETED)
  await assertFails(
    techDb.collection("tickets").doc("tk_1001").update({
      status: "COMPLETED",
      resolvedPhotoBase64: "base64_after_photo_payload",
      completedAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertSucceeds(
    techDb.collection("tickets").doc("tk_1001").update({
      status: "IN_PROGRESS",
      resolutionNote: "Đang kiểm tra cáp màn hình",
      updatedAt: new Date(Date.now() - 800),
    })
  );

  // Technician queries assigned tickets cleanly
  await assertSucceeds(techDb.collection("tickets").where("assigneeUid", "==", TECH_UID).get());

  // 4. Technician completes ticket (MUST include resolvedPhotoBase64 & completedAt)
  await assertFails(
    techDb.collection("tickets").doc("tk_1001").update({
      status: "COMPLETED",
      completedAt: new Date(Date.now() - 400),
      updatedAt: new Date(Date.now() - 400),
    })
  );

  await assertSucceeds(
    techDb.collection("tickets").doc("tk_1001").update({
      status: "COMPLETED",
      resolvedPhotoBase64: "base64_after_photo_verified_12345",
      resolutionNote: "Đã thay cáp màn hình và kiểm tra ổn định",
      completedAt: new Date(Date.now() - 300),
      updatedAt: new Date(Date.now() - 300),
    })
  );

  // Terminal state lock: Technician cannot modify COMPLETED ticket afterwards
  await assertFails(
    techDb.collection("tickets").doc("tk_1001").update({
      resolutionNote: "Tampered after completion",
      updatedAt: new Date(Date.now() - 100),
    })
  );
});

test("Notifications are scoped strictly to recipientUid", async () => {
  await seedRoles();
  const now = new Date(Date.now() - 3000);
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("tickets").doc("tk_2001").set({
      ticketCode: "TK-2001",
      title: "Lỗi kết nối Wi-Fi",
      description: "Không thể kết nối mạng nội bộ tầng 3",
      category: "Mạng & Kết nối",
      priority: "HIGH",
      status: "OPEN",
      creatorUid: USER_UID,
      creatorName: "Nguyễn Văn A",
      creatorEmail: "user@techticket.vn",
      createdAt: now,
      updatedAt: now,
    });
  });

  const leadDb = testEnv.authenticatedContext(LEAD_UID).firestore();
  const userDb = testEnv.authenticatedContext(USER_UID).firestore();
  const otherDb = testEnv.authenticatedContext(OTHER_USER_UID).firestore();

  await assertSucceeds(
    leadDb.collection("notifications").doc("notif_1").set({
      recipientUid: USER_UID,
      senderUid: LEAD_UID,
      ticketId: "tk_2001",
      ticketCode: "TK-2001",
      title: "Ticket đã được tiếp nhận",
      message: "Trưởng nhóm đã phân công KTV Tuấn xử lý TK-2001",
      newStatus: "ACCEPTED",
      readFlag: false,
      createdAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(userDb.collection("notifications").where("recipientUid", "==", USER_UID).get());
  await assertSucceeds(userDb.collection("notifications").doc("notif_1").update({ readFlag: true }));

  await assertFails(otherDb.collection("notifications").doc("notif_1").get());
  await assertFails(otherDb.collection("notifications").where("recipientUid", "==", USER_UID).get());
});

test("Admin has full technical capabilities on any ticket and Team Lead can execute assigned tickets", async () => {
  await seedRoles();
  const created = new Date(Date.now() - 5000);
  const userDb = testEnv.authenticatedContext(USER_UID).firestore();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: "giabao.vuphan@gmail.com",
    email_verified: true,
  }).firestore();
  const leadDb = testEnv.authenticatedContext(LEAD_UID).firestore();

  // User creates OPEN ticket
  await assertSucceeds(
    userDb.collection("tickets").doc("tk_admin_tech").set({
      ticketCode: "TK-3001",
      title: "Lỗi nguồn máy chủ",
      description: "Máy chủ không lên nguồn khi bật công tắc",
      category: "Hệ thống máy chủ",
      priority: "URGENT",
      status: "OPEN",
      creatorUid: USER_UID,
      creatorName: "Nguyễn Văn A",
      creatorEmail: "user@techticket.vn",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Admin directly starts progress on OPEN ticket (auto-assigning self) and completes with photo
  await assertSucceeds(
    adminDb.collection("tickets").doc("tk_admin_tech").update({
      status: "IN_PROGRESS",
      assigneeUid: ADMIN_UID,
      assigneeName: "Quản trị viên",
      resolutionNote: "Admin đang kiểm tra bộ nguồn",
      updatedAt: new Date(Date.now() - 2000),
    })
  );

  await assertSucceeds(
    adminDb.collection("tickets").doc("tk_admin_tech").update({
      status: "COMPLETED",
      resolvedPhotoBase64: "base64_admin_repaired_photo",
      resolutionNote: "Đã thay nguồn dự phòng mới",
      completedAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Team Lead can also be assigned a ticket and execute technician steps
  await assertSucceeds(
    userDb.collection("tickets").doc("tk_lead_tech").set({
      ticketCode: "TK-3002",
      title: "Lỗi cấu hình router",
      description: "Mất kết nối VLAN tầng 4",
      category: "Mạng & Kết nối",
      priority: "HIGH",
      status: "OPEN",
      creatorUid: USER_UID,
      creatorName: "Nguyễn Văn A",
      creatorEmail: "user@techticket.vn",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    leadDb.collection("tickets").doc("tk_lead_tech").update({
      assigneeUid: LEAD_UID,
      assigneeName: "Trưởng nhóm Minh",
      teamLeadUid: LEAD_UID,
      priority: "HIGH",
      status: "ACCEPTED",
      updatedAt: new Date(Date.now() - 1500),
    })
  );

  await assertSucceeds(
    leadDb.collection("tickets").doc("tk_lead_tech").update({
      status: "IN_PROGRESS",
      resolutionNote: "Đang cấu hình lại VLAN",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(
    leadDb.collection("tickets").doc("tk_lead_tech").update({
      status: "COMPLETED",
      resolvedPhotoBase64: "base64_lead_verified_photo",
      resolutionNote: "Mạng đã thông suốt",
      completedAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Only Team Lead and Admin can delete tickets in any status (with atomic invoice cleanup)", async () => {
  await seedRoles();
  const now = new Date(Date.now() - 4000);
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("tickets").doc("tk_del_1").set({
      ticketCode: "TK-4001",
      title: "Ticket tạo nhầm đã hoàn thành",
      description: "Test xóa ticket bởi trưởng nhóm",
      category: "Phần cứng",
      priority: "LOW",
      status: "COMPLETED",
      creatorUid: USER_UID,
      creatorName: "Nguyễn Văn A",
      creatorEmail: "user@techticket.vn",
      assigneeUid: TECH_UID,
      assigneeName: "KTV Tuấn",
      resolvedPhotoBase64: "base64_photo_done",
      completedAt: now,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("invoices").doc("tk_del_1").set({
      invoiceCode: "HD-4001",
      ticketId: "tk_del_1",
      ticketCode: "TK-4001",
      creatorUid: USER_UID,
      assigneeUid: TECH_UID,
      items: [{ name: "Cáp HDMI", unit: "Cái", quantity: 1, unitPrice: 150000, lineTotal: 150000, customEntry: true }],
      partsSubtotal: 150000,
      laborFee: 50000,
      totalAmount: 200000,
      paymentStatus: "UNPAID",
      paymentMethod: "CASH",
      updatedByUid: TECH_UID,
      updatedByName: "KTV Tuấn",
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("tickets").doc("tk_del_2").set({
      ticketCode: "TK-4002",
      title: "Ticket tạo nhầm chưa có hóa đơn",
      description: "Test xóa ticket bởi admin",
      category: "Phần mềm",
      priority: "MEDIUM",
      status: "OPEN",
      creatorUid: USER_UID,
      creatorName: "Nguyễn Văn A",
      creatorEmail: "user@techticket.vn",
      createdAt: now,
      updatedAt: now,
    });
  });

  const userDb = testEnv.authenticatedContext(USER_UID).firestore();
  const techDb = testEnv.authenticatedContext(TECH_UID).firestore();
  const leadDb = testEnv.authenticatedContext(LEAD_UID).firestore();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: "giabao.vuphan@gmail.com",
    email_verified: true,
  }).firestore();

  // Creator and assigned Technician cannot delete ticket
  await assertFails(userDb.collection("tickets").doc("tk_del_1").delete());
  await assertFails(techDb.collection("tickets").doc("tk_del_1").delete());

  // Team Lead atomically deletes COMPLETED ticket + linked invoice
  const leadBatch = leadDb.batch();
  leadBatch.delete(leadDb.collection("tickets").doc("tk_del_1"));
  leadBatch.delete(leadDb.collection("invoices").doc("tk_del_1"));
  await assertSucceeds(leadBatch.commit());

  // Admin atomically deletes OPEN ticket even when no invoice exists
  const adminBatch = adminDb.batch();
  adminBatch.delete(adminDb.collection("tickets").doc("tk_del_2"));
  adminBatch.delete(adminDb.collection("invoices").doc("tk_del_2"));
  await assertSucceeds(adminBatch.commit());
});

test("Products catalog RBAC: Lead/Admin manage catalog, Technician lists active only, User denied", async () => {
  await seedRoles();
  const now = new Date(Date.now() - 2000);
  const userDb = testEnv.authenticatedContext(USER_UID).firestore();
  const techDb = testEnv.authenticatedContext(TECH_UID).firestore();
  const leadDb = testEnv.authenticatedContext(LEAD_UID).firestore();

  // Team Lead creates active and inactive products
  await assertSucceeds(
    leadDb.collection("products").doc("prod_ssd").set({
      name: "SSD NVMe 512GB",
      sku: "SSD-512",
      category: "Phần cứng",
      unit: "Cái",
      unitPrice: 1200000,
      active: true,
      createdByUid: LEAD_UID,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    leadDb.collection("products").doc("prod_old").set({
      name: "HDD Cũ 250GB",
      category: "Phần cứng",
      unit: "Cái",
      unitPrice: 300000,
      active: false,
      createdByUid: LEAD_UID,
      createdAt: now,
      updatedAt: now,
    })
  );

  // User cannot read or write products
  await assertFails(userDb.collection("products").where("active", "==", true).get());
  await assertFails(userDb.collection("products").doc("prod_ssd").get());

  // Technician cannot mutate products or list inactive products, but CAN list active==true
  await assertFails(
    techDb.collection("products").doc("prod_hack").set({
      name: "Fake part",
      category: "Phần cứng",
      unit: "Cái",
      unitPrice: 1000,
      active: true,
      createdByUid: TECH_UID,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(techDb.collection("products").get());
  await assertSucceeds(techDb.collection("products").where("active", "==", true).get());

  // Team Lead updates product price & toggles active
  await assertSucceeds(
    leadDb.collection("products").doc("prod_ssd").update({
      unitPrice: 1350000,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Repair invoices RBAC: assigned Technician, Team Lead, Admin can manage; Creator can view only", async () => {
  await seedRoles();
  const now = new Date(Date.now() - 4000);
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("tickets").doc("tk_inv_1").set({
      ticketCode: "TK-5001",
      title: "Hỏng RAM máy trạm",
      description: "Máy báo lỗi beep RAM",
      category: "Phần cứng",
      priority: "HIGH",
      status: "IN_PROGRESS",
      creatorUid: USER_UID,
      creatorName: "Nguyễn Văn A",
      creatorEmail: "user@techticket.vn",
      assigneeUid: TECH_UID,
      assigneeName: "KTV Tuấn",
      teamLeadUid: LEAD_UID,
      createdAt: now,
      updatedAt: now,
    });
  });

  const userDb = testEnv.authenticatedContext(USER_UID).firestore();
  const otherDb = testEnv.authenticatedContext(OTHER_USER_UID).firestore();
  const techDb = testEnv.authenticatedContext(TECH_UID).firestore();
  const leadDb = testEnv.authenticatedContext(LEAD_UID).firestore();

  // Before invoice exists, creator can observe document safely without permission/null crash
  await assertSucceeds(userDb.collection("invoices").doc("tk_inv_1").get());
  await assertFails(otherDb.collection("invoices").doc("tk_inv_1").get());

  // Creator cannot create an invoice
  await assertFails(
    userDb.collection("invoices").doc("tk_inv_1").set({
      invoiceCode: "HD-5001",
      ticketId: "tk_inv_1",
      ticketCode: "TK-5001",
      creatorUid: USER_UID,
      items: [],
      partsSubtotal: 0,
      laborFee: 100000,
      totalAmount: 100000,
      paymentStatus: "UNPAID",
      paymentMethod: "CASH",
      updatedByUid: USER_UID,
      updatedByName: "Nguyễn Văn A",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Negative unitPrice / laborFee rejected
  await assertFails(
    techDb.collection("invoices").doc("tk_inv_1").set({
      invoiceCode: "HD-5001",
      ticketId: "tk_inv_1",
      ticketCode: "TK-5001",
      creatorUid: USER_UID,
      assigneeUid: TECH_UID,
      items: [],
      partsSubtotal: 0,
      laborFee: -50000,
      totalAmount: 0,
      paymentStatus: "UNPAID",
      paymentMethod: "CASH",
      updatedByUid: TECH_UID,
      updatedByName: "KTV Tuấn",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Assigned Technician creates invoice with catalog item + custom item + laborFee
  await assertSucceeds(
    techDb.collection("invoices").doc("tk_inv_1").set({
      invoiceCode: "HD-5001",
      ticketId: "tk_inv_1",
      ticketCode: "TK-5001",
      creatorUid: USER_UID,
      assigneeUid: TECH_UID,
      items: [
        {
          productId: "prod_ram",
          name: "RAM DDR4 16GB",
          unit: "Thanh",
          quantity: 1,
          unitPrice: 950000,
          lineTotal: 950000,
          customEntry: false,
        },
        {
          name: "Vệ sinh khe RAM",
          unit: "Lần",
          quantity: 1,
          unitPrice: 50000,
          lineTotal: 50000,
          customEntry: true,
        },
      ],
      partsSubtotal: 1000000,
      laborFee: 150000,
      totalAmount: 1150000,
      paymentStatus: "UNPAID",
      paymentMethod: "TRANSFER",
      note: "Bảo hành RAM 12 tháng",
      updatedByUid: TECH_UID,
      updatedByName: "KTV Tuấn",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Team Lead marks invoice PAID
  await assertSucceeds(
    leadDb.collection("invoices").doc("tk_inv_1").update({
      paymentStatus: "PAID",
      paymentMethod: "TRANSFER",
      paidAt: new Date(Date.now() - 500),
      updatedByUid: LEAD_UID,
      updatedByName: "Trưởng nhóm Minh",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Creator can read the paid invoice, other user cannot
  await assertSucceeds(userDb.collection("invoices").doc("tk_inv_1").get());
  await assertFails(otherDb.collection("invoices").doc("tk_inv_1").get());
});

test("Inventory RBAC: Lead/Admin manage stock; Technician lists active & deducts stock; User denied", async () => {
  await seedRoles();
  const now = new Date(Date.now() - 3000);
  const userDb = testEnv.authenticatedContext(USER_UID).firestore();
  const techDb = testEnv.authenticatedContext(TECH_UID).firestore();
  const leadDb = testEnv.authenticatedContext(LEAD_UID).firestore();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: "giabao.vuphan@gmail.com",
    email_verified: true,
  }).firestore();

  // Team Lead creates active & inactive inventory items
  await assertSucceeds(
    leadDb.collection("inventory").doc("inv_ram_16").set({
      name: "RAM DDR4 16GB Kingston",
      sku: "INV-RAM-16G",
      category: "Phần cứng",
      unit: "Thanh",
      unitPrice: 950000,
      quantityOnHand: 8,
      minThreshold: 3,
      location: "Kệ A1",
      active: true,
      updatedByUid: LEAD_UID,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    adminDb.collection("inventory").doc("inv_legacy").set({
      name: "Bàn phím cũ",
      category: "Thiết bị ngoại vi",
      unit: "Cái",
      unitPrice: 120000,
      quantityOnHand: 0,
      minThreshold: 1,
      active: false,
      updatedByUid: ADMIN_UID,
      createdAt: now,
      updatedAt: now,
    })
  );

  // User cannot read or write inventory
  await assertFails(userDb.collection("inventory").where("active", "==", true).get());
  await assertFails(userDb.collection("inventory").doc("inv_ram_16").get());

  // Technician cannot create, delete, or list inactive inventory, or inflate stock
  await assertFails(techDb.collection("inventory").get());
  await assertSucceeds(techDb.collection("inventory").where("active", "==", true).get());
  await assertFails(
    techDb.collection("inventory").doc("inv_fake").set({
      name: "Fake Stock",
      category: "Phần cứng",
      unit: "Cái",
      unitPrice: 1000,
      quantityOnHand: 10,
      minThreshold: 1,
      active: true,
      updatedByUid: TECH_UID,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(
    techDb.collection("inventory").doc("inv_ram_16").update({
      quantityOnHand: 99,
      updatedByUid: TECH_UID,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Technician CAN deduct stock (quantityOnHand decreases from 8 -> 6) when billing
  await assertSucceeds(
    techDb.collection("inventory").doc("inv_ram_16").update({
      quantityOnHand: 6,
      updatedByUid: TECH_UID,
      updatedAt: new Date(Date.now() - 900),
    })
  );

  // Negative stock is always rejected
  await assertFails(
    leadDb.collection("inventory").doc("inv_ram_16").update({
      quantityOnHand: -1,
      updatedByUid: LEAD_UID,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Team Lead can restock and delete
  await assertSucceeds(
    leadDb.collection("inventory").doc("inv_ram_16").update({
      quantityOnHand: 12,
      updatedByUid: LEAD_UID,
      updatedAt: new Date(Date.now() - 400),
    })
  );
  await assertSucceeds(adminDb.collection("inventory").doc("inv_legacy").delete());
});
