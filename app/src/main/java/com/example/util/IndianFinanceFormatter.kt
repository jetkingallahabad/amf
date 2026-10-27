package com.example.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Deterministic Indian currency (INR ₹ with lakh/crore grouping) and DD-MM-YYYY date utilities.
 * All monetary amounts in the ledger are stored in Paise (Long: ₹1.00 = 100L paise)
 * to eliminate IEEE-754 floating-point precision drift.
 */
object IndianFinanceFormatter {

  private const val DATE_PATTERN = "dd-MM-yyyy"
  private const val DATE_TIME_PATTERN = "dd-MM-yyyy HH:mm"
  private const val DATE_TIME_SEC_PATTERN = "dd-MM-yyyy HH:mm:ss"

  /**
   * Formats paise (100 paise = ₹1.00) using the Indian numbering system:
   * e.g., 15000000L -> "₹1,50,000.00" or "₹1,50,000" when showZeroPaise = false.
   */
  fun formatPaise(paise: Long, showZeroPaise: Boolean = false): String {
    val isNegative = paise < 0L
    val absPaise = abs(paise)
    val rupees = absPaise / 100L
    val remPaise = absPaise % 100L

    val groupedRupees = groupIndianDigits(rupees)
    val decimalPart = if (remPaise > 0L || showZeroPaise) {
      ".%02d".format(Locale.US, remPaise)
    } else {
      ""
    }
    val sign = if (isNegative) "-" else ""
    return "$sign₹$groupedRupees$decimalPart"
  }

  /**
   * Groups positive integer digits using Indian Lakh/Crore convention:
   * Last 3 digits together, then pairs of 2 digits (e.g. 12345678 -> "1,23,45,678").
   */
  fun groupIndianDigits(number: Long): String {
    val raw = abs(number).toString()
    if (raw.length <= 3) return raw
    val lastThree = raw.takeLast(3)
    val prefix = raw.dropLast(3)
    val sb = StringBuilder()
    var idx = prefix.length
    while (idx > 0) {
      val start = (idx - 2).coerceAtLeast(0)
      if (sb.isNotEmpty()) sb.insert(0, ',')
      sb.insert(0, prefix.substring(start, idx))
      idx = start
    }
    return "$sb,$lastThree"
  }

  /**
   * Parses user-entered rupee string (e.g. "25,000.50" or "25000") into exact Paise (Long).
   * Returns null if invalid or negative.
   */
  fun parseRupeesToPaise(input: String): Long? {
    val cleaned = input.replace("₹", "").replace(",", "").trim()
    if (cleaned.isEmpty()) return null
    return try {
      val bd = BigDecimal(cleaned).setScale(2, RoundingMode.HALF_UP)
      if (bd < BigDecimal.ZERO) return null
      bd.multiply(BigDecimal(100)).longValueExact()
    } catch (_: Exception) {
      null
    }
  }

  /**
   * Formats epoch milliseconds into strict DD-MM-YYYY string.
   */
  fun formatDate(epochMillis: Long): String {
    val sdf = SimpleDateFormat(DATE_PATTERN, Locale.US)
    return sdf.format(Date(epochMillis))
  }

  /**
   * Formats epoch milliseconds into DD-MM-YYYY HH:mm string.
   */
  fun formatDateTime(epochMillis: Long): String {
    val sdf = SimpleDateFormat(DATE_TIME_PATTERN, Locale.US)
    return sdf.format(Date(epochMillis))
  }

  /**
   * Formats epoch milliseconds into DD-MM-YYYY HH:mm:ss string.
   */
  fun formatDateTimeSeconds(epochMillis: Long): String {
    val sdf = SimpleDateFormat(DATE_TIME_SEC_PATTERN, Locale.US)
    return sdf.format(Date(epochMillis))
  }

  fun todayDdMmYyyy(nowMillis: Long = System.currentTimeMillis()): String {
    return formatDate(startOfDayMillis(nowMillis))
  }

  fun startOfDayMillis(epochMillis: Long = System.currentTimeMillis()): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = epochMillis
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
  }

  /**
   * Strictly validates and parses a DD-MM-YYYY string into local start-of-day epoch millis.
   */
  fun parseDdMmYyyyToMillis(dateStr: String): Long? {
    val trimmed = dateStr.trim()
    val regex = Regex("^\\d{2}-\\d{2}-\\d{4}$")
    if (!regex.matches(trimmed)) return null
    return try {
      val sdf = SimpleDateFormat(DATE_PATTERN, Locale.US).apply {
        isLenient = false
      }
      val parsed = sdf.parse(trimmed) ?: return null
      startOfDayMillis(parsed.time)
    } catch (_: Exception) {
      null
    }
  }

  /**
   * Adds calendar days (e.g., +1 for Daily EDI, +7 for Weekly EMI) to a DD-MM-YYYY date.
   */
  fun addDays(ddMmYyyy: String, daysToAdd: Int): Pair<String, Long> {
    val baseMillis = parseDdMmYyyyToMillis(ddMmYyyy)
      ?: startOfDayMillis(System.currentTimeMillis())
    val cal = Calendar.getInstance()
    cal.timeInMillis = baseMillis
    cal.add(Calendar.DAY_OF_MONTH, daysToAdd)
    val targetMillis = startOfDayMillis(cal.timeInMillis)
    return formatDate(targetMillis) to targetMillis
  }

  /**
   * Formats paise back to plain editable rupee string (e.g., 150000L -> "1500" or "1500.50").
   */
  fun paiseToPlainRupeesString(paise: Long): String {
    val rupees = paise / 100L
    val rem = abs(paise % 100L)
    return if (rem == 0L) rupees.toString() else "%d.%02d".format(Locale.US, rupees, rem)
  }
}
