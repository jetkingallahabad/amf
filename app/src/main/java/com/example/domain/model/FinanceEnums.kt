package com.example.domain.model

import com.example.util.IndianFinanceFormatter
import java.math.BigDecimal
import java.math.RoundingMode

enum class UserRole(val displayTitle: String) {
  OWNER("Owner"),
  BRANCH_MANAGER("Branch Manager"),
  COLLECTION_AGENT("Collection Agent"),
}

enum class RepaymentFrequency(val displayTitle: String, val stepDays: Int, val shortLabel: String) {
  DAILY("Daily EDI", 1, "EDI"),
  WEEKLY("Weekly EMI", 7, "EMI"),
}

enum class LoanProductType(val displayTitle: String, val defaultRatePercent: String) {
  DAILY_BUSINESS_EDI("Daily Trade EDI Loan", "18"),
  WEEKLY_GROUP_EMI("Weekly Micro EMI Loan", "20"),
  MICRO_ENTERPRISE("Small Enterprise Finance", "16"),
  EMERGENCY_BRIDGE("Short-Term Working Capital", "22"),
}

enum class LoanStatus(val displayTitle: String) {
  PENDING_APPROVAL("Pending Approval"),
  APPROVED("Approved • Ready to Disburse"),
  ACTIVE("Active"),
  PRE_CLOSURE_REQUESTED("Pre-Closure Requested"),
  COMPLETED("Completed"),
  PRE_CLOSED("Pre-Closed"),
  REJECTED("Rejected"),
  ;

  val isActiveCollectible: Boolean
    get() = this == ACTIVE || this == PRE_CLOSURE_REQUESTED

  val isHistoricalClosed: Boolean
    get() = this == COMPLETED || this == PRE_CLOSED || this == REJECTED
}

enum class InstallmentStatus(val displayTitle: String) {
  UPCOMING("Upcoming"),
  PARTIAL("Partially Paid"),
  PAID("Paid"),
  WAIVED_PRECLOSURE("Waived (Pre-Closure)"),
}

enum class PaymentMode(val displayTitle: String) {
  CASH("Cash"),
  UPI("UPI"),
  BANK_TRANSFER("NEFT / IMPS / RTGS"),
  CHEQUE("Cheque"),
  ADVANCE_ADJUSTMENT("Advance Adjustment"),
}

enum class PaymentType(val displayTitle: String) {
  REGULAR_INSTALLMENT("Regular EMI/EDI Collection"),
  ADVANCE_COLLECTION("Advance Deposit"),
  PRE_CLOSURE_SETTLEMENT("Pre-Closure Settlement"),
}

enum class SyncStatus(val displayTitle: String) {
  CONFIRMED_LOCALLY_PENDING_SYNC("Confirmed Locally • Pending Sync"),
  SYNCING("Syncing to Server…"),
  SERVER_CONFIRMED("Server Confirmed"),
  SYNC_FAILED("Confirmed Locally • Retry Queued"),
}

enum class PaymentRecordStatus(val displayTitle: String) {
  ACTIVE("Active"),
  REVERSED("Reversed"),
  CORRECTED("Corrected (Replaced)"),
}

enum class PaymentAdjustmentType(val displayTitle: String) {
  REVERSAL("Payment Reversal"),
  CORRECTION("Payment Correction"),
}

enum class PreClosureStatus(val displayTitle: String) {
  PENDING("Pending Review"),
  APPROVED_SETTLED("Approved & Settled"),
  REJECTED("Rejected"),
}

enum class DocumentType(val displayTitle: String) {
  AADHAAR_CARD("Aadhaar Card"),
  PAN_CARD("PAN Card"),
  VOTER_ID("Voter ID"),
  DRIVING_LICENCE("Driving Licence"),
  SHOP_ESTABLISHMENT("Shop / Trade Licence"),
  ADDRESS_PROOF("Address Proof"),
  PASSPORT_PHOTO("Customer Photograph"),
  INCOME_PROOF("Business / Income Proof"),
  LOAN_AGREEMENT("Signed Loan Agreement"),
}

enum class SearchFilterMode(val label: String) {
  ALL("All"),
  CUSTOMER_ID("Customer ID"),
  NAME("Name"),
  MOBILE("Mobile"),
  LOAN_ID("Loan ID"),
}

