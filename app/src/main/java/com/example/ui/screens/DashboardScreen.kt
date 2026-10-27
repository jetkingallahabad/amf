package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AddBusiness
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.EmployeeEntity
import com.example.data.local.InstallmentScheduleEntity
import com.example.domain.model.InstallmentStatus
import com.example.domain.model.UserRole
import com.example.ui.components.KpiStatCard
import com.example.ui.components.RoleBadge
import com.example.ui.theme.ConfirmedGreen
import com.example.ui.theme.ConfirmedGreenContainer
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.OverdueCrimson
import com.example.ui.theme.PendingSyncAmber
import com.example.ui.theme.PendingSyncAmberContainer
import com.example.ui.viewmodel.DashboardMetrics
import com.example.util.IndianFinanceFormatter

@Composable
fun RoleBasedDashboardScreen(
  actor: EmployeeEntity,
  metrics: DashboardMetrics,
  schedules: List<InstallmentScheduleEntity>,
  onNavigateSearch: () -> Unit,
  onNavigateRegisterCustomer: () -> Unit,
  onNavigateNewLoan: () -> Unit,
  onNavigateSchedules: () -> Unit,
  onNavigateLoanDetail: (String) -> Unit,
  onNavigateEmployees: () -> Unit,
  onNavigateTeams: () -> Unit,
  onNavigateSyncQueue: () -> Unit,
  onNavigateAuditLogs: () -> Unit,
  onSignOut: () -> Unit,
) {
  val todayDueList = schedules.filter {
    it.dueDateDdMmYyyy == metrics.todayDateDdMmYyyy &&
      (it.status == InstallmentStatus.UPCOMING || it.status == InstallmentStatus.PARTIAL)
  }.take(8)

  LazyColumn(
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
    modifier = Modifier
      .fillMaxSize()
      .testTag("dashboard_screen"),
  ) {
    // 1. Role-Specific Hero Header
    item {
      DashboardRoleHeaderCard(
        actor = actor,
        todayDdMmYyyy = metrics.todayDateDdMmYyyy,
        onSignOut = onSignOut,
      )
    }

    // 2. Server Confirmed vs Local Pending Sync Distinction Banner
    item {
      SyncDistinctionBanner(
        serverConfirmedCount = metrics.serverConfirmedPaymentsCount,
        serverConfirmedPaise = metrics.serverConfirmedAmountPaise,
        localPendingCount = metrics.localPendingSyncCount,
        localPendingPaise = metrics.localPendingSyncAmountPaise,
        onOpenSyncQueue = onNavigateSyncQueue,
      )
    }

    // 3. Role-Tailored Quick Actions Bar
    item {
      RoleQuickActionsGrid(
        actor = actor,
        onSearch = onNavigateSearch,
        onRegisterCustomer = onNavigateRegisterCustomer,
        onNewLoan = onNavigateNewLoan,
        onSchedules = onNavigateSchedules,
        onEmployees = onNavigateEmployees,
        onTeams = onNavigateTeams,
        onAuditLogs = onNavigateAuditLogs,
      )
    }

    // 4. Role-Specific KPI Cards
    item {
      Text(
        text = when (actor.role) {
          UserRole.OWNER -> "Organization Portfolio Overview"
          UserRole.BRANCH_MANAGER -> "Branch Portfolio (${actor.branchName})"
          UserRole.COLLECTION_AGENT -> "My Assigned Collection Beat"
        },
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )
    }

    item {
      Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        KpiStatCard(
          title = "Today's Collected",
          valueText = IndianFinanceFormatter.formatPaise(metrics.todayCollectedPaise),
          subtitle = "Date: ${metrics.todayDateDdMmYyyy}",
          icon = Icons.Default.Payments,
          accentColor = ConfirmedGreen,
          modifier = Modifier.weight(1f),
        )
        KpiStatCard(
          title = "Today's EMI/EDI Due",
          valueText = IndianFinanceFormatter.formatPaise(metrics.todayScheduledDuePaise),
          subtitle = "${metrics.todayDueInstallmentsCount} installment(s)",
          icon = Icons.Default.EventRepeat,
          accentColor = MaterialTheme.colorScheme.primary,
          onClick = onNavigateSchedules,
          modifier = Modifier.weight(1f),
        )
      }
    }

    item {
      Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        KpiStatCard(
          title = "Active Outstanding",
          valueText = IndianFinanceFormatter.formatPaise(metrics.totalOutstandingPaise),
          subtitle = "${metrics.activeLoansCount} active loan(s) • ${metrics.totalCustomers} customer(s)",
          icon = Icons.Default.AccountBalanceWallet,
          accentColor = MaterialTheme.colorScheme.secondary,
          modifier = Modifier.weight(1f),
        )
        KpiStatCard(
          title = "Overdue Dues",
          valueText = IndianFinanceFormatter.formatPaise(metrics.overdueAmountPaise),
          subtitle = "${metrics.overdueInstallmentsCount} overdue installment(s)",
          icon = Icons.Default.WarningAmber,
          accentColor = OverdueCrimson,
          onClick = onNavigateSchedules,
          modifier = Modifier.weight(1f),
        )
      }
    }

    if (actor.role == UserRole.OWNER || actor.role == UserRole.BRANCH_MANAGER) {
      item {
        Row(
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          KpiStatCard(
            title = "Principal Disbursed",
            valueText = IndianFinanceFormatter.formatPaise(metrics.totalPrincipalDisbursedPaise),
            subtitle = "Advance Held: ${IndianFinanceFormatter.formatPaise(metrics.totalAdvanceHeldPaise)}",
            icon = Icons.Default.RequestQuote,
            modifier = Modifier.weight(1f),
          )
          KpiStatCard(
            title = "Pending Reviews",
            valueText = "${metrics.pendingLoanApprovalsCount + metrics.pendingPreClosuresCount}",
            subtitle = "${metrics.pendingLoanApprovalsCount} Loan(s) • ${metrics.pendingPreClosuresCount} Pre-Closure(s)",
            icon = Icons.Default.Gavel,
            accentColor = PendingSyncAmber,
            onClick = onNavigateSearch,
            modifier = Modifier.weight(1f),
          )
        }
      }
    }

    // 5. Owner Branch-Wise Performance Breakdown
    if (actor.role == UserRole.OWNER && metrics.branchBreakdown.isNotEmpty()) {
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
            Text(
              text = "Branch-Wise Portfolio Summary",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
            )
            metrics.branchBreakdown.forEach { row ->
              Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(row.branchName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                  Text(
                    "${row.activeLoans} Active Loan(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                Column(horizontalAlignment = Alignment.End) {
                  Text(
                    "Collected: ${IndianFinanceFormatter.formatPaise(row.collectedPaise)}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                    color = ConfirmedGreen,
                  )
                  Text(
                    "Due: ${IndianFinanceFormatter.formatPaise(row.outstandingPaise)}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          }
        }
      }
    }

    // 6. Today's Due Collection List
    item {
      Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          text = "Due Today (${metrics.todayDateDdMmYyyy})",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        AssistChip(
          onClick = onNavigateSchedules,
          label = { Text("View Full Schedule") },
        )
      }
    }

    if (todayDueList.isEmpty()) {
      item {
        Surface(
          color = MaterialTheme.colorScheme.surface,
          shape = RoundedCornerShape(14.dp),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = "No unpaid EMI/EDI installments scheduled for ${metrics.todayDateDdMmYyyy}. Register a customer and disburse a Daily EDI or Weekly EMI loan to track collections.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
          )
        }
      }
    } else {
      items(todayDueList, key = { it.id }) { inst ->
        val rem = (inst.scheduledAmountPaise - inst.paidAmountPaise).coerceAtLeast(0L)
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
          modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigateLoanDetail(inst.loanId) },
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
                text = "${inst.customerName} • ${inst.loanCode}",
                style = MaterialTheme.typography.titleSmall,
              )
              Text(
                text = "${inst.frequency.displayTitle} #${inst.installmentNumber} • Mobile: ${inst.customerMobile}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { onNavigateLoanDetail(inst.loanId) }) {
              Text("Collect ${IndianFinanceFormatter.formatPaise(rem)}")
            }
          }
        }
      }
    }
  }
}

