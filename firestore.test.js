const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_UID = "admin_prabhu_001";
const ADMIN_EMAIL = "PrabhuJS30@gmail.com";
const CO_ADMIN_UID = "co_admin_002";
const MEMBER_A_UID = "member_arun_101";
const MEMBER_A_EMAIL = "arun.dev@example.com";
const MEMBER_B_UID = "member_karthik_102";
const MEMBER_B_EMAIL = "karthik.v@example.com";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastTime(offsetMs = 2000) {
  return new Date(Date.now() - offsetMs);
}

function futureTime(offsetMs = 60000) {
  return new Date(Date.now() + offsetMs);
}

function adminContext() {
  return testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: true,
  });
}

function memberAContext(emailVerified = true) {
  return testEnv.authenticatedContext(MEMBER_A_UID, {
    email: MEMBER_A_EMAIL,
    email_verified: emailVerified,
  });
}

function memberBContext() {
  return testEnv.authenticatedContext(MEMBER_B_UID, {
    email: MEMBER_B_EMAIL,
    email_verified: true,
  });
}

function validMemberPayload(overrides = {}) {
  const now = pastTime();
  return {
    fullName: "Arun Kumar",
    phone: "+919876543210",
    email: MEMBER_A_EMAIL,
    emailLower: MEMBER_A_EMAIL.toLowerCase(),
    address: "12 Temple Street, Mylapore",
    monthlyAmount: 500,
    membershipStatus: "ACTIVE",
    userId: MEMBER_A_UID,
    lastRemindedMonth: "",
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validPaymentPayload(overrides = {}) {
  const now = pastTime();
  return {
    memberId: "mem_arun_1",
    memberUserId: MEMBER_A_UID,
    memberEmailLower: MEMBER_A_EMAIL.toLowerCase(),
    memberName: "Arun Kumar",
    billingMonth: "2026-11",
    amount: 500,
    paymentMethod: "UPI",
    upiRef: "UTR411223344556",
    status: "PAID",
    notes: "November Vinayagar contribution",
    recordedByUid: ADMIN_UID,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validSettingsPayload(overrides = {}) {
  return {
    festivalName: "Sri Vinayagar Chaturthi Vizha Committee",
    upiId: "vinayagarcommittee@upi",
    upiPayeeName: "Vinayagar Festival Committee",
    defaultMonthlyAmount: 500,
    dueDayOfMonth: 10,
    reminderDaysBefore: 5,
    whatsappMessageTemplate:
      "Namaskaram {name}, gentle reminder for {month} Vinayagar festival contribution of Rs.{amount}. Due by {dueDate}. UPI: {upiId}",
    autoReminderEnabled: true,
    updatedAt: pastTime(),
    ...overrides,
  };
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

// 1. Unauthenticated Denials
test("Unauthenticated users cannot read or write any collection", async () => {
  const db = testEnv.unauthenticatedContext().firestore();
  await assertFails(db.collection("settings").doc("committee").get());
  await assertFails(db.collection("members").get());
  await assertFails(db.collection("payments").get());
  await assertFails(db.collection("reminder_logs").get());
  await assertFails(db.collection("join_requests").get());
  await assertFails(db.collection("members").doc("mem_1").set(validMemberPayload()));
});

// 2. Email Spoofing Attack (Red Team)
test("Spoofed admin email with email_verified=false is rejected", async () => {
  const spoofDb = testEnv
    .authenticatedContext("attacker_uid", {
      email: ADMIN_EMAIL,
      email_verified: false,
    })
    .firestore();

  await assertFails(
    spoofDb.collection("settings").doc("committee").set(validSettingsPayload())
  );
  await assertFails(
    spoofDb.collection("members").doc("mem_1").set(validMemberPayload())
  );
});

// 3. Committee Settings RBAC
test("Admin can write settings; members can read settings/committee but cannot edit or list", async () => {
  const adminDb = adminContext().firestore();
  const memberDb = memberAContext().firestore();

  await assertSucceeds(
    adminDb.collection("settings").doc("committee").set(validSettingsPayload())
  );
  await assertSucceeds(
    adminDb.collection("settings").doc("committee").update({
      dueDayOfMonth: 15,
      reminderDaysBefore: 5,
      updatedAt: pastTime(500),
    })
  );

  // Member can get settings/committee (needed for UPI ID & due date)
  await assertSucceeds(memberDb.collection("settings").doc("committee").get());
  // Member cannot list settings or write settings
  await assertFails(memberDb.collection("settings").get());
  await assertFails(
    memberDb.collection("settings").doc("committee").update({
      upiId: "attacker@upi",
      updatedAt: pastTime(500),
    })
  );

  // Admin-only secondary lock settings (/settings/admin_security)
  const validSecurity = {
    lockEnabled: true,
    adminUsername: "committee_admin",
    passcodeSalt: "a1b2c3d4e5f60718293a4b5c6d7e8f90",
    passcodeHash: "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
    updatedByUid: ADMIN_UID,
    updatedAt: pastTime(500),
  };
  await assertSucceeds(
    adminDb.collection("settings").doc("admin_security").set(validSecurity)
  );
  await assertSucceeds(
    adminDb.collection("settings").doc("admin_security").get()
  );
  // Reject ghost field on admin_security
  await assertFails(
    adminDb.collection("settings").doc("admin_security").update({
      plaintextPassword: "bad",
      updatedAt: pastTime(300),
    })
  );
  // Non-admin member cannot read or write /settings/admin_security
  await assertFails(
    memberDb.collection("settings").doc("admin_security").get()
  );
  await assertFails(
    memberDb.collection("settings").doc("admin_security").set(validSecurity)
  );
});

// 4. Members Collection CRUD, Isolation, Shadow Updates & Query Alignment
test("Admin manages members in real time; members can only read their own record and never edit/delete", async () => {
  const adminDb = adminContext().firestore();
  const arunDb = memberAContext().firestore();
  const karthikDb = memberBContext().firestore();

  // Admin creates Member A and Member B
  await assertSucceeds(
    adminDb.collection("members").doc("mem_arun_1").set(validMemberPayload())
  );
  await assertSucceeds(
    adminDb.collection("members").doc("mem_karthik_2").set(
      validMemberPayload({
        fullName: "Karthik Sundar",
        phone: "+919988776655",
        email: MEMBER_B_EMAIL,
        emailLower: MEMBER_B_EMAIL.toLowerCase(),
        userId: MEMBER_B_UID,
      })
    )
  );

  // Admin can list all members, partial-update status, and delete
  await assertSucceeds(adminDb.collection("members").get());
  await assertSucceeds(
    adminDb.collection("members").doc("mem_arun_1").update({
      membershipStatus: "PAUSED",
      monthlyAmount: 750,
      updatedAt: pastTime(500),
    })
  );
  // Restore active
  await assertSucceeds(
    adminDb.collection("members").doc("mem_arun_1").update({
      membershipStatus: "ACTIVE",
      updatedAt: pastTime(400),
    })
  );

  // Reject ghost field / @DocumentId "id" injection (Shadow Update Test)
  await assertFails(
    adminDb.collection("members").doc("mem_ghost").set({
      ...validMemberPayload(),
      id: "mem_ghost",
    })
  );
  await assertFails(
    adminDb.collection("members").doc("mem_arun_1").update({
      isAdmin: true,
      updatedAt: pastTime(300),
    })
  );
  // Reject future timestamp tampering
  await assertFails(
    adminDb.collection("members").doc("mem_future").set(
      validMemberPayload({ createdAt: futureTime(), updatedAt: futureTime() })
    )
  );

  // Member A can query their own record via emailLower OR userId
  await assertSucceeds(
    arunDb.collection("members").where("emailLower", "==", MEMBER_A_EMAIL.toLowerCase()).get()
  );
  await assertSucceeds(
    arunDb.collection("members").where("userId", "==", MEMBER_A_UID).get()
  );
  await assertSucceeds(arunDb.collection("members").doc("mem_arun_1").get());

  // PII Isolation: Member A cannot read Member B or run unfiltered list query
  await assertFails(arunDb.collection("members").doc("mem_karthik_2").get());
  await assertFails(arunDb.collection("members").get());
  await assertFails(
    arunDb.collection("members").where("emailLower", "==", MEMBER_B_EMAIL.toLowerCase()).get()
  );

  // Admin-only mutation invariant: Member A cannot create, update, or delete members
  await assertFails(
    arunDb.collection("members").doc("mem_self").set(validMemberPayload())
  );
  await assertFails(
    arunDb.collection("members").doc("mem_arun_1").update({
      monthlyAmount: 1,
      updatedAt: pastTime(200),
    })
  );
  await assertFails(arunDb.collection("members").doc("mem_arun_1").delete());

  // Karthik cannot delete Arun
  await assertFails(karthikDb.collection("members").doc("mem_arun_1").delete());

  // Admin can delete a member
  await assertSucceeds(adminDb.collection("members").doc("mem_karthik_2").delete());
});

// 5. Payments Collection: Member UPI Self-Payment vs Admin-Only Edit/Delete
test("Active member can pay own dues via UPI; only admin can edit/delete/verify transactions", async () => {
  const adminDb = adminContext().firestore();
  const arunDb = memberAContext().firestore();
  const karthikDb = memberBContext().firestore();

  await assertSucceeds(
    adminDb.collection("members").doc("mem_arun_1").set(validMemberPayload())
  );
  await assertSucceeds(
    adminDb.collection("members").doc("mem_paused_3").set(
      validMemberPayload({
        fullName: "Karthik Sundar",
        email: MEMBER_B_EMAIL,
        emailLower: MEMBER_B_EMAIL.toLowerCase(),
        userId: MEMBER_B_UID,
        membershipStatus: "PAUSED",
      })
    )
  );

  // Active Member A submits their own UPI payment
  await assertSucceeds(
    arunDb.collection("payments").doc("pay_arun_nov").set(
      validPaymentPayload({
        memberId: "mem_arun_1",
        recordedByUid: MEMBER_A_UID,
        paymentMethod: "UPI",
        status: "PAID",
      })
    )
  );

  // Member A cannot submit CASH payment (members pay via UPI only)
  await assertFails(
    arunDb.collection("payments").doc("pay_arun_cash").set(
      validPaymentPayload({
        memberId: "mem_arun_1",
        recordedByUid: MEMBER_A_UID,
        paymentMethod: "CASH",
        status: "PAID",
      })
    )
  );

  // Member A cannot create payment for another member or spoof recordedByUid
  await assertFails(
    arunDb.collection("payments").doc("pay_spoof").set(
      validPaymentPayload({
        memberId: "mem_arun_1",
        recordedByUid: ADMIN_UID,
      })
    )
  );

  // Paused Member B cannot self-submit payment
  await assertFails(
    karthikDb.collection("payments").doc("pay_karthik_nov").set(
      validPaymentPayload({
        memberId: "mem_paused_3",
        memberUserId: MEMBER_B_UID,
        memberEmailLower: MEMBER_B_EMAIL.toLowerCase(),
        memberName: "Karthik Sundar",
        recordedByUid: MEMBER_B_UID,
        paymentMethod: "UPI",
        status: "PAID",
      })
    )
  );

  // Member A can list ONLY their own payments
  await assertSucceeds(
    arunDb
      .collection("payments")
      .where("memberEmailLower", "==", MEMBER_A_EMAIL.toLowerCase())
      .get()
  );
  await assertFails(arunDb.collection("payments").get());
  await assertFails(karthikDb.collection("payments").doc("pay_arun_nov").get());

  // Member A CANNOT edit or delete their own payment once recorded
  await assertFails(
    arunDb.collection("payments").doc("pay_arun_nov").update({
      amount: 1000,
      updatedAt: pastTime(200),
    })
  );
  await assertFails(arunDb.collection("payments").doc("pay_arun_nov").delete());

  // Admin CAN edit, change status, and delete transactions in real time
  await assertSucceeds(adminDb.collection("payments").get());
  await assertSucceeds(
    adminDb.collection("payments").doc("pay_arun_nov").update({
      status: "PAID",
      notes: "Verified against committee bank statement",
      updatedAt: pastTime(200),
    })
  );
  await assertSucceeds(adminDb.collection("payments").doc("pay_arun_nov").delete());
});

// 6. Reminder Logs & Join Requests & Co-Admin
test("Admin-only reminder logs, co-admin delegation, and member join requests work securely", async () => {
  const adminDb = adminContext().firestore();
  const arunDb = memberAContext().firestore();
  const coAdminDb = testEnv
    .authenticatedContext(CO_ADMIN_UID, {
      email: "treasurer@example.com",
      email_verified: true,
    })
    .firestore();

  await assertSucceeds(
    adminDb.collection("members").doc("mem_arun_1").set(validMemberPayload())
  );

  // Admin creates a WhatsApp reminder log
  await assertSucceeds(
    adminDb.collection("reminder_logs").doc("rem_1").set({
      memberId: "mem_arun_1",
      memberName: "Arun Kumar",
      memberPhone: "+919876543210",
      billingMonth: "2026-11",
      channel: "WHATSAPP",
      sentByUid: ADMIN_UID,
      messagePreview: "Gentle reminder for November contribution",
      createdAt: pastTime(),
    })
  );

  // Member cannot read or write reminder logs
  await assertFails(arunDb.collection("reminder_logs").get());
  await assertFails(arunDb.collection("reminder_logs").doc("rem_1").get());

  // Member can submit their own Join Request keyed by their UID
  const now = pastTime();
  await assertSucceeds(
    arunDb.collection("join_requests").doc(MEMBER_A_UID).set({
      userId: MEMBER_A_UID,
      fullName: "Arun Kumar",
      emailLower: MEMBER_A_EMAIL.toLowerCase(),
      phone: "+919876543210",
      address: "12 Temple Street",
      status: "PENDING",
      createdAt: now,
      updatedAt: now,
    })
  );
  // Member cannot self-approve their join request
  await assertFails(
    arunDb.collection("join_requests").doc(MEMBER_A_UID).update({
      status: "APPROVED",
      updatedAt: pastTime(200),
    })
  );

  // Admin grants Co-Admin role, then Co-Admin can approve join request
  await assertSucceeds(
    adminDb.collection("admins").doc(CO_ADMIN_UID).set({
      uid: CO_ADMIN_UID,
      email: "treasurer@example.com",
      displayName: "Committee Treasurer",
      addedByUid: ADMIN_UID,
      createdAt: pastTime(),
    })
  );
  await assertSucceeds(
    coAdminDb.collection("join_requests").doc(MEMBER_A_UID).update({
      status: "APPROVED",
      updatedAt: pastTime(200),
    })
  );
});
