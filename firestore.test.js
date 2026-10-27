const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";

const OWNER_UID = "owner_uid_001";
const MGR_BR1_UID = "mgr_br1_uid_002";
const MGR_BR2_UID = "mgr_br2_uid_003";
const AGENT_A_UID = "agent_a_uid_004";
const AGENT_B_UID = "agent_b_uid_005";
const INTRUDER_UID = "intruder_uid_999";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastTimestamp() {
  return new Date(Date.now() - 2000);
}

function ownerPerms() {
  return {
    canRegisterCustomer: true,
    canCreateLoan: true,
    canApproveLoan: true,
    canCollectPayment: true,
    canManageAdvance: true,
    canRequestPreClosure: true,
    canApprovePreClosure: true,
    canUploadDocuments: true,
    canManageTeams: true,
    canViewReports: true,
    canViewAuditLogs: true,
  };
}

function agentPerms() {
  return {
    canRegisterCustomer: true,
    canCreateLoan: false,
    canApproveLoan: false,
    canCollectPayment: true,
    canManageAdvance: true,
    canRequestPreClosure: true,
    canApprovePreClosure: false,
    canUploadDocuments: true,
    canManageTeams: false,
    canViewReports: true,
    canViewAuditLogs: false,
  };
}

function managerPerms() {
  return {
    canRegisterCustomer: true,
    canCreateLoan: true,
    canApproveLoan: true,
    canCollectPayment: true,
    canManageAdvance: true,
    canRequestPreClosure: true,
    canApprovePreClosure: false,
    canUploadDocuments: true,
    canManageTeams: true,
    canViewReports: true,
    canViewAuditLogs: true,
  };
}

