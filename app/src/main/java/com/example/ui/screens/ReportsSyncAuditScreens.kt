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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.example.data.local.AuditLogEntity
import com.example.data.local.EmployeeEntity
import com.example.data.local.SyncQueueEntity
import com.example.domain.model.PaymentRecordStatus
import com.example.domain.model.SyncStatus
import com.example.domain.model.UserRole
import com.example.ui.components.DdMmYyyyDateField
import com.example.ui.components.EmptyLedgerState
import com.example.ui.components.PaymentRecordStatusBadge
import com.example.ui.components.RoleBadge
import com.example.ui.components.SyncStatusBadge
import com.example.ui.theme.ConfirmedGreen
import com.example.ui.theme.ConfirmedGreenContainer
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.PendingSyncAmber
import com.example.ui.theme.PendingSyncAmberContainer
import com.example.ui.viewmodel.CollectionReportSummary
import com.example.util.IndianFinanceFormatter

@Composable
fun CollectionReportsScreen(
  actor: EmployeeEntity,
  report: CollectionReportSummary,
  fromDate: String,
  toDate: String,
  onUpdateDateRange: (String, String) -> Unit,
) {
  val today = IndianFinanceFormatter.todayDdMmYyyy()

  LazyColumn(
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
    modifier = Modifier
      .fillMaxSize()
      .testTag("collection_reports_screen"),
  ) {
    item {
      Text(
        text = "Collection Reports",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
      )
      Text(
        text = when (actor.role) {
          UserRole.OWNER -> "Organization-wide collection ledger"
          UserRole.BRANCH_MANAGER -> "Branch report: ${actor.branchName}"
          UserRole.COLLECTION_AGENT -> "Agent report: ${actor.fullName} (${actor.employeeCode})"
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    item {
      Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
      ) {
        AssistChip(
          onClick = { onUpdateDateRange(today, today) },
          label = { Text("Today ($today)") },
        )
        AssistChip(
          onClick = {
            val (sevenAgo, _) = IndianFinanceFormatter.addDays(today, -6)
            onUpdateDateRange(sevenAgo, today)
          },
          label = { Text("Last 7 Days") },
        )
        AssistChip(
          onClick = {
            val (thirtyAgo, _) = IndianFinanceFormatter.addDays(today, -29)
            onUpdateDateRange(thirtyAgo, today)
          },
          label = { Text("Last 30 Days") },
        )
        AssistChip(
          onClick = { onUpdateDateRange("", "") },
          label = { Text("All Dates") },
        )
      }
    }

    item {
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DdMmYyyyDateField(
          label = "From",
          value = fromDate,
          onValueChange = { onUpdateDateRange(it, toDate) },
          modifier = Modifier.weight(1f),
          testTagName = "report_from_date",
        )
        DdMmYyyyDateField(
          label = "To",
          value = toDate,
          onValueChange = { onUpdateDateRange(fromDate, it) },
          modifier = Modifier.weight(1f),
          testTagName = "report_to_date",
        )
      }
    }

    item {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text("Total Collections", style = MaterialTheme.typography.labelLarge)
          Text(
            text = IndianFinanceFormatter.formatPaise(report.totalCollectedPaise, showZeroPaise = true),
            style = MaterialTheme.typography.headlineMedium.copy(fontFamily = MonoFontFamily),
            color = MaterialTheme.colorScheme.primary,
          )
          Text(
            text = "${report.totalReceiptsCount} active receipt(s)" +
              (if (report.reversedOrCorrectedCount > 0) " • ${report.reversedOrCorrectedCount} reversed/corrected (excluded from totals)" else ""),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(
              color = ConfirmedGreenContainer,
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.weight(1f),
            ) {
              Column(modifier = Modifier.padding(10.dp)) {
                Text("Cloud Confirmed", style = MaterialTheme.typography.labelSmall, color = ConfirmedGreen, fontWeight = FontWeight.Bold)
                Text(
                  IndianFinanceFormatter.formatPaise(report.serverConfirmedPaise),
                  style = MaterialTheme.typography.titleSmall.copy(fontFamily = MonoFontFamily),
                  color = ConfirmedGreen,
                )
                Text("${report.serverConfirmedCount} receipt(s)", style = MaterialTheme.typography.labelSmall, color = ConfirmedGreen)
              }
            }
            Surface(
              color = PendingSyncAmberContainer,
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.weight(1f),
            ) {
              Column(modifier = Modifier.padding(10.dp)) {
                Text("Confirmed Locally (Pending Sync)", style = MaterialTheme.typography.labelSmall, color = PendingSyncAmber, fontWeight = FontWeight.Bold)
                Text(
                  IndianFinanceFormatter.formatPaise(report.localPendingSyncPaise),
                  style = MaterialTheme.typography.titleSmall.copy(fontFamily = MonoFontFamily),
                  color = PendingSyncAmber,
                )
                Text("${report.localPendingSyncCount} receipt(s)", style = MaterialTheme.typography.labelSmall, color = PendingSyncAmber)
              }
            }
          }

          HorizontalDivider()
          ReportRow("Cash Collections", IndianFinanceFormatter.formatPaise(report.cashCollectedPaise))
          ReportRow("UPI Collections", IndianFinanceFormatter.formatPaise(report.upiCollectedPaise))
          ReportRow("NEFT / IMPS / Bank", IndianFinanceFormatter.formatPaise(report.bankTransferCollectedPaise))
          ReportRow("Cheque Collections", IndianFinanceFormatter.formatPaise(report.chequeCollectedPaise))
          HorizontalDivider()
          ReportRow("Daily EDI Loans", IndianFinanceFormatter.formatPaise(report.dailyEdiCollectedPaise))
          ReportRow("Weekly EMI Loans", IndianFinanceFormatter.formatPaise(report.weeklyEmiCollectedPaise))
          if (report.reversedOrCorrectedCount > 0) {
            HorizontalDivider()
            ReportRow(
              "Reversed / Superseded Receipts (${report.reversedOrCorrectedCount})",
              IndianFinanceFormatter.formatPaise(report.reversedAmountPaise),
            )
          }
        }
      }
    }

    if (report.agentWiseTotals.isNotEmpty()) {
      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Officer-Wise Collection Summary", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            report.agentWiseTotals.forEach { (agentName, totalPaise) ->
              ReportRow(agentName, IndianFinanceFormatter.formatPaise(totalPaise))
            }
          }
        }
      }
    }

    if (actor.role == UserRole.OWNER && report.branchWiseTotals.size > 1) {
      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Branch-Wise Collection Summary", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            report.branchWiseTotals.forEach { (branchName, totalPaise) ->
              ReportRow(branchName, IndianFinanceFormatter.formatPaise(totalPaise))
            }
          }
        }
      }
    }

    item {
      Text(
        "Receipt Ledger (${report.filteredPayments.size})",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )
    }

    if (report.filteredPayments.isEmpty()) {
      item {
        EmptyLedgerState(
          icon = Icons.Default.Assessment,
          title = "No Collections in Selected Period",
          subtitle = "Record EMI/EDI or advance payments on an active loan to generate collection reports.",
        )
      }
    } else {
      items(report.filteredPayments, key = { it.id }) { p ->
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                IndianFinanceFormatter.formatPaise(p.amountPaise, showZeroPaise = true),
                style = MaterialTheme.typography.titleSmall.copy(fontFamily = MonoFontFamily),
                fontWeight = FontWeight.Bold,
              )
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (p.recordStatus != PaymentRecordStatus.ACTIVE) {
                  PaymentRecordStatusBadge(recordStatus = p.recordStatus)
                }
                SyncStatusBadge(syncStatus = p.syncStatus)
              }
            }
            Text(
              "${p.receiptNumber} • Txn: ${p.transactionId} • ${p.customerName} (${p.loanCode})",
              style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
            )
            Text(
              "${p.collectionDateDdMmYyyy} • ${p.paymentMode.displayTitle} • By ${p.collectedByName}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ReportRow(label: String, value: String) {
  Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
    Text(label, style = MaterialTheme.typography.bodySmall)
    Text(
      value,
      style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
      fontWeight = FontWeight.SemiBold,
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncQueueScreen(
  queueItems: List<SyncQueueEntity>,
  isSyncing: Boolean,
  onBack: () -> Unit,
  onSyncNow: () -> Unit,
) {
  BackHandler(onBack = onBack)

  var statusFilter by rememberSaveable { mutableStateOf("ALL") }

  val filtered = queueItems.filter {
    when (statusFilter) {
      "PENDING" -> it.syncStatus != SyncStatus.SERVER_CONFIRMED
      "CONFIRMED" -> it.syncStatus == SyncStatus.SERVER_CONFIRMED
      else -> true
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Cloud Firestore Sync Queue") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
      )
    },
  ) { innerPadding ->
    LazyColumn(
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .testTag("sync_queue_screen"),
    ) {
      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.CloudDone,
                contentDescription = null,
                tint = ConfirmedGreen,
              )
              Spacer(Modifier.width(8.dp))
              Text(
                "Cloud Firestore + Local SQLite Queue",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
              )
            }
            Text(
              "Every collection is saved atomically in the local Room SQLite database and synchronized to Cloud Firestore via atomic WriteBatch. Entries only become 'Cloud Firestore Confirmed' after the Firestore server commits the batch.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
              onClick = onSyncNow,
              enabled = !isSyncing,
              modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("sync_now_button"),
            ) {
              if (isSyncing) {
                CircularProgressIndicator(
                  modifier = Modifier.size(20.dp),
                  color = MaterialTheme.colorScheme.onPrimary,
                  strokeWidth = 2.dp,
                )
              } else {
                Icon(Icons.Default.CloudSync, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Sync Pending Receipts to Cloud Firestore")
              }
            }
          }
        }
      }

      item {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf(
            "ALL" to "All (${queueItems.size})",
            "PENDING" to "Pending (${queueItems.count { it.syncStatus != SyncStatus.SERVER_CONFIRMED }})",
            "CONFIRMED" to "Confirmed (${queueItems.count { it.syncStatus == SyncStatus.SERVER_CONFIRMED }})",
          ).forEach { (key, label) ->
            FilterChip(
              selected = statusFilter == key,
              onClick = { statusFilter = key },
              label = { Text(label) },
            )
          }
        }
      }

      if (filtered.isEmpty()) {
        item {
          EmptyLedgerState(
            icon = Icons.Default.CloudSync,
            title = "No Sync Entries in Filter",
            subtitle = "Collected EMI/EDI receipts automatically appear in this Cloud Firestore synchronization queue.",
          )
        }
      } else {
        items(filtered, key = { it.id }) { item ->
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text(
                  IndianFinanceFormatter.formatPaise(item.amountPaise, showZeroPaise = true),
                  style = MaterialTheme.typography.titleSmall.copy(fontFamily = MonoFontFamily),
                  fontWeight = FontWeight.Bold,
                )
                SyncStatusBadge(syncStatus = item.syncStatus)
              }
              Text(
                "${item.receiptNumber} • Txn: ${item.transactionId.ifBlank { "—" }} • ${item.customerName} (${item.loanCode})",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
              )
              Text(
                "Queued: ${IndianFinanceFormatter.formatDateTimeSeconds(item.createdAtMillis)} • Attempts: ${item.attemptCount}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              if (item.serverConfirmationId != null) {
                Text(
                  "Cloud Ref: ${item.serverConfirmationId}",
                  style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                  color = ConfirmedGreen,
                )
              }
              if (!item.errorMessage.isNullOrBlank()) {
                Text(
                  "Last Sync Note: ${item.errorMessage}",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.error,
                )
              }
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditLogsScreen(
  logs: List<AuditLogEntity>,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  var selectedCategory by rememberSaveable { mutableStateOf("ALL") }

  val categories = listOf("ALL", "AUTH", "EMPLOYEE", "TEAM", "CUSTOMER", "LOAN", "PAYMENT", "ADVANCE", "PRECLOSURE", "SYNC")
  val filtered = if (selectedCategory == "ALL") {
    logs
  } else {
    logs.filter { it.category == selectedCategory }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Audit Logs (${filtered.size})") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
      )
    },
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .padding(horizontal = 16.dp)
        .testTag("audit_logs_screen"),
    ) {
      Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState())
          .padding(vertical = 8.dp),
      ) {
        categories.forEach { cat ->
          FilterChip(
            selected = selectedCategory == cat,
            onClick = { selectedCategory = cat },
            label = { Text(cat) },
          )
        }
      }

      if (filtered.isEmpty()) {
        EmptyLedgerState(
          icon = Icons.Default.HistoryEdu,
          title = "No Audit Entries",
          subtitle = "Every authentication, employee, customer, loan, and payment action is recorded here.",
        )
      } else {
        LazyColumn(
          contentPadding = PaddingValues(bottom = 24.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxSize(),
        ) {
          items(filtered, key = { it.id }) { log ->
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Text(
                    "${log.category} • ${log.action}",
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                  )
                  RoleBadge(role = log.actorRole)
                }
                Text(log.summary, style = MaterialTheme.typography.bodySmall)
                Text(
                  "Actor: ${log.actorName} • Target: ${log.targetId} • ${log.timestampDdMmYyyyHhMm}",
                  style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      }
    }
  }
}
