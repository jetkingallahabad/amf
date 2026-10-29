package com.example

import com.example.domain.model.LoanScheduleCalculator
import com.example.domain.model.RepaymentFrequency
import com.example.util.IndianFinanceFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ExampleUnitTest {

  @Test
  fun indianCurrencyFormatting_formatsLakhAndCroreCorrectly() {
    assertEquals("₹0", IndianFinanceFormatter.formatPaise(0L))
    assertEquals("₹0.00", IndianFinanceFormatter.formatPaise(0L, showZeroPaise = true))
    assertEquals("₹500", IndianFinanceFormatter.formatPaise(50_000L))
    assertEquals("₹12,500", IndianFinanceFormatter.formatPaise(1_250_000L))
    assertEquals("₹1,50,000", IndianFinanceFormatter.formatPaise(15_000_000L))
    assertEquals("₹1,23,45,678.50", IndianFinanceFormatter.formatPaise(1_234_567_850L))
    assertEquals("-₹2,50,000", IndianFinanceFormatter.formatPaise(-25_000_000L))
  }

  @Test
  fun parseRupeesToPaise_parsesFormattedAndDecimalInputs() {
    assertEquals(2_500_050L, IndianFinanceFormatter.parseRupeesToPaise("₹25,000.50"))
    assertEquals(10_000_000L, IndianFinanceFormatter.parseRupeesToPaise("1,00,000"))
    assertNull(IndianFinanceFormatter.parseRupeesToPaise("abc"))
    assertNull(IndianFinanceFormatter.parseRupeesToPaise("-500"))
  }

  @Test
  fun ddMmYyyyDateOperations_strictlyValidateAndAddDays() {
    assertNotNull(IndianFinanceFormatter.parseDdMmYyyyToMillis("26-10-2026"))
    // Invalid calendar date (31st Feb) must be rejected
    assertNull(IndianFinanceFormatter.parseDdMmYyyyToMillis("31-02-2026"))
    // Wrong order YYYY-MM-DD must be rejected
    assertNull(IndianFinanceFormatter.parseDdMmYyyyToMillis("2026-10-26"))

    val (nextDay, _) = IndianFinanceFormatter.addDays("26-10-2026", 1)
    assertEquals("27-10-2026", nextDay)

    val (nextWeek, _) = IndianFinanceFormatter.addDays("26-10-2026", 7)
    assertEquals("02-11-2026", nextWeek)
  }

  @Test
  fun loanScheduleCalculator_generatesExactFixedProductWeeklyEmiAndDailyEdiTotals() {
    // Example 1: Loan ₹10,000 • 12 weeks • Fixed Weekly EMI ₹1,000 -> Total Scheduled Repayment ₹12,000
    val weekly12 = LoanScheduleCalculator.calculateFixedProductQuote(
      principalPaise = 10_000_00L,
      fixedInstallmentPaise = 1_000_00L,
      totalInstallments = 12,
      frequency = RepaymentFrequency.WEEKLY,
      disbursementDateDdMmYyyy = "01-10-2026",
    )
    assertNotNull(weekly12)
    assertEquals(12_000_00L, weekly12!!.totalScheduledRepaymentPaise)
    assertEquals(2_000_00L, weekly12.totalMarginPaise)
    assertEquals("08-10-2026", weekly12.repaymentStartDateDdMmYyyy)
    assertEquals("24-12-2026", weekly12.expectedCompletionDateDdMmYyyy)

    // Example 2: Loan ₹10,000 • 15 weeks • Fixed Weekly EMI ₹800 -> Total Scheduled Repayment ₹12,000
    val weekly15 = LoanScheduleCalculator.calculateFixedProductQuote(
      principalPaise = 10_000_00L,
      fixedInstallmentPaise = 800_00L,
      totalInstallments = 15,
      frequency = RepaymentFrequency.WEEKLY,
      disbursementDateDdMmYyyy = "01-10-2026",
    )
    assertNotNull(weekly15)
    assertEquals(12_000_00L, weekly15!!.totalScheduledRepaymentPaise)
    assertEquals("08-10-2026", weekly15.repaymentStartDateDdMmYyyy)
    assertEquals("14-01-2027", weekly15.expectedCompletionDateDdMmYyyy)

    // Daily EDI: Loan ₹10,000 • 100 days • Fixed Daily EDI ₹120 -> Total ₹12,000
    val daily100 = LoanScheduleCalculator.calculateFixedProductQuote(
      principalPaise = 10_000_00L,
      fixedInstallmentPaise = 120_00L,
      totalInstallments = 100,
      frequency = RepaymentFrequency.DAILY,
      disbursementDateDdMmYyyy = "01-10-2026",
      processingFeePaise = 200_00L,
    )
    assertNotNull(daily100)
    assertEquals(12_000_00L, daily100!!.totalScheduledRepaymentPaise)
    assertEquals(9_800_00L, daily100.netDisbursedPaise)
    assertEquals("02-10-2026", daily100.repaymentStartDateDdMmYyyy)

    // Validator rejects totalScheduledRepayment < principalPaise or invalid duration
    assertNull(
      com.example.domain.model.FixedLoanProductValidator.validationErrorOrNull(
        principalPaise = 10_000_00L,
        repaymentFrequency = RepaymentFrequency.WEEKLY,
        durationCount = 12,
        fixedInstallmentPaise = 1_000_00L,
      ),
    )
    assertNotNull(
      com.example.domain.model.FixedLoanProductValidator.validationErrorOrNull(
        principalPaise = 10_000_00L,
        repaymentFrequency = RepaymentFrequency.WEEKLY,
        durationCount = 12,
        fixedInstallmentPaise = 500_00L, // ₹6,000 < ₹10,000 -> must fail
      ),
    )
  }

  @Test
  fun customerKycValidator_enforcesIndianMobileAadhaarAndPanFormats() {
    assertEquals(
      "9876543210",
      com.example.domain.model.CustomerKycValidator.validateIndianMobile("98765 43210"),
    )
    assertNotNull(
      runCatching {
        com.example.domain.model.CustomerKycValidator.validateIndianMobile("1234567890")
      }.exceptionOrNull(),
    )
    assertEquals(
      "123456789012",
      com.example.domain.model.CustomerKycValidator.normalizeAndValidateKyc("Aadhaar Card", "1234 5678 9012"),
    )
    assertNotNull(
      runCatching {
        com.example.domain.model.CustomerKycValidator.normalizeAndValidateKyc("Aadhaar Card", "12345")
      }.exceptionOrNull(),
    )
    assertEquals(
      "ABCDE1234F",
      com.example.domain.model.CustomerKycValidator.normalizeAndValidateKyc("PAN Card", "abcde1234f"),
    )
    assertNotNull(
      runCatching {
        com.example.domain.model.CustomerKycValidator.normalizeAndValidateKyc("PAN Card", "INVALIDPAN")
      }.exceptionOrNull(),
    )
  }
}

