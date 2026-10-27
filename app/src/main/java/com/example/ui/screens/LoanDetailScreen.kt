package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.data.local.AdvanceTransactionEntity
import com.example.data.local.EmployeeEntity
import com.example.data.local.InstallmentScheduleEntity
import com.example.data.local.LoanEntity
import com.example.data.local.PaymentAdjustmentEntity
import com.example.data.local.PaymentEntity
import com.example.data.local.PreClosureRequestEntity
import com.example.domain.model.InstallmentStatus
import com.example.domain.model.LoanStatus
import com.example.domain.model.PaymentAdjustmentType
import com.example.domain.model.PaymentAllocationCalculator
import com.example.domain.model.PaymentMode
import com.example.domain.model.PaymentRecordStatus
import com.example.domain.model.PaymentType
import com.example.domain.model.PreClosureStatus
import com.example.domain.model.UserRole
import com.example.ui.components.LoanStatusBadge
import com.example.ui.components.PaymentRecordStatusBadge
import com.example.ui.components.SyncStatusBadge
import com.example.ui.theme.ConfirmedGreen
import com.example.ui.theme.ConfirmedGreenContainer
import com.example.ui.theme.InfoBlue
import com.example.ui.theme.InfoBlueContainer
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.OverdueCrimson
import com.example.ui.theme.OverdueCrimsonContainer
import com.example.util.IndianFinanceFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoanDetailScreen(
  actor: EmployeeEntity,
  loan: LoanEntity?,
  schedules: List<InstallmentScheduleEntity>,
  payments: List<PaymentEntity>,
  advanceLedger: List<AdvanceTransactionEntity>,
  preClosures: List<PreClosureRequestEntity>,
  adjustments: List<PaymentAdjustmentEntity> = emptyList(),
  onBack: () -> Unit,
  onOpenCustomerProfile: (String) -> Unit,
  onApproveApplication: (approvalRemarks: String) -> Unit,
  onRejectApplication: (rejectionReason: String) -> Unit,
  onDisburseLoan: (
    disbursementDateDdMmYyyy: String,
    disbursementMode: PaymentMode,
    disbursementReference: String,
    approvalRemarks: String,
  ) -> Unit,
  onCollectPayment: (
    amountPaise: Long,
    mode: PaymentMode,
    type: PaymentType,
    refNumber: String,
    remarks: String,
    dateDdMmYyyy: String,
    idempotencyKey: String,
  ) -> Unit,
  onReversePayment: (paymentId: String, reason: String) -> Unit = { _, _ -> },
  onCorrectPayment: (
    originalPaymentId: String,
    correctedAmountPaise: Long,
    correctedMode: PaymentMode,
    correctedRef: String,
    correctedDateDdMmYyyy: String,
    reason: String,
    idempotencyKey: String,
  ) -> Unit = { _, _, _, _, _, _, _ -> },
  onApplyAdvance: () -> Unit,
  onSubmitPreClosure: (rebatePaise: Long, chargePaise: Long, reason: String) -> Unit,
  onApprovePreClosure: (requestId: String, mode: PaymentMode, refNumber: String, remarks: String) -> Unit,
  onRejectPreClosure: (requestId: String, remarks: String) -> Unit,
) {
  BackHandler(onBack = onBack)

  var selectedTab by rememberSaveable { mutableIntStateOf(0) }
  var scheduleFilter by rememberSaveable { mutableStateOf("ALL") }
  var paymentDialogType by rememberSaveable { mutableStateOf<PaymentType?>(null) }
  var showApproveDialog by rememberSaveable { mutableStateOf(false) }
  var showRejectDialog by rememberSaveable { mutableStateOf(false) }
  var showDisburseDialog by rememberSaveable { mutableStateOf(false) }
  var preClosureSettleTarget by remember { mutableStateOf<PreClosureRequestEntity?>(null) }
  var preClosureRejectTarget by remember { mutableStateOf<PreClosureRequestEntity?>(null) }
  var reverseTargetPayment by remember { mutableStateOf<PaymentEntity?>(null) }
  var correctTargetPayment by remember { mutableStateOf<PaymentEntity?>(null) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(loan?.let { "${it.loanCode} • ${it.customerName}" } ?: "Loan Account") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        actions = {
          if (loan != null) {
            IconButton(
              onClick = { onOpenCustomerProfile(loan.customerId) },
              modifier = Modifier.testTag("loan_open_customer_profile_button"),
            ) {
              Icon(Icons.Default.Person, contentDescription = "Open Customer Profile")
            }
          }
        },
      )
    },
  ) { innerPadding ->
    if (loan == null) {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding),
      ) {
        Text("Loan account not found.")
      }
      return@Scaffold
    }

    val canApproveOrReject = actor.role == UserRole.OWNER || actor.canApproveLoan
    val isCollectible = loan.status == LoanStatus.ACTIVE || loan.status == LoanStatus.PRE_CLOSURE_REQUESTED
    val isDisbursed = loan.status != LoanStatus.PENDING_APPROVAL &&
      loan.status != LoanStatus.APPROVED &&
      loan.status != LoanStatus.REJECTED
    val canAdjustPayments = (actor.role == UserRole.OWNER ||
      (actor.role == UserRole.BRANCH_MANAGER && actor.branchId == loan.branchId && actor.canCollectPayment)) &&
      loan.status != LoanStatus.PRE_CLOSED &&
      loan.status != LoanStatus.REJECTED

    val scheduleSnapshots = remember(schedules) {
      schedules.map { s ->
        PaymentAllocationCalculator.ScheduleSnapshot(
          id = s.id,
          installmentNumber = s.installmentNumber,
          dueDateDdMmYyyy = s.dueDateDdMmYyyy,
          dueDateEpochMillis = s.dueDateEpochMillis,
          scheduledAmountPaise = s.scheduledAmountPaise,
          paidAmountPaise = s.paidAmountPaise,
          status = s.status,
        )
      }
    }
    val dueSummary = remember(loan.outstandingPaise, loan.advanceBalancePaise, scheduleSnapshots) {
      PaymentAllocationCalculator.computeLoanDueSummary(
        outstandingPaise = loan.outstandingPaise,
        advanceBalancePaise = loan.advanceBalancePaise,
        schedules = scheduleSnapshots,
      )
    }

    LazyColumn(
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .testTag("loan_detail_screen"),
    ) {
      // Account Summary Card
      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Row(
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                text = "${loan.loanCode} (${loan.repaymentFrequency.displayTitle})",
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = MonoFontFamily),
                fontWeight = FontWeight.Bold,
              )
              LoanStatusBadge(status = loan.status)
            }
            Text(
              text = "${loan.productType.displayTitle} • Branch: ${loan.branchName} • Officer: ${loan.assignedAgentName}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()
            SummaryMetricPair(
              "Sanctioned Principal",
              IndianFinanceFormatter.formatPaise(loan.principalPaise),
              "Total Payable",
              IndianFinanceFormatter.formatPaise(loan.totalPayablePaise),
            )
            SummaryMetricPair(
              "Processing Fee",
              IndianFinanceFormatter.formatPaise(loan.processingFeePaise),
              "Net Disbursed Payout",
              if (isDisbursed) {
                IndianFinanceFormatter.formatPaise(
                  loan.netDisbursedPaise.takeIf { it > 0L }
                    ?: (loan.principalPaise - loan.processingFeePaise).coerceAtLeast(0L),
                )
              } else {
                "Pending Disbursement"
              },
            )
            SummaryMetricPair(
              "Collected So Far",
              IndianFinanceFormatter.formatPaise(loan.paidAmountPaise),
              "Outstanding Due",
              IndianFinanceFormatter.formatPaise(loan.outstandingPaise),
            )
            SummaryMetricPair(
              "Installment (${loan.repaymentFrequency.shortLabel})",
              "${IndianFinanceFormatter.formatPaise(loan.installmentAmountPaise)} × ${loan.totalInstallments}",
              "Advance Held",
              IndianFinanceFormatter.formatPaise(loan.advanceBalancePaise),
            )
            SummaryMetricPair(
              if (isDisbursed) "Disbursed (${loan.disbursementMode.displayTitle})" else "Proposed Disbursement",
              loan.disbursementDateDdMmYyyy +
                (if (isDisbursed && loan.disbursementReference.isNotBlank()) " • ${loan.disbursementReference}" else ""),
              "Maturity Date",
              loan.maturityDateDdMmYyyy,
            )

            // Lifecycle Action / Archive Banners
            when (loan.status) {
              LoanStatus.PENDING_APPROVAL -> {
                HorizontalDivider()
                Text(
                  text = "Application submitted on ${loan.applicationDateDdMmYyyy}. Awaiting credit review & disbursement.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (canApproveOrReject) {
                  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                      onClick = { showApproveDialog = true },
                      modifier = Modifier
                        .weight(1f)
                        .testTag("approve_only_loan_button"),
                    ) {
                      Icon(Icons.Default.CheckCircle, contentDescription = null)
                      Spacer(Modifier.width(4.dp))
                      Text("Approve")
                    }
                    Button(
                      onClick = { showDisburseDialog = true },
                      modifier = Modifier
                        .weight(1.2f)
                        .testTag("approve_disburse_loan_button"),
                    ) {
                      Icon(Icons.Default.AccountBalanceWallet, contentDescription = null)
                      Spacer(Modifier.width(4.dp))
                      Text("Disburse")
                    }
                  }
                  OutlinedButton(
                    onClick = { showRejectDialog = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier
                      .fillMaxWidth()
                      .testTag("reject_loan_button"),
                  ) {
                    Text("Reject Application")
                  }
                }
              }

              LoanStatus.APPROVED -> {
                HorizontalDivider()
                Card(
                  colors = CardDefaults.cardColors(containerColor = InfoBlueContainer),
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                  ) {
                    Text(
                      text = "Sanctioned on ${loan.approvedDateDdMmYyyy.ifBlank { loan.applicationDateDdMmYyyy }} — Ready for Disbursement",
                      style = MaterialTheme.typography.titleSmall,
                      fontWeight = FontWeight.Bold,
                      color = InfoBlue,
                    )
                    if (loan.approvalRemarks.isNotBlank()) {
                      Text(
                        text = "Remarks: ${loan.approvalRemarks}",
                        style = MaterialTheme.typography.bodySmall,
                        color = InfoBlue,
                      )
                    }
                    if (canApproveOrReject) {
                      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                          onClick = { showDisburseDialog = true },
                          modifier = Modifier
                            .weight(1.4f)
                            .testTag("disburse_sanctioned_loan_button"),
                        ) {
                          Text("Disburse & Generate Schedule")
                        }
                        OutlinedButton(
                          onClick = { showRejectDialog = true },
                          colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                          modifier = Modifier
                            .weight(0.8f)
                            .testTag("reject_loan_button"),
                        ) {
                          Text("Reject")
                        }
                      }
                    }
                  }
                }
              }

              LoanStatus.REJECTED -> {
                HorizontalDivider()
                Card(
                  colors = CardDefaults.cardColors(containerColor = OverdueCrimsonContainer),
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                  ) {
                    Text(
                      text = "Application Rejected${if (loan.closedDateDdMmYyyy.isNotBlank()) " on ${loan.closedDateDdMmYyyy}" else ""}",
                      style = MaterialTheme.typography.titleSmall,
                      fontWeight = FontWeight.Bold,
                      color = OverdueCrimson,
                    )
                    Text(
                      text = "Reason: ${loan.rejectionReason.ifBlank { "Declined during credit review" }}",
                      style = MaterialTheme.typography.bodySmall,
                      color = OverdueCrimson,
                    )
                  }
                }
              }

              LoanStatus.COMPLETED, LoanStatus.PRE_CLOSED -> {
                HorizontalDivider()
                Card(
                  colors = CardDefaults.cardColors(containerColor = ConfirmedGreenContainer),
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(12.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Default.History,
                      contentDescription = null,
                      tint = ConfirmedGreen,
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                      Text(
                        text = "Closed Historical Account (${loan.status.displayTitle})" +
                          (if (loan.closedDateDdMmYyyy.isNotBlank()) " • ${loan.closedDateDdMmYyyy}" else ""),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = ConfirmedGreen,
                      )
                      Text(
                        text = "All schedules, receipts, advance entries, and pre-closure records are permanently preserved for audit.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ConfirmedGreen,
                      )
                    }
                  }
                }
              }

              LoanStatus.ACTIVE, LoanStatus.PRE_CLOSURE_REQUESTED -> Unit
            }

            if (isCollectible) {
              HorizontalDivider()
              Card(
                colors = CardDefaults.cardColors(
                  containerColor = if (dueSummary.overduePaise > 0L) {
                    OverdueCrimsonContainer
                  } else {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                  },
                ),
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("loan_installment_due_banner"),
              ) {
                Column(
                  modifier = Modifier.padding(12.dp),
                  verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  SummaryMetricPair(
                    "Installment Due Now",
                    IndianFinanceFormatter.formatPaise(dueSummary.actionableDueNowPaise, showZeroPaise = true),
                    "Next Due (${dueSummary.nextDueDateDdMmYyyy ?: "—"})",
                    IndianFinanceFormatter.formatPaise(dueSummary.nextDueRemainingPaise, showZeroPaise = true),
                  )
                  SummaryMetricPair(
                    "Due Today (${dueSummary.dueTodayCount} inst.)",
                    IndianFinanceFormatter.formatPaise(dueSummary.dueTodayPaise, showZeroPaise = true),
                    "Overdue (${dueSummary.overdueCount} inst.)",
                    IndianFinanceFormatter.formatPaise(dueSummary.overduePaise, showZeroPaise = true),
                  )
                }
              }

              Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 4.dp),
              ) {
                if (actor.role == UserRole.OWNER || actor.canCollectPayment) {
                  Button(
                    onClick = { paymentDialogType = PaymentType.REGULAR_INSTALLMENT },
                    modifier = Modifier
                      .weight(1f)
                      .testTag("open_collect_payment_button"),
                  ) {
                    Icon(Icons.Default.Payments, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Collect ${loan.repaymentFrequency.shortLabel}")
                  }
                }
                if (actor.role == UserRole.OWNER || actor.canManageAdvance) {
                  FilledTonalButton(
                    onClick = { paymentDialogType = PaymentType.ADVANCE_COLLECTION },
                    modifier = Modifier
                      .weight(1f)
                      .testTag("open_advance_deposit_button"),
                  ) {
                    Icon(Icons.Default.Savings, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Add Advance")
                  }
                }
              }
            }
          }
        }
      }

      // Tabs
      item {
        val tabs = listOf(
          "Schedule (${schedules.size})",
          "Payments (${payments.size})",
          "Advance (${advanceLedger.size})",
          "Pre-Closure (${preClosures.size})",
        )
        ScrollableTabRow(
          selectedTabIndex = selectedTab,
          edgePadding = 0.dp,
        ) {
          tabs.forEachIndexed { idx, label ->
            Tab(
              selected = selectedTab == idx,
              onClick = { selectedTab = idx },
              text = { Text(label) },
              modifier = Modifier.testTag("loan_tab_$idx"),
            )
          }
        }
      }

      // Tab Content
      when (selectedTab) {
        0 -> {
          if (schedules.isEmpty()) {
            item {
              Text(
                if (loan.status == LoanStatus.REJECTED) {
                  "Application was rejected prior to disbursement; no installment schedule was generated."
                } else {
                  "Installment schedule will be generated once this loan is disbursed."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          } else {
            val todayMillis = IndianFinanceFormatter.startOfDayMillis()
            val openCount = schedules.count {
              it.status == InstallmentStatus.UPCOMING || it.status == InstallmentStatus.PARTIAL
            }
            val overdueCount = schedules.count {
              (it.status == InstallmentStatus.UPCOMING || it.status == InstallmentStatus.PARTIAL) &&
                it.dueDateEpochMillis < todayMillis
            }
            val settledCount = schedules.count {
              it.status == InstallmentStatus.PAID || it.status == InstallmentStatus.WAIVED_PRECLOSURE
            }
            val filteredSchedules = schedules.filter { inst ->
              val isOpen = inst.status == InstallmentStatus.UPCOMING || inst.status == InstallmentStatus.PARTIAL
              when (scheduleFilter) {
                "OPEN" -> isOpen
                "OVERDUE" -> isOpen && inst.dueDateEpochMillis < todayMillis
                "SETTLED" -> inst.status == InstallmentStatus.PAID || inst.status == InstallmentStatus.WAIVED_PRECLOSURE
                else -> true
              }
            }

            item {
              Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                  .fillMaxWidth()
                  .horizontalScroll(rememberScrollState()),
              ) {
                listOf(
                  "ALL" to "All (${schedules.size})",
                  "OPEN" to "Open ($openCount)",
                  "OVERDUE" to "Overdue ($overdueCount)",
                  "SETTLED" to "Paid / Settled ($settledCount)",
                ).forEach { (key, label) ->
                  FilterChip(
                    selected = scheduleFilter == key,
                    onClick = { scheduleFilter = key },
                    label = { Text(label) },
                  )
                }
              }
            }

            items(filteredSchedules, key = { it.id }) { inst ->
              val isOverdue = (inst.status == InstallmentStatus.UPCOMING || inst.status == InstallmentStatus.PARTIAL) &&
                inst.dueDateEpochMillis < todayMillis
              Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(
                  1.dp,
                  if (isOverdue) OverdueCrimson.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceVariant,
                ),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Row(
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      "#${inst.installmentNumber} • Due: ${inst.dueDateDdMmYyyy}",
                      style = MaterialTheme.typography.titleSmall.copy(fontFamily = MonoFontFamily),
                    )
                    Text(
                      "Scheduled: ${IndianFinanceFormatter.formatPaise(inst.scheduledAmountPaise)} • Paid: ${IndianFinanceFormatter.formatPaise(inst.paidAmountPaise)}" +
                        (inst.paidDateDdMmYyyy?.let { " on $it" } ?: ""),
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                  Text(
                    text = if (isOverdue) "OVERDUE" else inst.status.displayTitle,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = when {
                      inst.status == InstallmentStatus.PAID || inst.status == InstallmentStatus.WAIVED_PRECLOSURE -> ConfirmedGreen
                      isOverdue -> OverdueCrimson
                      else -> MaterialTheme.colorScheme.primary
                    },
                  )
                }
              }
            }
          }
        }

        1 -> {
          if (payments.isEmpty()) {
            item {
              Text(
                "No collection receipts recorded on this loan yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          } else {
            items(payments, key = { it.id }) { p ->
              val isActiveRecord = p.recordStatus == PaymentRecordStatus.ACTIVE
              Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(
                  1.dp,
                  if (isActiveRecord) MaterialTheme.colorScheme.surfaceVariant else OverdueCrimson.copy(alpha = 0.35f),
                ),
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("payment_card_${p.receiptNumber}"),
              ) {
                Column(
                  modifier = Modifier.padding(14.dp),
                  verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                  ) {
                    Text(
                      text = IndianFinanceFormatter.formatPaise(p.amountPaise, showZeroPaise = true),
                      style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = MonoFontFamily,
                        textDecoration = if (isActiveRecord) TextDecoration.None else TextDecoration.LineThrough,
                      ),
                      fontWeight = FontWeight.Bold,
                      color = if (isActiveRecord) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                      horizontalArrangement = Arrangement.spacedBy(6.dp),
                      verticalAlignment = Alignment.CenterVertically,
                    ) {
                      PaymentRecordStatusBadge(recordStatus = p.recordStatus)
                      SyncStatusBadge(syncStatus = p.syncStatus)
                    }
                  }
                  Text(
                    text = "${p.receiptNumber} • Txn: ${p.transactionId}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                    fontWeight = FontWeight.SemiBold,
                  )
                  Text(
                    text = "${p.paymentMode.displayTitle} • ${p.paymentType.displayTitle}",
                    style = MaterialTheme.typography.bodySmall,
                  )
                  if (p.allocatedToInstallmentsPaise > 0L || p.creditedToAdvancePaise > 0L) {
                    Text(
                      text = "EMI/EDI Applied: ${IndianFinanceFormatter.formatPaise(p.allocatedToInstallmentsPaise)} • Advance Credited: ${IndianFinanceFormatter.formatPaise(p.creditedToAdvancePaise)}",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                  if (p.allocationsSummary.isNotBlank()) {
                    Text(
                      text = "Allocation: ${p.allocationsSummary}",
                      style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                  Text(
                    text = "Date: ${p.collectionDateDdMmYyyy} • Collected By: ${p.collectedByName}" +
                      (if (p.referenceNumber.isNotBlank()) " • Ref: ${p.referenceNumber}" else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  if (p.serverConfirmationId != null) {
                    Text(
                      text = "Server Confirmation ID: ${p.serverConfirmationId}",
                      style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                      color = ConfirmedGreen,
                    )
                  }
                  if (!isActiveRecord) {
                    Text(
                      text = "${p.recordStatus.displayTitle} on ${p.reversedDateDdMmYyyy.orEmpty()} by ${p.reversedByName.orEmpty()} — Reason: ${p.reversalReason.orEmpty()}",
                      style = MaterialTheme.typography.labelSmall,
                      color = OverdueCrimson,
                      fontWeight = FontWeight.Medium,
                    )
                  }
                  if (canAdjustPayments &&
                    isActiveRecord &&
                    p.paymentType != PaymentType.PRE_CLOSURE_SETTLEMENT &&
                    p.paymentMode != PaymentMode.ADVANCE_ADJUSTMENT
                  ) {
                    Row(
                      horizontalArrangement = Arrangement.spacedBy(8.dp),
                      modifier = Modifier.padding(top = 4.dp),
                    ) {
                      OutlinedButton(
                        onClick = { correctTargetPayment = p },
                        modifier = Modifier
                          .weight(1f)
                          .testTag("correct_payment_${p.receiptNumber}"),
                      ) {
                        Icon(Icons.Default.Edit, contentDescription = "Correct Receipt")
                        Spacer(Modifier.width(4.dp))
                        Text("Correct")
                      }
                      OutlinedButton(
                        onClick = { reverseTargetPayment = p },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = OverdueCrimson),
                        modifier = Modifier
                          .weight(1f)
                          .testTag("reverse_payment_${p.receiptNumber}"),
                      ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Reverse Receipt")
                        Spacer(Modifier.width(4.dp))
                        Text("Reverse")
                      }
                    }
                  }
                }
              }
            }
          }

          if (adjustments.isNotEmpty()) {
            item {
              Text(
                text = "Correction & Reversal Audit Trail (${adjustments.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp),
              )
            }
            items(adjustments, key = { it.id }) { adj ->
              Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Column(
                  modifier = Modifier.padding(12.dp),
                  verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                  ) {
                    Text(
                      text = adj.adjustmentCode,
                      style = MaterialTheme.typography.labelLarge.copy(fontFamily = MonoFontFamily),
                      fontWeight = FontWeight.Bold,
                    )
                    Text(
                      text = adj.adjustmentType.displayTitle,
                      style = MaterialTheme.typography.labelSmall,
                      fontWeight = FontWeight.Bold,
                      color = if (adj.adjustmentType == PaymentAdjustmentType.REVERSAL) OverdueCrimson else InfoBlue,
                    )
                  }
                  Text(
                    text = "Original: ${adj.originalReceiptNumber} (${IndianFinanceFormatter.formatPaise(adj.originalAmountPaise, showZeroPaise = true)})" +
                      (if (adj.replacementReceiptNumber != null) {
                        " → Replacement: ${adj.replacementReceiptNumber} (${IndianFinanceFormatter.formatPaise(adj.replacementAmountPaise, showZeroPaise = true)})"
                      } else {
                        ""
                      }),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                  )
                  Text(
                    text = "Reason: ${adj.reason}",
                    style = MaterialTheme.typography.bodySmall,
                  )
                  Text(
                    text = "Recorded on ${adj.performedDateDdMmYyyy} by ${adj.performedByName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          }
        }

        2 -> {
          item {
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Text("Available Advance Balance", style = MaterialTheme.typography.labelLarge)
                Text(
                  text = IndianFinanceFormatter.formatPaise(loan.advanceBalancePaise, showZeroPaise = true),
                  style = MaterialTheme.typography.headlineSmall.copy(fontFamily = MonoFontFamily),
                  color = MaterialTheme.colorScheme.primary,
                )
                if (loan.advanceBalancePaise > 0L && loan.outstandingPaise > 0L && isCollectible &&
                  (actor.role == UserRole.OWNER || actor.canManageAdvance)
                ) {
                  Button(
                    onClick = onApplyAdvance,
                    modifier = Modifier
                      .fillMaxWidth()
                      .testTag("apply_advance_button"),
                  ) {
                    Text("Apply Advance Balance to Upcoming Installments")
                  }
                }
              }
            }
          }
          if (advanceLedger.isEmpty()) {
            item {
              Text(
                "No advance deposits or adjustments recorded on this loan.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          } else {
            items(advanceLedger, key = { it.id }) { tx ->
              Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Row(
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(tx.note, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(
                      "${tx.dateDdMmYyyy} • By ${tx.createdByName}",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                  Column(horizontalAlignment = Alignment.End) {
                    val prefix = if (tx.entryType.startsWith("CREDIT")) "+" else "-"
                    Text(
                      "$prefix${IndianFinanceFormatter.formatPaise(tx.amountPaise)}",
                      style = MaterialTheme.typography.titleSmall.copy(fontFamily = MonoFontFamily),
                      color = if (prefix == "+") ConfirmedGreen else MaterialTheme.colorScheme.primary,
                    )
                    Text(
                      "Bal: ${IndianFinanceFormatter.formatPaise(tx.balanceAfterPaise)}",
                      style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                    )
                  }
                }
              }
            }
          }
        }

        3 -> {
          if (loan.status == LoanStatus.ACTIVE && (actor.role == UserRole.OWNER || actor.canRequestPreClosure)) {
            item {
              PreClosureRequestFormCard(
                loan = loan,
                onSubmit = onSubmitPreClosure,
              )
            }
          }
          if (preClosures.isEmpty()) {
            item {
              Text(
                "No pre-closure quotes or settlements recorded for this loan.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          } else {
            items(preClosures, key = { it.id }) { req ->
              Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Column(
                  modifier = Modifier.padding(14.dp),
                  verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                  ) {
                    Text(
                      req.requestCode,
                      style = MaterialTheme.typography.titleSmall.copy(fontFamily = MonoFontFamily),
                      fontWeight = FontWeight.Bold,
                    )
                    Text(
                      req.status.displayTitle,
                      style = MaterialTheme.typography.labelMedium,
                      color = MaterialTheme.colorScheme.primary,
                      fontWeight = FontWeight.Bold,
                    )
                  }
                  Text(
                    "Outstanding: ${IndianFinanceFormatter.formatPaise(req.outstandingBeforePaise)} • Adv Adjusted: ${IndianFinanceFormatter.formatPaise(req.advanceAdjustedPaise)} • Rebate: ${IndianFinanceFormatter.formatPaise(req.proposedRebatePaise)} • Charge: ${IndianFinanceFormatter.formatPaise(req.preClosureChargePaise)}",
                    style = MaterialTheme.typography.bodySmall,
                  )
                  Text(
                    "Net Settlement Payable: ${IndianFinanceFormatter.formatPaise(req.netSettlementPaise)}",
                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = MonoFontFamily),
                    color = MaterialTheme.colorScheme.primary,
                  )
                  Text(
                    "Requested on ${req.requestedDateDdMmYyyy} by ${req.requestedByName}: \"${req.reason}\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  if (!req.reviewRemarks.isNullOrBlank()) {
                    Text(
                      "Review (${req.reviewedDateDdMmYyyy ?: ""} • ${req.reviewedByName ?: ""}): ${req.reviewRemarks}",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }

                  if (req.status == PreClosureStatus.PENDING &&
                    (actor.role == UserRole.OWNER || actor.canApprovePreClosure)
                  ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                      Button(
                        onClick = { preClosureSettleTarget = req },
                        modifier = Modifier
                          .weight(1f)
                          .testTag("approve_preclosure_${req.requestCode}"),
                      ) {
                        Text("Approve & Settle")
                      }
                      OutlinedButton(
                        onClick = { preClosureRejectTarget = req },
                        modifier = Modifier
                          .weight(1f)
                          .testTag("reject_preclosure_${req.requestCode}"),
                      ) {
                        Text("Reject")
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  if (showApproveDialog && loan != null) {
    ApproveApplicationDialog(
      loan = loan,
      onDismiss = { showApproveDialog = false },
      onConfirmApprove = { remarks ->
        showApproveDialog = false
        onApproveApplication(remarks)
      },
    )
  }

  if (showRejectDialog && loan != null) {
    RejectApplicationDialog(
      loan = loan,
      onDismiss = { showRejectDialog = false },
      onConfirmReject = { reason ->
        showRejectDialog = false
        onRejectApplication(reason)
      },
    )
  }

  if (showDisburseDialog && loan != null) {
    DisburseLoanDialog(
      loan = loan,
      onDismiss = { showDisburseDialog = false },
      onConfirmDisburse = { disbDate, disbMode, disbRef, remarks ->
        showDisburseDialog = false
        onDisburseLoan(disbDate, disbMode, disbRef, remarks)
      },
    )
  }

  val activeDialogType = paymentDialogType
  if (activeDialogType != null && loan != null) {
    CollectPaymentDialog(
      loan = loan,
      schedules = schedules,
      initialType = activeDialogType,
      onDismiss = { paymentDialogType = null },
      onConfirmSave = { amtPaise, mode, type, ref, rem, dateStr, idempotencyKey ->
        paymentDialogType = null
        onCollectPayment(amtPaise, mode, type, ref, rem, dateStr, idempotencyKey)
      },
    )
  }

  val targetReverse = reverseTargetPayment
  if (targetReverse != null) {
    ReversePaymentDialog(
      payment = targetReverse,
      onDismiss = { reverseTargetPayment = null },
      onConfirmReverse = { reason ->
        val pId = targetReverse.id
        reverseTargetPayment = null
        onReversePayment(pId, reason)
      },
    )
  }

  val targetCorrect = correctTargetPayment
  if (targetCorrect != null) {
    CorrectPaymentDialog(
      payment = targetCorrect,
      onDismiss = { correctTargetPayment = null },
      onConfirmCorrect = { correctedAmt, correctedMode, correctedRef, correctedDate, reason, idempotencyKey ->
        val pId = targetCorrect.id
        correctTargetPayment = null
        onCorrectPayment(pId, correctedAmt, correctedMode, correctedRef, correctedDate, reason, idempotencyKey)
      },
    )
  }

  val targetPcr = preClosureSettleTarget
  if (targetPcr != null) {
    SettlePreClosureDialog(
      request = targetPcr,
      onDismiss = { preClosureSettleTarget = null },
      onConfirm = { mode, ref, remarks ->
        val reqId = targetPcr.id
        preClosureSettleTarget = null
        onApprovePreClosure(reqId, mode, ref, remarks)
      },
    )
  }

  val rejectPcr = preClosureRejectTarget
  if (rejectPcr != null) {
    RejectPreClosureDialog(
      request = rejectPcr,
      onDismiss = { preClosureRejectTarget = null },
      onConfirmReject = { remarks ->
        val reqId = rejectPcr.id
        preClosureRejectTarget = null
        onRejectPreClosure(reqId, remarks)
      },
    )
  }
}

@Composable
private fun SummaryMetricPair(l1: String, v1: String, l2: String, v2: String) {
  Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.weight(1f)) {
      Text(l1, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Text(v1, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily), fontWeight = FontWeight.SemiBold)
    }
    Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
      Text(l2, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Text(v2, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily), fontWeight = FontWeight.SemiBold)
    }
  }
}

@Composable
private fun PreClosureRequestFormCard(
  loan: LoanEntity,
  onSubmit: (Long, Long, String) -> Unit,
) {
  var rebateRupees by rememberSaveable { mutableStateOf("0") }
  var chargeRupees by rememberSaveable { mutableStateOf("0") }
  var reason by rememberSaveable { mutableStateOf("") }

  val rebatePaise = IndianFinanceFormatter.parseRupeesToPaise(rebateRupees) ?: 0L
  val chargePaise = IndianFinanceFormatter.parseRupeesToPaise(chargeRupees) ?: 0L
  val netSettlement = (loan.outstandingPaise - loan.advanceBalancePaise - rebatePaise + chargePaise).coerceAtLeast(0L)

  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text("Submit Pre-Closure Request", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = rebateRupees,
          onValueChange = { rebateRupees = it },
          label = { Text("Interest Rebate (₹)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
          value = chargeRupees,
          onValueChange = { chargeRupees = it },
          label = { Text("Foreclosure Fee (₹)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          modifier = Modifier.weight(1f),
        )
      }
      OutlinedTextField(
        value = reason,
        onValueChange = { reason = it },
        label = { Text("Customer Reason / Remarks *") },
        modifier = Modifier
          .fillMaxWidth()
          .testTag("preclosure_reason_input"),
      )
      Text(
        text = "Net Settlement Payable: ${IndianFinanceFormatter.formatPaise(netSettlement, showZeroPaise = true)}",
        style = MaterialTheme.typography.titleSmall.copy(fontFamily = MonoFontFamily),
        color = MaterialTheme.colorScheme.primary,
      )
      Button(
        onClick = { onSubmit(rebatePaise, chargePaise, reason) },
        enabled = reason.trim().isNotEmpty(),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("submit_preclosure_button"),
      ) {
        Text("Submit Pre-Closure Request")
      }
    }
  }
}
