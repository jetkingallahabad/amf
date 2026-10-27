package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.AdvanceTransactionEntity
import com.example.data.local.BranchEntity
import com.example.data.local.CustomerDocumentEntity
import com.example.data.local.CustomerEntity
import com.example.data.local.EmployeeEntity
import com.example.data.local.LoanEntity
import com.example.data.local.PaymentEntity
import com.example.data.local.PreClosureRequestEntity
import com.example.domain.model.CustomerKycValidator
import com.example.domain.model.DocumentType
import com.example.domain.model.LoanStatus
import com.example.domain.model.PaymentRecordStatus
import com.example.domain.model.SearchFilterMode
import com.example.ui.components.PaymentRecordStatusBadge
import com.example.domain.model.UserRole
import com.example.ui.components.EmptyLedgerState
import com.example.ui.components.LoanStatusBadge
import com.example.ui.components.SyncStatusBadge
import com.example.ui.theme.ConfirmedGreen
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.OverdueCrimson
import com.example.ui.viewmodel.CustomerLoanSearchHit
import com.example.util.IndianFinanceFormatter
import java.io.File

@Composable
fun CustomerSearchScreen(
  actor: EmployeeEntity,
  query: String,
  filterMode: SearchFilterMode,
  results: List<CustomerLoanSearchHit>,
  onQueryChange: (String) -> Unit,
  onFilterModeChange: (SearchFilterMode) -> Unit,
  onOpenCustomerProfile: (String) -> Unit,
  onOpenLoanDetail: (String) -> Unit,
  onRegisterCustomer: () -> Unit,
) {
  var statusSegment by rememberSaveable { mutableStateOf("ALL") }

  val filteredHits = remember(results, statusSegment) {
    when (statusSegment) {
      "ACTIVE" -> results.filter { it.activeLoansCount > 0 }
      "PENDING" -> results.filter { it.pendingOrApprovedLoansCount > 0 }
      "CLOSED" -> results.filter { it.closedLoansCount > 0 }
      else -> results
    }
  }

  Box(modifier = Modifier.fillMaxSize()) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
      Text(
        text = "Customer & Loan Directory",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
      )
      Spacer(Modifier.height(8.dp))

      OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Search Customer ID, Name, Mobile, or Loan ID…") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
        trailingIcon = {
          if (query.isNotEmpty()) {
            IconButton(onClick = { onQueryChange("") }) {
              Icon(Icons.Default.Clear, contentDescription = "Clear search")
            }
          }
        },
        singleLine = true,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("customer_search_input"),
      )

      Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState())
          .padding(top = 8.dp),
      ) {
        SearchFilterMode.entries.forEach { mode ->
          FilterChip(
            selected = filterMode == mode,
            onClick = { onFilterModeChange(mode) },
            label = { Text(mode.label) },
            modifier = Modifier.testTag("search_filter_${mode.name.lowercase()}"),
          )
        }
      }

      Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState())
          .padding(bottom = 8.dp),
      ) {
        listOf(
          "ALL" to "All Customers (${results.size})",
          "ACTIVE" to "Active Loans",
          "PENDING" to "Pending / Approved",
          "CLOSED" to "Closed History",
        ).forEach { (key, label) ->
          FilterChip(
            selected = statusSegment == key,
            onClick = { statusSegment = key },
            label = { Text(label) },
            modifier = Modifier.testTag("search_status_${key.lowercase()}"),
          )
        }
      }

      if (filteredHits.isEmpty()) {
        Spacer(Modifier.height(12.dp))
        EmptyLedgerState(
          icon = Icons.Default.Person,
          title = if (query.isBlank() && statusSegment == "ALL") {
            "No Registered Customers Yet"
          } else {
            "No Matching Records"
          },
          subtitle = if (query.isBlank() && statusSegment == "ALL") {
            "Register your first customer to create Daily EDI or Weekly EMI loan accounts."
          } else {
            "No permitted customer or active/historical loan matched your search criteria."
          },
        )
      } else {
        LazyColumn(
          contentPadding = PaddingValues(bottom = 84.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.fillMaxSize(),
        ) {
          items(filteredHits, key = { it.customer.id }) { hit ->
            CustomerSearchCard(
              hit = hit,
              onOpenProfile = { onOpenCustomerProfile(hit.customer.id) },
              onOpenLoan = onOpenLoanDetail,
            )
          }
        }
      }
    }

    if (actor.role == UserRole.OWNER || actor.canRegisterCustomer) {
      ExtendedFloatingActionButton(
        onClick = onRegisterCustomer,
        icon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
        text = { Text("Register Customer") },
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .padding(16.dp)
          .testTag("fab_register_customer"),
      )
    }
  }
}

