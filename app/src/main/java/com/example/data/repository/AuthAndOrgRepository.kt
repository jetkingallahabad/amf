package com.example.data.repository

import com.example.data.local.AuditLogEntity
import com.example.data.local.BranchEntity
import com.example.data.local.EmployeeEntity
import com.example.data.local.EmployeeInviteEntity
import com.example.data.local.LoanFinanceDao
import com.example.data.local.TeamEntity
import com.example.data.local.encodeUidList
import com.example.data.remote.FirestoreBranch
import com.example.data.remote.FirestoreEmployee
import com.example.data.remote.FirestoreEmployeeInvite
import com.example.data.remote.FirestoreOrgConfig
import com.example.data.remote.FirestorePayloads
import com.example.data.remote.FirestoreTeam
import com.example.data.session.SessionStore
import com.example.domain.model.RolePermissionTemplate
import com.example.domain.model.UserRole
import com.example.util.IndianFinanceFormatter
import com.example.util.OperationType
import com.example.util.handleFirestoreError
import com.example.util.toUserFriendlyFirestoreMessage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID

sealed interface CloudAuthResolution {
  data class NeedsOwnerSetup(
    val uid: String,
    val email: String,
    val displayName: String,
  ) : CloudAuthResolution

  data class Authenticated(val employee: EmployeeEntity) : CloudAuthResolution

  data class AccountDeactivated(val employee: EmployeeEntity) : CloudAuthResolution

  data class AccessNotProvisioned(
    val uid: String,
    val email: String,
  ) : CloudAuthResolution
}

