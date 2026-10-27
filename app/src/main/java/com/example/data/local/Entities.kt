package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.DocumentType
import com.example.domain.model.InstallmentStatus
import com.example.domain.model.LoanProductType
import com.example.domain.model.LoanStatus
import com.example.domain.model.PaymentAdjustmentType
import com.example.domain.model.PaymentMode
import com.example.domain.model.PaymentRecordStatus
import com.example.domain.model.PaymentType
import com.example.domain.model.PreClosureStatus
import com.example.domain.model.RepaymentFrequency
import com.example.domain.model.SyncStatus
import com.example.domain.model.UserRole

fun encodeUidList(uids: List<String>): String =
  uids.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(20).joinToString(",")

fun decodeUidList(csv: String): List<String> =
  if (csv.isBlank()) emptyList() else csv.split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinct()

@Entity(
  tableName = "branches",
  indices = [Index(value = ["branchCode"], unique = true)],
)
data class BranchEntity(
  @PrimaryKey val id: String,
  val orgOwnerUid: String = "",
  val branchCode: String,
  val name: String,
  val city: String,
  val address: String,
  val managerUidsCsv: String = "",
  val memberUidsCsv: String = "",
  val createdByUid: String = "",
  val createdAtMillis: Long,
) {
  fun managerUidsList(): List<String> = decodeUidList(managerUidsCsv)
  fun memberUidsList(): List<String> = decodeUidList(memberUidsCsv)
}

@Entity(
  tableName = "teams",
  indices = [Index(value = ["branchId"]), Index(value = ["teamCode"], unique = true)],
)
data class TeamEntity(
  @PrimaryKey val id: String,
  val orgOwnerUid: String = "",
  val teamCode: String,
  val name: String,
  val branchId: String,
  val branchName: String,
  val branchManagerUidsCsv: String = "",
  val memberUidsCsv: String = "",
  val managerEmployeeId: String?,
  val managerName: String,
  val areaBeat: String,
  val createdByUid: String = "",
  val createdAtMillis: Long,
) {
  fun branchManagerUidsList(): List<String> = decodeUidList(branchManagerUidsCsv)
  fun memberUidsList(): List<String> = decodeUidList(memberUidsCsv)
}

@Entity(
  tableName = "employees",
  indices = [
    Index(value = ["employeeCode"], unique = true),
    Index(value = ["mobile"]),
    Index(value = ["branchId"]),
  ],
)
data class EmployeeEntity(
  @PrimaryKey val id: String,
  val orgOwnerUid: String = "",
  val employeeCode: String,
  val fullName: String,
  val mobile: String,
  val email: String,
  val role: UserRole,
  val branchId: String,
  val branchName: String,
  val branchManagerUidsCsv: String = "",
  val teamId: String?,
  val teamName: String?,
  val isActive: Boolean,
  val canRegisterCustomer: Boolean,
  val canCreateLoan: Boolean,
  val canApproveLoan: Boolean,
  val canCollectPayment: Boolean,
  val canManageAdvance: Boolean,
  val canRequestPreClosure: Boolean,
  val canApprovePreClosure: Boolean,
  val canUploadDocuments: Boolean,
  val canManageTeams: Boolean,
  val canViewReports: Boolean,
  val canViewAuditLogs: Boolean,
  val createdByEmployeeId: String,
  val createdDateDdMmYyyy: String,
  val createdAtMillis: Long,
) {
  fun branchManagerUidsList(): List<String> = decodeUidList(branchManagerUidsCsv)
}

@Entity(
  tableName = "employee_invites",
  indices = [
    Index(value = ["invitedEmail"]),
    Index(value = ["branchId"]),
  ],
)
data class EmployeeInviteEntity(
  @PrimaryKey val employeeCode: String,
  val orgOwnerUid: String,
  val fullName: String,
  val mobile: String,
  val invitedEmail: String,
  val invitedUid: String = "",
  val role: UserRole,
  val branchId: String,
  val branchName: String,
  val branchManagerUidsCsv: String = "",
  val teamId: String?,
  val teamName: String?,
  val isActive: Boolean,
  val status: String, // PENDING, CLAIMED, REVOKED
  val claimedByUid: String = "",
  val canRegisterCustomer: Boolean,
  val canCreateLoan: Boolean,
  val canApproveLoan: Boolean,
  val canCollectPayment: Boolean,
  val canManageAdvance: Boolean,
  val canRequestPreClosure: Boolean,
  val canApprovePreClosure: Boolean,
  val canUploadDocuments: Boolean,
  val canManageTeams: Boolean,
  val canViewReports: Boolean,
  val canViewAuditLogs: Boolean,
  val createdByEmployeeId: String,
  val createdDateDdMmYyyy: String,
  val createdAtMillis: Long,
)