object CustomerKycValidator {
  val PRIMARY_KYC_OPTIONS = listOf(
    "Aadhaar Card",
    "PAN Card",
    "Voter ID",
    "Driving Licence",
    "Shop / Trade Licence",
  )

  private val INDIAN_MOBILE_REGEX = Regex("^[6-9]\\d{9}$")
  private val AADHAAR_REGEX = Regex("^\\d{12}$")
  private val PAN_REGEX = Regex("^[A-Z]{5}\\d{4}[A-Z]$")
  private val GENERIC_KYC_REGEX = Regex("^[A-Z0-9\\-/]{4,30}$")

  fun validateIndianMobile(rawMobile: String, fieldLabel: String = "Mobile number"): String {
    val digits = rawMobile.filter { it.isDigit() }
    require(digits.length == 10 && INDIAN_MOBILE_REGEX.matches(digits)) {
      "$fieldLabel must be a valid 10-digit Indian mobile number starting with 6–9."
    }
    return digits
  }

  fun normalizeAndValidateKyc(kycType: String, rawKycNumber: String): String {
    val normalizedType = kycType.trim()
    require(normalizedType.length in 2..60) { "Select a valid KYC document type." }
    val cleanNumber = rawKycNumber.trim().replace(" ", "").uppercase()
    require(cleanNumber.isNotEmpty()) { "KYC ID number is required." }

    when {
      normalizedType.contains("Aadhaar", ignoreCase = true) -> {
        require(AADHAAR_REGEX.matches(cleanNumber)) {
          "Aadhaar number must be exactly 12 digits."
        }
      }
      normalizedType.contains("PAN", ignoreCase = true) -> {
        require(PAN_REGEX.matches(cleanNumber)) {
          "PAN number must match standard format (e.g. ABCDE1234F)."
        }
      }
      else -> {
        require(GENERIC_KYC_REGEX.matches(cleanNumber)) {
          "KYC ID number must be 4–30 alphanumeric characters."
        }
      }
    }
    return cleanNumber
  }
}

data class RolePermissionTemplate(
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
) {
  companion object {
    fun defaultsFor(role: UserRole): RolePermissionTemplate = when (role) {
      UserRole.OWNER -> RolePermissionTemplate(
        canRegisterCustomer = true,
        canCreateLoan = true,
        canApproveLoan = true,
        canCollectPayment = true,
        canManageAdvance = true,
        canRequestPreClosure = true,
        canApprovePreClosure = true,
        canUploadDocuments = true,
        canManageTeams = true,
        canViewReports = true,
        canViewAuditLogs = true,
      )
      UserRole.BRANCH_MANAGER -> RolePermissionTemplate(
        canRegisterCustomer = true,
        canCreateLoan = true,
        canApproveLoan = true,
        canCollectPayment = true,
        canManageAdvance = true,
        canRequestPreClosure = true,
        canApprovePreClosure = false,
        canUploadDocuments = true,
        canManageTeams = true,
        canViewReports = true,
        canViewAuditLogs = true,
      )
      UserRole.COLLECTION_AGENT -> RolePermissionTemplate(
        canRegisterCustomer = true,
        canCreateLoan = false,
        canApproveLoan = false,
        canCollectPayment = true,
        canManageAdvance = true,
        canRequestPreClosure = true,
        canApprovePreClosure = false,
        canUploadDocuments = true,
        canManageTeams = false,
        canViewReports = true,
        canViewAuditLogs = false,
      )
    }
  }
}

/**
 * Deterministic loan schedule calculator using exact Paise arithmetic.
 */
object LoanScheduleCalculator {

  data class LoanQuote(
    val principalPaise: Long,
    val annualInterestRateBps: Int,
    val processingFeePaise: Long,
    val totalInterestPaise: Long,
    val totalPayablePaise: Long,
    val installmentAmountPaise: Long,
    val lastInstallmentAmountPaise: Long,
    val totalInstallments: Int,
    val frequency: RepaymentFrequency,
    val firstDueDateDdMmYyyy: String,
    val maturityDateDdMmYyyy: String,
  )

