package com.example.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.local.EmployeeEntity
import com.example.ui.components.VerifiedReceiptConfirmationDialog
import com.example.ui.screens.AccessNotProvisionedScreen
import com.example.ui.screens.AccountDeactivatedScreen
import com.example.ui.screens.AuditLogsScreen
import com.example.ui.screens.CollectionReportsScreen
import com.example.ui.screens.CustomerProfileScreen
import com.example.ui.screens.CustomerRegistrationScreen
import com.example.ui.screens.CustomerSearchScreen
import com.example.ui.screens.EmployeeManagementScreen
import com.example.ui.screens.GlobalSchedulesScreen
import com.example.ui.screens.GoogleSignInScreen
import com.example.ui.screens.LoanDetailScreen
import com.example.ui.screens.LoanProductManagementScreen
import com.example.ui.screens.ManagementHubScreen
import com.example.ui.screens.NewLoanApplicationScreen
import com.example.ui.screens.OwnerSetupScreen
import com.example.ui.screens.RoleBasedDashboardScreen
import com.example.ui.screens.SyncQueueScreen
import com.example.ui.screens.TeamManagementScreen
import com.example.domain.model.UserRole
import com.example.ui.viewmodel.AuthSessionState
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.LoanFinanceViewModel
import com.example.util.GoogleCredentialAuthHelper

object AppRoutes {
  const val DASHBOARD = "dashboard"
  const val CUSTOMERS = "customers"
  const val SCHEDULES = "schedules"
  const val REPORTS = "reports"
  const val MANAGE = "manage"

  const val REGISTER_CUSTOMER = "register_customer?returnToNewLoan={returnToNewLoan}"
  const val CUSTOMER_PROFILE = "customer_profile/{customerId}"
  const val NEW_LOAN = "new_loan?customerId={customerId}"
  const val LOAN_DETAIL = "loan_detail/{loanId}"
  const val LOAN_PRODUCTS = "loan_products"
  const val EMPLOYEES = "employees"
  const val TEAMS = "teams"
  const val SYNC_QUEUE = "sync_queue"
  const val AUDIT_LOGS = "audit_logs"

  fun registerCustomer(returnToNewLoan: Boolean = false) =
    if (returnToNewLoan) "register_customer?returnToNewLoan=true" else "register_customer"
  fun customerProfile(customerId: String) = "customer_profile/${Uri.encode(customerId)}"
  fun newLoan(customerId: String? = null) =
    if (customerId.isNullOrBlank()) "new_loan" else "new_loan?customerId=${Uri.encode(customerId)}"
  fun loanDetail(loanId: String) = "loan_detail/${Uri.encode(loanId)}"
}

private data class TopLevelDestination(
  val route: String,
  val label: String,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
  val tag: String,
)

private val TOP_LEVEL_DESTINATIONS = listOf(
  TopLevelDestination(
    route = AppRoutes.DASHBOARD,
    label = "Dashboard",
    selectedIcon = Icons.Filled.Dashboard,
    unselectedIcon = Icons.Outlined.Dashboard,
    tag = "nav_dashboard",
  ),
  TopLevelDestination(
    route = AppRoutes.CUSTOMERS,
    label = "Search",
    selectedIcon = Icons.Filled.Search,
    unselectedIcon = Icons.Outlined.Search,
    tag = "nav_customers",
  ),
  TopLevelDestination(
    route = AppRoutes.SCHEDULES,
    label = "Schedules",
    selectedIcon = Icons.Filled.EventRepeat,
    unselectedIcon = Icons.Outlined.EventRepeat,
    tag = "nav_schedules",
  ),
  TopLevelDestination(
    route = AppRoutes.REPORTS,
    label = "Reports",
    selectedIcon = Icons.Filled.Assessment,
    unselectedIcon = Icons.Outlined.Assessment,
    tag = "nav_reports",
  ),
  TopLevelDestination(
    route = AppRoutes.MANAGE,
    label = "Manage",
    selectedIcon = Icons.Filled.ManageAccounts,
    unselectedIcon = Icons.Outlined.ManageAccounts,
    tag = "nav_manage",
  ),
)

