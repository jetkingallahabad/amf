package com.example.data.sync

import com.example.data.local.AuditLogEntity
import com.example.data.local.EmployeeEntity
import com.example.data.local.LoanFinanceDao
import com.example.data.local.PaymentEntity
import com.example.data.local.SyncQueueEntity
import com.example.data.remote.FirestorePayloads
import com.example.data.remote.FirestorePayment
import com.example.domain.model.SyncStatus
import com.example.domain.model.UserRole
import com.example.util.IndianFinanceFormatter
import com.example.util.OperationType
import com.example.util.handleFirestoreError
import com.example.util.toUserFriendlyFirestoreMessage
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.UUID

/**
 * Cloud Firestore Synchronization Engine for offline & online payment collection entries.
 *
 * CRITICAL INTEGRITY INVARIANTS:
 * 1. Never fakes server confirmation. A queued payment transitions to [SyncStatus.SERVER_CONFIRMED]
 *    ONLY after Cloud Firestore acknowledges the atomic WriteBatch (or confirms an already-committed
 *    idempotent payment document on retry).
 * 2. When offline or unreachable, locally committed payments remain safely stored in Room SQLite as
 *    [SyncStatus.CONFIRMED_LOCALLY_PENDING_SYNC] or [SyncStatus.SYNC_FAILED].
 * 3. Server-side idempotency check prevents duplicate payment documents and double balance updates
 *    when retrying after transient network drops.
 */
