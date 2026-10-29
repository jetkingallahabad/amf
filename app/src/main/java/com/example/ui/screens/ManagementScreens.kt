package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.local.BranchEntity
import com.example.data.local.EmployeeEntity
import com.example.data.local.EmployeeInviteEntity
import com.example.data.local.TeamEntity
import com.example.domain.model.RolePermissionTemplate
import com.example.domain.model.UserRole
import com.example.ui.components.RoleBadge
import com.example.ui.theme.ConfirmedGreen
import com.example.ui.theme.ConfirmedGreenContainer
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.PendingSyncAmber
import com.example.ui.theme.PendingSyncAmberContainer

@Composable
fun ManagementHubScreen(
  actor: EmployeeEntity,
  pendingSyncCount: Int,
  activeProductCount: Int = 0,
  onOpenLoanProducts: () -> Unit,
  onOpenEmployees: () -> Unit,
  onOpenTeams: () -> Unit,
  onOpenSyncQueue: () -> Unit,
  onOpenAuditLogs: () -> Unit,
  onSignOut: () -> Unit,
) {
  LazyColumn(
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
    modifier = Modifier
      .fillMaxSize()
      .testTag("management_hub_screen"),
  ) {
    item {
      Text(
        text = "Organization, Cloud Sync & Governance",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
      )
      Text(
        text = "Signed in as ${actor.fullName} (${actor.employeeCode}) • ${actor.role.displayTitle}" +
          (actor.email.takeIf { it.isNotBlank() }?.let { " • $it" } ?: ""),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    item {
      HubMenuCard(
        title = "Fixed Loan Products (Daily EDI & Weekly EMI)",
        subtitle = if (actor.role == UserRole.OWNER) {
          "$activeProductCount active fixed scheme(s) • Add, edit, activate, or deactivate fixed EMI/EDI products"
        } else {
          "$activeProductCount active fixed scheme(s) • Owner-managed fixed repayment catalog"
        },
        icon = Icons.Default.Inventory2,
        onClick = onOpenLoanProducts,
        tag = "hub_loan_products_card",
      )
    }

    item {
      HubMenuCard(
        title = "Offline Sync Queue & Cloud Firestore Status",
        subtitle = "$pendingSyncCount local collection receipt(s) awaiting Cloud Firestore confirmation",
        icon = Icons.Default.CloudSync,
        onClick = onOpenSyncQueue,
        tag = "hub_sync_queue_card",
      )
    }

    item {
      HubMenuCard(
        title = "Employee & Role Management",
        subtitle = if (actor.role == UserRole.OWNER) {
          "Authorize Branch Managers & Collection Agents by Google email and configure permissions"
        } else {
          "Owner-restricted module • View your assigned profile & permissions"
        },
        icon = if (actor.role == UserRole.OWNER) Icons.Default.Badge else Icons.Default.Lock,
        onClick = onOpenEmployees,
        tag = "hub_employees_card",
      )
    }

    if (actor.role == UserRole.OWNER || actor.canManageTeams) {
      item {
        HubMenuCard(
          title = "Branches & Field Collection Teams",
          subtitle = "Manage branches, collection beats, and team assignments",
          icon = Icons.Default.Groups,
          onClick = onOpenTeams,
          tag = "hub_teams_card",
        )
      }
    }

    if (actor.role == UserRole.OWNER || actor.canViewAuditLogs) {
      item {
        HubMenuCard(
          title = "Financial & Security Audit Logs",
          subtitle = "Chronological immutable trail of logins, loans, collections, and role updates",
          icon = Icons.Default.HistoryEdu,
          onClick = onOpenAuditLogs,
          tag = "hub_audit_logs_card",
        )
      }
    }

    item {
      Spacer(Modifier.height(8.dp))
      OutlinedButton(
        onClick = onSignOut,
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .testTag("hub_sign_out_button"),
      ) {
        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Sign Out (${actor.employeeCode})")
      }
    }
  }
}

@Composable
private fun HubMenuCard(
  title: String,
  subtitle: String,
  icon: ImageVector,
  onClick: () -> Unit,
  tag: String,
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .testTag(tag),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(16.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = title,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(28.dp),
      )
      Spacer(Modifier.width(14.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamManagementScreen(
  actor: EmployeeEntity,
  branches: List<BranchEntity>,
  teams: List<TeamEntity>,
  employees: List<EmployeeEntity>,
  onBack: () -> Unit,
  onCreateBranch: (name: String, city: String, address: String) -> Unit,
  onCreateTeam: (name: String, branchId: String, managerId: String?, areaBeat: String) -> Unit,
) {
  BackHandler(onBack = onBack)

  var showBranchDialog by rememberSaveable { mutableStateOf(false) }
  var showTeamDialog by rememberSaveable { mutableStateOf(false) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Branches & Teams") },
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
        .padding(innerPadding),
    ) {
      item {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          if (actor.role == UserRole.OWNER) {
            Button(
              onClick = { showBranchDialog = true },
              modifier = Modifier
                .weight(1f)
                .testTag("add_branch_button"),
            ) {
              Icon(Icons.Default.Add, contentDescription = null)
              Spacer(Modifier.width(4.dp))
              Text("New Branch")
            }
          }
          if (actor.role == UserRole.OWNER || actor.canManageTeams) {
            FilledTonalButton(
              onClick = { showTeamDialog = true },
              modifier = Modifier
                .weight(1f)
                .testTag("add_team_button"),
            ) {
              Icon(Icons.Default.Groups, contentDescription = null)
              Spacer(Modifier.width(4.dp))
              Text("New Team / Beat")
            }
          }
        }
      }

      item {
        Text("Branches (${branches.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
      }
      items(branches, key = { it.id }) { branch ->
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Text(
              "${branch.name} (${branch.branchCode})",
              style = MaterialTheme.typography.titleSmall.copy(fontFamily = MonoFontFamily),
              fontWeight = FontWeight.Bold,
            )
            Text(
              "${branch.city} • ${branch.address}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      item {
        Spacer(Modifier.height(4.dp))
        Text("Field Collection Teams (${teams.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
      }
      if (teams.isEmpty()) {
        item {
          Text(
            "No collection teams created yet. Create a Team to organize collection beats.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      } else {
        items(teams, key = { it.id }) { team ->
          val memberCount = employees.count { it.teamId == team.id }
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.padding(14.dp)) {
              Text(
                "${team.name} (${team.teamCode})",
                style = MaterialTheme.typography.titleSmall.copy(fontFamily = MonoFontFamily),
                fontWeight = FontWeight.Bold,
              )
              Text("Branch: ${team.branchName} • Lead: ${team.managerName}", style = MaterialTheme.typography.bodySmall)
              Text(
                "Collection Beat: ${team.areaBeat} • Assigned Staff: $memberCount",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }
  }

  if (showBranchDialog) {
    var name by rememberSaveable { mutableStateOf("") }
    var city by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    AlertDialog(
      onDismissRequest = { showBranchDialog = false },
      title = { Text("Create New Branch") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Branch Name *") }, singleLine = true)
          OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text("City *") }, singleLine = true)
          OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Address") })
        }
      },
      confirmButton = {
        Button(onClick = {
          onCreateBranch(name, city, address)
          showBranchDialog = false
        }) { Text("Save Branch") }
      },
      dismissButton = { TextButton(onClick = { showBranchDialog = false }) { Text("Cancel") } },
    )
  }

  if (showTeamDialog) {
    var teamName by rememberSaveable { mutableStateOf("") }
    var areaBeat by rememberSaveable { mutableStateOf("") }
    var selectedBranchId by rememberSaveable { mutableStateOf(actor.branchId) }
    var branchExpanded by remember { mutableStateOf(false) }

    AlertDialog(
      onDismissRequest = { showTeamDialog = false },
      title = { Text("Create Field Collection Team") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          if (actor.role == UserRole.OWNER && branches.size > 1) {
            ExposedDropdownMenuBox(expanded = branchExpanded, onExpandedChange = { branchExpanded = !branchExpanded }) {
              OutlinedTextField(
                value = branches.find { it.id == selectedBranchId }?.name ?: actor.branchName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Branch") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = branchExpanded) },
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
              )
              ExposedDropdownMenu(expanded = branchExpanded, onDismissRequest = { branchExpanded = false }) {
                branches.forEach { b ->
                  DropdownMenuItem(
                    text = { Text(b.name) },
                    onClick = {
                      selectedBranchId = b.id
                      branchExpanded = false
                    },
                  )
                }
              }
            }
          }
          OutlinedTextField(value = teamName, onValueChange = { teamName = it }, label = { Text("Team Name *") }, singleLine = true)
          OutlinedTextField(value = areaBeat, onValueChange = { areaBeat = it }, label = { Text("Area / Collection Beat *") })
        }
      },
      confirmButton = {
        Button(onClick = {
          onCreateTeam(teamName, selectedBranchId, actor.id, areaBeat)
          showTeamDialog = false
        }) { Text("Save Team") }
      },
      dismissButton = { TextButton(onClick = { showTeamDialog = false }) { Text("Cancel") } },
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeeManagementScreen(
  actor: EmployeeEntity,
  branches: List<BranchEntity>,
  teams: List<TeamEntity>,
  employees: List<EmployeeEntity>,
  invites: List<EmployeeInviteEntity>,
  onBack: () -> Unit,
  onCreateEmployee: (
    fullName: String,
    mobile: String,
    invitedEmail: String,
    role: UserRole,
    branchId: String,
    teamId: String?,
    teamName: String?,
    perms: RolePermissionTemplate,
  ) -> Unit,
  onRevokeInvite: (employeeCode: String) -> Unit,
  onUpdateEmployee: (
    targetEmployeeId: String,
    role: UserRole,
    branchId: String,
    teamId: String?,
    teamName: String?,
    isActive: Boolean,
    perms: RolePermissionTemplate,
  ) -> Unit,
) {
  BackHandler(onBack = onBack)

  var showCreateDialog by rememberSaveable { mutableStateOf(false) }
  var editingEmployee by remember { mutableStateOf<EmployeeEntity?>(null) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Employees & Role Permissions") },
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
        .testTag("employee_management_screen"),
    ) {
      if (actor.role == UserRole.OWNER) {
        item {
          Button(
            onClick = { showCreateDialog = true },
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp)
              .testTag("open_create_employee_button"),
          ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Authorize Employee via Google Email")
          }
        }
        item {
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = "Zero-Password Security: Enter the employee's Google email and assign their role & permissions. When they sign in with Google, Cloud Firestore rules automatically bind their account to your assigned permissions.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSecondaryContainer,
              modifier = Modifier.padding(12.dp),
            )
          }
        }
      } else {
        item {
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = "Governance Policy: Only the Owner can create employees and assign roles. Below are the staff records visible within your permission scope.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSecondaryContainer,
              modifier = Modifier.padding(14.dp),
            )
          }
        }
      }

      if (actor.role == UserRole.OWNER && invites.isNotEmpty()) {
        item {
          Text(
            text = "Provisioned Google Invitations (${invites.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
        }
        items(invites, key = { it.employeeCode }) { inv ->
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
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(inv.fullName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                  Text(
                    "${inv.employeeCode} • ${inv.invitedEmail}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                  )
                }
                RoleBadge(role = inv.role)
              }

              Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text(
                  "Branch: ${inv.branchName} • +91 ${inv.mobile}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                InviteStatusPill(inv.status)
              }

              if (inv.status == "PENDING") {
                OutlinedButton(
                  onClick = { onRevokeInvite(inv.employeeCode) },
                  modifier = Modifier
                    .align(Alignment.End)
                    .testTag("revoke_invite_${inv.employeeCode}"),
                ) {
                  Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(Modifier.width(4.dp))
                  Text("Revoke Invite")
                }
              }
            }
          }
        }
      }

      item {
        Text(
          text = "Active & Registered Staff (${employees.size})",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
      }

      items(employees, key = { it.id }) { emp ->
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
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(emp.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                  "${emp.employeeCode} • +91 ${emp.mobile}" +
                    (emp.email.takeIf { it.isNotBlank() }?.let { " • $it" } ?: "") +
                    (if (!emp.isActive) " • INACTIVE" else ""),
                  style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                  color = if (emp.isActive) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
              }
              RoleBadge(role = emp.role)
            }
            Text(
              "Branch: ${emp.branchName}" + (emp.teamName?.let { " • Team: $it" } ?: ""),
              style = MaterialTheme.typography.bodySmall,
            )
            val activePerms = buildList {
              if (emp.canRegisterCustomer) add("Register Customers")
              if (emp.canCreateLoan) add("Create Loans")
              if (emp.canApproveLoan) add("Approve Loans")
              if (emp.canCollectPayment) add("Collect Payments")
              if (emp.canManageAdvance) add("Manage Advance")
              if (emp.canRequestPreClosure) add("Request Pre-Closure")
              if (emp.canApprovePreClosure) add("Approve Pre-Closure")
              if (emp.canUploadDocuments) add("Upload Docs")
            }
            Text(
              "Permissions: ${activePerms.joinToString(", ").ifBlank { "Read Only" }}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (actor.role == UserRole.OWNER) {
              OutlinedButton(
                onClick = { editingEmployee = emp },
                modifier = Modifier.align(Alignment.End),
              ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Edit Role & Permissions")
              }
            }
          }
        }
      }
    }
  }

  if (showCreateDialog && actor.role == UserRole.OWNER) {
    EmployeeEditorDialog(
      existing = null,
      branches = branches,
      teams = teams,
      onDismiss = { showCreateDialog = false },
      onSaveCreate = { name, mob, mail, r, bId, tId, tName, perms ->
        showCreateDialog = false
        onCreateEmployee(name, mob, mail, r, bId, tId, tName, perms)
      },
      onSaveUpdate = { _, _, _, _, _, _, _ -> },
    )
  }

  val targetEdit = editingEmployee
  if (targetEdit != null && actor.role == UserRole.OWNER) {
    EmployeeEditorDialog(
      existing = targetEdit,
      branches = branches,
      teams = teams,
      onDismiss = { editingEmployee = null },
      onSaveCreate = { _, _, _, _, _, _, _, _ -> },
      onSaveUpdate = { empId, r, bId, tId, tName, active, perms ->
        editingEmployee = null
        onUpdateEmployee(empId, r, bId, tId, tName, active, perms)
      },
    )
  }
}

@Composable
private fun InviteStatusPill(status: String) {
  val (bg, fg) = when (status) {
    "CLAIMED" -> ConfirmedGreenContainer to ConfirmedGreen
    "PENDING" -> PendingSyncAmberContainer to PendingSyncAmber
    else -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
  }
  Surface(
    color = bg,
    shape = RoundedCornerShape(8.dp),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
      Icon(
        imageVector = Icons.Default.MarkEmailRead,
        contentDescription = null,
        tint = fg,
        modifier = Modifier.size(13.dp),
      )
      Spacer(Modifier.width(4.dp))
      Text(
        text = status,
        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
        color = fg,
        fontWeight = FontWeight.Bold,
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmployeeEditorDialog(
  existing: EmployeeEntity?,
  branches: List<BranchEntity>,
  teams: List<TeamEntity>,
  onDismiss: () -> Unit,
  onSaveCreate: (
    String, String, String, UserRole, String, String?, String?, RolePermissionTemplate,
  ) -> Unit,
  onSaveUpdate: (
    String, UserRole, String, String?, String?, Boolean, RolePermissionTemplate,
  ) -> Unit,
) {
  var fullName by rememberSaveable { mutableStateOf(existing?.fullName.orEmpty()) }
  var mobile by rememberSaveable { mutableStateOf(existing?.mobile.orEmpty()) }
  var invitedEmail by rememberSaveable { mutableStateOf(existing?.email.orEmpty()) }
  var role by rememberSaveable { mutableStateOf(existing?.role ?: UserRole.COLLECTION_AGENT) }
  var branchId by rememberSaveable { mutableStateOf(existing?.branchId ?: branches.firstOrNull()?.id.orEmpty()) }
  var teamId by rememberSaveable { mutableStateOf(existing?.teamId) }
  var isActive by rememberSaveable { mutableStateOf(existing?.isActive ?: true) }

  val initialTemplate = if (existing != null) {
    RolePermissionTemplate(
      canRegisterCustomer = existing.canRegisterCustomer,
      canCreateLoan = existing.canCreateLoan,
      canApproveLoan = existing.canApproveLoan,
      canCollectPayment = existing.canCollectPayment,
      canManageAdvance = existing.canManageAdvance,
      canRequestPreClosure = existing.canRequestPreClosure,
      canApprovePreClosure = existing.canApprovePreClosure,
      canUploadDocuments = existing.canUploadDocuments,
      canManageTeams = existing.canManageTeams,
      canViewReports = existing.canViewReports,
      canViewAuditLogs = existing.canViewAuditLogs,
    )
  } else {
    RolePermissionTemplate.defaultsFor(role)
  }

  var perms by remember { mutableStateOf(initialTemplate) }
  var roleExpanded by remember { mutableStateOf(false) }
  var branchExpanded by remember { mutableStateOf(false) }
  var teamExpanded by remember { mutableStateOf(false) }

  val branchTeams = teams.filter { it.branchId == branchId }
  val selectedTeamName = branchTeams.find { it.id == teamId }?.name

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (existing == null) "Authorize Employee (Google Sign-In)" else "Edit ${existing.employeeCode}") },
    text = {
      Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.verticalScroll(rememberScrollState()),
      ) {
        if (existing == null) {
          OutlinedTextField(
            value = fullName,
            onValueChange = { fullName = it },
            label = { Text("Full Name *") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("emp_name_input"),
          )
          OutlinedTextField(
            value = mobile,
            onValueChange = { mobile = it.filter { ch -> ch.isDigit() }.take(10) },
            label = { Text("10-Digit Mobile *") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("emp_mobile_input"),
          )
          OutlinedTextField(
            value = invitedEmail,
            onValueChange = { invitedEmail = it },
            label = { Text("Authorized Google Email *") },
            placeholder = { Text("staff.member@gmail.com") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("emp_email_input"),
          )
        }

        ExposedDropdownMenuBox(expanded = roleExpanded, onExpandedChange = { roleExpanded = !roleExpanded }) {
          OutlinedTextField(
            value = role.displayTitle,
            onValueChange = {},
            readOnly = true,
            label = { Text("Assigned Role") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = roleExpanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
          )
          ExposedDropdownMenu(expanded = roleExpanded, onDismissRequest = { roleExpanded = false }) {
            UserRole.entries.forEach { r ->
              DropdownMenuItem(
                text = { Text(r.displayTitle) },
                onClick = {
                  role = r
                  perms = RolePermissionTemplate.defaultsFor(r)
                  roleExpanded = false
                },
              )
            }
          }
        }

        if (branches.isNotEmpty()) {
          ExposedDropdownMenuBox(expanded = branchExpanded, onExpandedChange = { branchExpanded = !branchExpanded }) {
            OutlinedTextField(
              value = branches.find { it.id == branchId }?.name ?: "Select Branch",
              onValueChange = {},
              readOnly = true,
              label = { Text("Assigned Branch") },
              trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = branchExpanded) },
              modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = branchExpanded, onDismissRequest = { branchExpanded = false }) {
              branches.forEach { b ->
                DropdownMenuItem(
                  text = { Text(b.name) },
                  onClick = {
                    branchId = b.id
                    teamId = null
                    branchExpanded = false
                  },
                )
              }
            }
          }
        }

        if (branchTeams.isNotEmpty()) {
          ExposedDropdownMenuBox(expanded = teamExpanded, onExpandedChange = { teamExpanded = !teamExpanded }) {
            OutlinedTextField(
              value = selectedTeamName ?: "No Specific Team",
              onValueChange = {},
              readOnly = true,
              label = { Text("Assigned Team") },
              trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = teamExpanded) },
              modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = teamExpanded, onDismissRequest = { teamExpanded = false }) {
              DropdownMenuItem(
                text = { Text("No Specific Team") },
                onClick = {
                  teamId = null
                  teamExpanded = false
                },
              )
              branchTeams.forEach { t ->
                DropdownMenuItem(
                  text = { Text("${t.name} (${t.areaBeat})") },
                  onClick = {
                    teamId = t.id
                    teamExpanded = false
                  },
                )
              }
            }
          }
        }

        if (existing != null) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("Account Active", style = MaterialTheme.typography.bodyMedium)
            Switch(checked = isActive, onCheckedChange = { isActive = it })
          }
        }

        if (role != UserRole.OWNER) {
          HorizontalDivider()
          Text("Granular Access Permissions", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
          PermCheckRow("Register Customers", perms.canRegisterCustomer) {
            perms = perms.copy(canRegisterCustomer = it)
          }
          PermCheckRow("Create Loan Applications", perms.canCreateLoan) {
            perms = perms.copy(canCreateLoan = it)
          }
          PermCheckRow("Approve & Disburse Loans", perms.canApproveLoan) {
            perms = perms.copy(canApproveLoan = it)
          }
          PermCheckRow("Collect EMI/EDI Payments", perms.canCollectPayment) {
            perms = perms.copy(canCollectPayment = it)
          }
          PermCheckRow("Manage Advance Payments", perms.canManageAdvance) {
            perms = perms.copy(canManageAdvance = it)
          }
          PermCheckRow("Submit Pre-Closure Requests", perms.canRequestPreClosure) {
            perms = perms.copy(canRequestPreClosure = it)
          }
          PermCheckRow("Approve Pre-Closure Settlements", perms.canApprovePreClosure) {
            perms = perms.copy(canApprovePreClosure = it)
          }
          PermCheckRow("Upload KYC Documents", perms.canUploadDocuments) {
            perms = perms.copy(canUploadDocuments = it)
          }
          PermCheckRow("Manage Branch Teams", perms.canManageTeams) {
            perms = perms.copy(canManageTeams = it)
          }
          PermCheckRow("View Audit Logs", perms.canViewAuditLogs) {
            perms = perms.copy(canViewAuditLogs = it)
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (existing == null) {
            onSaveCreate(fullName, mobile, invitedEmail, role, branchId, teamId, selectedTeamName, perms)
          } else {
            onSaveUpdate(
              existing.id,
              role,
              branchId,
              teamId,
              selectedTeamName,
              isActive,
              perms,
            )
          }
        },
        modifier = Modifier.testTag("save_employee_button"),
      ) {
        Text(if (existing == null) "Authorize Invite" else "Save Changes")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@Composable
private fun PermCheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onChange(!checked) },
  ) {
    Checkbox(checked = checked, onCheckedChange = onChange)
    Text(label, style = MaterialTheme.typography.bodySmall)
  }
}
