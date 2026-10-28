package com.example.domain.model

import com.example.util.IndianFinanceFormatter

enum class UserRole(val displayTitle: String) {
  OWNER("Owner"),
  BRANCH_MANAGER("Branch Manager"),
  COLLECTION_AGENT("Collection Agent"),
}

enum class RepaymentFrequency(val displayTitle: String, val stepDays: Int, val shortLabel: String) {
  DAILY("Daily EDI", 1, "EDI"),
  WEEKLY("Weekly EMI", 7, "EMI"),
}

enum class LoanProductType(val displayTitle: String) {
  DAILY_BUSINESS_EDI("Daily Fixed EDI Loan"),
  WEEKLY_GROUP_EMI("Weekly Fixed EMI Loan"),
  MICRO_ENTERPRISE("Small Enterprise Finance"),
  EMERGENCY_BRIDGE("Short-Term Working Capital"),
  ;

  companion object {
    fun forFrequency(frequency: RepaymentFrequency): LoanProductType = when (frequency) {
      RepaymentFrequency.DAILY -> DAILY_BUSINESS_EDI
      RepaymentFrequency.WEEKLY -> WEEKLY_GROUP_EMI
    }
  }
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
 * Validates Owner-configured fixed loan products without any percentage-based interest calculation.
 * Total Scheduled Repayment is strictly Fixed EMI/EDI × Number of Installments.
 */
object FixedLoanProductValidator {
  const val MIN_PRINCIPAL_PAISE = 10_000L // ₹100
  const val MAX_PRINCIPAL_PAISE = 500_000_000L // ₹50,00,000
  const val MIN_INSTALLMENTS = 1
  const val MAX_INSTALLMENTS = 1_000

  data class ValidatedProductTerms(
    val name: String,
    val principalPaise: Long,
    val repaymentFrequency: RepaymentFrequency,
    val durationCount: Int,
    val totalInstallments: Int,
    val fixedInstallmentPaise: Long,
    val totalScheduledRepaymentPaise: Long,
    val fixedMarginPaise: Long,
    val processingFeePaise: Long,
  )

  fun validate(
    rawName: String,
    principalPaise: Long,
    repaymentFrequency: RepaymentFrequency,
    durationCount: Int,
    fixedInstallmentPaise: Long,
    processingFeePaise: Long = 0L,
  ): ValidatedProductTerms {
    require(principalPaise in MIN_PRINCIPAL_PAISE..MAX_PRINCIPAL_PAISE) {
      "Loan amount must be between ₹100 and ₹50,00,000."
    }
    val unitPlural = if (repaymentFrequency == RepaymentFrequency.DAILY) "days" else "weeks"
    require(durationCount in MIN_INSTALLMENTS..MAX_INSTALLMENTS) {
      "Loan duration must be between $MIN_INSTALLMENTS and $MAX_INSTALLMENTS $unitPlural."
    }
    val totalInstallments = durationCount
    require(fixedInstallmentPaise > 0L) {
      "Fixed ${repaymentFrequency.shortLabel} amount must be greater than ₹0."
    }
    val totalScheduledRepaymentPaise = try {
      Math.multiplyExact(fixedInstallmentPaise, totalInstallments.toLong())
    } catch (_: ArithmeticException) {
      throw IllegalArgumentException("Total scheduled repayment exceeds maximum supported value.")
    }
    require(totalScheduledRepaymentPaise >= principalPaise) {
      "Total Scheduled Repayment (${IndianFinanceFormatter.formatPaise(totalScheduledRepaymentPaise)} = " +
        "${IndianFinanceFormatter.formatPaise(fixedInstallmentPaise)} × $totalInstallments) cannot be less than Loan Amount (${IndianFinanceFormatter.formatPaise(principalPaise)})."
    }
    require(processingFeePaise in 0L until principalPaise) {
      "Processing fee must be non-negative and less than the Loan Amount."
    }
    val defaultName = buildDefaultProductName(
      principalPaise = principalPaise,
      repaymentFrequency = repaymentFrequency,
      durationCount = durationCount,
      fixedInstallmentPaise = fixedInstallmentPaise,
    )
    val cleanName = rawName.trim().ifBlank { defaultName }
    require(cleanName.length in 3..120) {
      "Product name must be between 3 and 120 characters."
    }

    return ValidatedProductTerms(
      name = cleanName,
      principalPaise = principalPaise,
      repaymentFrequency = repaymentFrequency,
      durationCount = durationCount,
      totalInstallments = totalInstallments,
      fixedInstallmentPaise = fixedInstallmentPaise,
      totalScheduledRepaymentPaise = totalScheduledRepaymentPaise,
      fixedMarginPaise = totalScheduledRepaymentPaise - principalPaise,
      processingFeePaise = processingFeePaise,
    )
  }

