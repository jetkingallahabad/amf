package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.local.CustomerEntity
import com.example.data.local.EmployeeEntity
import com.example.data.local.InstallmentScheduleEntity
import com.example.domain.model.InstallmentStatus
import com.example.domain.model.LoanProductType
import com.example.domain.model.LoanScheduleCalculator
import com.example.domain.model.PaymentMode
import com.example.domain.model.RepaymentFrequency
import com.example.domain.model.UserRole
import com.example.ui.components.DdMmYyyyDateField
import com.example.ui.components.EmptyLedgerState
import com.example.ui.theme.ConfirmedGreen
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.OverdueCrimson
import com.example.util.IndianFinanceFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewLoanApplicationScreen(
  actor: EmployeeEntity,
  customers: List<CustomerEntity>,
  employees: List<EmployeeEntity>,
  preselectedCustomerId: String?,
  isSaving: Boolean = false,
  onBack: () -> Unit,
  onSubmitLoan: (
    customerId: String,
    productType: LoanProductType,
    principalPaise: Long,
    fixedInstallmentPaise: Long,
    processingFeePaise: Long,
    frequency: RepaymentFrequency,
    totalInstallments: Int,
    disbursementDateDdMmYyyy: String,
    assignedAgentId: String,
    disburseImmediately: Boolean,
    disbursementMode: PaymentMode,
    disbursementReference: String,
  ) -> Unit,
) {
  BackHandler(onBack = onBack)

  var selectedCustomerId by rememberSaveable {
    mutableStateOf(preselectedCustomerId ?: customers.firstOrNull()?.id.orEmpty())
  }
  var productType by rememberSaveable { mutableStateOf(LoanProductType.DAILY_BUSINESS_EDI) }
  var frequency by rememberSaveable { mutableStateOf(RepaymentFrequency.DAILY) }
  var principalRupees by rememberSaveable { mutableStateOf("5000") }
  var fixedInstallmentRupees by rememberSaveable { mutableStateOf("100") }
  var processingFeeRupees by rememberSaveable { mutableStateOf("0") }
  var installmentsStr by rememberSaveable { mutableStateOf("60") }
  var disbursementDate by rememberSaveable { mutableStateOf(IndianFinanceFormatter.todayDdMmYyyy()) }
  val canApprove = actor.role == UserRole.OWNER || actor.canApproveLoan
  var disburseImmediately by rememberSaveable { mutableStateOf(canApprove) }
  var disbursementMode by rememberSaveable { mutableStateOf(PaymentMode.CASH) }
  var disbursementReference by rememberSaveable { mutableStateOf("") }

  val selectedCustomer = customers.find { it.id == selectedCustomerId }
  var selectedAgentId by rememberSaveable(selectedCustomerId) {
    mutableStateOf(selectedCustomer?.assignedAgentId ?: actor.id)
  }

  var custDropdownExpanded by remember { mutableStateOf(false) }
  var prodDropdownExpanded by remember { mutableStateOf(false) }
  var agentDropdownExpanded by remember { mutableStateOf(false) }
  var disbModeDropdownExpanded by remember { mutableStateOf(false) }

  val principalPaise = IndianFinanceFormatter.parseRupeesToPaise(principalRupees) ?: 0L
  val feePaise = IndianFinanceFormatter.parseRupeesToPaise(processingFeeRupees) ?: 0L
  val fixedInstallmentPaise = IndianFinanceFormatter.parseRupeesToPaise(fixedInstallmentRupees) ?: 0L
  val tenureInt = installmentsStr.toIntOrNull() ?: 0

  val liveQuote = LoanScheduleCalculator.calculateFixedProductQuote(
    principalPaise = principalPaise,
    fixedInstallmentPaise = fixedInstallmentPaise,
    processingFeePaise = feePaise,
    totalInstallments = tenureInt,
    frequency = frequency,
    disbursementDateDdMmYyyy = disbursementDate,
  )

  val validationError = when {
    principalPaise !in 10_000L..500_000_000L -> "Principal must be between ₹100 and ₹50,00,000."
    fixedInstallmentPaise <= 0L -> "Fixed EMI/EDI amount must be greater than ₹0."
    feePaise < 0L || feePaise >= principalPaise -> "Processing fee must be non-negative and less than principal."
    tenureInt !in 1..1000 -> "Tenure must be between 1 and 1,000 installments."
    liveQuote == null -> "Enter a valid disbursement date in DD-MM-YYYY format."
    canApprove && disburseImmediately && disbursementMode != PaymentMode.CASH && disbursementReference.trim().isEmpty() ->
      "UTR / Cheque / Reference number is required for ${disbursementMode.displayTitle} disbursement."
    else -> null
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("New Loan Application") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
      )
    },
  ) { innerPadding ->
    Column(
      verticalArrangement = Arrangement.spacedBy(12.dp),
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
    ) {
      if (customers.isEmpty()) {
        EmptyLedgerState(
          icon = Icons.Default.EventRepeat,
          title = "Register a Customer First",
          subtitle = "At least one registered customer is required before creating a loan application.",
        )
        return@Column
      }

      // Customer Selector
      ExposedDropdownMenuBox(
        expanded = custDropdownExpanded,
        onExpandedChange = { custDropdownExpanded = !custDropdownExpanded },
      ) {
        OutlinedTextField(
          value = selectedCustomer?.let { "${it.fullName} (${it.customerCode} • ${it.mobile})" } ?: "Select Customer",
          onValueChange = {},
          readOnly = true,
          label = { Text("Applicant Customer *") },
          trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = custDropdownExpanded) },
          modifier = Modifier
            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
            .fillMaxWidth()
            .testTag("loan_customer_selector"),
        )
        ExposedDropdownMenu(
          expanded = custDropdownExpanded,
          onDismissRequest = { custDropdownExpanded = false },
        ) {
          customers.forEach { c ->
            DropdownMenuItem(
              text = { Text("${c.fullName} (${c.customerCode} • +91 ${c.mobile})") },
              onClick = {
                selectedCustomerId = c.id
                custDropdownExpanded = false
              },
            )
          }
        }
      }

      // Repayment Frequency: Daily EDI vs Weekly EMI
      Text("Repayment Schedule Frequency", style = MaterialTheme.typography.labelLarge)
      SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        RepaymentFrequency.entries.forEachIndexed { idx, freq ->
          SegmentedButton(
            selected = frequency == freq,
            onClick = {
              frequency = freq
              installmentsStr = if (freq == RepaymentFrequency.DAILY) "60" else "12"
              fixedInstallmentRupees = if (freq == RepaymentFrequency.DAILY) "100" else "500"
            },
            shape = SegmentedButtonDefaults.itemShape(index = idx, count = RepaymentFrequency.entries.size),
            modifier = Modifier.testTag("freq_btn_${freq.name.lowercase()}"),
          ) {
            Text(freq.displayTitle)
          }
        }
      }

      // Loan Product Selector
      ExposedDropdownMenuBox(
        expanded = prodDropdownExpanded,
        onExpandedChange = { prodDropdownExpanded = !prodDropdownExpanded },
      ) {
        OutlinedTextField(
          value = productType.displayTitle,
          onValueChange = {},
          readOnly = true,
          label = { Text("Loan Scheme / Product") },
          trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = prodDropdownExpanded) },
          modifier = Modifier
            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
            .fillMaxWidth(),
        )
        ExposedDropdownMenu(
          expanded = prodDropdownExpanded,
          onDismissRequest = { prodDropdownExpanded = false },
        ) {
          LoanProductType.entries.forEach { pt ->
            DropdownMenuItem(
              text = { Text(pt.displayTitle) },
              onClick = {
                productType = pt
                prodDropdownExpanded = false
              },
            )
          }
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = principalRupees,
          onValueChange = { principalRupees = it },
          label = { Text("Principal (₹) *") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          modifier = Modifier
            .weight(1.2f)
            .testTag("loan_principal_input"),
        )
        OutlinedTextField(
          value = fixedInstallmentRupees,
          onValueChange = { fixedInstallmentRupees = it.filter { ch -> ch.isDigit() || ch == '.' } },
          label = { Text("Fixed EMI/EDI (₹) *") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier
            .weight(0.8f)
            .testTag("loan_fixed_installment_input"),
        )
      }

      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = installmentsStr,
          onValueChange = { installmentsStr = it.filter { ch -> ch.isDigit() } },
          label = {
            Text(
              if (frequency == RepaymentFrequency.DAILY) "Tenure (Days / EDIs)" else "Tenure (Weeks / EMIs)",
            )
          },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          modifier = Modifier
            .weight(1f)
            .testTag("loan_tenure_input"),
        )
        OutlinedTextField(
          value = processingFeeRupees,
          onValueChange = { processingFeeRupees = it },
          label = { Text("Processing Fee (₹)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          modifier = Modifier.weight(1f),
        )
      }

      // Quick Tenure Presets
      Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
      ) {
        val presets = if (frequency == RepaymentFrequency.DAILY) {
          listOf("30", "60", "90", "100", "120")
        } else {
          listOf("12", "15", "16", "24", "36", "52")
        }
        presets.forEach { preset ->
          AssistChip(
            onClick = { installmentsStr = preset },
            label = {
              Text("$preset ${if (frequency == RepaymentFrequency.DAILY) "Days" else "Weeks"}")
            },
          )
        }
      }

      DdMmYyyyDateField(
        label = if (canApprove && disburseImmediately) "Disbursement Date" else "Proposed Disbursement Date",
        value = disbursementDate,
        onValueChange = { disbursementDate = it },
        modifier = Modifier.fillMaxWidth(),
        testTagName = "loan_disbursement_date_input",
      )

      // Assigned Agent Selector
      val availableAgents = employees.filter {
        it.isActive && (selectedCustomer == null || it.branchId == selectedCustomer.branchId)
      }.ifEmpty { listOf(actor) }
      ExposedDropdownMenuBox(
        expanded = agentDropdownExpanded,
        onExpandedChange = { agentDropdownExpanded = !agentDropdownExpanded },
      ) {
        val agentLabel = availableAgents.find { it.id == selectedAgentId }?.fullName ?: actor.fullName
        OutlinedTextField(
          value = agentLabel,
          onValueChange = {},
          readOnly = true,
          label = { Text("Assigned Collection Officer") },
          trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = agentDropdownExpanded) },
          modifier = Modifier
            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
            .fillMaxWidth(),
        )
        ExposedDropdownMenu(
          expanded = agentDropdownExpanded,
          onDismissRequest = { agentDropdownExpanded = false },
        ) {
          availableAgents.forEach { emp ->
            DropdownMenuItem(
              text = { Text("${emp.fullName} (${emp.employeeCode})") },
              onClick = {
                selectedAgentId = emp.id
                agentDropdownExpanded = false
              },
            )
          }
        }
      }

      if (canApprove) {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text("Approve & Disburse Immediately", style = MaterialTheme.typography.titleSmall)
                Text(
                  if (disburseImmediately) {
                    "Disburses funds & generates ${frequency.displayTitle} schedule immediately"
                  } else {
                    "Saves as 'Pending Approval' for two-stage credit review & treasury disbursement"
                  },
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Switch(
                checked = disburseImmediately,
                onCheckedChange = { disburseImmediately = it },
                modifier = Modifier.testTag("loan_disburse_now_switch"),
              )
            }

            if (disburseImmediately) {
              ExposedDropdownMenuBox(
                expanded = disbModeDropdownExpanded,
                onExpandedChange = { disbModeDropdownExpanded = !disbModeDropdownExpanded },
              ) {
                OutlinedTextField(
                  value = disbursementMode.displayTitle,
                  onValueChange = {},
                  readOnly = true,
                  label = { Text("Disbursement Payout Mode") },
                  trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = disbModeDropdownExpanded) },
                  modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                  expanded = disbModeDropdownExpanded,
                  onDismissRequest = { disbModeDropdownExpanded = false },
                ) {
                  listOf(PaymentMode.CASH, PaymentMode.UPI, PaymentMode.BANK_TRANSFER, PaymentMode.CHEQUE).forEach { m ->
                    DropdownMenuItem(
                      text = { Text(m.displayTitle) },
                      onClick = {
                        disbursementMode = m
                        disbModeDropdownExpanded = false
                      },
                    )
                  }
                }
              }

              if (disbursementMode != PaymentMode.CASH) {
                OutlinedTextField(
                  value = disbursementReference,
                  onValueChange = { disbursementReference = it },
                  label = { Text("Disbursement UTR / Cheque / Ref No. *") },
                  singleLine = true,
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("loan_disburse_ref_input"),
                )
              }
            }
          }
        }
      }

      if (validationError != null) {
        Text(
          text = validationError,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
        )
      }

      // Live Quote Summary Card
      if (liveQuote != null && principalPaise >= 10_000L && feePaise in 0L until principalPaise) {
        val netDisbursedPayout = (liveQuote.principalPaise - liveQuote.processingFeePaise).coerceAtLeast(0L)
        Card(
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
          ),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Text(
              text = "${frequency.displayTitle} Repayment & Payout Preview",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            QuoteRow("Sanctioned Principal", IndianFinanceFormatter.formatPaise(liveQuote.principalPaise))
            QuoteRow("Processing Fee Deducted", "- ${IndianFinanceFormatter.formatPaise(liveQuote.processingFeePaise)}")
            QuoteRow("Net Disbursed Payout", IndianFinanceFormatter.formatPaise(netDisbursedPayout))
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            QuoteRow("Fixed Margin", IndianFinanceFormatter.formatPaise(liveQuote.totalMarginPaise))
            QuoteRow("Total Payable", IndianFinanceFormatter.formatPaise(liveQuote.totalScheduledRepaymentPaise))
            QuoteRow(
              "Fixed Installment (${frequency.shortLabel})",
              "${IndianFinanceFormatter.formatPaise(liveQuote.fixedInstallmentPaise)} × ${liveQuote.totalInstallments}",
            )
            QuoteRow("First Installment Due", liveQuote.repaymentStartDateDdMmYyyy)
            QuoteRow("Maturity Date", liveQuote.expectedCompletionDateDdMmYyyy)
          }
        }
      }

      Button(
        onClick = {
          onSubmitLoan(
            selectedCustomerId,
            productType,
            principalPaise,
            fixedInstallmentPaise,
            feePaise,
            frequency,
            tenureInt,
            disbursementDate,
            selectedAgentId,
            disburseImmediately,
            disbursementMode,
            disbursementReference.trim(),
          )
        },
        enabled = selectedCustomerId.isNotBlank() && validationError == null && !isSaving,
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag("submit_loan_button"),
      ) {
        Text(
          when {
            isSaving -> "Saving Loan…"
            canApprove && disburseImmediately -> "Approve, Disburse & Generate Schedule"
            else -> "Submit Loan Application"
          },
        )
      }
    }
  }
}

