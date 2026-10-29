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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.CustomerEntity
import com.example.data.local.EmployeeEntity
import com.example.data.local.InstallmentScheduleEntity
import com.example.data.local.LoanProductEntity
import com.example.domain.model.InstallmentStatus
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
  activeProducts: List<LoanProductEntity>,
  preselectedCustomerId: String?,
  isSaving: Boolean = false,
  onBack: () -> Unit,
  onRegisterNewCustomer: () -> Unit,
  onOpenLoanProducts: () -> Unit,
  onSeedStandardProducts: () -> Unit = {},
  onSubmitLoan: (
    customerId: String,
    loanProductId: String,
    disbursementDateDdMmYyyy: String,
    assignedAgentId: String,
    disburseImmediately: Boolean,
    disbursementMode: PaymentMode,
    disbursementReference: String,
  ) -> Unit,
) {
  BackHandler(onBack = onBack)

  val isOwner = actor.role == UserRole.OWNER
  val canRegisterCustomer = isOwner || actor.canRegisterCustomer
  val canApprove = isOwner || actor.canApproveLoan

  var selectedCustomerId by rememberSaveable(preselectedCustomerId) {
    mutableStateOf(preselectedCustomerId ?: customers.firstOrNull()?.id.orEmpty())
  }
  LaunchedEffect(customers, preselectedCustomerId) {
    if (!preselectedCustomerId.isNullOrBlank() && customers.any { it.id == preselectedCustomerId }) {
      selectedCustomerId = preselectedCustomerId
    } else if (selectedCustomerId.isBlank() && customers.isNotEmpty()) {
      selectedCustomerId = customers.first().id
    }
  }

  val availablePrincipals = remember(activeProducts) {
    activeProducts.map { it.principalPaise }.distinct().sorted()
  }

  var selectedPrincipalPaise by rememberSaveable {
    mutableLongStateOf(availablePrincipals.firstOrNull() ?: 0L)
  }
  var frequency by rememberSaveable {
    mutableStateOf(activeProducts.firstOrNull()?.repaymentFrequency ?: RepaymentFrequency.WEEKLY)
  }
  var selectedProductId by rememberSaveable {
    mutableStateOf(activeProducts.firstOrNull()?.id.orEmpty())
  }

  // Keep selections synchronized as activeProducts stream arrives or changes
  LaunchedEffect(activeProducts) {
    if (activeProducts.isNotEmpty()) {
      if (selectedPrincipalPaise !in availablePrincipals) {
        selectedPrincipalPaise = availablePrincipals.first()
      }
      val forPrincipalAndFreq = activeProducts.filter {
        it.principalPaise == selectedPrincipalPaise && it.repaymentFrequency == frequency
      }
      if (forPrincipalAndFreq.isEmpty()) {
        val firstForPrincipal = activeProducts.firstOrNull { it.principalPaise == selectedPrincipalPaise }
        if (firstForPrincipal != null) {
          frequency = firstForPrincipal.repaymentFrequency
          selectedProductId = firstForPrincipal.id
        } else {
          val firstAny = activeProducts.first()
          selectedPrincipalPaise = firstAny.principalPaise
          frequency = firstAny.repaymentFrequency
          selectedProductId = firstAny.id
        }
      } else if (forPrincipalAndFreq.none { it.id == selectedProductId }) {
        selectedProductId = forPrincipalAndFreq.minBy { it.durationCount }.id
      }
    }
  }

  val matchingDurationProducts = remember(activeProducts, selectedPrincipalPaise, frequency) {
    activeProducts
      .filter { it.principalPaise == selectedPrincipalPaise && it.repaymentFrequency == frequency }
      .sortedWith(compareBy<LoanProductEntity> { it.durationCount }.thenBy { it.fixedInstallmentPaise })
  }

  val selectedProduct = matchingDurationProducts.find { it.id == selectedProductId }
    ?: matchingDurationProducts.firstOrNull()

  var disbursementDate by rememberSaveable { mutableStateOf(IndianFinanceFormatter.todayDdMmYyyy()) }
  var disburseImmediately by rememberSaveable { mutableStateOf(canApprove) }
  var disbursementMode by rememberSaveable { mutableStateOf(PaymentMode.CASH) }
  var disbursementReference by rememberSaveable { mutableStateOf("") }

  val selectedCustomer = customers.find { it.id == selectedCustomerId }
  var selectedAgentId by rememberSaveable(selectedCustomerId) {
    mutableStateOf(selectedCustomer?.assignedAgentId ?: actor.id)
  }

  var custDropdownExpanded by remember { mutableStateOf(false) }
  var agentDropdownExpanded by remember { mutableStateOf(false) }
  var disbModeDropdownExpanded by remember { mutableStateOf(false) }

  val liveQuote = selectedProduct?.let { prod ->
    LoanScheduleCalculator.calculateFixedProductQuote(
      principalPaise = prod.principalPaise,
      fixedInstallmentPaise = prod.fixedInstallmentPaise,
      totalInstallments = prod.totalInstallments,
      frequency = prod.repaymentFrequency,
      disbursementDateDdMmYyyy = disbursementDate,
      processingFeePaise = prod.processingFeePaise,
    )
  }

  val validationError = when {
    selectedCustomer == null -> "Select an existing customer or register a new customer."
    activeProducts.isEmpty() -> "No active Fixed Loan Products configured yet."
    selectedProduct == null -> "No active ${frequency.displayTitle} product configured for ${IndianFinanceFormatter.formatPaise(selectedPrincipalPaise)}."
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
        actions = {
          if (isOwner) {
            TextButton(
              onClick = onOpenLoanProducts,
              modifier = Modifier.testTag("manage_products_topbar_btn"),
            ) {
              Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(4.dp))
              Text("Products")
            }
          }
        },
      )
    },
  ) { innerPadding ->
    Column(
      verticalArrangement = Arrangement.spacedBy(14.dp),
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
    ) {
      // STEP 1: Select an existing customer OR register a new customer
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = "1. Applicant Customer",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
            )
            if (canRegisterCustomer) {
              OutlinedButton(
                onClick = onRegisterNewCustomer,
                modifier = Modifier
                  .height(48.dp)
                  .testTag("register_customer_from_loan_btn"),
              ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Register New")
              }
            }
          }

          if (customers.isEmpty()) {
            Text(
              text = "No customers registered yet. Tap 'Register New' to add a customer and return directly to this loan form.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          } else {
            ExposedDropdownMenuBox(
              expanded = custDropdownExpanded,
              onExpandedChange = { custDropdownExpanded = !custDropdownExpanded },
            ) {
              OutlinedTextField(
                value = selectedCustomer?.let { "${it.fullName} (${it.customerCode} • ${it.mobile})" } ?: "Select Customer",
                onValueChange = {},
                readOnly = true,
                label = { Text("Select Existing Customer *") },
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
          }
        }
      }

      // Guard if no Fixed Loan Products have been configured yet
      if (activeProducts.isEmpty()) {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            EmptyLedgerState(
              icon = Icons.Default.Inventory2,
              title = "No Active Fixed Loan Products",
              subtitle = if (isOwner) {
                "Configure fixed Loan Amounts, DAILY/WEEKLY schedules, Durations, and Fixed EMI/EDI amounts before creating loans."
              } else {
                "Ask the Organization Owner to configure active Fixed Loan Products before submitting new loan applications."
              },
            )
            if (isOwner) {
              Button(
                onClick = onOpenLoanProducts,
                modifier = Modifier
                  .fillMaxWidth()
                  .height(48.dp)
                  .testTag("open_loan_products_empty_btn"),
              ) {
                Icon(Icons.Default.Inventory2, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Configure Fixed Loan Products")
              }
              OutlinedButton(
                onClick = onSeedStandardProducts,
                enabled = !isSaving,
                modifier = Modifier
                  .fillMaxWidth()
                  .height(48.dp)
                  .testTag("seed_products_from_new_loan_btn"),
              ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (isSaving) "Configuring Products…" else "Load Standard Fixed Products (₹10k / ₹25k / ₹50k)")
              }
            }
          }
        }
        return@Column
      }

      // STEP 2: Select a configured Loan Amount
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text(
            text = "2. Select Configured Loan Amount",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
          )
          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
          ) {
            availablePrincipals.forEach { principalPaise ->
              val isSelected = selectedPrincipalPaise == principalPaise
              FilterChip(
                selected = isSelected,
                onClick = {
                  selectedPrincipalPaise = principalPaise
                  val matchingForFreq = activeProducts.filter {
                    it.principalPaise == principalPaise && it.repaymentFrequency == frequency
                  }
                  if (matchingForFreq.isNotEmpty()) {
                    selectedProductId = matchingForFreq.minBy { it.durationCount }.id
                  } else {
                    val fallback = activeProducts.firstOrNull { it.principalPaise == principalPaise }
                    if (fallback != null) {
                      frequency = fallback.repaymentFrequency
                      selectedProductId = fallback.id
                    }
                  }
                },
                label = {
                  Text(
                    text = IndianFinanceFormatter.formatPaise(principalPaise),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily),
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                  )
                },
                modifier = Modifier.testTag("loan_amount_option_$principalPaise"),
              )
            }
          }

          // STEP 3: Select DAILY or WEEKLY repayment
          Text(
            text = "3. Select Repayment Frequency",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
          )
          SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            RepaymentFrequency.entries.forEachIndexed { idx, freq ->
              val hasForSelectedAmount = activeProducts.any {
                it.principalPaise == selectedPrincipalPaise && it.repaymentFrequency == freq
              }
              SegmentedButton(
                selected = frequency == freq,
                onClick = {
                  frequency = freq
                  val forCurrentPrincipal = activeProducts.filter {
                    it.principalPaise == selectedPrincipalPaise && it.repaymentFrequency == freq
                  }
                  if (forCurrentPrincipal.isNotEmpty()) {
                    selectedProductId = forCurrentPrincipal.minBy { it.durationCount }.id
                  } else {
                    val otherWithFreq = activeProducts.firstOrNull { it.repaymentFrequency == freq }
                    if (otherWithFreq != null) {
                      selectedPrincipalPaise = otherWithFreq.principalPaise
                      selectedProductId = otherWithFreq.id
                    }
                  }
                },
                shape = SegmentedButtonDefaults.itemShape(index = idx, count = RepaymentFrequency.entries.size),
                modifier = Modifier.testTag("freq_btn_${freq.name.lowercase()}"),
              ) {
                Text(
                  text = if (hasForSelectedAmount) freq.displayTitle else "${freq.displayTitle} (Other Amt)",
                  maxLines = 1,
                )
              }
            }
          }

          // STEP 4: Select an available Loan Duration
          Text(
            text = "4. Select Available Loan Duration",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
          )
          if (matchingDurationProducts.isEmpty()) {
            Text(
              text = "No active ${frequency.displayTitle} duration configured for ${IndianFinanceFormatter.formatPaise(selectedPrincipalPaise)}.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error,
            )
          } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              matchingDurationProducts.forEach { prod ->
                val isSelected = selectedProduct?.id == prod.id
                Card(
                  colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) {
                      MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    } else {
                      MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    },
                  ),
                  border = BorderStroke(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                  ),
                  modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selectedProductId = prod.id }
                    .testTag("loan_duration_option_${prod.id}"),
                ) {
                  Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(horizontal = 14.dp, vertical = 12.dp),
                  ) {
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = prod.durationLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                      )
                      Text(
                        text = "${prod.name} (${prod.productCode})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                      Text(
                        text = "Fixed ${prod.repaymentFrequency.shortLabel}: ${IndianFinanceFormatter.formatPaise(prod.fixedInstallmentPaise)}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                      )
                      Text(
                        text = "Total: ${IndianFinanceFormatter.formatPaise(prod.totalPayablePaise)}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                      )
                    }
                    if (isSelected) {
                      Spacer(Modifier.width(8.dp))
                      Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Selected Duration",
                        tint = MaterialTheme.colorScheme.primary,
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }

      // Disbursement Reference Date & Assigned Agent
      DdMmYyyyDateField(
        label = if (canApprove && disburseImmediately) "Disbursement Date" else "Proposed Disbursement Date",
        value = disbursementDate,
        onValueChange = { disbursementDate = it },
        modifier = Modifier.fillMaxWidth(),
        testTagName = "loan_disbursement_date_input",
      )

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
                    "Disburses funds & generates fixed ${frequency.displayTitle} schedule immediately"
                  } else {
                    "Saves as 'Pending Approval' preserving selected product terms"
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

      // STEPS 5, 6, 7 & 8: Auto-calculated Fixed EMI/EDI, Installment Count, Total Scheduled Repayment & Dates
      if (selectedProduct != null && liveQuote != null) {
        Card(
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("fixed_loan_quote_card"),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text = "Fixed Product Repayment Terms",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = "${selectedProduct.name} • ${selectedProduct.productCode}",
              style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

            QuoteRow(
              label = "Loan Amount",
              value = IndianFinanceFormatter.formatPaise(liveQuote.principalPaise),
            )
            if (liveQuote.processingFeePaise > 0L) {
              QuoteRow(
                label = "Processing Fee Deducted",
                value = "- ${IndianFinanceFormatter.formatPaise(liveQuote.processingFeePaise)}",
              )
              QuoteRow(
                label = "Net Disbursed Payout",
                value = IndianFinanceFormatter.formatPaise(liveQuote.netDisbursedPaise),
              )
            }
            QuoteRow(
              label = "5. Fixed ${frequency.shortLabel} Amount",
              value = IndianFinanceFormatter.formatPaise(liveQuote.fixedInstallmentPaise),
              valueTestTag = "loan_fixed_installment_display",
            )
            QuoteRow(
              label = "6. Number of Installments",
              value = "${liveQuote.totalInstallments} (${selectedProduct.durationLabel})",
              valueTestTag = "loan_installment_count_display",
            )
            Surface(
              color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
              shape = MaterialTheme.shapes.small,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
              ) {
                Text(
                  text = "7. Total Scheduled Repayment (Fixed ${frequency.shortLabel} × Installments)",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                  text = "${IndianFinanceFormatter.formatPaise(liveQuote.fixedInstallmentPaise)} × ${liveQuote.totalInstallments} = ${IndianFinanceFormatter.formatPaise(liveQuote.totalScheduledRepaymentPaise)}",
                  style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily),
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.testTag("loan_total_repayment_display"),
                )
              }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
            QuoteRow(
              label = "8. Repayment Start Date",
              value = liveQuote.repaymentStartDateDdMmYyyy,
              valueTestTag = "loan_repayment_start_date_display",
            )
            QuoteRow(
              label = "Expected Completion Date",
              value = liveQuote.expectedCompletionDateDdMmYyyy,
              valueTestTag = "loan_expected_completion_date_display",
            )
          }
        }
      }

      // STEP 9: Save selected product and its fixed repayment terms to the loan record
      Button(
        onClick = {
          val prod = selectedProduct ?: return@Button
          onSubmitLoan(
            selectedCustomerId,
            prod.id,
            disbursementDate,
            selectedAgentId,
            disburseImmediately,
            disbursementMode,
            disbursementReference.trim(),
          )
        },
        enabled = selectedCustomerId.isNotBlank() && selectedProduct != null && validationError == null && !isSaving,
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag("submit_loan_button"),
      ) {
        Text(
          when {
            isSaving -> "Saving Fixed-Term Loan…"
            canApprove && disburseImmediately -> "Approve, Disburse & Generate Fixed Schedule"
            else -> "Submit Loan Application"
          },
        )
      }
    }
  }
}

@Composable
private fun QuoteRow(
  label: String,
  value: String,
  valueTestTag: String? = null,
) {
  Row(
    horizontalArrangement = Arrangement.SpaceBetween,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Text(label, style = MaterialTheme.typography.bodySmall)
    Text(
      value,
      style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
      fontWeight = FontWeight.Bold,
      modifier = if (valueTestTag != null) Modifier.testTag(valueTestTag) else Modifier,
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