@Composable
private fun DashboardRoleHeaderCard(
  actor: EmployeeEntity,
  todayDdMmYyyy: String,
  onSignOut: () -> Unit,
) {
  Card(
    shape = RoundedCornerShape(20.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .background(
          Brush.linearGradient(
            listOf(
              MaterialTheme.colorScheme.primary,
              MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
            ),
          ),
        )
        .padding(18.dp),
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
          modifier = Modifier.fillMaxWidth(),
        ) {
          RoleBadge(role = actor.role)
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = todayDdMmYyyy,
              style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
              color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
            )
            Spacer(Modifier.width(6.dp))
            IconButton(
              onClick = onSignOut,
              modifier = Modifier
                .size(40.dp)
                .testTag("sign_out_button"),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.Logout,
                contentDescription = "Sign Out",
                tint = MaterialTheme.colorScheme.onPrimary,
              )
            }
          }
        }
        Text(
          text = actor.fullName,
          style = MaterialTheme.typography.headlineSmall,
          color = MaterialTheme.colorScheme.onPrimary,
        )
        Text(
          text = "ID: ${actor.employeeCode} • Branch: ${actor.branchName}" +
            (actor.teamName?.let { " • Team: $it" } ?: ""),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
        )
      }
    }
  }
}

@Composable
private fun SyncDistinctionBanner(
  serverConfirmedCount: Int,
  serverConfirmedPaise: Long,
  localPendingCount: Int,
  localPendingPaise: Long,
  onOpenSyncQueue: () -> Unit,
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onOpenSyncQueue)
      .testTag("sync_distinction_banner"),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.CloudSync,
            contentDescription = "Sync Status",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
          )
          Spacer(Modifier.width(8.dp))
          Text(
            text = "Payment Confirmation & Sync Queue",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
          )
        }
        Text(
          text = "Manage →",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary,
        )
      }

      Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Surface(
          color = ConfirmedGreenContainer,
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.weight(1f),
        ) {
          Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                Icons.Default.CloudDone,
                contentDescription = null,
                tint = ConfirmedGreen,
                modifier = Modifier.size(15.dp),
              )
              Spacer(Modifier.width(4.dp))
              Text(
                "Server Confirmed",
                style = MaterialTheme.typography.labelSmall,
                color = ConfirmedGreen,
                fontWeight = FontWeight.Bold,
              )
            }
            Spacer(Modifier.height(4.dp))
            Text(
              text = IndianFinanceFormatter.formatPaise(serverConfirmedPaise),
              style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily),
              color = ConfirmedGreen,
            )
            Text(
              text = "$serverConfirmedCount receipt(s)",
              style = MaterialTheme.typography.labelSmall,
              color = ConfirmedGreen,
            )
          }
        }

        Surface(
          color = PendingSyncAmberContainer,
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.weight(1f),
        ) {
          Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                Icons.Default.CloudSync,
                contentDescription = null,
                tint = PendingSyncAmber,
                modifier = Modifier.size(15.dp),
              )
              Spacer(Modifier.width(4.dp))
              Text(
                "Saved Locally • Pending",
                style = MaterialTheme.typography.labelSmall,
                color = PendingSyncAmber,
                fontWeight = FontWeight.Bold,
              )
            }
            Spacer(Modifier.height(4.dp))
            Text(
              text = IndianFinanceFormatter.formatPaise(localPendingPaise),
              style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily),
              color = PendingSyncAmber,
            )
            Text(
              text = "$localPendingCount receipt(s) queued",
              style = MaterialTheme.typography.labelSmall,
              color = PendingSyncAmber,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun RoleQuickActionsGrid(
  actor: EmployeeEntity,
  onSearch: () -> Unit,
  onRegisterCustomer: () -> Unit,
  onNewLoan: () -> Unit,
  onSchedules: () -> Unit,
  onEmployees: () -> Unit,
  onTeams: () -> Unit,
  onAuditLogs: () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.fillMaxWidth(),
    ) {
      QuickActionButton(
        label = "Find / Collect",
        icon = Icons.Default.Search,
        onClick = onSearch,
        tag = "quick_action_search",
        modifier = Modifier.weight(1f),
      )
      if (actor.role == UserRole.OWNER || actor.canRegisterCustomer) {
        QuickActionButton(
          label = "New Customer",
          icon = Icons.Default.PersonAdd,
          onClick = onRegisterCustomer,
          tag = "quick_action_register_customer",
          modifier = Modifier.weight(1f),
        )
      }
      if (actor.role == UserRole.OWNER || actor.canCreateLoan) {
        QuickActionButton(
          label = "New Loan",
          icon = Icons.Default.AddBusiness,
          onClick = onNewLoan,
          tag = "quick_action_new_loan",
          modifier = Modifier.weight(1f),
        )
      }
    }

    if (actor.role == UserRole.OWNER || actor.canManageTeams || actor.canViewAuditLogs) {
      Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        if (actor.role == UserRole.OWNER) {
          QuickActionButton(
            label = "Employees",
            icon = Icons.Default.Badge,
            onClick = onEmployees,
            tag = "quick_action_employees",
            modifier = Modifier.weight(1f),
          )
        }
        if (actor.role == UserRole.OWNER || actor.canManageTeams) {
          QuickActionButton(
            label = "Teams & Beats",
            icon = Icons.Default.Groups,
            onClick = onTeams,
            tag = "quick_action_teams",
            modifier = Modifier.weight(1f),
          )
        }
        if (actor.role == UserRole.OWNER || actor.canViewAuditLogs) {
          QuickActionButton(
            label = "Audit Logs",
            icon = Icons.Default.HistoryEdu,
            onClick = onAuditLogs,
            tag = "quick_action_audit_logs",
            modifier = Modifier.weight(1f),
          )
        }
      }
    }
  }
}

@Composable
private fun QuickActionButton(
  label: String,
  icon: ImageVector,
  onClick: () -> Unit,
  tag: String,
  modifier: Modifier = Modifier,
) {
  FilledTonalButton(
    onClick = onClick,
    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
    modifier = modifier
      .height(48.dp)
      .testTag(tag),
  ) {
    Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp))
    Spacer(Modifier.width(6.dp))
    Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
  }
}