  fun calculateQuote(
    principalPaise: Long,
    annualRatePercent: Double,
    processingFeePaise: Long,
    totalInstallments: Int,
    frequency: RepaymentFrequency,
    disbursementDateDdMmYyyy: String,
  ): LoanQuote? {
    if (principalPaise <= 0L || totalInstallments <= 0 || annualRatePercent < 0.0) return null
    if (IndianFinanceFormatter.parseDdMmYyyyToMillis(disbursementDateDdMmYyyy) == null) return null

    val rateBps = (annualRatePercent * 100.0).toInt()
    val durationDays = totalInstallments * frequency.stepDays
    // Proportional flat interest for microfinance EDI/EMI clarity: P * R * (days / 365)
    val interestBd = BigDecimal(principalPaise)
      .multiply(BigDecimal.valueOf(annualRatePercent))
      .multiply(BigDecimal(durationDays))
      .divide(BigDecimal(36500), 0, RoundingMode.HALF_UP)

    val totalInterestPaise = interestBd.toLong().coerceAtLeast(0L)
    val totalPayablePaise = principalPaise + totalInterestPaise

    // Round regular installment to nearest ₹1 (100 paise) if > 1 installment, adjusting final installment
    val rawInstallment = totalPayablePaise / totalInstallments
    val regularInstallment = if (totalInstallments == 1) {
      totalPayablePaise
    } else {
      ((rawInstallment + 50L) / 100L) * 100L
    }.coerceAtLeast(100L)

    val sumBeforeLast = regularInstallment * (totalInstallments - 1)
    val lastInstallment = (totalPayablePaise - sumBeforeLast).coerceAtLeast(0L)

    val (firstDue, _) = IndianFinanceFormatter.addDays(disbursementDateDdMmYyyy, frequency.stepDays)
    val (maturity, _) = IndianFinanceFormatter.addDays(
      disbursementDateDdMmYyyy,
      frequency.stepDays * totalInstallments,
    )

    return LoanQuote(
      principalPaise = principalPaise,
      annualInterestRateBps = rateBps,
      processingFeePaise = processingFeePaise,
      totalInterestPaise = totalInterestPaise,
      totalPayablePaise = totalPayablePaise,
      installmentAmountPaise = regularInstallment,
      lastInstallmentAmountPaise = lastInstallment,
      totalInstallments = totalInstallments,
      frequency = frequency,
      firstDueDateDdMmYyyy = firstDue,
      maturityDateDdMmYyyy = maturity,
    )
  }
}

/**
 * Deterministic payment allocation & installment due calculator using exact Paise arithmetic.
 */
object PaymentAllocationCalculator {

  data class AllocationSlice(
    val scheduleId: String,
    val installmentNumber: Int,
    val dueDateDdMmYyyy: String,
    val scheduledAmountPaise: Long,
    val paidBeforePaise: Long,
    val allocatedAmountPaise: Long,
    val paidAfterPaise: Long,
    val statusAfter: InstallmentStatus,
  )

  data class LoanDueSummary(
    val dueTodayPaise: Long,
    val dueTodayCount: Int,
    val overduePaise: Long,
    val overdueCount: Int,
    val nextDueDateDdMmYyyy: String?,
    val nextDueRemainingPaise: Long,
    val totalInstallmentRemainingPaise: Long,
    val totalOutstandingPaise: Long,
    val advanceBalancePaise: Long,
  ) {
    val actionableDueNowPaise: Long
      get() = when {
        overduePaise + dueTodayPaise > 0L -> overduePaise + dueTodayPaise
        nextDueRemainingPaise > 0L -> nextDueRemainingPaise
        else -> totalOutstandingPaise
      }
  }

  data class AllocationPlan(
    val slices: List<AllocationSlice>,
    val totalAllocatedPaise: Long,
    val creditedToAdvancePaise: Long,
    val summaryText: String,
  )

