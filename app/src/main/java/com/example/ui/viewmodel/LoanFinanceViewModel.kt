package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.LoanFinanceApplication
import com.example.data.local.AdvanceTransactionEntity
import com.example.data.local.AuditLogEntity
import com.example.data.local.BranchEntity
import com.example.data.local.CustomerDocumentEntity
import com.example.data.local.CustomerEntity
import com.example.data.local.EmployeeEntity
import com.example.data.local.EmployeeInviteEntity
import com.example.data.local.InstallmentScheduleEntity
import com.example.data.local.LoanEntity
import com.example.data.local.PaymentAdjustmentEntity
import com.example.data.local.PaymentEntity
import com.example.data.local.PreClosureRequestEntity
import com.example.data.local.SyncQueueEntity
import com.example.data.local.TeamEntity
import com.example.data.repository.AuthAndOrgRepository
import com.example.data.repository.CustomerLoanRepository
import com.example.domain.model.DocumentType
import com.example.domain.model.InstallmentStatus
import com.example.domain.model.LoanProductType
import com.example.domain.model.LoanStatus
import com.example.domain.model.PaymentMode
import com.example.domain.model.PaymentRecordStatus
import com.example.domain.model.PaymentType
import com.example.domain.model.PreClosureStatus
import com.example.domain.model.RepaymentFrequency
import com.example.domain.model.RolePermissionTemplate
import com.example.domain.model.SearchFilterMode
import com.example.domain.model.SyncStatus
import com.example.domain.model.UserRole
import com.example.util.ConnectivityObserver
import com.example.util.DocumentStorageHelper
import com.example.util.IndianFinanceFormatter
import com.example.util.toUserFriendlyFirestoreMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardMetrics(
  val todayDateDdMmYyyy: String = IndianFinanceFormatter.todayDdMmYyyy(),
  val totalCustomers: Int = 0,
  val activeLoansCount: Int = 0,
  val pendingLoanApprovalsCount: Int = 0,
  val pendingPreClosuresCount: Int = 0,
  val totalPrincipalDisbursedPaise: Long = 0L,
  val totalOutstandingPaise: Long = 0L,
  val totalAdvanceHeldPaise: Long = 0L,
  val todayScheduledDuePaise: Long = 0L,
  val todayDueInstallmentsCount: Int = 0,
  val todayCollectedPaise: Long = 0L,
  val overdueInstallmentsCount: Int = 0,
  val overdueAmountPaise: Long = 0L,
  val localPendingSyncCount: Int = 0,
  val localPendingSyncAmountPaise: Long = 0L,
  val serverConfirmedPaymentsCount: Int = 0,
  val serverConfirmedAmountPaise: Long = 0L,
  val branchBreakdown: List<BranchPerformanceRow> = emptyList(),
)

data class BranchPerformanceRow(
  val branchName: String,
  val activeLoans: Int,
  val outstandingPaise: Long,
  val collectedPaise: Long,
)

data class CustomerLoanSearchHit(
  val customer: CustomerEntity,
  val matchingLoans: List<LoanEntity>,
  val activeOutstandingPaise: Long,
  val totalAdvanceBalancePaise: Long = 0L,
  val activeLoansCount: Int = 0,
  val pendingOrApprovedLoansCount: Int = 0,
  val closedLoansCount: Int = 0,
)

data class CollectionReportSummary(
  val fromDateDdMmYyyy: String = "",
  val toDateDdMmYyyy: String = "",
  val totalReceiptsCount: Int = 0,
  val totalCollectedPaise: Long = 0L,
  val cashCollectedPaise: Long = 0L,
  val upiCollectedPaise: Long = 0L,
  val bankTransferCollectedPaise: Long = 0L,
  val chequeCollectedPaise: Long = 0L,
  val dailyEdiCollectedPaise: Long = 0L,
  val weeklyEmiCollectedPaise: Long = 0L,
  val serverConfirmedPaise: Long = 0L,
  val serverConfirmedCount: Int = 0,
  val localPendingSyncPaise: Long = 0L,
  val localPendingSyncCount: Int = 0,
  val reversedOrCorrectedCount: Int = 0,
  val reversedAmountPaise: Long = 0L,
  val agentWiseTotals: List<Pair<String, Long>> = emptyList(),
  val branchWiseTotals: List<Pair<String, Long>> = emptyList(),
  val filteredPayments: List<PaymentEntity> = emptyList(),
)