@Composable
fun LoanFinanceAppRoot(
  authViewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory),
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }

  val sessionState by authViewModel.sessionState.collectAsStateWithLifecycle()
  val isBusy by authViewModel.isBusy.collectAsStateWithLifecycle()
  val authError by authViewModel.authError.collectAsStateWithLifecycle()

  fun performFullSignOut(currentActor: EmployeeEntity?) {
    authViewModel.signOut(currentActor) {
      GoogleCredentialAuthHelper.signOut(
        credentialManager = credentialManager,
        scope = scope,
      )
    }
  }

  when (val state = sessionState) {
    AuthSessionState.Loading -> {
      Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          CircularProgressIndicator()
          Spacer(Modifier.height(12.dp))
          Text(
            text = "Connecting to Cloud Firestore…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    AuthSessionState.Unauthenticated -> {
      GoogleSignInScreen(
        isBusy = isBusy,
        errorMessage = authError,
        onSetBusy = authViewModel::setBusy,
        onClearError = authViewModel::clearError,
        onAuthSuccess = authViewModel::refreshCloudSession,
        onAuthError = authViewModel::reportAuthError,
      )
    }

    is AuthSessionState.NeedsOwnerSetup -> {
      OwnerSetupScreen(
        signedInEmail = state.email,
        suggestedFullName = state.displayName,
        isBusy = isBusy,
        errorMessage = authError,
        onCreateOwner = { orgName, fullName, mobile, email, branchName, branchCity, branchAddress ->
          authViewModel.initializeOwner(
            orgName = orgName,
            fullName = fullName,
            mobile = mobile,
            email = email,
            branchName = branchName,
            branchCity = branchCity,
            branchAddress = branchAddress,
          )
        },
        onSignOut = { performFullSignOut(null) },
      )
    }

    is AuthSessionState.AccessNotProvisioned -> {
      AccessNotProvisionedScreen(
        signedInEmail = state.email,
        isBusy = isBusy,
        errorMessage = authError,
        onRecheckAccess = authViewModel::refreshCloudSession,
        onSignOut = { performFullSignOut(null) },
      )
    }

    is AuthSessionState.AccountDeactivated -> {
      AccountDeactivatedScreen(
        employee = state.employee,
        onSignOut = { performFullSignOut(state.employee) },
      )
    }

    is AuthSessionState.Authenticated -> {
      AuthenticatedWorkspace(
        actor = state.employee,
        onSignOut = { performFullSignOut(state.employee) },
      )
    }
  }
}

