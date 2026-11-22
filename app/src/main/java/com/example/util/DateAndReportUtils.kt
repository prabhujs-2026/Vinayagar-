package com.example.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.data.CommitteeSettings
import com.example.data.Member
import com.example.data.MembershipStatus
import com.example.data.Payment
import com.example.data.PaymentMethod
import com.example.data.PaymentStatus
import com.google.firebase.Timestamp
import java.net.URLEncoder
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

data class MonthlyReportSummary(
    val billingMonth: String,
    val formattedMonth: String,
    val activeMemberCount: Int,
    val paidMemberCount: Int,
    val pendingVerificationCount: Int,
    val unpaidMemberCount: Int,
    val expectedAmount: Long,
    val collectedAmount: Long,
    val pendingAmount: Long,
    val upiTotal: Long,
    val cashTotal: Long,
    val bankTotal: Long,
    val collectionRate: Float
)

data class MonthTrendPoint(
    val billingMonth: String,
    val shortLabel: String,
    val collected: Long,
    val expected: Long
)

data class UpiParsedResult(
    val status: String,
    val txnId: String,
    val approvalRefNo: String,
    val txnRef: String
) {
    val succeeded: Boolean get() = status.equals("SUCCESS", ignoreCase = true)
    val submitted: Boolean get() = status.equals("SUBMITTED", ignoreCase = true)
    val bestReference: String get() = txnId.ifBlank { approvalRefNo.ifBlank { txnRef } }
}

object DateAndReportUtils {

    private val indiaLocale = Locale("en", "IN")