class LoanFinanceViewModel(
  val currentActor: EmployeeEntity,
  private val authOrgRepository: AuthAndOrgRepository,
  private val customerLoanRepository: CustomerLoanRepository,
  private val connectivityObserver: ConnectivityObserver? = null,
) : ViewModel() {

  private val _isInitialCloudLoading = MutableStateFlow(true)
  val isInitialCloudLoading: StateFlow<Boolean> = _isInitialCloudLoading.asStateFlow()

  private val _cloudError = MutableStateFlow<String?>(null)
  val cloudError: StateFlow<String?> = _cloudError.asStateFlow()

  val isNetworkOnline: StateFlow<Boolean> = (connectivityObserver?.isOnline ?: flowOf(true))
    .stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5_000L),
      connectivityObserver?.isCurrentlyOnline() ?: true,
    )

  private var syncJobs: List<Job> = emptyList()

  init {
    startCloudListeners()
    observeConnectivityForAutoSync()
  }

  private fun observeConnectivityForAutoSync() {
    val observer = connectivityObserver ?: return
    viewModelScope.launch {
      var wasOnline = observer.isCurrentlyOnline()
      if (wasOnline) {
        autoFlushOfflineQueueSilently()
      }
      observer.isOnline.collect { online ->
        if (online && !wasOnline) {
          startCloudListeners()
          autoFlushOfflineQueueSilently()
        }
        wasOnline = online
      }
    }
  }

  private fun autoFlushOfflineQueueSilently() {
    if (_isSyncing.value) return
    viewModelScope.launch {
      val result = customerLoanRepository.triggerSyncQueue(actor = currentActor)
      if (result.confirmedCount > 0) {
        _feedbackMessage.value = result.message
      }
    }
  }

  private fun startCloudListeners() {
    syncJobs.forEach { it.cancel() }
    _isInitialCloudLoading.value = true
    _cloudError.value = null

    val orgJobs = authOrgRepository.startRealtimeOrgSync(
      actor = currentActor,
      scope = viewModelScope,
      onSyncError = { msg -> _cloudError.value = msg },
    )
    val ledgerJobs = customerLoanRepository.startRealtimeLedgerSync(
      actor = currentActor,
      scope = viewModelScope,
      onSyncComplete = { _isInitialCloudLoading.value = false },
      onSyncError = { msg ->
        _isInitialCloudLoading.value = false
        _cloudError.value = msg
      },
    )
    syncJobs = orgJobs + ledgerJobs
  }

  fun dismissCloudError() {
    _cloudError.value = null
  }

  fun retryCloudSync() {
    startCloudListeners()
    runSyncNow()
  }

  val branches: StateFlow<List<BranchEntity>> = authOrgRepository.observeBranches()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

  val teams: StateFlow<List<TeamEntity>> = authOrgRepository.observePermittedTeams(currentActor)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

  val employees: StateFlow<List<EmployeeEntity>> = authOrgRepository.observePermittedEmployees(currentActor)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

  val employeeInvites: StateFlow<List<EmployeeInviteEntity>> = authOrgRepository.observeEmployeeInvites(currentActor)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

  val customers: StateFlow<List<CustomerEntity>> = customerLoanRepository.observePermittedCustomers(currentActor)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

  val loans: StateFlow<List<LoanEntity>> = customerLoanRepository.observePermittedLoans(currentActor)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

  val schedules: StateFlow<List<InstallmentScheduleEntity>> = customerLoanRepository.observePermittedSchedules(currentActor)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

  val payments: StateFlow<List<PaymentEntity>> = customerLoanRepository.observePermittedPayments(currentActor)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

  val adjustments: StateFlow<List<PaymentAdjustmentEntity>> = customerLoanRepository.observePermittedAdjustments(currentActor)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

  val preClosures: StateFlow<List<PreClosureRequestEntity>> = customerLoanRepository.observePermittedPreClosures(currentActor)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

  val syncQueue: StateFlow<List<SyncQueueEntity>> = customerLoanRepository.observePermittedSyncQueue(currentActor)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

  val auditLogs: StateFlow<List<AuditLogEntity>> = customerLoanRepository.observePermittedAuditLogs(currentActor)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

  // --- Search State ---
  private val _searchQuery = MutableStateFlow("")
  val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

  private val _searchMode = MutableStateFlow(SearchFilterMode.ALL)
  val searchMode: StateFlow<SearchFilterMode> = _searchMode.asStateFlow()

  val searchHits: StateFlow<List<CustomerLoanSearchHit>> = combine(
    customers,
    loans,
    _searchQuery,
    _searchMode,
  ) { customerList, loanList, rawQuery, mode ->
    val q = rawQuery.trim().lowercase()
    val loansByCustomer = loanList.groupBy { it.customerId }
    customerList.mapNotNull { cust ->
      val custLoans = loansByCustomer[cust.id].orEmpty()
      val matches = if (q.isEmpty()) {
        true
      } else {
        when (mode) {
          SearchFilterMode.CUSTOMER_ID -> cust.customerCode.lowercase().contains(q)
          SearchFilterMode.NAME -> cust.fullName.lowercase().contains(q)
          SearchFilterMode.MOBILE -> cust.mobile.contains(q) || cust.alternateMobile.contains(q)
          SearchFilterMode.LOAN_ID -> custLoans.any { it.loanCode.lowercase().contains(q) }
          SearchFilterMode.ALL -> {
            cust.customerCode.lowercase().contains(q) ||
              cust.fullName.lowercase().contains(q) ||
              cust.mobile.contains(q) ||
              cust.alternateMobile.contains(q) ||
              custLoans.any { it.loanCode.lowercase().contains(q) }
          }
        }
      }
      if (!matches) null else {
        val activeLoans = custLoans.filter {
          it.status == LoanStatus.ACTIVE || it.status == LoanStatus.PRE_CLOSURE_REQUESTED
        }
        val pendingOrApproved = custLoans.count {
          it.status == LoanStatus.PENDING_APPROVAL || it.status == LoanStatus.APPROVED
        }
        val closedCount = custLoans.count {
          it.status == LoanStatus.COMPLETED || it.status == LoanStatus.PRE_CLOSED || it.status == LoanStatus.REJECTED
        }
        CustomerLoanSearchHit(
          customer = cust,
          matchingLoans = custLoans,
          activeOutstandingPaise = activeLoans.sumOf { it.outstandingPaise },
          totalAdvanceBalancePaise = custLoans.sumOf { it.advanceBalancePaise },
          activeLoansCount = activeLoans.size,
          pendingOrApprovedLoansCount = pendingOrApproved,
          closedLoansCount = closedCount,
        )
      }
    }
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

  // --- Dashboard Metrics ---
  val dashboardMetrics: StateFlow<DashboardMetrics> = combine(
    customers,
    loans,
    schedules,
    payments,
    preClosures,
  ) { custs, loanList, schedList, payList, pcrList ->
    val todayStr = IndianFinanceFormatter.todayDdMmYyyy()
    val todayStartMillis = IndianFinanceFormatter.startOfDayMillis()

    val activeLoans = loanList.filter {
      it.status == LoanStatus.ACTIVE || it.status == LoanStatus.PRE_CLOSURE_REQUESTED
    }
    val pendingLoans = loanList.count {
      it.status == LoanStatus.PENDING_APPROVAL || it.status == LoanStatus.APPROVED
    }
    val pendingPcrs = pcrList.count { it.status == PreClosureStatus.PENDING }

    val openInstallments = schedList.filter {
      it.status == InstallmentStatus.UPCOMING || it.status == InstallmentStatus.PARTIAL
    }
    val todayDueItems = openInstallments.filter { it.dueDateDdMmYyyy == todayStr }
    val overdueItems = openInstallments.filter { it.dueDateEpochMillis < todayStartMillis }

    val activePayments = payList.filter { it.recordStatus == PaymentRecordStatus.ACTIVE }
    val todayCollected = activePayments
      .filter { it.collectionDateDdMmYyyy == todayStr }
      .sumOf { it.amountPaise }

    val confirmedPayments = activePayments.filter { it.syncStatus == SyncStatus.SERVER_CONFIRMED }
    val localPendingPayments = activePayments.filter { it.syncStatus != SyncStatus.SERVER_CONFIRMED }

    val branchRows = loanList.groupBy { it.branchName }.map { (branchName, bLoans) ->
      val branchPaid = activePayments.filter { it.branchName == branchName }.sumOf { it.amountPaise }
      BranchPerformanceRow(
        branchName = branchName,
        activeLoans = bLoans.count { it.status == LoanStatus.ACTIVE || it.status == LoanStatus.PRE_CLOSURE_REQUESTED },
        outstandingPaise = bLoans.sumOf { it.outstandingPaise },
        collectedPaise = branchPaid,
      )
    }

    DashboardMetrics(
      todayDateDdMmYyyy = todayStr,
      totalCustomers = custs.size,
      activeLoansCount = activeLoans.size,
      pendingLoanApprovalsCount = pendingLoans,
      pendingPreClosuresCount = pendingPcrs,
      totalPrincipalDisbursedPaise = loanList
        .filter {
          it.status != LoanStatus.PENDING_APPROVAL &&
            it.status != LoanStatus.APPROVED &&
            it.status != LoanStatus.REJECTED
        }
        .sumOf { it.principalPaise },
      totalOutstandingPaise = activeLoans.sumOf { it.outstandingPaise },
      totalAdvanceHeldPaise = loanList.sumOf { it.advanceBalancePaise },
      todayScheduledDuePaise = todayDueItems.sumOf { (it.scheduledAmountPaise - it.paidAmountPaise).coerceAtLeast(0L) },
      todayDueInstallmentsCount = todayDueItems.size,
      todayCollectedPaise = todayCollected,
      overdueInstallmentsCount = overdueItems.size,
      overdueAmountPaise = overdueItems.sumOf { (it.scheduledAmountPaise - it.paidAmountPaise).coerceAtLeast(0L) },
      localPendingSyncCount = localPendingPayments.size,
      localPendingSyncAmountPaise = localPendingPayments.sumOf { it.amountPaise },
      serverConfirmedPaymentsCount = confirmedPayments.size,
      serverConfirmedAmountPaise = confirmedPayments.sumOf { it.amountPaise },
      branchBreakdown = branchRows,
    )
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), DashboardMetrics())

  // --- Collection Reports Date Filter ---
  private val _reportFromDate = MutableStateFlow("")
  val reportFromDate: StateFlow<String> = _reportFromDate.asStateFlow()

  private val _reportToDate = MutableStateFlow("")
  val reportToDate: StateFlow<String> = _reportToDate.asStateFlow()

  val collectionReport: StateFlow<CollectionReportSummary> = combine(
    payments,
    loans,
    _reportFromDate,
    _reportToDate,
  ) { payList, loanList, fromStr, toStr ->
    val fromMillis = IndianFinanceFormatter.parseDdMmYyyyToMillis(fromStr)
    val toMillis = IndianFinanceFormatter.parseDdMmYyyyToMillis(toStr)
    val loanFreqMap = loanList.associate { it.id to it.repaymentFrequency }

    val filtered = payList.filter { p ->
      val pDateMillis = IndianFinanceFormatter.parseDdMmYyyyToMillis(p.collectionDateDdMmYyyy)
        ?: IndianFinanceFormatter.startOfDayMillis(p.collectedAtMillis)
      val afterFrom = fromMillis == null || pDateMillis >= fromMillis
      val beforeTo = toMillis == null || pDateMillis <= toMillis
      afterFrom && beforeTo
    }

    val activeFiltered = filtered.filter { it.recordStatus == PaymentRecordStatus.ACTIVE }
    val nonActiveFiltered = filtered.filter { it.recordStatus != PaymentRecordStatus.ACTIVE }
    val confirmed = activeFiltered.filter { it.syncStatus == SyncStatus.SERVER_CONFIRMED }
    val pending = activeFiltered.filter { it.syncStatus != SyncStatus.SERVER_CONFIRMED }

    CollectionReportSummary(
      fromDateDdMmYyyy = fromStr,
      toDateDdMmYyyy = toStr,
      totalReceiptsCount = activeFiltered.size,
      totalCollectedPaise = activeFiltered.sumOf { it.amountPaise },
      cashCollectedPaise = activeFiltered.filter { it.paymentMode == PaymentMode.CASH }.sumOf { it.amountPaise },
      upiCollectedPaise = activeFiltered.filter { it.paymentMode == PaymentMode.UPI }.sumOf { it.amountPaise },
      bankTransferCollectedPaise = activeFiltered.filter { it.paymentMode == PaymentMode.BANK_TRANSFER }.sumOf { it.amountPaise },
      chequeCollectedPaise = activeFiltered.filter { it.paymentMode == PaymentMode.CHEQUE }.sumOf { it.amountPaise },
      dailyEdiCollectedPaise = activeFiltered.filter { loanFreqMap[it.loanId] == RepaymentFrequency.DAILY }.sumOf { it.amountPaise },
      weeklyEmiCollectedPaise = activeFiltered.filter { loanFreqMap[it.loanId] == RepaymentFrequency.WEEKLY }.sumOf { it.amountPaise },
      serverConfirmedPaise = confirmed.sumOf { it.amountPaise },
      serverConfirmedCount = confirmed.size,
      localPendingSyncPaise = pending.sumOf { it.amountPaise },
      localPendingSyncCount = pending.size,
      reversedOrCorrectedCount = nonActiveFiltered.size,
      reversedAmountPaise = nonActiveFiltered.filter { it.recordStatus == PaymentRecordStatus.REVERSED }.sumOf { it.amountPaise },
      agentWiseTotals = activeFiltered.groupBy { it.collectedByName }
        .map { (name, items) -> name to items.sumOf { it.amountPaise } }
        .sortedByDescending { it.second },
      branchWiseTotals = activeFiltered.groupBy { it.branchName }
        .map { (name, items) -> name to items.sumOf { it.amountPaise } }
        .sortedByDescending { it.second },
      filteredPayments = filtered,
    )
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), CollectionReportSummary())

  // --- UI Feedback & Immediate Receipt Confirmation ---
  private val _feedbackMessage = MutableStateFlow<String?>(null)
  val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

  private val _verifiedReceiptConfirmation = MutableStateFlow<PaymentEntity?>(null)
  val verifiedReceiptConfirmation: StateFlow<PaymentEntity?> = _verifiedReceiptConfirmation.asStateFlow()

  private val _isSyncing = MutableStateFlow(false)
  val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

  fun consumeFeedback() {
    _feedbackMessage.value = null
  }

  fun dismissReceiptConfirmation() {
    _verifiedReceiptConfirmation.value = null
  }

  fun updateSearchQuery(q: String) {
    _searchQuery.value = q
  }

  fun updateSearchMode(mode: SearchFilterMode) {
    _searchMode.value = mode
  }

  fun updateReportDateRange(fromDdMmYyyy: String, toDdMmYyyy: String) {
    _reportFromDate.value = fromDdMmYyyy
    _reportToDate.value = toDdMmYyyy
  }

  private fun canReadScoped(
    branchId: String,
    assignedAgentId: String,
    collectedByEmployeeId: String? = null,
  ): Boolean = when (currentActor.role) {
    UserRole.OWNER -> true
    UserRole.BRANCH_MANAGER -> branchId == currentActor.branchId
    UserRole.COLLECTION_AGENT ->
      assignedAgentId == currentActor.id ||
        (!collectedByEmployeeId.isNullOrBlank() && collectedByEmployeeId == currentActor.id)
  }

  // --- Detail Observers (Zero-Trust Role Scoped) ---
  fun observeCustomer(customerId: String): Flow<CustomerEntity?> =
    customerLoanRepository.observeCustomerById(customerId).map { customer ->
      customer?.takeIf { canReadScoped(it.branchId, it.assignedAgentId) }
    }

  fun observeCustomerLoans(customerId: String): Flow<List<LoanEntity>> =
    customerLoanRepository.observeCustomerLoans(customerId).map { list ->
      list.filter { canReadScoped(it.branchId, it.assignedAgentId) }
    }

  fun observeCustomerDocuments(customerId: String): Flow<List<CustomerDocumentEntity>> =
    customerLoanRepository.observeCustomerDocuments(customerId).map { list ->
      list.filter { canReadScoped(it.branchId, it.assignedAgentId) }
    }

  fun observeCustomerPayments(customerId: String): Flow<List<PaymentEntity>> =
    customerLoanRepository.observeCustomerPayments(customerId).map { list ->
      list.filter { canReadScoped(it.branchId, it.assignedAgentId, it.collectedByEmployeeId) }
    }

  fun observeCustomerAdjustments(customerId: String): Flow<List<PaymentAdjustmentEntity>> =
    customerLoanRepository.observeCustomerAdjustments(customerId).map { list ->
      list.filter { canReadScoped(it.branchId, it.assignedAgentId) }
    }

  fun observeCustomerAdvanceLedger(customerId: String): Flow<List<AdvanceTransactionEntity>> =
    customerLoanRepository.observeCustomerAdvanceLedger(customerId).map { list ->
      list.filter { canReadScoped(it.branchId, it.assignedAgentId) }
    }

  fun observeCustomerPreClosures(customerId: String): Flow<List<PreClosureRequestEntity>> =
    customerLoanRepository.observeCustomerPreClosures(customerId).map { list ->
      list.filter { canReadScoped(it.branchId, it.assignedAgentId) }
    }

  fun observeLoan(loanId: String): Flow<LoanEntity?> =
    customerLoanRepository.observeLoanById(loanId).map { loan ->
      loan?.takeIf { canReadScoped(it.branchId, it.assignedAgentId) }
    }

  fun observeLoanSchedule(loanId: String): Flow<List<InstallmentScheduleEntity>> =
    customerLoanRepository.observeLoanSchedule(loanId).map { list ->
      list.filter { canReadScoped(it.branchId, it.assignedAgentId) }
    }

  fun observeLoanPayments(loanId: String): Flow<List<PaymentEntity>> =
    customerLoanRepository.observeLoanPayments(loanId).map { list ->
      list.filter { canReadScoped(it.branchId, it.assignedAgentId, it.collectedByEmployeeId) }
    }

  fun observeLoanAdjustments(loanId: String): Flow<List<PaymentAdjustmentEntity>> =
    customerLoanRepository.observeLoanAdjustments(loanId).map { list ->
      list.filter { canReadScoped(it.branchId, it.assignedAgentId) }
    }

  fun observeLoanAdvanceLedger(loanId: String): Flow<List<AdvanceTransactionEntity>> =
    customerLoanRepository.observeLoanAdvanceLedger(loanId).map { list ->
      list.filter { canReadScoped(it.branchId, it.assignedAgentId) }
    }

  fun observeLoanPreClosures(loanId: String): Flow<List<PreClosureRequestEntity>> =
    customerLoanRepository.observeLoanPreClosures(loanId).map { list ->
      list.filter { canReadScoped(it.branchId, it.assignedAgentId) }
    }

  // --- Mutations ---
  fun registerCustomer(
    fullName: String,
    guardianName: String,
    mobile: String,
    alternateMobile: String,
    address: String,
    city: String,
    kycDocType: String,
    kycDocNumber: String,
    occupation: String,
    monthlyIncomePaise: Long,
    branchId: String,
    assignedAgentId: String,
    onSuccess: (String) -> Unit,
  ) {
    _isSyncing.value = true
    viewModelScope.launch {
      val result = customerLoanRepository.registerCustomer(
        actor = currentActor,
        fullName = fullName,
        guardianName = guardianName,
        mobile = mobile,
        alternateMobile = alternateMobile,
        address = address,
        city = city,
        kycDocumentType = kycDocType,
        kycDocumentNumber = kycDocNumber,
        occupation = occupation,
        monthlyIncomePaise = monthlyIncomePaise,
        branchId = branchId,
        assignedAgentId = assignedAgentId,
      )
      _isSyncing.value = false
      result.onSuccess { created ->
        _feedbackMessage.value = "Registered ${created.fullName} (${created.customerCode}) in Cloud Firestore"
        onSuccess(created.id)
      }.onFailure { err ->
        _feedbackMessage.value = toUserFriendlyFirestoreMessage(err, "Failed to register customer.")
      }
    }
  }

  fun updateCustomerProfile(
    customerId: String,
    fullName: String,
    guardianName: String,
    mobile: String,
    alternateMobile: String,
    address: String,
    city: String,
    occupation: String,
    monthlyIncomePaise: Long,
    onDone: () -> Unit = {},
  ) {
    _isSyncing.value = true
    viewModelScope.launch {
      val result = customerLoanRepository.updateCustomerProfile(
        actor = currentActor,
        customerId = customerId,
        fullName = fullName,
        guardianName = guardianName,
        mobile = mobile,
        alternateMobile = alternateMobile,
        address = address,
        city = city,
        occupation = occupation,
        monthlyIncomePaise = monthlyIncomePaise,
      )
      _isSyncing.value = false
      result.onSuccess { updated ->
        _feedbackMessage.value = "Updated profile for ${updated.fullName} (${updated.customerCode})"
        onDone()
      }.onFailure { err ->
        _feedbackMessage.value = toUserFriendlyFirestoreMessage(err, "Failed to update customer profile.")
      }
    }
  }

  fun uploadCustomerDocument(
    context: Context,
    customerId: String,
    documentType: DocumentType,
    documentNumber: String,
    uri: Uri,
    onDone: () -> Unit = {},
  ) {
    if (currentActor.role != UserRole.OWNER && !currentActor.canUploadDocuments) {
      _feedbackMessage.value = "You do not have permission to upload customer documents."
      return
    }
    _isSyncing.value = true
    viewModelScope.launch {
      DocumentStorageHelper.copyUriToInternalStorage(context.applicationContext, uri, customerId)
        .onSuccess { savedFile ->
          customerLoanRepository.recordCustomerDocument(
            actor = currentActor,
            customerId = customerId,
            documentType = documentType,
            documentNumber = documentNumber,
            savedFile = savedFile,
          ).onSuccess {
            _isSyncing.value = false
            _feedbackMessage.value = "Saved ${documentType.displayTitle} (${savedFile.originalFileName})"
            onDone()
          }.onFailure { err ->
            runCatching { java.io.File(savedFile.localFilePath).delete() }
            _isSyncing.value = false
            _feedbackMessage.value = toUserFriendlyFirestoreMessage(err, "Failed to save document metadata.")
          }
        }
        .onFailure { err ->
          _isSyncing.value = false
          _feedbackMessage.value = err.message ?: "Unable to copy selected document file."
        }
    }
  }

  fun submitLoanApplication(
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
    onSuccess: (String) -> Unit,
  ) {
    _isSyncing.value = true
    viewModelScope.launch {
      val result = customerLoanRepository.createLoanApplication(
        actor = currentActor,
        customerId = customerId,
        productType = productType,
        principalPaise = principalPaise,
        fixedInstallmentPaise = fixedInstallmentPaise,
        processingFeePaise = processingFeePaise,
        repaymentFrequency = repaymentFrequency,
        totalInstallments = totalInstallments,
        disbursementDateDdMmYyyy = disbursementDateDdMmYyyy,
        assignedAgentId = assignedAgentId,
        disburseImmediately = disburseImmediately,
        disbursementMode = disbursementMode,
        disbursementReference = disbursementReference,
      )
      _isSyncing.value = false
      result.onSuccess { loan ->
        _feedbackMessage.value = "Saved loan ${loan.loanCode} (${loan.status.displayTitle}) in Cloud Firestore"
        onSuccess(loan.id)
      }.onFailure { err ->
        _feedbackMessage.value = toUserFriendlyFirestoreMessage(err, "Failed to save loan application.")
      }
    }
  }

  fun approveLoanApplication(loanId: String, approvalRemarks: String, onDone: () -> Unit = {}) {
    _isSyncing.value = true
    viewModelScope.launch {
      val result = customerLoanRepository.approveLoanApplication(
        actor = currentActor,
        loanId = loanId,
        approvalRemarks = approvalRemarks,
      )
      _isSyncing.value = false
      result.onSuccess { loan ->
        _feedbackMessage.value = "Loan ${loan.loanCode} sanctioned (Approved). Ready for disbursement."
        onDone()
      }.onFailure { err ->
        _feedbackMessage.value = toUserFriendlyFirestoreMessage(err, "Failed to approve loan application.")
      }
    }
  }

  fun rejectLoanApplication(loanId: String, rejectionReason: String, onDone: () -> Unit = {}) {
    _isSyncing.value = true
    viewModelScope.launch {
      val result = customerLoanRepository.rejectLoanApplication(
        actor = currentActor,
        loanId = loanId,
        rejectionReason = rejectionReason,
      )
      _isSyncing.value = false
      result.onSuccess { loan ->
        _feedbackMessage.value = "Loan application ${loan.loanCode} rejected and archived."
        onDone()
      }.onFailure { err ->
        _feedbackMessage.value = toUserFriendlyFirestoreMessage(err, "Failed to reject loan application.")
      }
    }
  }

  fun disburseLoan(
    loanId: String,
    disbursementDateDdMmYyyy: String,
    disbursementMode: PaymentMode = PaymentMode.CASH,
    disbursementReference: String = "",
    approvalRemarks: String = "",
    onDone: () -> Unit = {},
  ) {
    _isSyncing.value = true
    viewModelScope.launch {
      val result = customerLoanRepository.disburseLoan(
        actor = currentActor,
        loanId = loanId,
        disbursementDateDdMmYyyy = disbursementDateDdMmYyyy,
        disbursementMode = disbursementMode,
        disbursementReference = disbursementReference,
        approvalRemarks = approvalRemarks,
      )
      _isSyncing.value = false
      result.onSuccess { loan ->
        _feedbackMessage.value =
          "Loan ${loan.loanCode} disbursed via ${loan.disbursementMode.displayTitle} & schedule saved to Cloud Firestore."
        onDone()
      }.onFailure { err ->
        _feedbackMessage.value = toUserFriendlyFirestoreMessage(err, "Failed to disburse loan.")
      }
    }
  }

  fun approveAndDisburseLoan(loanId: String, disbursementDateDdMmYyyy: String) {
    disburseLoan(loanId = loanId, disbursementDateDdMmYyyy = disbursementDateDdMmYyyy)
  }

  fun collectPayment(
    loanId: String,
    amountPaise: Long,
    paymentMode: PaymentMode,
    paymentType: PaymentType,
    referenceNumber: String,
    remarks: String,
    collectionDateDdMmYyyy: String,
    idempotencyKey: String = "",
    onSaved: () -> Unit = {},
  ) {
    _isSyncing.value = true
    viewModelScope.launch {
      val onlineNow = connectivityObserver?.isCurrentlyOnline() ?: true
      val result = customerLoanRepository.collectPayment(
        actor = currentActor,
        loanId = loanId,
        amountPaise = amountPaise,
        paymentMode = paymentMode,
        paymentType = paymentType,
        referenceNumber = referenceNumber,
        remarks = remarks,
        collectionDateDdMmYyyy = collectionDateDdMmYyyy,
        syncImmediately = onlineNow,
        idempotencyKey = idempotencyKey,
      )
      _isSyncing.value = false
      result.onSuccess { savedPayment ->
        _verifiedReceiptConfirmation.value = savedPayment
        onSaved()
      }.onFailure { err ->
        _feedbackMessage.value = toUserFriendlyFirestoreMessage(err, "Payment could not be saved.")
      }
    }
  }

  fun reversePayment(
    paymentId: String,
    reason: String,
    onDone: () -> Unit = {},
  ) {
    _isSyncing.value = true
    viewModelScope.launch {
      val result = customerLoanRepository.reverseConfirmedPayment(
        actor = currentActor,
        paymentId = paymentId,
        reason = reason,
      )
      _isSyncing.value = false
      result.onSuccess { adj ->
        _feedbackMessage.value =
          "Reversed receipt ${adj.originalReceiptNumber} (${adj.adjustmentCode}). Original entry preserved in audit history."
        onDone()
      }.onFailure { err ->
        _feedbackMessage.value = toUserFriendlyFirestoreMessage(err, "Payment reversal failed.")
      }
    }
  }

  fun correctPayment(
    originalPaymentId: String,
    correctedAmountPaise: Long,
    correctedPaymentMode: PaymentMode,
    correctedReferenceNumber: String,
    correctedCollectionDateDdMmYyyy: String,
    correctionReason: String,
    idempotencyKey: String = "",
    onDone: () -> Unit = {},
  ) {
    _isSyncing.value = true
    viewModelScope.launch {
      val result = customerLoanRepository.correctConfirmedPayment(
        actor = currentActor,
        originalPaymentId = originalPaymentId,
        correctedAmountPaise = correctedAmountPaise,
        correctedPaymentMode = correctedPaymentMode,
        correctedReferenceNumber = correctedReferenceNumber,
        correctedCollectionDateDdMmYyyy = correctedCollectionDateDdMmYyyy,
        correctionReason = correctionReason,
        idempotencyKey = idempotencyKey,
      )
      _isSyncing.value = false
      result.onSuccess { replacementPayment ->
        _verifiedReceiptConfirmation.value = replacementPayment
        onDone()
      }.onFailure { err ->
        _feedbackMessage.value = toUserFriendlyFirestoreMessage(err, "Payment correction failed.")
      }
    }
  }

  fun applyAdvanceToInstallments(loanId: String) {
    _isSyncing.value = true
    viewModelScope.launch {
      val result = customerLoanRepository.applyAdvanceBalanceToInstallments(currentActor, loanId)
      _isSyncing.value = false
      result.onSuccess { appliedPaise ->
        _feedbackMessage.value = "Applied ${IndianFinanceFormatter.formatPaise(appliedPaise)} from advance balance to installments."
      }.onFailure { err ->
        _feedbackMessage.value = toUserFriendlyFirestoreMessage(err, "Could not apply advance balance.")
      }
    }
  }

  fun submitPreClosureRequest(
    loanId: String,
    proposedRebatePaise: Long,
    preClosureChargePaise: Long,
    reason: String,
    onDone: () -> Unit = {},
  ) {
    _isSyncing.value = true
    viewModelScope.launch {
      val result = customerLoanRepository.submitPreClosureRequest(
        actor = currentActor,
        loanId = loanId,
        proposedRebatePaise = proposedRebatePaise,
        preClosureChargePaise = preClosureChargePaise,
        reason = reason,
      )
      _isSyncing.value = false
      result.onSuccess { req ->
        _feedbackMessage.value = "Pre-closure request ${req.requestCode} submitted."
        onDone()
      }.onFailure { err ->
        _feedbackMessage.value = toUserFriendlyFirestoreMessage(err, "Failed to submit pre-closure request.")
      }
    }
  }

  fun approvePreClosure(
    requestId: String,
    paymentMode: PaymentMode,
    referenceNumber: String,
    reviewRemarks: String,
  ) {
    _isSyncing.value = true
    viewModelScope.launch {
      val result = customerLoanRepository.approveAndSettlePreClosure(
        actor = currentActor,
        requestId = requestId,
        paymentMode = paymentMode,
        referenceNumber = referenceNumber,
        reviewRemarks = reviewRemarks,
      )
      _isSyncing.value = false
      result.onSuccess { settlementPayment ->
        _verifiedReceiptConfirmation.value = settlementPayment
      }.onFailure { err ->
        _feedbackMessage.value = toUserFriendlyFirestoreMessage(err, "Failed to settle pre-closure.")
      }
    }
  }

  fun rejectPreClosure(requestId: String, reviewRemarks: String) {
    _isSyncing.value = true
    viewModelScope.launch {
      val result = customerLoanRepository.rejectPreClosureRequest(currentActor, requestId, reviewRemarks)
      _isSyncing.value = false
      result.onSuccess {
        _feedbackMessage.value = "Pre-closure request rejected."
      }.onFailure { err ->
        _feedbackMessage.value = toUserFriendlyFirestoreMessage(err, "Failed to reject pre-closure.")
      }
    }
  }

  fun createBranch(name: String, city: String, address: String, onDone: () -> Unit = {}) {
    _isSyncing.value = true
    viewModelScope.launch {
      val result = authOrgRepository.createBranch(currentActor, name, city, address)
      _isSyncing.value = false
      result.onSuccess { branch ->
        _feedbackMessage.value = "Created branch ${branch.name} (${branch.branchCode})"
        onDone()
      }.onFailure { err ->
        _feedbackMessage.value = toUserFriendlyFirestoreMessage(err, "Failed to create branch.")
      }
    }
  }

  fun createTeam(
    teamName: String,
    branchId: String,
    managerEmployeeId: String?,
    areaBeat: String,
    onDone: () -> Unit = {},
  ) {
    _isSyncing.value = true
    viewModelScope.launch {
      val result = authOrgRepository.createTeam(currentActor, teamName, branchId, managerEmployeeId, areaBeat)
      _isSyncing.value = false
      result.onSuccess { team ->
        _feedbackMessage.value = "Created team ${team.name} (${team.teamCode})"
        onDone()
      }.onFailure { err ->
        _feedbackMessage.value = toUserFriendlyFirestoreMessage(err, "Failed to create team.")
      }
    }
  }

  fun createEmployee(
    fullName: String,
    mobile: String,
    invitedEmail: String,
    role: UserRole,
    branchId: String,
    teamId: String?,
    teamName: String?,
    permissions: RolePermissionTemplate,
    onDone: () -> Unit = {},
  ) {
    _isSyncing.value = true
    viewModelScope.launch {
      val result = authOrgRepository.createEmployeeInvite(
        actor = currentActor,
        fullName = fullName,
        mobile = mobile,
        invitedEmail = invitedEmail,
        assignedRole = role,
        branchId = branchId,
        teamId = teamId,
        teamName = teamName,
        permissions = permissions,
      )
      _isSyncing.value = false
      result.onSuccess { invite ->
        _feedbackMessage.value =
          "Provisioned ${invite.role.displayTitle} ${invite.fullName} (${invite.employeeCode}) for ${invite.invitedEmail}"
        onDone()
      }.onFailure { err ->
        _feedbackMessage.value = toUserFriendlyFirestoreMessage(err, "Failed to create employee invitation.")
      }
    }
  }

  fun revokeEmployeeInvite(employeeCode: String) {
    _isSyncing.value = true
    viewModelScope.launch {
      val result = authOrgRepository.revokeEmployeeInvite(currentActor, employeeCode)
      _isSyncing.value = false
      result.onSuccess {
        _feedbackMessage.value = "Revoked pending invitation $employeeCode."
      }.onFailure { err ->
        _feedbackMessage.value = toUserFriendlyFirestoreMessage(err, "Failed to revoke invitation.")
      }
    }
  }

  fun updateEmployee(
    targetEmployeeId: String,
    role: UserRole,
    branchId: String,
    teamId: String?,
    teamName: String?,
    isActive: Boolean,
    permissions: RolePermissionTemplate,
    onDone: () -> Unit = {},
  ) {
    _isSyncing.value = true
    viewModelScope.launch {
      val result = authOrgRepository.updateEmployeeRoleAndPermissions(
        actor = currentActor,
        targetEmployeeId = targetEmployeeId,
        newRole = role,
        branchId = branchId,
        teamId = teamId,
        teamName = teamName,
        isActive = isActive,
        permissions = permissions,
      )
      _isSyncing.value = false
      result.onSuccess { emp ->
        _feedbackMessage.value = "Updated ${emp.fullName} (${emp.employeeCode})"
        onDone()
      }.onFailure { err ->
        _feedbackMessage.value = toUserFriendlyFirestoreMessage(err, "Failed to update employee.")
      }
    }
  }

  fun runSyncNow() {
    if (_isSyncing.value) return
    _isSyncing.value = true
    viewModelScope.launch {
      val result = customerLoanRepository.triggerSyncQueue(actor = currentActor)
      _isSyncing.value = false
      _feedbackMessage.value = result.message
    }
  }

  companion object {
    fun factory(actor: EmployeeEntity): ViewModelProvider.Factory = viewModelFactory {
      initializer {
        val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as LoanFinanceApplication
        LoanFinanceViewModel(
          currentActor = actor,
          authOrgRepository = app.container.authOrgRepository,
          customerLoanRepository = app.container.customerLoanRepository,
          connectivityObserver = app.container.connectivityObserver,
        )
      }
    }
  }
}
