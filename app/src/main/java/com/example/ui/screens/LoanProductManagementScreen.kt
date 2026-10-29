package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.local.EmployeeEntity
import com.example.data.local.LoanProductEntity
import com.example.domain.model.FixedLoanProductValidator
import com.example.domain.model.RepaymentFrequency
import com.example.domain.model.UserRole
import com.example.ui.components.EmptyLedgerState
import com.example.ui.theme.ConfirmedGreen
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.OverdueCrimson
import com.example.util.IndianFinanceFormatter

private enum class ProductFilter(val label: String) {
  ALL("All"),
  ACTIVE("Active"),
  INACTIVE("Inactive"),
  DAILY("Daily EDI"),
  WEEKLY("Weekly EMI"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoanProductManagementScreen(
  actor: EmployeeEntity,
  products: List<LoanProductEntity>,
  isSaving: Boolean,
  onBack: () -> Unit,
  onCreateProduct: (
    name: String,
    principalPaise: Long,
    frequency: RepaymentFrequency,
    durationCount: Int,
    fixedInstallmentPaise: Long,
    processingFeePaise: Long,
  ) -> Unit,
  onUpdateProduct: (
    productId: String,
    name: String,
    principalPaise: Long,
    frequency: RepaymentFrequency,
    durationCount: Int,
    fixedInstallmentPaise: Long,
    processingFeePaise: Long,
    isActive: Boolean,
  ) -> Unit,
  onToggleProductActive: (productId: String, isActive: Boolean) -> Unit,
  onSeedStandardProducts: () -> Unit,
) {
  BackHandler(onBack = onBack)

  val isOwner = actor.role == UserRole.OWNER
  var selectedFilter by rememberSaveable { mutableStateOf(ProductFilter.ALL) }
  var showEditorDialog by rememberSaveable { mutableStateOf(false) }
  var editingProductId by rememberSaveable { mutableStateOf<String?>(null) }

  val editingProduct = products.find { it.id == editingProductId }

  val filteredProducts = products.filter { p ->
    when (selectedFilter) {
      ProductFilter.ALL -> true
      ProductFilter.ACTIVE -> p.isActive
      ProductFilter.INACTIVE -> !p.isActive
      ProductFilter.DAILY -> p.repaymentFrequency == RepaymentFrequency.DAILY
      ProductFilter.WEEKLY -> p.repaymentFrequency == RepaymentFrequency.WEEKLY
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text("Fixed Loan Products")
            Text(
              text = "${products.count { it.isActive }} Active • ${products.size} Total Configured",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        },
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.testTag("loan_products_back_btn")) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
      )
    },
    floatingActionButton = {
      if (isOwner) {
        ExtendedFloatingActionButton(
          onClick = {
            editingProductId = null
            showEditorDialog = true
          },
          icon = { Icon(Icons.Default.Add, contentDescription = "Add Fixed Product") },
          text = { Text("Add Fixed Product") },
          modifier = Modifier.testTag("add_loan_product_fab"),
        )
      }
    },
  ) { innerPadding ->
    LazyColumn(
      contentPadding = PaddingValues(
        start = 16.dp,
        end = 16.dp,
        top = 12.dp,
        bottom = 96.dp,
      ),
      verticalArrangement = Arrangement.spacedBy(12.dp),
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
    ) {
      // Governance & Immutability Notice Card
      item {
        Card(
          colors = CardDefaults.cardColors(
            containerColor = if (isOwner) {
              MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            } else {
              MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            },
          ),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
          ) {
            Icon(
              imageVector = if (isOwner) Icons.Default.Inventory2 else Icons.Default.Lock,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(22.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(
                text = if (isOwner) {
                  "Owner Fixed Repayment Catalog (Zero % Interest Formula)"
                } else {
                  "Read-Only Product Catalog (Owner Managed)"
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
              )
              Text(
                text = "Each product defines a fixed Loan Amount, DAILY or WEEKLY schedule, Duration, and Fixed EMI/EDI. Every sanctioned loan permanently snapshots its approved product terms—editing or deactivating a product never alters existing loans.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }

      // Filter Chips
      item {
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
          ProductFilter.entries.forEach { filter ->
            FilterChip(
              selected = selectedFilter == filter,
              onClick = { selectedFilter = filter },
              label = { Text(filter.label) },
              modifier = Modifier.testTag("product_filter_${filter.name.lowercase()}"),
            )
          }
        }
      }

      if (products.isEmpty()) {
        item {
          EmptyLedgerState(
            icon = Icons.Default.Inventory2,
            title = "No Fixed Loan Products Configured",
            subtitle = if (isOwner) {
              "Add your custom fixed EMI/EDI schemes (e.g., ₹10,000 • 12 Weeks • ₹1,000 EMI) or load standard editable templates below."
            } else {
              "Ask the Organization Owner to configure active Fixed Loan Products before submitting new loan applications."
            },
          )
        }
        if (isOwner) {
          item {
            OutlinedButton(
              onClick = onSeedStandardProducts,
              enabled = !isSaving,
              modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("seed_standard_products_btn"),
            ) {
              Icon(Icons.Default.AutoAwesome, contentDescription = null)
              Spacer(Modifier.width(8.dp))
              Text(if (isSaving) "Saving Templates…" else "Load Standard Fixed Products (₹10k / ₹25k / ₹50k)")
            }
          }
        }
      } else if (filteredProducts.isEmpty()) {
        item {
          EmptyLedgerState(
            icon = Icons.Default.Inventory2,
            title = "No Matching Products",
            subtitle = "No fixed loan products match the '${selectedFilter.label}' filter.",
          )
        }
      } else {
        items(filteredProducts, key = { it.id }) { product ->
          LoanProductCard(
            product = product,
            isOwner = isOwner,
            isSaving = isSaving,
            onEdit = {
              editingProductId = product.id
              showEditorDialog = true
            },
            onToggleActive = { active ->
              onToggleProductActive(product.id, active)
            },
          )
        }
      }
    }
  }

  if (showEditorDialog && isOwner) {
    FixedLoanProductEditorDialog(
      existing = editingProduct,
      isSaving = isSaving,
      onDismiss = {
        showEditorDialog = false
        editingProductId = null
      },
      onSave = { name, principalPaise, freq, duration, fixedInstPaise, feePaise, isActive ->
        if (editingProduct == null) {
          onCreateProduct(name, principalPaise, freq, duration, fixedInstPaise, feePaise)
        } else {
          onUpdateProduct(
            editingProduct.id,
            name,
            principalPaise,
            freq,
            duration,
            fixedInstPaise,
            feePaise,
            isActive,
          )
        }
        showEditorDialog = false
        editingProductId = null
      },
    )
  }
}

@Composable
private fun LoanProductCard(
  product: LoanProductEntity,
  isOwner: Boolean,
  isSaving: Boolean,
  onEdit: () -> Unit,
  onToggleActive: (Boolean) -> Unit,
) {
  Card(
    colors = CardDefaults.cardColors(
      containerColor = if (product.isActive) {
        MaterialTheme.colorScheme.surface
      } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
      },
    ),
    border = BorderStroke(
      1.dp,
      if (product.isActive) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
    ),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("loan_product_card_${product.id}"),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = product.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = "${product.productCode} • ${product.repaymentFrequency.displayTitle}",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        ProductStatusBadge(isActive = product.isActive)
      }

      HorizontalDivider()

      Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
      ) {
        ProductMetricItem(
          label = "Loan Amount",
          value = IndianFinanceFormatter.formatPaise(product.principalPaise),
        )
        ProductMetricItem(
          label = "Duration",
          value = product.durationLabel,
        )
        ProductMetricItem(
          label = "Fixed ${product.repaymentFrequency.shortLabel}",
          value = IndianFinanceFormatter.formatPaise(product.fixedInstallmentPaise),
        )
      }

      Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
          Text(
            text = "Total Scheduled Repayment (${IndianFinanceFormatter.formatPaise(product.fixedInstallmentPaise)} × ${product.totalInstallments})",
            style = MaterialTheme.typography.bodySmall,
          )
          Text(
            text = IndianFinanceFormatter.formatPaise(product.totalPayablePaise),
            style = MaterialTheme.typography.titleSmall.copy(fontFamily = MonoFontFamily),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
          )
        }
      }

      if (product.processingFeePaise > 0L) {
        Text(
          text = "Upfront Processing Fee: ${IndianFinanceFormatter.formatPaise(product.processingFeePaise)} (Net Payout: ${IndianFinanceFormatter.formatPaise((product.principalPaise - product.processingFeePaise).coerceAtLeast(0L))})",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      if (isOwner) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.fillMaxWidth(),
        ) {
          OutlinedButton(
            onClick = onEdit,
            enabled = !isSaving,
            modifier = Modifier
              .weight(1f)
              .height(48.dp)
              .testTag("edit_product_btn_${product.id}"),
          ) {
            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Edit Terms")
          }
          OutlinedButton(
            onClick = { onToggleActive(!product.isActive) },
            enabled = !isSaving,
            modifier = Modifier
              .weight(1f)
              .height(48.dp)
              .testTag("toggle_product_btn_${product.id}"),
          ) {
            Icon(
              imageVector = if (product.isActive) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(if (product.isActive) "Deactivate" else "Activate")
          }
        }
      }
    }
  }
}

