package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.base.FirestoreEmulatorTestBase
import com.example.data.local.EmployeeEntity
import com.example.data.local.LoanFinanceDatabase
import com.example.data.local.encodeUidList
import com.example.data.session.SessionStore
import com.example.data.sync.SyncEngine
import com.example.domain.model.InstallmentStatus
import com.example.domain.model.LoanProductType
import com.example.domain.model.LoanStatus
import com.example.domain.model.PaymentMode
import com.example.domain.model.PaymentRecordStatus
import com.example.domain.model.PaymentType
import com.example.domain.model.RepaymentFrequency
import com.example.domain.model.RolePermissionTemplate
import com.example.domain.model.SyncStatus
import com.example.domain.model.UserRole
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class FakeSessionStore : SessionStore {
  private val activeId = MutableStateFlow<String?>(null)
  override val activeEmployeeIdFlow: Flow<String?> = activeId
  override suspend fun setActiveEmployeeId(employeeId: String?) {
    activeId.value = employeeId
  }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LoanFinanceRepositoryRuleTest : FirestoreEmulatorTestBase() {

  private lateinit var db: LoanFinanceDatabase
  private lateinit var authRepo: AuthAndOrgRepository
  private lateinit var loanRepo: CustomerLoanRepository

  @Before
  override fun setUpFirebase() {
    super.setUpFirebase()
    val context = ApplicationProvider.getApplicationContext<Context>()
    db = Room.inMemoryDatabaseBuilder(context, LoanFinanceDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    val syncEngine = SyncEngine(dao = db.dao(), firestore = firestore)
    authRepo = AuthAndOrgRepository(
      dao = db.dao(),
      sessionStore = FakeSessionStore(),
      firestore = firestore,
      firebaseAuth = auth,
    )
    loanRepo = CustomerLoanRepository(
      dao = db.dao(),
      syncEngine = syncEngine,
      firestore = firestore,
      firebaseAuth = auth,
    )
  }

  @After
  override fun tearDownFirebase() {
    if (::db.isInitialized) {
      db.close()
    }
    super.tearDownFirebase()
  }

  private fun assertPermissionDenied(error: Throwable?) {
    assertNotNull("Expected PERMISSION_DENIED error but call succeeded", error)
    var cur: Throwable? = error
    while (cur is CancellationException && cur.cause != null) {
      cur = cur.cause
    }
    if (cur !is FirebaseFirestoreException && cur?.cause is FirebaseFirestoreException) {
      cur = cur.cause
    }
    assertTrue(
      "Expected FirebaseFirestoreException(PERMISSION_DENIED) but got: $cur",
      cur is FirebaseFirestoreException && cur.code == FirebaseFirestoreException.Code.PERMISSION_DENIED,
    )
  }

  @Test
  fun unauthenticatedReadsAndLists_areRejectedWithPermissionDenied() = runEmulatorTest {
      auth.signOut()

      val fakeActor = EmployeeEntity(
        id = "anon-uid",
        orgOwnerUid = "owner-uid",
        employeeCode = "EMP-0001",
        fullName = "Anon",
        mobile = "9876543210",
        email = "anon@example.com",
        role = UserRole.OWNER,
        branchId = "br-1",
        branchName = "HO",
        branchManagerUidsCsv = encodeUidList(listOf("owner-uid")),
        teamId = null,
        teamName = null,
        isActive = true,
        canRegisterCustomer = true,
        canCreateLoan = true,
        canApproveLoan = true,
        canCollectPayment = true,
        canManageAdvance = true,
        canRequestPreClosure = true,
        canApprovePreClosure = true,
        canUploadDocuments = true,
        canManageTeams = true,
        canViewReports = true,
        canViewAuditLogs = true,
        createdByEmployeeId = "owner-uid",
        createdDateDdMmYyyy = "26-10-2026",
        createdAtMillis = 1_700_000_000_000L,
      )

      val customerListErr = runCatching { loanRepo.observeCloudCustomers(fakeActor).first() }.exceptionOrNull()
      assertPermissionDenied(customerListErr)

      val loanListErr = runCatching { loanRepo.observeCloudLoans(fakeActor).first() }.exceptionOrNull()
      assertPermissionDenied(loanListErr)

      val branchListErr = runCatching { authRepo.observeCloudBranches(fakeActor).first() }.exceptionOrNull()
      assertPermissionDenied(branchListErr)
  }

  @Test
  fun ownerBootstrapOnce_andSecondOwnerBootstrapOrSelfRegistration_deniedByRules() = runEmulatorTest {
      val ownerUid = signInTestUser("owner@samruddhi.in")

      // First run -> NeedsOwnerSetup
      val firstResolution = authRepo.resolveAuthenticatedSession().getOrThrow()
      assertTrue(firstResolution is CloudAuthResolution.NeedsOwnerSetup)

      val owner = authRepo.initializeOwnerAccount(
        ownerFullName = "Rajesh Sharma",
        mobile = "9876543210",
        email = "owner@samruddhi.in",
        orgName = "Samruddhi Finance",
        headOfficeBranchName = "Mumbai Head Office",
        headOfficeCity = "Mumbai",
        headOfficeAddress = "Nariman Point",
      ).getOrThrow()

      assertEquals(ownerUid, owner.id)
      assertEquals(UserRole.OWNER, owner.role)
      assertEquals("EMP-0001", owner.employeeCode)

      // Sign in as a second uninvited Google user -> Must get AccessNotProvisioned
      signInTestUser("intruder@gmail.com")
      val intruderResolution = authRepo.resolveAuthenticatedSession().getOrThrow()
      assertTrue(intruderResolution is CloudAuthResolution.AccessNotProvisioned)

      // Intruder attempting to overwrite /org_config/main or bootstrap a second Owner is rejected by Rules
      val hijackAttempt = authRepo.initializeOwnerAccount(
        ownerFullName = "Intruder",
        mobile = "9111222333",
        email = "intruder@gmail.com",
        orgName = "Hijacked Org",
        headOfficeBranchName = "Fake HO",
        headOfficeCity = "Delhi",
        headOfficeAddress = "CP",
      )
      assertPermissionDenied(hijackAttempt.exceptionOrNull())
  }

  @Test
  fun ownerOnlyEmployeeCreation_verifiedInviteClaim_andPrivilegeEscalationBlocked() = runEmulatorTest {
      signInTestUser("owner@finance.in")
      val owner = authRepo.initializeOwnerAccount(
        ownerFullName = "Kiran Kulkarni",
        mobile = "9876543210",
        email = "owner@finance.in",
        orgName = "Kiran Microfinance",
        headOfficeBranchName = "Pune Main",
        headOfficeCity = "Pune",
        headOfficeAddress = "FC Road",
      ).getOrThrow()

      // Owner provisions Branch Manager invite for anita@finance.in (zero passwords!)
      val mgrInvite = authRepo.createEmployeeInvite(
        actor = owner,
        fullName = "Anita Deshmukh",
        mobile = "9123456780",
        invitedEmail = "anita@finance.in",
        assignedRole = UserRole.BRANCH_MANAGER,
        branchId = owner.branchId,
        teamId = null,
        teamName = null,
        permissions = RolePermissionTemplate.defaultsFor(UserRole.BRANCH_MANAGER),
      ).getOrThrow()
      assertEquals("EMP-0002", mgrInvite.employeeCode)
      assertEquals("PENDING", mgrInvite.status)

      // Sign in as Anita (verified email) -> resolveAuthenticatedSession claims invite atomically
      val anitaUid = signInTestUser("anita@finance.in", markEmailVerified = true)
      val anitaResolution = authRepo.resolveAuthenticatedSession().getOrThrow()
      assertTrue(anitaResolution is CloudAuthResolution.Authenticated)
      val anita = (anitaResolution as CloudAuthResolution.Authenticated).employee
      assertEquals(anitaUid, anita.id)
      assertEquals(UserRole.BRANCH_MANAGER, anita.role)
      assertEquals("EMP-0002", anita.employeeCode)

      // Branch Manager attempting to create an employee invite (bypassing client guard) MUST fail in Firestore Rules!
      val mgrInviteAttempt = authRepo.createEmployeeInvite(
        actor = anita,
        fullName = "Unauthorized Agent",
        mobile = "9000011111",
        invitedEmail = "unauth@finance.in",
        assignedRole = UserRole.COLLECTION_AGENT,
        branchId = anita.branchId,
        teamId = null,
        teamName = null,
        permissions = RolePermissionTemplate.defaultsFor(UserRole.COLLECTION_AGENT),
        bypassClientGuardForRulesTest = true,
      )
      assertPermissionDenied(mgrInviteAttempt.exceptionOrNull())

      // Branch Manager attempting to escalate her own role to OWNER MUST fail in Firestore Rules!
      val selfPromoteAttempt = authRepo.updateEmployeeRoleAndPermissions(
        actor = anita,
        targetEmployeeId = anita.id,
        newRole = UserRole.OWNER,
        branchId = anita.branchId,
        teamId = null,
        teamName = null,
        isActive = true,
        permissions = RolePermissionTemplate.defaultsFor(UserRole.OWNER),
        bypassClientGuardForRulesTest = true,
      )
      assertPermissionDenied(selfPromoteAttempt.exceptionOrNull())
  }

  @Test
  fun roleBasedDatabaseIsolation_ownerBranchManagerAndCollectionAgent_enforced() = runEmulatorTest(timeoutMs = 30_000L) {
      // 1. Setup Owner + Mumbai HO + Pune Branch
      signInTestUser("owner@ledger.in")
      val owner = authRepo.initializeOwnerAccount(
        ownerFullName = "Vikram Mehta",
        mobile = "9820011223",
        email = "owner@ledger.in",
        orgName = "Mehta Finance",
        headOfficeBranchName = "Mumbai HO",
        headOfficeCity = "Mumbai",
        headOfficeAddress = "Fort",
      ).getOrThrow()
      val puneBranch = authRepo.createBranch(owner, "Pune Branch", "Pune", "Kothrud").getOrThrow()

      // Provision Pune Branch Manager + Pune Agent A
      authRepo.createEmployeeInvite(
        actor = owner,
        fullName = "Sneha Joshi",
        mobile = "9822011111",
        invitedEmail = "sneha.mgr@ledger.in",
        assignedRole = UserRole.BRANCH_MANAGER,
        branchId = puneBranch.id,
        teamId = null,
        teamName = null,
        permissions = RolePermissionTemplate.defaultsFor(UserRole.BRANCH_MANAGER),
      ).getOrThrow()

      // Claim Pune Manager first so Pune Branch's managerUids includes Sneha
      signInTestUser("sneha.mgr@ledger.in")
      val sneha = (authRepo.resolveAuthenticatedSession().getOrThrow() as CloudAuthResolution.Authenticated).employee

      // Switch back to Owner to invite Agent A in Pune (inheriting Sneha in branchManagerUids)
      signInTestUser("owner@ledger.in")
      db.dao().upsertBranches(authRepo.observeCloudBranches(owner).first())
      authRepo.createEmployeeInvite(
        actor = owner,
        fullName = "Rohan Kulkarni",
        mobile = "9822022222",
        invitedEmail = "rohan.agent@ledger.in",
        assignedRole = UserRole.COLLECTION_AGENT,
        branchId = puneBranch.id,
        teamId = null,
        teamName = null,
        permissions = RolePermissionTemplate.defaultsFor(UserRole.COLLECTION_AGENT),
      ).getOrThrow()

      // Owner creates a Mumbai HO customer (not in Pune, not assigned to Rohan)
      val mumbaiCustomer = loanRepo.registerCustomer(
        actor = owner,
        fullName = "Mumbai Diamond Traders",
        guardianName = "pravin",
        mobile = "9811100001",
        alternateMobile = "",
        address = "Zaveri Bazaar",
        city = "Mumbai",
        kycDocumentType = "PAN Card",
        kycDocumentNumber = "AAAPM1234K",
        occupation = "Trading",
        monthlyIncomePaise = 9_000_000L,
        branchId = owner.branchId,
        assignedAgentId = owner.id,
      ).getOrThrow()

      // Sign in as Rohan (Collection Agent in Pune) to claim invite
      signInTestUser("rohan.agent@ledger.in")
      val rohan = (authRepo.resolveAuthenticatedSession().getOrThrow() as CloudAuthResolution.Authenticated).employee

      // Sign in as Sneha (Pune Branch Manager) and register two Pune customers: one for Rohan, one for Sneha
      signInTestUser("sneha.mgr@ledger.in")
      val puneCustomerForRohan = loanRepo.registerCustomer(
        actor = sneha,
        fullName = "Pune Dairy Mart",
        guardianName = "Madhav",
        mobile = "9822200002",
        alternateMobile = "",
        address = "Karve Road",
        city = "Pune",
        kycDocumentType = "Aadhaar Card",
        kycDocumentNumber = "445566778899",
        occupation = "Dairy",
        monthlyIncomePaise = 4_000_000L,
        branchId = puneBranch.id,
        assignedAgentId = rohan.id,
      ).getOrThrow()

      val puneCustomerForSnehaOnly = loanRepo.registerCustomer(
        actor = sneha,
        fullName = "Deccan Auto Works",
        guardianName = "Suresh",
        mobile = "9822200003",
        alternateMobile = "",
        address = "Deccan",
        city = "Pune",
        kycDocumentType = "PAN Card",
        kycDocumentNumber = "BBBPN5678L",
        occupation = "Garage",
        monthlyIncomePaise = 5_500_000L,
        branchId = puneBranch.id,
        assignedAgentId = sneha.id,
      ).getOrThrow()

      // Pune Branch Manager sees BOTH Pune customers in Cloud Firestore, and is DENIED Mumbai customer
      val snehaCloudCustomers = loanRepo.observeCloudCustomers(sneha).first()
      assertEquals(2, snehaCloudCustomers.size)
      assertPermissionDenied(loanRepo.getCloudCustomerById(mumbaiCustomer.id).exceptionOrNull())

      // Sign in as Rohan (Collection Agent) -> sees ONLY puneCustomerForRohan, DENIED on Sneha's Pune customer & Mumbai customer!
      signInTestUser("rohan.agent@ledger.in")
      val rohanCloudCustomers = loanRepo.observeCloudCustomers(rohan).first()
      assertEquals(1, rohanCloudCustomers.size)
      assertEquals(puneCustomerForRohan.id, rohanCloudCustomers.first().id)
      assertPermissionDenied(loanRepo.getCloudCustomerById(puneCustomerForSnehaOnly.id).exceptionOrNull())
      assertPermissionDenied(loanRepo.getCloudCustomerById(mumbaiCustomer.id).exceptionOrNull())

      // Sign in as Owner -> sees ALL 3 customers across Mumbai & Pune
      signInTestUser("owner@ledger.in")
      val ownerCloudCustomers = loanRepo.observeCloudCustomers(owner).first()
      assertEquals(3, ownerCloudCustomers.size)
  }

  @Test
  fun persistentLoanLifecycle_andOfflineQueueToFirestoreServerConfirmed_verified() = runEmulatorTest {
      signInTestUser("owner@bengaluru.in")
      val owner = authRepo.initializeOwnerAccount(
        ownerFullName = "Nandini Rao",
        mobile = "9845099887",
        email = "owner@bengaluru.in",
        orgName = "Nandini Microfin",
        headOfficeBranchName = "Bengaluru HO",
        headOfficeCity = "Bengaluru",
        headOfficeAddress = "Indiranagar",
      ).getOrThrow()

      val customer = loanRepo.registerCustomer(
        actor = owner,
        fullName = "Sri Krishna Provision Store",
        guardianName = "Venkatesh",
        mobile = "9845011223",
        alternateMobile = "",
        address = "Jayanagar",
        city = "Bengaluru",
        kycDocumentType = "Aadhaar Card",
        kycDocumentNumber = "998877665544",
        occupation = "Retail",
        monthlyIncomePaise = 6_000_000L,
        branchId = owner.branchId,
        assignedAgentId = owner.id,
      ).getOrThrow()

      val loan = loanRepo.createLoanApplication(
        actor = owner,
        customerId = customer.id,
        productType = LoanProductType.DAILY_BUSINESS_EDI,
        principalPaise = 1_000_000L, // ₹10,000
        annualInterestRatePercent = 0.0,
        processingFeePaise = 0L,
        repaymentFrequency = RepaymentFrequency.DAILY,
        totalInstallments = 10,
        disbursementDateDdMmYyyy = "10-10-2026",
        assignedAgentId = owner.id,
        disburseImmediately = true,
      ).getOrThrow()

      assertEquals(LoanStatus.ACTIVE, loan.status)
      assertEquals(10, loanRepo.observeCloudSchedules(owner).first().size)

      // 1. Collect ₹1,000 in offline-queued mode (syncImmediately = false) -> must stay CONFIRMED_LOCALLY_PENDING_SYNC
      val localReceipt = loanRepo.collectPayment(
        actor = owner,
        loanId = loan.id,
        amountPaise = 100_000L,
        paymentMode = PaymentMode.CASH,
        paymentType = PaymentType.REGULAR_INSTALLMENT,
        referenceNumber = "",
        remarks = "Offline field collection",
        collectionDateDdMmYyyy = "11-10-2026",
        syncImmediately = false,
        idempotencyKey = "IDEM-OFFLINE-001",
      ).getOrThrow()

      assertTrue(localReceipt.transactionId.startsWith("TXN-11102026-"))
      assertEquals(SyncStatus.CONFIRMED_LOCALLY_PENDING_SYNC, localReceipt.syncStatus)
      assertNull(localReceipt.serverConfirmationId)
      assertEquals(0, loanRepo.observeCloudPayments(owner).first().size)

      // 2. Flush offline queue to Cloud Firestore -> must transition to SERVER_CONFIRMED
      val syncBatchResult = loanRepo.triggerSyncQueue(owner)
      assertEquals(1, syncBatchResult.confirmedCount)
      assertEquals(0, syncBatchResult.remainingPendingCount)

      val cloudPayments = loanRepo.observeCloudPayments(owner).first()
      assertEquals(1, cloudPayments.size)
      assertEquals(SyncStatus.SERVER_CONFIRMED, cloudPayments.first().syncStatus)
      assertTrue(cloudPayments.first().serverConfirmationId.orEmpty().startsWith("FS-ACK-"))

      val cloudLoan = loanRepo.getCloudLoanById(loan.id).getOrThrow()
      assertEquals(900_000L, cloudLoan.outstandingPaise)
      assertEquals(100_000L, cloudLoan.paidAmountPaise)

      val cloudSchedules = loanRepo.observeCloudSchedules(owner).first()
      assertEquals(InstallmentStatus.PAID, cloudSchedules.first().status)

      // 3. Permanent deletion of confirmed payment MUST be denied by Firestore Security Rules!
      val deleteAttempt = runCatching {
        firestore.collection("payments").document(localReceipt.id).delete().await()
      }.exceptionOrNull()
      assertPermissionDenied(deleteAttempt)

      // 4. Correct confirmed payment from ₹1,000 -> ₹1,500 & verify Cloud Firestore rules allow non-destructive correction
      val correctedReplacement = loanRepo.correctConfirmedPayment(
        actor = owner,
        originalPaymentId = localReceipt.id,
        correctedAmountPaise = 150_000L,
        correctedPaymentMode = PaymentMode.CASH,
        correctedReferenceNumber = "",
        correctedCollectionDateDdMmYyyy = "11-10-2026",
        correctionReason = "Physical receipt verified as ₹1,500",
      ).getOrThrow()
      assertEquals(SyncStatus.SERVER_CONFIRMED, correctedReplacement.syncStatus)

      val cloudPaymentsAfterCorrection = loanRepo.observeCloudPayments(owner).first()
      assertEquals(2, cloudPaymentsAfterCorrection.size)
      assertEquals(
        PaymentRecordStatus.CORRECTED,
        cloudPaymentsAfterCorrection.first { it.id == localReceipt.id }.recordStatus,
      )
      assertEquals(850_000L, loanRepo.getCloudLoanById(loan.id).getOrThrow().outstandingPaise)

      // 5. Reverse the replacement payment -> Cloud Firestore loan balance restores to ₹10,000 & both audit entries exist
      loanRepo.reverseConfirmedPayment(
        actor = owner,
        paymentId = correctedReplacement.id,
        reason = "Customer requested full reversal to pay via UPI instead",
      ).getOrThrow()
      assertEquals(1_000_000L, loanRepo.getCloudLoanById(loan.id).getOrThrow().outstandingPaise)
      assertEquals(2, loanRepo.observeCloudPaymentAdjustments(owner).first().size)
  }

  @Test
  fun multiLoanCustomerLifecycle_approvalRejectionDisbursement_advancePreClosureAndClosedHistoryPreserved() =
    runEmulatorTest(timeoutMs = 35_000L) {
      signInTestUser("owner@chennai.in")
      val owner = authRepo.initializeOwnerAccount(
        ownerFullName = "Senthil Nathan",
        mobile = "9840012345",
        email = "owner@chennai.in",
        orgName = "Cauvery Finance",
        headOfficeBranchName = "Chennai HO",
        headOfficeCity = "Chennai",
        headOfficeAddress = "T Nagar",
      ).getOrThrow()

      // 1. Register Customer & verify collision-safe CUST-XXXX-XXXX + duplicate KYC rejection
      val customer = loanRepo.registerCustomer(
        actor = owner,
        fullName = "Meenakshi Textiles",
        guardianName = "Sundaram",
        mobile = "9840155667",
        alternateMobile = "9840155668",
        address = "45 Usman Road",
        city = "Chennai",
        kycDocumentType = "PAN Card",
        kycDocumentNumber = "abcde1234f", // normalized to ABCDE1234F
        occupation = "Textiles",
        monthlyIncomePaise = 8_500_000L,
        branchId = owner.branchId,
        assignedAgentId = owner.id,
      ).getOrThrow()
      assertTrue(customer.customerCode.startsWith("CUST-0001-"))
      assertEquals("ABCDE1234F", customer.kycDocumentNumber)

      val duplicateAttempt = loanRepo.registerCustomer(
        actor = owner,
        fullName = "Duplicate Applicant",
        guardianName = "Test",
        mobile = "9840199999",
        alternateMobile = "",
        address = "99 Anna Salai",
        city = "Chennai",
        kycDocumentType = "PAN Card",
        kycDocumentNumber = "ABCDE1234F",
        occupation = "Shop",
        monthlyIncomePaise = 3_000_000L,
        branchId = owner.branchId,
        assignedAgentId = owner.id,
      )
      assertTrue(duplicateAttempt.isFailure)

      // 2. Update Customer Profile & verify persistence in Cloud Firestore + Room
      val updatedCustomer = loanRepo.updateCustomerProfile(
        actor = owner,
        customerId = customer.id,
        fullName = "Meenakshi Silk & Textiles",
        guardianName = "Sundaram",
        mobile = "9840155667",
        alternateMobile = "9840155669",
        address = "45A North Usman Road",
        city = "Chennai",
        occupation = "Silk Retail",
        monthlyIncomePaise = 9_500_000L,
      ).getOrThrow()
      assertEquals("Meenakshi Silk & Textiles", loanRepo.getCloudCustomerById(customer.id).getOrThrow().fullName)
      assertEquals("9840155669", updatedCustomer.alternateMobile)

      // 3. Upload KYC Document & verify SHA-256 local integrity helper
      val context = ApplicationProvider.getApplicationContext<Context>()
      val tempKycFile = java.io.File(context.cacheDir, "sample_dl.jpg").apply {
        writeBytes("VALID_JPEG_KYC_BYTES_FOR_SHA256_TEST".encodeToByteArray())
      }
      val savedDocFile = com.example.util.DocumentStorageHelper.copyUriToInternalStorage(
        context = context,
        uri = android.net.Uri.fromFile(tempKycFile),
        customerId = customer.id,
      ).getOrThrow()
      val recordedDoc = loanRepo.recordCustomerDocument(
        actor = owner,
        customerId = customer.id,
        documentType = com.example.domain.model.DocumentType.DRIVING_LICENCE,
        documentNumber = "TN09-20200012345",
        savedFile = savedDocFile,
      ).getOrThrow()
      val integrity = com.example.util.DocumentStorageHelper.verifyDocumentIntegrity(
        localFilePath = recordedDoc.localFilePath,
        expectedSha256 = recordedDoc.sha256Checksum,
      )
      assertEquals(
        com.example.util.DocumentStorageHelper.DocumentIntegrityStatus.VERIFIED_LOCAL_FILE,
        integrity,
      )

      // 4. Loan #1: Submit Application (PENDING_APPROVAL) -> Approve (APPROVED) -> Disburse (ACTIVE)
      val loan1Pending = loanRepo.createLoanApplication(
        actor = owner,
        customerId = customer.id,
        productType = LoanProductType.DAILY_BUSINESS_EDI,
        principalPaise = 2_000_000L, // ₹20,000
        annualInterestRatePercent = 18.0,
        processingFeePaise = 40_000L, // ₹400
        repaymentFrequency = RepaymentFrequency.DAILY,
        totalInstallments = 30,
        disbursementDateDdMmYyyy = "15-10-2026",
        assignedAgentId = owner.id,
        disburseImmediately = false,
      ).getOrThrow()
      assertEquals(LoanStatus.PENDING_APPROVAL, loan1Pending.status)
      assertEquals(0, loanRepo.observeLoanSchedule(loan1Pending.id).first().size)

      val loan1Approved = loanRepo.approveLoanApplication(
        actor = owner,
        loanId = loan1Pending.id,
        approvalRemarks = "Approved for seasonal silk inventory",
      ).getOrThrow()
      assertEquals(LoanStatus.APPROVED, loan1Approved.status)
      assertEquals(LoanStatus.APPROVED, loanRepo.getCloudLoanById(loan1Pending.id).getOrThrow().status)

      val loan1Active = loanRepo.disburseLoan(
        actor = owner,
        loanId = loan1Approved.id,
        disbursementDateDdMmYyyy = "16-10-2026",
        disbursementMode = PaymentMode.UPI,
        disbursementReference = "UTR202610169988",
        approvalRemarks = "Disbursed via UPI",
      ).getOrThrow()
      assertEquals(LoanStatus.ACTIVE, loan1Active.status)
      assertEquals(1_960_000L, loan1Active.netDisbursedPaise) // ₹20,000 - ₹400 fee = ₹19,600
      assertEquals(30, loanRepo.observeLoanSchedule(loan1Active.id).first().size)

      // 5. Loan #2 (same customer): Submit & Reject -> preserved in history as REJECTED
      val loan2Pending = loanRepo.createLoanApplication(
        actor = owner,
        customerId = customer.id,
        productType = LoanProductType.EMERGENCY_BRIDGE,
        principalPaise = 5_000_000L,
        annualInterestRatePercent = 20.0,
        processingFeePaise = 50_000L,
        repaymentFrequency = RepaymentFrequency.WEEKLY,
        totalInstallments = 20,
        disbursementDateDdMmYyyy = "16-10-2026",
        assignedAgentId = owner.id,
        disburseImmediately = false,
      ).getOrThrow()
      val loan2Rejected = loanRepo.rejectLoanApplication(
        actor = owner,
        loanId = loan2Pending.id,
        rejectionReason = "Requested amount exceeds current monthly turnover ratio",
      ).getOrThrow()
      assertEquals(LoanStatus.REJECTED, loan2Rejected.status)
      assertEquals(0L, loan2Rejected.outstandingPaise)

      // 6. Loan #3 (same customer): Weekly EMI -> Advance Deposit -> Apply Advance -> Pre-Closure Settlement -> PRE_CLOSED
      val loan3Active = loanRepo.createLoanApplication(
        actor = owner,
        customerId = customer.id,
        productType = LoanProductType.WEEKLY_GROUP_EMI,
        principalPaise = 1_200_000L, // ₹12,000
        annualInterestRatePercent = 0.0,
        processingFeePaise = 20_000L,
        repaymentFrequency = RepaymentFrequency.WEEKLY,
        totalInstallments = 12,
        disbursementDateDdMmYyyy = "01-10-2026",
        assignedAgentId = owner.id,
        disburseImmediately = true,
        disbursementMode = PaymentMode.BANK_TRANSFER,
        disbursementReference = "NEFT00112233",
      ).getOrThrow()
      assertEquals(LoanStatus.ACTIVE, loan3Active.status)

      // Collect ₹2,000 Advance Deposit & apply ₹2,000 to first 2 Weekly EMIs
      loanRepo.collectPayment(
        actor = owner,
        loanId = loan3Active.id,
        amountPaise = 200_000L,
        paymentMode = PaymentMode.UPI,
        paymentType = PaymentType.ADVANCE_COLLECTION,
        referenceNumber = "UPIADV001",
        remarks = "Advance deposit",
        collectionDateDdMmYyyy = "05-10-2026",
      ).getOrThrow()
      val appliedPaise = loanRepo.applyAdvanceBalanceToInstallments(owner, loan3Active.id).getOrThrow()
      assertEquals(200_000L, appliedPaise)

      // Submit & Settle Pre-Closure on Loan #3 (Remaining ₹10,000 - ₹500 rebate = ₹9,500)
      val pcr = loanRepo.submitPreClosureRequest(
        actor = owner,
        loanId = loan3Active.id,
        proposedRebatePaise = 50_000L,
        preClosureChargePaise = 0L,
        reason = "Early wholesale payout",
      ).getOrThrow()
      assertEquals(950_000L, pcr.netSettlementPaise)

      val settlementReceipt = loanRepo.approveAndSettlePreClosure(
        actor = owner,
        requestId = pcr.id,
        paymentMode = PaymentMode.BANK_TRANSFER,
        referenceNumber = "NEFTCLOSE999",
        reviewRemarks = "Settled & closed cleanly",
      ).getOrThrow()
      assertEquals(SyncStatus.SERVER_CONFIRMED, settlementReceipt.syncStatus)

      val closedLoan3 = loanRepo.getCloudLoanById(loan3Active.id).getOrThrow()
      assertEquals(LoanStatus.PRE_CLOSED, closedLoan3.status)
      assertEquals(0L, closedLoan3.outstandingPaise)
      assertTrue(closedLoan3.closedDateDdMmYyyy.isNotBlank())

      // 7. Verify ALL 3 loans (Active, Rejected, Pre-Closed) & complete customer histories remain permanently available!
      val allCustomerLoans = loanRepo.observeCustomerLoans(customer.id).first()
      assertEquals(3, allCustomerLoans.size)
      assertEquals(2, loanRepo.observeCustomerPayments(customer.id).first().size)
      assertEquals(2, loanRepo.observeCustomerAdvanceLedger(customer.id).first().size)
      assertEquals(1, loanRepo.observeCustomerPreClosures(customer.id).first().size)
      assertEquals(1, loanRepo.observeCustomerDocuments(customer.id).first().size)
    }

  @Test
  fun documentAccessSecurity_sandboxConfinement_andUnauthorizedRoleReadsAndDeletesDeniedByRules() =
    runEmulatorTest(timeoutMs = 30_000L) {
      val context = ApplicationProvider.getApplicationContext<Context>()

      // 1. Local sandbox confinement: path traversal in customerId or localFilePath MUST be blocked
      val tempFile = java.io.File(context.cacheDir, "kyc_test.jpg").apply {
        writeBytes("SECURE_KYC_IMAGE_PAYLOAD".encodeToByteArray())
      }
      val traversalCopyAttempt = com.example.util.DocumentStorageHelper.copyUriToInternalStorage(
        context = context,
        uri = android.net.Uri.fromFile(tempFile),
        customerId = "../escaped_dir",
      )
      assertTrue(traversalCopyAttempt.isFailure)

      // Outside-sandbox path resolution MUST return null & CHECKSUM_MISMATCH
      assertNull(
        com.example.util.DocumentStorageHelper.resolveConfinedDocumentFile(
          context = context,
          customerId = "cust_valid_1",
          localFilePath = tempFile.absolutePath,
        ),
      )
      assertEquals(
        com.example.util.DocumentStorageHelper.DocumentIntegrityStatus.CHECKSUM_MISMATCH,
        com.example.util.DocumentStorageHelper.verifyDocumentIntegrity(
          context = context,
          customerId = "cust_valid_1",
          localFilePath = tempFile.absolutePath,
          expectedSha256 = "0000000000000000",
        ),
      )

      // 2. Cloud Firestore role-scoped document access & deletion immutability
      signInTestUser("owner@docsec.in")
      val owner = authRepo.initializeOwnerAccount(
        ownerFullName = "Arvind Nair",
        mobile = "9847011223",
        email = "owner@docsec.in",
        orgName = "Nair Finance",
        headOfficeBranchName = "Kochi HO",
        headOfficeCity = "Kochi",
        headOfficeAddress = "MG Road",
      ).getOrThrow()
      val kozhikodeBranch = authRepo.createBranch(owner, "Kozhikode Branch", "Kozhikode", "SM Street").getOrThrow()

      // Provision an unassigned Collection Agent in Kozhikode Branch
      authRepo.createEmployeeInvite(
        actor = owner,
        fullName = "Deepak Menon",
        mobile = "9847099881",
        invitedEmail = "deepak.agent@docsec.in",
        assignedRole = UserRole.COLLECTION_AGENT,
        branchId = kozhikodeBranch.id,
        teamId = null,
        teamName = null,
        permissions = RolePermissionTemplate.defaultsFor(UserRole.COLLECTION_AGENT),
      ).getOrThrow()

      val customer = loanRepo.registerCustomer(
        actor = owner,
        fullName = "Malabar Spices",
        guardianName = "Gopalan",
        mobile = "9847055443",
        alternateMobile = "",
        address = "Marine Drive",
        city = "Kochi",
        kycDocumentType = "PAN Card",
        kycDocumentNumber = "MALAB1234P",
        occupation = "Spice Export",
        monthlyIncomePaise = 7_500_000L,
        branchId = owner.branchId,
        assignedAgentId = owner.id,
      ).getOrThrow()

      val savedDocFile = com.example.util.DocumentStorageHelper.copyUriToInternalStorage(
        context = context,
        uri = android.net.Uri.fromFile(tempFile),
        customerId = customer.id,
      ).getOrThrow()
      assertNotNull(
        com.example.util.DocumentStorageHelper.resolveConfinedDocumentFile(
          context = context,
          customerId = customer.id,
          localFilePath = savedDocFile.localFilePath,
        ),
      )

      val recordedDoc = loanRepo.recordCustomerDocument(
        actor = owner,
        customerId = customer.id,
        documentType = com.example.domain.model.DocumentType.PAN_CARD,
        documentNumber = "MALAB1234P",
        savedFile = savedDocFile,
      ).getOrThrow()

      // Permanent deletion of KYC document metadata MUST be denied even for Owner
      val ownerDeleteDocAttempt = runCatching {
        firestore.collection("customer_documents").document(recordedDoc.id).delete().await()
      }.exceptionOrNull()
      assertPermissionDenied(ownerDeleteDocAttempt)

      // Sign in as Deepak (unassigned Collection Agent in another branch) -> MUST be denied reading Kochi customer's KYC doc!
      signInTestUser("deepak.agent@docsec.in")
      val deepak = (authRepo.resolveAuthenticatedSession().getOrThrow() as CloudAuthResolution.Authenticated).employee
      val unauthGetDocAttempt = runCatching {
        firestore.collection("customer_documents").document(recordedDoc.id).get().await()
      }.exceptionOrNull()
      assertPermissionDenied(unauthGetDocAttempt)
      assertEquals(0, loanRepo.observeCloudCustomerDocuments(deepak).first().size)
    }
}