@Entity(
  tableName = "customers",
  indices = [
    Index(value = ["customerCode"], unique = true),
    Index(value = ["mobile"]),
    Index(value = ["branchId"]),
    Index(value = ["assignedAgentId"]),
  ],
)
data class CustomerEntity(
  @PrimaryKey val id: String,
  val orgOwnerUid: String = "",
  val branchManagerUidsCsv: String = "",
  val customerCode: String,
  val fullName: String,
  val guardianName: String,
  val mobile: String,
  val alternateMobile: String,
  val address: String,
  val city: String,
  val kycDocumentType: String,
  val kycDocumentNumber: String,
  val occupation: String,
  val monthlyIncomePaise: Long,
  val branchId: String,
  val branchName: String,
  val teamId: String?,
  val assignedAgentId: String,
  val assignedAgentName: String,
  val createdByEmployeeId: String,
  val createdDateDdMmYyyy: String,
  val createdAtMillis: Long,
) {
  fun branchManagerUidsList(): List<String> = decodeUidList(branchManagerUidsCsv)
}

@Entity(
  tableName = "customer_documents",
  indices = [Index(value = ["customerId"])],
)
data class CustomerDocumentEntity(
  @PrimaryKey val id: String,
  val orgOwnerUid: String = "",
  val branchManagerUidsCsv: String = "",
  val assignedAgentId: String = "",
  val customerId: String,
  val customerCode: String,
  val branchId: String,
  val documentType: DocumentType,
  val documentNumber: String,
  val fileName: String,
  val localFilePath: String,
  val mimeType: String,
  val fileSizeBytes: Long,
  val sha256Checksum: String,
  val uploadedByEmployeeId: String,
  val uploadedByName: String,
  val uploadedDateDdMmYyyy: String,
  val createdAtMillis: Long,
)

@Entity(
  tableName = "loans",
  indices = [
    Index(value = ["loanCode"], unique = true),
    Index(value = ["customerId"]),
    Index(value = ["branchId"]),
    Index(value = ["assignedAgentId"]),
  ],
)
data class LoanEntity(
  @PrimaryKey val id: String,
  val orgOwnerUid: String = "",
  val branchManagerUidsCsv: String = "",
  val loanCode: String,
  val customerId: String,
  val customerCode: String,
  val customerName: String,
  val customerMobile: String,
  val branchId: String,
  val branchName: String,
  val teamId: String?,
  val assignedAgentId: String,
  val assignedAgentName: String,
  val productType: LoanProductType,
  val principalPaise: Long,
  val annualInterestRateBps: Int,
  val processingFeePaise: Long,
  val totalInterestPaise: Long,
  val totalPayablePaise: Long,
  val installmentAmountPaise: Long,
  val repaymentFrequency: RepaymentFrequency,
  val totalInstallments: Int,
  val paidAmountPaise: Long,
  val advanceBalancePaise: Long,
  val outstandingPaise: Long,
  val status: LoanStatus,
  val applicationDateDdMmYyyy: String,
  val approvedDateDdMmYyyy: String = "",
  val approvalRemarks: String = "",
  val disbursementDateDdMmYyyy: String,
  val disbursementMode: PaymentMode = PaymentMode.CASH,
  val disbursementReference: String = "",
  val netDisbursedPaise: Long = 0L,
  val firstDueDateDdMmYyyy: String,
  val maturityDateDdMmYyyy: String,
  val closedDateDdMmYyyy: String = "",
  val createdByEmployeeId: String,
  val approvedByEmployeeId: String?,
  val disbursedByEmployeeId: String? = null,
  val rejectedByEmployeeId: String? = null,
  val rejectionReason: String = "",
  val createdAtMillis: Long,
) {
  fun branchManagerUidsList(): List<String> = decodeUidList(branchManagerUidsCsv)
}

