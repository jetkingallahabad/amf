package com.example.data.repository

import com.example.data.local.AdvanceTransactionEntity
import com.example.data.local.AuditLogEntity
import com.example.data.local.BranchEntity
import com.example.data.local.CustomerDocumentEntity
import com.example.data.local.CustomerEntity
import com.example.data.local.EmployeeEntity
import com.example.data.local.InstallmentScheduleEntity
import com.example.data.local.LoanEntity
import com.example.data.local.LoanFinanceDao
import com.example.data.local.PaymentAdjustmentEntity
import com.example.data.local.PaymentEntity
import com.example.data.local.PreClosureRequestEntity
import com.example.data.local.SyncQueueEntity
import com.example.data.local.encodeUidList
import com.example.data.remote.FirestoreAdvanceTransaction
import com.example.data.remote.FirestoreAuditLog
import com.example.data.remote.FirestoreBranch
import com.example.data.remote.FirestoreCustomer
import com.example.data.remote.FirestoreCustomerDocument
import com.example.data.remote.FirestoreEmployee
import com.example.data.remote.FirestoreInstallmentSchedule
import com.example.data.remote.FirestoreLoan
import com.example.data.remote.FirestorePayloads
import com.example.data.remote.FirestorePayment
import com.example.data.remote.FirestorePaymentAdjustment
import com.example.data.remote.FirestorePreClosureRequest
import com.example.data.sync.SyncEngine
import com.example.domain.model.CustomerKycValidator
import com.example.domain.model.DocumentType
import com.example.domain.model.InstallmentStatus
import com.example.domain.model.LoanProductType
import com.example.domain.model.LoanScheduleCalculator
import com.example.domain.model.LoanStatus
import com.example.domain.model.PaymentAdjustmentType
import com.example.domain.model.PaymentAllocationCalculator
import com.example.domain.model.PaymentMode
import com.example.domain.model.PaymentRecordStatus
import com.example.domain.model.PaymentType
import com.example.domain.model.PreClosureStatus
import com.example.domain.model.RepaymentFrequency
import com.example.domain.model.SyncStatus
import com.example.domain.model.UserRole
import com.example.util.DocumentStorageHelper
import com.example.util.IndianFinanceFormatter
import com.example.util.OperationType
import com.example.util.handleFirestoreError
import com.example.util.toUserFriendlyFirestoreMessage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

