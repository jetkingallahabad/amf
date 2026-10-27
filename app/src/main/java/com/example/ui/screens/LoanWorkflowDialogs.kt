package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.local.InstallmentScheduleEntity
import com.example.data.local.LoanEntity
import com.example.data.local.PaymentEntity
import com.example.data.local.PreClosureRequestEntity
import com.example.domain.model.PaymentAllocationCalculator
import com.example.domain.model.PaymentMode
import com.example.domain.model.PaymentType
import com.example.ui.components.DdMmYyyyDateField
import com.example.ui.theme.ConfirmedGreen
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.OverdueCrimson
import com.example.util.IndianFinanceFormatter
import java.util.UUID

@Composable
fun ApproveApplicationDialog(
  loan: LoanEntity,
  onDismiss: () -> Unit,
  onConfirmApprove: (approvalRemarks: String) -> Unit,
) {
  var remarks by rememberSaveable(loan.id) {
    mutableStateOf("Sanctioned after KYC & repayment capacity verification")
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Approve Application ${loan.loanCode}") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = "Applicant: ${loan.customerName} (${loan.customerCode})",
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          text = "Principal: ${IndianFinanceFormatter.formatPaise(loan.principalPaise)} • ${loan.repaymentFrequency.displayTitle} (${loan.totalInstallments} × ${IndianFinanceFormatter.formatPaise(loan.installmentAmountPaise)})",
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
        )
        OutlinedTextField(
          value = remarks,
          onValueChange = { remarks = it },
          label = { Text("Sanction / Approval Remarks") },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("approve_loan_remarks_input"),
        )
        Text(
          text = "Approving moves this application to 'Approved' status so treasury disbursement can be recorded separately.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onConfirmApprove(remarks) },
        modifier = Modifier.testTag("confirm_approve_application_button"),
      ) {
        Text("Approve Application")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

@Composable
fun RejectApplicationDialog(
  loan: LoanEntity,
  onDismiss: () -> Unit,
  onConfirmReject: (rejectionReason: String) -> Unit,
) {
  var reason by rememberSaveable(loan.id) { mutableStateOf("") }
  val isValid = reason.trim().length >= 3

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Reject Loan Application ${loan.loanCode}") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = "Rejecting archives ${loan.loanCode} permanently in ${loan.customerName}'s loan history with zero outstanding balance.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
          value = reason,
          onValueChange = { reason = it },
          label = { Text("Rejection Reason * (min 3 chars)") },
          isError = reason.isNotEmpty() && !isValid,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("reject_loan_reason_input"),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onConfirmReject(reason.trim()) },
        enabled = isValid,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        modifier = Modifier.testTag("confirm_reject_application_button"),
      ) {
        Text("Reject Application")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisburseLoanDialog(
  loan: LoanEntity,
  onDismiss: () -> Unit,
  onConfirmDisburse: (
    disbursementDateDdMmYyyy: String,
    disbursementMode: PaymentMode,
    disbursementReference: String,
    remarks: String,
  ) -> Unit,
) {
  var disbDate by rememberSaveable(loan.id) {
    mutableStateOf(loan.disbursementDateDdMmYyyy.ifBlank { IndianFinanceFormatter.todayDdMmYyyy() })
  }
  var disbMode by rememberSaveable(loan.id) { mutableStateOf(PaymentMode.CASH) }
  var disbRef by rememberSaveable(loan.id) { mutableStateOf("") }
  var remarks by rememberSaveable(loan.id) {
    mutableStateOf(loan.approvalRemarks.ifBlank { "Disbursed to borrower" })
  }
  var modeExpanded by remember { mutableStateOf(false) }

  val netPayoutPaise = (loan.principalPaise - loan.processingFeePaise).coerceAtLeast(0L)
  val validDate = IndianFinanceFormatter.parseDdMmYyyyToMillis(disbDate) != null
  val refRequired = disbMode != PaymentMode.CASH
  val canDisburse = validDate && (!refRequired || disbRef.trim().isNotEmpty())

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Disburse Loan ${loan.loanCode}") },
    text = {
      Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.verticalScroll(rememberScrollState()),
      ) {
        Card(
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
          ),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            DisburseMetricRow("Sanctioned Principal", IndianFinanceFormatter.formatPaise(loan.principalPaise))
            DisburseMetricRow("Less Processing Fee", "- ${IndianFinanceFormatter.formatPaise(loan.processingFeePaise)}")
            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
            DisburseMetricRow(
              "Net Payout to Borrower",
              IndianFinanceFormatter.formatPaise(netPayoutPaise, showZeroPaise = true),
            )
          }
        }

        DdMmYyyyDateField(
          label = "Disbursement Date",
          value = disbDate,
          onValueChange = { disbDate = it },
          modifier = Modifier.fillMaxWidth(),
          testTagName = "disburse_dialog_date_input",
        )

        ExposedDropdownMenuBox(
          expanded = modeExpanded,
          onExpandedChange = { modeExpanded = !modeExpanded },
        ) {
          OutlinedTextField(
            value = disbMode.displayTitle,
            onValueChange = {},
            readOnly = true,
            label = { Text("Disbursement Mode") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modeExpanded) },
            modifier = Modifier
              .menuAnchor(MenuAnchorType.PrimaryNotEditable)
              .fillMaxWidth(),
          )
          ExposedDropdownMenu(
            expanded = modeExpanded,
            onDismissRequest = { modeExpanded = false },
          ) {
            listOf(PaymentMode.CASH, PaymentMode.UPI, PaymentMode.BANK_TRANSFER, PaymentMode.CHEQUE).forEach { m ->
              DropdownMenuItem(
                text = { Text(m.displayTitle) },
                onClick = {
                  disbMode = m
                  modeExpanded = false
                },
              )
            }
          }
        }

        if (refRequired) {
          OutlinedTextField(
            value = disbRef,
            onValueChange = { disbRef = it },
            label = { Text("Disbursement UTR / Cheque / Ref No. *") },
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("disburse_dialog_ref_input"),
          )
        }

        OutlinedTextField(
          value = remarks,
          onValueChange = { remarks = it },
          label = { Text("Disbursement Remarks") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onConfirmDisburse(disbDate, disbMode, disbRef.trim(), remarks.trim()) },
        enabled = canDisburse,
        modifier = Modifier.testTag("confirm_disburse_loan_button"),
      ) {
        Text("Disburse & Generate ${loan.repaymentFrequency.shortLabel} Schedule")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectPaymentDialog(
  loan: LoanEntity,
  schedules: List<InstallmentScheduleEntity> = emptyList(),
  initialType: PaymentType,
  onDismiss: () -> Unit,
  onConfirmSave: (Long, PaymentMode, PaymentType, String, String, String, String) -> Unit,
) {
  val scheduleSnapshots = remember(schedules) {
    schedules.map {
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
  }
  val dueSummary = remember(loan.outstandingPaise, loan.advanceBalancePaise, scheduleSnapshots) {
    PaymentAllocationCalculator.computeLoanDueSummary(
      outstandingPaise = loan.outstandingPaise,
      advanceBalancePaise = loan.advanceBalancePaise,
      schedules = scheduleSnapshots,
    )
  }

  val defaultRupees = if (initialType == PaymentType.ADVANCE_COLLECTION) {
    IndianFinanceFormatter.paiseToPlainRupeesString(loan.installmentAmountPaise * 2)
  } else {
    val suggested = dueSummary.actionableDueNowPaise.takeIf { it > 0L }
      ?: minOf(loan.installmentAmountPaise, loan.outstandingPaise)
    IndianFinanceFormatter.paiseToPlainRupeesString(suggested)
  }

  val dialogIdempotencyKey = rememberSaveable(loan.id, initialType.name) {
    "IDEM-${UUID.randomUUID()}"
  }
  var submittedOnce by rememberSaveable(dialogIdempotencyKey) { mutableStateOf(false) }

  var amountRupees by rememberSaveable(dialogIdempotencyKey) { mutableStateOf(defaultRupees) }
  var paymentMode by rememberSaveable(dialogIdempotencyKey) { mutableStateOf(PaymentMode.CASH) }
  var refNumber by rememberSaveable(dialogIdempotencyKey) { mutableStateOf("") }
  var remarks by rememberSaveable(dialogIdempotencyKey) { mutableStateOf("") }
  var dateStr by rememberSaveable(dialogIdempotencyKey) { mutableStateOf(IndianFinanceFormatter.todayDdMmYyyy()) }
  var modeExpanded by remember { mutableStateOf(false) }

  val enteredPaise = IndianFinanceFormatter.parseRupeesToPaise(amountRupees) ?: 0L
  val allocationPreview = remember(enteredPaise, scheduleSnapshots, initialType) {
    PaymentAllocationCalculator.computeAllocationPlan(
      amountPaise = enteredPaise,
      schedules = scheduleSnapshots,
      paymentType = initialType,
    )
  }
  val validDate = IndianFinanceFormatter.parseDdMmYyyyToMillis(dateStr) != null
  val refMissing = paymentMode != PaymentMode.CASH && refNumber.trim().isEmpty()

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        if (initialType == PaymentType.ADVANCE_COLLECTION) "Record Advance Payment" else "Collect EMI/EDI Payment",
      )
    },
    text = {
      Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.verticalScroll(rememberScrollState()),
      ) {
        Text(
          "${loan.customerName} (${loan.loanCode})",
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.SemiBold,
        )

        Card(
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
          ),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            DisburseMetricRow("Total Outstanding", IndianFinanceFormatter.formatPaise(loan.outstandingPaise))
            if (dueSummary.overduePaise > 0L) {
              DisburseMetricRow(
                "Overdue (${dueSummary.overdueCount})",
                IndianFinanceFormatter.formatPaise(dueSummary.overduePaise),
              )
            }
            if (dueSummary.dueTodayPaise > 0L) {
              DisburseMetricRow(
                "Due Today (${dueSummary.dueTodayCount})",
                IndianFinanceFormatter.formatPaise(dueSummary.dueTodayPaise),
              )
            } else if (dueSummary.nextDueDateDdMmYyyy != null) {
              DisburseMetricRow(
                "Next Installment (${dueSummary.nextDueDateDdMmYyyy})",
                IndianFinanceFormatter.formatPaise(dueSummary.nextDueRemainingPaise),
              )
            }
          }
        }

        OutlinedTextField(
          value = amountRupees,
          onValueChange = { amountRupees = it },
          label = { Text("Amount Collected (₹) — Full or Partial *") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("collect_amount_input"),
        )

        if (enteredPaise > 0L && allocationPreview.summaryText.isNotBlank()) {
          Text(
            text = "Allocation Preview: ${allocationPreview.summaryText}",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
            color = ConfirmedGreen,
          )
        }

        ExposedDropdownMenuBox(
          expanded = modeExpanded,
          onExpandedChange = { modeExpanded = !modeExpanded },
        ) {
          OutlinedTextField(
            value = paymentMode.displayTitle,
            onValueChange = {},
            readOnly = true,
            label = { Text("Payment Mode") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modeExpanded) },
            modifier = Modifier
              .menuAnchor(MenuAnchorType.PrimaryNotEditable)
              .fillMaxWidth(),
          )
          ExposedDropdownMenu(
            expanded = modeExpanded,
            onDismissRequest = { modeExpanded = false },
          ) {
            listOf(PaymentMode.CASH, PaymentMode.UPI, PaymentMode.BANK_TRANSFER, PaymentMode.CHEQUE).forEach { m ->
              DropdownMenuItem(
                text = { Text(m.displayTitle) },
                onClick = {
                  paymentMode = m
                  modeExpanded = false
                },
              )
            }
          }
        }

        if (paymentMode != PaymentMode.CASH) {
          OutlinedTextField(
            value = refNumber,
            onValueChange = { refNumber = it },
            label = { Text("UTR / Cheque / Reference No. *") },
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("collect_ref_input"),
          )
        }

        DdMmYyyyDateField(
          label = "Collection Date",
          value = dateStr,
          onValueChange = { dateStr = it },
          modifier = Modifier.fillMaxWidth(),
          testTagName = "collect_date_input",
        )

        OutlinedTextField(
          value = remarks,
          onValueChange = { remarks = it },
          label = { Text("Collection Remarks") },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("collect_remarks_input"),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (!submittedOnce) {
            submittedOnce = true
            onConfirmSave(
              enteredPaise,
              paymentMode,
              initialType,
              refNumber,
              remarks,
              dateStr,
              dialogIdempotencyKey,
            )
          }
        },
        enabled = !submittedOnce && enteredPaise > 0L && validDate && !refMissing,
        modifier = Modifier.testTag("confirm_save_payment_button"),
      ) {
        Text("Save & Generate Receipt")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

@Composable
fun ReversePaymentDialog(
  payment: PaymentEntity,
  onDismiss: () -> Unit,
  onConfirmReverse: (reason: String) -> Unit,
) {
  var reason by rememberSaveable(payment.id) { mutableStateOf("") }
  var submittedOnce by rememberSaveable(payment.id) { mutableStateOf(false) }
  val isValid = reason.trim().length in 3..300

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Reverse Receipt ${payment.receiptNumber}") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = "Txn ID: ${payment.transactionId} • Amount: ${IndianFinanceFormatter.formatPaise(payment.amountPaise, showZeroPaise = true)}",
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          text = "Confirmed transactions are never deleted. Reversing marks ${payment.receiptNumber} as REVERSED, records an immutable adjustment entry, and recalculates loan & installment balances.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
          value = reason,
          onValueChange = { reason = it },
          label = { Text("Reversal Reason * (min 3 chars)") },
          isError = reason.isNotEmpty() && !isValid,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("reverse_payment_reason_input"),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (!submittedOnce && isValid) {
            submittedOnce = true
            onConfirmReverse(reason.trim())
          }
        },
        enabled = !submittedOnce && isValid,
        colors = ButtonDefaults.buttonColors(containerColor = OverdueCrimson),
        modifier = Modifier.testTag("confirm_reverse_payment_button"),
      ) {
        Text("Confirm Reversal")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CorrectPaymentDialog(
  payment: PaymentEntity,
  onDismiss: () -> Unit,
  onConfirmCorrect: (
    correctedAmountPaise: Long,
    correctedMode: PaymentMode,
    correctedRef: String,
    correctedDateDdMmYyyy: String,
    reason: String,
    idempotencyKey: String,
  ) -> Unit,
) {
  val correctionIdempotencyKey = rememberSaveable(payment.id) {
    "IDEM-CORR-${UUID.randomUUID()}"
  }
  var submittedOnce by rememberSaveable(correctionIdempotencyKey) { mutableStateOf(false) }
  var amountRupees by rememberSaveable(correctionIdempotencyKey) {
    mutableStateOf(IndianFinanceFormatter.paiseToPlainRupeesString(payment.amountPaise))
  }
  var mode by rememberSaveable(correctionIdempotencyKey) { mutableStateOf(payment.paymentMode) }
  var refNumber by rememberSaveable(correctionIdempotencyKey) { mutableStateOf(payment.referenceNumber) }
  var dateStr by rememberSaveable(correctionIdempotencyKey) { mutableStateOf(payment.collectionDateDdMmYyyy) }
  var reason by rememberSaveable(correctionIdempotencyKey) { mutableStateOf("") }
  var modeExpanded by remember { mutableStateOf(false) }

  val enteredPaise = IndianFinanceFormatter.parseRupeesToPaise(amountRupees) ?: 0L
  val validDate = IndianFinanceFormatter.parseDdMmYyyyToMillis(dateStr) != null
  val refMissing = mode != PaymentMode.CASH && mode != PaymentMode.ADVANCE_ADJUSTMENT && refNumber.trim().isEmpty()
  val validReason = reason.trim().length in 3..300
  val canSubmit = !submittedOnce && enteredPaise > 0L && validDate && !refMissing && validReason

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Correct Receipt ${payment.receiptNumber}") },
    text = {
      Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.verticalScroll(rememberScrollState()),
      ) {
        Text(
          text = "Original: ${IndianFinanceFormatter.formatPaise(payment.amountPaise, showZeroPaise = true)} (${payment.paymentMode.displayTitle}) • Txn: ${payment.transactionId}",
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
        )
        Text(
          text = "Marks ${payment.receiptNumber} as CORRECTED (preserved in ledger), creates a linked replacement receipt, and recalculates installment allocations.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
          value = amountRupees,
          onValueChange = { amountRupees = it },
          label = { Text("Corrected Amount (₹) *") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("correct_payment_amount_input"),
        )

        ExposedDropdownMenuBox(
          expanded = modeExpanded,
          onExpandedChange = { modeExpanded = !modeExpanded },
        ) {
          OutlinedTextField(
            value = mode.displayTitle,
            onValueChange = {},
            readOnly = true,
            label = { Text("Corrected Payment Mode") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modeExpanded) },
            modifier = Modifier
              .menuAnchor(MenuAnchorType.PrimaryNotEditable)
              .fillMaxWidth(),
          )
          ExposedDropdownMenu(
            expanded = modeExpanded,
            onDismissRequest = { modeExpanded = false },
          ) {
            listOf(PaymentMode.CASH, PaymentMode.UPI, PaymentMode.BANK_TRANSFER, PaymentMode.CHEQUE).forEach { m ->
              DropdownMenuItem(
                text = { Text(m.displayTitle) },
                onClick = {
                  mode = m
                  modeExpanded = false
                },
              )
            }
          }
        }

        if (mode != PaymentMode.CASH && mode != PaymentMode.ADVANCE_ADJUSTMENT) {
          OutlinedTextField(
            value = refNumber,
            onValueChange = { refNumber = it },
            label = { Text("UTR / Cheque / Reference No. *") },
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("correct_payment_ref_input"),
          )
        }

        DdMmYyyyDateField(
          label = "Corrected Collection Date",
          value = dateStr,
          onValueChange = { dateStr = it },
          modifier = Modifier.fillMaxWidth(),
          testTagName = "correct_payment_date_input",
        )

        OutlinedTextField(
          value = reason,
          onValueChange = { reason = it },
          label = { Text("Correction Reason * (min 3 chars)") },
          isError = reason.isNotEmpty() && !validReason,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("correct_payment_reason_input"),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (canSubmit) {
            submittedOnce = true
            onConfirmCorrect(
              enteredPaise,
              mode,
              refNumber.trim(),
              dateStr,
              reason.trim(),
              correctionIdempotencyKey,
            )
          }
        },
        enabled = canSubmit,
        modifier = Modifier.testTag("confirm_correct_payment_button"),
      ) {
        Text("Save Correction Receipt")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettlePreClosureDialog(
  request: PreClosureRequestEntity,
  onDismiss: () -> Unit,
  onConfirm: (PaymentMode, String, String) -> Unit,
) {
  var mode by rememberSaveable { mutableStateOf(PaymentMode.CASH) }
  var ref by rememberSaveable { mutableStateOf("") }
  var remarks by rememberSaveable { mutableStateOf("Final pre-closure settlement received") }
  var expanded by remember { mutableStateOf(false) }

  val canSettle = mode == PaymentMode.CASH || ref.trim().isNotEmpty()

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Settle Pre-Closure ${request.requestCode}") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          "Settlement Payable: ${IndianFinanceFormatter.formatPaise(request.netSettlementPaise, showZeroPaise = true)}",
          style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily),
          color = MaterialTheme.colorScheme.primary,
        )
        ExposedDropdownMenuBox(
          expanded = expanded,
          onExpandedChange = { expanded = !expanded },
        ) {
          OutlinedTextField(
            value = mode.displayTitle,
            onValueChange = {},
            readOnly = true,
            label = { Text("Settlement Mode") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
              .menuAnchor(MenuAnchorType.PrimaryNotEditable)
              .fillMaxWidth(),
          )
          ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf(PaymentMode.CASH, PaymentMode.UPI, PaymentMode.BANK_TRANSFER, PaymentMode.CHEQUE).forEach { m ->
              DropdownMenuItem(
                text = { Text(m.displayTitle) },
                onClick = {
                  mode = m
                  expanded = false
                },
              )
            }
          }
        }
        if (mode != PaymentMode.CASH) {
          OutlinedTextField(
            value = ref,
            onValueChange = { ref = it },
            label = { Text("UTR / Cheque Ref *") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
        }
        OutlinedTextField(
          value = remarks,
          onValueChange = { remarks = it },
          label = { Text("Approval Remarks") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onConfirm(mode, ref, remarks) },
        enabled = canSettle,
      ) {
        Text("Approve & Close Loan")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

@Composable
fun RejectPreClosureDialog(
  request: PreClosureRequestEntity,
  onDismiss: () -> Unit,
  onConfirmReject: (String) -> Unit,
) {
  var remarks by rememberSaveable(request.id) { mutableStateOf("") }
  val isValid = remarks.trim().length >= 3

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Reject Pre-Closure ${request.requestCode}") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = "Rejecting returns the loan to Active status and records this decision in pre-closure history.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
          value = remarks,
          onValueChange = { remarks = it },
          label = { Text("Rejection Remarks *") },
          modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onConfirmReject(remarks.trim()) },
        enabled = isValid,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
      ) {
        Text("Reject Request")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

@Composable
private fun DisburseMetricRow(label: String, value: String) {
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
