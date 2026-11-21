package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class CommitteeRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    private fun handleStreamError(error: Throwable, uid: String, op: OperationType, path: String) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val toLog = firestoreError ?: (error as? Exception)
        if (toLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(toLog, op, path)
        }
        throw error
    }

    /**
     * Resolves whether the signed-in user is an administrator (bootstrapped email or /admins/{uid}).
     */
    suspend fun resolveIsAdmin(uid: String, email: String?, emailVerified: Boolean): Boolean {
        if (uid.isBlank()) return false
        if (emailVerified && email?.trim()?.lowercase() == BOOTSTRAPPED_ADMIN_EMAIL) {
            return true
        }
        val path = "admins/$uid"
        return try {
            val snap = db.collection("admins").document(uid).get().await()
            snap.exists()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            false
        }
    }

    fun observeSettings(): Flow<CommitteeSettings> = flow {
        val uid = requireUserId()
        val path = "settings/committee"
        emitAll(
            db.collection("settings").document("committee")
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(
                            CommitteeSettings::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        ) ?: CommitteeSettings()
                    } else {
                        CommitteeSettings()
                    }
                }
                .catch { handleStreamError(it, uid, OperationType.GET, path) }
        )
    }

    fun observeAdminSecurity(): Flow<AdminSecurity> = flow {
        val uid = requireUserId()
        val path = "settings/admin_security"
        emitAll(
            db.collection("settings").document("admin_security")
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(
                            AdminSecurity::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        ) ?: AdminSecurity()
                    } else {
                        AdminSecurity()
                    }
                }
                .catch { handleStreamError(it, uid, OperationType.GET, path) }
        )
    }

    fun observeAllMembers(): Flow<List<Member>> = flow {
        val uid = requireUserId()
        val path = "members"
        emitAll(
            db.collection("members")
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        Member::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.fullName.lowercase() }
                }
                .catch { handleStreamError(it, uid, OperationType.LIST, path) }
        )
    }

    fun observeAllPayments(): Flow<List<Payment>> = flow {
        val uid = requireUserId()
        val path = "payments"
        emitAll(
            db.collection("payments")
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        Payment::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { handleStreamError(it, uid, OperationType.LIST, path) }
        )
    }

    fun observeReminderLogs(): Flow<List<ReminderLog>> = flow {
        val uid = requireUserId()
        val path = "reminder_logs"
        emitAll(
            db.collection("reminder_logs")
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        ReminderLog::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { handleStreamError(it, uid, OperationType.LIST, path) }
        )
    }

    fun observeJoinRequests(): Flow<List<JoinRequest>> = flow {
        val uid = requireUserId()
        val path = "join_requests"
        emitAll(
            db.collection("join_requests")
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        JoinRequest::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { handleStreamError(it, uid, OperationType.LIST, path) }
        )
    }

    fun observeAdmins(): Flow<List<AdminUser>> = flow {
        val uid = requireUserId()
        val path = "admins"
        emitAll(
            db.collection("admins")
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        AdminUser::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { handleStreamError(it, uid, OperationType.LIST, path) }
        )
    }

    /**
     * Scoped member query aligned with firestore.rules: matches userId OR verified emailLower.
     */
    fun observeMyMemberRecord(uid: String, emailLower: String, emailVerified: Boolean): Flow<Member?> = flow {
        requireUserId()
        val byUidFlow = db.collection("members")
            .whereEqualTo("userId", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(
                    Member::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { handleStreamError(it, uid, OperationType.LIST, "members[userId]") }

        if (emailVerified && emailLower.isNotBlank()) {
            val byEmailFlow = db.collection("members")
                .whereEqualTo("emailLower", emailLower)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        Member::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { handleStreamError(it, uid, OperationType.LIST, "members[emailLower]") }

            emitAll(
                combine(byUidFlow, byEmailFlow) { byUid, byEmail ->
                    byUid.firstOrNull() ?: byEmail.firstOrNull()
                }
            )
        } else {
            emitAll(byUidFlow.map { it.firstOrNull() })
        }
    }

    /**
     * Scoped member payments query aligned with firestore.rules: matches memberUserId OR memberEmailLower.
     */
    fun observeMyPayments(uid: String, emailLower: String, emailVerified: Boolean): Flow<List<Payment>> = flow {
        requireUserId()
        val byUidFlow = db.collection("payments")
            .whereEqualTo("memberUserId", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(
                    Payment::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { handleStreamError(it, uid, OperationType.LIST, "payments[memberUserId]") }

        if (emailVerified && emailLower.isNotBlank()) {
            val byEmailFlow = db.collection("payments")
                .whereEqualTo("memberEmailLower", emailLower)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        Payment::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { handleStreamError(it, uid, OperationType.LIST, "payments[memberEmailLower]") }

            emitAll(
                combine(byUidFlow, byEmailFlow) { byUid, byEmail ->
                    (byUid + byEmail)
                        .distinctBy { it.id }
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
            )
        } else {
            emitAll(byUidFlow.map { list -> list.sortedByDescending { it.createdAt?.seconds ?: 0L } })
        }
    }

    fun observeMyJoinRequest(uid: String): Flow<JoinRequest?> = flow {
        requireUserId()
        val path = "join_requests/$uid"
        emitAll(
            db.collection("join_requests").document(uid)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(
                            JoinRequest::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else null
                }
                .catch { handleStreamError(it, uid, OperationType.GET, path) }
        )
    }

    // --- Admin Mutations ---

    suspend fun saveSettings(settings: CommitteeSettings): Result<Unit> {
        val path = "settings/committee"
        return try {
            requireUserId()
            val festivalName = settings.festivalName.trim().take(100)
            val upiId = settings.upiId.trim().take(100)
            val upiPayeeName = settings.upiPayeeName.trim().take(80)
            val template = settings.whatsappMessageTemplate.trim().take(600)
            require(festivalName.isNotEmpty()) { "Committee name is required." }
            require(upiId.length >= 3) { "Valid UPI ID is required." }
            require(upiPayeeName.isNotEmpty()) { "UPI payee name is required." }
            require(settings.defaultMonthlyAmount in 1L..1_000_000L) { "Default amount must be between ₹1 and ₹10,00,000." }
            require(settings.dueDayOfMonth in 1L..28L) { "Due day must be between 1 and 28." }
            require(settings.reminderDaysBefore in 1L..15L) { "Reminder window must be between 1 and 15 days." }
            require(template.length >= 10) { "WhatsApp reminder message must be at least 10 characters." }

            val payload = mapOf(
                "festivalName" to festivalName,
                "upiId" to upiId,
                "upiPayeeName" to upiPayeeName,
                "defaultMonthlyAmount" to settings.defaultMonthlyAmount,
                "dueDayOfMonth" to settings.dueDayOfMonth,
                "reminderDaysBefore" to settings.reminderDaysBefore,
                "whatsappMessageTemplate" to template,
                "autoReminderEnabled" to settings.autoReminderEnabled,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("settings").document("committee").set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun saveAdminSecurity(
        lockEnabled: Boolean,
        adminUsername: String,
        passcodeSalt: String,
        passcodeHash: String
    ): Result<Unit> {
        val path = "settings/admin_security"
        return try {
            val uid = requireUserId()
            val cleanUser = adminUsername.trim().take(60)
            if (lockEnabled) {
                require(cleanUser.length >= 3) { "Admin username must be at least 3 characters." }
                require(passcodeSalt.length >= 16 && passcodeHash.length == 64) { "Invalid passcode digest." }
            }
            val payload = mapOf(
                "lockEnabled" to lockEnabled,
                "adminUsername" to cleanUser,
                "passcodeSalt" to if (lockEnabled) passcodeSalt else "",
                "passcodeHash" to if (lockEnabled) passcodeHash else "",
                "updatedByUid" to uid,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("settings").document("admin_security").set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun addMember(
        fullName: String,
        phone: String,
        email: String,
        address: String,
        monthlyAmount: Long,
        membershipStatus: MembershipStatus,
        linkedUserId: String = ""
    ): Result<String> {
        var path = "members"
        return try {
            requireUserId()
            val cleanName = fullName.trim().take(80)
            val cleanPhone = phone.trim().take(20)
            val cleanEmail = email.trim().take(120)
            val cleanAddress = address.trim().take(160)
            require(cleanName.isNotEmpty()) { "Member name is required." }
            require(cleanPhone.length >= 7) { "Enter a valid mobile/WhatsApp number." }
            require(monthlyAmount in 1L..1_000_000L) { "Monthly amount must be between ₹1 and ₹10,00,000." }

            val docRef = db.collection("members").document()
            path = docRef.path
            val payload = mapOf(
                "fullName" to cleanName,
                "phone" to cleanPhone,
                "email" to cleanEmail,
                "emailLower" to cleanEmail.lowercase(),
                "address" to cleanAddress,
                "monthlyAmount" to monthlyAmount,
                "membershipStatus" to membershipStatus.name,
                "userId" to linkedUserId.trim().take(128),
                "lastRemindedMonth" to "",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateMember(
        memberId: String,
        fullName: String,
        phone: String,
        email: String,
        address: String,
        monthlyAmount: Long,
        membershipStatus: MembershipStatus
    ): Result<Unit> {
        val path = "members/$memberId"
        return try {
            requireUserId()
            require(memberId.isNotBlank()) { "Member ID is required." }
            val cleanName = fullName.trim().take(80)
            val cleanPhone = phone.trim().take(20)
            val cleanEmail = email.trim().take(120)
            val cleanAddress = address.trim().take(160)
            require(cleanName.isNotEmpty()) { "Member name is required." }
            require(cleanPhone.length >= 7) { "Enter a valid mobile/WhatsApp number." }
            require(monthlyAmount in 1L..1_000_000L) { "Monthly amount must be valid." }

            db.collection("members").document(memberId).update(
                mapOf(
                    "fullName" to cleanName,
                    "phone" to cleanPhone,
                    "email" to cleanEmail,
                    "emailLower" to cleanEmail.lowercase(),
                    "address" to cleanAddress,
                    "monthlyAmount" to monthlyAmount,
                    "membershipStatus" to membershipStatus.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateMemberStatus(memberId: String, status: MembershipStatus): Result<Unit> {
        val path = "members/$memberId"
        return try {
            requireUserId()
            require(memberId.isNotBlank()) { "Member ID is required." }
            db.collection("members").document(memberId).update(
                mapOf(
                    "membershipStatus" to status.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteMember(memberId: String): Result<Unit> {
        val path = "members/$memberId"
        return try {
            requireUserId()
            require(memberId.isNotBlank()) { "Member ID is required." }
            db.collection("members").document(memberId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun recordPayment(
        member: Member,
        billingMonth: String,
        amount: Long,
        paymentMethod: PaymentMethod,
        upiRef: String,
        status: PaymentStatus,
        notes: String
    ): Result<String> {
        var path = "payments"
        return try {
            val uid = requireUserId()
            require(member.id.isNotBlank()) { "Select a valid member." }
            require(billingMonth.matches(Regex("^[0-9]{4}-[0-9]{2}$"))) { "Invalid billing month." }
            require(amount in 1L..1_000_000L) { "Payment amount must be between ₹1 and ₹10,00,000." }

            val docRef = db.collection("payments").document()
            path = docRef.path
            val payload = mapOf(
                "memberId" to member.id,
                "memberUserId" to member.userId,
                "memberEmailLower" to member.emailLower,
                "memberName" to member.fullName,
                "billingMonth" to billingMonth,
                "amount" to amount,
                "paymentMethod" to paymentMethod.name,
                "upiRef" to upiRef.trim().take(100),
                "status" to status.name,
                "notes" to notes.trim().take(240),
                "recordedByUid" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updatePayment(
        paymentId: String,
        billingMonth: String,
        amount: Long,
        paymentMethod: PaymentMethod,
        upiRef: String,
        status: PaymentStatus,
        notes: String
    ): Result<Unit> {
        val path = "payments/$paymentId"
        return try {
            requireUserId()
            require(paymentId.isNotBlank()) { "Payment ID is required." }
            require(billingMonth.matches(Regex("^[0-9]{4}-[0-9]{2}$"))) { "Invalid billing month." }
            require(amount in 1L..1_000_000L) { "Amount must be valid." }

            db.collection("payments").document(paymentId).update(
                mapOf(
                    "billingMonth" to billingMonth,
                    "amount" to amount,
                    "paymentMethod" to paymentMethod.name,
                    "upiRef" to upiRef.trim().take(100),
                    "status" to status.name,
                    "notes" to notes.trim().take(240),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deletePayment(paymentId: String): Result<Unit> {
        val path = "payments/$paymentId"
        return try {
            requireUserId()
            require(paymentId.isNotBlank()) { "Payment ID is required." }
            db.collection("payments").document(paymentId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun logWhatsAppReminder(
        member: Member,
        billingMonth: String,
        messagePreview: String
    ): Result<Unit> {
        var path = "reminder_logs"
        return try {
            val uid = requireUserId()
            require(member.id.isNotBlank()) { "Invalid member." }
            require(billingMonth.matches(Regex("^[0-9]{4}-[0-9]{2}$"))) { "Invalid month." }

            val batch = db.batch()
            val logRef = db.collection("reminder_logs").document()
            path = logRef.path
            batch.set(
                logRef,
                mapOf(
                    "memberId" to member.id,
                    "memberName" to member.fullName,
                    "memberPhone" to member.phone,
                    "billingMonth" to billingMonth,
                    "channel" to "WHATSAPP",
                    "sentByUid" to uid,
                    "messagePreview" to messagePreview.trim().take(500),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            val memberRef = db.collection("members").document(member.id)
            batch.update(
                memberRef,
                mapOf(
                    "lastRemindedMonth" to billingMonth,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun submitJoinRequest(
        fullName: String,
        emailLower: String,
        phone: String,
        address: String
    ): Result<Unit> {
        var path = "join_requests"
        return try {
            val uid = requireUserId()
            path = "join_requests/$uid"
            val cleanName = fullName.trim().take(80)
            val cleanPhone = phone.trim().take(20)
            val cleanAddress = address.trim().take(160)
            require(cleanName.isNotEmpty()) { "Full name is required." }
            require(cleanPhone.length >= 7) { "Valid WhatsApp mobile number is required." }
            require(emailLower.length >= 3) { "Verified Google email is required." }

            val payload = mapOf(
                "userId" to uid,
                "fullName" to cleanName,
                "emailLower" to emailLower.trim().lowercase(),
                "phone" to cleanPhone,
                "address" to cleanAddress,
                "status" to JoinRequestStatus.PENDING.name,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("join_requests").document(uid).set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun approveJoinRequest(request: JoinRequest, monthlyAmount: Long): Result<Unit> {
        val path = "join_requests/${request.id}"
        return try {
            requireUserId()
            require(monthlyAmount in 1L..1_000_000L) { "Amount must be valid." }
            val batch = db.batch()
            val memberRef = db.collection("members").document()
            batch.set(
                memberRef,
                mapOf(
                    "fullName" to request.fullName.trim().take(80),
                    "phone" to request.phone.trim().take(20),
                    "email" to request.emailLower,
                    "emailLower" to request.emailLower.lowercase(),
                    "address" to request.address.trim().take(160),
                    "monthlyAmount" to monthlyAmount,
                    "membershipStatus" to MembershipStatus.ACTIVE.name,
                    "userId" to request.userId,
                    "lastRemindedMonth" to "",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            val reqRef = db.collection("join_requests").document(request.id)
            batch.update(
                reqRef,
                mapOf(
                    "status" to JoinRequestStatus.APPROVED.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun rejectJoinRequest(requestId: String): Result<Unit> {
        val path = "join_requests/$requestId"
        return try {
            requireUserId()
            db.collection("join_requests").document(requestId).update(
                mapOf(
                    "status" to JoinRequestStatus.REJECTED.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun addAdmin(uid: String, email: String, displayName: String): Result<Unit> {
        val cleanUid = uid.trim()
        val path = "admins/$cleanUid"
        return try {
            val addedByUid = requireUserId()
            require(cleanUid.matches(Regex("^[a-zA-Z0-9_\\-]+$"))) { "Enter a valid Firebase UID." }
            val cleanEmail = email.trim().take(120)
            val cleanName = displayName.trim().take(80)
            require(cleanEmail.length >= 3) { "Valid email is required." }
            require(cleanName.isNotEmpty()) { "Display name is required." }
            db.collection("admins").document(cleanUid).set(
                mapOf(
                    "uid" to cleanUid,
                    "email" to cleanEmail,
                    "displayName" to cleanName,
                    "addedByUid" to addedByUid,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun removeAdmin(uid: String): Result<Unit> {
        val path = "admins/$uid"
        return try {
            requireUserId()
            db.collection("admins").document(uid).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }
}
