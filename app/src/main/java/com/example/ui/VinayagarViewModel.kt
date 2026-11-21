package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AdminSecurity
import com.example.data.AdminUser
import com.example.data.BOOTSTRAPPED_ADMIN_EMAIL
import com.example.data.CommitteeRepository
import com.example.data.CommitteeSettings
import com.example.data.JoinRequest
import com.example.data.Member
import com.example.data.MembershipStatus
import com.example.data.Payment
import com.example.data.PaymentMethod
import com.example.data.PaymentStatus
import com.example.data.ReminderLog
import com.example.data.UiState
import com.example.util.DateAndReportUtils
import com.example.util.SecurityHashUtils
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface RoleResolution {
    data object Loading : RoleResolution
    data class Resolved(val admin: Boolean) : RoleResolution
}

data class AdminSnapshot(
    val settings: CommitteeSettings = CommitteeSettings(),
    val adminSecurity: AdminSecurity = AdminSecurity(),
    val members: List<Member> = emptyList(),
    val payments: List<Payment> = emptyList(),
    val reminderLogs: List<ReminderLog> = emptyList(),
    val joinRequests: List<JoinRequest> = emptyList(),
    val admins: List<AdminUser> = emptyList()
)

data class MemberPortalSnapshot(
    val settings: CommitteeSettings = CommitteeSettings(),
    val memberRecord: Member? = null,
    val payments: List<Payment> = emptyList(),
    val joinRequest: JoinRequest? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class VinayagarViewModel(
    private val repository: CommitteeRepository,
    val currentUserId: String,
    val currentUserEmail: String,
    val currentUserName: String,
    val emailVerified: Boolean
) : ViewModel() {

    private val _roleResolution = MutableStateFlow<RoleResolution>(RoleResolution.Loading)
    val roleResolution: StateFlow<RoleResolution> = _roleResolution.asStateFlow()

    private val _selectedMonth = MutableStateFlow(DateAndReportUtils.currentBillingMonth())
    val selectedMonth: StateFlow<String> = _selectedMonth.asStateFlow()

    private val _previewMemberMode = MutableStateFlow(false)
    val previewMemberMode: StateFlow<Boolean> = _previewMemberMode.asStateFlow()

    private val _adminConsoleUnlocked = MutableStateFlow(false)
    val adminConsoleUnlocked: StateFlow<Boolean> = _adminConsoleUnlocked.asStateFlow()

    val isPrimaryGoogleAdmin: Boolean
        get() = emailVerified && currentUserEmail.trim().lowercase() == BOOTSTRAPPED_ADMIN_EMAIL

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _feedback = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val feedback: SharedFlow<String> = _feedback.asSharedFlow()

    init {
        refreshRole()
    }

    fun refreshRole() {
        viewModelScope.launch {
            _roleResolution.value = RoleResolution.Loading
            val admin = repository.resolveIsAdmin(currentUserId, currentUserEmail, emailVerified)
            _roleResolution.value = RoleResolution.Resolved(admin)
        }
    }

    private fun <T> Flow<T>.toUiStateFlow(tag: String, fallbackError: String): StateFlow<UiState<T>> =
        this.map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Stream error [$tag]", rootError)
                emit(UiState.Error(rootError.localizedMessage ?: fallbackError))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = UiState.Loading
            )

    val adminState: StateFlow<UiState<AdminSnapshot>> = _roleResolution
        .flatMapLatest { role ->
            if (role is RoleResolution.Resolved && role.admin) {
                val baseFlow = combine(
                    repository.observeSettings(),
                    repository.observeAllMembers(),
                    repository.observeAllPayments(),
                    repository.observeReminderLogs(),
                    repository.observeJoinRequests()
                ) { settings, members, payments, reminders, requests ->
                    AdminSnapshot(
                        settings = settings,
                        members = members,
                        payments = payments,
                        reminderLogs = reminders,
                        joinRequests = requests
                    )
                }
                combine(
                    baseFlow,
                    repository.observeAdmins(),
                    repository.observeAdminSecurity()
                ) { snapshot, admins, adminSecurity ->
                    snapshot.copy(admins = admins, adminSecurity = adminSecurity)
                }
            } else {
                flowOf(AdminSnapshot())
            }
        }
        .toUiStateFlow("admin", "Unable to load committee dashboard.")

    val memberPortalState: StateFlow<UiState<MemberPortalSnapshot>> = _roleResolution
        .flatMapLatest { role ->
            if (role is RoleResolution.Resolved) {
                val emailLower = currentUserEmail.trim().lowercase()
                combine(
                    repository.observeSettings(),
                    repository.observeMyMemberRecord(currentUserId, emailLower, emailVerified),
                    repository.observeMyPayments(currentUserId, emailLower, emailVerified),
                    repository.observeMyJoinRequest(currentUserId)
                ) { settings, member, payments, joinRequest ->
                    MemberPortalSnapshot(
                        settings = settings,
                        memberRecord = member,
                        payments = payments,
                        joinRequest = joinRequest
                    )
                }
            } else {
                flowOf(MemberPortalSnapshot())
            }
        }
        .toUiStateFlow("member", "Unable to load your contribution profile.")

    fun previousMonth() {
        _selectedMonth.value = DateAndReportUtils.shiftBillingMonth(_selectedMonth.value, -1)
    }

    fun nextMonth() {
        _selectedMonth.value = DateAndReportUtils.shiftBillingMonth(_selectedMonth.value, 1)
    }

    fun selectMonth(month: String) {
        _selectedMonth.value = month
    }

    fun toggleMemberPreview(enabled: Boolean) {
        _previewMemberMode.value = enabled
    }

    private fun runMutation(
        successMessage: String,
        action: suspend () -> Result<Any?>
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val result = action()
                if (result.isSuccess) {
                    _feedback.tryEmit(successMessage)
                } else {
                    val err = result.exceptionOrNull()?.localizedMessage ?: "Operation failed. Please check your inputs."
                    _feedback.tryEmit(err)
                }
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun saveSettings(settings: CommitteeSettings) {
        runMutation("Committee settings updated in real time.") {
            repository.saveSettings(settings)
        }
    }

    fun unlockAdminConsole(username: String, passcode: String): Boolean {
        val security = (adminState.value as? UiState.Success)?.data?.adminSecurity ?: return false
        if (!security.lockEnabled) {
            _adminConsoleUnlocked.value = true
            return true
        }
        val userMatch = username.trim().equals(security.adminUsername.trim(), ignoreCase = true)
        val passMatch = SecurityHashUtils.verifyPasscode(
            saltHex = security.passcodeSalt,
            passcode = passcode,
            expectedHashHex = security.passcodeHash
        )
        if (userMatch && passMatch) {
            _adminConsoleUnlocked.value = true
            return true
        }
        return false
    }

    fun lockAdminConsole() {
        _adminConsoleUnlocked.value = false
    }

    fun saveAdminPasscodeLock(
        enabled: Boolean,
        username: String,
        newPasscode: String,
        existingSecurity: AdminSecurity
    ) {
        val cleanUser = username.trim()
        if (!enabled) {
            runMutation("Admin username & password lock disabled.") {
                repository.saveAdminSecurity(
                    lockEnabled = false,
                    adminUsername = cleanUser,
                    passcodeSalt = "",
                    passcodeHash = ""
                )
            }
            return
        }
        val salt: String
        val hash: String
        if (newPasscode.isNotEmpty()) {
            salt = SecurityHashUtils.generateSaltHex()
            hash = SecurityHashUtils.hashPasscode(salt, newPasscode)
        } else {
            salt = existingSecurity.passcodeSalt
            hash = existingSecurity.passcodeHash
        }
        _adminConsoleUnlocked.value = true
        runMutation("Admin username & password lock saved.") {
            repository.saveAdminSecurity(
                lockEnabled = true,
                adminUsername = cleanUser,
                passcodeSalt = salt,
                passcodeHash = hash
            )
        }
    }

    fun resetAdminLockViaGoogleAdmin() {
        if (!isPrimaryGoogleAdmin) return
        _adminConsoleUnlocked.value = true
        runMutation("Admin lock reset. You can set a new username and password in Settings.") {
            repository.saveAdminSecurity(
                lockEnabled = false,
                adminUsername = "",
                passcodeSalt = "",
                passcodeHash = ""
            )
        }
    }

    fun addMember(
        fullName: String,
        phone: String,
        email: String,
        address: String,
        monthlyAmount: Long,
        status: MembershipStatus
    ) {
        runMutation("$fullName added to committee ledger.") {
            repository.addMember(fullName, phone, email, address, monthlyAmount, status)
        }
    }

    fun updateMember(
        memberId: String,
        fullName: String,
        phone: String,
        email: String,
        address: String,
        monthlyAmount: Long,
        status: MembershipStatus
    ) {
        runMutation("Updated $fullName.") {
            repository.updateMember(memberId, fullName, phone, email, address, monthlyAmount, status)
        }
    }

    fun updateMemberStatus(member: Member, status: MembershipStatus) {
        runMutation("${member.fullName} marked as ${status.label}.") {
            repository.updateMemberStatus(member.id, status)
        }
    }

    fun deleteMember(member: Member) {
        runMutation("Removed ${member.fullName} from members.") {
            repository.deleteMember(member.id)
        }
    }

    fun recordPayment(
        member: Member,
        billingMonth: String,
        amount: Long,
        method: PaymentMethod,
        upiRef: String,
        status: PaymentStatus,
        notes: String
    ) {
        runMutation("Payment of ${DateAndReportUtils.formatInr(amount)} recorded for ${member.fullName}.") {
            repository.recordPayment(member, billingMonth, amount, method, upiRef, status, notes)
        }
    }

    fun updatePayment(
        paymentId: String,
        billingMonth: String,
        amount: Long,
        method: PaymentMethod,
        upiRef: String,
        status: PaymentStatus,
        notes: String
    ) {
        runMutation("Transaction updated in real time.") {
            repository.updatePayment(paymentId, billingMonth, amount, method, upiRef, status, notes)
        }
    }

    fun deletePayment(payment: Payment) {
        runMutation("Transaction deleted.") {
            repository.deletePayment(payment.id)
        }
    }

    fun logWhatsAppReminder(member: Member, billingMonth: String, messagePreview: String) {
        runMutation("Reminder logged for ${member.fullName}.") {
            repository.logWhatsAppReminder(member, billingMonth, messagePreview)
        }
    }

    fun submitMemberUpiPayment(
        member: Member,
        billingMonth: String,
        amount: Long,
        upiRef: String,
        status: PaymentStatus,
        notes: String
    ) {
        val msg = if (status == PaymentStatus.PAID) {
            "UPI payment confirmed! Receipt recorded for ${DateAndReportUtils.formatBillingMonth(billingMonth)}."
        } else {
            "UPI reference submitted for committee verification."
        }
        runMutation(msg) {
            repository.recordPayment(
                member = member,
                billingMonth = billingMonth,
                amount = amount,
                paymentMethod = PaymentMethod.UPI,
                upiRef = upiRef,
                status = status,
                notes = notes
            )
        }
    }

    fun submitJoinRequest(fullName: String, phone: String, address: String) {
        runMutation("Membership request submitted to committee admin.") {
            repository.submitJoinRequest(
                fullName = fullName,
                emailLower = currentUserEmail.trim().lowercase(),
                phone = phone,
                address = address
            )
        }
    }

    fun approveJoinRequest(request: JoinRequest, monthlyAmount: Long) {
        runMutation("${request.fullName} approved as an active member.") {
            repository.approveJoinRequest(request, monthlyAmount)
        }
    }

    fun rejectJoinRequest(request: JoinRequest) {
        runMutation("Request from ${request.fullName} declined.") {
            repository.rejectJoinRequest(request.id)
        }
    }

    fun addCoAdmin(uid: String, email: String, displayName: String) {
        runMutation("Co-admin access granted to $displayName.") {
            repository.addAdmin(uid, email, displayName)
        }
    }

    fun removeCoAdmin(admin: AdminUser) {
        runMutation("Co-admin access revoked for ${admin.displayName}.") {
            repository.removeAdmin(admin.uid)
        }
    }

    private companion object {
        const val TAG = "VinayagarVM"
        const val STOP_TIMEOUT_MS = 5000L
    }
}