    fun currentBillingMonth(): String {
        val cal = Calendar.getInstance()
        return String.format(Locale.US, "%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
    }

    fun shiftBillingMonth(billingMonth: String, deltaMonths: Int): String {
        val cal = parseMonthToCalendar(billingMonth)
        cal.add(Calendar.MONTH, deltaMonths)
        return String.format(Locale.US, "%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
    }

    fun parseMonthToCalendar(billingMonth: String): Calendar {
        val parts = billingMonth.split("-")
        val year = parts.getOrNull(0)?.toIntOrNull() ?: Calendar.getInstance().get(Calendar.YEAR)
        val monthIndex = (parts.getOrNull(1)?.toIntOrNull() ?: (Calendar.getInstance().get(Calendar.MONTH) + 1)) - 1
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, monthIndex.coerceIn(0, 11))
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    fun formatBillingMonth(billingMonth: String): String {
        val cal = parseMonthToCalendar(billingMonth)
        return SimpleDateFormat("MMMM yyyy", Locale.US).format(cal.time)
    }

    fun formatShortMonth(billingMonth: String): String {
        val cal = parseMonthToCalendar(billingMonth)
        return SimpleDateFormat("MMM", Locale.US).format(cal.time)
    }

    fun formatDueDate(billingMonth: String, dueDayOfMonth: Long): String {
        val cal = parseMonthToCalendar(billingMonth)
        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        cal.set(Calendar.DAY_OF_MONTH, dueDayOfMonth.toInt().coerceIn(1, maxDay))
        return SimpleDateFormat("dd MMM yyyy", Locale.US).format(cal.time)
    }

    fun formatBillingMonthRange(billingMonth: String): String {
        val cal = parseMonthToCalendar(billingMonth)
        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val monthYear = SimpleDateFormat("MMM yyyy", Locale.US).format(cal.time)
        return String.format(Locale.US, "01–%02d %s", maxDay, monthYear)
    }

    fun todayIsoDate(): String {
        val cal = Calendar.getInstance()
        return String.format(
            Locale.US,
            "%04d-%02d-%02d",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun defaultBillingDateForMonth(billingMonth: String): String {
        return if (billingMonth == currentBillingMonth()) {
            todayIsoDate()
        } else {
            "$billingMonth-01"
        }
    }

    fun isValidIsoDate(dateStr: String): Boolean {
        if (!dateStr.matches(Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}$"))) return false
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
            sdf.parse(dateStr) != null
        } catch (_: Exception) {
            false
        }
    }

    fun formatIsoDate(isoDate: String): String {
        if (!isValidIsoDate(isoDate)) return isoDate
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
            val date = parser.parse(isoDate) ?: return isoDate
            SimpleDateFormat("dd MMM yyyy", Locale.US).format(date)
        } catch (_: Exception) {
            isoDate
        }
    }

    fun epochMillisToIsoDate(utcMillis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        return sdf.format(Date(utcMillis))
    }

    fun isoDateToUtcMillis(isoDate: String): Long? {
        if (!isValidIsoDate(isoDate)) return null
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                isLenient = false
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }
            sdf.parse(isoDate)?.time
        } catch (_: Exception) {
            null
        }
    }

    private val dateTagRegex = Regex("""^\[Date:\s*([0-9]{4}-[0-9]{2}-[0-9]{2})]\s*""")

    fun extractBillingDateIso(payment: Payment): String {
        val match = dateTagRegex.find(payment.notes.trim())
        if (match != null) {
            val candidate = match.groupValues[1]
            if (isValidIsoDate(candidate)) return candidate
        }
        val created = payment.createdAt?.toDate()
        if (created != null) {
            val createdIso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(created)
            if (createdIso.startsWith(payment.billingMonth)) {
                return createdIso
            }
        }
        return defaultBillingDateForMonth(payment.billingMonth)
    }

    fun formatPaymentDate(payment: Payment): String {
        return formatIsoDate(extractBillingDateIso(payment))
    }

    fun stripDateTagFromNotes(notes: String): String {
        return dateTagRegex.replace(notes.trim(), "").trim()
    }

    fun attachBillingDateToNotes(isoDate: String, rawNotes: String): String {
        val cleanNotes = stripDateTagFromNotes(rawNotes)
        if (!isValidIsoDate(isoDate)) return cleanNotes.take(240)
        val combined = if (cleanNotes.isBlank()) {
            "[Date: $isoDate]"
        } else {
            "[Date: $isoDate] $cleanNotes"
        }
        return combined.take(240)
    }

    /**
     * Positive = days remaining before deadline; 0 = due today; Negative = overdue days.
     */
    fun daysUntilDue(billingMonth: String, dueDayOfMonth: Long): Int {
        val dueCal = parseMonthToCalendar(billingMonth)
        val maxDay = dueCal.getActualMaximum(Calendar.DAY_OF_MONTH)
        dueCal.set(Calendar.DAY_OF_MONTH, dueDayOfMonth.toInt().coerceIn(1, maxDay))

        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val diffMillis = dueCal.timeInMillis - today.timeInMillis
        return (diffMillis / (24L * 60L * 60L * 1000L)).toInt()
    }

    fun isInReminderWindow(billingMonth: String, dueDayOfMonth: Long, reminderDaysBefore: Long): Boolean {
        val remaining = daysUntilDue(billingMonth, dueDayOfMonth)
        return remaining <= reminderDaysBefore.toInt()
    }

    fun deadlineStatusLabel(billingMonth: String, dueDayOfMonth: Long): String {
        val remaining = daysUntilDue(billingMonth, dueDayOfMonth)
        return when {
            remaining > 1 -> "Due in $remaining days"
            remaining == 1 -> "Due tomorrow"
            remaining == 0 -> "Due today"
            remaining == -1 -> "Overdue by 1 day"
            else -> "Overdue by ${abs(remaining)} days"
        }
    }

    fun formatInr(amount: Long): String {
        val formatter = NumberFormat.getCurrencyInstance(indiaLocale)
        formatter.maximumFractionDigits = 0
        return formatter.format(amount)
    }

    fun formatTimestamp(timestamp: Timestamp?): String {
        val date = timestamp?.toDate() ?: Date()
        return SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(date)
    }

    fun computeMonthlyReport(
        billingMonth: String,
        members: List<Member>,
        payments: List<Payment>
    ): MonthlyReportSummary {
        val activeMembers = members.filter { it.statusEnum() == MembershipStatus.ACTIVE }
        val monthPayments = payments.filter { it.billingMonth == billingMonth }
        val paidPayments = monthPayments.filter { it.statusEnum() == PaymentStatus.PAID }
        val pendingVerifications = monthPayments.filter { it.statusEnum() == PaymentStatus.PENDING_VERIFICATION }

        val paidMemberIds = paidPayments.map { it.memberId }.toSet()
        val expectedAmount = activeMembers.sumOf { it.monthlyAmount }
        val collectedAmount = paidPayments.sumOf { it.amount }
        val pendingAmount = (expectedAmount - collectedAmount).coerceAtLeast(0L)

        val upiTotal = paidPayments.filter { it.methodEnum() == PaymentMethod.UPI }.sumOf { it.amount }
        val cashTotal = paidPayments.filter { it.methodEnum() == PaymentMethod.CASH }.sumOf { it.amount }
        val bankTotal = paidPayments.filter { it.methodEnum() == PaymentMethod.BANK_TRANSFER }.sumOf { it.amount }

        val paidActiveCount = activeMembers.count { it.id in paidMemberIds }
        val unpaidActiveCount = (activeMembers.size - paidActiveCount).coerceAtLeast(0)
        val rate = if (expectedAmount > 0L) {
            (collectedAmount.toFloat() / expectedAmount.toFloat()).coerceIn(0f, 1f)
        } else 0f

        return MonthlyReportSummary(
            billingMonth = billingMonth,
            formattedMonth = formatBillingMonth(billingMonth),
            activeMemberCount = activeMembers.size,
            paidMemberCount = paidActiveCount,
            pendingVerificationCount = pendingVerifications.size,
            unpaidMemberCount = unpaidActiveCount,
            expectedAmount = expectedAmount,
            collectedAmount = collectedAmount,
            pendingAmount = pendingAmount,
            upiTotal = upiTotal,
            cashTotal = cashTotal,
            bankTotal = bankTotal,
            collectionRate = rate
        )
    }

    fun computeSixMonthTrend(
        selectedMonth: String,
        members: List<Member>,
        payments: List<Payment>
    ): List<MonthTrendPoint> {
        val activeExpected = members.filter { it.statusEnum() == MembershipStatus.ACTIVE }.sumOf { it.monthlyAmount }
        return (-5..0).map { offset ->
            val month = shiftBillingMonth(selectedMonth, offset)
            val collected = payments
                .filter { it.billingMonth == month && it.statusEnum() == PaymentStatus.PAID }
                .sumOf { it.amount }
            MonthTrendPoint(
                billingMonth = month,
                shortLabel = formatShortMonth(month),
                collected = collected,
                expected = activeExpected
            )
        }
    }

    fun buildGentleReminderMessage(
        member: Member,
        billingMonth: String,
        settings: CommitteeSettings
    ): String {
        val dueDateStr = formatDueDate(billingMonth, settings.dueDayOfMonth)
        val monthStr = formatBillingMonth(billingMonth)
        return settings.whatsappMessageTemplate
            .replace("{name}", member.fullName)
            .replace("{month}", monthStr)
            .replace("{amount}", member.monthlyAmount.toString())
            .replace("{dueDate}", dueDateStr)
            .replace("{upiId}", settings.upiId)
            .replace("{festivalName}", settings.festivalName)
    }

    fun buildGroupReminderMessage(
        billingMonth: String,
        pendingMembers: List<Member>,
        settings: CommitteeSettings
    ): String {
        val monthStr = formatBillingMonth(billingMonth)
        val dueDateStr = formatDueDate(billingMonth, settings.dueDayOfMonth)
        return buildString {
            appendLine("*${settings.festivalName}*")
            appendLine("Gentle Monthly Contribution Reminder - $monthStr")
            appendLine()
            appendLine("Namaskaram everyone! Our monthly Vinayagar festival contribution deadline is *$dueDateStr* (${deadlineStatusLabel(billingMonth, settings.dueDayOfMonth)}).")
            appendLine("UPI ID: `${settings.upiId}`")
            appendLine("Payee: ${settings.upiPayeeName}")
            if (pendingMembers.isNotEmpty()) {
                appendLine()
                appendLine("Pending members (${pendingMembers.size}):")
                pendingMembers.take(25).forEachIndexed { idx, m ->
                    appendLine("${idx + 1}. ${m.fullName} - ${formatInr(m.monthlyAmount)}")
                }
                if (pendingMembers.size > 25) {
                    appendLine("...and ${pendingMembers.size - 25} more members.")
                }
            }
            appendLine()
            appendLine("Members can also open the Vinayagar Fund app and tap 'Pay Now via UPI' for instant receipts.")
        }
    }

    fun buildFormattedCollectionReport(
        summary: MonthlyReportSummary,
        settings: CommitteeSettings,
        members: List<Member>,
        payments: List<Payment>
    ): String {
        val paidIds = payments
            .filter { it.billingMonth == summary.billingMonth && it.statusEnum() == PaymentStatus.PAID }
            .map { it.memberId }
            .toSet()
        val pendingMembers = members.filter {
            it.statusEnum() == MembershipStatus.ACTIVE && it.id !in paidIds
        }

        return buildString {
            appendLine("*${settings.festivalName}*")
            appendLine("Automated Collection Report - ${summary.formattedMonth}")
            appendLine("--------------------------------")
            appendLine("Total Expected : ${formatInr(summary.expectedAmount)}")
            appendLine("Total Collected: ${formatInr(summary.collectedAmount)} (${(summary.collectionRate * 100).toInt()}%)")
            appendLine("Pending Balance: ${formatInr(summary.pendingAmount)}")
            appendLine()
            appendLine("*Payment Mode Breakdown*")
            appendLine("- UPI          : ${formatInr(summary.upiTotal)}")
            appendLine("- Cash         : ${formatInr(summary.cashTotal)}")
            appendLine("- Bank Transfer: ${formatInr(summary.bankTotal)}")
            appendLine()
            appendLine("*Member Status*")
            appendLine("- Active Members: ${summary.activeMemberCount}")
            appendLine("- Paid          : ${summary.paidMemberCount}")
            appendLine("- Pending       : ${summary.unpaidMemberCount}")
            if (summary.pendingVerificationCount > 0) {
                appendLine("- Pending Review: ${summary.pendingVerificationCount}")
            }
            if (pendingMembers.isNotEmpty()) {
                appendLine()
                appendLine("*Pending Contributors*")
                pendingMembers.forEachIndexed { idx, member ->
                    appendLine("${idx + 1}. ${member.fullName} (${formatInr(member.monthlyAmount)})")
                }
            }
            appendLine()
            appendLine("Generated via Vinayagar Fund")
        }
    }

    fun buildCsvReport(
        billingMonth: String,
        members: List<Member>,
        payments: List<Payment>
    ): String {
        val paymentByMember = payments
            .filter { it.billingMonth == billingMonth }
            .associateBy { it.memberId }

        return buildString {
            appendLine("Member Name,Phone,Email,Membership Status,Monthly Target (INR),Payment Status,Payment Date,Amount Paid (INR),Method,UPI Ref,Notes")
            members.forEach { member ->
                val p = paymentByMember[member.id]
                val row = listOf(
                    member.fullName,
                    member.phone,
                    member.email,
                    member.membershipStatus,
                    member.monthlyAmount.toString(),
                    p?.status ?: "PENDING",
                    p?.let { formatPaymentDate(it) } ?: "",
                    (p?.amount ?: 0L).toString(),
                    p?.paymentMethod ?: "",
                    p?.upiRef ?: "",
                    p?.let { stripDateTagFromNotes(it.notes) } ?: ""
                ).joinToString(",") { cell ->
                    "\"${cell.replace("\"", "\"\"")}\""
                }
                appendLine(row)
            }
        }
    }

    fun normalizePhoneForWhatsApp(phone: String): String {
        val digits = phone.filter { it.isDigit() }
        return when {
            digits.length == 10 -> "91$digits"
            digits.startsWith("0") && digits.length == 11 -> "91${digits.drop(1)}"
            else -> digits
        }
    }

    fun launchWhatsAppReminder(context: Context, phone: String, message: String): Boolean {
        val normalizedPhone = normalizePhoneForWhatsApp(phone)
        val encodedText = URLEncoder.encode(message, "UTF-8")
        val uri = Uri.parse("https://api.whatsapp.com/send?phone=$normalizedPhone&text=$encodedText")

        val packages = listOf("com.whatsapp", "com.whatsapp.w4b")
        for (pkg in packages) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage(pkg)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return true
            } catch (_: ActivityNotFoundException) {
                // Try next WhatsApp package
            }
        }

        return try {
            val fallbackIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallbackIntent)
            true
        } catch (_: Exception) {
            shareText(context, "Gentle Payment Reminder", message)
            false
        }
    }

    fun shareText(context: Context, title: String, content: String) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, content)
        }
        val chooser = Intent.createChooser(sendIntent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun buildUpiPayUri(
        upiId: String,
        payeeName: String,
        amountInr: Long,
        note: String,
        txnRef: String
    ): Uri {
        return Uri.Builder()
            .scheme("upi")
            .authority("pay")
            .appendQueryParameter("pa", upiId.trim())
            .appendQueryParameter("pn", payeeName.trim())
            .appendQueryParameter("tr", txnRef)
            .appendQueryParameter("tn", note.take(80))
            .appendQueryParameter("am", "$amountInr.00")
            .appendQueryParameter("cu", "INR")
            .build()
    }

    fun parseUpiResponse(responseString: String?): UpiParsedResult {
        if (responseString.isNullOrBlank()) {
            return UpiParsedResult(status = "CANCELLED", txnId = "", approvalRefNo = "", txnRef = "")
        }
        val map = mutableMapOf<String, String>()
        responseString.split("&").forEach { pair ->
            val idx = pair.indexOf('=')
            if (idx > 0) {
                val key = pair.substring(0, idx).lowercase(Locale.US)
                val value = pair.substring(idx + 1)
                map[key] = value
            }
        }
        return UpiParsedResult(
            status = map["status"] ?: "UNKNOWN",
            txnId = map["txnid"] ?: "",
            approvalRefNo = map["approvalrefno"] ?: "",
            txnRef = map["txnref"] ?: ""
        )
    }
}