@Composable
private fun AuthenticatedWorkspace(
  actor: EmployeeEntity,
  onSignOut: () -> Unit,
) {
  val vmKey = "${actor.id}_${actor.role.name}_${actor.branchId}"
  val financeVm: LoanFinanceViewModel = viewModel(
    key = vmKey,
    factory = LoanFinanceViewModel.factory(actor),
  )

  val navController = rememberNavController()
  val backStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = backStackEntry?.destination?.route

  val snackbarHostState = remember { SnackbarHostState() }
  val feedback by financeVm.feedbackMessage.collectAsStateWithLifecycle()
  val receiptConfirmation by financeVm.verifiedReceiptConfirmation.collectAsStateWithLifecycle()

  LaunchedEffect(feedback) {
    val msg = feedback
    if (!msg.isNullOrBlank()) {
      snackbarHostState.showSnackbar(msg)
      financeVm.consumeFeedback()
    }
  }

  val isInitialCloudLoading by financeVm.isInitialCloudLoading.collectAsStateWithLifecycle()
  val cloudError by financeVm.cloudError.collectAsStateWithLifecycle()
  val dashboardMetrics by financeVm.dashboardMetrics.collectAsStateWithLifecycle()
  val searchQuery by financeVm.searchQuery.collectAsStateWithLifecycle()
  val searchMode by financeVm.searchMode.collectAsStateWithLifecycle()
  val searchHits by financeVm.searchHits.collectAsStateWithLifecycle()
  val customers by financeVm.customers.collectAsStateWithLifecycle()
  val allLoanProducts by financeVm.allLoanProducts.collectAsStateWithLifecycle()
  val activeLoanProducts by financeVm.activeLoanProducts.collectAsStateWithLifecycle()
  val schedules by financeVm.schedules.collectAsStateWithLifecycle()
  val branches by financeVm.branches.collectAsStateWithLifecycle()
  val teams by financeVm.teams.collectAsStateWithLifecycle()
  val employees by financeVm.employees.collectAsStateWithLifecycle()
  val employeeInvites by financeVm.employeeInvites.collectAsStateWithLifecycle()
  val collectionReport by financeVm.collectionReport.collectAsStateWithLifecycle()
  val reportFrom by financeVm.reportFromDate.collectAsStateWithLifecycle()
  val reportTo by financeVm.reportToDate.collectAsStateWithLifecycle()
  val syncQueue by financeVm.syncQueue.collectAsStateWithLifecycle()
  val isSyncing by financeVm.isSyncing.collectAsStateWithLifecycle()
  val auditLogs by financeVm.auditLogs.collectAsStateWithLifecycle()

  val visibleTopLevelDestinations = remember(actor.id, actor.role, actor.canViewReports) {
    TOP_LEVEL_DESTINATIONS.filter { dest ->
      if (dest.route == AppRoutes.REPORTS) {
        actor.role == UserRole.OWNER || actor.canViewReports
      } else {
        true
      }
    }
  }
  val isTopLevel = visibleTopLevelDestinations.any { it.route == currentRoute }

  fun navigateTopLevel(route: String) {
    navController.navigate(route) {
      popUpTo(navController.graph.findStartDestination().id) {
        saveState = true
      }
      launchSingleTop = true
      restoreState = true
    }
  }

  fun safeNavigateBack() {
    if (navController.previousBackStackEntry != null) {
      navController.popBackStack()
    } else {
      navigateTopLevel(AppRoutes.DASHBOARD)
    }
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useNavigationRail = maxWidth >= 600.dp

    Scaffold(
      contentWindowInsets = WindowInsets.safeDrawing,
      snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
      bottomBar = {
        if (!useNavigationRail && isTopLevel) {
          NavigationBar {
            visibleTopLevelDestinations.forEach { dest ->
              val selected = currentRoute == dest.route
              NavigationBarItem(
                selected = selected,
                onClick = { navigateTopLevel(dest.route) },
                icon = {
                  Icon(
                    imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                    contentDescription = dest.label,
                  )
                },
                label = { Text(dest.label) },
                modifier = Modifier.testTag(dest.tag),
              )
            }
          }
        }
      },
    ) { innerPadding ->
      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding),
      ) {
        if (useNavigationRail && isTopLevel) {
          NavigationRail {
            visibleTopLevelDestinations.forEach { dest ->
              val selected = currentRoute == dest.route
              NavigationRailItem(
                selected = selected,
                onClick = { navigateTopLevel(dest.route) },
                icon = {
                  Icon(
                    imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                    contentDescription = dest.label,
                  )
                },
                label = { Text(dest.label) },
                modifier = Modifier.testTag(dest.tag),
              )
            }
          }
        }

        Column(modifier = Modifier.weight(1f)) {
          if (isInitialCloudLoading || isSyncing) {
            LinearProgressIndicator(
              modifier = Modifier
                .fillMaxWidth()
                .testTag("cloud_sync_loading_bar"),
            )
          }

          val activeCloudError = cloudError
          if (!activeCloudError.isNullOrBlank()) {
            CloudSyncErrorBanner(
              message = activeCloudError,
              onRetry = financeVm::retryCloudSync,
              onDismiss = financeVm::dismissCloudError,
            )
          }

          NavHost(
            navController = navController,
            startDestination = AppRoutes.DASHBOARD,
            modifier = Modifier.weight(1f),
          ) {
            composable(AppRoutes.DASHBOARD) {
              RoleBasedDashboardScreen(
                actor = actor,
                metrics = dashboardMetrics,
                schedules = schedules,
                onNavigateSearch = { navigateTopLevel(AppRoutes.CUSTOMERS) },
                onNavigateRegisterCustomer = { navController.navigate(AppRoutes.registerCustomer()) },
                onNavigateNewLoan = { navController.navigate(AppRoutes.newLoan()) },
                onNavigateLoanProducts = { navController.navigate(AppRoutes.LOAN_PRODUCTS) },
                onNavigateSchedules = { navigateTopLevel(AppRoutes.SCHEDULES) },
                onNavigateLoanDetail = { id -> navController.navigate(AppRoutes.loanDetail(id)) },
                onNavigateEmployees = { navController.navigate(AppRoutes.EMPLOYEES) },
                onNavigateTeams = { navController.navigate(AppRoutes.TEAMS) },
                onNavigateSyncQueue = { navController.navigate(AppRoutes.SYNC_QUEUE) },
                onNavigateAuditLogs = { navController.navigate(AppRoutes.AUDIT_LOGS) },
                onSignOut = onSignOut,
              )
            }

            composable(AppRoutes.CUSTOMERS) {
              CustomerSearchScreen(
                actor = actor,
                query = searchQuery,
                filterMode = searchMode,
                results = searchHits,
                onQueryChange = financeVm::updateSearchQuery,
                onFilterModeChange = financeVm::updateSearchMode,
                onOpenCustomerProfile = { id -> navController.navigate(AppRoutes.customerProfile(id)) },
                onOpenLoanDetail = { id -> navController.navigate(AppRoutes.loanDetail(id)) },
                onRegisterCustomer = { navController.navigate(AppRoutes.registerCustomer()) },
              )
            }

            composable(AppRoutes.SCHEDULES) {
              GlobalSchedulesScreen(
                schedules = schedules,
                onOpenLoanDetail = { id -> navController.navigate(AppRoutes.loanDetail(id)) },
              )
            }

            composable(AppRoutes.REPORTS) {
              CollectionReportsScreen(
                actor = actor,
                report = collectionReport,
                fromDate = reportFrom,
                toDate = reportTo,
                onUpdateDateRange = financeVm::updateReportDateRange,
              )
            }

            composable(AppRoutes.MANAGE) {
              ManagementHubScreen(
                actor = actor,
                pendingSyncCount = dashboardMetrics.localPendingSyncCount,
                activeProductCount = activeLoanProducts.size,
                onOpenLoanProducts = { navController.navigate(AppRoutes.LOAN_PRODUCTS) },
                onOpenEmployees = { navController.navigate(AppRoutes.EMPLOYEES) },
                onOpenTeams = { navController.navigate(AppRoutes.TEAMS) },
                onOpenSyncQueue = { navController.navigate(AppRoutes.SYNC_QUEUE) },
                onOpenAuditLogs = { navController.navigate(AppRoutes.AUDIT_LOGS) },
                onSignOut = onSignOut,
              )
            }

            composable(
              route = AppRoutes.REGISTER_CUSTOMER,
              arguments = listOf(
                navArgument("returnToNewLoan") {
                  type = NavType.BoolType
                  defaultValue = false
                },
              ),
            ) { entry ->
              val returnToNewLoan = entry.arguments?.getBoolean("returnToNewLoan") == true
              CustomerRegistrationScreen(
                actor = actor,
                branches = branches,
                employees = employees,
                isSaving = isSyncing,
                onBack = ::safeNavigateBack,
                onSaveCustomer = { name, guardian, mob, altMob, addr, city, kycType, kycNum, occ, inc, bId, agId ->
                  financeVm.registerCustomer(
                    fullName = name,
                    guardianName = guardian,
                    mobile = mob,
                    alternateMobile = altMob,
                    address = addr,
                    city = city,
                    kycDocType = kycType,
                    kycDocNumber = kycNum,
                    occupation = occ,
                    monthlyIncomePaise = inc,
                    branchId = bId,
                    assignedAgentId = agId,
                    onSuccess = { newCustId ->
                      if (returnToNewLoan) {
                        navController.navigate(AppRoutes.newLoan(newCustId)) {
                          popUpTo(AppRoutes.NEW_LOAN) { inclusive = true }
                          launchSingleTop = true
                        }
                      } else {
                        navController.navigate(AppRoutes.customerProfile(newCustId)) {
                          popUpTo(AppRoutes.REGISTER_CUSTOMER) { inclusive = true }
                          launchSingleTop = true
                        }
                      }
                    },
                  )
                },
              )
            }

            composable(
              route = AppRoutes.CUSTOMER_PROFILE,
              arguments = listOf(navArgument("customerId") { type = NavType.StringType }),
            ) { entry ->
              val customerId = entry.arguments?.getString("customerId").orEmpty()
              val context = LocalContext.current
              val customer by remember(customerId) { financeVm.observeCustomer(customerId) }
                .collectAsStateWithLifecycle(initialValue = null)
              val custLoans by remember(customerId) { financeVm.observeCustomerLoans(customerId) }
                .collectAsStateWithLifecycle(initialValue = emptyList())
              val custDocs by remember(customerId) { financeVm.observeCustomerDocuments(customerId) }
                .collectAsStateWithLifecycle(initialValue = emptyList())
              val custPayments by remember(customerId) { financeVm.observeCustomerPayments(customerId) }
                .collectAsStateWithLifecycle(initialValue = emptyList())
              val custAdvances by remember(customerId) { financeVm.observeCustomerAdvanceLedger(customerId) }
                .collectAsStateWithLifecycle(initialValue = emptyList())
              val custPreClosures by remember(customerId) { financeVm.observeCustomerPreClosures(customerId) }
                .collectAsStateWithLifecycle(initialValue = emptyList())

              CustomerProfileScreen(
                actor = actor,
                customer = customer,
                loans = custLoans,
                documents = custDocs,
                payments = custPayments,
                advanceLedger = custAdvances,
                preClosures = custPreClosures,
                onBack = ::safeNavigateBack,
                onCreateNewLoanForCustomer = { cId -> navController.navigate(AppRoutes.newLoan(cId)) },
                onOpenLoanDetail = { lId -> navController.navigate(AppRoutes.loanDetail(lId)) },
                onUploadDocumentUri = { docType, docNum, uri ->
                  financeVm.uploadCustomerDocument(context, customerId, docType, docNum, uri)
                },
                onUpdateCustomerProfile = { name, guardian, mob, altMob, addr, city, occ, incomePaise ->
                  financeVm.updateCustomerProfile(
                    customerId = customerId,
                    fullName = name,
                    guardianName = guardian,
                    mobile = mob,
                    alternateMobile = altMob,
                    address = addr,
                    city = city,
                    occupation = occ,
                    monthlyIncomePaise = incomePaise,
                  )
                },
              )
            }

            composable(
              route = AppRoutes.NEW_LOAN,
              arguments = listOf(
                navArgument("customerId") {
                  type = NavType.StringType
                  nullable = true
                  defaultValue = null
                },
              ),
            ) { entry ->
              val preselectedCustomerId = entry.arguments?.getString("customerId")
              NewLoanApplicationScreen(
                actor = actor,
                customers = customers,
                employees = employees,
                activeProducts = activeLoanProducts,
                preselectedCustomerId = preselectedCustomerId,
                isSaving = isSyncing,
                onBack = ::safeNavigateBack,
                onRegisterNewCustomer = {
                  navController.navigate(AppRoutes.registerCustomer(returnToNewLoan = true))
                },
                onOpenLoanProducts = {
                  navController.navigate(AppRoutes.LOAN_PRODUCTS)
                },
                onSeedStandardProducts = {
                  financeVm.seedStandardFixedProducts()
                },
                onSubmitLoan = { custId, productId, disbDate, agentId, disbNow, disbMode, disbRef ->
                  financeVm.submitLoanApplication(
                    customerId = custId,
                    loanProductId = productId,
                    disbursementDateDdMmYyyy = disbDate,
                    assignedAgentId = agentId,
                    disburseImmediately = disbNow,
                    disbursementMode = disbMode,
                    disbursementReference = disbRef,
                    onSuccess = { newLoanId ->
                      navController.navigate(AppRoutes.loanDetail(newLoanId)) {
                        popUpTo(AppRoutes.NEW_LOAN) { inclusive = true }
                        launchSingleTop = true
                      }
                    },
                  )
                },
              )
            }

            composable(AppRoutes.LOAN_PRODUCTS) {
              LoanProductManagementScreen(
                actor = actor,
                products = allLoanProducts,
                isSaving = isSyncing,
                onBack = ::safeNavigateBack,
                onCreateProduct = { name, principalPaise, freq, durationCount, fixedInstPaise, feePaise ->
                  financeVm.createLoanProduct(
                    name = name,
                    principalPaise = principalPaise,
                    repaymentFrequency = freq,
                    durationCount = durationCount,
                    fixedInstallmentPaise = fixedInstPaise,
                    processingFeePaise = feePaise,
                  )
                },
                onUpdateProduct = { productId, name, principalPaise, freq, durationCount, fixedInstPaise, feePaise, active ->
                  financeVm.updateLoanProduct(
                    productId = productId,
                    name = name,
                    principalPaise = principalPaise,
                    repaymentFrequency = freq,
                    durationCount = durationCount,
                    fixedInstallmentPaise = fixedInstPaise,
                    processingFeePaise = feePaise,
                    isActive = active,
                  )
                },
                onToggleProductActive = { productId, active ->
                  financeVm.setLoanProductActiveStatus(
                    productId = productId,
                    isActive = active,
                  )
                },
                onSeedStandardProducts = {
                  financeVm.seedStandardFixedProducts()
                },
              )
            }

            composable(
              route = AppRoutes.LOAN_DETAIL,
              arguments = listOf(navArgument("loanId") { type = NavType.StringType }),
            ) { entry ->
              val loanId = entry.arguments?.getString("loanId").orEmpty()
              val loan by remember(loanId) { financeVm.observeLoan(loanId) }
                .collectAsStateWithLifecycle(initialValue = null)
              val loanSchedules by remember(loanId) { financeVm.observeLoanSchedule(loanId) }
                .collectAsStateWithLifecycle(initialValue = emptyList())
              val loanPayments by remember(loanId) { financeVm.observeLoanPayments(loanId) }
                .collectAsStateWithLifecycle(initialValue = emptyList())
              val advanceLedger by remember(loanId) { financeVm.observeLoanAdvanceLedger(loanId) }
                .collectAsStateWithLifecycle(initialValue = emptyList())
              val loanPreClosures by remember(loanId) { financeVm.observeLoanPreClosures(loanId) }
                .collectAsStateWithLifecycle(initialValue = emptyList())
              val loanAdjustments by remember(loanId) { financeVm.observeLoanAdjustments(loanId) }
                .collectAsStateWithLifecycle(initialValue = emptyList())

              LoanDetailScreen(
                actor = actor,
                loan = loan,
                schedules = loanSchedules,
                payments = loanPayments,
                advanceLedger = advanceLedger,
                preClosures = loanPreClosures,
                adjustments = loanAdjustments,
                onBack = ::safeNavigateBack,
                onOpenCustomerProfile = { cId -> navController.navigate(AppRoutes.customerProfile(cId)) },
                onApproveApplication = { remarks ->
                  financeVm.approveLoanApplication(loanId = loanId, approvalRemarks = remarks)
                },
                onRejectApplication = { reason ->
                  financeVm.rejectLoanApplication(loanId = loanId, rejectionReason = reason)
                },
                onDisburseLoan = { disbDate, disbMode, disbRef, remarks ->
                  financeVm.disburseLoan(
                    loanId = loanId,
                    disbursementDateDdMmYyyy = disbDate,
                    disbursementMode = disbMode,
                    disbursementReference = disbRef,
                    approvalRemarks = remarks,
                  )
                },
                onCollectPayment = { amt, mode, type, ref, rem, dateStr, idempotencyKey ->
                  financeVm.collectPayment(loanId, amt, mode, type, ref, rem, dateStr, idempotencyKey)
                },
                onReversePayment = { paymentId, reason ->
                  financeVm.reversePayment(paymentId = paymentId, reason = reason)
                },
                onCorrectPayment = { origId, corrAmt, corrMode, corrRef, corrDate, reason, idempotencyKey ->
                  financeVm.correctPayment(
                    originalPaymentId = origId,
                    correctedAmountPaise = corrAmt,
                    correctedPaymentMode = corrMode,
                    correctedReferenceNumber = corrRef,
                    correctedCollectionDateDdMmYyyy = corrDate,
                    correctionReason = reason,
                    idempotencyKey = idempotencyKey,
                  )
                },
                onApplyAdvance = { financeVm.applyAdvanceToInstallments(loanId) },
                onSubmitPreClosure = { rebate, charge, reason ->
                  financeVm.submitPreClosureRequest(loanId, rebate, charge, reason)
                },
                onApprovePreClosure = { reqId, mode, ref, remarks ->
                  financeVm.approvePreClosure(reqId, mode, ref, remarks)
                },
                onRejectPreClosure = { reqId, remarks ->
                  financeVm.rejectPreClosure(reqId, remarks)
                },
              )
            }

            composable(AppRoutes.EMPLOYEES) {
              EmployeeManagementScreen(
                actor = actor,
                branches = branches,
                teams = teams,
                employees = employees,
                invites = employeeInvites,
                onBack = ::safeNavigateBack,
                onCreateEmployee = { name, mob, mail, r, bId, tId, tName, perms ->
                  financeVm.createEmployee(name, mob, mail, r, bId, tId, tName, perms)
                },
                onRevokeInvite = financeVm::revokeEmployeeInvite,
                onUpdateEmployee = { empId, r, bId, tId, tName, active, perms ->
                  financeVm.updateEmployee(empId, r, bId, tId, tName, active, perms)
                },
              )
            }

            composable(AppRoutes.TEAMS) {
              TeamManagementScreen(
                actor = actor,
                branches = branches,
                teams = teams,
                employees = employees,
                onBack = ::safeNavigateBack,
                onCreateBranch = { name, city, addr -> financeVm.createBranch(name, city, addr) },
                onCreateTeam = { name, bId, mgrId, beat -> financeVm.createTeam(name, bId, mgrId, beat) },
              )
            }

            composable(AppRoutes.SYNC_QUEUE) {
              SyncQueueScreen(
                queueItems = syncQueue,
                isSyncing = isSyncing,
                onBack = ::safeNavigateBack,
                onSyncNow = financeVm::runSyncNow,
              )
            }

            composable(AppRoutes.AUDIT_LOGS) {
              AuditLogsScreen(
                logs = auditLogs,
                onBack = ::safeNavigateBack,
              )
            }
          }
        }
      }
    }
  }

  val confirmedReceipt = receiptConfirmation
  if (confirmedReceipt != null) {
    VerifiedReceiptConfirmationDialog(
      payment = confirmedReceipt,
      onDismiss = financeVm::dismissReceiptConfirmation,
    )
  }
}

@Composable
private fun CloudSyncErrorBanner(
  message: String,
  onRetry: () -> Unit,
  onDismiss: () -> Unit,
) {
  Surface(
    color = MaterialTheme.colorScheme.errorContainer,
    modifier = Modifier
      .fillMaxWidth()
      .testTag("cloud_error_banner"),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
      Icon(
        imageVector = Icons.Default.WarningAmber,
        contentDescription = "Cloud Sync Error",
        tint = MaterialTheme.colorScheme.onErrorContainer,
      )
      Spacer(Modifier.width(8.dp))
      Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onErrorContainer,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.weight(1f),
      )
      TextButton(onClick = onRetry) {
        Icon(
          imageVector = Icons.Default.Refresh,
          contentDescription = "Retry Cloud Sync",
          tint = MaterialTheme.colorScheme.onErrorContainer,
        )
        Spacer(Modifier.width(4.dp))
        Text("Retry", color = MaterialTheme.colorScheme.onErrorContainer)
      }
      IconButton(onClick = onDismiss) {
        Icon(
          imageVector = Icons.Default.Close,
          contentDescription = "Dismiss error",
          tint = MaterialTheme.colorScheme.onErrorContainer,
        )
      }
    }
  }
}