class SyncEngine(
  private val dao: LoanFinanceDao,
  private val firestore: FirebaseFirestore? = null,
) {

  private val syncMutex = Mutex()

  data class SyncBatchResult(
    val totalPending: Int,
    val confirmedCount: Int,
    val remainingPendingCount: Int,
    val message: String,
  )

  suspend fun syncSinglePayment(
    paymentId: String,
    actor: EmployeeEntity,
    markFailedOnError: Boolean,
    timeoutMs: Long = 10_000L,
  ): Result<PaymentEntity> = withContext(Dispatchers.IO) {
    syncMutex.withLock {
      syncSinglePaymentLocked(
        paymentId = paymentId,
        actor = actor,
        markFailedOnError = markFailedOnError,
        timeoutMs = timeoutMs,
      )
    }
  }

  private suspend fun syncSinglePaymentLocked(
    paymentId: String,
    actor: EmployeeEntity,
    markFailedOnError: Boolean,
    timeoutMs: Long,
  ): Result<PaymentEntity> {
    val payment = dao.getPaymentById(paymentId)
      ?: return Result.failure(IllegalArgumentException("Payment $paymentId not found locally."))
    val queueItem = dao.getSyncQueueItemByPaymentId(paymentId)
      ?: return Result.failure(IllegalArgumentException("Sync queue entry missing for $paymentId."))

    if (payment.syncStatus == SyncStatus.SERVER_CONFIRMED && queueItem.syncStatus == SyncStatus.SERVER_CONFIRMED) {
      return Result.success(payment)
    }

    val fs = firestore
    if (fs == null) {
      return Result.success(payment)
    }

    // Firestore Security Rules require d.collectedByUid == request.auth.uid on payment creation
    if (payment.collectedByEmployeeId != actor.id) {
      return Result.failure(
        SecurityException(
          "Receipt ${payment.receiptNumber} must be synchronized by collecting officer ${payment.collectedByName}.",
        ),
      )
    }

    val loan = dao.getLoanById(payment.loanId)
      ?: return Result.failure(IllegalArgumentException("Associated loan ${payment.loanId} not found."))
    val touchedSchedules = dao.getScheduleForLoan(loan.id).filter { it.lastPaymentId == payment.id }
    val advanceTx = dao.getAdvanceTransactionByPaymentId(payment.id)
    val settledPreClosure = dao.getPreClosureBySettlementPaymentId(payment.id)

    val serverConfirmationId = "FS-ACK-${payment.receiptNumber}"
    val confirmedMillis = System.currentTimeMillis()
    val ownerUid = payment.orgOwnerUid.ifBlank { actor.orgOwnerUid.ifBlank { actor.id } }

    val syncAudit = AuditLogEntity(
      id = UUID.randomUUID().toString(),
      orgOwnerUid = ownerUid,
      branchManagerUidsCsv = payment.branchManagerUidsCsv,
      actorEmployeeId = actor.id,
      actorName = actor.fullName,
      actorRole = actor.role,
      branchId = payment.branchId,
      category = "SYNC",
      action = "PAYMENT_SERVER_CONFIRMED",
      targetType = "PAYMENT",
      targetId = payment.receiptNumber,
      summary = "Receipt ${payment.receiptNumber} (Txn: ${payment.transactionId}) confirmed on Cloud Firestore (Ref: $serverConfirmationId)",
      timestampDdMmYyyyHhMm = IndianFinanceFormatter.formatDateTimeSeconds(confirmedMillis),
      createdAtMillis = confirmedMillis,
    )

    return try {
      var deduplicatedRemotePayment: PaymentEntity? = null

      withTimeout(timeoutMs) {
        // 0. Server-side idempotency check (role-scoped list query aligned with firestore.rules)
        val duplicateQuery = when (actor.role) {
          UserRole.OWNER -> fs.collection("payments")
            .whereEqualTo("orgOwnerUid", ownerUid)
            .whereEqualTo("idempotencyKey", payment.idempotencyKey)
            .limit(1)
          UserRole.BRANCH_MANAGER -> fs.collection("payments")
            .whereArrayContains("branchManagerUids", actor.id)
            .whereEqualTo("idempotencyKey", payment.idempotencyKey)
            .limit(1)
          UserRole.COLLECTION_AGENT -> fs.collection("payments")
            .whereEqualTo("collectedByUid", actor.id)
            .whereEqualTo("idempotencyKey", payment.idempotencyKey)
            .limit(1)
        }
        val existingSnap = duplicateQuery.get().await()
        if (!existingSnap.isEmpty) {
          deduplicatedRemotePayment = existingSnap.documents.first()
            .toObject(FirestorePayment::class.java)
            ?.toEntity()
        } else {
          // Batch 1: Core non-deletable payment receipt + loan balance/status update (< 700 CEL)
          val coreBatch = fs.batch()
          val paymentRef = fs.collection("payments").document(payment.id)
          coreBatch.set(paymentRef, FirestorePayloads.paymentCreate(payment, serverConfirmationId))

          val loanRef = fs.collection("loans").document(loan.id)
          coreBatch.update(
            loanRef,
            mapOf(
              "paidAmountPaise" to loan.paidAmountPaise,
              "advanceBalancePaise" to loan.advanceBalancePaise,
              "outstandingPaise" to loan.outstandingPaise,
              "status" to loan.status.name,
              "closedDateDdMmYyyy" to loan.closedDateDdMmYyyy,
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
          coreBatch.commit().await()

          // Batch 2: Optional Advance Ledger, Pre-Closure settlement status, and Immutable Cloud Audit Log (< 550 CEL)
          val ledgerBatch = fs.batch()
          if (advanceTx != null) {
            val advRef = fs.collection("advance_transactions").document(advanceTx.id)
            ledgerBatch.set(advRef, FirestorePayloads.advanceTxCreate(advanceTx))
          }
          if (settledPreClosure != null) {
            val pcrRef = fs.collection("preclosure_requests").document(settledPreClosure.id)
            ledgerBatch.update(
              pcrRef,
              mapOf(
                "status" to settledPreClosure.status.name,
                "reviewedByUid" to (settledPreClosure.reviewedByEmployeeId ?: actor.id),
                "reviewedByName" to (settledPreClosure.reviewedByName ?: actor.fullName),
                "reviewedDateDdMmYyyy" to (settledPreClosure.reviewedDateDdMmYyyy ?: payment.collectionDateDdMmYyyy),
                "reviewRemarks" to (settledPreClosure.reviewRemarks ?: "Approved and settled"),
                "settlementPaymentId" to payment.id,
                "updatedAt" to FieldValue.serverTimestamp(),
              ),
            )
          }
          val auditRef = fs.collection("audit_logs").document(syncAudit.id)
          ledgerBatch.set(auditRef, FirestorePayloads.auditLogCreate(syncAudit))
          ledgerBatch.commit().await()

          // Schedule updates in compact chunks of 2 (< 850 CEL per chunk including serverTimestamp transforms)
          for (chunk in touchedSchedules.chunked(2)) {
            val extraBatch = fs.batch()
            for (sched in chunk) {
              val schedRef = fs.collection("installment_schedules").document(sched.id)
              extraBatch.update(
                schedRef,
                mapOf(
                  "paidAmountPaise" to sched.paidAmountPaise,
                  "status" to sched.status.name,
                  "paidDateDdMmYyyy" to (sched.paidDateDdMmYyyy ?: ""),
                  "lastPaymentId" to (sched.lastPaymentId ?: ""),
                  "updatedAt" to FieldValue.serverTimestamp(),
                ),
              )
            }
            extraBatch.commit().await()
          }
        }
      }

      val effectiveAckId = deduplicatedRemotePayment?.serverConfirmationId?.takeIf { it.isNotBlank() }
        ?: serverConfirmationId
      val updatedQueue = queueItem.copy(
        syncStatus = SyncStatus.SERVER_CONFIRMED,
        attemptCount = queueItem.attemptCount + 1,
        lastAttemptAtMillis = confirmedMillis,
        confirmedAtMillis = confirmedMillis,
        serverConfirmationId = effectiveAckId,
        errorMessage = null,
      )
      val basePayment = deduplicatedRemotePayment ?: payment
      val updatedPayment = basePayment.copy(
        syncStatus = SyncStatus.SERVER_CONFIRMED,
        serverConfirmationId = effectiveAckId,
        serverConfirmedAtMillis = confirmedMillis,
        lastSyncError = null,
      )
      val finalAudit = if (deduplicatedRemotePayment != null) {
        syncAudit.copy(
          action = "PAYMENT_SYNC_DEDUPLICATED",
          summary = "Idempotency verification matched existing server receipt ${updatedPayment.receiptNumber} (Txn: ${updatedPayment.transactionId}); duplicate submission prevented.",
        )
      } else {
        syncAudit
      }
      dao.markPaymentServerConfirmed(updatedQueue, updatedPayment, finalAudit)
      Result.success(updatedPayment)
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, "payments/${payment.id}")
      val friendlyReason = toUserFriendlyFirestoreMessage(e, "Confirmed locally; Cloud Firestore unreachable.")
      if (markFailedOnError) {
        recordSyncFailure(queueItem, payment, System.currentTimeMillis(), friendlyReason)
      }
      Result.failure(e)
    }
  }

  suspend fun processPendingQueue(
    actor: EmployeeEntity,
    timeoutMs: Long = 10_000L,
  ): SyncBatchResult = withContext(Dispatchers.IO) {
    val allPendingItems = dao.getPendingSyncItems()
    if (allPendingItems.isEmpty()) {
      return@withContext SyncBatchResult(
        totalPending = 0,
        confirmedCount = 0,
        remainingPendingCount = 0,
        message = "All collection receipts are synchronized and confirmed on Cloud Firestore.",
      )
    }

    if (firestore == null) {
      return@withContext SyncBatchResult(
        totalPending = allPendingItems.size,
        confirmedCount = 0,
        remainingPendingCount = allPendingItems.size,
        message = "Offline mode active. ${allPendingItems.size} receipt(s) are confirmed locally and awaiting server sync.",
      )
    }

    val actorItems = allPendingItems.filter { it.collectedByEmployeeId == actor.id }
    val otherOfficerCount = allPendingItems.size - actorItems.size

    var confirmed = 0
    var lastError: String? = null
    for (item in actorItems) {
      val syncRes = syncSinglePayment(
        paymentId = item.paymentId,
        actor = actor,
        markFailedOnError = true,
        timeoutMs = timeoutMs,
      )
      if (syncRes.isSuccess && syncRes.getOrNull()?.syncStatus == SyncStatus.SERVER_CONFIRMED) {
        confirmed++
      } else {
        lastError = syncRes.exceptionOrNull()?.let { toUserFriendlyFirestoreMessage(it) }
      }
    }

    val remaining = allPendingItems.size - confirmed
    val summaryMsg = when {
      confirmed == allPendingItems.size ->
        "Synchronized and server-confirmed $confirmed receipt(s) on Cloud Firestore."
      confirmed > 0 && otherOfficerCount > 0 && confirmed == actorItems.size ->
        "Confirmed $confirmed receipt(s). $otherOfficerCount receipt(s) remain queued for their collecting officer."
      confirmed > 0 ->
        "Confirmed $confirmed of ${allPendingItems.size} receipt(s). $remaining remain pending sync (${lastError ?: "retry queued"})."
      actorItems.isEmpty() && otherOfficerCount > 0 ->
        "$otherOfficerCount queued receipt(s) belong to another collecting officer session."
      else ->
        "Could not confirm $remaining queued receipt(s): ${lastError ?: "check network connectivity"}."
    }

    SyncBatchResult(
      totalPending = allPendingItems.size,
      confirmedCount = confirmed,
      remainingPendingCount = remaining,
      message = summaryMsg,
    )
  }

  private suspend fun recordSyncFailure(
    item: SyncQueueEntity,
    payment: PaymentEntity,
    now: Long,
    reason: String,
  ) {
    dao.updateSyncQueueItem(
      item.copy(
        syncStatus = SyncStatus.SYNC_FAILED,
        attemptCount = item.attemptCount + 1,
        lastAttemptAtMillis = now,
        errorMessage = reason,
      ),
    )
    dao.updatePayment(
      payment.copy(
        syncStatus = SyncStatus.SYNC_FAILED,
        lastSyncError = reason,
      ),
    )
  }
}
