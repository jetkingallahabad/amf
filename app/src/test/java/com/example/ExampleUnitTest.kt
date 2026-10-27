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
  fun loanScheduleCalculator_generatesExactDailyEdiAndWeeklyEmiTotals() {
    val dailyQuote = LoanScheduleCalculator.calculateQuote(
      principalPaise = 10_000_00L, // ₹10,000
      annualRatePercent = 18.25,
      processingFeePaise = 200_00L,
      totalInstallments = 100,
      frequency = RepaymentFrequency.DAILY,
      disbursementDateDdMmYyyy = "01-10-2026",
    )
    assertNotNull(dailyQuote)
    val q = dailyQuote!!
    val totalScheduled = q.installmentAmountPaise * (q.totalInstallments - 1) + q.lastInstallmentAmountPaise
    assertEquals(q.totalPayablePaise, totalScheduled)
    assertEquals("02-10-2026", q.firstDueDateDdMmYyyy)

    val weeklyQuote = LoanScheduleCalculator.calculateQuote(
      principalPaise = 24_000_00L,
      annualRatePercent = 20.0,
      processingFeePaise = 500_00L,
      totalInstallments = 12,
      frequency = RepaymentFrequency.WEEKLY,
      disbursementDateDdMmYyyy = "01-10-2026",
    )
    assertNotNull(weeklyQuote)
    assertEquals("08-10-2026", weeklyQuote!!.firstDueDateDdMmYyyy)
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

