package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

const val BOOTSTRAPPED_ADMIN_EMAIL = "prabhujs30@gmail.com"
const val DEFAULT_WHATSAPP_TEMPLATE =
    "Namaskaram {name}, gentle reminder for your {month} {festivalName} monthly contribution of Rs.{amount}. Kindly pay before {dueDate} via UPI ({upiId}). Thank you for your devotion and support!"

enum class MembershipStatus(val label: String) {
    ACTIVE("Active"),
    PAUSED("Paused"),
    INACTIVE("Inactive");

    companion object {
        fun fromRaw(raw: String?): MembershipStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: ACTIVE
    }
}

enum class PaymentMethod(val label: String) {
    UPI("UPI"),
    CASH("Cash"),
    BANK_TRANSFER("Bank Transfer");

    companion object {
        fun fromRaw(raw: String?): PaymentMethod =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: UPI
    }
}

enum class PaymentStatus(val label: String) {
    PAID("Paid"),
    PENDING_VERIFICATION("Pending Review"),
    REJECTED("Rejected");

    companion object {
        fun fromRaw(raw: String?): PaymentStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: PAID
    }
}

enum class JoinRequestStatus(val label: String) {
    PENDING("Pending"),
    APPROVED("Approved"),
    REJECTED("Rejected");

    companion object {
        fun fromRaw(raw: String?): JoinRequestStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: PENDING
    }
}

@Keep
@IgnoreExtraProperties
data class CommitteeSettings(
    @DocumentId val id: String = "committee",
    val festivalName: String = "Sri Vinayagar Vizha Committee",
    val upiId: String = "vinayagarcommittee@upi",
    val upiPayeeName: String = "Vinayagar Festival Committee",
    val defaultMonthlyAmount: Long = 500L,
    val dueDayOfMonth: Long = 10L,
    val reminderDaysBefore: Long = 5L,
    val whatsappMessageTemplate: String = DEFAULT_WHATSAPP_TEMPLATE,
    val autoReminderEnabled: Boolean = true,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AdminSecurity(
    @DocumentId val id: String = "admin_security",
    val lockEnabled: Boolean = false,
    val adminUsername: String = "",
    val passcodeSalt: String = "",
    val passcodeHash: String = "",
    val updatedByUid: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Member(
    @DocumentId val id: String = "",
    val fullName: String = "",
    val phone: String = "",
    val email: String = "",
    val emailLower: String = "",
    val address: String = "",
    val monthlyAmount: Long = 500L,
    val membershipStatus: String = MembershipStatus.ACTIVE.name,
    val userId: String = "",
    val lastRemindedMonth: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun statusEnum(): MembershipStatus = MembershipStatus.fromRaw(membershipStatus)
}

@Keep
@IgnoreExtraProperties
data class Payment(
    @DocumentId val id: String = "",
    val memberId: String = "",
    val memberUserId: String = "",
    val memberEmailLower: String = "",
    val memberName: String = "",
    val billingMonth: String = "",
    val amount: Long = 0L,
    val paymentMethod: String = PaymentMethod.UPI.name,
    val upiRef: String = "",
    val status: String = PaymentStatus.PAID.name,
    val notes: String = "",
    val recordedByUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun methodEnum(): PaymentMethod = PaymentMethod.fromRaw(paymentMethod)
    fun statusEnum(): PaymentStatus = PaymentStatus.fromRaw(status)
}

@Keep
@IgnoreExtraProperties
data class ReminderLog(
    @DocumentId val id: String = "",
    val memberId: String = "",
    val memberName: String = "",
    val memberPhone: String = "",
    val billingMonth: String = "",
    val channel: String = "WHATSAPP",
    val sentByUid: String = "",
    val messagePreview: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class JoinRequest(
    @DocumentId val id: String = "",
    val userId: String = "",
    val fullName: String = "",
    val emailLower: String = "",
    val phone: String = "",
    val address: String = "",
    val status: String = JoinRequestStatus.PENDING.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun statusEnum(): JoinRequestStatus = JoinRequestStatus.fromRaw(status)
}

@Keep
@IgnoreExtraProperties
data class AdminUser(
    @DocumentId val id: String = "",
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val addedByUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