class CustomerLoanRepository(
  private val dao: LoanFinanceDao,
  private val syncEngine: SyncEngine,
  private val firestore: FirebaseFirestore? = null,
  private val firebaseAuth: FirebaseAuth? = null,
) {

  // --- Role-Scoped Local + Cloud Mirrored Reactive Streams ---

  fun observePermittedCustomers(actor: EmployeeEntity): Flow<List<CustomerEntity>> = when (actor.role) {
    UserRole.OWNER -> dao.observeAllCustomers()
    UserRole.BRANCH_MANAGER -> dao.observeCustomersByBranch(actor.branchId)
    UserRole.COLLECTION_AGENT -> dao.observeCustomersForAgent(actor.id)
  }

  fun observePermittedLoans(actor: EmployeeEntity): Flow<List<LoanEntity>> = when (actor.role) {
    UserRole.OWNER -> dao.observeAllLoans()
    UserRole.BRANCH_MANAGER -> dao.observeLoansByBranch(actor.branchId)
    UserRole.COLLECTION_AGENT -> dao.observeLoansForAgent(actor.id)
  }

  fun observePermittedSchedules(actor: EmployeeEntity): Flow<List<InstallmentScheduleEntity>> = when (actor.role) {
    UserRole.OWNER -> dao.observeAllSchedules()
    UserRole.BRANCH_MANAGER -> dao.observeSchedulesByBranch(actor.branchId)
    UserRole.COLLECTION_AGENT -> dao.observeSchedulesForAgent(actor.id)
  }

  fun observePermittedPayments(actor: EmployeeEntity): Flow<List<PaymentEntity>> = when (actor.role) {
    UserRole.OWNER -> dao.observeAllPayments()
    UserRole.BRANCH_MANAGER -> dao.observePaymentsByBranch(actor.branchId)
    UserRole.COLLECTION_AGENT -> dao.observePaymentsByAgent(actor.id)
  }

  fun observePermittedAdjustments(actor: EmployeeEntity): Flow<List<PaymentAdjustmentEntity>> = when (actor.role) {
    UserRole.OWNER -> dao.observeAllAdjustments()
    UserRole.BRANCH_MANAGER -> dao.observeAdjustmentsByBranch(actor.branchId)
    UserRole.COLLECTION_AGENT -> dao.observeAdjustmentsForAgent(actor.id)
  }

  fun observePermittedPreClosures(actor: EmployeeEntity): Flow<List<PreClosureRequestEntity>> = when (actor.role) {
    UserRole.OWNER -> dao.observeAllPreClosures()
    UserRole.BRANCH_MANAGER -> dao.observePreClosuresByBranch(actor.branchId)
    UserRole.COLLECTION_AGENT -> dao.observePreClosuresForAgent(actor.id)
  }

  fun observePermittedSyncQueue(actor: EmployeeEntity): Flow<List<SyncQueueEntity>> = when (actor.role) {
    UserRole.OWNER -> dao.observeAllSyncQueue()
    UserRole.BRANCH_MANAGER -> dao.observeSyncQueueByBranch(actor.branchId)
    UserRole.COLLECTION_AGENT -> dao.observeSyncQueueForAgent(actor.id)
  }

  fun observePermittedAuditLogs(actor: EmployeeEntity): Flow<List<AuditLogEntity>> {
    if (actor.role != UserRole.OWNER && !actor.canViewAuditLogs) {
      return flowOf(emptyList())
    }
    return when (actor.role) {
      UserRole.OWNER -> dao.observeAllAuditLogs()
      UserRole.BRANCH_MANAGER -> dao.observeAuditLogsByBranch(actor.branchId)
      UserRole.COLLECTION_AGENT -> dao.observeAuditLogsForActor(actor.id)
    }
  }

  fun observeCustomerById(customerId: String): Flow<CustomerEntity?> = dao.observeCustomerById(customerId)
  fun observeCustomerLoans(customerId: String): Flow<List<LoanEntity>> = dao.observeLoansByCustomer(customerId)
  fun observeCustomerDocuments(customerId: String): Flow<List<CustomerDocumentEntity>> =
    dao.observeCustomerDocuments(customerId)
  fun observeCustomerPayments(customerId: String): Flow<List<PaymentEntity>> =
    dao.observePaymentsForCustomer(customerId)
  fun observeCustomerAdjustments(customerId: String): Flow<List<PaymentAdjustmentEntity>> =
    dao.observeAdjustmentsForCustomer(customerId)
  fun observeCustomerAdvanceLedger(customerId: String): Flow<List<AdvanceTransactionEntity>> =
    dao.observeAdvanceLedgerForCustomer(customerId)
  fun observeCustomerPreClosures(customerId: String): Flow<List<PreClosureRequestEntity>> =
    dao.observePreClosuresForCustomer(customerId)

  fun observeLoanById(loanId: String): Flow<LoanEntity?> = dao.observeLoanById(loanId)
  fun observeLoanSchedule(loanId: String): Flow<List<InstallmentScheduleEntity>> = dao.observeScheduleForLoan(loanId)
  fun observeLoanPayments(loanId: String): Flow<List<PaymentEntity>> = dao.observePaymentsForLoan(loanId)
  fun observeLoanAdjustments(loanId: String): Flow<List<PaymentAdjustmentEntity>> = dao.observeAdjustmentsForLoan(loanId)
  fun observeLoanAdvanceLedger(loanId: String): Flow<List<AdvanceTransactionEntity>> = dao.observeAdvanceLedger(loanId)
  fun observeLoanPreClosures(loanId: String): Flow<List<PreClosureRequestEntity>> = dao.observePreClosuresForLoan(loanId)

  // --- Direct Cloud Firestore Streams & Getters (Strictly Aligned with firestore.rules) ---

  fun observeCloudCustomers(actor: EmployeeEntity): Flow<List<CustomerEntity>> {
    val fs = checkNotNull(firestore) { "Firestore not configured" }
    val ownerUid = actor.orgOwnerUid.ifBlank { actor.id }
    val query = when (actor.role) {
      UserRole.OWNER -> fs.collection("customers").whereEqualTo("orgOwnerUid", ownerUid)
      UserRole.BRANCH_MANAGER -> fs.collection("customers").whereArrayContains("branchManagerUids", actor.id)
      UserRole.COLLECTION_AGENT -> fs.collection("customers").whereEqualTo("assignedAgentUid", actor.id)
    }
    return query.snapshots().map { snap ->
      snap.documents
        .mapNotNull { it.toObject(FirestoreCustomer::class.java)?.toEntity() }
        .sortedByDescending { it.createdAtMillis }
    }
  }

  fun observeCloudLoans(actor: EmployeeEntity): Flow<List<LoanEntity>> {
    val fs = checkNotNull(firestore) { "Firestore not configured" }
    val ownerUid = actor.orgOwnerUid.ifBlank { actor.id }
    val query = when (actor.role) {
      UserRole.OWNER -> fs.collection("loans").whereEqualTo("orgOwnerUid", ownerUid)
      UserRole.BRANCH_MANAGER -> fs.collection("loans").whereArrayContains("branchManagerUids", actor.id)
      UserRole.COLLECTION_AGENT -> fs.collection("loans").whereEqualTo("assignedAgentUid", actor.id)
    }
    return query.snapshots().map { snap ->
      snap.documents
        .mapNotNull { it.toObject(FirestoreLoan::class.java)?.toEntity() }
        .sortedByDescending { it.createdAtMillis }
    }
  }

  fun observeCloudSchedules(actor: EmployeeEntity): Flow<List<InstallmentScheduleEntity>> {
    val fs = checkNotNull(firestore) { "Firestore not configured" }
    val ownerUid = actor.orgOwnerUid.ifBlank { actor.id }
    val query = when (actor.role) {
      UserRole.OWNER -> fs.collection("installment_schedules").whereEqualTo("orgOwnerUid", ownerUid)
      UserRole.BRANCH_MANAGER -> fs.collection("installment_schedules").whereArrayContains("branchManagerUids", actor.id)
      UserRole.COLLECTION_AGENT -> fs.collection("installment_schedules").whereEqualTo("assignedAgentUid", actor.id)
    }
    return query.snapshots().map { snap ->
      snap.documents
        .mapNotNull { it.toObject(FirestoreInstallmentSchedule::class.java)?.toEntity() }
        .sortedWith(compareBy({ it.dueDateEpochMillis }, { it.installmentNumber }))
    }
  }

  fun observeCloudPayments(actor: EmployeeEntity): Flow<List<PaymentEntity>> {
    val fs = checkNotNull(firestore) { "Firestore not configured" }
    val ownerUid = actor.orgOwnerUid.ifBlank { actor.id }
    val query = when (actor.role) {
      UserRole.OWNER -> fs.collection("payments").whereEqualTo("orgOwnerUid", ownerUid)
      UserRole.BRANCH_MANAGER -> fs.collection("payments").whereArrayContains("branchManagerUids", actor.id)
      UserRole.COLLECTION_AGENT -> fs.collection("payments").whereEqualTo("assignedAgentUid", actor.id)
    }
    return query.snapshots().map { snap ->
      snap.documents
        .mapNotNull { it.toObject(FirestorePayment::class.java)?.toEntity() }
        .sortedByDescending { it.collectedAtMillis }
    }
  }

  fun observeCloudPaymentAdjustments(actor: EmployeeEntity): Flow<List<PaymentAdjustmentEntity>> {
    val fs = checkNotNull(firestore) { "Firestore not configured" }
    val ownerUid = actor.orgOwnerUid.ifBlank { actor.id }
    val query = when (actor.role) {
      UserRole.OWNER -> fs.collection("payment_adjustments").whereEqualTo("orgOwnerUid", ownerUid)
      UserRole.BRANCH_MANAGER -> fs.collection("payment_adjustments").whereArrayContains("branchManagerUids", actor.id)
      UserRole.COLLECTION_AGENT -> fs.collection("payment_adjustments").whereEqualTo("assignedAgentUid", actor.id)
    }
    return query.snapshots().map { snap ->
      snap.documents
        .mapNotNull { it.toObject(FirestorePaymentAdjustment::class.java)?.toEntity() }
        .sortedByDescending { it.createdAtMillis }
    }
  }

  fun observeCloudCustomerDocuments(actor: EmployeeEntity): Flow<List<CustomerDocumentEntity>> {
    val fs = checkNotNull(firestore) { "Firestore not configured" }
    val ownerUid = actor.orgOwnerUid.ifBlank { actor.id }
    val query = when (actor.role) {
      UserRole.OWNER -> fs.collection("customer_documents").whereEqualTo("orgOwnerUid", ownerUid)
      UserRole.BRANCH_MANAGER -> fs.collection("customer_documents").whereArrayContains("branchManagerUids", actor.id)
      UserRole.COLLECTION_AGENT -> fs.collection("customer_documents").whereEqualTo("assignedAgentUid", actor.id)
    }
    return query.snapshots().map { snap ->
      snap.documents
        .mapNotNull { it.toObject(FirestoreCustomerDocument::class.java)?.toEntity() }
        .sortedByDescending { it.createdAtMillis }
    }
  }

  fun observeCloudAdvanceTransactions(actor: EmployeeEntity): Flow<List<AdvanceTransactionEntity>> {
    val fs = checkNotNull(firestore) { "Firestore not configured" }
    val ownerUid = actor.orgOwnerUid.ifBlank { actor.id }
    val query = when (actor.role) {
      UserRole.OWNER -> fs.collection("advance_transactions").whereEqualTo("orgOwnerUid", ownerUid)
      UserRole.BRANCH_MANAGER -> fs.collection("advance_transactions").whereArrayContains("branchManagerUids", actor.id)
      UserRole.COLLECTION_AGENT -> fs.collection("advance_transactions").whereEqualTo("assignedAgentUid", actor.id)
    }
    return query.snapshots().map { snap ->
      snap.documents
        .mapNotNull { it.toObject(FirestoreAdvanceTransaction::class.java)?.toEntity() }
        .sortedByDescending { it.createdAtMillis }
    }
  }

  fun observeCloudPreClosures(actor: EmployeeEntity): Flow<List<PreClosureRequestEntity>> {
    val fs = checkNotNull(firestore) { "Firestore not configured" }
    val ownerUid = actor.orgOwnerUid.ifBlank { actor.id }
    val query = when (actor.role) {
      UserRole.OWNER -> fs.collection("preclosure_requests").whereEqualTo("orgOwnerUid", ownerUid)
      UserRole.BRANCH_MANAGER -> fs.collection("preclosure_requests").whereArrayContains("branchManagerUids", actor.id)
      UserRole.COLLECTION_AGENT -> fs.collection("preclosure_requests").whereEqualTo("assignedAgentUid", actor.id)
    }
    return query.snapshots().map { snap ->
      snap.documents
        .mapNotNull { it.toObject(FirestorePreClosureRequest::class.java)?.toEntity() }
        .sortedByDescending { it.createdAtMillis }
    }
  }

  fun observeCloudAuditLogs(actor: EmployeeEntity): Flow<List<AuditLogEntity>> {
    val fs = checkNotNull(firestore) { "Firestore not configured" }
    if (actor.role != UserRole.OWNER && !actor.canViewAuditLogs) return flowOf(emptyList())
    val ownerUid = actor.orgOwnerUid.ifBlank { actor.id }
    val query = when (actor.role) {
      UserRole.OWNER -> fs.collection("audit_logs").whereEqualTo("orgOwnerUid", ownerUid)
      UserRole.BRANCH_MANAGER -> fs.collection("audit_logs").whereArrayContains("branchManagerUids", actor.id)
      UserRole.COLLECTION_AGENT -> fs.collection("audit_logs").whereEqualTo("actorUid", actor.id)
    }
    return query.snapshots().map { snap ->
      snap.documents
        .mapNotNull { it.toObject(FirestoreAuditLog::class.java)?.toEntity() }
        .sortedByDescending { it.createdAtMillis }
    }
  }

  suspend fun getCloudCustomerById(customerId: String): Result<CustomerEntity> = withContext(Dispatchers.IO) {
    runCatching {
      val fs = checkNotNull(firestore) { "Firestore not configured" }
      try {
        val snap = fs.collection("customers").document(customerId).get().await()
        snap.toObject(FirestoreCustomer::class.java)?.toEntity()
          ?: throw NoSuchElementException("Customer $customerId not found")
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.GET, "customers/$customerId")
        throw e
      }
    }
  }

  suspend fun getCloudLoanById(loanId: String): Result<LoanEntity> = withContext(Dispatchers.IO) {
    runCatching {
      val fs = checkNotNull(firestore) { "Firestore not configured" }
      try {
        val snap = fs.collection("loans").document(loanId).get().await()
        snap.toObject(FirestoreLoan::class.java)?.toEntity()
          ?: throw NoSuchElementException("Loan $loanId not found")
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.GET, "loans/$loanId")
        throw e
      }
    }
  }

  /**
   * Starts lifecycle-scoped real-time mirroring of permitted Cloud Firestore financial ledgers
   * into the local Room SQLite database for seamless multi-device sync + offline access.
   */
  fun startRealtimeLedgerSync(
    actor: EmployeeEntity,
    scope: CoroutineScope,
    onSyncComplete: () -> Unit,
    onSyncError: (String) -> Unit,
  ): List<Job> {
    if (firestore == null || firebaseAuth?.currentUser == null) {
      onSyncComplete()
      return emptyList()
    }
    val jobs = mutableListOf<Job>()

    jobs += scope.launch(Dispatchers.IO) {
      var firstDelivered = false
      observeCloudCustomers(actor)
        .catch { e ->
          if (e is Exception) handleFirestoreError(e, OperationType.LIST, "customers")
          onSyncError(toUserFriendlyFirestoreMessage(e))
          if (!firstDelivered) {
            firstDelivered = true
            onSyncComplete()
          }
        }
        .collect { list ->
          dao.upsertCustomers(list)
          if (!firstDelivered) {
            firstDelivered = true
            onSyncComplete()
          }
        }
    }

    jobs += scope.launch(Dispatchers.IO) {
      observeCloudLoans(actor)
        .catch { e ->
          if (e is Exception) handleFirestoreError(e, OperationType.LIST, "loans")
          onSyncError(toUserFriendlyFirestoreMessage(e))
        }
        .collect { list ->
          val pendingLoanIds = dao.getLoanIdsWithUnsyncedPayments().toSet()
          val safeList = if (pendingLoanIds.isEmpty()) list else list.filterNot { it.id in pendingLoanIds }
          if (safeList.isNotEmpty()) dao.upsertLoans(safeList)
        }
    }

    jobs += scope.launch(Dispatchers.IO) {
      observeCloudSchedules(actor)
        .catch { e ->
          if (e is Exception) handleFirestoreError(e, OperationType.LIST, "installment_schedules")
          onSyncError(toUserFriendlyFirestoreMessage(e))
        }
        .collect { list ->
          val pendingLoanIds = dao.getLoanIdsWithUnsyncedPayments().toSet()
          val safeList = if (pendingLoanIds.isEmpty()) list else list.filterNot { it.loanId in pendingLoanIds }
          if (safeList.isNotEmpty()) dao.insertSchedules(safeList)
        }
    }

    jobs += scope.launch(Dispatchers.IO) {
      observeCloudPayments(actor)
        .catch { e ->
          if (e is Exception) handleFirestoreError(e, OperationType.LIST, "payments")
          onSyncError(toUserFriendlyFirestoreMessage(e))
        }
        .collect { list -> dao.upsertPayments(list) }
    }

    jobs += scope.launch(Dispatchers.IO) {
      observeCloudPaymentAdjustments(actor)
        .catch { e ->
          if (e is Exception) handleFirestoreError(e, OperationType.LIST, "payment_adjustments")
        }
        .collect { list -> dao.upsertPaymentAdjustments(list) }
    }

    jobs += scope.launch(Dispatchers.IO) {
      observeCloudCustomerDocuments(actor)
        .catch { e ->
          if (e is Exception) handleFirestoreError(e, OperationType.LIST, "customer_documents")
        }
        .collect { list -> dao.upsertCustomerDocuments(list) }
    }

    jobs += scope.launch(Dispatchers.IO) {
      observeCloudAdvanceTransactions(actor)
        .catch { e ->
          if (e is Exception) handleFirestoreError(e, OperationType.LIST, "advance_transactions")
        }
        .collect { list -> dao.upsertAdvanceTransactions(list) }
    }

    jobs += scope.launch(Dispatchers.IO) {
      observeCloudPreClosures(actor)
        .catch { e ->
          if (e is Exception) handleFirestoreError(e, OperationType.LIST, "preclosure_requests")
          onSyncError(toUserFriendlyFirestoreMessage(e))
        }
        .collect { list ->
          val pendingLoanIds = dao.getLoanIdsWithUnsyncedPayments().toSet()
          val safeList = if (pendingLoanIds.isEmpty()) list else list.filterNot { it.loanId in pendingLoanIds }
          if (safeList.isNotEmpty()) dao.upsertPreClosureRequests(safeList)
        }
    }

    if (actor.role == UserRole.OWNER || actor.canViewAuditLogs) {
      jobs += scope.launch(Dispatchers.IO) {
        observeCloudAuditLogs(actor)
          .catch { e ->
            if (e is Exception) handleFirestoreError(e, OperationType.LIST, "audit_logs")
          }
          .collect { list -> dao.upsertAuditLogs(list) }
      }
    }

    return jobs
  }

  private fun assertCanAccessCustomer(actor: EmployeeEntity, customer: CustomerEntity) {
    when (actor.role) {
      UserRole.OWNER -> Unit
      UserRole.BRANCH_MANAGER -> {
        if (customer.branchId != actor.branchId) {
          throw SecurityException("Access denied: Customer belongs to a different branch.")
        }
      }
      UserRole.COLLECTION_AGENT -> {
        if (customer.assignedAgentId != actor.id && customer.createdByEmployeeId != actor.id) {
          throw SecurityException("Access denied: Customer is not assigned to you.")
        }
      }
    }
  }

  private fun assertCanAccessLoan(actor: EmployeeEntity, loan: LoanEntity) {
    when (actor.role) {
      UserRole.OWNER -> Unit
      UserRole.BRANCH_MANAGER -> {
        if (loan.branchId != actor.branchId) {
          throw SecurityException("Access denied: Loan belongs to a different branch.")
        }
      }
      UserRole.COLLECTION_AGENT -> {
        if (loan.assignedAgentId != actor.id) {
          throw SecurityException("Access denied: Loan is not assigned to your collection beat.")
        }
      }
    }
  }

  private suspend fun generateUniqueCustomerCode(): String {
    var seq = dao.countCustomers() + 1
    repeat(8) {
      val suffix = UUID.randomUUID().toString().replace("-", "").take(4).uppercase(Locale.US)
      val candidate = "CUST-%04d-%s".format(Locale.US, seq, suffix)
      if (dao.getCustomerByCode(candidate) == null) {
        return candidate
      }
      seq++
    }
    return "CUST-${System.currentTimeMillis().toString().takeLast(6)}-${UUID.randomUUID().toString().take(4).uppercase(Locale.US)}"
  }

  private suspend fun generateUniqueLoanCode(): String {
    var seq = dao.countLoans() + 1
    repeat(8) {
      val suffix = UUID.randomUUID().toString().replace("-", "").take(4).uppercase(Locale.US)
      val candidate = "LN-%04d-%s".format(Locale.US, seq, suffix)
      if (dao.getLoanByCode(candidate) == null) {
        return candidate
      }
      seq++
    }
    return "LN-${System.currentTimeMillis().toString().takeLast(6)}-${UUID.randomUUID().toString().take(4).uppercase(Locale.US)}"
  }

  private suspend fun generateUniqueTransactionId(dateDdMmYyyy: String): String {
    val dateToken = dateDdMmYyyy.replace("-", "")
    repeat(8) {
      val token = UUID.randomUUID().toString().replace("-", "").take(8).uppercase(Locale.US)
      val candidate = "TXN-$dateToken-$token"
      if (dao.getPaymentByTransactionId(candidate) == null) {
        return candidate
      }
    }
    return "TXN-$dateToken-${System.currentTimeMillis().toString().takeLast(6)}-${UUID.randomUUID().toString().take(4).uppercase(Locale.US)}"
  }

  private suspend fun generateUniqueReceiptNumber(dateDdMmYyyy: String): String {
    val dateToken = dateDdMmYyyy.replace("-", "")
    var seq = dao.countPayments() + 1
    repeat(8) {
      val suffix = UUID.randomUUID().toString().replace("-", "").take(4).uppercase(Locale.US)
      val candidate = "RCPT-$dateToken-%04d-%s".format(Locale.US, seq, suffix)
      if (dao.getPaymentByReceiptNumber(candidate) == null) {
        return candidate
      }
      seq++
    }
    return "RCPT-$dateToken-${System.currentTimeMillis().toString().takeLast(6)}-${UUID.randomUUID().toString().take(4).uppercase(Locale.US)}"
  }

  private suspend fun generateUniqueAdjustmentCode(prefix: String, dateDdMmYyyy: String): String {
    val dateToken = dateDdMmYyyy.replace("-", "")
    val seq = dao.countPaymentAdjustments() + 1
    val suffix = UUID.randomUUID().toString().replace("-", "").take(4).uppercase(Locale.US)
    return "$prefix-$dateToken-%04d-%s".format(Locale.US, seq, suffix)
  }

  // --- 3. Customer Registration & Profile Update (Persistent Cloud Firestore + Room) ---
  suspend fun registerCustomer(
    actor: EmployeeEntity,
    fullName: String,
    guardianName: String,
    mobile: String,
    alternateMobile: String,
    address: String,
    city: String,
    kycDocumentType: String,
    kycDocumentNumber: String,
    occupation: String,
    monthlyIncomePaise: Long,
    branchId: String,
    assignedAgentId: String,
    bypassClientGuardForRulesTest: Boolean = false,
  ): Result<CustomerEntity> = withContext(Dispatchers.IO) {
    runCatching {
      if (!bypassClientGuardForRulesTest) {
        if (actor.role != UserRole.OWNER && !actor.canRegisterCustomer) {
          throw SecurityException("Your role does not have permission to register customers.")
        }
        if (actor.role != UserRole.OWNER && branchId != actor.branchId) {
          throw SecurityException("You can only register customers in your assigned branch.")
        }
      }
      val cleanName = fullName.trim()
      require(cleanName.length in 3..100) { "Customer full name must be between 3 and 100 characters." }
      val cleanMobile = CustomerKycValidator.validateIndianMobile(mobile, "Primary mobile number")
      val cleanAltMobile = if (alternateMobile.isBlank()) {
        ""
      } else {
        val alt = CustomerKycValidator.validateIndianMobile(alternateMobile, "Alternate mobile number")
        require(alt != cleanMobile) { "Alternate mobile number must be different from primary mobile number." }
        alt
      }
      val cleanAddress = address.trim()
      require(cleanAddress.length in 2..300) { "Customer residential/business address is required (2–300 characters)." }
      val cleanKycType = kycDocumentType.trim().ifBlank { "Aadhaar Card" }
      val cleanKycNumber = CustomerKycValidator.normalizeAndValidateKyc(cleanKycType, kycDocumentNumber)
      require(monthlyIncomePaise >= 0L) { "Monthly income cannot be negative." }

      val duplicateKycCustomer = dao.getCustomerByKycNumber(cleanKycNumber)
      if (duplicateKycCustomer != null) {
        throw IllegalArgumentException(
          "A customer (${duplicateKycCustomer.fullName} • ${duplicateKycCustomer.customerCode}) is already registered with KYC ID $cleanKycNumber.",
        )
      }

      val branch = resolveBranch(branchId) ?: throw IllegalArgumentException("Branch not found.")
      val effectiveAgentId = if (actor.role == UserRole.COLLECTION_AGENT && !bypassClientGuardForRulesTest) {
        actor.id
      } else {
        assignedAgentId.ifBlank { actor.id }
      }
      val assignedAgent = resolveEmployee(effectiveAgentId) ?: actor
      val ownerUid = actor.orgOwnerUid.ifBlank { branch.orgOwnerUid.ifBlank { actor.id } }
      val branchManagerUids = (branch.managerUidsList() + listOfNotNull(ownerUid, actor.id.takeIf { actor.role == UserRole.BRANCH_MANAGER }))
        .distinct()

      val customerCode = generateUniqueCustomerCode()
      val now = System.currentTimeMillis()
      val today = IndianFinanceFormatter.todayDdMmYyyy(now)

      val customer = CustomerEntity(
        id = UUID.randomUUID().toString(),
        orgOwnerUid = ownerUid,
        branchManagerUidsCsv = encodeUidList(branchManagerUids),
        customerCode = customerCode,
        fullName = cleanName,
        guardianName = guardianName.trim(),
        mobile = cleanMobile,
        alternateMobile = cleanAltMobile,
        address = cleanAddress,
        city = city.trim().ifBlank { branch.city },
        kycDocumentType = cleanKycType,
        kycDocumentNumber = cleanKycNumber,
        occupation = occupation.trim(),
        monthlyIncomePaise = monthlyIncomePaise.coerceAtLeast(0L),
        branchId = branch.id,
        branchName = branch.name,
        teamId = assignedAgent.teamId,
        assignedAgentId = assignedAgent.id,
        assignedAgentName = assignedAgent.fullName,
        createdByEmployeeId = actor.id,
        createdDateDdMmYyyy = today,
        createdAtMillis = now,
      )

      val audit = AuditLogEntity(
        id = UUID.randomUUID().toString(),
        orgOwnerUid = ownerUid,
        branchManagerUidsCsv = customer.branchManagerUidsCsv,
        actorEmployeeId = actor.id,
        actorName = actor.fullName,
        actorRole = actor.role,
        branchId = branch.id,
        category = "CUSTOMER",
        action = "CUSTOMER_REGISTERED",
        targetType = "CUSTOMER",
        targetId = customer.customerCode,
        summary = "Registered customer ${customer.fullName} (${customer.customerCode}), Mobile: ${customer.mobile}",
        timestampDdMmYyyyHhMm = IndianFinanceFormatter.formatDateTimeSeconds(now),
        createdAtMillis = now,
      )

      val fs = firestore
      if (fs != null) {
        try {
          val batch = fs.batch()
          batch.set(fs.collection("customers").document(customer.id), FirestorePayloads.customerCreate(customer))
          batch.set(fs.collection("audit_logs").document(audit.id), FirestorePayloads.auditLogCreate(audit))
          batch.commit().await()
        } catch (e: Exception) {
          handleFirestoreError(e, OperationType.CREATE, "customers/${customer.id}")
          throw e
        }
      }

      dao.insertCustomer(customer)
      dao.insertAuditLog(audit)
      customer
    }
  }

  suspend fun updateCustomerProfile(
    actor: EmployeeEntity,
    customerId: String,
    fullName: String,
    guardianName: String,
    mobile: String,
    alternateMobile: String,
    address: String,
    city: String,
    occupation: String,
    monthlyIncomePaise: Long,
  ): Result<CustomerEntity> = withContext(Dispatchers.IO) {
    runCatching {
      if (actor.role != UserRole.OWNER && !actor.canRegisterCustomer) {
        throw SecurityException("You do not have permission to update customer records.")
      }
      val existing = resolveCustomer(customerId) ?: throw IllegalArgumentException("Customer record not found.")
      assertCanAccessCustomer(actor, existing)

      val cleanName = fullName.trim()
      require(cleanName.length in 3..100) { "Customer full name must be between 3 and 100 characters." }
      val cleanMobile = CustomerKycValidator.validateIndianMobile(mobile, "Primary mobile number")
      val cleanAltMobile = if (alternateMobile.isBlank()) {
        ""
      } else {
        val alt = CustomerKycValidator.validateIndianMobile(alternateMobile, "Alternate mobile number")
        require(alt != cleanMobile) { "Alternate mobile number must be different from primary mobile number." }
        alt
      }
      val cleanAddress = address.trim()
      require(cleanAddress.length in 2..300) { "Address must be between 2 and 300 characters." }
      val cleanCity = city.trim().ifBlank { existing.city }
      require(cleanCity.length in 1..100) { "City is required." }
      require(monthlyIncomePaise >= 0L) { "Monthly income cannot be negative." }

      val updated = existing.copy(
        fullName = cleanName,
        guardianName = guardianName.trim(),
        mobile = cleanMobile,
        alternateMobile = cleanAltMobile,
        address = cleanAddress,
        city = cleanCity,
        occupation = occupation.trim(),
        monthlyIncomePaise = monthlyIncomePaise,
      )

      val now = System.currentTimeMillis()
      val ownerUid = existing.orgOwnerUid.ifBlank { actor.orgOwnerUid.ifBlank { actor.id } }
      val audit = AuditLogEntity(
        id = UUID.randomUUID().toString(),
        orgOwnerUid = ownerUid,
        branchManagerUidsCsv = existing.branchManagerUidsCsv,
        actorEmployeeId = actor.id,
        actorName = actor.fullName,
        actorRole = actor.role,
        branchId = existing.branchId,
        category = "CUSTOMER",
        action = "CUSTOMER_PROFILE_UPDATED",
        targetType = "CUSTOMER",
        targetId = existing.customerCode,
        summary = "Updated profile details for ${updated.fullName} (${existing.customerCode})",
        timestampDdMmYyyyHhMm = IndianFinanceFormatter.formatDateTimeSeconds(now),
        createdAtMillis = now,
      )

      val fs = firestore
      if (fs != null) {
        try {
          val batch = fs.batch()
          batch.update(
            fs.collection("customers").document(updated.id),
            mapOf(
              "fullName" to updated.fullName,
              "guardianName" to updated.guardianName,
              "mobile" to updated.mobile,
              "alternateMobile" to updated.alternateMobile,
              "address" to updated.address,
              "city" to updated.city,
              "occupation" to updated.occupation,
              "monthlyIncomePaise" to updated.monthlyIncomePaise,
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
          batch.set(fs.collection("audit_logs").document(audit.id), FirestorePayloads.auditLogCreate(audit))
          batch.commit().await()
        } catch (e: Exception) {
          handleFirestoreError(e, OperationType.UPDATE, "customers/${updated.id}")
          throw e
        }
      }

      dao.updateCustomer(updated)
      dao.insertAuditLog(audit)
      updated
    }
  }

  // --- 12. Customer Document Uploads ---
  suspend fun recordCustomerDocument(
    actor: EmployeeEntity,
    customerId: String,
    documentType: DocumentType,
    documentNumber: String,
    savedFile: DocumentStorageHelper.SavedDocumentFile,
  ): Result<CustomerDocumentEntity> = withContext(Dispatchers.IO) {
    runCatching {
      if (actor.role != UserRole.OWNER && !actor.canUploadDocuments) {
        throw SecurityException("You do not have permission to upload customer documents.")
      }
      val customer = resolveCustomer(customerId) ?: throw IllegalArgumentException("Customer not found.")
      assertCanAccessCustomer(actor, customer)

      val now = System.currentTimeMillis()
      val today = IndianFinanceFormatter.todayDdMmYyyy(now)
      val ownerUid = customer.orgOwnerUid.ifBlank { actor.orgOwnerUid.ifBlank { actor.id } }

      val doc = CustomerDocumentEntity(
        id = UUID.randomUUID().toString(),
        orgOwnerUid = ownerUid,
        branchManagerUidsCsv = customer.branchManagerUidsCsv,
        assignedAgentId = customer.assignedAgentId,
        customerId = customer.id,
        customerCode = customer.customerCode,
        branchId = customer.branchId,
        documentType = documentType,
        documentNumber = documentNumber.trim().uppercase(Locale.US),
        fileName = savedFile.originalFileName,
        localFilePath = savedFile.localFilePath,
        mimeType = savedFile.mimeType,
        fileSizeBytes = savedFile.fileSizeBytes,
        sha256Checksum = savedFile.sha256Checksum,
        uploadedByEmployeeId = actor.id,
        uploadedByName = actor.fullName,
        uploadedDateDdMmYyyy = today,
        createdAtMillis = now,
      )
      val audit = AuditLogEntity(
        id = UUID.randomUUID().toString(),
        orgOwnerUid = ownerUid,
        branchManagerUidsCsv = customer.branchManagerUidsCsv,
        actorEmployeeId = actor.id,
        actorName = actor.fullName,
        actorRole = actor.role,
        branchId = customer.branchId,
        category = "CUSTOMER",
        action = "DOCUMENT_UPLOADED",
        targetType = "CUSTOMER_DOC",
        targetId = customer.customerCode,
        summary = "Uploaded ${documentType.displayTitle} (${savedFile.originalFileName}) for ${customer.fullName}",
        timestampDdMmYyyyHhMm = IndianFinanceFormatter.formatDateTimeSeconds(now),
        createdAtMillis = now,
      )

      val fs = firestore
      if (fs != null) {
        try {
          val batch = fs.batch()
          batch.set(
            fs.collection("customer_documents").document(doc.id),
            FirestorePayloads.customerDocumentCreate(doc),
          )
          batch.set(fs.collection("audit_logs").document(audit.id), FirestorePayloads.auditLogCreate(audit))
          batch.commit().await()
        } catch (e: Exception) {
          handleFirestoreError(e, OperationType.CREATE, "customer_documents/${doc.id}")
          throw e
        }
      }

      dao.insertCustomerDocument(doc)
      dao.insertAuditLog(audit)
      doc
    }
  }

  // --- 6, 7, 8. New Loan Application, Approval/Rejection & Disbursement Workflow ---
  suspend fun createLoanApplication(
    actor: EmployeeEntity,
    customerId: String,
    productType: LoanProductType,
    principalPaise: Long,
    fixedInstallmentPaise: Long,
    processingFeePaise: Long,
    repaymentFrequency: RepaymentFrequency,
    totalInstallments: Int,
    disbursementDateDdMmYyyy: String,
    assignedAgentId: String,
    disburseImmediately: Boolean,
    disbursementMode: PaymentMode = PaymentMode.CASH,
    disbursementReference: String = "",
    bypassClientGuardForRulesTest: Boolean = false,
  ): Result<LoanEntity> = withContext(Dispatchers.IO) {
    runCatching {
      if (!bypassClientGuardForRulesTest && actor.role != UserRole.OWNER && !actor.canCreateLoan) {
        throw SecurityException("Your role does not have permission to create loan applications.")
      }
      val customer = resolveCustomer(customerId) ?: throw IllegalArgumentException("Customer not found.")
      if (!bypassClientGuardForRulesTest) {
        assertCanAccessCustomer(actor, customer)
      }

      require(principalPaise in 10_000L..500_000_000L) {
        "Principal amount must be between ₹100 and ₹50,00,000."
      }
      require(fixedInstallmentPaise > 0L) {
        "Fixed EMI/EDI amount must be greater than ₹0."
      }
      require(processingFeePaise in 0L until principalPaise) {
        "Processing fee must be non-negative and less than the principal amount."
      }
      require(totalInstallments in 1..1000) {
        "Total installments must be between 1 and 1,000."
      }

      val quote = LoanScheduleCalculator.calculateFixedProductQuote(
        principalPaise = principalPaise,
        fixedInstallmentPaise = fixedInstallmentPaise,
        processingFeePaise = processingFeePaise,
        totalInstallments = totalInstallments,
        frequency = repaymentFrequency,
        disbursementDateDdMmYyyy = disbursementDateDdMmYyyy,
      ) ?: throw IllegalArgumentException("Invalid fixed EMI/EDI, total repayment, or date format (must be DD-MM-YYYY).")

      val effectiveDisbMode = if (disbursementMode == PaymentMode.ADVANCE_ADJUSTMENT) PaymentMode.CASH else disbursementMode
      val canApproveNow = disburseImmediately && (bypassClientGuardForRulesTest || actor.role == UserRole.OWNER || actor.canApproveLoan)
      if (canApproveNow && effectiveDisbMode != PaymentMode.CASH) {
        require(disbursementReference.trim().isNotEmpty()) {
          "UTR / Cheque / Reference number is required for ${effectiveDisbMode.displayTitle} disbursement."
        }
      }

      val agent = resolveEmployee(assignedAgentId.ifBlank { customer.assignedAgentId }) ?: actor
      val initialStatus = if (canApproveNow) LoanStatus.ACTIVE else LoanStatus.PENDING_APPROVAL

      val loanCode = generateUniqueLoanCode()
      val loanId = UUID.randomUUID().toString()
      val now = System.currentTimeMillis()
      val today = IndianFinanceFormatter.todayDdMmYyyy(now)
      val ownerUid = customer.orgOwnerUid.ifBlank { actor.orgOwnerUid.ifBlank { actor.id } }
      val netDisbursed = if (canApproveNow) (quote.principalPaise - quote.processingFeePaise).coerceAtLeast(0L) else 0L

      val loan = LoanEntity(
        id = loanId,
        orgOwnerUid = ownerUid,
        branchManagerUidsCsv = customer.branchManagerUidsCsv,
        loanCode = loanCode,
        customerId = customer.id,
        customerCode = customer.customerCode,
        customerName = customer.fullName,
        customerMobile = customer.mobile,
        branchId = customer.branchId,
        branchName = customer.branchName,
        teamId = agent.teamId,
        assignedAgentId = agent.id,
        assignedAgentName = agent.fullName,
        productType = productType,
        principalPaise = quote.principalPaise,
        annualInterestRateBps = 0,
        processingFeePaise = quote.processingFeePaise,
        totalInterestPaise = quote.totalMarginPaise,
        totalPayablePaise = quote.totalScheduledRepaymentPaise,
        installmentAmountPaise = quote.fixedInstallmentPaise,
        repaymentFrequency = repaymentFrequency,
        totalInstallments = quote.totalInstallments,
        paidAmountPaise = 0L,
        advanceBalancePaise = 0L,
        outstandingPaise = quote.totalScheduledRepaymentPaise,
        status = initialStatus,
        applicationDateDdMmYyyy = today,
        approvedDateDdMmYyyy = if (canApproveNow) today else "",
        approvalRemarks = if (canApproveNow) "Approved & disbursed at origination" else "",
        disbursementDateDdMmYyyy = disbursementDateDdMmYyyy.trim(),
        disbursementMode = effectiveDisbMode,
        disbursementReference = if (canApproveNow) disbursementReference.trim() else "",
        netDisbursedPaise = netDisbursed,
        firstDueDateDdMmYyyy = quote.repaymentStartDateDdMmYyyy,
        maturityDateDdMmYyyy = quote.expectedCompletionDateDdMmYyyy,
        closedDateDdMmYyyy = "",
        createdByEmployeeId = actor.id,
        approvedByEmployeeId = if (canApproveNow) actor.id else null,
        disbursedByEmployeeId = if (canApproveNow) actor.id else null,
        rejectedByEmployeeId = null,
        rejectionReason = "",
        createdAtMillis = now,
      )

      val schedules = if (canApproveNow) {
        buildInstallmentEntities(loan, quote)
      } else {
        emptyList()
      }

      val audit = AuditLogEntity(
        id = UUID.randomUUID().toString(),
        orgOwnerUid = ownerUid,
        branchManagerUidsCsv = loan.branchManagerUidsCsv,
        actorEmployeeId = actor.id,
        actorName = actor.fullName,
        actorRole = actor.role,
        branchId = loan.branchId,
        category = "LOAN",
        action = if (canApproveNow) "LOAN_CREATED_AND_DISBURSED" else "LOAN_APPLICATION_SUBMITTED",
        targetType = "LOAN",
        targetId = loan.loanCode,
        summary = "${if (canApproveNow) "Disbursed" else "Submitted application for"} ${repaymentFrequency.displayTitle} loan ${loan.loanCode} for ${customer.fullName}: Principal ${IndianFinanceFormatter.formatPaise(principalPaise)}, Total Payable ${IndianFinanceFormatter.formatPaise(quote.totalScheduledRepaymentPaise)} (${quote.totalInstallments} installments)",
        timestampDdMmYyyyHhMm = IndianFinanceFormatter.formatDateTimeSeconds(now),
        createdAtMillis = now,
      )

      val fs = firestore
      if (fs != null) {
        try {
          // Batch 1: Loan + Audit Log (~460 CEL); Schedule creates in chunks of 3 (~630 CEL each)
          val batch = fs.batch()
          batch.set(fs.collection("loans").document(loan.id), FirestorePayloads.loanCreate(loan))
          batch.set(fs.collection("audit_logs").document(audit.id), FirestorePayloads.auditLogCreate(audit))
          batch.commit().await()

          for (chunk in schedules.chunked(2)) {
            val extraBatch = fs.batch()
            for (sched in chunk) {
              extraBatch.set(
                fs.collection("installment_schedules").document(sched.id),
                FirestorePayloads.scheduleCreate(sched),
              )
            }
            extraBatch.commit().await()
          }
        } catch (e: Exception) {
          handleFirestoreError(e, OperationType.CREATE, "loans/${loan.id}")
          throw e
        }
      }

      dao.commitLoanWithSchedule(loan, schedules, audit)
      loan
    }
  }

  /**
   * Stage 1 Approval: Approves a PENDING_APPROVAL loan application into APPROVED state
   * so disbursement can be executed as a dedicated treasury step.
   */
  suspend fun approveLoanApplication(
    actor: EmployeeEntity,
    loanId: String,
    approvalRemarks: String = "",
  ): Result<LoanEntity> = withContext(Dispatchers.IO) {
    runCatching {
      if (actor.role != UserRole.OWNER && !actor.canApproveLoan) {
        throw SecurityException("You do not have permission to approve loan applications.")
      }
      val loan = resolveLoan(loanId) ?: throw IllegalArgumentException("Loan account not found.")
      assertCanAccessLoan(actor, loan)
      require(loan.status == LoanStatus.PENDING_APPROVAL) {
        "Only Pending Approval loan applications can be approved (current status: ${loan.status.displayTitle})."
      }

      val now = System.currentTimeMillis()
      val today = IndianFinanceFormatter.todayDdMmYyyy(now)
      val cleanRemarks = approvalRemarks.trim().ifBlank { "Sanctioned after KYC & credit review" }.take(300)

      val updatedLoan = loan.copy(
        status = LoanStatus.APPROVED,
        approvedByEmployeeId = actor.id,
        approvedDateDdMmYyyy = today,
        approvalRemarks = cleanRemarks,
      )

      val audit = AuditLogEntity(
        id = UUID.randomUUID().toString(),
        orgOwnerUid = loan.orgOwnerUid.ifBlank { actor.orgOwnerUid.ifBlank { actor.id } },
        branchManagerUidsCsv = loan.branchManagerUidsCsv,
        actorEmployeeId = actor.id,
        actorName = actor.fullName,
        actorRole = actor.role,
        branchId = loan.branchId,
        category = "LOAN",
        action = "LOAN_APPLICATION_APPROVED",
        targetType = "LOAN",
        targetId = loan.loanCode,
        summary = "Approved loan application ${loan.loanCode} for ${loan.customerName}: $cleanRemarks",
        timestampDdMmYyyyHhMm = IndianFinanceFormatter.formatDateTimeSeconds(now),
        createdAtMillis = now,
      )

      val fs = firestore
      if (fs != null) {
        try {
          val batch = fs.batch()
          batch.update(
            fs.collection("loans").document(updatedLoan.id),
            mapOf(
              "status" to LoanStatus.APPROVED.name,
              "approvedByUid" to actor.id,
              "approvedDateDdMmYyyy" to today,
              "approvalRemarks" to cleanRemarks,
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
          batch.set(fs.collection("audit_logs").document(audit.id), FirestorePayloads.auditLogCreate(audit))
          batch.commit().await()
        } catch (e: Exception) {
          handleFirestoreError(e, OperationType.UPDATE, "loans/${updatedLoan.id}")
          throw e
        }
      }

      dao.commitLoanStatusUpdate(updatedLoan, audit)
      updatedLoan
    }
  }

  /**
   * Rejects a PENDING_APPROVAL or APPROVED loan application, permanently preserving the historical
   * rejected record with reviewer attribution and mandatory rejection reason.
   */
  suspend fun rejectLoanApplication(
    actor: EmployeeEntity,
    loanId: String,
    rejectionReason: String,
  ): Result<LoanEntity> = withContext(Dispatchers.IO) {
    runCatching {
      if (actor.role != UserRole.OWNER && !actor.canApproveLoan) {
        throw SecurityException("You do not have permission to reject loan applications.")
      }
      val loan = resolveLoan(loanId) ?: throw IllegalArgumentException("Loan account not found.")
      assertCanAccessLoan(actor, loan)
      require(loan.status == LoanStatus.PENDING_APPROVAL || loan.status == LoanStatus.APPROVED) {
        "Only Pending or Approved (undisbursed) applications can be rejected (current status: ${loan.status.displayTitle})."
      }
      val cleanReason = rejectionReason.trim()
      require(cleanReason.length in 3..300) {
        "Enter a clear rejection reason (at least 3 characters)."
      }

      val now = System.currentTimeMillis()
      val today = IndianFinanceFormatter.todayDdMmYyyy(now)
      val updatedLoan = loan.copy(
        status = LoanStatus.REJECTED,
        outstandingPaise = 0L,
        rejectedByEmployeeId = actor.id,
        rejectionReason = cleanReason,
        closedDateDdMmYyyy = today,
      )

      val audit = AuditLogEntity(
        id = UUID.randomUUID().toString(),
        orgOwnerUid = loan.orgOwnerUid.ifBlank { actor.orgOwnerUid.ifBlank { actor.id } },
        branchManagerUidsCsv = loan.branchManagerUidsCsv,
        actorEmployeeId = actor.id,
        actorName = actor.fullName,
        actorRole = actor.role,
        branchId = loan.branchId,
        category = "LOAN",
        action = "LOAN_APPLICATION_REJECTED",
        targetType = "LOAN",
        targetId = loan.loanCode,
        summary = "Rejected loan application ${loan.loanCode} for ${loan.customerName}: $cleanReason",
        timestampDdMmYyyyHhMm = IndianFinanceFormatter.formatDateTimeSeconds(now),
        createdAtMillis = now,
      )

      val fs = firestore
      if (fs != null) {
        try {
          val batch = fs.batch()
          batch.update(
            fs.collection("loans").document(updatedLoan.id),
            mapOf(
              "status" to LoanStatus.REJECTED.name,
              "rejectedByUid" to actor.id,
              "rejectionReason" to cleanReason,
              "outstandingPaise" to 0L,
              "closedDateDdMmYyyy" to today,
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
          batch.set(fs.collection("audit_logs").document(audit.id), FirestorePayloads.auditLogCreate(audit))
          batch.commit().await()
        } catch (e: Exception) {
          handleFirestoreError(e, OperationType.UPDATE, "loans/${updatedLoan.id}")
          throw e
        }
      }

      dao.commitLoanStatusUpdate(updatedLoan, audit)
      updatedLoan
    }
  }

  /**
   * Disburses an APPROVED (or PENDING_APPROVAL) loan into ACTIVE status, recording disbursement mode,
   * UTR/reference, net payout after processing fee, and generating all Daily EDI / Weekly EMI installments.
   */
  suspend fun disburseLoan(
    actor: EmployeeEntity,
    loanId: String,
    disbursementDateDdMmYyyy: String,
    disbursementMode: PaymentMode = PaymentMode.CASH,
    disbursementReference: String = "",
    approvalRemarks: String = "",
  ): Result<LoanEntity> = withContext(Dispatchers.IO) {
    runCatching {
      if (actor.role != UserRole.OWNER && !actor.canApproveLoan) {
        throw SecurityException("You do not have permission to disburse loans.")
      }
      val loan = resolveLoan(loanId) ?: throw IllegalArgumentException("Loan account not found.")
      assertCanAccessLoan(actor, loan)
      require(loan.status == LoanStatus.APPROVED || loan.status == LoanStatus.PENDING_APPROVAL) {
        "Only Pending Approval or Approved loans can be disbursed (current status: ${loan.status.displayTitle})."
      }

      val effectiveMode = if (disbursementMode == PaymentMode.ADVANCE_ADJUSTMENT) PaymentMode.CASH else disbursementMode
      val cleanRef = disbursementReference.trim()
      if (effectiveMode != PaymentMode.CASH) {
        require(cleanRef.isNotEmpty()) {
          "UTR / Cheque / Reference number is required for ${effectiveMode.displayTitle} disbursement."
        }
      }

      val quote = LoanScheduleCalculator.calculateFixedProductQuote(
        principalPaise = loan.principalPaise,
        fixedInstallmentPaise = loan.installmentAmountPaise,
        processingFeePaise = loan.processingFeePaise,
        totalInstallments = loan.totalInstallments,
        frequency = loan.repaymentFrequency,
        disbursementDateDdMmYyyy = disbursementDateDdMmYyyy,
      ) ?: throw IllegalArgumentException("Enter a valid fixed EMI/EDI and disbursement date in DD-MM-YYYY format.")

      val now = System.currentTimeMillis()
      val today = IndianFinanceFormatter.todayDdMmYyyy(now)
      val netDisbursed = (loan.principalPaise - loan.processingFeePaise).coerceAtLeast(0L)
      val effectiveApprovedBy = loan.approvedByEmployeeId ?: actor.id
      val effectiveApprovedDate = loan.approvedDateDdMmYyyy.ifBlank { today }
      val effectiveRemarks = approvalRemarks.trim().ifBlank {
        loan.approvalRemarks.ifBlank { "Approved & disbursed" }
      }.take(300)

      val updatedLoan = loan.copy(
        status = LoanStatus.ACTIVE,
        approvedByEmployeeId = effectiveApprovedBy,
        approvedDateDdMmYyyy = effectiveApprovedDate,
        approvalRemarks = effectiveRemarks,
        disbursedByEmployeeId = actor.id,
        disbursementDateDdMmYyyy = disbursementDateDdMmYyyy.trim(),
        disbursementMode = effectiveMode,
        disbursementReference = cleanRef,
        netDisbursedPaise = netDisbursed,
        firstDueDateDdMmYyyy = quote.repaymentStartDateDdMmYyyy,
        maturityDateDdMmYyyy = quote.expectedCompletionDateDdMmYyyy,
      )
      val schedules = buildInstallmentEntities(updatedLoan, quote)
      val audit = AuditLogEntity(
        id = UUID.randomUUID().toString(),
        orgOwnerUid = loan.orgOwnerUid.ifBlank { actor.orgOwnerUid.ifBlank { actor.id } },
        branchManagerUidsCsv = loan.branchManagerUidsCsv,
        actorEmployeeId = actor.id,
        actorName = actor.fullName,
        actorRole = actor.role,
        branchId = loan.branchId,
        category = "LOAN",
        action = "LOAN_DISBURSED",
        targetType = "LOAN",
        targetId = loan.loanCode,
        summary = "Disbursed loan ${loan.loanCode} (${IndianFinanceFormatter.formatPaise(netDisbursed)} net via ${effectiveMode.displayTitle}) on ${updatedLoan.disbursementDateDdMmYyyy} with ${schedules.size} ${loan.repaymentFrequency.shortLabel} installments",
        timestampDdMmYyyyHhMm = IndianFinanceFormatter.formatDateTimeSeconds(now),
        createdAtMillis = now,
      )

      val fs = firestore
      if (fs != null) {
        try {
          // Batch 1: Loan Disbursement Update + Audit Log (~520 CEL); Schedule creates in chunks of 3 (~630 CEL each)
          val batch = fs.batch()
          batch.update(
            fs.collection("loans").document(updatedLoan.id),
            mapOf(
              "status" to updatedLoan.status.name,
              "approvedByUid" to effectiveApprovedBy,
              "approvedDateDdMmYyyy" to effectiveApprovedDate,
              "approvalRemarks" to effectiveRemarks,
              "disbursedByUid" to actor.id,
              "disbursementDateDdMmYyyy" to updatedLoan.disbursementDateDdMmYyyy,
              "disbursementMode" to effectiveMode.name,
              "disbursementReference" to cleanRef,
              "netDisbursedPaise" to netDisbursed,
              "firstDueDateDdMmYyyy" to updatedLoan.firstDueDateDdMmYyyy,
              "maturityDateDdMmYyyy" to updatedLoan.maturityDateDdMmYyyy,
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
          batch.set(fs.collection("audit_logs").document(audit.id), FirestorePayloads.auditLogCreate(audit))
          batch.commit().await()

          for (chunk in schedules.chunked(2)) {
            val extraBatch = fs.batch()
            for (sched in chunk) {
              extraBatch.set(
                fs.collection("installment_schedules").document(sched.id),
                FirestorePayloads.scheduleCreate(sched),
              )
            }
            extraBatch.commit().await()
          }
        } catch (e: Exception) {
          handleFirestoreError(e, OperationType.UPDATE, "loans/${updatedLoan.id}")
          throw e
        }
      }

      dao.commitLoanApprovalWithSchedule(updatedLoan, schedules, audit)
      updatedLoan
    }
  }

  suspend fun approveAndDisburseLoan(
    actor: EmployeeEntity,
    loanId: String,
    disbursementDateDdMmYyyy: String,
  ): Result<LoanEntity> = disburseLoan(
    actor = actor,
    loanId = loanId,
    disbursementDateDdMmYyyy = disbursementDateDdMmYyyy,
    disbursementMode = PaymentMode.CASH,
    disbursementReference = "",
  )

  private fun buildInstallmentEntities(
    loan: LoanEntity,
    quote: LoanScheduleCalculator.FixedLoanQuote,
  ): List<InstallmentScheduleEntity> {
    return (1..quote.totalInstallments).map { num ->
      val (dueStr, dueMillis) = IndianFinanceFormatter.addDays(
        loan.disbursementDateDdMmYyyy,
        num * quote.frequency.stepDays,
      )
      val amt = quote.fixedInstallmentPaise
      InstallmentScheduleEntity(
        id = UUID.randomUUID().toString(),
        orgOwnerUid = loan.orgOwnerUid,
        branchManagerUidsCsv = loan.branchManagerUidsCsv,
        loanId = loan.id,
        loanCode = loan.loanCode,
        customerId = loan.customerId,
        customerCode = loan.customerCode,
        customerName = loan.customerName,
        customerMobile = loan.customerMobile,
        branchId = loan.branchId,
        assignedAgentId = loan.assignedAgentId,
        installmentNumber = num,
        dueDateDdMmYyyy = dueStr,
        dueDateEpochMillis = dueMillis,
        frequency = quote.frequency,
        scheduledAmountPaise = amt,
        paidAmountPaise = 0L,
        status = InstallmentStatus.UPCOMING,
        paidDateDdMmYyyy = null,
        lastPaymentId = null,
      )
    }
  }

  // --- 9 & 10. Payment Collection & Advance Deposit (Room ACID + Cloud Firestore Sync) ---
  suspend fun collectPayment(
    actor: EmployeeEntity,
    loanId: String,
    amountPaise: Long,
    paymentMode: PaymentMode,
    paymentType: PaymentType,
    referenceNumber: String,
    remarks: String,
    collectionDateDdMmYyyy: String,
    syncImmediately: Boolean = true,
    idempotencyKey: String = "",
  ): Result<PaymentEntity> = withContext(Dispatchers.IO) {
    runCatching {
      if (paymentType == PaymentType.ADVANCE_COLLECTION) {
        if (actor.role != UserRole.OWNER && !actor.canManageAdvance) {
          throw SecurityException("You do not have permission to record advance payments.")
        }
      } else {
        if (actor.role != UserRole.OWNER && !actor.canCollectPayment) {
          throw SecurityException("You do not have permission to collect payments.")
        }
      }
      require(amountPaise > 0L) { "Collection amount must be greater than ₹0." }
      require(IndianFinanceFormatter.parseDdMmYyyyToMillis(collectionDateDdMmYyyy) != null) {
        "Collection date must be a valid DD-MM-YYYY date."
      }
      val cleanRef = referenceNumber.trim()
      if (paymentMode != PaymentMode.CASH && paymentMode != PaymentMode.ADVANCE_ADJUSTMENT) {
        require(cleanRef.isNotBlank()) { "UTR / Cheque / Reference number is required for ${paymentMode.displayTitle}." }
      }

      val effectiveIdempotencyKey = idempotencyKey.trim().takeIf { it.length >= 6 }
        ?: "IDEM-${UUID.randomUUID()}"

      // Duplicate submission guard #1: Client/Sync idempotency key
      val existingByIdempotency = dao.getPaymentByIdempotencyKey(effectiveIdempotencyKey)
      if (existingByIdempotency != null) {
        throw IllegalArgumentException(
          "Duplicate payment submission prevented: Transaction ${existingByIdempotency.transactionId} (${existingByIdempotency.receiptNumber}) is already saved.",
        )
      }

      val loan = resolveLoan(loanId) ?: throw IllegalArgumentException("Loan account not found.")
      assertCanAccessLoan(actor, loan)
      require(loan.status == LoanStatus.ACTIVE || loan.status == LoanStatus.PRE_CLOSURE_REQUESTED) {
        "Payments can only be collected on Active loans (current status: ${loan.status.displayTitle})."
      }

      // Duplicate submission guard #2: Active non-cash UTR / Cheque / Reference number on this loan
      if (paymentMode != PaymentMode.CASH && paymentMode != PaymentMode.ADVANCE_ADJUSTMENT && cleanRef.isNotEmpty()) {
        val duplicateRef = dao.getPaymentsForLoan(loan.id).firstOrNull {
          it.recordStatus == PaymentRecordStatus.ACTIVE &&
            it.paymentMode == paymentMode &&
            it.referenceNumber.equals(cleanRef, ignoreCase = true)
        }
        if (duplicateRef != null) {
          throw IllegalArgumentException(
            "Duplicate ${paymentMode.displayTitle} reference '$cleanRef' rejected: already recorded on active receipt ${duplicateRef.receiptNumber} (${duplicateRef.transactionId}).",
          )
        }
      }

      val now = System.currentTimeMillis()
      val transactionId = generateUniqueTransactionId(collectionDateDdMmYyyy)
      val receiptNumber = generateUniqueReceiptNumber(collectionDateDdMmYyyy)
      val paymentId = UUID.randomUUID().toString()
      val ownerUid = loan.orgOwnerUid.ifBlank { actor.orgOwnerUid.ifBlank { actor.id } }

      val schedules = dao.getScheduleForLoan(loan.id)
      val scheduleSnapshots = schedules.map {
        PaymentAllocationCalculator.ScheduleSnapshot(
          id = it.id,
          installmentNumber = it.installmentNumber,
          dueDateDdMmYyyy = it.dueDateDdMmYyyy,
          dueDateEpochMillis = it.dueDateEpochMillis,
          scheduledAmountPaise = it.scheduledAmountPaise,
          paidAmountPaise = it.paidAmountPaise,
          status = it.status,
        )
      }
      val allocationPlan = PaymentAllocationCalculator.computeAllocationPlan(
        amountPaise = amountPaise,
        schedules = scheduleSnapshots,
        paymentType = paymentType,
      )
      val sliceByScheduleId = allocationPlan.slices.associateBy { it.scheduleId }
      val modifiedSchedules = schedules.mapNotNull { inst ->
        val slice = sliceByScheduleId[inst.id] ?: return@mapNotNull null
        inst.copy(
          paidAmountPaise = slice.paidAfterPaise,
          status = slice.statusAfter,
          paidDateDdMmYyyy = collectionDateDdMmYyyy,
          lastPaymentId = paymentId,
        )
      }

      val allocatedToInstallments = allocationPlan.totalAllocatedPaise
      val creditedToAdvance = allocationPlan.creditedToAdvancePaise

      val newPaidTotal = loan.paidAmountPaise + allocatedToInstallments
      val newOutstanding = (loan.totalPayablePaise - newPaidTotal).coerceAtLeast(0L)
      val newAdvanceBalance = loan.advanceBalancePaise + creditedToAdvance
      val newLoanStatus = if (newOutstanding == 0L && paymentType != PaymentType.ADVANCE_COLLECTION) {
        LoanStatus.COMPLETED
      } else {
        loan.status
      }

      val updatedLoan = loan.copy(
        paidAmountPaise = newPaidTotal,
        outstandingPaise = newOutstanding,
        advanceBalancePaise = newAdvanceBalance,
        status = newLoanStatus,
        closedDateDdMmYyyy = if (newLoanStatus == LoanStatus.COMPLETED) collectionDateDdMmYyyy else loan.closedDateDdMmYyyy,
      )

      val payment = PaymentEntity(
        id = paymentId,
        orgOwnerUid = ownerUid,
        branchManagerUidsCsv = loan.branchManagerUidsCsv,
        assignedAgentId = loan.assignedAgentId,
        transactionId = transactionId,
        receiptNumber = receiptNumber,
        idempotencyKey = effectiveIdempotencyKey,
        loanId = loan.id,
        loanCode = loan.loanCode,
        customerId = loan.customerId,
        customerCode = loan.customerCode,
        customerName = loan.customerName,
        branchId = loan.branchId,
        branchName = loan.branchName,
        collectedByEmployeeId = actor.id,
        collectedByName = actor.fullName,
        amountPaise = amountPaise,
        allocatedToInstallmentsPaise = allocatedToInstallments,
        creditedToAdvancePaise = creditedToAdvance,
        allocationsSummary = allocationPlan.summaryText,
        paymentMode = paymentMode,
        paymentType = paymentType,
        referenceNumber = cleanRef,
        remarks = remarks.trim(),
        collectionDateDdMmYyyy = collectionDateDdMmYyyy,
        collectedAtMillis = now,
        syncStatus = SyncStatus.CONFIRMED_LOCALLY_PENDING_SYNC,
        serverConfirmationId = null,
        serverConfirmedAtMillis = null,
        lastSyncError = null,
        recordStatus = PaymentRecordStatus.ACTIVE,
      )

      val advanceTx = if (creditedToAdvance > 0L) {
        AdvanceTransactionEntity(
          id = UUID.randomUUID().toString(),
          orgOwnerUid = ownerUid,
          branchManagerUidsCsv = loan.branchManagerUidsCsv,
          assignedAgentId = loan.assignedAgentId,
          loanId = loan.id,
          loanCode = loan.loanCode,
          customerId = loan.customerId,
          branchId = loan.branchId,
          entryType = if (paymentType == PaymentType.ADVANCE_COLLECTION) "CREDIT_ADVANCE" else "CREDIT_OVERPAYMENT",
          amountPaise = creditedToAdvance,
          balanceAfterPaise = newAdvanceBalance,
          relatedPaymentId = paymentId,
          note = if (paymentType == PaymentType.ADVANCE_COLLECTION) {
            "Advance deposit via $receiptNumber ($transactionId)"
          } else {
            "Excess collection credited to advance via $receiptNumber ($transactionId)"
          },
          dateDdMmYyyy = collectionDateDdMmYyyy,
          createdByEmployeeId = actor.id,
          createdByName = actor.fullName,
          createdAtMillis = now,
        )
      } else {
        null
      }

      val syncPayload = JSONObject().apply {
        put("paymentId", payment.id)
        put("transactionId", payment.transactionId)
        put("receiptNumber", payment.receiptNumber)
        put("idempotencyKey", payment.idempotencyKey)
        put("loanId", payment.loanId)
        put("loanCode", payment.loanCode)
        put("customerCode", payment.customerCode)
        put("amountPaise", payment.amountPaise)
        put("allocatedToInstallmentsPaise", payment.allocatedToInstallmentsPaise)
        put("creditedToAdvancePaise", payment.creditedToAdvancePaise)
        put("paymentMode", payment.paymentMode.name)
        put("paymentType", payment.paymentType.name)
        put("referenceNumber", payment.referenceNumber)
        put("collectionDateDdMmYyyy", payment.collectionDateDdMmYyyy)
        put("collectedByEmployeeId", payment.collectedByEmployeeId)
        put("collectedAtMillis", payment.collectedAtMillis)
      }.toString()

      val syncItem = SyncQueueEntity(
        id = UUID.randomUUID().toString(),
        paymentId = payment.id,
        transactionId = payment.transactionId,
        receiptNumber = payment.receiptNumber,
        idempotencyKey = payment.idempotencyKey,
        loanCode = loan.loanCode,
        customerName = loan.customerName,
        amountPaise = payment.amountPaise,
        branchId = loan.branchId,
        collectedByEmployeeId = actor.id,
        payloadJson = syncPayload,
        syncStatus = SyncStatus.CONFIRMED_LOCALLY_PENDING_SYNC,
        attemptCount = 0,
        createdAtMillis = now,
        lastAttemptAtMillis = null,
        confirmedAtMillis = null,
        serverConfirmationId = null,
        errorMessage = null,
      )

      val audit = AuditLogEntity(
        id = UUID.randomUUID().toString(),
        orgOwnerUid = ownerUid,
        branchManagerUidsCsv = loan.branchManagerUidsCsv,
        actorEmployeeId = actor.id,
        actorName = actor.fullName,
        actorRole = actor.role,
        branchId = loan.branchId,
        category = "PAYMENT",
        action = if (paymentType == PaymentType.ADVANCE_COLLECTION) "ADVANCE_COLLECTED" else "PAYMENT_COLLECTED",
        targetType = "PAYMENT",
        targetId = receiptNumber,
        summary = "Saved receipt $receiptNumber (Txn: $transactionId) of ${IndianFinanceFormatter.formatPaise(amountPaise)} (${paymentMode.displayTitle}) for ${loan.customerName} (${loan.loanCode})",
        timestampDdMmYyyyHhMm = IndianFinanceFormatter.formatDateTimeSeconds(now),
        createdAtMillis = now,
      )

      // 1. Atomic local Room commit (offline-safe durability: CONFIRMED_LOCALLY_PENDING_SYNC)
      dao.commitPaymentCollection(
        payment = payment,
        updatedLoan = updatedLoan,
        updatedSchedules = modifiedSchedules,
        advanceTx = advanceTx,
        syncItem = syncItem,
        auditLog = audit,
      )

      // 2. If online & immediate sync requested, push atomic WriteBatch to Cloud Firestore
      if (syncImmediately && firestore != null) {
        val synced = syncEngine.syncSinglePayment(
          paymentId = payment.id,
          actor = actor,
          markFailedOnError = false,
        )
        synced.getOrElse { payment }
      } else {
        payment
      }
    }
  }

  // --- 9b. Non-Deletable Payment Reversal & Correction Workflows ---
  private data class InstallmentReplayEvent(
    val timestampMillis: Long,
    val dateDdMmYyyy: String,
    val paymentId: String?,
    val allocatedPaise: Long,
  )

  private fun replayInstallmentSchedules(
    schedules: List<InstallmentScheduleEntity>,
    activePaymentsChronological: List<PaymentEntity>,
    advanceTxsChronological: List<AdvanceTransactionEntity>,
  ): List<InstallmentScheduleEntity> {
    val events = buildList {
      for (p in activePaymentsChronological) {
        if (p.allocatedToInstallmentsPaise > 0L && p.paymentType == PaymentType.REGULAR_INSTALLMENT) {
          add(
            InstallmentReplayEvent(
              timestampMillis = p.collectedAtMillis,
              dateDdMmYyyy = p.collectionDateDdMmYyyy,
              paymentId = p.id,
              allocatedPaise = p.allocatedToInstallmentsPaise,
            ),
          )
        }
      }
      for (tx in advanceTxsChronological) {
        if (tx.entryType == "APPLIED_INSTALLMENTS" && tx.amountPaise > 0L) {
          add(
            InstallmentReplayEvent(
              timestampMillis = tx.createdAtMillis,
              dateDdMmYyyy = tx.dateDdMmYyyy,
              paymentId = null,
              allocatedPaise = tx.amountPaise,
            ),
          )
        }
      }
    }.sortedBy { it.timestampMillis }

    val working = schedules
      .sortedBy { it.installmentNumber }
      .map {
        if (it.status == InstallmentStatus.WAIVED_PRECLOSURE) {
          it
        } else {
          it.copy(
            paidAmountPaise = 0L,
            status = InstallmentStatus.UPCOMING,
            paidDateDdMmYyyy = null,
            lastPaymentId = null,
          )
        }
      }
      .toMutableList()

    for (ev in events) {
      var remaining = ev.allocatedPaise
      for (idx in working.indices) {
        if (remaining <= 0L) break
        val cur = working[idx]
        if (cur.status == InstallmentStatus.PAID || cur.status == InstallmentStatus.WAIVED_PRECLOSURE) continue
        val unpaid = (cur.scheduledAmountPaise - cur.paidAmountPaise).coerceAtLeast(0L)
        if (unpaid <= 0L) continue
        val applied = minOf(remaining, unpaid)
        val newPaid = cur.paidAmountPaise + applied
        working[idx] = cur.copy(
          paidAmountPaise = newPaid,
          status = if (newPaid >= cur.scheduledAmountPaise) InstallmentStatus.PAID else InstallmentStatus.PARTIAL,
          paidDateDdMmYyyy = ev.dateDdMmYyyy,
          lastPaymentId = ev.paymentId ?: cur.lastPaymentId,
        )
        remaining -= applied
      }
    }
    return working
  }

  private fun assertCanReverseOrCorrect(actor: EmployeeEntity, payment: PaymentEntity, loan: LoanEntity) {
    val authorized = actor.role == UserRole.OWNER ||
      (actor.role == UserRole.BRANCH_MANAGER && actor.branchId == payment.branchId && actor.canCollectPayment)
    if (!authorized) {
      throw SecurityException("Only the Owner or an authorized Branch Manager for this branch can reverse or correct payment receipts.")
    }
    require(payment.recordStatus == PaymentRecordStatus.ACTIVE) {
      "Receipt ${payment.receiptNumber} is already ${payment.recordStatus.displayTitle} and cannot be modified again."
    }
    require(payment.paymentType != PaymentType.PRE_CLOSURE_SETTLEMENT) {
      "Pre-closure settlement receipts cannot be reversed."
    }
    require(loan.status != LoanStatus.PRE_CLOSED && loan.status != LoanStatus.REJECTED) {
      "Payments on ${loan.status.displayTitle} accounts cannot be reversed."
    }
  }

  /**
   * Non-destructively reverses an active payment receipt (ACTIVE -> REVERSED), records an
   * immutable [PaymentAdjustmentEntity], recomputes installment allocations and loan balances,
   * and reopens COMPLETED loans to ACTIVE if unpaid balance remains. Never deletes the payment.
   */
  suspend fun reverseConfirmedPayment(
    actor: EmployeeEntity,
    paymentId: String,
    reason: String,
  ): Result<PaymentAdjustmentEntity> = withContext(Dispatchers.IO) {
    runCatching {
      val cleanReason = reason.trim()
      require(cleanReason.length in 3..300) {
        "Enter a clear reversal reason (between 3 and 300 characters)."
      }

      var original = dao.getPaymentById(paymentId)
        ?: throw IllegalArgumentException("Payment receipt not found.")
      val loan = resolveLoan(original.loanId)
        ?: throw IllegalArgumentException("Associated loan account not found.")

      assertCanReverseOrCorrect(actor, original, loan)

      require(original.creditedToAdvancePaise <= loan.advanceBalancePaise) {
        "Cannot reverse ${original.receiptNumber} because part of its advance credit has already been applied to installments."
      }

      val fs = firestore
      if (fs != null && original.syncStatus != SyncStatus.SERVER_CONFIRMED) {
        if (original.collectedByEmployeeId == actor.id) {
          original = syncEngine.syncSinglePayment(original.id, actor, markFailedOnError = false)
            .getOrElse {
              throw IllegalStateException(
                "Original receipt ${original.receiptNumber} is pending server sync. Connect to the internet and sync pending receipts before reversing.",
              )
            }
        } else {
          throw IllegalStateException(
            "Original receipt ${original.receiptNumber} is still pending server sync from ${original.collectedByName}. Sync pending receipts first.",
          )
        }
      }

      val now = System.currentTimeMillis()
      val today = IndianFinanceFormatter.todayDdMmYyyy(now)
      val ownerUid = loan.orgOwnerUid.ifBlank { actor.orgOwnerUid.ifBlank { actor.id } }

      val allSchedules = dao.getScheduleForLoan(loan.id)
      val remainingActivePayments = dao.getPaymentsForLoan(loan.id).filter {
        it.id != original.id && it.recordStatus == PaymentRecordStatus.ACTIVE
      }
      val advanceHistory = dao.getAdvanceTransactionsForLoan(loan.id)

      val replayedSchedules = replayInstallmentSchedules(
        schedules = allSchedules,
        activePaymentsChronological = remainingActivePayments,
        advanceTxsChronological = advanceHistory,
      )
      val scheduleById = allSchedules.associateBy { it.id }
      val changedSchedules = replayedSchedules.filter { sched -> scheduleById[sched.id] != sched }

      val newPaidTotal = replayedSchedules.sumOf { it.paidAmountPaise }
      val newOutstanding = (loan.totalPayablePaise - newPaidTotal).coerceAtLeast(0L)
      val newAdvanceBalance = (loan.advanceBalancePaise - original.creditedToAdvancePaise).coerceAtLeast(0L)
      val newLoanStatus = when {
        newOutstanding == 0L -> LoanStatus.COMPLETED
        loan.status == LoanStatus.COMPLETED -> LoanStatus.ACTIVE
        else -> loan.status
      }
      val newClosedDate = if (newLoanStatus == LoanStatus.COMPLETED) {
        loan.closedDateDdMmYyyy.ifBlank { today }
      } else {
        ""
      }

      val updatedLoan = loan.copy(
        paidAmountPaise = newPaidTotal,
        outstandingPaise = newOutstanding,
        advanceBalancePaise = newAdvanceBalance,
        status = newLoanStatus,
        closedDateDdMmYyyy = newClosedDate,
      )

      val adjustmentId = UUID.randomUUID().toString()
      val adjustmentCode = generateUniqueAdjustmentCode("REV", today)

      val updatedOriginalPayment = original.copy(
        recordStatus = PaymentRecordStatus.REVERSED,
        reversedByEmployeeId = actor.id,
        reversedByName = actor.fullName,
        reversedDateDdMmYyyy = today,
        reversalReason = cleanReason,
        adjustmentId = adjustmentId,
        replacementPaymentId = null,
      )

      val adjustment = PaymentAdjustmentEntity(
        id = adjustmentId,
        orgOwnerUid = ownerUid,
        branchManagerUidsCsv = loan.branchManagerUidsCsv,
        assignedAgentId = loan.assignedAgentId,
        adjustmentCode = adjustmentCode,
        adjustmentType = PaymentAdjustmentType.REVERSAL,
        loanId = loan.id,
        loanCode = loan.loanCode,
        customerId = loan.customerId,
        customerCode = loan.customerCode,
        customerName = loan.customerName,
        branchId = loan.branchId,
        originalPaymentId = original.id,
        originalTransactionId = original.transactionId,
        originalReceiptNumber = original.receiptNumber,
        originalAmountPaise = original.amountPaise,
        replacementPaymentId = null,
        replacementTransactionId = null,
        replacementReceiptNumber = null,
        replacementAmountPaise = 0L,
        reason = cleanReason,
        performedByEmployeeId = actor.id,
        performedByName = actor.fullName,
        performedDateDdMmYyyy = today,
        createdAtMillis = now,
      )

      val reversalAdvanceTx = if (original.creditedToAdvancePaise > 0L) {
        AdvanceTransactionEntity(
          id = UUID.randomUUID().toString(),
          orgOwnerUid = ownerUid,
          branchManagerUidsCsv = loan.branchManagerUidsCsv,
          assignedAgentId = loan.assignedAgentId,
          loanId = loan.id,
          loanCode = loan.loanCode,
          customerId = loan.customerId,
          branchId = loan.branchId,
          entryType = "REVERSAL_DEBIT",
          amountPaise = original.creditedToAdvancePaise,
          balanceAfterPaise = newAdvanceBalance,
          relatedPaymentId = original.id,
          note = "Reversed advance credit from ${original.receiptNumber} ($adjustmentCode): $cleanReason".take(300),
          dateDdMmYyyy = today,
          createdByEmployeeId = actor.id,
          createdByName = actor.fullName,
          createdAtMillis = now,
        )
      } else {
        null
      }

      val audit = AuditLogEntity(
        id = UUID.randomUUID().toString(),
        orgOwnerUid = ownerUid,
        branchManagerUidsCsv = loan.branchManagerUidsCsv,
        actorEmployeeId = actor.id,
        actorName = actor.fullName,
        actorRole = actor.role,
        branchId = loan.branchId,
        category = "PAYMENT",
        action = "PAYMENT_REVERSED",
        targetType = "PAYMENT",
        targetId = original.receiptNumber,
        summary = "Reversed receipt ${original.receiptNumber} (Txn: ${original.transactionId}, ${IndianFinanceFormatter.formatPaise(original.amountPaise)}) via $adjustmentCode: $cleanReason",
        timestampDdMmYyyyHhMm = IndianFinanceFormatter.formatDateTimeSeconds(now),
        createdAtMillis = now,
      )

      if (fs != null) {
        try {
          // Batch 1: Atomic Original Payment REVERSED + PaymentAdjustment + Loan Update (~720 CEL)
          val batch = fs.batch()
          batch.update(
            fs.collection("payments").document(original.id),
            mapOf(
              "recordStatus" to PaymentRecordStatus.REVERSED.name,
              "reversedByUid" to actor.id,
              "reversedByName" to actor.fullName,
              "reversedDateDdMmYyyy" to today,
              "reversalReason" to cleanReason,
              "adjustmentId" to adjustment.id,
              "replacementPaymentId" to "",
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
          batch.set(
            fs.collection("payment_adjustments").document(adjustment.id),
            FirestorePayloads.paymentAdjustmentCreate(adjustment),
          )
          batch.update(
            fs.collection("loans").document(updatedLoan.id),
            mapOf(
              "paidAmountPaise" to updatedLoan.paidAmountPaise,
              "advanceBalancePaise" to updatedLoan.advanceBalancePaise,
              "outstandingPaise" to updatedLoan.outstandingPaise,
              "status" to updatedLoan.status.name,
              "closedDateDdMmYyyy" to updatedLoan.closedDateDdMmYyyy,
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
          batch.commit().await()

          // Batch 2: Optional Reversal Advance Debit + Immutable Audit Log (~300 CEL)
          val auditBatch = fs.batch()
          if (reversalAdvanceTx != null) {
            auditBatch.set(
              fs.collection("advance_transactions").document(reversalAdvanceTx.id),
              FirestorePayloads.advanceTxCreate(reversalAdvanceTx),
            )
          }
          auditBatch.set(fs.collection("audit_logs").document(audit.id), FirestorePayloads.auditLogCreate(audit))
          auditBatch.commit().await()

          // Schedule updates in chunks of 2 (< 850 CEL each)
          for (chunk in changedSchedules.chunked(2)) {
            val extraBatch = fs.batch()
            for (sched in chunk) {
              extraBatch.update(
                fs.collection("installment_schedules").document(sched.id),
                mapOf(
                  "paidAmountPaise" to sched.paidAmountPaise,
                  "status" to sched.status.name,
                  "paidDateDdMmYyyy" to (sched.paidDateDdMmYyyy ?: ""),
                  "lastPaymentId" to (sched.lastPaymentId ?: ""),
                  "updatedAt" to FieldValue.serverTimestamp(),
                ),
              )
            }
            extraBatch.commit().await()
          }
        } catch (e: Exception) {
          handleFirestoreError(e, OperationType.WRITE, "payment_adjustments/${adjustment.id}")
          throw e
        }
      }

      dao.commitPaymentReversal(
        updatedOriginalPayment = updatedOriginalPayment,
        adjustment = adjustment,
        updatedLoan = updatedLoan,
        updatedSchedules = changedSchedules,
        reversalAdvanceTx = reversalAdvanceTx,
        auditLog = audit,
      )
      adjustment
    }
  }

  /**
   * Non-destructively corrects an active payment receipt: marks the original receipt as CORRECTED,
   * creates a linked replacement ACTIVE receipt with a new unique transactionId & receiptNumber,
   * records an immutable [PaymentAdjustmentEntity], and deterministically recomputes installment
   * allocations and loan balances. Never deletes the original payment.
   */
  suspend fun correctConfirmedPayment(
    actor: EmployeeEntity,
    originalPaymentId: String,
    correctedAmountPaise: Long,
    correctedPaymentMode: PaymentMode,
    correctedReferenceNumber: String,
    correctedCollectionDateDdMmYyyy: String,
    correctionReason: String,
    idempotencyKey: String = "",
  ): Result<PaymentEntity> = withContext(Dispatchers.IO) {
    runCatching {
      val cleanReason = correctionReason.trim()
      require(cleanReason.length in 3..300) {
        "Enter a clear correction reason (between 3 and 300 characters)."
      }
      require(correctedAmountPaise > 0L) {
        "Corrected payment amount must be greater than ₹0."
      }
      require(IndianFinanceFormatter.parseDdMmYyyyToMillis(correctedCollectionDateDdMmYyyy) != null) {
        "Corrected collection date must be a valid DD-MM-YYYY date."
      }
      val cleanRef = correctedReferenceNumber.trim()
      if (correctedPaymentMode != PaymentMode.CASH && correctedPaymentMode != PaymentMode.ADVANCE_ADJUSTMENT) {
        require(cleanRef.isNotBlank()) {
          "UTR / Cheque / Reference number is required for ${correctedPaymentMode.displayTitle}."
        }
      }

      val effectiveIdempotencyKey = idempotencyKey.trim().takeIf { it.length >= 6 }
        ?: "IDEM-CORR-${UUID.randomUUID()}"
      val existingByIdempotency = dao.getPaymentByIdempotencyKey(effectiveIdempotencyKey)
      if (existingByIdempotency != null) {
        throw IllegalArgumentException(
          "Duplicate correction submission prevented: Replacement transaction ${existingByIdempotency.transactionId} is already saved.",
        )
      }

      var original = dao.getPaymentById(originalPaymentId)
        ?: throw IllegalArgumentException("Original payment receipt not found.")
      val loan = resolveLoan(original.loanId)
        ?: throw IllegalArgumentException("Associated loan account not found.")

      assertCanReverseOrCorrect(actor, original, loan)

      require(original.creditedToAdvancePaise <= loan.advanceBalancePaise) {
        "Cannot correct ${original.receiptNumber} because part of its advance credit has already been applied to installments."
      }

      if (correctedPaymentMode != PaymentMode.CASH && correctedPaymentMode != PaymentMode.ADVANCE_ADJUSTMENT && cleanRef.isNotEmpty()) {
        val duplicateRef = dao.getPaymentsForLoan(loan.id).firstOrNull {
          it.id != original.id &&
            it.recordStatus == PaymentRecordStatus.ACTIVE &&
            it.paymentMode == correctedPaymentMode &&
            it.referenceNumber.equals(cleanRef, ignoreCase = true)
        }
        if (duplicateRef != null) {
          throw IllegalArgumentException(
            "Duplicate ${correctedPaymentMode.displayTitle} reference '$cleanRef' rejected: already recorded on active receipt ${duplicateRef.receiptNumber}.",
          )
        }
      }

      val fs = firestore
      if (fs != null && original.syncStatus != SyncStatus.SERVER_CONFIRMED) {
        if (original.collectedByEmployeeId == actor.id) {
          original = syncEngine.syncSinglePayment(original.id, actor, markFailedOnError = false)
            .getOrElse {
              throw IllegalStateException(
                "Original receipt ${original.receiptNumber} is pending server sync. Connect to the internet and sync pending receipts before correcting.",
              )
            }
        } else {
          throw IllegalStateException(
            "Original receipt ${original.receiptNumber} is still pending server sync from ${original.collectedByName}. Sync pending receipts first.",
          )
        }
      }

      val now = System.currentTimeMillis()
      val today = IndianFinanceFormatter.todayDdMmYyyy(now)
      val ownerUid = loan.orgOwnerUid.ifBlank { actor.orgOwnerUid.ifBlank { actor.id } }

      val allSchedules = dao.getScheduleForLoan(loan.id)
      val otherActivePayments = dao.getPaymentsForLoan(loan.id).filter {
        it.id != original.id && it.recordStatus == PaymentRecordStatus.ACTIVE
      }
      val advanceHistory = dao.getAdvanceTransactionsForLoan(loan.id)

      // 1. Replay all OTHER active payments + advance applications first
      val baseSchedules = replayInstallmentSchedules(
        schedules = allSchedules,
        activePaymentsChronological = otherActivePayments,
        advanceTxsChronological = advanceHistory,
      )

      // 2. Compute allocation of the corrected replacement payment on top of baseSchedules
      val baseSnapshots = baseSchedules.map {
        PaymentAllocationCalculator.ScheduleSnapshot(
          id = it.id,
          installmentNumber = it.installmentNumber,
          dueDateDdMmYyyy = it.dueDateDdMmYyyy,
          dueDateEpochMillis = it.dueDateEpochMillis,
          scheduledAmountPaise = it.scheduledAmountPaise,
          paidAmountPaise = it.paidAmountPaise,
          status = it.status,
        )
      }
      val replacementPlan = PaymentAllocationCalculator.computeAllocationPlan(
        amountPaise = correctedAmountPaise,
        schedules = baseSnapshots,
        paymentType = original.paymentType,
      )

      val replacementPaymentId = UUID.randomUUID().toString()
      val replacementTransactionId = generateUniqueTransactionId(correctedCollectionDateDdMmYyyy)
      val replacementReceiptNumber = generateUniqueReceiptNumber(correctedCollectionDateDdMmYyyy)
      val adjustmentId = UUID.randomUUID().toString()
      val adjustmentCode = generateUniqueAdjustmentCode("COR", today)

      val sliceMap = replacementPlan.slices.associateBy { it.scheduleId }
      val finalSchedules = baseSchedules.map { inst ->
        val slice = sliceMap[inst.id]
        if (slice != null) {
          inst.copy(
            paidAmountPaise = slice.paidAfterPaise,
            status = slice.statusAfter,
            paidDateDdMmYyyy = correctedCollectionDateDdMmYyyy,
            lastPaymentId = replacementPaymentId,
          )
        } else {
          inst
        }
      }
      val originalScheduleMap = allSchedules.associateBy { it.id }
      val changedSchedules = finalSchedules.filter { originalScheduleMap[it.id] != it }

      val finalPaidTotal = finalSchedules.sumOf { it.paidAmountPaise }
      val finalOutstanding = (loan.totalPayablePaise - finalPaidTotal).coerceAtLeast(0L)
      val advanceAfterReversal = (loan.advanceBalancePaise - original.creditedToAdvancePaise).coerceAtLeast(0L)
      val finalAdvanceBalance = advanceAfterReversal + replacementPlan.creditedToAdvancePaise
      val finalLoanStatus = when {
        finalOutstanding == 0L && original.paymentType != PaymentType.ADVANCE_COLLECTION -> LoanStatus.COMPLETED
        loan.status == LoanStatus.COMPLETED -> LoanStatus.ACTIVE
        else -> loan.status
      }
      val finalClosedDate = if (finalLoanStatus == LoanStatus.COMPLETED) {
        correctedCollectionDateDdMmYyyy
      } else {
        ""
      }

      val updatedLoan = loan.copy(
        paidAmountPaise = finalPaidTotal,
        outstandingPaise = finalOutstanding,
        advanceBalancePaise = finalAdvanceBalance,
        status = finalLoanStatus,
        closedDateDdMmYyyy = finalClosedDate,
      )

      val updatedOriginalPayment = original.copy(
        recordStatus = PaymentRecordStatus.CORRECTED,
        reversedByEmployeeId = actor.id,
        reversedByName = actor.fullName,
        reversedDateDdMmYyyy = today,
        reversalReason = cleanReason,
        adjustmentId = adjustmentId,
        replacementPaymentId = replacementPaymentId,
      )

      val replacementServerAckId = "FS-ACK-$replacementReceiptNumber"
      val isCloudCommitted = fs != null

      val replacementPayment = PaymentEntity(
        id = replacementPaymentId,
        orgOwnerUid = ownerUid,
        branchManagerUidsCsv = loan.branchManagerUidsCsv,
        assignedAgentId = loan.assignedAgentId,
        transactionId = replacementTransactionId,
        receiptNumber = replacementReceiptNumber,
        idempotencyKey = effectiveIdempotencyKey,
        loanId = loan.id,
        loanCode = loan.loanCode,
        customerId = loan.customerId,
        customerCode = loan.customerCode,
        customerName = loan.customerName,
        branchId = loan.branchId,
        branchName = loan.branchName,
        collectedByEmployeeId = actor.id,
        collectedByName = actor.fullName,
        amountPaise = correctedAmountPaise,
        allocatedToInstallmentsPaise = replacementPlan.totalAllocatedPaise,
        creditedToAdvancePaise = replacementPlan.creditedToAdvancePaise,
        allocationsSummary = replacementPlan.summaryText,
        paymentMode = correctedPaymentMode,
        paymentType = original.paymentType,
        referenceNumber = cleanRef,
        remarks = "Correction of ${original.receiptNumber}: $cleanReason".take(300),
        collectionDateDdMmYyyy = correctedCollectionDateDdMmYyyy,
        collectedAtMillis = now,
        syncStatus = if (isCloudCommitted) SyncStatus.SERVER_CONFIRMED else SyncStatus.CONFIRMED_LOCALLY_PENDING_SYNC,
        serverConfirmationId = if (isCloudCommitted) replacementServerAckId else null,
        serverConfirmedAtMillis = if (isCloudCommitted) now else null,
        lastSyncError = null,
        recordStatus = PaymentRecordStatus.ACTIVE,
        correctsPaymentId = original.id,
      )

      val adjustment = PaymentAdjustmentEntity(
        id = adjustmentId,
        orgOwnerUid = ownerUid,
        branchManagerUidsCsv = loan.branchManagerUidsCsv,
        assignedAgentId = loan.assignedAgentId,
        adjustmentCode = adjustmentCode,
        adjustmentType = PaymentAdjustmentType.CORRECTION,
        loanId = loan.id,
        loanCode = loan.loanCode,
        customerId = loan.customerId,
        customerCode = loan.customerCode,
        customerName = loan.customerName,
        branchId = loan.branchId,
        originalPaymentId = original.id,
        originalTransactionId = original.transactionId,
        originalReceiptNumber = original.receiptNumber,
        originalAmountPaise = original.amountPaise,
        replacementPaymentId = replacementPayment.id,
        replacementTransactionId = replacementPayment.transactionId,
        replacementReceiptNumber = replacementPayment.receiptNumber,
        replacementAmountPaise = replacementPayment.amountPaise,
        reason = cleanReason,
        performedByEmployeeId = actor.id,
        performedByName = actor.fullName,
        performedDateDdMmYyyy = today,
        createdAtMillis = now,
      )

      val reversalAdvanceTx = if (original.creditedToAdvancePaise > 0L) {
        AdvanceTransactionEntity(
          id = UUID.randomUUID().toString(),
          orgOwnerUid = ownerUid,
          branchManagerUidsCsv = loan.branchManagerUidsCsv,
          assignedAgentId = loan.assignedAgentId,
          loanId = loan.id,
          loanCode = loan.loanCode,
          customerId = loan.customerId,
          branchId = loan.branchId,
          entryType = "REVERSAL_DEBIT",
          amountPaise = original.creditedToAdvancePaise,
          balanceAfterPaise = advanceAfterReversal,
          relatedPaymentId = original.id,
          note = "Reversed prior advance credit from ${original.receiptNumber} ($adjustmentCode)".take(300),
          dateDdMmYyyy = today,
          createdByEmployeeId = actor.id,
          createdByName = actor.fullName,
          createdAtMillis = now,
        )
      } else {
        null
      }

      val replacementAdvanceTx = if (replacementPlan.creditedToAdvancePaise > 0L) {
        AdvanceTransactionEntity(
          id = UUID.randomUUID().toString(),
          orgOwnerUid = ownerUid,
          branchManagerUidsCsv = loan.branchManagerUidsCsv,
          assignedAgentId = loan.assignedAgentId,
          loanId = loan.id,
          loanCode = loan.loanCode,
          customerId = loan.customerId,
          branchId = loan.branchId,
          entryType = if (original.paymentType == PaymentType.ADVANCE_COLLECTION) "CREDIT_ADVANCE" else "CREDIT_OVERPAYMENT",
          amountPaise = replacementPlan.creditedToAdvancePaise,
          balanceAfterPaise = finalAdvanceBalance,
          relatedPaymentId = replacementPayment.id,
          note = "Corrected advance credit via ${replacementPayment.receiptNumber} ($adjustmentCode)".take(300),
          dateDdMmYyyy = correctedCollectionDateDdMmYyyy,
          createdByEmployeeId = actor.id,
          createdByName = actor.fullName,
          createdAtMillis = now + 1L,
        )
      } else {
        null
      }

      val syncPayload = JSONObject().apply {
        put("paymentId", replacementPayment.id)
        put("transactionId", replacementPayment.transactionId)
        put("receiptNumber", replacementPayment.receiptNumber)
        put("idempotencyKey", replacementPayment.idempotencyKey)
        put("correctsPaymentId", original.id)
        put("loanId", loan.id)
        put("loanCode", loan.loanCode)
        put("amountPaise", replacementPayment.amountPaise)
        put("paymentMode", replacementPayment.paymentMode.name)
        put("collectionDateDdMmYyyy", correctedCollectionDateDdMmYyyy)
        put("collectedByEmployeeId", actor.id)
      }.toString()

      val replacementSyncItem = SyncQueueEntity(
        id = UUID.randomUUID().toString(),
        paymentId = replacementPayment.id,
        transactionId = replacementPayment.transactionId,
        receiptNumber = replacementPayment.receiptNumber,
        idempotencyKey = replacementPayment.idempotencyKey,
        loanCode = loan.loanCode,
        customerName = loan.customerName,
        amountPaise = replacementPayment.amountPaise,
        branchId = loan.branchId,
        collectedByEmployeeId = actor.id,
        payloadJson = syncPayload,
        syncStatus = if (isCloudCommitted) SyncStatus.SERVER_CONFIRMED else SyncStatus.CONFIRMED_LOCALLY_PENDING_SYNC,
        attemptCount = if (isCloudCommitted) 1 else 0,
        createdAtMillis = now,
        lastAttemptAtMillis = if (isCloudCommitted) now else null,
        confirmedAtMillis = if (isCloudCommitted) now else null,
        serverConfirmationId = if (isCloudCommitted) replacementServerAckId else null,
        errorMessage = null,
      )

      val audit = AuditLogEntity(
        id = UUID.randomUUID().toString(),
        orgOwnerUid = ownerUid,
        branchManagerUidsCsv = loan.branchManagerUidsCsv,
        actorEmployeeId = actor.id,
        actorName = actor.fullName,
        actorRole = actor.role,
        branchId = loan.branchId,
        category = "PAYMENT",
        action = "PAYMENT_CORRECTED",
        targetType = "PAYMENT",
        targetId = replacementPayment.receiptNumber,
        summary = "Corrected receipt ${original.receiptNumber} (${IndianFinanceFormatter.formatPaise(original.amountPaise)}) -> ${replacementPayment.receiptNumber} (${IndianFinanceFormatter.formatPaise(replacementPayment.amountPaise)}) via $adjustmentCode: $cleanReason",
        timestampDdMmYyyyHhMm = IndianFinanceFormatter.formatDateTimeSeconds(now),
        createdAtMillis = now,
      )

      if (fs != null) {
        try {
          // Batch 1: Atomic Original Payment CORRECTED + PaymentAdjustment + Replacement Payment (~640 CEL)
          val batch = fs.batch()
          batch.update(
            fs.collection("payments").document(original.id),
            mapOf(
              "recordStatus" to PaymentRecordStatus.CORRECTED.name,
              "reversedByUid" to actor.id,
              "reversedByName" to actor.fullName,
              "reversedDateDdMmYyyy" to today,
              "reversalReason" to cleanReason,
              "adjustmentId" to adjustment.id,
              "replacementPaymentId" to replacementPayment.id,
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
          batch.set(
            fs.collection("payment_adjustments").document(adjustment.id),
            FirestorePayloads.paymentAdjustmentCreate(adjustment),
          )
          batch.set(
            fs.collection("payments").document(replacementPayment.id),
            FirestorePayloads.paymentCreate(replacementPayment, replacementServerAckId),
          )
          batch.commit().await()

          // Batch 2: Loan Balance Update + Immutable Audit Log (~520 CEL)
          val loanBatch = fs.batch()
          loanBatch.update(
            fs.collection("loans").document(updatedLoan.id),
            mapOf(
              "paidAmountPaise" to updatedLoan.paidAmountPaise,
              "advanceBalancePaise" to updatedLoan.advanceBalancePaise,
              "outstandingPaise" to updatedLoan.outstandingPaise,
              "status" to updatedLoan.status.name,
              "closedDateDdMmYyyy" to updatedLoan.closedDateDdMmYyyy,
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
          loanBatch.set(fs.collection("audit_logs").document(audit.id), FirestorePayloads.auditLogCreate(audit))
          loanBatch.commit().await()

          // Batch 3: Optional Advance Ledger Adjustments (~320 CEL)
          if (reversalAdvanceTx != null || replacementAdvanceTx != null) {
            val advBatch = fs.batch()
            if (reversalAdvanceTx != null) {
              advBatch.set(
                fs.collection("advance_transactions").document(reversalAdvanceTx.id),
                FirestorePayloads.advanceTxCreate(reversalAdvanceTx),
              )
            }
            if (replacementAdvanceTx != null) {
              advBatch.set(
                fs.collection("advance_transactions").document(replacementAdvanceTx.id),
                FirestorePayloads.advanceTxCreate(replacementAdvanceTx),
              )
            }
            advBatch.commit().await()
          }

          // Schedule updates in chunks of 2 (< 850 CEL each)
          for (chunk in changedSchedules.chunked(2)) {
            val extraBatch = fs.batch()
            for (sched in chunk) {
              extraBatch.update(
                fs.collection("installment_schedules").document(sched.id),
                mapOf(
                  "paidAmountPaise" to sched.paidAmountPaise,
                  "status" to sched.status.name,
                  "paidDateDdMmYyyy" to (sched.paidDateDdMmYyyy ?: ""),
                  "lastPaymentId" to (sched.lastPaymentId ?: ""),
                  "updatedAt" to FieldValue.serverTimestamp(),
                ),
              )
            }
            extraBatch.commit().await()
          }
        } catch (e: Exception) {
          handleFirestoreError(e, OperationType.WRITE, "payment_adjustments/${adjustment.id}")
          throw e
        }
      }

      dao.commitPaymentCorrection(
        updatedOriginalPayment = updatedOriginalPayment,
        replacementPayment = replacementPayment,
        adjustment = adjustment,
        updatedLoan = updatedLoan,
        updatedSchedules = changedSchedules,
        reversalAdvanceTx = reversalAdvanceTx,
        replacementAdvanceTx = replacementAdvanceTx,
        replacementSyncItem = replacementSyncItem,
        auditLog = audit,
      )
      replacementPayment
    }
  }

  // --- 10b. Apply Advance Balance to Upcoming Installments ---
  suspend fun applyAdvanceBalanceToInstallments(
    actor: EmployeeEntity,
    loanId: String,
  ): Result<Long> = withContext(Dispatchers.IO) {
    runCatching {
      if (actor.role != UserRole.OWNER && !actor.canManageAdvance) {
        throw SecurityException("You do not have permission to manage advance adjustments.")
      }
      val loan = resolveLoan(loanId) ?: throw IllegalArgumentException("Loan account not found.")
      assertCanAccessLoan(actor, loan)
      require(loan.advanceBalancePaise > 0L) { "No advance balance available on this loan." }
      require(loan.outstandingPaise > 0L) { "This loan has no remaining unpaid installments." }

      val schedules = dao.getScheduleForLoan(loan.id)
      val modifiedSchedules = mutableListOf<InstallmentScheduleEntity>()
      var remainingAdvance = loan.advanceBalancePaise
      var totalApplied = 0L
      val now = System.currentTimeMillis()
      val today = IndianFinanceFormatter.todayDdMmYyyy(now)
      val ownerUid = loan.orgOwnerUid.ifBlank { actor.orgOwnerUid.ifBlank { actor.id } }

      for (inst in schedules) {
        if (remainingAdvance <= 0L) break
        if (inst.status == InstallmentStatus.PAID || inst.status == InstallmentStatus.WAIVED_PRECLOSURE) continue
        val unpaid = (inst.scheduledAmountPaise - inst.paidAmountPaise).coerceAtLeast(0L)
        if (unpaid <= 0L) continue

        val applied = minOf(remainingAdvance, unpaid)
        val newPaid = inst.paidAmountPaise + applied
        modifiedSchedules.add(
          inst.copy(
            paidAmountPaise = newPaid,
            status = if (newPaid >= inst.scheduledAmountPaise) InstallmentStatus.PAID else InstallmentStatus.PARTIAL,
            paidDateDdMmYyyy = today,
          ),
        )
        remainingAdvance -= applied
        totalApplied += applied
      }

      require(totalApplied > 0L) { "No unpaid installments eligible for advance adjustment." }

      val newPaidTotal = loan.paidAmountPaise + totalApplied
      val newOutstanding = (loan.totalPayablePaise - newPaidTotal).coerceAtLeast(0L)
      val newStatus = if (newOutstanding == 0L) LoanStatus.COMPLETED else loan.status
      val updatedLoan = loan.copy(
        paidAmountPaise = newPaidTotal,
        outstandingPaise = newOutstanding,
        advanceBalancePaise = remainingAdvance,
        status = newStatus,
        closedDateDdMmYyyy = if (newStatus == LoanStatus.COMPLETED) today else loan.closedDateDdMmYyyy,
      )

      val advanceTx = AdvanceTransactionEntity(
        id = UUID.randomUUID().toString(),
        orgOwnerUid = ownerUid,
        branchManagerUidsCsv = loan.branchManagerUidsCsv,
        assignedAgentId = loan.assignedAgentId,
        loanId = loan.id,
        loanCode = loan.loanCode,
        customerId = loan.customerId,
        branchId = loan.branchId,
        entryType = "APPLIED_INSTALLMENTS",
        amountPaise = totalApplied,
        balanceAfterPaise = remainingAdvance,
        relatedPaymentId = null,
        note = "Adjusted ${IndianFinanceFormatter.formatPaise(totalApplied)} across ${modifiedSchedules.size} installment(s)",
        dateDdMmYyyy = today,
        createdByEmployeeId = actor.id,
        createdByName = actor.fullName,
        createdAtMillis = now,
      )

      val audit = AuditLogEntity(
        id = UUID.randomUUID().toString(),
        orgOwnerUid = ownerUid,
        branchManagerUidsCsv = loan.branchManagerUidsCsv,
        actorEmployeeId = actor.id,
        actorName = actor.fullName,
        actorRole = actor.role,
        branchId = loan.branchId,
        category = "ADVANCE",
        action = "ADVANCE_APPLIED",
        targetType = "LOAN",
        targetId = loan.loanCode,
        summary = "Applied ${IndianFinanceFormatter.formatPaise(totalApplied)} from Advance Balance to ${modifiedSchedules.size} installment(s) on ${loan.loanCode}",
        timestampDdMmYyyyHhMm = IndianFinanceFormatter.formatDateTimeSeconds(now),
        createdAtMillis = now,
      )

      val fs = firestore
      if (fs != null) {
        try {
          // Batch 1: Loan Balance Update + Advance Ledger + Audit Log (~680 CEL)
          val batch = fs.batch()
          batch.update(
            fs.collection("loans").document(updatedLoan.id),
            mapOf(
              "paidAmountPaise" to updatedLoan.paidAmountPaise,
              "advanceBalancePaise" to updatedLoan.advanceBalancePaise,
              "outstandingPaise" to updatedLoan.outstandingPaise,
              "status" to updatedLoan.status.name,
              "closedDateDdMmYyyy" to updatedLoan.closedDateDdMmYyyy,
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
          batch.set(
            fs.collection("advance_transactions").document(advanceTx.id),
            FirestorePayloads.advanceTxCreate(advanceTx),
          )
          batch.set(fs.collection("audit_logs").document(audit.id), FirestorePayloads.auditLogCreate(audit))
          batch.commit().await()

          // Schedule updates in chunks of 2 (< 850 CEL each)
          for (chunk in modifiedSchedules.chunked(2)) {
            val extraBatch = fs.batch()
            for (sched in chunk) {
              extraBatch.update(
                fs.collection("installment_schedules").document(sched.id),
                mapOf(
                  "paidAmountPaise" to sched.paidAmountPaise,
                  "status" to sched.status.name,
                  "paidDateDdMmYyyy" to (sched.paidDateDdMmYyyy ?: ""),
                  "lastPaymentId" to (sched.lastPaymentId ?: ""),
                  "updatedAt" to FieldValue.serverTimestamp(),
                ),
              )
            }
            extraBatch.commit().await()
          }
        } catch (e: Exception) {
          handleFirestoreError(e, OperationType.WRITE, "loans/${updatedLoan.id}")
          throw e
        }
      }

      dao.commitAdvanceApplication(updatedLoan, modifiedSchedules, advanceTx, audit)
      totalApplied
    }
  }

  // --- 11. Pre-Closure Requests & Settlement ---
  suspend fun submitPreClosureRequest(
    actor: EmployeeEntity,
    loanId: String,
    proposedRebatePaise: Long,
    preClosureChargePaise: Long,
    reason: String,
  ): Result<PreClosureRequestEntity> = withContext(Dispatchers.IO) {
    runCatching {
      if (actor.role != UserRole.OWNER && !actor.canRequestPreClosure) {
        throw SecurityException("You do not have permission to submit pre-closure requests.")
      }
      val loan = resolveLoan(loanId) ?: throw IllegalArgumentException("Loan account not found.")
      assertCanAccessLoan(actor, loan)
      require(loan.status == LoanStatus.ACTIVE) { "Pre-closure can only be requested for Active loans." }
      require(loan.outstandingPaise > 0L) { "Loan has no outstanding balance." }
      require(proposedRebatePaise >= 0L && preClosureChargePaise >= 0L) { "Rebate and charges cannot be negative." }
      require(proposedRebatePaise < loan.outstandingPaise) { "Rebate must be less than remaining outstanding balance." }
      require(reason.isNotBlank()) { "Enter reason / customer remarks for pre-closure." }

      val netPayable = (loan.outstandingPaise - loan.advanceBalancePaise - proposedRebatePaise + preClosureChargePaise)
        .coerceAtLeast(0L)
      val suffix = UUID.randomUUID().toString().replace("-", "").take(4).uppercase(Locale.US)
      val nextSeq = dao.countPreClosureRequests() + 1
      val reqCode = "PCR-%04d-%s".format(Locale.US, nextSeq, suffix)
      val now = System.currentTimeMillis()
      val today = IndianFinanceFormatter.todayDdMmYyyy(now)
      val ownerUid = loan.orgOwnerUid.ifBlank { actor.orgOwnerUid.ifBlank { actor.id } }

      val request = PreClosureRequestEntity(
        id = UUID.randomUUID().toString(),
        orgOwnerUid = ownerUid,
        branchManagerUidsCsv = loan.branchManagerUidsCsv,
        assignedAgentId = loan.assignedAgentId,
        requestCode = reqCode,
        loanId = loan.id,
        loanCode = loan.loanCode,
        customerId = loan.customerId,
        customerCode = loan.customerCode,
        customerName = loan.customerName,
        branchId = loan.branchId,
        outstandingBeforePaise = loan.outstandingPaise,
        proposedRebatePaise = proposedRebatePaise,
        preClosureChargePaise = preClosureChargePaise,
        netSettlementPaise = netPayable,
        status = PreClosureStatus.PENDING,
        reason = reason.trim(),
        requestedByEmployeeId = actor.id,
        requestedByName = actor.fullName,
        requestedDateDdMmYyyy = today,
        reviewedByEmployeeId = null,
        reviewedByName = null,
        reviewedDateDdMmYyyy = null,
        reviewRemarks = null,
        settlementPaymentId = null,
        createdAtMillis = now,
      )

      val audit = AuditLogEntity(
        id = UUID.randomUUID().toString(),
        orgOwnerUid = ownerUid,
        branchManagerUidsCsv = loan.branchManagerUidsCsv,
        actorEmployeeId = actor.id,
        actorName = actor.fullName,
        actorRole = actor.role,
        branchId = loan.branchId,
        category = "PRECLOSURE",
        action = "PRECLOSURE_REQUESTED",
        targetType = "PRECLOSURE",
        targetId = reqCode,
        summary = "Submitted pre-closure $reqCode for ${loan.loanCode}: Net Settlement ${IndianFinanceFormatter.formatPaise(netPayable)}",
        timestampDdMmYyyyHhMm = IndianFinanceFormatter.formatDateTimeSeconds(now),
        createdAtMillis = now,
      )

      val fs = firestore
      if (fs != null) {
        try {
          val batch = fs.batch()
          batch.set(
            fs.collection("preclosure_requests").document(request.id),
            FirestorePayloads.preClosureCreate(request),
          )
          batch.update(
            fs.collection("loans").document(loan.id),
            mapOf(
              "status" to LoanStatus.PRE_CLOSURE_REQUESTED.name,
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
          batch.set(fs.collection("audit_logs").document(audit.id), FirestorePayloads.auditLogCreate(audit))
          batch.commit().await()
        } catch (e: Exception) {
          handleFirestoreError(e, OperationType.CREATE, "preclosure_requests/${request.id}")
          throw e
        }
      }

      dao.insertPreClosureRequest(request)
      dao.updateLoan(loan.copy(status = LoanStatus.PRE_CLOSURE_REQUESTED))
      dao.insertAuditLog(audit)
      request
    }
  }

  suspend fun approveAndSettlePreClosure(
    actor: EmployeeEntity,
    requestId: String,
    paymentMode: PaymentMode,
    referenceNumber: String,
    reviewRemarks: String,
    syncImmediately: Boolean = true,
    idempotencyKey: String = "",
  ): Result<PaymentEntity> = withContext(Dispatchers.IO) {
    runCatching {
      if (actor.role != UserRole.OWNER && !actor.canApprovePreClosure) {
        throw SecurityException("Only the Owner or permitted manager can approve and settle pre-closure requests.")
      }
      val request = dao.getPreClosureById(requestId)
        ?: throw IllegalArgumentException("Pre-closure request not found.")
      require(request.status == PreClosureStatus.PENDING) { "Pre-closure request is already processed." }

      val effectiveIdempotencyKey = idempotencyKey.trim().takeIf { it.length >= 6 }
        ?: "IDEM-PCR-${request.id}"
      val existingByIdempotency = dao.getPaymentByIdempotencyKey(effectiveIdempotencyKey)
      if (existingByIdempotency != null) {
        throw IllegalArgumentException(
          "Pre-closure settlement already recorded under transaction ${existingByIdempotency.transactionId}.",
        )
      }

      val loan = resolveLoan(request.loanId) ?: throw IllegalArgumentException("Loan not found.")
      assertCanAccessLoan(actor, loan)

      val cleanRef = referenceNumber.trim()
      if (paymentMode != PaymentMode.CASH && request.netSettlementPaise > 0L) {
        require(cleanRef.isNotBlank()) { "Reference / UTR number required for ${paymentMode.displayTitle}." }
      }

      val now = System.currentTimeMillis()
      val today = IndianFinanceFormatter.todayDdMmYyyy(now)
      val transactionId = generateUniqueTransactionId(today)
      val receiptNumber = generateUniqueReceiptNumber(today)
      val paymentId = UUID.randomUUID().toString()
      val ownerUid = loan.orgOwnerUid.ifBlank { actor.orgOwnerUid.ifBlank { actor.id } }

      val schedules = dao.getScheduleForLoan(loan.id)
      val waivedSchedules = schedules.filter {
        it.status != InstallmentStatus.PAID && it.status != InstallmentStatus.WAIVED_PRECLOSURE
      }.map {
        it.copy(
          status = InstallmentStatus.WAIVED_PRECLOSURE,
          paidDateDdMmYyyy = today,
          lastPaymentId = paymentId,
        )
      }

      val settlementPayment = PaymentEntity(
        id = paymentId,
        orgOwnerUid = ownerUid,
        branchManagerUidsCsv = loan.branchManagerUidsCsv,
        assignedAgentId = loan.assignedAgentId,
        transactionId = transactionId,
        receiptNumber = receiptNumber,
        idempotencyKey = effectiveIdempotencyKey,
        loanId = loan.id,
        loanCode = loan.loanCode,
        customerId = loan.customerId,
        customerCode = loan.customerCode,
        customerName = loan.customerName,
        branchId = loan.branchId,
        branchName = loan.branchName,
        collectedByEmployeeId = actor.id,
        collectedByName = actor.fullName,
        amountPaise = request.netSettlementPaise,
        allocatedToInstallmentsPaise = request.netSettlementPaise,
        creditedToAdvancePaise = 0L,
        allocationsSummary = "Pre-closure settlement (${request.requestCode}); waived ${waivedSchedules.size} remaining installment(s)",
        paymentMode = paymentMode,
        paymentType = PaymentType.PRE_CLOSURE_SETTLEMENT,
        referenceNumber = cleanRef,
        remarks = "Pre-closure settlement (${request.requestCode}). ${reviewRemarks.trim()}".trim(),
        collectionDateDdMmYyyy = today,
        collectedAtMillis = now,
        syncStatus = SyncStatus.CONFIRMED_LOCALLY_PENDING_SYNC,
        serverConfirmationId = null,
        serverConfirmedAtMillis = null,
        lastSyncError = null,
        recordStatus = PaymentRecordStatus.ACTIVE,
      )

      val updatedLoan = loan.copy(
        paidAmountPaise = loan.paidAmountPaise + request.netSettlementPaise + loan.advanceBalancePaise,
        advanceBalancePaise = 0L,
        outstandingPaise = 0L,
        status = LoanStatus.PRE_CLOSED,
        closedDateDdMmYyyy = today,
      )

      val updatedRequest = request.copy(
        status = PreClosureStatus.APPROVED_SETTLED,
        reviewedByEmployeeId = actor.id,
        reviewedByName = actor.fullName,
        reviewedDateDdMmYyyy = today,
        reviewRemarks = reviewRemarks.trim().ifBlank { "Approved and settled" },
        settlementPaymentId = paymentId,
      )

      val syncPayload = JSONObject().apply {
        put("paymentId", settlementPayment.id)
        put("transactionId", settlementPayment.transactionId)
        put("receiptNumber", settlementPayment.receiptNumber)
        put("idempotencyKey", settlementPayment.idempotencyKey)
        put("loanId", loan.id)
        put("loanCode", loan.loanCode)
        put("preClosureCode", request.requestCode)
        put("amountPaise", settlementPayment.amountPaise)
        put("paymentMode", settlementPayment.paymentMode.name)
        put("paymentType", settlementPayment.paymentType.name)
        put("collectionDateDdMmYyyy", today)
        put("collectedByEmployeeId", actor.id)
        put("collectedAtMillis", now)
      }.toString()

      val syncItem = SyncQueueEntity(
        id = UUID.randomUUID().toString(),
        paymentId = settlementPayment.id,
        transactionId = settlementPayment.transactionId,
        receiptNumber = settlementPayment.receiptNumber,
        idempotencyKey = settlementPayment.idempotencyKey,
        loanCode = loan.loanCode,
        customerName = loan.customerName,
        amountPaise = settlementPayment.amountPaise,
        branchId = loan.branchId,
        collectedByEmployeeId = actor.id,
        payloadJson = syncPayload,
        syncStatus = SyncStatus.CONFIRMED_LOCALLY_PENDING_SYNC,
        attemptCount = 0,
        createdAtMillis = now,
        lastAttemptAtMillis = null,
        confirmedAtMillis = null,
        serverConfirmationId = null,
        errorMessage = null,
      )

      val audit = AuditLogEntity(
        id = UUID.randomUUID().toString(),
        orgOwnerUid = ownerUid,
        branchManagerUidsCsv = loan.branchManagerUidsCsv,
        actorEmployeeId = actor.id,
        actorName = actor.fullName,
        actorRole = actor.role,
        branchId = loan.branchId,
        category = "PRECLOSURE",
        action = "PRECLOSURE_APPROVED_SETTLED",
        targetType = "LOAN",
        targetId = loan.loanCode,
        summary = "Approved & settled pre-closure ${request.requestCode} on ${loan.loanCode} via receipt $receiptNumber (Txn: $transactionId, ${IndianFinanceFormatter.formatPaise(request.netSettlementPaise)})",
        timestampDdMmYyyyHhMm = IndianFinanceFormatter.formatDateTimeSeconds(now),
        createdAtMillis = now,
      )

      dao.commitPreClosureSettlement(
        updatedRequest = updatedRequest,
        updatedLoan = updatedLoan,
        waivedSchedules = waivedSchedules,
        settlementPayment = settlementPayment,
        syncItem = syncItem,
        auditLog = audit,
      )

      if (syncImmediately && firestore != null) {
        val synced = syncEngine.syncSinglePayment(
          paymentId = settlementPayment.id,
          actor = actor,
          markFailedOnError = false,
        )
        synced.getOrElse { settlementPayment }
      } else {
        settlementPayment
      }
    }
  }

  suspend fun rejectPreClosureRequest(
    actor: EmployeeEntity,
    requestId: String,
    reviewRemarks: String,
  ): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
      if (actor.role != UserRole.OWNER && !actor.canApprovePreClosure) {
        throw SecurityException("Only the Owner or permitted manager can review pre-closure requests.")
      }
      val request = dao.getPreClosureById(requestId)
        ?: throw IllegalArgumentException("Pre-closure request not found.")
      val loan = resolveLoan(request.loanId) ?: throw IllegalArgumentException("Loan not found.")
      assertCanAccessLoan(actor, loan)

      val now = System.currentTimeMillis()
      val today = IndianFinanceFormatter.todayDdMmYyyy(now)
      val cleanRemarks = reviewRemarks.trim().ifBlank { "Rejected" }
      val ownerUid = loan.orgOwnerUid.ifBlank { actor.orgOwnerUid.ifBlank { actor.id } }

      val audit = AuditLogEntity(
        id = UUID.randomUUID().toString(),
        orgOwnerUid = ownerUid,
        branchManagerUidsCsv = loan.branchManagerUidsCsv,
        actorEmployeeId = actor.id,
        actorName = actor.fullName,
        actorRole = actor.role,
        branchId = loan.branchId,
        category = "PRECLOSURE",
        action = "PRECLOSURE_REJECTED",
        targetType = "PRECLOSURE",
        targetId = request.requestCode,
        summary = "Rejected pre-closure ${request.requestCode} on ${loan.loanCode}: $cleanRemarks",
        timestampDdMmYyyyHhMm = IndianFinanceFormatter.formatDateTimeSeconds(now),
        createdAtMillis = now,
      )

      val fs = firestore
      if (fs != null) {
        try {
          val batch = fs.batch()
          batch.update(
            fs.collection("preclosure_requests").document(request.id),
            mapOf(
              "status" to PreClosureStatus.REJECTED.name,
              "reviewedByUid" to actor.id,
              "reviewedByName" to actor.fullName,
              "reviewedDateDdMmYyyy" to today,
              "reviewRemarks" to cleanRemarks,
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
          if (loan.status == LoanStatus.PRE_CLOSURE_REQUESTED) {
            batch.update(
              fs.collection("loans").document(loan.id),
              mapOf(
                "status" to LoanStatus.ACTIVE.name,
                "updatedAt" to FieldValue.serverTimestamp(),
              ),
            )
          }
          batch.set(fs.collection("audit_logs").document(audit.id), FirestorePayloads.auditLogCreate(audit))
          batch.commit().await()
        } catch (e: Exception) {
          handleFirestoreError(e, OperationType.UPDATE, "preclosure_requests/${request.id}")
          throw e
        }
      }

      dao.updatePreClosureRequest(
        request.copy(
          status = PreClosureStatus.REJECTED,
          reviewedByEmployeeId = actor.id,
          reviewedByName = actor.fullName,
          reviewedDateDdMmYyyy = today,
          reviewRemarks = cleanRemarks,
        ),
      )
      if (loan.status == LoanStatus.PRE_CLOSURE_REQUESTED) {
        dao.updateLoan(loan.copy(status = LoanStatus.ACTIVE))
      }
      dao.insertAuditLog(audit)
    }
  }

  // --- Offline Sync Queue Trigger ---
  suspend fun triggerSyncQueue(actor: EmployeeEntity): SyncEngine.SyncBatchResult {
    return syncEngine.processPendingQueue(actor = actor)
  }

  private suspend fun resolveBranch(branchId: String): BranchEntity? {
    dao.getBranchById(branchId)?.let { return it }
    val fs = firestore ?: return null
    return try {
      val snap = fs.collection("branches").document(branchId).get().await()
      snap.toObject(FirestoreBranch::class.java)?.toEntity()?.also { dao.insertBranch(it) }
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, "branches/$branchId")
      null
    }
  }

  private suspend fun resolveEmployee(employeeId: String): EmployeeEntity? {
    dao.getEmployeeById(employeeId)?.let { return it }
    val fs = firestore ?: return null
    return try {
      val snap = fs.collection("employees").document(employeeId).get().await()
      snap.toObject(FirestoreEmployee::class.java)?.toEntity()?.also { dao.insertEmployee(it) }
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, "employees/$employeeId")
      null
    }
  }

  private suspend fun resolveCustomer(customerId: String): CustomerEntity? {
    dao.getCustomerById(customerId)?.let { return it }
    val fs = firestore ?: return null
    return try {
      val snap = fs.collection("customers").document(customerId).get().await()
      snap.toObject(FirestoreCustomer::class.java)?.toEntity()?.also { dao.insertCustomer(it) }
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, "customers/$customerId")
      null
    }
  }

  private suspend fun resolveLoan(loanId: String): LoanEntity? {
    dao.getLoanById(loanId)?.let { return it }
    val fs = firestore ?: return null
    return try {
      val snap = fs.collection("loans").document(loanId).get().await()
      snap.toObject(FirestoreLoan::class.java)?.toEntity()?.also { dao.insertLoan(it) }
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, "loans/$loanId")
      null
    }
  }
}
