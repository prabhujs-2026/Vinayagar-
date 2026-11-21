package com.example.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.AdminSecurity
import com.example.data.AdminUser
import com.example.data.CommitteeSettings
import com.example.data.Member
import com.example.data.MembershipStatus
import com.example.data.Payment
import com.example.data.PaymentMethod
import com.example.data.PaymentStatus
import com.example.util.DateAndReportUtils

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MemberEditorDialog(
    initialMember: Member?,
    defaultAmount: Long,
    onSave: (String, String, String, String, Long, MembershipStatus) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var fullName by remember(initialMember) { mutableStateOf(initialMember?.fullName ?: "") }
    var phone by remember(initialMember) { mutableStateOf(initialMember?.phone ?: "") }
    var email by remember(initialMember) { mutableStateOf(initialMember?.email ?: "") }
    var address by remember(initialMember) { mutableStateOf(initialMember?.address ?: "") }
    var amountText by remember(initialMember) {
        mutableStateOf((initialMember?.monthlyAmount ?: defaultAmount).toString())
    }
    var status by remember(initialMember) {
        mutableStateOf(initialMember?.statusEnum() ?: MembershipStatus.ACTIVE)
    }
    var validationError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialMember == null) "Add Committee Member" else "Edit Member",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it; validationError = null },
                    label = { Text("Full Name *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("member_name_input")
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it; validationError = null },
                    label = { Text("WhatsApp Mobile Number *") },
                    placeholder = { Text("+91 9876543210") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("member_phone_input")
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Google Email (for Member APK login)") },
                    placeholder = { Text("member@gmail.com") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("member_email_input")
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Street / House / Ward") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { ch -> ch.isDigit() }; validationError = null },
                    label = { Text("Monthly Contribution (₹) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("member_amount_input")
                )

                Text(
                    text = "Membership Status",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MembershipStatus.entries.forEach { option ->
                        FilterChip(
                            selected = status == option,
                            onClick = { status = option },
                            label = { Text(option.label) }
                        )
                    }
                }

                if (validationError != null) {
                    Text(
                        text = validationError ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (initialMember != null && onDelete != null) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    OutlinedButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("delete_member_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delete Member Record")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountText.toLongOrNull() ?: 0L
                    when {
                        fullName.trim().isEmpty() -> validationError = "Please enter member's full name."
                        phone.filter { it.isDigit() }.length < 7 -> validationError = "Enter a valid mobile number (at least 7 digits)."
                        amt !in 1L..1_000_000L -> validationError = "Monthly amount must be between ₹1 and ₹10,00,000."
                        else -> onSave(fullName.trim(), phone.trim(), email.trim(), address.trim(), amt, status)
                    }
                },
                modifier = Modifier.testTag("save_member_button")
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PaymentEditorDialog(
    members: List<Member>,
    initialMember: Member?,
    initialPayment: Payment?,
    billingMonth: String,
    onCreate: (Member, String, Long, PaymentMethod, String, PaymentStatus, String) -> Unit,
    onUpdate: (String, String, Long, PaymentMethod, String, PaymentStatus, String) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var selectedMember by remember(initialMember, initialPayment) {
        mutableStateOf(
            initialMember
                ?: members.firstOrNull { it.id == initialPayment?.memberId }
                ?: members.firstOrNull()
        )
    }
    var memberDropdownOpen by remember { mutableStateOf(false) }
    var monthText by remember(initialPayment, billingMonth) {
        mutableStateOf(initialPayment?.billingMonth?.ifBlank { billingMonth } ?: billingMonth)
    }
    var amountText by remember(initialPayment, selectedMember) {
        mutableStateOf(
            (initialPayment?.amount ?: selectedMember?.monthlyAmount ?: 500L).toString()
        )
    }
    var method by remember(initialPayment) {
        mutableStateOf(initialPayment?.methodEnum() ?: PaymentMethod.UPI)
    }
    var status by remember(initialPayment) {
        mutableStateOf(initialPayment?.statusEnum() ?: PaymentStatus.PAID)
    }
    var upiRef by remember(initialPayment) { mutableStateOf(initialPayment?.upiRef ?: "") }
    var notes by remember(initialPayment) { mutableStateOf(initialPayment?.notes ?: "") }
    var validationError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialPayment == null) "Record Monthly Payment" else "Edit Transaction",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (initialPayment == null) {
                    ExposedDropdownMenuBox(
                        expanded = memberDropdownOpen,
                        onExpandedChange = { memberDropdownOpen = !memberDropdownOpen }
                    ) {
                        OutlinedTextField(
                            value = selectedMember?.fullName ?: "Select member",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Member *") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = memberDropdownOpen) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = memberDropdownOpen,
                            onDismissRequest = { memberDropdownOpen = false }
                        ) {
                            members.forEach { m ->
                                DropdownMenuItem(
                                    text = { Text("${m.fullName} (${DateAndReportUtils.formatInr(m.monthlyAmount)})") },
                                    onClick = {
                                        selectedMember = m
                                        amountText = m.monthlyAmount.toString()
                                        memberDropdownOpen = false
                                    }
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Member: ${initialPayment.memberName}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                OutlinedTextField(
                    value = monthText,
                    onValueChange = { monthText = it; validationError = null },
                    label = { Text("Billing Month (YYYY-MM) *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { ch -> ch.isDigit() }; validationError = null },
                    label = { Text("Amount Paid (₹) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("payment_amount_input")
                )

                Text(
                    text = "Payment Method",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PaymentMethod.entries.forEach { opt ->
                        FilterChip(
                            selected = method == opt,
                            onClick = { method = opt },
                            label = { Text(opt.label) }
                        )
                    }
                }

                Text(
                    text = "Transaction Status",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PaymentStatus.entries.forEach { opt ->
                        FilterChip(
                            selected = status == opt,
                            onClick = { status = opt },
                            label = { Text(opt.label) }
                        )
                    }
                }

                OutlinedTextField(
                    value = upiRef,
                    onValueChange = { upiRef = it },
                    label = { Text("UPI Transaction ID / UTR Ref") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("payment_utr_input")
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Receipt Remarks / Notes") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                if (validationError != null) {
                    Text(
                        text = validationError ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (initialPayment != null && onDelete != null) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    OutlinedButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("delete_payment_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delete Transaction")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountText.toLongOrNull() ?: 0L
                    when {
                        initialPayment == null && selectedMember == null ->
                            validationError = "Add a member first before recording a payment."
                        !monthText.matches(Regex("^[0-9]{4}-[0-9]{2}$")) ->
                            validationError = "Use YYYY-MM format (e.g., 2026-11)."
                        amt !in 1L..1_000_000L ->
                            validationError = "Amount must be between ₹1 and ₹10,00,000."
                        else -> {
                            if (initialPayment == null) {
                                onCreate(selectedMember!!, monthText, amt, method, upiRef.trim(), status, notes.trim())
                            } else {
                                onUpdate(initialPayment.id, monthText, amt, method, upiRef.trim(), status, notes.trim())
                            }
                        }
                    }
                },
                modifier = Modifier.testTag("save_payment_button")
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
fun CommitteeSettingsDialog(
    currentSettings: CommitteeSettings,
    currentSecurity: AdminSecurity,
    admins: List<AdminUser>,
    onSaveSettings: (CommitteeSettings) -> Unit,
    onSaveAdminLock: (enabled: Boolean, username: String, newPasscode: String) -> Unit,
    onAddCoAdmin: (uid: String, email: String, name: String) -> Unit,
    onRemoveCoAdmin: (AdminUser) -> Unit,
    onDismiss: () -> Unit
) {
    var festivalName by remember(currentSettings) { mutableStateOf(currentSettings.festivalName) }
    var upiId by remember(currentSettings) { mutableStateOf(currentSettings.upiId) }
    var upiPayeeName by remember(currentSettings) { mutableStateOf(currentSettings.upiPayeeName) }
    var defaultAmt by remember(currentSettings) { mutableStateOf(currentSettings.defaultMonthlyAmount.toString()) }
    var dueDay by remember(currentSettings) { mutableStateOf(currentSettings.dueDayOfMonth.toString()) }
    var reminderDays by remember(currentSettings) { mutableStateOf(currentSettings.reminderDaysBefore.toString()) }
    var template by remember(currentSettings) { mutableStateOf(currentSettings.whatsappMessageTemplate) }
    var autoEnabled by remember(currentSettings) { mutableStateOf(currentSettings.autoReminderEnabled) }

    // Changeable Admin Username & Password Lock State
    var lockEnabled by remember(currentSecurity) { mutableStateOf(currentSecurity.lockEnabled) }
    var adminUsername by remember(currentSecurity) {
        mutableStateOf(currentSecurity.adminUsername.ifBlank { "admin" })
    }
    var newPasscode by remember { mutableStateOf("") }
    var confirmPasscode by remember { mutableStateOf("") }
    var showPasscode by remember { mutableStateOf(false) }
    var lockMessage by remember { mutableStateOf<String?>(null) }

    var showAddAdmin by remember { mutableStateOf(false) }
    var coAdminUid by remember { mutableStateOf("") }
    var coAdminEmail by remember { mutableStateOf("") }
    var coAdminName by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Committee Settings", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = festivalName,
                    onValueChange = { festivalName = it },
                    label = { Text("Committee / Festival Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = upiId,
                    onValueChange = { upiId = it },
                    label = { Text("Committee UPI ID (VPA)") },
                    placeholder = { Text("vinayagarcommittee@upi") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_upi_input")
                )
                OutlinedTextField(
                    value = upiPayeeName,
                    onValueChange = { upiPayeeName = it },
                    label = { Text("UPI Payee Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = defaultAmt,
                        onValueChange = { defaultAmt = it.filter { c -> c.isDigit() } },
                        label = { Text("Default ₹") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = dueDay,
                        onValueChange = { dueDay = it.filter { c -> c.isDigit() } },
                        label = { Text("Due Day (1-28)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = reminderDays,
                    onValueChange = { reminderDays = it.filter { c -> c.isDigit() } },
                    label = { Text("Reminder Days Before Due Date (default 5)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Automated 5-Day Due Alerts", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Schedule daily local reminders when deadline is within $reminderDays days",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = autoEnabled, onCheckedChange = { autoEnabled = it })
                }
                OutlinedTextField(
                    value = template,
                    onValueChange = { template = it },
                    label = { Text("Gentle WhatsApp Reminder Template") },
                    supportingText = { Text("Placeholders: {name}, {month}, {amount}, {dueDate}, {upiId}, {festivalName}") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                // Admin Console Username & Password Lock Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text("Admin Username & Password Lock", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "Require custom username & password to open Admin Console (changeable anytime)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = lockEnabled,
                        onCheckedChange = {
                            lockEnabled = it
                            lockMessage = null
                        },
                        modifier = Modifier.testTag("settings_admin_lock_switch")
                    )
                }

                if (lockEnabled) {
                    OutlinedTextField(
                        value = adminUsername,
                        onValueChange = { adminUsername = it; lockMessage = null },
                        label = { Text("Admin Username (min 3 chars)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_admin_username_input")
                    )
                    OutlinedTextField(
                        value = newPasscode,
                        onValueChange = { newPasscode = it; lockMessage = null },
                        label = {
                            Text(
                                if (currentSecurity.passcodeHash.isNotBlank()) {
                                    "New Password / PIN (leave blank to keep current)"
                                } else {
                                    "Set Admin Password / PIN (min 4 chars) *"
                                }
                            )
                        },
                        singleLine = true,
                        visualTransformation = if (showPasscode) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPasscode = !showPasscode }) {
                                Icon(
                                    imageVector = if (showPasscode) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = if (showPasscode) "Hide password" else "Show password"
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_admin_password_input")
                    )
                    if (newPasscode.isNotEmpty()) {
                        OutlinedTextField(
                            value = confirmPasscode,
                            onValueChange = { confirmPasscode = it; lockMessage = null },
                            label = { Text("Confirm Password / PIN") },
                            singleLine = true,
                            visualTransformation = if (showPasscode) VisualTransformation.None else PasswordVisualTransformation(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_admin_confirm_password_input")
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        when {
                            !lockEnabled -> {
                                onSaveAdminLock(false, adminUsername.trim(), "")
                                lockMessage = "Admin lock turned off."
                            }
                            adminUsername.trim().length < 3 -> {
                                lockMessage = "Username must be at least 3 characters."
                            }
                            currentSecurity.passcodeHash.isBlank() && newPasscode.length < 4 -> {
                                lockMessage = "Password / PIN must be at least 4 characters."
                            }
                            newPasscode.isNotEmpty() && newPasscode.length < 4 -> {
                                lockMessage = "New password / PIN must be at least 4 characters."
                            }
                            newPasscode.isNotEmpty() && newPasscode != confirmPasscode -> {
                                lockMessage = "Passwords do not match."
                            }
                            else -> {
                                onSaveAdminLock(true, adminUsername.trim(), newPasscode)
                                newPasscode = ""
                                confirmPasscode = ""
                                lockMessage = "Admin username & password saved."
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("save_admin_lock_button")
                ) {
                    Text(if (lockEnabled) "Save Admin Username & Password" else "Disable Admin Lock")
                }

                if (lockMessage != null) {
                    Text(
                        text = lockMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Co-Administrators (${admins.size})", style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = { showAddAdmin = !showAddAdmin }) {
                        Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add")
                    }
                }
                if (showAddAdmin) {
                    OutlinedTextField(
                        value = coAdminName,
                        onValueChange = { coAdminName = it },
                        label = { Text("Admin Display Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = coAdminEmail,
                        onValueChange = { coAdminEmail = it },
                        label = { Text("Admin Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = coAdminUid,
                        onValueChange = { coAdminUid = it },
                        label = { Text("Firebase User UID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedButton(
                        onClick = {
                            if (coAdminUid.isNotBlank() && coAdminEmail.isNotBlank() && coAdminName.isNotBlank()) {
                                onAddCoAdmin(coAdminUid.trim(), coAdminEmail.trim(), coAdminName.trim())
                                coAdminUid = ""; coAdminEmail = ""; coAdminName = ""; showAddAdmin = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Grant Co-Admin Access")
                    }
                }
                admins.forEach { admin ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(admin.displayName, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                admin.email,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onRemoveCoAdmin(admin) }) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = stringResource(R.string.cd_delete),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                if (validationError != null) {
                    Text(
                        text = validationError ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = defaultAmt.toLongOrNull() ?: 0L
                    val dDay = dueDay.toLongOrNull() ?: 0L
                    val rDays = reminderDays.toLongOrNull() ?: 0L
                    when {
                        festivalName.trim().isEmpty() -> validationError = "Enter committee name."
                        upiId.trim().length < 3 -> validationError = "Enter a valid UPI ID."
                        amt !in 1L..1_000_000L -> validationError = "Enter a valid default amount."
                        dDay !in 1L..28L -> validationError = "Due day must be between 1 and 28."
                        rDays !in 1L..15L -> validationError = "Reminder days must be between 1 and 15."
                        template.trim().length < 10 -> validationError = "Reminder message is too short."
                        else -> {
                            onSaveSettings(
                                currentSettings.copy(
                                    festivalName = festivalName.trim(),
                                    upiId = upiId.trim(),
                                    upiPayeeName = upiPayeeName.trim().ifBlank { festivalName.trim() },
                                    defaultMonthlyAmount = amt,
                                    dueDayOfMonth = dDay,
                                    reminderDaysBefore = rDays,
                                    whatsappMessageTemplate = template.trim(),
                                    autoReminderEnabled = autoEnabled
                                )
                            )
                        }
                    }
                },
                modifier = Modifier.testTag("save_settings_button")
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