@Composable
private fun CustomerSearchCard(
  hit: CustomerLoanSearchHit,
  onOpenProfile: () -> Unit,
  onOpenLoan: (String) -> Unit,
) {
  val customer = hit.customer
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onOpenProfile)
      .testTag("customer_card_${customer.customerCode}"),
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
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = customer.fullName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = "${customer.customerCode} • +91 ${customer.mobile}",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Column(horizontalAlignment = Alignment.End) {
          Text(
            text = IndianFinanceFormatter.formatPaise(hit.activeOutstandingPaise),
            style = MaterialTheme.typography.titleSmall.copy(fontFamily = MonoFontFamily),
            color = MaterialTheme.colorScheme.primary,
          )
          Text(
            text = "Active Due",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Text(
        text = "Branch: ${customer.branchName} • Agent: ${customer.assignedAgentName} • Active: ${hit.activeLoansCount} • Closed: ${hit.closedLoansCount}" +
          (if (hit.totalAdvanceBalancePaise > 0L) " • Adv: ${IndianFinanceFormatter.formatPaise(hit.totalAdvanceBalancePaise)}" else ""),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      if (hit.matchingLoans.isNotEmpty()) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        ) {
          hit.matchingLoans.forEach { loan ->
            AssistChip(
              onClick = { onOpenLoan(loan.id) },
              label = {
                Text(
                  "${loan.loanCode} (${loan.repaymentFrequency.shortLabel} • ${loan.status.displayTitle})",
                  style = MaterialTheme.typography.labelSmall,
                )
              },
            )
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerRegistrationScreen(
  actor: EmployeeEntity,
  branches: List<BranchEntity>,
  employees: List<EmployeeEntity>,
  isSaving: Boolean = false,
  onBack: () -> Unit,
  onSaveCustomer: (
    fullName: String,
    guardianName: String,
    mobile: String,
    altMobile: String,
    address: String,
    city: String,
    kycDocType: String,
    kycDocNumber: String,
    occupation: String,
    monthlyIncomePaise: Long,
    branchId: String,
    assignedAgentId: String,
  ) -> Unit,
) {
  BackHandler(onBack = onBack)

  var fullName by rememberSaveable { mutableStateOf("") }
  var guardianName by rememberSaveable { mutableStateOf("") }
  var mobile by rememberSaveable { mutableStateOf("") }
  var altMobile by rememberSaveable { mutableStateOf("") }
  var address by rememberSaveable { mutableStateOf("") }
  var city by rememberSaveable { mutableStateOf("") }
  var kycDocType by rememberSaveable { mutableStateOf(CustomerKycValidator.PRIMARY_KYC_OPTIONS.first()) }
  var kycDocNumber by rememberSaveable { mutableStateOf("") }
  var occupation by rememberSaveable { mutableStateOf("") }
  var monthlyIncomeRupees by rememberSaveable { mutableStateOf("") }

  var selectedBranchId by rememberSaveable { mutableStateOf(actor.branchId) }
  var selectedAgentId by rememberSaveable { mutableStateOf(actor.id) }
  var kycTypeExpanded by remember { mutableStateOf(false) }
  var branchExpanded by remember { mutableStateOf(false) }
  var agentExpanded by remember { mutableStateOf(false) }

  val branchAgents = employees.filter { it.isActive && it.branchId == selectedBranchId }
    .ifEmpty { listOf(actor) }

  val mobileValidationMsg = remember(mobile) {
    if (mobile.isEmpty()) null
    else runCatching { CustomerKycValidator.validateIndianMobile(mobile, "Primary mobile") }.exceptionOrNull()?.message
  }
  val altMobileValidationMsg = remember(altMobile, mobile) {
    if (altMobile.isBlank()) null
    else if (altMobile == mobile) "Alternate mobile must differ from primary mobile."
    else runCatching { CustomerKycValidator.validateIndianMobile(altMobile, "Alternate mobile") }.exceptionOrNull()?.message
  }
  val kycValidationMsg = remember(kycDocType, kycDocNumber) {
    if (kycDocNumber.isEmpty()) null
    else runCatching { CustomerKycValidator.normalizeAndValidateKyc(kycDocType, kycDocNumber) }.exceptionOrNull()?.message
  }

  val isFormValid = fullName.trim().length >= 3 &&
    mobile.length == 10 && mobileValidationMsg == null &&
    altMobileValidationMsg == null &&
    address.trim().length >= 2 &&
    kycDocNumber.isNotBlank() && kycValidationMsg == null &&
    !isSaving

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Customer Registration") },
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
      Text(
        text = "A collision-safe Customer ID (CUST-XXXX-XXXX) will be assigned automatically upon registration.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      OutlinedTextField(
        value = fullName,
        onValueChange = { fullName = it },
        label = { Text("Customer Full Name *") },
        isError = fullName.isNotEmpty() && fullName.trim().length < 3,
        singleLine = true,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("reg_customer_name_input"),
      )

      OutlinedTextField(
        value = guardianName,
        onValueChange = { guardianName = it },
        label = { Text("Father / Spouse Name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )

      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = mobile,
          onValueChange = { mobile = it.filter { ch -> ch.isDigit() }.take(10) },
          label = { Text("Mobile (10 digits) *") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
          isError = mobileValidationMsg != null,
          singleLine = true,
          modifier = Modifier
            .weight(1f)
            .testTag("reg_customer_mobile_input"),
        )
        OutlinedTextField(
          value = altMobile,
          onValueChange = { altMobile = it.filter { ch -> ch.isDigit() }.take(10) },
          label = { Text("Alt Mobile") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
          isError = altMobileValidationMsg != null,
          singleLine = true,
          modifier = Modifier.weight(1f),
        )
      }
      val phoneIssue = mobileValidationMsg ?: altMobileValidationMsg
      if (phoneIssue != null) {
        Text(
          text = phoneIssue,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
        )
      }

      OutlinedTextField(
        value = address,
        onValueChange = { address = it },
        label = { Text("Residential / Shop Address *") },
        isError = address.isNotEmpty() && address.trim().length < 2,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("reg_customer_address_input"),
      )

      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = city,
          onValueChange = { city = it },
          label = { Text("City / Area") },
          singleLine = true,
          modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
          value = occupation,
          onValueChange = { occupation = it },
          label = { Text("Occupation / Trade") },
          singleLine = true,
          modifier = Modifier.weight(1f),
        )
      }

      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ExposedDropdownMenuBox(
          expanded = kycTypeExpanded,
          onExpandedChange = { kycTypeExpanded = !kycTypeExpanded },
          modifier = Modifier.weight(0.95f),
        ) {
          OutlinedTextField(
            value = kycDocType,
            onValueChange = {},
            readOnly = true,
            label = { Text("Primary KYC Type") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = kycTypeExpanded) },
            singleLine = true,
            modifier = Modifier
              .menuAnchor(MenuAnchorType.PrimaryNotEditable)
              .fillMaxWidth(),
          )
          ExposedDropdownMenu(
            expanded = kycTypeExpanded,
            onDismissRequest = { kycTypeExpanded = false },
          ) {
            CustomerKycValidator.PRIMARY_KYC_OPTIONS.forEach { option ->
              DropdownMenuItem(
                text = { Text(option) },
                onClick = {
                  kycDocType = option
                  kycTypeExpanded = false
                },
              )
            }
          }
        }
        OutlinedTextField(
          value = kycDocNumber,
          onValueChange = { kycDocNumber = it.uppercase() },
          label = { Text("KYC ID Number *") },
          isError = kycValidationMsg != null,
          singleLine = true,
          modifier = Modifier
            .weight(1.05f)
            .testTag("reg_customer_kyc_input"),
        )
      }
      if (kycValidationMsg != null) {
        Text(
          text = kycValidationMsg,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
        )
      }

      OutlinedTextField(
        value = monthlyIncomeRupees,
        onValueChange = { monthlyIncomeRupees = it },
        label = { Text("Estimated Monthly Income (₹)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )

      if (actor.role == UserRole.OWNER && branches.size > 1) {
        val currentBranchName = branches.find { it.id == selectedBranchId }?.name ?: actor.branchName
        ExposedDropdownMenuBox(
          expanded = branchExpanded,
          onExpandedChange = { branchExpanded = !branchExpanded },
        ) {
          OutlinedTextField(
            value = currentBranchName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Assigned Branch") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = branchExpanded) },
            modifier = Modifier
              .menuAnchor(MenuAnchorType.PrimaryNotEditable)
              .fillMaxWidth(),
          )
          ExposedDropdownMenu(
            expanded = branchExpanded,
            onDismissRequest = { branchExpanded = false },
          ) {
            branches.forEach { b ->
              DropdownMenuItem(
                text = { Text("${b.name} (${b.city})") },
                onClick = {
                  selectedBranchId = b.id
                  branchExpanded = false
                },
              )
            }
          }
        }
      }

      if (actor.role != UserRole.COLLECTION_AGENT && branchAgents.isNotEmpty()) {
        val selectedAgentName = branchAgents.find { it.id == selectedAgentId }?.let {
          "${it.fullName} (${it.role.displayTitle})"
        } ?: actor.fullName
        ExposedDropdownMenuBox(
          expanded = agentExpanded,
          onExpandedChange = { agentExpanded = !agentExpanded },
        ) {
          OutlinedTextField(
            value = selectedAgentName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Assigned Collection Agent / Officer") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = agentExpanded) },
            modifier = Modifier
              .menuAnchor(MenuAnchorType.PrimaryNotEditable)
              .fillMaxWidth(),
          )
          ExposedDropdownMenu(
            expanded = agentExpanded,
            onDismissRequest = { agentExpanded = false },
          ) {
            branchAgents.forEach { emp ->
              DropdownMenuItem(
                text = { Text("${emp.fullName} (${emp.employeeCode} • ${emp.role.displayTitle})") },
                onClick = {
                  selectedAgentId = emp.id
                  agentExpanded = false
                },
              )
            }
          }
        }
      }

      Spacer(Modifier.height(6.dp))
      Button(
        onClick = {
          val incomePaise = IndianFinanceFormatter.parseRupeesToPaise(monthlyIncomeRupees) ?: 0L
          onSaveCustomer(
            fullName,
            guardianName,
            mobile,
            altMobile,
            address,
            city,
            kycDocType,
            kycDocNumber,
            occupation,
            incomePaise,
            selectedBranchId,
            selectedAgentId,
          )
        },
        enabled = isFormValid,
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag("save_customer_button"),
      ) {
        Text(if (isSaving) "Saving Customer…" else "Save Customer Record")
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerProfileScreen(
  actor: EmployeeEntity,
  customer: CustomerEntity?,
  loans: List<LoanEntity>,
  documents: List<CustomerDocumentEntity>,
  payments: List<PaymentEntity> = emptyList(),
  advanceLedger: List<AdvanceTransactionEntity> = emptyList(),
  preClosures: List<PreClosureRequestEntity> = emptyList(),
  onBack: () -> Unit,
  onCreateNewLoanForCustomer: (String) -> Unit,
  onOpenLoanDetail: (String) -> Unit,
  onUploadDocumentUri: (DocumentType, String, android.net.Uri) -> Unit,
  onUpdateCustomerProfile: (
    fullName: String,
    guardianName: String,
    mobile: String,
    altMobile: String,
    address: String,
    city: String,
    occupation: String,
    monthlyIncomePaise: Long,
  ) -> Unit = { _, _, _, _, _, _, _, _ -> },
) {
  BackHandler(onBack = onBack)

  var showUploadDialog by rememberSaveable { mutableStateOf(false) }
  var showEditDialog by rememberSaveable { mutableStateOf(false) }
  var selectedDocForPreview by remember { mutableStateOf<CustomerDocumentEntity?>(null) }
  var pendingDocType by rememberSaveable { mutableStateOf(DocumentType.AADHAAR_CARD) }
  var pendingDocNumber by rememberSaveable { mutableStateOf("") }
  var selectedSectionTab by rememberSaveable { mutableIntStateOf(0) }
  var loanFilter by rememberSaveable { mutableStateOf("ALL") }

  val photoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia(),
  ) { uri ->
    if (uri != null) {
      onUploadDocumentUri(pendingDocType, pendingDocNumber, uri)
      showUploadDialog = false
    }
  }

  val filePickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument(),
  ) { uri ->
    if (uri != null) {
      onUploadDocumentUri(pendingDocType, pendingDocNumber, uri)
      showUploadDialog = false
    }
  }

  val canEditCustomer = actor.role == UserRole.OWNER || actor.canRegisterCustomer

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(customer?.fullName ?: "Customer Profile") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        actions = {
          if (customer != null && canEditCustomer) {
            IconButton(
              onClick = { showEditDialog = true },
              modifier = Modifier.testTag("profile_edit_customer_button"),
            ) {
              Icon(Icons.Default.Edit, contentDescription = "Edit Customer Profile")
            }
          }
        },
      )
    },
  ) { innerPadding ->
    if (customer == null) {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding),
      ) {
        Text("Customer record not found.")
      }
      return@Scaffold
    }

    val activeLoans = loans.filter { it.status.isActiveCollectible }
    val pendingOrApprovedLoans = loans.filter {
      it.status == LoanStatus.PENDING_APPROVAL || it.status == LoanStatus.APPROVED
    }
    val closedHistoricalLoans = loans.filter { it.status.isHistoricalClosed }
    val totalRepaidPaise = loans.sumOf { it.paidAmountPaise }
    val totalActiveDuePaise = activeLoans.sumOf { it.outstandingPaise }
    val totalAdvancePaise = loans.sumOf { it.advanceBalancePaise }

    val displayedLoans = when (loanFilter) {
      "ACTIVE" -> activeLoans
      "PENDING" -> pendingOrApprovedLoans
      "CLOSED" -> closedHistoricalLoans
      else -> loans
    }

    LazyColumn(
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .testTag("customer_profile_screen"),
    ) {
      // 1. Customer KYC & Contact Summary
      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Row(
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                text = customer.customerCode,
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
              )
              Text(
                text = "Registered: ${customer.createdDateDdMmYyyy}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Text(customer.fullName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (customer.guardianName.isNotBlank()) {
              Text("S/o, D/o, W/o: ${customer.guardianName}", style = MaterialTheme.typography.bodySmall)
            }
            Text(
              "Mobile: +91 ${customer.mobile}" +
                (if (customer.alternateMobile.isNotBlank()) " • Alt: +91 ${customer.alternateMobile}" else ""),
              style = MaterialTheme.typography.bodyMedium,
            )
            Text("Address: ${customer.address}, ${customer.city}", style = MaterialTheme.typography.bodySmall)
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text(
              "KYC: ${customer.kycDocumentType} — ${customer.kycDocumentNumber}",
              style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
            )
            Text(
              "Occupation: ${customer.occupation.ifBlank { "N/A" }} • Monthly Income: ${IndianFinanceFormatter.formatPaise(customer.monthlyIncomePaise)}",
              style = MaterialTheme.typography.bodySmall,
            )
            Text(
              "Branch: ${customer.branchName} • Assigned Agent: ${customer.assignedAgentName}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              if (actor.role == UserRole.OWNER || actor.canCreateLoan) {
                Button(
                  onClick = { onCreateNewLoanForCustomer(customer.id) },
                  modifier = Modifier
                    .weight(1f)
                    .testTag("profile_new_loan_button"),
                ) {
                  Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(Modifier.width(6.dp))
                  Text("New Loan")
                }
              }
              if (actor.role == UserRole.OWNER || actor.canUploadDocuments) {
                FilledTonalButton(
                  onClick = {
                    pendingDocNumber = customer.kycDocumentNumber
                    showUploadDialog = true
                  },
                  modifier = Modifier
                    .weight(1f)
                    .testTag("profile_upload_doc_button"),
                ) {
                  Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(Modifier.width(6.dp))
                  Text("Upload KYC")
                }
              }
            }
          }
        }
      }

      // 2. Customer Multi-Loan Portfolio Summary
      item {
        Card(
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
          ),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text = "Portfolio & Lifetime Repayment Summary",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
            )
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
              PortfolioStat("Total Loans", "${loans.size} (${activeLoans.size} Active • ${closedHistoricalLoans.size} Closed)")
              PortfolioStat("Active Due", IndianFinanceFormatter.formatPaise(totalActiveDuePaise), alignEnd = true)
            }
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
              PortfolioStat("Total Repaid", IndianFinanceFormatter.formatPaise(totalRepaidPaise))
              PortfolioStat("Advance Balance", IndianFinanceFormatter.formatPaise(totalAdvancePaise), alignEnd = true)
            }
          }
        }
      }

      // 3. Profile Section Tabs (Loans, Payments, Advance & Pre-Closures, KYC Docs)
      item {
        val sectionTabs = listOf(
          "Loans (${loans.size})",
          "Payments (${payments.size})",
          "Advance & Closure (${advanceLedger.size + preClosures.size})",
          "KYC Docs (${documents.size})",
        )
        ScrollableTabRow(
          selectedTabIndex = selectedSectionTab,
          edgePadding = 0.dp,
        ) {
          sectionTabs.forEachIndexed { idx, title ->
            Tab(
              selected = selectedSectionTab == idx,
              onClick = { selectedSectionTab = idx },
              text = { Text(title) },
              modifier = Modifier.testTag("profile_tab_$idx"),
            )
          }
        }
      }

      when (selectedSectionTab) {
        0 -> {
          item {
            Row(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            ) {
              listOf(
                "ALL" to "All (${loans.size})",
                "ACTIVE" to "Active (${activeLoans.size})",
                "PENDING" to "Pending / Approved (${pendingOrApprovedLoans.size})",
                "CLOSED" to "Closed History (${closedHistoricalLoans.size})",
              ).forEach { (k, label) ->
                FilterChip(
                  selected = loanFilter == k,
                  onClick = { loanFilter = k },
                  label = { Text(label) },
                  modifier = Modifier.testTag("profile_loan_filter_${k.lowercase()}"),
                )
              }
            }
          }

          if (displayedLoans.isEmpty()) {
            item {
              EmptyLedgerState(
                icon = Icons.Default.Description,
                title = if (loans.isEmpty()) "No Loans Issued Yet" else "No Loans in This Filter",
                subtitle = if (loans.isEmpty()) {
                  "Create a Daily EDI or Weekly EMI loan application for ${customer.fullName}. Multiple concurrent or sequential loans are supported."
                } else {
                  "Switch loan filter above to view active or closed historical accounts."
                },
              )
            }
          } else {
            items(displayedLoans, key = { it.id }) { loan ->
              val progress = if (loan.totalPayablePaise > 0L) {
                (loan.paidAmountPaise.toFloat() / loan.totalPayablePaise.toFloat()).coerceIn(0f, 1f)
              } else {
                0f
              }
              Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable { onOpenLoanDetail(loan.id) }
                  .testTag("history_loan_card_${loan.loanCode}"),
              ) {
                Column(
                  modifier = Modifier.padding(14.dp),
                  verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                  ) {
                    Text(
                      text = "${loan.loanCode} • ${loan.repaymentFrequency.displayTitle}",
                      style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily),
                      fontWeight = FontWeight.Bold,
                    )
                    LoanStatusBadge(status = loan.status)
                  }
                  Text(
                    text = "${loan.productType.displayTitle} • Principal: ${IndianFinanceFormatter.formatPaise(loan.principalPaise)} • ${loan.totalInstallments} × ${IndianFinanceFormatter.formatPaise(loan.installmentAmountPaise)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  Text(
                    text = "Disbursed: ${loan.disbursementDateDdMmYyyy} (${loan.disbursementMode.displayTitle}) • Maturity: ${loan.maturityDateDdMmYyyy}" +
                      (if (loan.closedDateDdMmYyyy.isNotBlank()) " • Closed: ${loan.closedDateDdMmYyyy}" else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  if (loan.status == LoanStatus.REJECTED && loan.rejectionReason.isNotBlank()) {
                    Text(
                      text = "Rejection Reason: ${loan.rejectionReason}",
                      style = MaterialTheme.typography.bodySmall,
                      color = OverdueCrimson,
                    )
                  } else {
                    LinearProgressIndicator(
                      progress = { progress },
                      modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    )
                    Row(
                      horizontalArrangement = Arrangement.SpaceBetween,
                      modifier = Modifier.fillMaxWidth(),
                    ) {
                      Text(
                        text = "Paid: ${IndianFinanceFormatter.formatPaise(loan.paidAmountPaise)} / ${IndianFinanceFormatter.formatPaise(loan.totalPayablePaise)}",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                      )
                      Text(
                        text = "Bal: ${IndianFinanceFormatter.formatPaise(loan.outstandingPaise)}",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                      )
                    }
                  }
                }
              }
            }
          }
        }

        1 -> {
          if (payments.isEmpty()) {
            item {
              EmptyLedgerState(
                icon = Icons.Default.Payments,
                title = "No Payment Receipts Recorded",
                subtitle = "All EMI/EDI receipts, advance deposits, and pre-closure settlements across ${customer.fullName}'s loans appear here.",
              )
            }
          } else {
            items(payments, key = { it.id }) { p ->
              Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable { onOpenLoanDetail(p.loanId) },
              ) {
                Column(
                  modifier = Modifier.padding(14.dp),
                  verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                  Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                  ) {
                    Text(
                      text = IndianFinanceFormatter.formatPaise(p.amountPaise, showZeroPaise = true),
                      style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily),
                      fontWeight = FontWeight.Bold,
                      color = if (p.recordStatus == PaymentRecordStatus.ACTIVE) {
                        MaterialTheme.colorScheme.primary
                      } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                      },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                      if (p.recordStatus != PaymentRecordStatus.ACTIVE) {
                        PaymentRecordStatusBadge(recordStatus = p.recordStatus)
                      }
                      SyncStatusBadge(syncStatus = p.syncStatus)
                    }
                  }
                  Text(
                    text = "${p.receiptNumber} • Txn: ${p.transactionId}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                    fontWeight = FontWeight.SemiBold,
                  )
                  Text(
                    text = "Loan: ${p.loanCode} • ${p.paymentMode.displayTitle} • ${p.paymentType.displayTitle} • ${p.collectionDateDdMmYyyy}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          }
        }

        2 -> {
          if (advanceLedger.isEmpty() && preClosures.isEmpty()) {
            item {
              EmptyLedgerState(
                icon = Icons.Default.History,
                title = "No Advance or Pre-Closure History",
                subtitle = "Advance payment credits/adjustments and pre-closure requests across all loans are permanently archived here.",
              )
            }
          } else {
            if (preClosures.isNotEmpty()) {
              item {
                Text(
                  text = "Pre-Closure History (${preClosures.size})",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                )
              }
              items(preClosures, key = { "pcr_${it.id}" }) { req ->
                Card(
                  colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                  border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                  modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenLoanDetail(req.loanId) },
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
                        text = "${req.requestCode} • Loan ${req.loanCode}",
                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = MonoFontFamily),
                        fontWeight = FontWeight.Bold,
                      )
                      Text(
                        text = req.status.displayTitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                      )
                    }
                    Text(
                      text = "Settlement: ${IndianFinanceFormatter.formatPaise(req.netSettlementPaise)} (Rebate: ${IndianFinanceFormatter.formatPaise(req.proposedRebatePaise)}) • Requested: ${req.requestedDateDdMmYyyy}",
                      style = MaterialTheme.typography.bodySmall,
                    )
                    if (!req.reviewRemarks.isNullOrBlank()) {
                      Text(
                        text = "Review (${req.reviewedDateDdMmYyyy ?: ""}): ${req.reviewRemarks}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                  }
                }
              }
            }

            if (advanceLedger.isNotEmpty()) {
              item {
                Text(
                  text = "Advance Payment Ledger (${advanceLedger.size})",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                )
              }
              items(advanceLedger, key = { "adv_${it.id}" }) { tx ->
                val isCredit = tx.entryType.startsWith("CREDIT")
                Card(
                  colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                  border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                  modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenLoanDetail(tx.loanId) },
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
                      Text(
                        text = "${if (isCredit) "+" else "-"}${IndianFinanceFormatter.formatPaise(tx.amountPaise)}",
                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = MonoFontFamily),
                        color = if (isCredit) ConfirmedGreen else MaterialTheme.colorScheme.primary,
                      )
                      Text(
                        text = "Bal: ${IndianFinanceFormatter.formatPaise(tx.balanceAfterPaise)}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                      )
                    }
                  }
                }
              }
            }
          }
        }

        3 -> {
          if (documents.isEmpty()) {
            item {
              EmptyLedgerState(
                icon = Icons.Default.AttachFile,
                title = "No KYC Documents Attached",
                subtitle = "Tap 'Upload KYC' above to securely attach Aadhaar, PAN, Voter ID, Driving Licence, Photo, or Signed Loan Agreements.",
              )
            }
          } else {
            items(documents, key = { it.id }) { doc ->
              Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable { selectedDocForPreview = doc }
                  .testTag("customer_doc_card_${doc.id}"),
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.padding(12.dp),
                ) {
                  if (doc.mimeType.startsWith("image/") && File(doc.localFilePath).exists()) {
                    AsyncImage(
                      model = File(doc.localFilePath),
                      contentDescription = doc.documentType.displayTitle,
                      contentScale = ContentScale.Crop,
                      modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    )
                  } else {
                    Icon(
                      imageVector = Icons.Default.Description,
                      contentDescription = doc.documentType.displayTitle,
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(44.dp),
                    )
                  }
                  Spacer(Modifier.width(12.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = "${doc.documentType.displayTitle}${if (doc.documentNumber.isNotBlank()) " • ${doc.documentNumber}" else ""}",
                      style = MaterialTheme.typography.titleSmall,
                      fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                      text = "${doc.fileName} (${(doc.fileSizeBytes / 1024L).coerceAtLeast(1L)} KB) • Uploaded ${doc.uploadedDateDdMmYyyy}",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                      text = "SHA-256: ${doc.sha256Checksum.take(16)}… • Tap to verify integrity",
                      style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                      color = MaterialTheme.colorScheme.primary,
                    )
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  if (showEditDialog && customer != null) {
    EditCustomerProfileDialog(
      customer = customer,
      onDismiss = { showEditDialog = false },
      onSaveProfile = { name, guardian, mob, altMob, addr, city, occ, incomePaise ->
        showEditDialog = false
        onUpdateCustomerProfile(name, guardian, mob, altMob, addr, city, occ, incomePaise)
      },
    )
  }

  if (showUploadDialog && customer != null) {
    UploadCustomerDocumentDialog(
      initialDocumentNumber = pendingDocNumber,
      onDismiss = { showUploadDialog = false },
      onSelectPhoto = { dt, docNum ->
        pendingDocType = dt
        pendingDocNumber = docNum
        photoPickerLauncher.launch(
          PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
      },
      onSelectFile = { dt, docNum ->
        pendingDocType = dt
        pendingDocNumber = docNum
        filePickerLauncher.launch(arrayOf("application/pdf", "image/*"))
      },
    )
  }

  val previewDoc = selectedDocForPreview
  if (previewDoc != null) {
    DocumentPreviewAndIntegrityDialog(
      doc = previewDoc,
      onDismiss = { selectedDocForPreview = null },
    )
  }
}

@Composable
private fun PortfolioStat(label: String, value: String, alignEnd: Boolean = false) {
  Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = value,
      style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily),
      fontWeight = FontWeight.Bold,
    )
  }
}