class AuthAndOrgRepository(
  private val dao: LoanFinanceDao,
  private val sessionStore: SessionStore,
  private val firestore: FirebaseFirestore? = null,
  private val firebaseAuth: FirebaseAuth? = null,
) {

  fun observeBranches(): Flow<List<BranchEntity>> = dao.observeBranches()

  fun observePermittedTeams(actor: EmployeeEntity): Flow<List<TeamEntity>> = when (actor.role) {
    UserRole.OWNER -> dao.observeAllTeams()
    UserRole.BRANCH_MANAGER, UserRole.COLLECTION_AGENT -> dao.observeTeamsByBranch(actor.branchId)
  }

  fun observePermittedEmployees(actor: EmployeeEntity): Flow<List<EmployeeEntity>> = when (actor.role) {
    UserRole.OWNER -> dao.observeAllEmployees()
    UserRole.BRANCH_MANAGER -> dao.observeEmployeesByBranch(actor.branchId)
    UserRole.COLLECTION_AGENT -> dao.observeEmployeeById(actor.id).map { listOfNotNull(it) }
  }

  fun observeEmployeeInvites(actor: EmployeeEntity): Flow<List<EmployeeInviteEntity>> =
    if (actor.role == UserRole.OWNER) dao.observeAllInvites() else flowOf(emptyList())

  /**
   * Direct Cloud Firestore real-time streams (aligned strictly with firestore.rules allow list).
   */
  fun observeCloudBranches(actor: EmployeeEntity): Flow<List<BranchEntity>> {
    val fs = checkNotNull(firestore) { "Firestore not configured" }
    val ownerUid = actor.orgOwnerUid.ifBlank { actor.id }
    val query = when (actor.role) {
      UserRole.OWNER -> fs.collection("branches").whereEqualTo("orgOwnerUid", ownerUid)
      UserRole.BRANCH_MANAGER -> fs.collection("branches").whereArrayContains("managerUids", actor.id)
      UserRole.COLLECTION_AGENT -> fs.collection("branches").whereArrayContains("memberUids", actor.id)
    }
    return query.snapshots().map { snap ->
      snap.documents
        .mapNotNull { it.toObject(FirestoreBranch::class.java)?.toEntity() }
        .sortedBy { it.createdAtMillis }
    }
  }

  fun observeCloudTeams(actor: EmployeeEntity): Flow<List<TeamEntity>> {
    val fs = checkNotNull(firestore) { "Firestore not configured" }
    val ownerUid = actor.orgOwnerUid.ifBlank { actor.id }
    val query = when (actor.role) {
      UserRole.OWNER -> fs.collection("teams").whereEqualTo("orgOwnerUid", ownerUid)
      UserRole.BRANCH_MANAGER -> fs.collection("teams").whereArrayContains("branchManagerUids", actor.id)
      UserRole.COLLECTION_AGENT -> fs.collection("teams").whereArrayContains("memberUids", actor.id)
    }
    return query.snapshots().map { snap ->
      snap.documents
        .mapNotNull { it.toObject(FirestoreTeam::class.java)?.toEntity() }
        .sortedByDescending { it.createdAtMillis }
    }
  }

  fun observeCloudEmployees(actor: EmployeeEntity): Flow<List<EmployeeEntity>> {
    val fs = checkNotNull(firestore) { "Firestore not configured" }
    val ownerUid = actor.orgOwnerUid.ifBlank { actor.id }
    val query = when (actor.role) {
      UserRole.OWNER -> fs.collection("employees").whereEqualTo("orgOwnerUid", ownerUid)
      UserRole.BRANCH_MANAGER -> fs.collection("employees").whereArrayContains("branchManagerUids", actor.id)
      UserRole.COLLECTION_AGENT -> fs.collection("employees").whereEqualTo("userId", actor.id)
    }
    return query.snapshots().map { snap ->
      snap.documents
        .mapNotNull { it.toObject(FirestoreEmployee::class.java)?.toEntity() }
        .sortedBy { it.createdAtMillis }
    }
  }

  fun observeCloudInvites(actor: EmployeeEntity): Flow<List<EmployeeInviteEntity>> {
    val fs = checkNotNull(firestore) { "Firestore not configured" }
    if (actor.role != UserRole.OWNER) return flowOf(emptyList())
    val ownerUid = actor.orgOwnerUid.ifBlank { actor.id }
    return fs.collection("employee_invites")
      .whereEqualTo("orgOwnerUid", ownerUid)
      .snapshots()
      .map { snap ->
        snap.documents
          .mapNotNull { it.toObject(FirestoreEmployeeInvite::class.java)?.toEntity() }
          .sortedByDescending { it.createdAtMillis }
      }
  }

  /**
   * Starts lifecycle-scoped mirroring of org-level Cloud Firestore collections into local Room.
   */
  fun startRealtimeOrgSync(
    actor: EmployeeEntity,
    scope: CoroutineScope,
    onSyncError: (String) -> Unit,
  ): List<Job> {
    if (firestore == null || firebaseAuth?.currentUser == null) return emptyList()
    val jobs = mutableListOf<Job>()

    jobs += scope.launch(Dispatchers.IO) {
      observeCloudBranches(actor)
        .catch { e ->
          if (e is Exception) handleFirestoreError(e, OperationType.LIST, "branches")
          onSyncError(toUserFriendlyFirestoreMessage(e))
        }
        .collect { list -> dao.upsertBranches(list) }
    }

    jobs += scope.launch(Dispatchers.IO) {
      observeCloudTeams(actor)
        .catch { e ->
          if (e is Exception) handleFirestoreError(e, OperationType.LIST, "teams")
          onSyncError(toUserFriendlyFirestoreMessage(e))
        }
        .collect { list -> dao.upsertTeams(list) }
    }

    jobs += scope.launch(Dispatchers.IO) {
      observeCloudEmployees(actor)
        .catch { e ->
          if (e is Exception) handleFirestoreError(e, OperationType.LIST, "employees")
          onSyncError(toUserFriendlyFirestoreMessage(e))
        }
        .collect { list -> dao.upsertEmployees(list) }
    }

    if (actor.role == UserRole.OWNER) {
      jobs += scope.launch(Dispatchers.IO) {
        observeCloudInvites(actor)
          .catch { e ->
            if (e is Exception) handleFirestoreError(e, OperationType.LIST, "employee_invites")
            onSyncError(toUserFriendlyFirestoreMessage(e))
          }
          .collect { list -> dao.upsertInvites(list) }
      }
    }

    return jobs
  }

  /**
   * Resolves the currently signed-in Firebase Auth user's employee profile or claims a pending
   * Owner-provisioned invite. Never allows uninvited users to self-register or pick roles.
   */
  suspend fun resolveAuthenticatedSession(): Result<CloudAuthResolution> = withContext(Dispatchers.IO) {
    runCatching {
      val fs = checkNotNull(firestore) { "Cloud Firestore is not initialized." }
      val user = checkNotNull(firebaseAuth?.currentUser) { "Please sign in with Google first." }
      val uid = user.uid
      val cleanEmail = user.email.orEmpty().trim().lowercase(Locale.US)

      // 1. Check whether organization has been bootstrapped
      val orgSnap = try {
        fs.collection("org_config").document("main").get().await()
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.GET, "org_config/main")
        throw IllegalStateException(toUserFriendlyFirestoreMessage(e, "Failed to verify organization configuration."), e)
      }

      val orgConfig = orgSnap.toObject(FirestoreOrgConfig::class.java)
      if (!orgSnap.exists() || orgConfig == null || !orgConfig.initialized) {
        return@runCatching CloudAuthResolution.NeedsOwnerSetup(
          uid = uid,
          email = cleanEmail,
          displayName = user.displayName.orEmpty(),
        )
      }

      if (orgConfig.ledgerEpochVersion < 1 && orgConfig.ownerUid == uid) {
        try {
          fs.collection("org_config").document("main").update(
            mapOf(
              "ledgerEpochVersion" to 1,
              "ledgerStartedAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          ).await()
        } catch (e: Exception) {
          handleFirestoreError(e, OperationType.UPDATE, "org_config/main")
        }
      }

      // 2. Check existing employee profile under /employees/{uid}
      val empSnap = try {
        fs.collection("employees").document(uid).get().await()
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.GET, "employees/$uid")
        throw IllegalStateException(toUserFriendlyFirestoreMessage(e, "Failed to load employee profile."), e)
      }

      if (empSnap.exists()) {
        val emp = empSnap.toObject(FirestoreEmployee::class.java)?.toEntity()
          ?: throw IllegalStateException("Corrupted employee profile in Cloud Firestore.")
        dao.insertEmployee(emp)
        cacheBranchQuietly(fs, emp.branchId)
        if (!emp.isActive) {
          sessionStore.setActiveEmployeeId(null)
          return@runCatching CloudAuthResolution.AccountDeactivated(emp)
        }
        sessionStore.setActiveEmployeeId(emp.id)
        return@runCatching CloudAuthResolution.Authenticated(emp)
      }

      // 3. No /employees/{uid} yet -> look for a PENDING Owner-created invite targeting this user
      val pendingInvite = findMatchingPendingInvite(
        fs = fs,
        uid = uid,
        email = cleanEmail,
        emailVerified = user.isEmailVerified,
      ) ?: return@runCatching CloudAuthResolution.AccessNotProvisioned(uid = uid, email = cleanEmail)

      // 4. Atomically claim the invite and create /employees/{uid} with exact Owner-assigned role & permissions
      val claimedEmployee = claimInviteAtomically(
        fs = fs,
        uid = uid,
        email = cleanEmail.ifBlank { pendingInvite.invitedEmail },
        invite = pendingInvite,
      )
      dao.insertEmployee(claimedEmployee)
      cacheBranchQuietly(fs, claimedEmployee.branchId)
      sessionStore.setActiveEmployeeId(claimedEmployee.id)
      CloudAuthResolution.Authenticated(claimedEmployee)
    }
  }

  private suspend fun cacheBranchQuietly(fs: FirebaseFirestore, branchId: String) {
    try {
      val bSnap = fs.collection("branches").document(branchId).get().await()
      bSnap.toObject(FirestoreBranch::class.java)?.toEntity()?.let { dao.insertBranch(it) }
    } catch (_: Exception) {
      // Non-fatal; realtime listener will populate
    }
  }

  private suspend fun findMatchingPendingInvite(
    fs: FirebaseFirestore,
    uid: String,
    email: String,
    emailVerified: Boolean,
  ): FirestoreEmployeeInvite? {
    // Path 1: UID-targeted invite
    try {
      val uidSnap = fs.collection("employee_invites")
        .whereEqualTo("invitedUid", uid)
        .get()
        .await()
      val matchByUid = uidSnap.documents
        .mapNotNull { it.toObject(FirestoreEmployeeInvite::class.java) }
        .firstOrNull { it.status == "PENDING" && it.isActive }
      if (matchByUid != null) return matchByUid
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.LIST, "employee_invites")
    }

    // Path 2: Verified Google email invite
    if (emailVerified && email.isNotBlank()) {
      try {
        val emailSnap = fs.collection("employee_invites")
          .whereEqualTo("invitedEmail", email)
          .get()
          .await()
        val matchByEmail = emailSnap.documents
          .mapNotNull { it.toObject(FirestoreEmployeeInvite::class.java) }
          .firstOrNull { it.status == "PENDING" && it.isActive }
        if (matchByEmail != null) return matchByEmail
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.LIST, "employee_invites")
      }
    }
    return null
  }

  private suspend fun claimInviteAtomically(
    fs: FirebaseFirestore,
    uid: String,
    email: String,
    invite: FirestoreEmployeeInvite,
  ): EmployeeEntity {
    val now = System.currentTimeMillis()
    val today = IndianFinanceFormatter.todayDdMmYyyy(now)
    val parsedRole = runCatching { UserRole.valueOf(invite.role) }.getOrDefault(UserRole.COLLECTION_AGENT)
    val managerUids = buildList {
      addAll(invite.branchManagerUids)
      if (invite.orgOwnerUid.isNotBlank()) add(invite.orgOwnerUid)
      if (parsedRole == UserRole.BRANCH_MANAGER) add(uid)
    }.distinct().take(20)

    val newEmp = EmployeeEntity(
      id = uid,
      orgOwnerUid = invite.orgOwnerUid,
      employeeCode = invite.employeeCode.ifBlank { invite.id },
      fullName = invite.fullName,
      mobile = invite.mobile,
      email = email,
      role = parsedRole,
      branchId = invite.branchId,
      branchName = invite.branchName,
      branchManagerUidsCsv = encodeUidList(managerUids),
      teamId = invite.teamId.ifBlank { null },
      teamName = invite.teamName.ifBlank { null },
      isActive = invite.isActive,
      canRegisterCustomer = invite.canRegisterCustomer,
      canCreateLoan = invite.canCreateLoan,
      canApproveLoan = invite.canApproveLoan,
      canCollectPayment = invite.canCollectPayment,
      canManageAdvance = invite.canManageAdvance,
      canRequestPreClosure = invite.canRequestPreClosure,
      canApprovePreClosure = invite.canApprovePreClosure,
      canUploadDocuments = invite.canUploadDocuments,
      canManageTeams = invite.canManageTeams,
      canViewReports = invite.canViewReports,
      canViewAuditLogs = invite.canViewAuditLogs,
      createdByEmployeeId = invite.createdByUid,
      createdDateDdMmYyyy = invite.createdDateDdMmYyyy.ifBlank { today },
      createdAtMillis = now,
    )

    try {
      val batch = fs.batch()
      val inviteRef = fs.collection("employee_invites").document(newEmp.employeeCode)
      batch.update(
        inviteRef,
        mapOf(
          "status" to "CLAIMED",
          "claimedByUid" to uid,
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )

      val empRef = fs.collection("employees").document(uid)
      batch.set(empRef, FirestorePayloads.employeeCreate(newEmp))

      val branchRef = fs.collection("branches").document(newEmp.branchId)
      val branchUpdate = mutableMapOf<String, Any>(
        "memberUids" to FieldValue.arrayUnion(uid),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
      if (parsedRole == UserRole.BRANCH_MANAGER) {
        branchUpdate["managerUids"] = FieldValue.arrayUnion(uid)
      }
      batch.update(branchRef, branchUpdate)

      if (!newEmp.teamId.isNullOrBlank()) {
        val teamRef = fs.collection("teams").document(newEmp.teamId)
        val teamUpdate = mutableMapOf<String, Any>(
          "memberUids" to FieldValue.arrayUnion(uid),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
        if (parsedRole == UserRole.BRANCH_MANAGER) {
          teamUpdate["branchManagerUids"] = FieldValue.arrayUnion(uid)
        }
        batch.update(teamRef, teamUpdate)
      }

      batch.commit().await()
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, "employees/$uid")
      throw IllegalStateException(toUserFriendlyFirestoreMessage(e, "Failed to activate employee invite."), e)
    }

    // Record post-claim audit log now that /employees/{uid} is active
    val claimAudit = AuditLogEntity(
      id = UUID.randomUUID().toString(),
      orgOwnerUid = newEmp.orgOwnerUid,
      branchManagerUidsCsv = newEmp.branchManagerUidsCsv,
      actorEmployeeId = uid,
      actorName = newEmp.fullName,
      actorRole = newEmp.role,
      branchId = newEmp.branchId,
      category = "AUTH",
      action = "INVITE_CLAIMED",
      targetType = "EMPLOYEE",
      targetId = newEmp.employeeCode,
      summary = "${newEmp.fullName} (${newEmp.employeeCode}) claimed ${newEmp.role.displayTitle} invite in ${newEmp.branchName}",
      timestampDdMmYyyyHhMm = IndianFinanceFormatter.formatDateTimeSeconds(now),
      createdAtMillis = now,
    )
    try {
      fs.collection("audit_logs")
        .document(claimAudit.id)
        .set(FirestorePayloads.auditLogCreate(claimAudit))
        .await()
    } catch (_: Exception) {
      // Non-fatal
    }
    dao.insertAuditLog(claimAudit)
    return newEmp
  }

  /**
   * One-time atomic Organization & Owner bootstrap on Cloud Firestore + Room.
   * Zero passwords are accepted or stored.
   */
  suspend fun initializeOwnerAccount(
    ownerFullName: String,
    mobile: String,
    email: String,
    orgName: String = "",
    headOfficeBranchName: String,
    headOfficeCity: String,
    headOfficeAddress: String,
  ): Result<EmployeeEntity> = withContext(Dispatchers.IO) {
    runCatching {
      require(ownerFullName.trim().length >= 3) { "Owner full name must be at least 3 characters." }
      val cleanMobile = mobile.filter { it.isDigit() }
      require(cleanMobile.length == 10) { "Enter a valid 10-digit Indian mobile number." }
      require(headOfficeBranchName.trim().length >= 2) { "Head Office / Branch name is required." }
      require(headOfficeCity.trim().length >= 2) { "Branch city is required." }

      val authUser = firebaseAuth?.currentUser
      if (firestore != null && authUser == null) {
        throw IllegalStateException("Please sign in with Google before initializing the organization.")
      }
      if (firestore == null) {
        check(dao.countEmployees() == 0) { "Organization is already initialized." }
      }

      val now = System.currentTimeMillis()
      val today = IndianFinanceFormatter.todayDdMmYyyy(now)
      val ownerUid = authUser?.uid ?: UUID.randomUUID().toString()
      val resolvedEmail = (authUser?.email ?: email).trim().lowercase(Locale.US)
      val branchId = UUID.randomUUID().toString()
      val resolvedOrgName = orgName.trim().ifBlank { headOfficeBranchName.trim() }

      val branch = BranchEntity(
        id = branchId,
        orgOwnerUid = ownerUid,
        branchCode = "BR-001",
        name = headOfficeBranchName.trim(),
        city = headOfficeCity.trim(),
        address = headOfficeAddress.trim().ifBlank { headOfficeCity.trim() },
        managerUidsCsv = encodeUidList(listOf(ownerUid)),
        memberUidsCsv = encodeUidList(listOf(ownerUid)),
        createdByUid = ownerUid,
        createdAtMillis = now,
      )

      val perms = RolePermissionTemplate.defaultsFor(UserRole.OWNER)
      val owner = EmployeeEntity(
        id = ownerUid,
        orgOwnerUid = ownerUid,
        employeeCode = "EMP-0001",
        fullName = ownerFullName.trim(),
        mobile = cleanMobile,
        email = resolvedEmail,
        role = UserRole.OWNER,
        branchId = branchId,
        branchName = branch.name,
        branchManagerUidsCsv = encodeUidList(listOf(ownerUid)),
        teamId = null,
        teamName = null,
        isActive = true,
        canRegisterCustomer = perms.canRegisterCustomer,
        canCreateLoan = perms.canCreateLoan,
        canApproveLoan = perms.canApproveLoan,
        canCollectPayment = perms.canCollectPayment,
        canManageAdvance = perms.canManageAdvance,
        canRequestPreClosure = perms.canRequestPreClosure,
        canApprovePreClosure = perms.canApprovePreClosure,
        canUploadDocuments = perms.canUploadDocuments,
        canManageTeams = perms.canManageTeams,
        canViewReports = perms.canViewReports,
        canViewAuditLogs = perms.canViewAuditLogs,
        createdByEmployeeId = ownerUid,
        createdDateDdMmYyyy = today,
        createdAtMillis = now,
      )

      val audit = AuditLogEntity(
        id = UUID.randomUUID().toString(),
        orgOwnerUid = ownerUid,
        branchManagerUidsCsv = encodeUidList(listOf(ownerUid)),
        actorEmployeeId = ownerUid,
        actorName = owner.fullName,
        actorRole = UserRole.OWNER,
        branchId = branchId,
        category = "AUTH",
        action = "OWNER_INITIALIZED",
        targetType = "EMPLOYEE",
        targetId = owner.employeeCode,
        summary = "Initialized organization '$resolvedOrgName' with Owner ${owner.fullName} (${owner.employeeCode}) and branch ${branch.name}",
        timestampDdMmYyyyHhMm = IndianFinanceFormatter.formatDateTimeSeconds(now),
        createdAtMillis = now,
      )

      val fs = firestore
      if (fs != null) {
        try {
          val batch = fs.batch()
          batch.set(
            fs.collection("org_config").document("main"),
            FirestorePayloads.orgConfigCreate(
              orgName = resolvedOrgName,
              ownerUid = ownerUid,
              headOfficeBranchId = branchId,
              headOfficeBranchName = branch.name,
            ),
          )
          batch.set(fs.collection("branches").document(branch.id), FirestorePayloads.branchCreate(branch))
          batch.set(fs.collection("employees").document(owner.id), FirestorePayloads.employeeCreate(owner))
          batch.set(fs.collection("audit_logs").document(audit.id), FirestorePayloads.auditLogCreate(audit))
          batch.commit().await()
        } catch (e: Exception) {
          handleFirestoreError(e, OperationType.WRITE, "org_config/main")
          throw e
        }
      }

      dao.commitOwnerInitialization(branch, owner, audit)
      sessionStore.setActiveEmployeeId(owner.id)
      owner
    }
  }

  suspend fun logout(actor: EmployeeEntity?) = withContext(Dispatchers.IO) {
    if (actor != null) {
      val now = System.currentTimeMillis()
      val audit = AuditLogEntity(
        id = UUID.randomUUID().toString(),
        orgOwnerUid = actor.orgOwnerUid.ifBlank { actor.id },
        branchManagerUidsCsv = actor.branchManagerUidsCsv,
        actorEmployeeId = actor.id,
        actorName = actor.fullName,
        actorRole = actor.role,
        branchId = actor.branchId,
        category = "AUTH",
        action = "LOGOUT",
        targetType = "EMPLOYEE",
        targetId = actor.employeeCode,
        summary = "${actor.fullName} signed out",
        timestampDdMmYyyyHhMm = IndianFinanceFormatter.formatDateTimeSeconds(now),
        createdAtMillis = now,
      )
      dao.insertAuditLog(audit)
      val fs = firestore
      if (fs != null && firebaseAuth?.currentUser != null) {
        runCatching {
          fs.collection("audit_logs")
            .document(audit.id)
            .set(FirestorePayloads.auditLogCreate(audit))
            .await()
        }
      }
    }
    sessionStore.setActiveEmployeeId(null)
  }

  suspend fun createBranch(
    actor: EmployeeEntity,
    name: String,
    city: String,
    address: String,
  ): Result<BranchEntity> = withContext(Dispatchers.IO) {
    runCatching {
      if (actor.role != UserRole.OWNER) {
        throw SecurityException("Only the Owner can create new branches.")
      }
      require(name.trim().length >= 2) { "Branch name is required." }
      require(city.trim().length >= 2) { "Branch city is required." }

      val ownerUid = actor.orgOwnerUid.ifBlank { actor.id }
      val existingCount = if (firestore != null) {
        firestore.collection("branches").whereEqualTo("orgOwnerUid", ownerUid).get().await().size()
      } else {
        dao.getBranches().size
      }
      val code = "BR-%03d".format(Locale.US, existingCount + 1)
      val now = System.currentTimeMillis()
      val branch = BranchEntity(
        id = UUID.randomUUID().toString(),
        orgOwnerUid = ownerUid,
        branchCode = code,
        name = name.trim(),
        city = city.trim(),
        address = address.trim().ifBlank { city.trim() },
        managerUidsCsv = encodeUidList(listOf(ownerUid)),
        memberUidsCsv = encodeUidList(listOf(ownerUid)),
        createdByUid = actor.id,
        createdAtMillis = now,
      )
      val audit = AuditLogEntity(
        id = UUID.randomUUID().toString(),
        orgOwnerUid = ownerUid,
        branchManagerUidsCsv = branch.managerUidsCsv,
        actorEmployeeId = actor.id,
        actorName = actor.fullName,
        actorRole = actor.role,
        branchId = branch.id,
        category = "TEAM",
        action = "BRANCH_CREATED",
        targetType = "BRANCH",
        targetId = branch.branchCode,
        summary = "Created branch ${branch.name} (${branch.branchCode}) in ${branch.city}",
        timestampDdMmYyyyHhMm = IndianFinanceFormatter.formatDateTimeSeconds(now),
        createdAtMillis = now,
      )

      val fs = firestore
      if (fs != null) {
        try {
          val batch = fs.batch()
          batch.set(fs.collection("branches").document(branch.id), FirestorePayloads.branchCreate(branch))
          batch.set(fs.collection("audit_logs").document(audit.id), FirestorePayloads.auditLogCreate(audit))
          batch.commit().await()
        } catch (e: Exception) {
          handleFirestoreError(e, OperationType.CREATE, "branches/${branch.id}")
          throw e
        }
      }

      dao.insertBranch(branch)
      dao.insertAuditLog(audit)
      branch
    }
  }

  suspend fun createTeam(
    actor: EmployeeEntity,
    teamName: String,
    branchId: String,
    managerEmployeeId: String?,
    areaBeat: String,
  ): Result<TeamEntity> = withContext(Dispatchers.IO) {
    runCatching {
      if (actor.role != UserRole.OWNER && !actor.canManageTeams) {
        throw SecurityException("You do not have permission to manage teams.")
      }
      if (actor.role != UserRole.OWNER && actor.branchId != branchId) {
        throw SecurityException("Branch Managers can only create teams within their assigned branch.")
      }
      require(teamName.trim().length >= 2) { "Team name is required." }
      require(areaBeat.trim().length >= 2) { "Area / Collection Beat description is required." }

      val branch = resolveBranch(branchId) ?: throw IllegalArgumentException("Selected branch not found.")
      val manager = managerEmployeeId?.let { dao.getEmployeeById(it) }
      val ownerUid = actor.orgOwnerUid.ifBlank { branch.orgOwnerUid.ifBlank { actor.id } }
      val count = dao.countTeams()
      val teamCode = "TM-%03d".format(Locale.US, count + 1)
      val now = System.currentTimeMillis()

      val managerUids = (branch.managerUidsList() + listOfNotNull(ownerUid, actor.id.takeIf { actor.role == UserRole.BRANCH_MANAGER }))
        .distinct()
      val memberUids = (branch.memberUidsList() + listOfNotNull(actor.id, manager?.id)).distinct()

      val team = TeamEntity(
        id = UUID.randomUUID().toString(),
        orgOwnerUid = ownerUid,
        teamCode = teamCode,
        name = teamName.trim(),
        branchId = branch.id,
        branchName = branch.name,
        branchManagerUidsCsv = encodeUidList(managerUids),
        memberUidsCsv = encodeUidList(memberUids),
        managerEmployeeId = manager?.id,
        managerName = manager?.fullName ?: actor.fullName,
        areaBeat = areaBeat.trim(),
        createdByUid = actor.id,
        createdAtMillis = now,
      )
      val audit = AuditLogEntity(
        id = UUID.randomUUID().toString(),
        orgOwnerUid = ownerUid,
        branchManagerUidsCsv = team.branchManagerUidsCsv,
        actorEmployeeId = actor.id,
        actorName = actor.fullName,
        actorRole = actor.role,
        branchId = branch.id,
        category = "TEAM",
        action = "TEAM_CREATED",
        targetType = "TEAM",
        targetId = team.teamCode,
        summary = "Created team ${team.name} (${team.teamCode}) for beat '${team.areaBeat}' in ${branch.name}",
        timestampDdMmYyyyHhMm = IndianFinanceFormatter.formatDateTimeSeconds(now),
        createdAtMillis = now,
      )

      val fs = firestore
      if (fs != null) {
        try {
          val batch = fs.batch()
          batch.set(fs.collection("teams").document(team.id), FirestorePayloads.teamCreate(team))
          batch.set(fs.collection("audit_logs").document(audit.id), FirestorePayloads.auditLogCreate(audit))
          batch.commit().await()
        } catch (e: Exception) {
          handleFirestoreError(e, OperationType.CREATE, "teams/${team.id}")
          throw e
        }
      }

      dao.insertTeam(team)
      dao.insertAuditLog(audit)
      team
    }
  }

  /**
   * STRICT OWNER-ONLY EMPLOYEE PROVISIONING:
   * Creates an authorized employee invite in `/employee_invites/{employeeCode}` (when Cloud Firestore
   * is active) without storing any passwords. When the employee signs in via Google Sign-In with
   * matching verified email, their account claims the exact role & permissions assigned by the Owner.
   */
  suspend fun createEmployeeInvite(
    actor: EmployeeEntity,
    fullName: String,
    mobile: String,
    invitedEmail: String,
    assignedRole: UserRole,
    branchId: String,
    teamId: String?,
    teamName: String?,
    permissions: RolePermissionTemplate,
    invitedUid: String = "",
    bypassClientGuardForRulesTest: Boolean = false,
  ): Result<EmployeeInviteEntity> = withContext(Dispatchers.IO) {
    runCatching {
      if (!bypassClientGuardForRulesTest && actor.role != UserRole.OWNER) {
        throw SecurityException("Only the Owner can create employees and assign roles.")
      }
      require(fullName.trim().length >= 3) { "Employee full name must be at least 3 characters." }
      val cleanMobile = mobile.filter { it.isDigit() }
      require(cleanMobile.length == 10) { "Enter a valid 10-digit Indian mobile number." }
      val cleanEmail = invitedEmail.trim().lowercase(Locale.US)
      require(cleanEmail.contains("@") && cleanEmail.length >= 5) {
        "Enter the employee's authorized Google email address."
      }

      val branch = resolveBranch(branchId) ?: throw IllegalArgumentException("Assigned branch not found.")
      val ownerUid = actor.orgOwnerUid.ifBlank { branch.orgOwnerUid.ifBlank { actor.id } }

      val nextIndex = nextEmployeeSequence(ownerUid)
      val empCode = "EMP-%04d".format(Locale.US, nextIndex)
      val now = System.currentTimeMillis()
      val today = IndianFinanceFormatter.todayDdMmYyyy(now)

      val effectivePerms = if (assignedRole == UserRole.OWNER) {
        RolePermissionTemplate.defaultsFor(UserRole.OWNER)
      } else {
        permissions
      }

      val invite = EmployeeInviteEntity(
        employeeCode = empCode,
        orgOwnerUid = ownerUid,
        fullName = fullName.trim(),
        mobile = cleanMobile,
        invitedEmail = cleanEmail,
        invitedUid = invitedUid.trim(),
        role = assignedRole,
        branchId = branch.id,
        branchName = branch.name,
        branchManagerUidsCsv = branch.managerUidsCsv,
        teamId = teamId,
        teamName = teamName,
        isActive = true,
        status = "PENDING",
        claimedByUid = "",
        canRegisterCustomer = effectivePerms.canRegisterCustomer,
        canCreateLoan = effectivePerms.canCreateLoan,
        canApproveLoan = effectivePerms.canApproveLoan,
        canCollectPayment = effectivePerms.canCollectPayment,
        canManageAdvance = effectivePerms.canManageAdvance,
        canRequestPreClosure = effectivePerms.canRequestPreClosure,
        canApprovePreClosure = effectivePerms.canApprovePreClosure,
        canUploadDocuments = effectivePerms.canUploadDocuments,
        canManageTeams = effectivePerms.canManageTeams,
        canViewReports = effectivePerms.canViewReports,
        canViewAuditLogs = effectivePerms.canViewAuditLogs,
        createdByEmployeeId = actor.id,
        createdDateDdMmYyyy = today,
        createdAtMillis = now,
      )

      val audit = AuditLogEntity(
        id = UUID.randomUUID().toString(),
        orgOwnerUid = ownerUid,
        branchManagerUidsCsv = branch.managerUidsCsv,
        actorEmployeeId = actor.id,
        actorName = actor.fullName,
        actorRole = actor.role,
        branchId = branch.id,
        category = "EMPLOYEE",
        action = "EMPLOYEE_INVITE_CREATED",
        targetType = "EMPLOYEE",
        targetId = invite.employeeCode,
        summary = "Owner provisioned ${assignedRole.displayTitle} ${invite.fullName} (${invite.employeeCode}, $cleanEmail) in ${branch.name}",
        timestampDdMmYyyyHhMm = IndianFinanceFormatter.formatDateTimeSeconds(now),
        createdAtMillis = now,
      )

      val fs = firestore
      if (fs != null) {
        try {
          val batch = fs.batch()
          batch.set(
            fs.collection("employee_invites").document(invite.employeeCode),
            FirestorePayloads.inviteCreate(invite),
          )
          batch.set(
            fs.collection("audit_logs").document(audit.id),
            FirestorePayloads.auditLogCreate(audit),
          )
          batch.commit().await()
        } catch (e: Exception) {
          handleFirestoreError(e, OperationType.CREATE, "employee_invites/${invite.employeeCode}")
          throw e
        }
        dao.upsertInvite(invite)
      } else {
        // Local-only unit test helper (when firestore == null)
        val resolvedUid = invitedUid.ifBlank { UUID.randomUUID().toString() }
        val localEmp = EmployeeEntity(
          id = resolvedUid,
          orgOwnerUid = ownerUid,
          employeeCode = invite.employeeCode,
          fullName = invite.fullName,
          mobile = invite.mobile,
          email = invite.invitedEmail,
          role = invite.role,
          branchId = branch.id,
          branchName = branch.name,
          branchManagerUidsCsv = branch.managerUidsCsv,
          teamId = teamId,
          teamName = teamName,
          isActive = true,
          canRegisterCustomer = invite.canRegisterCustomer,
          canCreateLoan = invite.canCreateLoan,
          canApproveLoan = invite.canApproveLoan,
          canCollectPayment = invite.canCollectPayment,
          canManageAdvance = invite.canManageAdvance,
          canRequestPreClosure = invite.canRequestPreClosure,
          canApprovePreClosure = invite.canApprovePreClosure,
          canUploadDocuments = invite.canUploadDocuments,
          canManageTeams = invite.canManageTeams,
          canViewReports = invite.canViewReports,
          canViewAuditLogs = invite.canViewAuditLogs,
          createdByEmployeeId = actor.id,
          createdDateDdMmYyyy = today,
          createdAtMillis = now,
        )
        dao.insertEmployee(localEmp)
        dao.upsertInvite(invite.copy(status = "CLAIMED", claimedByUid = resolvedUid))
      }

      dao.insertAuditLog(audit)
      invite
    }
  }

  /**
   * STRICT ACCESS CONTROL: Only the Owner can assign roles, change active status, or edit permissions.
   * Zero passwords are accepted or stored.
   */
  suspend fun updateEmployeeRoleAndPermissions(
    actor: EmployeeEntity,
    targetEmployeeId: String,
    newRole: UserRole,
    branchId: String,
    teamId: String?,
    teamName: String?,
    isActive: Boolean,
    permissions: RolePermissionTemplate,
    bypassClientGuardForRulesTest: Boolean = false,
  ): Result<EmployeeEntity> = withContext(Dispatchers.IO) {
    runCatching {
      if (!bypassClientGuardForRulesTest && actor.role != UserRole.OWNER) {
        throw SecurityException("Only the Owner can assign roles or modify employee permissions.")
      }
      val existing = resolveEmployee(targetEmployeeId)
        ?: throw IllegalArgumentException("Employee not found.")

      if (existing.id == actor.id && (!isActive || newRole != UserRole.OWNER)) {
        throw IllegalArgumentException("You cannot deactivate or demote your own active Owner account.")
      }

      val branch = resolveBranch(branchId) ?: throw IllegalArgumentException("Selected branch not found.")
      val effectivePerms = if (newRole == UserRole.OWNER) {
        RolePermissionTemplate.defaultsFor(UserRole.OWNER)
      } else {
        permissions
      }

      val updatedManagerUids = buildList {
        addAll(branch.managerUidsList())
        if (branch.orgOwnerUid.isNotBlank()) add(branch.orgOwnerUid)
        if (newRole == UserRole.BRANCH_MANAGER && isActive) add(existing.id)
      }.filter { uid ->
        if (uid == existing.id && (newRole != UserRole.BRANCH_MANAGER || !isActive) && uid != branch.orgOwnerUid) false else true
      }.distinct().take(20)

      val updatedMemberUids = (branch.memberUidsList() + existing.id).distinct().take(50)

      val updated = existing.copy(
        role = newRole,
        branchId = branch.id,
        branchName = branch.name,
        branchManagerUidsCsv = encodeUidList(updatedManagerUids),
        teamId = teamId,
        teamName = teamName,
        isActive = isActive,
        canRegisterCustomer = effectivePerms.canRegisterCustomer,
        canCreateLoan = effectivePerms.canCreateLoan,
        canApproveLoan = effectivePerms.canApproveLoan,
        canCollectPayment = effectivePerms.canCollectPayment,
        canManageAdvance = effectivePerms.canManageAdvance,
        canRequestPreClosure = effectivePerms.canRequestPreClosure,
        canApprovePreClosure = effectivePerms.canApprovePreClosure,
        canUploadDocuments = effectivePerms.canUploadDocuments,
        canManageTeams = effectivePerms.canManageTeams,
        canViewReports = effectivePerms.canViewReports,
        canViewAuditLogs = effectivePerms.canViewAuditLogs,
      )

      val now = System.currentTimeMillis()
      val audit = AuditLogEntity(
        id = UUID.randomUUID().toString(),
        orgOwnerUid = existing.orgOwnerUid.ifBlank { actor.orgOwnerUid.ifBlank { actor.id } },
        branchManagerUidsCsv = updated.branchManagerUidsCsv,
        actorEmployeeId = actor.id,
        actorName = actor.fullName,
        actorRole = actor.role,
        branchId = branch.id,
        category = "EMPLOYEE",
        action = "ROLE_OR_PERMISSIONS_UPDATED",
        targetType = "EMPLOYEE",
        targetId = updated.employeeCode,
        summary = "Updated ${updated.fullName} (${updated.employeeCode}): role=${newRole.displayTitle}, active=$isActive, branch=${branch.name}",
        timestampDdMmYyyyHhMm = IndianFinanceFormatter.formatDateTimeSeconds(now),
        createdAtMillis = now,
      )

      val fs = firestore
      if (fs != null) {
        try {
          val batch = fs.batch()
          batch.update(
            fs.collection("employees").document(updated.id),
            mapOf(
              "role" to updated.role.name,
              "branchId" to updated.branchId,
              "branchName" to updated.branchName,
              "branchManagerUids" to updated.branchManagerUidsList(),
              "teamId" to (updated.teamId ?: ""),
              "teamName" to (updated.teamName ?: ""),
              "isActive" to updated.isActive,
              "canRegisterCustomer" to updated.canRegisterCustomer,
              "canCreateLoan" to updated.canCreateLoan,
              "canApproveLoan" to updated.canApproveLoan,
              "canCollectPayment" to updated.canCollectPayment,
              "canManageAdvance" to updated.canManageAdvance,
              "canRequestPreClosure" to updated.canRequestPreClosure,
              "canApprovePreClosure" to updated.canApprovePreClosure,
              "canUploadDocuments" to updated.canUploadDocuments,
              "canManageTeams" to updated.canManageTeams,
              "canViewReports" to updated.canViewReports,
              "canViewAuditLogs" to updated.canViewAuditLogs,
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
          if (actor.role == UserRole.OWNER) {
            batch.update(
              fs.collection("branches").document(branch.id),
              mapOf(
                "managerUids" to updatedManagerUids,
                "memberUids" to updatedMemberUids,
                "updatedAt" to FieldValue.serverTimestamp(),
              ),
            )
          }
          batch.set(fs.collection("audit_logs").document(audit.id), FirestorePayloads.auditLogCreate(audit))
          batch.commit().await()
        } catch (e: Exception) {
          handleFirestoreError(e, OperationType.UPDATE, "employees/${updated.id}")
          throw e
        }
      }

      dao.updateEmployee(updated)
      dao.insertBranch(
        branch.copy(
          managerUidsCsv = encodeUidList(updatedManagerUids),
          memberUidsCsv = encodeUidList(updatedMemberUids),
        ),
      )
      dao.insertAuditLog(audit)
      updated
    }
  }

  suspend fun revokeEmployeeInvite(
    actor: EmployeeEntity,
    employeeCode: String,
  ): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
      if (actor.role != UserRole.OWNER) {
        throw SecurityException("Only the Owner can revoke employee invitations.")
      }
      val fs = firestore
      if (fs != null) {
        try {
          fs.collection("employee_invites")
            .document(employeeCode)
            .update(
              mapOf(
                "status" to "REVOKED",
                "isActive" to false,
                "updatedAt" to FieldValue.serverTimestamp(),
              ),
            )
            .await()
        } catch (e: Exception) {
          handleFirestoreError(e, OperationType.UPDATE, "employee_invites/$employeeCode")
          throw e
        }
      }
    }
  }

  private suspend fun resolveBranch(branchId: String): BranchEntity? {
    dao.getBranchById(branchId)?.let { return it }
    val fs = firestore ?: return null
    return try {
      val snap = fs.collection("branches").document(branchId).get().await()
      snap.toObject(FirestoreBranch::class.java)?.toEntity()?.also { dao.insertBranch(it) }
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, "branches/$branchId")
      null
    }
  }

  private suspend fun resolveEmployee(employeeId: String): EmployeeEntity? {
    dao.getEmployeeById(employeeId)?.let { return it }
    val fs = firestore ?: return null
    return try {
      val snap = fs.collection("employees").document(employeeId).get().await()
      snap.toObject(FirestoreEmployee::class.java)?.toEntity()?.also { dao.insertEmployee(it) }
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, "employees/$employeeId")
      null
    }
  }

  private suspend fun nextEmployeeSequence(ownerUid: String): Int {
    val localTotal = dao.countEmployees() + dao.countUnclaimedInvites()
    val fs = firestore
    if (fs != null) {
      val empCount = runCatching {
        fs.collection("employees").whereEqualTo("orgOwnerUid", ownerUid).get().await().size()
      }.getOrDefault(dao.countEmployees())
      val unclaimedInvCount = runCatching {
        fs.collection("employee_invites")
          .whereEqualTo("orgOwnerUid", ownerUid)
          .get()
          .await()
          .documents
          .count { it.getString("status") != "CLAIMED" }
      }.getOrDefault(dao.countUnclaimedInvites())
      return maxOf(empCount + unclaimedInvCount + 1, localTotal + 1)
    }
    return localTotal + 1
  }
}