async function seedInitializedOrg() {
  const ts = pastTimestamp();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("org_config").doc("main").set({
      orgName: "Apex Microfinance",
      ownerUid: OWNER_UID,
      headOfficeBranchId: "br_001",
      headOfficeBranchName: "Chennai Main",
      initialized: true,
      createdAt: ts,
      updatedAt: ts,
    });
    await db.collection("branches").doc("br_001").set({
      orgOwnerUid: OWNER_UID,
      branchCode: "BR-001",
      name: "Chennai Main",
      city: "Chennai",
      address: "Anna Salai, Chennai",
      managerUids: [MGR_BR1_UID],
      memberUids: [OWNER_UID, MGR_BR1_UID, AGENT_A_UID, AGENT_B_UID],
      createdByUid: OWNER_UID,
      createdAtMillis: 1700000000000,
      createdAt: ts,
      updatedAt: ts,
    });
    await db.collection("branches").doc("br_002").set({
      orgOwnerUid: OWNER_UID,
      branchCode: "BR-002",
      name: "Madurai South",
      city: "Madurai",
      address: "KK Nagar, Madurai",
      managerUids: [MGR_BR2_UID],
      memberUids: [MGR_BR2_UID],
      createdByUid: OWNER_UID,
      createdAtMillis: 1700000001000,
      createdAt: ts,
      updatedAt: ts,
    });
    await db.collection("employees").doc(OWNER_UID).set({
      userId: OWNER_UID,
      orgOwnerUid: OWNER_UID,
      employeeCode: "EMP-0001",
      fullName: "Rajesh Kumar",
      mobile: "9876543210",
      email: "owner@company.com",
      role: "OWNER",
      branchId: "br_001",
      branchName: "Chennai Main",
      branchManagerUids: [MGR_BR1_UID],
      teamId: "",
      teamName: "",
      isActive: true,
      ...ownerPerms(),
      createdByUid: OWNER_UID,
      createdDateDdMmYyyy: "26-10-2026",
      createdAtMillis: 1700000000000,
      createdAt: ts,
      updatedAt: ts,
    });
    await db.collection("employees").doc(MGR_BR1_UID).set({
      userId: MGR_BR1_UID,
      orgOwnerUid: OWNER_UID,
      employeeCode: "EMP-0002",
      fullName: "Priya Nair",
      mobile: "9876543211",
      email: "mgr1@company.com",
      role: "BRANCH_MANAGER",
      branchId: "br_001",
      branchName: "Chennai Main",
      branchManagerUids: [MGR_BR1_UID],
      teamId: "",
      teamName: "",
      isActive: true,
      ...managerPerms(),
      createdByUid: OWNER_UID,
      createdDateDdMmYyyy: "26-10-2026",
      createdAtMillis: 1700000002000,
      createdAt: ts,
      updatedAt: ts,
    });
    await db.collection("employees").doc(AGENT_A_UID).set({
      userId: AGENT_A_UID,
      orgOwnerUid: OWNER_UID,
      employeeCode: "EMP-0003",
      fullName: "Arun Kumar",
      mobile: "9876543212",
      email: "agenta@company.com",
      role: "COLLECTION_AGENT",
      branchId: "br_001",
      branchName: "Chennai Main",
      branchManagerUids: [MGR_BR1_UID],
      teamId: "",
      teamName: "",
      isActive: true,
      ...agentPerms(),
      createdByUid: OWNER_UID,
      createdDateDdMmYyyy: "26-10-2026",
      createdAtMillis: 1700000003000,
      createdAt: ts,
      updatedAt: ts,
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

test("1. Unauthenticated callers are rejected across all collections", async () => {
  await seedInitializedOrg();
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("org_config").doc("main").get());
  await assertFails(unauthDb.collection("customers").get());
  await assertFails(unauthDb.collection("loans").get());
  await assertFails(unauthDb.collection("employees").doc(OWNER_UID).get());
});

test("2. First-run atomic Owner bootstrap succeeds once, then blocks second Owner self-assignment", async () => {
  const ts = pastTimestamp();
  const ownerDb = testEnv.authenticatedContext(OWNER_UID, {
    email: "owner@company.com",
    email_verified: true,
  }).firestore();

  const batch = ownerDb.batch();
  batch.set(ownerDb.collection("org_config").doc("main"), {
    orgName: "Apex Finance",
    ownerUid: OWNER_UID,
    headOfficeBranchId: "br_001",
    headOfficeBranchName: "Head Office",
    initialized: true,
    createdAt: ts,
    updatedAt: ts,
  });
  batch.set(ownerDb.collection("branches").doc("br_001"), {
    orgOwnerUid: OWNER_UID,
    branchCode: "BR-001",
    name: "Head Office",
    city: "Chennai",
    address: "Mount Road, Chennai",
    managerUids: [],
    memberUids: [OWNER_UID],
    createdByUid: OWNER_UID,
    createdAtMillis: 1700000000000,
    createdAt: ts,
    updatedAt: ts,
  });
  batch.set(ownerDb.collection("employees").doc(OWNER_UID), {
    userId: OWNER_UID,
    orgOwnerUid: OWNER_UID,
    employeeCode: "EMP-0001",
    fullName: "Rajesh Kumar",
    mobile: "9876543210",
    email: "owner@company.com",
    role: "OWNER",
    branchId: "br_001",
    branchName: "Head Office",
    branchManagerUids: [],
    teamId: "",
    teamName: "",
    isActive: true,
    ...ownerPerms(),
    createdByUid: OWNER_UID,
    createdDateDdMmYyyy: "26-10-2026",
    createdAtMillis: 1700000000000,
    createdAt: ts,
    updatedAt: ts,
  });
  await assertSucceeds(batch.commit());

  // Intruder attempts to self-assign OWNER after org_config/main already exists
  const intruderDb = testEnv.authenticatedContext(INTRUDER_UID, {
    email: "hacker@evil.com",
    email_verified: true,
  }).firestore();
  await assertFails(
    intruderDb.collection("employees").doc(INTRUDER_UID).set({
      userId: INTRUDER_UID,
      orgOwnerUid: OWNER_UID,
      employeeCode: "EMP-9999",
      fullName: "Intruder User",
      mobile: "9999999999",
      email: "hacker@evil.com",
      role: "OWNER",
      branchId: "br_001",
      branchName: "Head Office",
      branchManagerUids: [],
      teamId: "",
      teamName: "",
      isActive: true,
      ...ownerPerms(),
      createdByUid: INTRUDER_UID,
      createdDateDdMmYyyy: "26-10-2026",
      createdAtMillis: 1700000009000,
      createdAt: ts,
      updatedAt: ts,
    })
  );
});

test("3. Only Owner can create employee invites; Branch Manager and Agent are blocked", async () => {
  await seedInitializedOrg();
  const ts = pastTimestamp();
  const invitePayload = {
    employeeCode: "EMP-0005",
    orgOwnerUid: OWNER_UID,
    fullName: "Suresh Agent",
    mobile: "9876543222",
    invitedEmail: "suresh@company.com",
    invitedUid: "",
    role: "COLLECTION_AGENT",
    branchId: "br_001",
    branchName: "Chennai Main",
    teamId: "",
    teamName: "",
    isActive: true,
    status: "PENDING",
    claimedByUid: "",
    ...agentPerms(),
    createdByUid: MGR_BR1_UID,
    createdDateDdMmYyyy: "26-10-2026",
    createdAtMillis: 1700000010000,
    createdAt: ts,
    updatedAt: ts,
  };

  const mgrDb = testEnv.authenticatedContext(MGR_BR1_UID).firestore();
  await assertFails(mgrDb.collection("employee_invites").doc("EMP-0005").set(invitePayload));

  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
  await assertSucceeds(
    ownerDb.collection("employee_invites").doc("EMP-0005").set({
      ...invitePayload,
      createdByUid: OWNER_UID,
    })
  );
});

test("4. Zero Passwords & Shadow Update Guard: storing password fields is strictly rejected", async () => {
  await seedInitializedOrg();
  const ts = pastTimestamp();
  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();

  await assertFails(
    ownerDb.collection("employee_invites").doc("EMP-0006").set({
      employeeCode: "EMP-0006",
      orgOwnerUid: OWNER_UID,
      fullName: "Bad Password Record",
      mobile: "9876543233",
      invitedEmail: "bad@company.com",
      invitedUid: "",
      role: "COLLECTION_AGENT",
      branchId: "br_001",
      branchName: "Chennai Main",
      teamId: "",
      teamName: "",
      isActive: true,
      status: "PENDING",
      claimedByUid: "",
      password: "plaintext_forbidden",
      ...agentPerms(),
      createdByUid: OWNER_UID,
      createdDateDdMmYyyy: "26-10-2026",
      createdAtMillis: 1700000010000,
      createdAt: ts,
      updatedAt: ts,
    })
  );
});

test("5. Email Spoofing Guard & Privilege Escalation Guard on Invite Claim", async () => {
  await seedInitializedOrg();
  const ts = pastTimestamp();
  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
  await assertSucceeds(
    ownerDb.collection("employee_invites").doc("EMP-0009").set({
      employeeCode: "EMP-0009",
      orgOwnerUid: OWNER_UID,
      fullName: "Meena Agent",
      mobile: "9876543299",
      invitedEmail: "meena@company.com",
      invitedUid: "",
      role: "COLLECTION_AGENT",
      branchId: "br_001",
      branchName: "Chennai Main",
      teamId: "",
      teamName: "",
      isActive: true,
      status: "PENDING",
      claimedByUid: "",
      ...agentPerms(),
      createdByUid: OWNER_UID,
      createdDateDdMmYyyy: "26-10-2026",
      createdAtMillis: 1700000010000,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Unverified email spoofer MUST be blocked
  const unverifiedSpooferDb = testEnv.authenticatedContext(AGENT_B_UID, {
    email: "meena@company.com",
    email_verified: false,
  }).firestore();
  await assertFails(unverifiedSpooferDb.collection("employee_invites").doc("EMP-0009").get());

  // Verified user attempting to escalate role from COLLECTION_AGENT to OWNER during claim MUST be blocked
  const verifiedMeenaDb = testEnv.authenticatedContext(AGENT_B_UID, {
    email: "meena@company.com",
    email_verified: true,
  }).firestore();
  const badBatch = verifiedMeenaDb.batch();
  badBatch.update(verifiedMeenaDb.collection("employee_invites").doc("EMP-0009"), {
    status: "CLAIMED",
    claimedByUid: AGENT_B_UID,
    updatedAt: ts,
  });
  badBatch.set(verifiedMeenaDb.collection("employees").doc(AGENT_B_UID), {
    userId: AGENT_B_UID,
    orgOwnerUid: OWNER_UID,
    employeeCode: "EMP-0009",
    fullName: "Meena Agent",
    mobile: "9876543299",
    email: "meena@company.com",
    role: "OWNER", // Tampered role!
    branchId: "br_001",
    branchName: "Chennai Main",
    branchManagerUids: [MGR_BR1_UID],
    teamId: "",
    teamName: "",
    isActive: true,
    ...agentPerms(),
    createdByUid: OWNER_UID,
    createdDateDdMmYyyy: "26-10-2026",
    createdAtMillis: 1700000012000,
    createdAt: ts,
    updatedAt: ts,
  });
  await assertFails(badBatch.commit());

  // Verified user claiming exact invited COLLECTION_AGENT role & permissions succeeds
  const validBatch = verifiedMeenaDb.batch();
  validBatch.update(verifiedMeenaDb.collection("employee_invites").doc("EMP-0009"), {
    status: "CLAIMED",
    claimedByUid: AGENT_B_UID,
    updatedAt: ts,
  });
  validBatch.set(verifiedMeenaDb.collection("employees").doc(AGENT_B_UID), {
    userId: AGENT_B_UID,
    orgOwnerUid: OWNER_UID,
    employeeCode: "EMP-0009",
    fullName: "Meena Agent",
    mobile: "9876543299",
    email: "meena@company.com",
    role: "COLLECTION_AGENT",
    branchId: "br_001",
    branchName: "Chennai Main",
    branchManagerUids: [MGR_BR1_UID],
    teamId: "",
    teamName: "",
    isActive: true,
    ...agentPerms(),
    createdByUid: OWNER_UID,
    createdDateDdMmYyyy: "26-10-2026",
    createdAtMillis: 1700000012000,
    createdAt: ts,
    updatedAt: ts,
  });
  await assertSucceeds(validBatch.commit());
});

test("6. Employee cannot self-update their own role or permissions", async () => {
  await seedInitializedOrg();
  const agentDb = testEnv.authenticatedContext(AGENT_A_UID).firestore();
  await assertFails(
    agentDb.collection("employees").doc(AGENT_A_UID).update({
      role: "OWNER",
      canApproveLoan: true,
      updatedAt: pastTimestamp(),
    })
  );
});

test("7. Role-Based Customer & Loan Access + Query Alignment Enforcement", async () => {
  await seedInitializedOrg();
  const ts = pastTimestamp();

  // Seed 2 customers: cust_1 in BR-001 assigned to AGENT_A, cust_2 in BR-002 assigned to MGR_BR2
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("customers").doc("cust_br1_a").set({
      orgOwnerUid: OWNER_UID,
      branchManagerUids: [MGR_BR1_UID],
      assignedAgentUid: AGENT_A_UID,
      createdByUid: AGENT_A_UID,
      customerCode: "CUST-0001",
      fullName: "Lakshmi Devi",
      guardianName: "Murugan",
      mobile: "9123456780",
      alternateMobile: "",
      address: "12 Bazaar Street",
      city: "Chennai",
      kycDocumentType: "AADHAAR_CARD",
      kycDocumentNumber: "123456789012",
      occupation: "Retail Shop",
      monthlyIncomePaise: 3500000,
      branchId: "br_001",
      branchName: "Chennai Main",
      teamId: "",
      assignedAgentName: "Arun Kumar",
      createdDateDdMmYyyy: "26-10-2026",
      createdAtMillis: 1700000020000,
      createdAt: ts,
      updatedAt: ts,
    });
    await db.collection("customers").doc("cust_br2_x").set({
      orgOwnerUid: OWNER_UID,
      branchManagerUids: [MGR_BR2_UID],
      assignedAgentUid: MGR_BR2_UID,
      createdByUid: OWNER_UID,
      customerCode: "CUST-0002",
      fullName: "Karthik Raja",
      guardianName: "Raja",
      mobile: "9123456789",
      alternateMobile: "",
      address: "88 Temple Road",
      city: "Madurai",
      kycDocumentType: "PAN_CARD",
      kycDocumentNumber: "ABCDE1234F",
      occupation: "Textiles",
      monthlyIncomePaise: 4500000,
      branchId: "br_002",
      branchName: "Madurai South",
      teamId: "",
      assignedAgentName: "Madurai Manager",
      createdDateDdMmYyyy: "26-10-2026",
      createdAtMillis: 1700000021000,
      createdAt: ts,
      updatedAt: ts,
    });
  });

  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
  const mgr1Db = testEnv.authenticatedContext(MGR_BR1_UID).firestore();
  const agentADb = testEnv.authenticatedContext(AGENT_A_UID).firestore();

  // Unfiltered query MUST fail (rules are not filters)
  await assertFails(agentADb.collection("customers").get());
  await assertFails(mgr1Db.collection("customers").get());

  // Aligned queries succeed for permitted scopes
  await assertSucceeds(ownerDb.collection("customers").where("orgOwnerUid", "==", OWNER_UID).get());
  await assertSucceeds(mgr1Db.collection("customers").where("branchManagerUids", "array-contains", MGR_BR1_UID).get());
  await assertSucceeds(agentADb.collection("customers").where("assignedAgentUid", "==", AGENT_A_UID).get());

  // Cross-branch get by Branch Manager 1 on Branch 2 customer MUST fail
  await assertFails(mgr1Db.collection("customers").doc("cust_br2_x").get());
  // Cross-agent get by Agent A on Customer 2 MUST fail
  await assertFails(agentADb.collection("customers").doc("cust_br2_x").get());
});

test("8. Loan Approval, Rejection, Disbursement Workflow & Closed Loan Immutability enforced", async () => {
  await seedInitializedOrg();
  const ts = pastTimestamp();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("customers").doc("cust_br1_a").set({
      orgOwnerUid: OWNER_UID,
      branchManagerUids: [MGR_BR1_UID],
      assignedAgentUid: AGENT_A_UID,
      createdByUid: AGENT_A_UID,
      customerCode: "CUST-0001-TEST",
      fullName: "Lakshmi Devi",
      guardianName: "Murugan",
      mobile: "9123456780",
      alternateMobile: "",
      address: "12 Bazaar Street",
      city: "Chennai",
      kycDocumentType: "AADHAAR_CARD",
      kycDocumentNumber: "123456789012",
      occupation: "Retail Shop",
      monthlyIncomePaise: 3500000,
      branchId: "br_001",
      branchName: "Chennai Main",
      teamId: "",
      assignedAgentName: "Arun Kumar",
      createdDateDdMmYyyy: "26-10-2026",
      createdAtMillis: 1700000020000,
      createdAt: ts,
      updatedAt: ts,
    });
  });
  const mgr1Db = testEnv.authenticatedContext(MGR_BR1_UID).firestore();
  const agentADb = testEnv.authenticatedContext(AGENT_A_UID).firestore();

  const baseLoan = {
    orgOwnerUid: OWNER_UID,
    branchManagerUids: [MGR_BR1_UID],
    assignedAgentUid: AGENT_A_UID,
    createdByUid: MGR_BR1_UID,
    approvedByUid: "",
    rejectedByUid: "",
    rejectionReason: "",
    disbursedByUid: "",
    loanCode: "LN-0001-TEST",
    customerId: "cust_br1_a",
    customerCode: "CUST-0001-TEST",
    customerName: "Lakshmi Devi",
    customerMobile: "9123456780",
    branchId: "br_001",
    branchName: "Chennai Main",
    teamId: "",
    assignedAgentName: "Arun Kumar",
    productType: "DAILY_BUSINESS_EDI",
    principalPaise: 2500000,
    annualInterestRateBps: 1800,
    processingFeePaise: 50000,
    totalInterestPaise: 123288,
    totalPayablePaise: 2623288,
    installmentAmountPaise: 26233,
    repaymentFrequency: "DAILY",
    totalInstallments: 100,
    paidAmountPaise: 0,
    advanceBalancePaise: 0,
    outstandingPaise: 2623288,
    status: "PENDING_APPROVAL",
    applicationDateDdMmYyyy: "26-10-2026",
    approvedDateDdMmYyyy: "",
    approvalRemarks: "",
    disbursementDateDdMmYyyy: "26-10-2026",
    disbursementMode: "CASH",
    disbursementReference: "",
    netDisbursedPaise: 0,
    firstDueDateDdMmYyyy: "27-10-2026",
    maturityDateDdMmYyyy: "03-02-2027",
    closedDateDdMmYyyy: "",
    createdAtMillis: 1700000030000,
    createdAt: ts,
    updatedAt: ts,
  };

  // Branch Manager creates PENDING_APPROVAL loan
  await assertSucceeds(mgr1Db.collection("loans").doc("loan_workflow_1").set(baseLoan));

  // Agent without canApproveLoan CANNOT approve or disburse loan
  await assertFails(
    agentADb.collection("loans").doc("loan_workflow_1").update({
      status: "APPROVED",
      approvedByUid: AGENT_A_UID,
      approvedDateDdMmYyyy: "26-10-2026",
      approvalRemarks: "Unauthorized",
      updatedAt: ts,
    })
  );

  // Branch Manager approves application -> APPROVED
  await assertSucceeds(
    mgr1Db.collection("loans").doc("loan_workflow_1").update({
      status: "APPROVED",
      approvedByUid: MGR_BR1_UID,
      approvedDateDdMmYyyy: "26-10-2026",
      approvalRemarks: "Sanctioned after shop verification",
      updatedAt: ts,
    })
  );

  // Branch Manager disburses APPROVED loan -> ACTIVE
  await assertSucceeds(
    mgr1Db.collection("loans").doc("loan_workflow_1").update({
      status: "ACTIVE",
      disbursedByUid: MGR_BR1_UID,
      disbursementDateDdMmYyyy: "27-10-2026",
      disbursementMode: "UPI",
      disbursementReference: "UTR88990011",
      netDisbursedPaise: 2450000,
      firstDueDateDdMmYyyy: "28-10-2026",
      maturityDateDdMmYyyy: "04-02-2027",
      updatedAt: ts,
    })
  );

  // Close loan via full repayment -> COMPLETED
  await assertSucceeds(
    agentADb.collection("loans").doc("loan_workflow_1").update({
      status: "COMPLETED",
      paidAmountPaise: 2623288,
      outstandingPaise: 0,
      closedDateDdMmYyyy: "04-02-2027",
      updatedAt: ts,
    })
  );

  // Terminal-State Protection: Closed historical loan CANNOT have its balance or status mutated!
  await assertFails(
    mgr1Db.collection("loans").doc("loan_workflow_1").update({
      status: "ACTIVE",
      outstandingPaise: 500000,
      updatedAt: ts,
    })
  );

  // Create second loan application and test REJECTED workflow (must require reason >= 3 chars)
  await assertSucceeds(
    mgr1Db.collection("loans").doc("loan_workflow_2").set({
      ...baseLoan,
      loanCode: "LN-0002-TEST",
    })
  );
  await assertFails(
    mgr1Db.collection("loans").doc("loan_workflow_2").update({
      status: "REJECTED",
      rejectedByUid: MGR_BR1_UID,
      rejectionReason: "", // Too short -> must fail
      outstandingPaise: 0,
      closedDateDdMmYyyy: "26-10-2026",
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    mgr1Db.collection("loans").doc("loan_workflow_2").update({
      status: "REJECTED",
      rejectedByUid: MGR_BR1_UID,
      rejectionReason: "Insufficient shop cashflow for second concurrent loan",
      outstandingPaise: 0,
      closedDateDdMmYyyy: "26-10-2026",
      updatedAt: ts,
    })
  );
});

test("9. Payment Creation, Permanent Non-Deletion (allow delete: if false), and Amount Immutability", async () => {
  await seedInitializedOrg();
  const ts = pastTimestamp();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("customers").doc("cust_pay_1").set({
      orgOwnerUid: OWNER_UID,
      branchManagerUids: [MGR_BR1_UID],
      assignedAgentUid: AGENT_A_UID,
      createdByUid: AGENT_A_UID,
      customerCode: "CUST-0091",
      fullName: "Ramesh Traders",
      guardianName: "Subramani",
      mobile: "9884011223",
      alternateMobile: "",
      address: "44 Market Street",
      city: "Chennai",
      kycDocumentType: "AADHAAR_CARD",
      kycDocumentNumber: "445566778899",
      occupation: "Provision Store",
      monthlyIncomePaise: 4000000,
      branchId: "br_001",
      branchName: "Chennai Main",
      teamId: "",
      assignedAgentName: "Arun Kumar",
      createdDateDdMmYyyy: "26-10-2026",
      createdAtMillis: 1700000020000,
      createdAt: ts,
      updatedAt: ts,
    });
    await db.collection("loans").doc("loan_pay_1").set({
      orgOwnerUid: OWNER_UID,
      branchManagerUids: [MGR_BR1_UID],
      assignedAgentUid: AGENT_A_UID,
      createdByUid: MGR_BR1_UID,
      approvedByUid: MGR_BR1_UID,
      approvedDateDdMmYyyy: "26-10-2026",
      approvalRemarks: "Approved",
      rejectedByUid: "",
      rejectionReason: "",
      disbursedByUid: MGR_BR1_UID,
      disbursementMode: "CASH",
      disbursementReference: "",
      netDisbursedPaise: 1000000,
      closedDateDdMmYyyy: "",
      loanCode: "LN-0091",
      customerId: "cust_pay_1",
      customerCode: "CUST-0091",
      customerName: "Ramesh Traders",
      customerMobile: "9884011223",
      branchId: "br_001",
      branchName: "Chennai Main",
      teamId: "",
      assignedAgentName: "Arun Kumar",
      productType: "DAILY_BUSINESS_EDI",
      principalPaise: 1000000,
      annualInterestRateBps: 1800,
      processingFeePaise: 0,
      totalInterestPaise: 50000,
      totalPayablePaise: 1050000,
      installmentAmountPaise: 105000,
      repaymentFrequency: "DAILY",
      totalInstallments: 10,
      paidAmountPaise: 0,
      advanceBalancePaise: 0,
      outstandingPaise: 1050000,
      status: "ACTIVE",
      applicationDateDdMmYyyy: "26-10-2026",
      disbursementDateDdMmYyyy: "26-10-2026",
      firstDueDateDdMmYyyy: "27-10-2026",
      maturityDateDdMmYyyy: "05-11-2026",
      createdAtMillis: 1700000030000,
      createdAt: ts,
      updatedAt: ts,
    });
  });

  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
  const mgr1Db = testEnv.authenticatedContext(MGR_BR1_UID).firestore();
  const agentADb = testEnv.authenticatedContext(AGENT_A_UID).firestore();

  const paymentDoc = {
    orgOwnerUid: OWNER_UID,
    branchManagerUids: [MGR_BR1_UID],
    assignedAgentUid: AGENT_A_UID,
    collectedByUid: AGENT_A_UID,
    transactionId: "TXN-20261026-AA11BB22",
    receiptNumber: "RCPT-0001-AA11",
    idempotencyKey: "idem_key_0001_aa11bb22",
    loanId: "loan_pay_1",
    loanCode: "LN-0091",
    customerId: "cust_pay_1",
    customerCode: "CUST-0091",
    customerName: "Ramesh Traders",
    branchId: "br_001",
    branchName: "Chennai Main",
    collectedByName: "Arun Kumar",
    amountPaise: 105000,
    allocatedToInstallmentsPaise: 105000,
    creditedToAdvancePaise: 0,
    allocationsSummary: "#1 (27-10-2026): ₹1,050 [PAID]",
    paymentMode: "UPI",
    paymentType: "REGULAR_INSTALLMENT",
    referenceNumber: "UTR9988776655",
    remarks: "First EDI collected",
    collectionDateDdMmYyyy: "27-10-2026",
    collectedAtMillis: 1700000040000,
    serverConfirmationId: "FS-ACK-RCPT-0001-AA11",
    recordStatus: "ACTIVE",
    reversedByUid: "",
    reversedByName: "",
    reversedDateDdMmYyyy: "",
    reversalReason: "",
    adjustmentId: "",
    replacementPaymentId: "",
    correctsPaymentId: "",
    createdAt: ts,
    updatedAt: ts,
  };

  // Authorized Manager creates valid 21-field installment_schedule -> succeeds
  await assertSucceeds(
    mgr1Db.collection("installment_schedules").doc("sched_0091").set({
      orgOwnerUid: OWNER_UID,
      branchManagerUids: [MGR_BR1_UID],
      assignedAgentUid: AGENT_A_UID,
      loanId: "loan_pay_1",
      loanCode: "LN-0091",
      customerId: "cust_pay_1",
      customerCode: "CUST-0091",
      customerName: "Ramesh Traders",
      customerMobile: "9884011223",
      branchId: "br_001",
      installmentNumber: 1,
      dueDateDdMmYyyy: "27-10-2026",
      dueDateEpochMillis: 1700000040000,
      frequency: "DAILY",
      scheduledAmountPaise: 105000,
      paidAmountPaise: 0,
      status: "UPCOMING",
      paidDateDdMmYyyy: "",
      lastPaymentId: "",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Assigned Collection Agent creates valid ACTIVE payment -> succeeds
  await assertSucceeds(agentADb.collection("payments").doc("pay_0091").set(paymentDoc));

  // Shadow field injection MUST fail
  await assertFails(
    agentADb.collection("payments").doc("pay_shadow").set({
      ...paymentDoc,
      transactionId: "TXN-20261026-SHADOW1",
      receiptNumber: "RCPT-0002-SHDW",
      idempotencyKey: "idem_shadow_002",
      ghostField: true,
    })
  );

  // CRITICAL: Permanent deletion of confirmed payments MUST ALWAYS fail (even for Owner & Branch Manager)
  await assertFails(agentADb.collection("payments").doc("pay_0091").delete());
  await assertFails(mgr1Db.collection("payments").doc("pay_0091").delete());
  await assertFails(ownerDb.collection("payments").doc("pay_0091").delete());

  // Mutating original amountPaise MUST fail
  await assertFails(
    ownerDb.collection("payments").doc("pay_0091").update({
      amountPaise: 50000,
      updatedAt: ts,
    })
  );
});

test("10. Payment Reversal & Correction History Enforces Atomic Adjustment Record and Blocks Unauthorized Agents", async () => {
  await seedInitializedOrg();
  const ts = pastTimestamp();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("customers").doc("cust_rev_1").set({
      orgOwnerUid: OWNER_UID,
      branchManagerUids: [MGR_BR1_UID],
      assignedAgentUid: AGENT_A_UID,
      createdByUid: AGENT_A_UID,
      customerCode: "CUST-0092",
      fullName: "Selvi Textiles",
      guardianName: "Kannan",
      mobile: "9884099887",
      alternateMobile: "",
      address: "19 Gandhi Road",
      city: "Chennai",
      kycDocumentType: "AADHAAR_CARD",
      kycDocumentNumber: "998877665544",
      occupation: "Textiles",
      monthlyIncomePaise: 5000000,
      branchId: "br_001",
      branchName: "Chennai Main",
      teamId: "",
      assignedAgentName: "Arun Kumar",
      createdDateDdMmYyyy: "26-10-2026",
      createdAtMillis: 1700000020000,
      createdAt: ts,
      updatedAt: ts,
    });
    await db.collection("loans").doc("loan_rev_1").set({
      orgOwnerUid: OWNER_UID,
      branchManagerUids: [MGR_BR1_UID],
      assignedAgentUid: AGENT_A_UID,
      createdByUid: MGR_BR1_UID,
      approvedByUid: MGR_BR1_UID,
      approvedDateDdMmYyyy: "26-10-2026",
      approvalRemarks: "Approved",
      rejectedByUid: "",
      rejectionReason: "",
      disbursedByUid: MGR_BR1_UID,
      disbursementMode: "CASH",
      disbursementReference: "",
      netDisbursedPaise: 1000000,
      closedDateDdMmYyyy: "27-10-2026",
      loanCode: "LN-0092",
      customerId: "cust_rev_1",
      customerCode: "CUST-0092",
      customerName: "Selvi Textiles",
      customerMobile: "9884099887",
      branchId: "br_001",
      branchName: "Chennai Main",
      teamId: "",
      assignedAgentName: "Arun Kumar",
      productType: "DAILY_BUSINESS_EDI",
      principalPaise: 1000000,
      annualInterestRateBps: 1800,
      processingFeePaise: 0,
      totalInterestPaise: 50000,
      totalPayablePaise: 1050000,
      installmentAmountPaise: 1050000,
      repaymentFrequency: "DAILY",
      totalInstallments: 1,
      paidAmountPaise: 1050000,
      advanceBalancePaise: 0,
      outstandingPaise: 0,
      status: "COMPLETED",
      applicationDateDdMmYyyy: "26-10-2026",
      disbursementDateDdMmYyyy: "26-10-2026",
      firstDueDateDdMmYyyy: "27-10-2026",
      maturityDateDdMmYyyy: "27-10-2026",
      createdAtMillis: 1700000030000,
      createdAt: ts,
      updatedAt: ts,
    });
    await db.collection("installment_schedules").doc("sched_rev_1").set({
      orgOwnerUid: OWNER_UID,
      branchManagerUids: [MGR_BR1_UID],
      assignedAgentUid: AGENT_A_UID,
      loanId: "loan_rev_1",
      loanCode: "LN-0092",
      customerId: "cust_rev_1",
      customerCode: "CUST-0092",
      customerName: "Selvi Textiles",
      customerMobile: "9884099887",
      branchId: "br_001",
      installmentNumber: 1,
      dueDateDdMmYyyy: "27-10-2026",
      dueDateEpochMillis: 1700000050000,
      frequency: "DAILY",
      scheduledAmountPaise: 1050000,
      paidAmountPaise: 1050000,
      status: "PAID",
      paidDateDdMmYyyy: "27-10-2026",
      lastPaymentId: "pay_rev_orig",
      createdAt: ts,
      updatedAt: ts,
    });
    await db.collection("payments").doc("pay_rev_orig").set({
      orgOwnerUid: OWNER_UID,
      branchManagerUids: [MGR_BR1_UID],
      assignedAgentUid: AGENT_A_UID,
      collectedByUid: AGENT_A_UID,
      transactionId: "TXN-20261026-ORIG99",
      receiptNumber: "RCPT-0099-ORIG",
      idempotencyKey: "idem_orig_99",
      loanId: "loan_rev_1",
      loanCode: "LN-0092",
      customerId: "cust_rev_1",
      customerCode: "CUST-0092",
      customerName: "Selvi Textiles",
      branchId: "br_001",
      branchName: "Chennai Main",
      collectedByName: "Arun Kumar",
      amountPaise: 1050000,
      allocatedToInstallmentsPaise: 1050000,
      creditedToAdvancePaise: 0,
      allocationsSummary: "#1 (27-10-2026): ₹10,500 [PAID]",
      paymentMode: "CHEQUE",
      paymentType: "REGULAR_INSTALLMENT",
      referenceNumber: "CHQ778899",
      remarks: "Cheque deposit",
      collectionDateDdMmYyyy: "27-10-2026",
      collectedAtMillis: 1700000060000,
      serverConfirmationId: "FS-ACK-RCPT-0099-ORIG",
      recordStatus: "ACTIVE",
      reversedByUid: "",
      reversedByName: "",
      reversedDateDdMmYyyy: "",
      reversalReason: "",
      adjustmentId: "",
      replacementPaymentId: "",
      correctsPaymentId: "",
      createdAt: ts,
      updatedAt: ts,
    });
  });

  const mgr1Db = testEnv.authenticatedContext(MGR_BR1_UID).firestore();
  const agentADb = testEnv.authenticatedContext(AGENT_A_UID).firestore();
  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();

  // Collection Agent CANNOT reverse a payment even if they create an adjustment batch
  const agentBatch = agentADb.batch();
  agentBatch.update(agentADb.collection("payments").doc("pay_rev_orig"), {
    recordStatus: "REVERSED",
    reversedByUid: AGENT_A_UID,
    reversedByName: "Arun Kumar",
    reversedDateDdMmYyyy: "28-10-2026",
    reversalReason: "Agent attempting unauthorized reversal",
    adjustmentId: "adj_agent_bad",
    replacementPaymentId: "",
    updatedAt: ts,
  });
  agentBatch.set(agentADb.collection("payment_adjustments").doc("adj_agent_bad"), {
    orgOwnerUid: OWNER_UID,
    branchManagerUids: [MGR_BR1_UID],
    assignedAgentUid: AGENT_A_UID,
    performedByUid: AGENT_A_UID,
    performedByName: "Arun Kumar",
    adjustmentCode: "ADJ-BAD1",
    adjustmentType: "REVERSAL",
    loanId: "loan_rev_1",
    loanCode: "LN-0092",
    customerId: "cust_rev_1",
    customerCode: "CUST-0092",
    customerName: "Selvi Textiles",
    branchId: "br_001",
    originalPaymentId: "pay_rev_orig",
    originalTransactionId: "TXN-20261026-ORIG99",
    originalReceiptNumber: "RCPT-0099-ORIG",
    originalAmountPaise: 1050000,
    replacementPaymentId: "",
    replacementTransactionId: "",
    replacementReceiptNumber: "",
    replacementAmountPaise: 0,
    reason: "Agent attempting unauthorized reversal",
    performedDateDdMmYyyy: "28-10-2026",
    createdAtMillis: 1700000070000,
    createdAt: ts,
    updatedAt: ts,
  });
  await assertFails(agentBatch.commit());

  // Branch Manager reversing WITHOUT creating /payment_adjustments/{adjustmentId} MUST fail (existsAfter guard)
  await assertFails(
    mgr1Db.collection("payments").doc("pay_rev_orig").update({
      recordStatus: "REVERSED",
      reversedByUid: MGR_BR1_UID,
      reversedByName: "Priya Nair",
      reversedDateDdMmYyyy: "28-10-2026",
      reversalReason: "Cheque dishonoured by bank",
      adjustmentId: "adj_missing",
      replacementPaymentId: "",
      updatedAt: ts,
    })
  );

  // Authorized Branch Manager atomic Reversal (updates payment status, creates adjustment, reopens schedule & loan) succeeds!
  const mgrBatch = mgr1Db.batch();
  mgrBatch.update(mgr1Db.collection("payments").doc("pay_rev_orig"), {
    recordStatus: "REVERSED",
    reversedByUid: MGR_BR1_UID,
    reversedByName: "Priya Nair",
    reversedDateDdMmYyyy: "28-10-2026",
    reversalReason: "Cheque dishonoured by bank",
    adjustmentId: "adj_rev_001",
    replacementPaymentId: "",
    updatedAt: ts,
  });
  mgrBatch.set(mgr1Db.collection("payment_adjustments").doc("adj_rev_001"), {
    orgOwnerUid: OWNER_UID,
    branchManagerUids: [MGR_BR1_UID],
    assignedAgentUid: AGENT_A_UID,
    performedByUid: MGR_BR1_UID,
    performedByName: "Priya Nair",
    adjustmentCode: "ADJ-0001-REV",
    adjustmentType: "REVERSAL",
    loanId: "loan_rev_1",
    loanCode: "LN-0092",
    customerId: "cust_rev_1",
    customerCode: "CUST-0092",
    customerName: "Selvi Textiles",
    branchId: "br_001",
    originalPaymentId: "pay_rev_orig",
    originalTransactionId: "TXN-20261026-ORIG99",
    originalReceiptNumber: "RCPT-0099-ORIG",
    originalAmountPaise: 1050000,
    replacementPaymentId: "",
    replacementTransactionId: "",
    replacementReceiptNumber: "",
    replacementAmountPaise: 0,
    reason: "Cheque dishonoured by bank",
    performedDateDdMmYyyy: "28-10-2026",
    createdAtMillis: 1700000075000,
    createdAt: ts,
    updatedAt: ts,
  });
  mgrBatch.update(mgr1Db.collection("installment_schedules").doc("sched_rev_1"), {
    paidAmountPaise: 0,
    status: "UPCOMING",
    paidDateDdMmYyyy: "",
    lastPaymentId: "",
    updatedAt: ts,
  });
  mgrBatch.update(mgr1Db.collection("loans").doc("loan_rev_1"), {
    paidAmountPaise: 0,
    outstandingPaise: 1050000,
    status: "ACTIVE",
    closedDateDdMmYyyy: "",
    updatedAt: ts,
  });
  await assertSucceeds(mgrBatch.commit());

  // PaymentAdjustment history CANNOT be deleted even by Owner
  await assertFails(ownerDb.collection("payment_adjustments").doc("adj_rev_001").delete());
});


