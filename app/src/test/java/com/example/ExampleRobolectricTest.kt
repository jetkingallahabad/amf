package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.LoanFinanceDatabase
import com.example.data.repository.AuthAndOrgRepository
import com.example.data.repository.CustomerLoanRepository
import com.example.data.session.SessionStore
import com.example.data.sync.SyncEngine
import com.example.domain.model.InstallmentStatus
import com.example.domain.model.LoanProductType
import com.example.domain.model.LoanStatus
import com.example.domain.model.PaymentAdjustmentType
import com.example.domain.model.PaymentMode
import com.example.domain.model.PaymentRecordStatus
import com.example.domain.model.PaymentType
import com.example.domain.model.PreClosureStatus
import com.example.domain.model.RepaymentFrequency
import com.example.domain.model.RolePermissionTemplate
import com.example.domain.model.SyncStatus
import com.example.domain.model.UserRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class InMemorySessionStore : SessionStore {
  private val empId = MutableStateFlow<String?>(null)
  override val activeEmployeeIdFlow: Flow<String?> = empId
  override suspend fun setActiveEmployeeId(employeeId: String?) {
    empId.value = employeeId
  }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  private lateinit var db: LoanFinanceDatabase
  private lateinit var authRepo: AuthAndOrgRepository
  private lateinit var loanRepo: CustomerLoanRepository

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db = Room.inMemoryDatabaseBuilder(context, LoanFinanceDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    val sessionStore = InMemorySessionStore()
    val syncEngine = SyncEngine(db.dao())
    authRepo = AuthAndOrgRepository(db.dao(), sessionStore)
    loanRepo = CustomerLoanRepository(db.dao(), syncEngine)
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun appName_matchesLoanFinanceManager() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertEquals("Loan Finance Manager", context.getString(R.string.app_name))
  }

  @Test
  fun ownerOnlyEmployeeCreation_andRolePermissionBoundaries_enforced() = runBlocking {
    val owner = authRepo.initializeOwnerAccount(
      ownerFullName = "Rajesh Sharma",
      mobile = "9876543210",
      email = "owner@finance.in",
      orgName = "Sharma Finance",
      headOfficeBranchName = "Mumbai Head Office",
      headOfficeCity = "Mumbai",
      headOfficeAddress = "Nariman Point",
    ).getOrThrow()

    assertEquals(UserRole.OWNER, owner.role)
    assertEquals("EMP-0001", owner.employeeCode)

    val puneBranch = authRepo.createBranch(owner, "Pune Branch", "Pune", "FC Road").getOrThrow()

    val managerInvite = authRepo.createEmployeeInvite(
      actor = owner,
      fullName = "Anita Deshmukh",
      mobile = "9123456780",
      invitedEmail = "anita@finance.in",
      assignedRole = UserRole.BRANCH_MANAGER,
      branchId = puneBranch.id,
      teamId = null,
      teamName = null,
      permissions = RolePermissionTemplate.defaultsFor(UserRole.BRANCH_MANAGER),
      invitedUid = "mgr-uid-1",
    ).getOrThrow()

    val agentInvite = authRepo.createEmployeeInvite(
      actor = owner,
      fullName = "Vikram Patil",
      mobile = "9988776655",
      invitedEmail = "vikram@finance.in",
      assignedRole = UserRole.COLLECTION_AGENT,
      branchId = puneBranch.id,
      teamId = null,
      teamName = null,
      permissions = RolePermissionTemplate.defaultsFor(UserRole.COLLECTION_AGENT),
      invitedUid = "agent-uid-1",
    ).getOrThrow()

    assertEquals("EMP-0002", managerInvite.employeeCode)
    assertEquals("EMP-0003", agentInvite.employeeCode)

    val manager = db.dao().getEmployeeById("mgr-uid-1")!!
    val agent = db.dao().getEmployeeById("agent-uid-1")!!

    // Branch Manager & Collection Agent MUST be blocked from creating employees
    val mgrCreateAttempt = authRepo.createEmployeeInvite(
      actor = manager,
      fullName = "Unauthorized Staff",
      mobile = "9000011111",
      invitedEmail = "unauth@finance.in",
      assignedRole = UserRole.COLLECTION_AGENT,
      branchId = puneBranch.id,
      teamId = null,
      teamName = null,
      permissions = RolePermissionTemplate.defaultsFor(UserRole.COLLECTION_AGENT),
    )
    assertTrue(mgrCreateAttempt.exceptionOrNull() is SecurityException)

    val agentRoleUpdateAttempt = authRepo.updateEmployeeRoleAndPermissions(
      actor = agent,
      targetEmployeeId = agent.id,
      newRole = UserRole.OWNER,
      branchId = puneBranch.id,
      teamId = null,
      teamName = null,
      isActive = true,
      permissions = RolePermissionTemplate.defaultsFor(UserRole.OWNER),
    )
    assertTrue(agentRoleUpdateAttempt.exceptionOrNull() is SecurityException)

    // Verify Branch Scoping: Customer in Mumbai HO is invisible to Pune Branch Manager
    val mumbaiCustomer = loanRepo.registerCustomer(
      actor = owner,
      fullName = "Suresh Kirana",
      guardianName = "Mohan",
      mobile = "9811122233",
      alternateMobile = "",
      address = "Dadar Market",
      city = "Mumbai",
      kycDocumentType = "Aadhaar Card",
      kycDocumentNumber = "123456789012",
      occupation = "Retail",
      monthlyIncomePaise = 4_500_000L,
      branchId = owner.branchId,
      assignedAgentId = owner.id,
    ).getOrThrow()

    val puneCustomer = loanRepo.registerCustomer(
      actor = manager,
      fullName = "Ganesh Traders",
      guardianName = "Ramesh",
      mobile = "9822233344",
      alternateMobile = "",
      address = "Shivaji Nagar",
      city = "Pune",
      kycDocumentType = "PAN Card",
      kycDocumentNumber = "ABCDE1234F",
      occupation = "Wholesale",
      monthlyIncomePaise = 6_000_000L,
      branchId = puneBranch.id,
      assignedAgentId = agent.id,
    ).getOrThrow()

    assertEquals(2, loanRepo.observePermittedCustomers(owner).first().size)
    val mgrVisible = loanRepo.observePermittedCustomers(manager).first()
    assertEquals(1, mgrVisible.size)
    assertEquals(puneCustomer.id, mgrVisible.first().id)

    // Pune Manager cannot create loan on Mumbai customer
    val crossBranchLoanAttempt = loanRepo.createLoanApplication(
      actor = manager,
      customerId = mumbaiCustomer.id,
      productType = LoanProductType.DAILY_BUSINESS_EDI,
      principalPaise = 2_000_000L,
      annualInterestRatePercent = 18.0,
      processingFeePaise = 0L,
      repaymentFrequency = RepaymentFrequency.DAILY,
      totalInstallments = 30,
      disbursementDateDdMmYyyy = "26-10-2026",
      assignedAgentId = agent.id,
      disburseImmediately = true,
    )
    assertTrue(crossBranchLoanAttempt.exceptionOrNull() is SecurityException)
  }

  @Test
  fun fullLoanLifecycle_scheduleCollectionAdvanceAndPreClosureOfflineQueue_verified() = runBlocking {
    val owner = authRepo.initializeOwnerAccount(
      ownerFullName = "Kiran Kulkarni",
      mobile = "9765432109",
      email = "kiran@finance.in",
      orgName = "Kulkarni Finance",
      headOfficeBranchName = "Bengaluru Main",
      headOfficeCity = "Bengaluru",
      headOfficeAddress = "MG Road",
    ).getOrThrow()

    val customer = loanRepo.registerCustomer(
      actor = owner,
      fullName = "Lakshmi Textiles",
      guardianName = "Narayan",
      mobile = "9845012345",
      alternateMobile = "",
      address = "Chickpet",
      city = "Bengaluru",
      kycDocumentType = "Aadhaar Card",
      kycDocumentNumber = "888877776666",
      occupation = "Textiles",
      monthlyIncomePaise = 5_000_000L,
      branchId = owner.branchId,
      assignedAgentId = owner.id,
    ).getOrThrow()

    val loan = loanRepo.createLoanApplication(
      actor = owner,
      customerId = customer.id,
      productType = LoanProductType.DAILY_BUSINESS_EDI,
      principalPaise = 1_000_000L,
      annualInterestRatePercent = 0.0,
      processingFeePaise = 0L,
      repaymentFrequency = RepaymentFrequency.DAILY,
      totalInstallments = 10,
      disbursementDateDdMmYyyy = "10-10-2026",
      assignedAgentId = owner.id,
      disburseImmediately = true,
    ).getOrThrow()

    assertEquals(LoanStatus.ACTIVE, loan.status)
    val initialSchedule = loanRepo.observeLoanSchedule(loan.id).first()
    assertEquals(10, initialSchedule.size)
    assertEquals("11-10-2026", initialSchedule[0].dueDateDdMmYyyy)
    assertEquals("20-10-2026", initialSchedule[9].dueDateDdMmYyyy)

    // Without Cloud Firestore configured, receipts remain CONFIRMED_LOCALLY_PENDING_SYNC (never fake confirmed!)
    val receipt1 = loanRepo.collectPayment(
      actor = owner,
      loanId = loan.id,
      amountPaise = 100_000L,
      paymentMode = PaymentMode.CASH,
      paymentType = PaymentType.REGULAR_INSTALLMENT,
      referenceNumber = "",
      remarks = "Installment 1 cash",
      collectionDateDdMmYyyy = "11-10-2026",
    ).getOrThrow()

    assertTrue(receipt1.transactionId.startsWith("TXN-11102026-"))
    assertTrue(receipt1.receiptNumber.startsWith("RCPT-11102026-"))
    assertEquals(SyncStatus.CONFIRMED_LOCALLY_PENDING_SYNC, receipt1.syncStatus)
    assertNull(receipt1.serverConfirmationId)

    val advanceReceipt = loanRepo.collectPayment(
      actor = owner,
      loanId = loan.id,
      amountPaise = 200_000L,
      paymentMode = PaymentMode.UPI,
      paymentType = PaymentType.ADVANCE_COLLECTION,
      referenceNumber = "UTR998877",
      remarks = "Advance deposit",
      collectionDateDdMmYyyy = "12-10-2026",
    ).getOrThrow()
    assertEquals(200_000L, advanceReceipt.creditedToAdvancePaise)

    val appliedPaise = loanRepo.applyAdvanceBalanceToInstallments(owner, loan.id).getOrThrow()
    assertEquals(200_000L, appliedPaise)

    val loanAfterAdvance = loanRepo.observeLoanById(loan.id).first()!!
    assertEquals(0L, loanAfterAdvance.advanceBalancePaise)
    assertEquals(700_000L, loanAfterAdvance.outstandingPaise)

    val pcr = loanRepo.submitPreClosureRequest(
      actor = owner,
      loanId = loan.id,
      proposedRebatePaise = 50_000L,
      preClosureChargePaise = 0L,
      reason = "Early business settlement",
    ).getOrThrow()
    assertEquals(650_000L, pcr.netSettlementPaise)
    assertEquals(PreClosureStatus.PENDING, pcr.status)

    val settlementReceipt = loanRepo.approveAndSettlePreClosure(
      actor = owner,
      requestId = pcr.id,
      paymentMode = PaymentMode.BANK_TRANSFER,
      referenceNumber = "NEFT112233",
      reviewRemarks = "Approved by Owner",
    ).getOrThrow()
    assertEquals(650_000L, settlementReceipt.amountPaise)
    assertEquals(SyncStatus.CONFIRMED_LOCALLY_PENDING_SYNC, settlementReceipt.syncStatus)

    val closedLoan = loanRepo.observeLoanById(loan.id).first()!!
    assertEquals(LoanStatus.PRE_CLOSED, closedLoan.status)
    assertEquals(0L, closedLoan.outstandingPaise)
    val finalSchedule = loanRepo.observeLoanSchedule(loan.id).first()
    assertEquals(3, finalSchedule.count { it.status == InstallmentStatus.PAID })
    assertEquals(7, finalSchedule.count { it.status == InstallmentStatus.WAIVED_PRECLOSURE })

    // Offline-only SyncEngine (firestore == null) keeps all 3 receipts as CONFIRMED_LOCALLY_PENDING_SYNC
    val offlineSync = loanRepo.triggerSyncQueue(owner)
    assertEquals(0, offlineSync.confirmedCount)
    assertEquals(3, offlineSync.remainingPendingCount)
  }

  @Test
  fun partialAndMultiInstallmentCollection_duplicateGuards_andNonDestructiveReversalAndCorrection_verified() =
    runBlocking {
      val owner = authRepo.initializeOwnerAccount(
        ownerFullName = "Arvind Nair",
        mobile = "9847011223",
        email = "arvind@finance.in",
        orgName = "Nair Finance",
        headOfficeBranchName = "Kochi HO",
        headOfficeCity = "Kochi",
        headOfficeAddress = "MG Road",
      ).getOrThrow()

      val customer = loanRepo.registerCustomer(
        actor = owner,
        fullName = "Malabar Spices Co",
        guardianName = "Gopinath",
        mobile = "9847099881",
        alternateMobile = "",
        address = "Broadway",
        city = "Kochi",
        kycDocumentType = "PAN Card",
        kycDocumentNumber = "AAACM9988P",
        occupation = "Spices",
        monthlyIncomePaise = 7_500_000L,
        branchId = owner.branchId,
        assignedAgentId = owner.id,
      ).getOrThrow()

      // 4 Daily installments of ₹1,000 each = ₹4,000 (400_000 paise)
      val loan = loanRepo.createLoanApplication(
        actor = owner,
        customerId = customer.id,
        productType = LoanProductType.DAILY_BUSINESS_EDI,
        principalPaise = 400_000L,
        annualInterestRatePercent = 0.0,
        processingFeePaise = 0L,
        repaymentFrequency = RepaymentFrequency.DAILY,
        totalInstallments = 4,
        disbursementDateDdMmYyyy = "10-10-2026",
        assignedAgentId = owner.id,
        disburseImmediately = true,
      ).getOrThrow()

      // 1. Partial payment of ₹400 (40_000 paise) with explicit idempotencyKey
      val p1 = loanRepo.collectPayment(
        actor = owner,
        loanId = loan.id,
        amountPaise = 40_000L,
        paymentMode = PaymentMode.UPI,
        paymentType = PaymentType.REGULAR_INSTALLMENT,
        referenceNumber = "UTR-PARTIAL-001",
        remarks = "Partial day 1",
        collectionDateDdMmYyyy = "11-10-2026",
        idempotencyKey = "IDEM-TEST-0001",
      ).getOrThrow()
      assertEquals(SyncStatus.CONFIRMED_LOCALLY_PENDING_SYNC, p1.syncStatus)
      assertEquals(40_000L, p1.allocatedToInstallmentsPaise)
      assertEquals(0L, p1.creditedToAdvancePaise)

      val schedAfterP1 = loanRepo.observeLoanSchedule(loan.id).first()
      assertEquals(InstallmentStatus.PARTIAL, schedAfterP1[0].status)
      assertEquals(40_000L, schedAfterP1[0].paidAmountPaise)

      // 2. Duplicate idempotencyKey MUST be rejected without altering loan balance
      val dupIdemAttempt = loanRepo.collectPayment(
        actor = owner,
        loanId = loan.id,
        amountPaise = 40_000L,
        paymentMode = PaymentMode.CASH,
        paymentType = PaymentType.REGULAR_INSTALLMENT,
        referenceNumber = "",
        remarks = "Accidental double tap",
        collectionDateDdMmYyyy = "11-10-2026",
        idempotencyKey = "IDEM-TEST-0001",
      )
      assertTrue(dupIdemAttempt.isFailure)

      // 3. Duplicate active UPI UTR MUST be rejected
      val dupUtrAttempt = loanRepo.collectPayment(
        actor = owner,
        loanId = loan.id,
        amountPaise = 60_000L,
        paymentMode = PaymentMode.UPI,
        paymentType = PaymentType.REGULAR_INSTALLMENT,
        referenceNumber = "utr-partial-001",
        remarks = "Reused UTR",
        collectionDateDdMmYyyy = "11-10-2026",
      )
      assertTrue(dupUtrAttempt.isFailure)
      assertEquals(360_000L, loanRepo.observeLoanById(loan.id).first()!!.outstandingPaise)

      // 4. Multi-installment payment of ₹1,800 (180_000 paise):
      //    Finishes #1 (+₹600 -> PAID), pays #2 (+₹1,000 -> PAID), partially pays #3 (+₹200 -> PARTIAL)
      val p2 = loanRepo.collectPayment(
        actor = owner,
        loanId = loan.id,
        amountPaise = 180_000L,
        paymentMode = PaymentMode.CASH,
        paymentType = PaymentType.REGULAR_INSTALLMENT,
        referenceNumber = "",
        remarks = "Multi-installment catchup",
        collectionDateDdMmYyyy = "12-10-2026",
      ).getOrThrow()
      assertTrue(p1.transactionId != p2.transactionId)
      val schedAfterP2 = loanRepo.observeLoanSchedule(loan.id).first()
      assertEquals(InstallmentStatus.PAID, schedAfterP2[0].status)
      assertEquals(InstallmentStatus.PAID, schedAfterP2[1].status)
      assertEquals(InstallmentStatus.PARTIAL, schedAfterP2[2].status)
      assertEquals(20_000L, schedAfterP2[2].paidAmountPaise)
      assertEquals(180_000L, loanRepo.observeLoanById(loan.id).first()!!.outstandingPaise)

      // 5. Pay remaining ₹1,800 to close loan as COMPLETED
      val p3Final = loanRepo.collectPayment(
        actor = owner,
        loanId = loan.id,
        amountPaise = 180_000L,
        paymentMode = PaymentMode.CASH,
        paymentType = PaymentType.REGULAR_INSTALLMENT,
        referenceNumber = "",
        remarks = "Final settlement",
        collectionDateDdMmYyyy = "13-10-2026",
      ).getOrThrow()
      val completedLoan = loanRepo.observeLoanById(loan.id).first()!!
      assertEquals(LoanStatus.COMPLETED, completedLoan.status)
      assertEquals(0L, completedLoan.outstandingPaise)
      assertTrue(completedLoan.closedDateDdMmYyyy.isNotBlank())

      // 6. Reverse p3Final -> original receipt preserved (REVERSED, never deleted!), COMPLETED loan reopens to ACTIVE!
      val reversalAdj = loanRepo.reverseConfirmedPayment(
        actor = owner,
        paymentId = p3Final.id,
        reason = "Cash count discrepancy - entered on wrong account",
      ).getOrThrow()
      assertEquals(PaymentAdjustmentType.REVERSAL, reversalAdj.adjustmentType)

      val reopenedLoan = loanRepo.observeLoanById(loan.id).first()!!
      assertEquals(LoanStatus.ACTIVE, reopenedLoan.status)
      assertEquals("", reopenedLoan.closedDateDdMmYyyy)
      assertEquals(180_000L, reopenedLoan.outstandingPaise)

      val paymentsAfterReversal = loanRepo.observeLoanPayments(loan.id).first()
      assertEquals(3, paymentsAfterReversal.size) // Zero deletions!
      assertEquals(
        PaymentRecordStatus.REVERSED,
        paymentsAfterReversal.first { it.id == p3Final.id }.recordStatus,
      )

      // 7. Correct p2 from ₹1,800 down to ₹1,600 -> p2 marked CORRECTED, new replacement ACTIVE p4 created!
      val replacementP4 = loanRepo.correctConfirmedPayment(
        actor = owner,
        originalPaymentId = p2.id,
        correctedAmountPaise = 160_000L,
        correctedPaymentMode = PaymentMode.CASH,
        correctedReferenceNumber = "",
        correctedCollectionDateDdMmYyyy = "12-10-2026",
        correctionReason = "Collected ₹1,600 instead of ₹1,800 as per physical receipt book",
      ).getOrThrow()
      assertEquals(PaymentRecordStatus.ACTIVE, replacementP4.recordStatus)
      assertEquals(p2.id, replacementP4.correctsPaymentId)

      val allPaymentsFinal = loanRepo.observeLoanPayments(loan.id).first()
      assertEquals(4, allPaymentsFinal.size) // p1 (ACTIVE ₹400), p2 (CORRECTED), p3 (REVERSED), p4 (ACTIVE ₹1,600)
      assertEquals(PaymentRecordStatus.CORRECTED, allPaymentsFinal.first { it.id == p2.id }.recordStatus)

      val finalAdjustments = loanRepo.observeLoanAdjustments(loan.id).first()
      assertEquals(2, finalAdjustments.size) // 1 REVERSAL + 1 CORRECTION

      val finalLoanState = loanRepo.observeLoanById(loan.id).first()!!
      // Active paid = ₹400 (p1) + ₹1,600 (p4) = ₹2,000 (200_000 paise); Outstanding = ₹2,000 (200_000 paise)
      assertEquals(200_000L, finalLoanState.paidAmountPaise)
      assertEquals(200_000L, finalLoanState.outstandingPaise)
      val schedFinal = loanRepo.observeLoanSchedule(loan.id).first()
      assertEquals(InstallmentStatus.PAID, schedFinal[0].status)
      assertEquals(InstallmentStatus.PAID, schedFinal[1].status)
      assertEquals(InstallmentStatus.UPCOMING, schedFinal[2].status)
      assertEquals(0L, schedFinal[2].paidAmountPaise)
    }
}