  fun computeLoanDueSummary(
    outstandingPaise: Long,
    advanceBalancePaise: Long,
    schedules: List<ScheduleSnapshot>,
    todayDdMmYyyy: String = IndianFinanceFormatter.todayDdMmYyyy(),
  ): LoanDueSummary {
    val todayMillis = IndianFinanceFormatter.parseDdMmYyyyToMillis(todayDdMmYyyy)
    var dueTodayPaise = 0L
    var dueTodayCount = 0
    var overduePaise = 0L
    var overdueCount = 0
    var nextDueDate: String? = null
    var nextDueRemaining = 0L
    var totalInstRemaining = 0L

    val openSchedules = schedules
      .filter { it.status != InstallmentStatus.PAID && it.status != InstallmentStatus.WAIVED_PRECLOSURE }
      .sortedBy { it.installmentNumber }

    for (sched in openSchedules) {
      val rem = (sched.scheduledAmountPaise - sched.paidAmountPaise).coerceAtLeast(0L)
      if (rem <= 0L) continue
      totalInstRemaining += rem
      if (nextDueDate == null) {
        nextDueDate = sched.dueDateDdMmYyyy
        nextDueRemaining = rem
      }
      when {
        sched.dueDateDdMmYyyy == todayDdMmYyyy -> {
          dueTodayPaise += rem
          dueTodayCount++
        }
        todayMillis != null && sched.dueDateEpochMillis < todayMillis -> {
          overduePaise += rem
          overdueCount++
        }
      }
    }

    return LoanDueSummary(
      dueTodayPaise = dueTodayPaise,
      dueTodayCount = dueTodayCount,
      overduePaise = overduePaise,
      overdueCount = overdueCount,
      nextDueDateDdMmYyyy = nextDueDate,
      nextDueRemainingPaise = nextDueRemaining,
      totalInstallmentRemainingPaise = totalInstRemaining,
      totalOutstandingPaise = outstandingPaise.coerceAtLeast(0L),
      advanceBalancePaise = advanceBalancePaise.coerceAtLeast(0L),
    )
  }

  data class ScheduleSnapshot(
    val id: String,
    val installmentNumber: Int,
    val dueDateDdMmYyyy: String,
    val dueDateEpochMillis: Long,
    val scheduledAmountPaise: Long,
    val paidAmountPaise: Long,
    val status: InstallmentStatus,
  )

  fun computeAllocationPlan(
    amountPaise: Long,
    schedules: List<ScheduleSnapshot>,
    paymentType: PaymentType = PaymentType.REGULAR_INSTALLMENT,
  ): AllocationPlan {
    if (amountPaise <= 0L) {
      return AllocationPlan(emptyList(), 0L, 0L, "")
    }
    if (paymentType == PaymentType.ADVANCE_COLLECTION) {
      val summary = "Advance Credit: ${IndianFinanceFormatter.formatPaise(amountPaise)}"
      return AllocationPlan(emptyList(), 0L, amountPaise, summary)
    }

    var remaining = amountPaise
    var allocatedTotal = 0L
    val slices = mutableListOf<AllocationSlice>()

    val sorted = schedules.sortedBy { it.installmentNumber }
    for (sched in sorted) {
      if (remaining <= 0L) break
      if (sched.status == InstallmentStatus.PAID || sched.status == InstallmentStatus.WAIVED_PRECLOSURE) {
        continue
      }
      val needed = (sched.scheduledAmountPaise - sched.paidAmountPaise).coerceAtLeast(0L)
      if (needed <= 0L) continue
      val alloc = minOf(remaining, needed)
      val newPaid = sched.paidAmountPaise + alloc
      val newStatus = if (newPaid >= sched.scheduledAmountPaise) {
        InstallmentStatus.PAID
      } else {
        InstallmentStatus.PARTIAL
      }
      slices.add(
        AllocationSlice(
          scheduleId = sched.id,
          installmentNumber = sched.installmentNumber,
          dueDateDdMmYyyy = sched.dueDateDdMmYyyy,
          scheduledAmountPaise = sched.scheduledAmountPaise,
          paidBeforePaise = sched.paidAmountPaise,
          allocatedAmountPaise = alloc,
          paidAfterPaise = newPaid,
          statusAfter = newStatus,
        ),
      )
      allocatedTotal += alloc
      remaining -= alloc
    }

    val excessAdvance = remaining.coerceAtLeast(0L)
    val parts = slices.map { slice ->
      "#${slice.installmentNumber} (${slice.dueDateDdMmYyyy}): ${IndianFinanceFormatter.formatPaise(slice.allocatedAmountPaise)} [${slice.statusAfter.name}]"
    }.toMutableList()
    if (excessAdvance > 0L) {
      parts.add("Excess to Advance: ${IndianFinanceFormatter.formatPaise(excessAdvance)}")
    }
    val rawSummary = parts.joinToString("; ")
    val boundedSummary = if (rawSummary.length > 950) {
      rawSummary.take(940) + "…"
    } else {
      rawSummary
    }

    return AllocationPlan(
      slices = slices,
      totalAllocatedPaise = allocatedTotal,
      creditedToAdvancePaise = excessAdvance,
      summaryText = boundedSummary,
    )
  }
}