@Composable
private fun ProductMetricItem(label: String, value: String) {
  Column {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = value,
      style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily),
      fontWeight = FontWeight.SemiBold,
    )
  }
}

@Composable
private fun ProductStatusBadge(isActive: Boolean) {
  val accentColor = if (isActive) ConfirmedGreen else OverdueCrimson
  Surface(
    color = accentColor.copy(alpha = 0.14f),
    shape = MaterialTheme.shapes.extraSmall,
  ) {
    Text(
      text = if (isActive) "ACTIVE" else "INACTIVE",
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.Bold,
      color = accentColor,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FixedLoanProductEditorDialog(
  existing: LoanProductEntity?,
  isSaving: Boolean,
  onDismiss: () -> Unit,
  onSave: (
    name: String,
    principalPaise: Long,
    frequency: RepaymentFrequency,
    durationCount: Int,
    fixedInstallmentPaise: Long,
    processingFeePaise: Long,
    isActive: Boolean,
  ) -> Unit,
) {
  var principalRupees by rememberSaveable(existing?.id) {
    mutableStateOf(
      existing?.let { (it.principalPaise / 100L).toString() } ?: "10000",
    )
  }
  var frequency by rememberSaveable(existing?.id) {
    mutableStateOf(existing?.repaymentFrequency ?: RepaymentFrequency.WEEKLY)
  }
  var durationStr by rememberSaveable(existing?.id) {
    mutableStateOf(existing?.durationCount?.toString() ?: "12")
  }
  var fixedInstallmentRupees by rememberSaveable(existing?.id) {
    mutableStateOf(
      existing?.let { (it.fixedInstallmentPaise / 100L).toString() } ?: "1000",
    )
  }
  var processingFeeRupees by rememberSaveable(existing?.id) {
    mutableStateOf(
      existing?.let { (it.processingFeePaise / 100L).toString() } ?: "0",
    )
  }
  var customName by rememberSaveable(existing?.id) {
    mutableStateOf(existing?.name.orEmpty())
  }
  var isActive by rememberSaveable(existing?.id) {
    mutableStateOf(existing?.isActive ?: true)
  }

  val principalPaise = IndianFinanceFormatter.parseRupeesToPaise(principalRupees) ?: 0L
  val durationCount = durationStr.toIntOrNull() ?: 0
  val fixedInstallmentPaise = IndianFinanceFormatter.parseRupeesToPaise(fixedInstallmentRupees) ?: 0L
  val processingFeePaise = if (processingFeeRupees.isBlank()) {
    0L
  } else {
    IndianFinanceFormatter.parseRupeesToPaise(processingFeeRupees) ?: -1L
  }

  val validationError = FixedLoanProductValidator.validationErrorOrNull(
    rawName = customName,
    principalPaise = principalPaise,
    repaymentFrequency = frequency,
    durationCount = durationCount,
    fixedInstallmentPaise = fixedInstallmentPaise,
    processingFeePaise = processingFeePaise,
  )

  val computedTotalPaise = if (fixedInstallmentPaise > 0L && durationCount > 0) {
    runCatching { Math.multiplyExact(fixedInstallmentPaise, durationCount.toLong()) }.getOrNull()
  } else {
    null
  }

  val autoGeneratedName = if (principalPaise > 0L && durationCount > 0 && fixedInstallmentPaise > 0L) {
    FixedLoanProductValidator.buildDefaultProductName(
      principalPaise = principalPaise,
      repaymentFrequency = frequency,
      durationCount = durationCount,
      fixedInstallmentPaise = fixedInstallmentPaise,
    )
  } else {
    ""
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(if (existing == null) "Configure Fixed Loan Product" else "Edit Fixed Loan Product")
    },
    text = {
      Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
      ) {
        OutlinedTextField(
          value = principalRupees,
          onValueChange = { principalRupees = it },
          label = { Text("Loan Amount (₹) *") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("product_principal_input"),
        )

        // Quick Loan Amount Chips
        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
          listOf("5000", "10000", "25000", "50000", "100000").forEach { amt ->
            AssistChip(
              onClick = { principalRupees = amt },
              label = { Text("₹$amt") },
            )
          }
        }

        Text("Repayment Frequency *", style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
          RepaymentFrequency.entries.forEachIndexed { idx, freq ->
            SegmentedButton(
              selected = frequency == freq,
              onClick = { frequency = freq },
              shape = SegmentedButtonDefaults.itemShape(index = idx, count = RepaymentFrequency.entries.size),
              modifier = Modifier.testTag("product_freq_${freq.name.lowercase()}"),
            ) {
              Text(freq.displayTitle)
            }
          }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = durationStr,
            onValueChange = { durationStr = it.filter { ch -> ch.isDigit() } },
            label = {
              Text(if (frequency == RepaymentFrequency.DAILY) "Duration (Days) *" else "Duration (Weeks) *")
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier
              .weight(1f)
              .testTag("product_duration_input"),
          )
          OutlinedTextField(
            value = fixedInstallmentRupees,
            onValueChange = { fixedInstallmentRupees = it },
            label = { Text("Fixed ${frequency.shortLabel} (₹) *") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier
              .weight(1f)
              .testTag("product_installment_input"),
          )
        }

        OutlinedTextField(
          value = processingFeeRupees,
          onValueChange = { processingFeeRupees = it },
          label = { Text("Processing Fee (₹, optional)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("product_fee_input"),
        )

        OutlinedTextField(
          value = customName,
          onValueChange = { customName = it },
          label = { Text("Product Label (optional)") },
          placeholder = { Text(autoGeneratedName.ifBlank { "Auto-generated from terms" }) },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("product_name_input"),
        )

        if (existing != null) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("Product Active for New Loans", style = MaterialTheme.typography.bodyMedium)
            Switch(
              checked = isActive,
              onCheckedChange = { isActive = it },
              modifier = Modifier.testTag("product_active_switch"),
            )
          }
        }

        if (computedTotalPaise != null && durationCount > 0) {
          Card(
            colors = CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
            ),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier.padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Text(
                text = "Auto-Calculated Product Summary",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
              )
              Text(
                text = "Installments: $durationCount (${frequency.shortLabel})",
                style = MaterialTheme.typography.bodySmall,
              )
              Text(
                text = "Total Scheduled Repayment: ${IndianFinanceFormatter.formatPaise(fixedInstallmentPaise)} × $durationCount = ${IndianFinanceFormatter.formatPaise(computedTotalPaise)}",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                fontWeight = FontWeight.Bold,
              )
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
      }
    },
    confirmButton = {
      Button(
        onClick = {
          onSave(
            customName.trim().ifBlank { autoGeneratedName },
            principalPaise,
            frequency,
            durationCount,
            fixedInstallmentPaise,
            processingFeePaise.coerceAtLeast(0L),
            isActive,
          )
        },
        enabled = validationError == null && !isSaving,
        modifier = Modifier.testTag("save_loan_product_btn"),
      ) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(if (existing == null) "Save Product" else "Update Product")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    },
  )
}