@Entity(
  tableName = "installment_schedules",
  indices = [
    Index(value = ["loanId"]),
    Index(value = ["dueDateDdMmYyyy"]),
    Index(value = ["branchId"]),
    Index(value = ["assignedAgentId"]),
  ],
)
data class InstallmentScheduleEntity(
  @PrimaryKey val id: String,
  val orgOwnerUid: String = "",
  val branchManagerUidsCsv: String = "",
  val loanId: String,
  val loanCode: String,
  val customerId: String,
  val customerCode: String,
  val customerName: String,
  val customerMobile: String,
  val branchId: String,
  val assignedAgentId: String,
  val installmentNumber: Int,
  val dueDateDdMmYyyy: String,
  val dueDateEpochMillis: Long,
  val frequency: RepaymentFrequency,
  val scheduledAmountPaise: Long,
  val paidAmountPaise: Long,
  val status: InstallmentStatus,
  val paidDateDdMmYyyy: String?,
  val lastPaymentId: String?,
) {
  fun branchManagerUidsList(): List<String> = decodeUidList(branchManagerUidsCsv)
}

@Entity(
  tableName = "payments",
  indices = [
    Index(value = ["receiptNumber"], unique = true),
    Index(value = ["transactionId"], unique = true),
    Index(value = ["idempotencyKey"], unique = true),
    Index(value = ["loanId"]),
    Index(value = ["customerId"]),
    Index(value = ["branchId"]),
    Index(value = ["collectedByEmployeeId"]),
    Index(value = ["syncStatus"]),
    Index(value = ["recordStatus"]),
  ],
)
data class PaymentEntity(
  @PrimaryKey val id: String,
  val orgOwnerUid: String = "",
  val branchManagerUidsCsv: String = "",
  val assignedAgentId: String = "",
  val transactionId: String,
  val receiptNumber: String,
  val idempotencyKey: String,
  val loanId: String,
  val loanCode: String,
  val customerId: String,
  val customerCode: String,
  val customerName: String,
  val branchId: String,
  val branchName: String,
  val collectedByEmployeeId: String,
  val collectedByName: String,
  val amountPaise: Long,
  val allocatedToInstallmentsPaise: Long,
  val creditedToAdvancePaise: Long,
  val allocationsSummary: String = "",
  val paymentMode: PaymentMode,
  val paymentType: PaymentType,
  val referenceNumber: String,
  val remarks: String,
  val collectionDateDdMmYyyy: String,
  val collectedAtMillis: Long,
  val syncStatus: SyncStatus,
  val serverConfirmationId: String?,
  val serverConfirmedAtMillis: Long?,
  val lastSyncError: String?,
  val recordStatus: PaymentRecordStatus = PaymentRecordStatus.ACTIVE,
  val reversedByEmployeeId: String? = null,
  val reversedByName: String? = null,
  val reversedDateDdMmYyyy: String? = null,
  val reversalReason: String? = null,
  val adjustmentId: String? = null,
  val replacementPaymentId: String? = null,
  val correctsPaymentId: String? = null,
) {
  fun branchManagerUidsList(): List<String> = decodeUidList(branchManagerUidsCsv)
}

@Entity(
  tableName = "payment_adjustments",
  indices = [
    Index(value = ["adjustmentCode"], unique = true),
    Index(value = ["loanId"]),
    Index(value = ["customerId"]),
    Index(value = ["branchId"]),
    Index(value = ["originalPaymentId"]),
  ],
)
data class PaymentAdjustmentEntity(
  @PrimaryKey val id: String,
  val orgOwnerUid: String = "",
  val branchManagerUidsCsv: String = "",
  val assignedAgentId: String = "",
  val adjustmentCode: String,
  val adjustmentType: PaymentAdjustmentType,
  val loanId: String,
  val loanCode: String,
  val customerId: String,
  val customerCode: String,
  val customerName: String,
  val branchId: String,
  val originalPaymentId: String,
  val originalTransactionId: String,
  val originalReceiptNumber: String,
  val originalAmountPaise: Long,
  val replacementPaymentId: String? = null,
  val replacementTransactionId: String? = null,
  val replacementReceiptNumber: String? = null,
  val replacementAmountPaise: Long = 0L,
  val reason: String,
  val performedByEmployeeId: String,
  val performedByName: String,
  val performedDateDdMmYyyy: String,
  val createdAtMillis: Long,
) {
  fun branchManagerUidsList(): List<String> = decodeUidList(branchManagerUidsCsv)
}