  fun buildDefaultProductName(
    principalPaise: Long,
    repaymentFrequency: RepaymentFrequency,
    durationCount: Int,
    fixedInstallmentPaise: Long,
  ): String {
    val unit = if (repaymentFrequency == RepaymentFrequency.DAILY) {
      if (durationCount == 1) "Day" else "Days"
    } else {
      if (durationCount == 1) "Week" else "Weeks"
    }
    return "${IndianFinanceFormatter.formatPaise(principalPaise)} • $durationCount $unit • ${IndianFinanceFormatter.formatPaise(fixedInstallmentPaise)} ${repaymentFrequency.shortLabel}"
  }
}

/**
 * Deterministic fixed-product loan schedule calculator using exact Paise arithmetic.
 * Total Scheduled Repayment = Fixed EMI/EDI × Number of Installments (no percentage calculation).
 */
object LoanScheduleCalculator {

  data class FixedLoanQuote(
    val principalPaise: Long,
    val processingFeePaise: Long,
    val totalMarginPaise: Long,
    val totalScheduledRepaymentPaise: Long,
    val fixedInstallmentPaise: Long,
    val totalInstallments: Int,
    val durationCount: Int,
    val frequency: RepaymentFrequency,
    val repaymentStartDateDdMmYyyy: String,
    val expectedCompletionDateDdMmYyyy: String,
  )

  fun calculateFixedProductQuote(
    principalPaise: Long,
    fixedInstallmentPaise: Long,
    totalInstallments: Int,
    frequency: RepaymentFrequency,
    disbursementDateDdMmYyyy: String,
    processingFeePaise: Long = 0L,
  ): FixedLoanQuote? {
    if (principalPaise <= 0L || fixedInstallmentPaise <= 0L || totalInstallments !in 1..1000) return null
    if (processingFeePaise < 0L || processingFeePaise >= principalPaise) return null
    if (IndianFinanceFormatter.parseDdMmYyyyToMillis(disbursementDateDdMmYyyy) == null) return null

    val totalScheduledRepaymentPaise = try {
      Math.multiplyExact(fixedInstallmentPaise, totalInstallments.toLong())
    } catch (_: ArithmeticException) {
      return null
    }
    if (totalScheduledRepaymentPaise < principalPaise) return null

    val totalMarginPaise = (totalScheduledRepaymentPaise - principalPaise).coerceAtLeast(0L)
    val (firstDue, _) = IndianFinanceFormatter.addDays(disbursementDateDdMmYyyy, frequency.stepDays)
    val (maturity, _) = IndianFinanceFormatter.addDays(
      disbursementDateDdMmYyyy,
      frequency.stepDays * totalInstallments,
    )

    return FixedLoanQuote(
      principalPaise = principalPaise,
      processingFeePaise = processingFeePaise,
      totalMarginPaise = totalMarginPaise,
      totalScheduledRepaymentPaise = totalScheduledRepaymentPaise,
      fixedInstallmentPaise = fixedInstallmentPaise,
      totalInstallments = totalInstallments,
      durationCount = totalInstallments,
      frequency = frequency,
      repaymentStartDateDdMmYyyy = firstDue,
      expectedCompletionDateDdMmYyyy = maturity,
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

