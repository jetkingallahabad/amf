package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface LoanFinanceDao {

  // --- Branches & Teams ---
  @Query("SELECT * FROM branches ORDER BY createdAtMillis ASC")
  fun observeBranches(): Flow<List<BranchEntity>>

  @Query("SELECT * FROM branches ORDER BY createdAtMillis ASC")
  suspend fun getBranches(): List<BranchEntity>

  @Query("SELECT * FROM branches WHERE id = :branchId LIMIT 1")
  suspend fun getBranchById(branchId: String): BranchEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertBranch(branch: BranchEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertBranches(branches: List<BranchEntity>)

  @Query("SELECT * FROM teams ORDER BY createdAtMillis DESC")
  fun observeAllTeams(): Flow<List<TeamEntity>>

  @Query("SELECT * FROM teams WHERE branchId = :branchId ORDER BY createdAtMillis DESC")
  fun observeTeamsByBranch(branchId: String): Flow<List<TeamEntity>>

  @Query("SELECT COUNT(*) FROM teams")
  suspend fun countTeams(): Int

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertTeam(team: TeamEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertTeams(teams: List<TeamEntity>)

  // --- Employees & Invites ---
  @Query("SELECT COUNT(*) FROM employees")
  suspend fun countEmployees(): Int

  @Query("SELECT COUNT(*) FROM employees")
  fun observeEmployeeCount(): Flow<Int>

  @Query("SELECT * FROM employees ORDER BY createdAtMillis ASC")
  fun observeAllEmployees(): Flow<List<EmployeeEntity>>

  @Query("SELECT * FROM employees WHERE branchId = :branchId ORDER BY createdAtMillis ASC")
  fun observeEmployeesByBranch(branchId: String): Flow<List<EmployeeEntity>>

  @Query("SELECT * FROM employees WHERE id = :id LIMIT 1")
  suspend fun getEmployeeById(id: String): EmployeeEntity?

  @Query("SELECT * FROM employees WHERE id = :id LIMIT 1")
  fun observeEmployeeById(id: String): Flow<EmployeeEntity?>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertEmployee(employee: EmployeeEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertEmployees(employees: List<EmployeeEntity>)

  @Update
  suspend fun updateEmployee(employee: EmployeeEntity)

  @Query("SELECT * FROM employee_invites ORDER BY createdAtMillis DESC")
  fun observeAllInvites(): Flow<List<EmployeeInviteEntity>>

  @Query("SELECT COUNT(*) FROM employee_invites")
  suspend fun countInvites(): Int

  @Query("SELECT COUNT(*) FROM employee_invites WHERE status != 'CLAIMED'")
  suspend fun countUnclaimedInvites(): Int

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertInvite(invite: EmployeeInviteEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertInvites(invites: List<EmployeeInviteEntity>)

  // --- Customers & Documents ---
  @Query("SELECT COUNT(*) FROM customers")
  suspend fun countCustomers(): Int

  @Query("SELECT * FROM customers ORDER BY createdAtMillis DESC")
  fun observeAllCustomers(): Flow<List<CustomerEntity>>

  @Query("SELECT * FROM customers WHERE branchId = :branchId ORDER BY createdAtMillis DESC")
  fun observeCustomersByBranch(branchId: String): Flow<List<CustomerEntity>>

  @Query(
    """
    SELECT * FROM customers 
    WHERE assignedAgentId = :agentId 
    ORDER BY createdAtMillis DESC
    """,
  )
  fun observeCustomersForAgent(agentId: String): Flow<List<CustomerEntity>>

  @Query("SELECT * FROM customers WHERE id = :customerId LIMIT 1")
  fun observeCustomerById(customerId: String): Flow<CustomerEntity?>

  @Query("SELECT * FROM customers WHERE id = :customerId LIMIT 1")
  suspend fun getCustomerById(customerId: String): CustomerEntity?

  @Query("SELECT * FROM customers WHERE customerCode = :customerCode LIMIT 1")
  suspend fun getCustomerByCode(customerCode: String): CustomerEntity?

  @Query("SELECT * FROM customers WHERE UPPER(kycDocumentNumber) = UPPER(:kycDocumentNumber) LIMIT 1")
  suspend fun getCustomerByKycNumber(kycDocumentNumber: String): CustomerEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertCustomer(customer: CustomerEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertCustomers(customers: List<CustomerEntity>)

  @Update
  suspend fun updateCustomer(customer: CustomerEntity)

  @Query("SELECT * FROM customer_documents WHERE customerId = :customerId ORDER BY createdAtMillis DESC")
  fun observeCustomerDocuments(customerId: String): Flow<List<CustomerDocumentEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertCustomerDocument(doc: CustomerDocumentEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertCustomerDocuments(docs: List<CustomerDocumentEntity>)

  // --- Owner-Configured Fixed Loan Products (Activated/Deactivated, Never Deleted) ---
  @Query("SELECT COUNT(*) FROM loan_products")
  suspend fun countLoanProducts(): Int

  @Query(
    """
    SELECT * FROM loan_products 
    ORDER BY isActive DESC, principalPaise ASC, repaymentFrequency ASC, durationCount ASC
    """,
  )
  fun observeAllLoanProducts(): Flow<List<LoanProductEntity>>

  @Query(
    """
    SELECT * FROM loan_products 
    WHERE isActive = 1 
    ORDER BY principalPaise ASC, repaymentFrequency ASC, durationCount ASC
    """,
  )
  fun observeActiveLoanProducts(): Flow<List<LoanProductEntity>>

  @Query("SELECT * FROM loan_products WHERE id = :productId LIMIT 1")
  suspend fun getLoanProductById(productId: String): LoanProductEntity?

  @Query("SELECT * FROM loan_products WHERE productCode = :productCode LIMIT 1")
  suspend fun getLoanProductByCode(productCode: String): LoanProductEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertLoanProduct(product: LoanProductEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertLoanProducts(products: List<LoanProductEntity>)

  @Update
  suspend fun updateLoanProduct(product: LoanProductEntity)

  @Transaction
  suspend fun commitLoanProductMutation(
    product: LoanProductEntity,
    auditLog: AuditLogEntity,
  ) {
    insertLoanProduct(product)
    insertAuditLog(auditLog)
  }

  // --- Loans & Schedules ---
  @Query("SELECT COUNT(*) FROM loans")
  suspend fun countLoans(): Int

  @Query("SELECT * FROM loans ORDER BY createdAtMillis DESC")
  fun observeAllLoans(): Flow<List<LoanEntity>>

  @Query("SELECT * FROM loans WHERE branchId = :branchId ORDER BY createdAtMillis DESC")
  fun observeLoansByBranch(branchId: String): Flow<List<LoanEntity>>

  @Query("SELECT * FROM loans WHERE assignedAgentId = :agentId ORDER BY createdAtMillis DESC")
  fun observeLoansForAgent(agentId: String): Flow<List<LoanEntity>>

  @Query("SELECT * FROM loans WHERE customerId = :customerId ORDER BY createdAtMillis DESC")
  fun observeLoansByCustomer(customerId: String): Flow<List<LoanEntity>>

  @Query("SELECT * FROM loans WHERE id = :loanId LIMIT 1")
  fun observeLoanById(loanId: String): Flow<LoanEntity?>

  @Query("SELECT * FROM loans WHERE id = :loanId LIMIT 1")
  suspend fun getLoanById(loanId: String): LoanEntity?

  @Query("SELECT * FROM loans WHERE loanCode = :loanCode LIMIT 1")
  suspend fun getLoanByCode(loanCode: String): LoanEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertLoan(loan: LoanEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertLoans(loans: List<LoanEntity>)

  @Update
  suspend fun updateLoan(loan: LoanEntity)

  @Query("SELECT * FROM installment_schedules WHERE loanId = :loanId ORDER BY installmentNumber ASC")
  fun observeScheduleForLoan(loanId: String): Flow<List<InstallmentScheduleEntity>>

  @Query("SELECT * FROM installment_schedules WHERE loanId = :loanId ORDER BY installmentNumber ASC")
  suspend fun getScheduleForLoan(loanId: String): List<InstallmentScheduleEntity>

  @Query("SELECT * FROM installment_schedules ORDER BY dueDateEpochMillis ASC, installmentNumber ASC")
  fun observeAllSchedules(): Flow<List<InstallmentScheduleEntity>>

  @Query(
    """
    SELECT * FROM installment_schedules 
    WHERE branchId = :branchId 
    ORDER BY dueDateEpochMillis ASC, installmentNumber ASC
    """,
  )
  fun observeSchedulesByBranch(branchId: String): Flow<List<InstallmentScheduleEntity>>

  @Query(
    """
    SELECT * FROM installment_schedules 
    WHERE assignedAgentId = :agentId 
    ORDER BY dueDateEpochMillis ASC, installmentNumber ASC
    """,
  )
  fun observeSchedulesForAgent(agentId: String): Flow<List<InstallmentScheduleEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertSchedules(items: List<InstallmentScheduleEntity>)

  @Update
  suspend fun updateSchedules(items: List<InstallmentScheduleEntity>)

  // --- Payments (NON-DELETABLE LEDGER: Zero DELETE queries permitted) ---
  @Query("SELECT COUNT(*) FROM payments")
  suspend fun countPayments(): Int

  @Query("SELECT * FROM payments ORDER BY collectedAtMillis DESC")
  fun observeAllPayments(): Flow<List<PaymentEntity>>

  @Query("SELECT * FROM payments WHERE branchId = :branchId ORDER BY collectedAtMillis DESC")
  fun observePaymentsByBranch(branchId: String): Flow<List<PaymentEntity>>

  @Query(
    """
    SELECT * FROM payments 
    WHERE collectedByEmployeeId = :agentId OR assignedAgentId = :agentId 
    ORDER BY collectedAtMillis DESC
    """,
  )
  fun observePaymentsByAgent(agentId: String): Flow<List<PaymentEntity>>

  @Query("SELECT * FROM payments WHERE loanId = :loanId ORDER BY collectedAtMillis DESC")
  fun observePaymentsForLoan(loanId: String): Flow<List<PaymentEntity>>

  @Query("SELECT * FROM payments WHERE loanId = :loanId ORDER BY collectedAtMillis ASC")
  suspend fun getPaymentsForLoan(loanId: String): List<PaymentEntity>

  @Query("SELECT * FROM payments WHERE customerId = :customerId ORDER BY collectedAtMillis DESC")
  fun observePaymentsForCustomer(customerId: String): Flow<List<PaymentEntity>>

  @Query("SELECT * FROM payments WHERE id = :paymentId LIMIT 1")
  suspend fun getPaymentById(paymentId: String): PaymentEntity?

  @Query("SELECT * FROM payments WHERE idempotencyKey = :idempotencyKey LIMIT 1")
  suspend fun getPaymentByIdempotencyKey(idempotencyKey: String): PaymentEntity?

  @Query("SELECT * FROM payments WHERE transactionId = :transactionId LIMIT 1")
  suspend fun getPaymentByTransactionId(transactionId: String): PaymentEntity?

  @Query("SELECT * FROM payments WHERE receiptNumber = :receiptNumber LIMIT 1")
  suspend fun getPaymentByReceiptNumber(receiptNumber: String): PaymentEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertPayment(payment: PaymentEntity)

  @Query("SELECT DISTINCT loanId FROM payments WHERE syncStatus != 'SERVER_CONFIRMED'")
  suspend fun getLoanIdsWithUnsyncedPayments(): List<String>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertPayments(payments: List<PaymentEntity>)

  @Update
  suspend fun updatePayment(payment: PaymentEntity)

  // --- Payment Adjustments (IMMUTABLE REVERSAL & CORRECTION HISTORY) ---
  @Query("SELECT COUNT(*) FROM payment_adjustments")
  suspend fun countPaymentAdjustments(): Int

  @Query("SELECT * FROM payment_adjustments ORDER BY createdAtMillis DESC")
  fun observeAllAdjustments(): Flow<List<PaymentAdjustmentEntity>>

  @Query("SELECT * FROM payment_adjustments WHERE branchId = :branchId ORDER BY createdAtMillis DESC")
  fun observeAdjustmentsByBranch(branchId: String): Flow<List<PaymentAdjustmentEntity>>

  @Query(
    """
    SELECT * FROM payment_adjustments 
    WHERE assignedAgentId = :agentId OR performedByEmployeeId = :agentId 
    ORDER BY createdAtMillis DESC
    """,
  )
  fun observeAdjustmentsForAgent(agentId: String): Flow<List<PaymentAdjustmentEntity>>

  @Query("SELECT * FROM payment_adjustments WHERE loanId = :loanId ORDER BY createdAtMillis DESC")
  fun observeAdjustmentsForLoan(loanId: String): Flow<List<PaymentAdjustmentEntity>>

  @Query("SELECT * FROM payment_adjustments WHERE customerId = :customerId ORDER BY createdAtMillis DESC")
  fun observeAdjustmentsForCustomer(customerId: String): Flow<List<PaymentAdjustmentEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertPaymentAdjustment(adjustment: PaymentAdjustmentEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertPaymentAdjustments(adjustments: List<PaymentAdjustmentEntity>)

  // --- Advance Ledger ---
  @Query("SELECT * FROM advance_transactions WHERE loanId = :loanId ORDER BY createdAtMillis DESC")
  fun observeAdvanceLedger(loanId: String): Flow<List<AdvanceTransactionEntity>>

  @Query("SELECT * FROM advance_transactions WHERE loanId = :loanId ORDER BY createdAtMillis ASC")
  suspend fun getAdvanceTransactionsForLoan(loanId: String): List<AdvanceTransactionEntity>

  @Query("SELECT * FROM advance_transactions WHERE customerId = :customerId ORDER BY createdAtMillis DESC")
  fun observeAdvanceLedgerForCustomer(customerId: String): Flow<List<AdvanceTransactionEntity>>

  @Query("SELECT * FROM advance_transactions WHERE relatedPaymentId = :paymentId LIMIT 1")
  suspend fun getAdvanceTransactionByPaymentId(paymentId: String): AdvanceTransactionEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAdvanceTransaction(tx: AdvanceTransactionEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertAdvanceTransactions(txs: List<AdvanceTransactionEntity>)

  // --- Pre-Closure Requests ---
  @Query("SELECT COUNT(*) FROM preclosure_requests")
  suspend fun countPreClosureRequests(): Int

  @Query("SELECT * FROM preclosure_requests ORDER BY createdAtMillis DESC")
  fun observeAllPreClosures(): Flow<List<PreClosureRequestEntity>>

  @Query("SELECT * FROM preclosure_requests WHERE branchId = :branchId ORDER BY createdAtMillis DESC")
  fun observePreClosuresByBranch(branchId: String): Flow<List<PreClosureRequestEntity>>

  @Query("SELECT * FROM preclosure_requests WHERE assignedAgentId = :agentId ORDER BY createdAtMillis DESC")
  fun observePreClosuresForAgent(agentId: String): Flow<List<PreClosureRequestEntity>>

  @Query("SELECT * FROM preclosure_requests WHERE loanId = :loanId ORDER BY createdAtMillis DESC")
  fun observePreClosuresForLoan(loanId: String): Flow<List<PreClosureRequestEntity>>

  @Query("SELECT * FROM preclosure_requests WHERE customerId = :customerId ORDER BY createdAtMillis DESC")
  fun observePreClosuresForCustomer(customerId: String): Flow<List<PreClosureRequestEntity>>

  @Query("SELECT * FROM preclosure_requests WHERE id = :requestId LIMIT 1")
  suspend fun getPreClosureById(requestId: String): PreClosureRequestEntity?

  @Query("SELECT * FROM preclosure_requests WHERE settlementPaymentId = :paymentId LIMIT 1")
  suspend fun getPreClosureBySettlementPaymentId(paymentId: String): PreClosureRequestEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertPreClosureRequest(request: PreClosureRequestEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertPreClosureRequests(requests: List<PreClosureRequestEntity>)

  @Update
  suspend fun updatePreClosureRequest(request: PreClosureRequestEntity)

  // --- Sync Queue ---
  @Query("SELECT * FROM sync_queue ORDER BY createdAtMillis DESC")
  fun observeAllSyncQueue(): Flow<List<SyncQueueEntity>>

  @Query("SELECT * FROM sync_queue WHERE branchId = :branchId ORDER BY createdAtMillis DESC")
  fun observeSyncQueueByBranch(branchId: String): Flow<List<SyncQueueEntity>>

  @Query("SELECT * FROM sync_queue WHERE collectedByEmployeeId = :agentId ORDER BY createdAtMillis DESC")
  fun observeSyncQueueForAgent(agentId: String): Flow<List<SyncQueueEntity>>

  @Query("SELECT * FROM sync_queue WHERE syncStatus != :confirmedStatus ORDER BY createdAtMillis ASC")
  suspend fun getPendingSyncItems(confirmedStatus: SyncStatus = SyncStatus.SERVER_CONFIRMED): List<SyncQueueEntity>

  @Query("SELECT * FROM sync_queue WHERE paymentId = :paymentId LIMIT 1")
  suspend fun getSyncQueueItemByPaymentId(paymentId: String): SyncQueueEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertSyncQueueItem(item: SyncQueueEntity)

  @Update
  suspend fun updateSyncQueueItem(item: SyncQueueEntity)

  // --- Audit Logs ---
  @Query("SELECT * FROM audit_logs ORDER BY createdAtMillis DESC LIMIT 500")
  fun observeAllAuditLogs(): Flow<List<AuditLogEntity>>

  @Query("SELECT * FROM audit_logs WHERE branchId = :branchId ORDER BY createdAtMillis DESC LIMIT 500")
  fun observeAuditLogsByBranch(branchId: String): Flow<List<AuditLogEntity>>

  @Query("SELECT * FROM audit_logs WHERE actorEmployeeId = :actorId ORDER BY createdAtMillis DESC LIMIT 500")
  fun observeAuditLogsForActor(actorId: String): Flow<List<AuditLogEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAuditLog(log: AuditLogEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertAuditLogs(logs: List<AuditLogEntity>)

  // --- Atomic Multi-Table Financial Transactions ---
  @Transaction
  suspend fun commitOwnerInitialization(
    branch: BranchEntity,
    owner: EmployeeEntity,
    auditLog: AuditLogEntity,
  ) {
    insertBranch(branch)
    insertEmployee(owner)
    insertAuditLog(auditLog)
  }

  @Transaction
  suspend fun commitLoanWithSchedule(
    loan: LoanEntity,
    schedules: List<InstallmentScheduleEntity>,
    auditLog: AuditLogEntity,
  ) {
    insertLoan(loan)
    if (schedules.isNotEmpty()) {
      insertSchedules(schedules)
    }
    insertAuditLog(auditLog)
  }

  @Transaction
  suspend fun commitLoanApprovalWithSchedule(
    updatedLoan: LoanEntity,
    schedules: List<InstallmentScheduleEntity>,
    auditLog: AuditLogEntity,
  ) {
    updateLoan(updatedLoan)
    if (schedules.isNotEmpty()) {
      insertSchedules(schedules)
    }
    insertAuditLog(auditLog)
  }

  @Transaction
  suspend fun commitLoanStatusUpdate(
    updatedLoan: LoanEntity,
    auditLog: AuditLogEntity,
  ) {
    updateLoan(updatedLoan)
    insertAuditLog(auditLog)
  }

  @Transaction
  suspend fun commitPaymentCollection(
    payment: PaymentEntity,
    updatedLoan: LoanEntity,
    updatedSchedules: List<InstallmentScheduleEntity>,
    advanceTx: AdvanceTransactionEntity?,
    syncItem: SyncQueueEntity,
    auditLog: AuditLogEntity,
  ) {
    insertPayment(payment)
    updateLoan(updatedLoan)
    if (updatedSchedules.isNotEmpty()) {
      updateSchedules(updatedSchedules)
    }
    if (advanceTx != null) {
      insertAdvanceTransaction(advanceTx)
    }
    insertSyncQueueItem(syncItem)
    insertAuditLog(auditLog)
  }

  @Transaction
  suspend fun commitPaymentReversal(
    updatedOriginalPayment: PaymentEntity,
    adjustment: PaymentAdjustmentEntity,
    updatedLoan: LoanEntity,
    updatedSchedules: List<InstallmentScheduleEntity>,
    reversalAdvanceTx: AdvanceTransactionEntity?,
    auditLog: AuditLogEntity,
  ) {
    updatePayment(updatedOriginalPayment)
    insertPaymentAdjustment(adjustment)
    updateLoan(updatedLoan)
    if (updatedSchedules.isNotEmpty()) {
      updateSchedules(updatedSchedules)
    }
    if (reversalAdvanceTx != null) {
      insertAdvanceTransaction(reversalAdvanceTx)
    }
    insertAuditLog(auditLog)
  }

  @Transaction
  suspend fun commitPaymentCorrection(
    updatedOriginalPayment: PaymentEntity,
    replacementPayment: PaymentEntity,
    adjustment: PaymentAdjustmentEntity,
    updatedLoan: LoanEntity,
    updatedSchedules: List<InstallmentScheduleEntity>,
    reversalAdvanceTx: AdvanceTransactionEntity?,
    replacementAdvanceTx: AdvanceTransactionEntity?,
    replacementSyncItem: SyncQueueEntity,
    auditLog: AuditLogEntity,
  ) {
    updatePayment(updatedOriginalPayment)
    insertPayment(replacementPayment)
    insertPaymentAdjustment(adjustment)
    updateLoan(updatedLoan)
    if (updatedSchedules.isNotEmpty()) {
      updateSchedules(updatedSchedules)
    }
    if (reversalAdvanceTx != null) {
      insertAdvanceTransaction(reversalAdvanceTx)
    }
    if (replacementAdvanceTx != null) {
      insertAdvanceTransaction(replacementAdvanceTx)
    }
    insertSyncQueueItem(replacementSyncItem)
    insertAuditLog(auditLog)
  }

  @Transaction
  suspend fun commitAdvanceApplication(
    updatedLoan: LoanEntity,
    updatedSchedules: List<InstallmentScheduleEntity>,
    advanceTx: AdvanceTransactionEntity,
    auditLog: AuditLogEntity,
  ) {
    updateLoan(updatedLoan)
    updateSchedules(updatedSchedules)
    insertAdvanceTransaction(advanceTx)
    insertAuditLog(auditLog)
  }

  @Transaction
  suspend fun commitPreClosureSettlement(
    updatedRequest: PreClosureRequestEntity,
    updatedLoan: LoanEntity,
    waivedSchedules: List<InstallmentScheduleEntity>,
    settlementPayment: PaymentEntity,
    syncItem: SyncQueueEntity,
    auditLog: AuditLogEntity,
  ) {
    updatePreClosureRequest(updatedRequest)
    insertPayment(settlementPayment)
    updateLoan(updatedLoan)
    if (waivedSchedules.isNotEmpty()) {
      updateSchedules(waivedSchedules)
    }
    insertSyncQueueItem(syncItem)
    insertAuditLog(auditLog)
  }

  @Transaction
  suspend fun markPaymentServerConfirmed(
    updatedQueueItem: SyncQueueEntity,
    updatedPayment: PaymentEntity,
    auditLog: AuditLogEntity,
  ) {
    updateSyncQueueItem(updatedQueueItem)
    updatePayment(updatedPayment)
    insertAuditLog(auditLog)
  }
}