@Entity(
  tableName = "advance_transactions",
  indices = [Index(value = ["loanId"]), Index(value = ["customerId"])],
)
data class AdvanceTransactionEntity(
  @PrimaryKey val id: String,
  val orgOwnerUid: String = "",
  val branchManagerUidsCsv: String = "",
  val assignedAgentId: String = "",
  val loanId: String,
  val loanCode: String,
  val customerId: String,
  val branchId: String,
  val entryType: String, // CREDIT_ADVANCE, CREDIT_OVERPAYMENT, APPLIED_INSTALLMENTS, REVERSAL_DEBIT
  val amountPaise: Long,
  val balanceAfterPaise: Long,
  val relatedPaymentId: String?,
  val note: String,
  val dateDdMmYyyy: String,
  val createdByEmployeeId: String,
  val createdByName: String,
  val createdAtMillis: Long,
) {
  fun branchManagerUidsList(): List<String> = decodeUidList(branchManagerUidsCsv)
}

@Entity(
  tableName = "preclosure_requests",
  indices = [Index(value = ["loanId"]), Index(value = ["customerId"]), Index(value = ["branchId"])],
)
data class PreClosureRequestEntity(
  @PrimaryKey val id: String,
  val orgOwnerUid: String = "",
  val branchManagerUidsCsv: String = "",
  val assignedAgentId: String = "",
  val requestCode: String,
  val loanId: String,
  val loanCode: String,
  val customerId: String,
  val customerCode: String,
  val customerName: String,
  val branchId: String,
  val outstandingBeforePaise: Long,
  val proposedRebatePaise: Long,
  val preClosureChargePaise: Long,
  val netSettlementPaise: Long,
  val status: PreClosureStatus,
  val reason: String,
  val requestedByEmployeeId: String,
  val requestedByName: String,
  val requestedDateDdMmYyyy: String,
  val reviewedByEmployeeId: String?,
  val reviewedByName: String?,
  val reviewedDateDdMmYyyy: String?,
  val reviewRemarks: String?,
  val settlementPaymentId: String?,
  val createdAtMillis: Long,
) {
  val advanceAdjustedPaise: Long
    get() = (outstandingBeforePaise - proposedRebatePaise + preClosureChargePaise - netSettlementPaise).coerceAtLeast(0L)

  fun branchManagerUidsList(): List<String> = decodeUidList(branchManagerUidsCsv)
}

@Entity(
  tableName = "sync_queue",
  indices = [Index(value = ["paymentId"], unique = true), Index(value = ["syncStatus"])],
)
data class SyncQueueEntity(
  @PrimaryKey val id: String,
  val paymentId: String,
  val transactionId: String = "",
  val idempotencyKey: String = "",
  val receiptNumber: String,
  val loanCode: String,
  val customerName: String,
  val amountPaise: Long,
  val branchId: String,
  val collectedByEmployeeId: String,
  val payloadJson: String,
  val syncStatus: SyncStatus,
  val attemptCount: Int,
  val createdAtMillis: Long,
  val lastAttemptAtMillis: Long?,
  val confirmedAtMillis: Long?,
  val serverConfirmationId: String?,
  val errorMessage: String?,
)

@Entity(
  tableName = "audit_logs",
  indices = [Index(value = ["branchId"]), Index(value = ["category"]), Index(value = ["createdAtMillis"])],
)
data class AuditLogEntity(
  @PrimaryKey val id: String,
  val orgOwnerUid: String = "",
  val branchManagerUidsCsv: String = "",
  val actorEmployeeId: String,
  val actorName: String,
  val actorRole: UserRole,
  val branchId: String,
  val category: String, // AUTH, EMPLOYEE, TEAM, CUSTOMER, LOAN, PAYMENT, ADVANCE, PRECLOSURE, SYNC
  val action: String,
  val targetType: String,
  val targetId: String,
  val summary: String,
  val timestampDdMmYyyyHhMm: String,
  val createdAtMillis: Long,
) {
  fun branchManagerUidsList(): List<String> = decodeUidList(branchManagerUidsCsv)
}