@Composable
private fun QuoteRow(label: String, value: String) {
  Row(
    horizontalArrangement = Arrangement.SpaceBetween,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Text(label, style = MaterialTheme.typography.bodySmall)
    Text(
      value,
      style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
      fontWeight = FontWeight.Bold,
    )
  }
}

@Composable
fun GlobalSchedulesScreen(
  schedules: List<InstallmentScheduleEntity>,
  onOpenLoanDetail: (String) -> Unit,
) {
  var bucket by rememberSaveable { mutableStateOf("DUE_TODAY") }
  var freqFilter by rememberSaveable { mutableStateOf("ALL") }

  val todayStr = IndianFinanceFormatter.todayDdMmYyyy()
  val todayMillis = IndianFinanceFormatter.startOfDayMillis()

  val filtered = schedules.filter { inst ->
    val matchesFreq = when (freqFilter) {
      "DAILY" -> inst.frequency == RepaymentFrequency.DAILY
      "WEEKLY" -> inst.frequency == RepaymentFrequency.WEEKLY
      else -> true
    }
    val isOpen = inst.status == InstallmentStatus.UPCOMING || inst.status == InstallmentStatus.PARTIAL
    val matchesBucket = when (bucket) {
      "DUE_TODAY" -> isOpen && inst.dueDateDdMmYyyy == todayStr
      "OVERDUE" -> isOpen && inst.dueDateEpochMillis < todayMillis
      "UPCOMING" -> isOpen && inst.dueDateEpochMillis > todayMillis
      "PAID" -> inst.status == InstallmentStatus.PAID || inst.status == InstallmentStatus.WAIVED_PRECLOSURE
      else -> true
    }
    matchesFreq && matchesBucket
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp, vertical = 12.dp)
      .testTag("schedules_screen"),
  ) {
    Text(
      text = "Daily EDI & Weekly EMI Schedules",
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.Bold,
    )
    Text(
      text = "Today: $todayStr",
      style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Row(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState())
        .padding(top = 8.dp),
    ) {
      listOf(
        "DUE_TODAY" to "Due Today",
        "OVERDUE" to "Overdue",
        "UPCOMING" to "Upcoming",
        "PAID" to "Paid / Settled",
        "ALL" to "All",
      ).forEach { (key, label) ->
        FilterChip(
          selected = bucket == key,
          onClick = { bucket = key },
          label = { Text(label) },
        )
      }
    }

    Row(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.padding(vertical = 4.dp),
    ) {
      listOf("ALL" to "All Frequencies", "DAILY" to "Daily EDI", "WEEKLY" to "Weekly EMI").forEach { (k, label) ->
        FilterChip(
          selected = freqFilter == k,
          onClick = { freqFilter = k },
          label = { Text(label) },
        )
      }
    }

    if (filtered.isEmpty()) {
      Spacer(Modifier.height(12.dp))
      EmptyLedgerState(
        icon = Icons.Default.EventRepeat,
        title = "No Installments in This View",
        subtitle = "Switch filters above or disburse a Daily EDI / Weekly EMI loan to populate schedules.",
      )
    } else {
      LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
      ) {
        items(filtered, key = { it.id }) { inst ->
          val remaining = (inst.scheduledAmountPaise - inst.paidAmountPaise).coerceAtLeast(0L)
          val isOverdue = (inst.status == InstallmentStatus.UPCOMING || inst.status == InstallmentStatus.PARTIAL) &&
            inst.dueDateEpochMillis < todayMillis
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(
              1.dp,
              if (isOverdue) OverdueCrimson.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant,
            ),
            modifier = Modifier
              .fillMaxWidth()
              .clickable { onOpenLoanDetail(inst.loanId) },
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
              modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "${inst.customerName} (${inst.loanCode})",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                )
                Text(
                  text = "${inst.frequency.displayTitle} #${inst.installmentNumber} • Due: ${inst.dueDateDdMmYyyy}",
                  style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                  color = if (isOverdue) OverdueCrimson else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                  text = "Scheduled: ${IndianFinanceFormatter.formatPaise(inst.scheduledAmountPaise)} • Paid: ${IndianFinanceFormatter.formatPaise(inst.paidAmountPaise)}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Spacer(Modifier.width(8.dp))
              if (remaining > 0L && inst.status != InstallmentStatus.WAIVED_PRECLOSURE) {
                Button(onClick = { onOpenLoanDetail(inst.loanId) }) {
                  Text(IndianFinanceFormatter.formatPaise(remaining))
                }
              } else {
                Text(
                  text = inst.status.displayTitle,
                  style = MaterialTheme.typography.labelMedium,
                  color = ConfirmedGreen,
                  fontWeight = FontWeight.Bold,
                )
              }
            }
          }
        }
      }
    }
  }
}
