package com.example.ui.member

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.JoinRequestStatus
import com.example.data.Member
import com.example.data.MembershipStatus
import com.example.data.Payment
import com.example.data.PaymentStatus
import com.example.ui.MemberPortalSnapshot
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.MonthSelectorBar
import com.example.ui.components.StatusPill
import com.example.ui.components.UpiQrCard
import com.example.ui.theme.LocalExtendedColors
import com.example.util.DateAndReportUtils

@Composable
fun MemberDuesScreen(
    snapshot: MemberPortalSnapshot,
    selectedMonth: String,
    userDisplayName: String,
    userEmail: String,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSubmitUpiPayment: (Member, String, Long, String, PaymentStatus, String) -> Unit,
    onSubmitJoinRequest: (String, String, String) -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val extended = LocalExtendedColors.current
    val member = snapshot.memberRecord
    val settings = snapshot.settings

    var showUtrDialog by remember { mutableStateOf(false) }
    var lastLaunchedTxnRef by remember { mutableStateOf("") }

    val upiLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val rawResponse = result.data?.getStringExtra("response")
        val parsed = DateAndReportUtils.parseUpiResponse(rawResponse)
        val activeMember = snapshot.memberRecord
        if (activeMember != null) {
            when {
                parsed.succeeded -> {
                    onSubmitUpiPayment(
                        activeMember,
                        selectedMonth,
                        activeMember.monthlyAmount,
                        parsed.bestReference.ifBlank { lastLaunchedTxnRef },
                        PaymentStatus.PAID,
                        "Paid directly via UPI app"
                    )
                }
                parsed.submitted -> {
                    onSubmitUpiPayment(
                        activeMember,
                        selectedMonth,
                        activeMember.monthlyAmount,
                        parsed.bestReference.ifBlank { lastLaunchedTxnRef },
                        PaymentStatus.PENDING_VERIFICATION,
                        "UPI payment submitted — awaiting confirmation"
                    )
                }
                else -> {
                    onShowMessage("UPI payment was cancelled or no response returned. If you paid, tap 'Submit UPI Ref'.")
                }
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (member == null) {
            item {
                UnlinkedMemberJoinCard(
                    userDisplayName = userDisplayName,
                    userEmail = userEmail,
                    festivalName = settings.festivalName,
                    existingRequestStatus = snapshot.joinRequest?.statusEnum(),
                    onSubmitJoinRequest = onSubmitJoinRequest
                )
            }
        } else {
            val monthPayment = snapshot.payments.firstOrNull {
                it.billingMonth == selectedMonth && it.statusEnum() != PaymentStatus.REJECTED
            }
            val isPaid = monthPayment?.statusEnum() == PaymentStatus.PAID
            val isPendingReview = monthPayment?.statusEnum() == PaymentStatus.PENDING_VERIFICATION
            val isActive = member.statusEnum() == MembershipStatus.ACTIVE
            val deadlineLabel = DateAndReportUtils.deadlineStatusLabel(selectedMonth, settings.dueDayOfMonth)
            val dueDateStr = DateAndReportUtils.formatDueDate(selectedMonth, settings.dueDayOfMonth)

            val txnRef = "VF${selectedMonth.replace("-", "")}${member.id.take(6).uppercase()}"
            val upiNote = "${settings.festivalName.take(30)} ${DateAndReportUtils.formatShortMonth(selectedMonth)} - ${member.fullName.take(24)}"
            val upiUri = DateAndReportUtils.buildUpiPayUri(
                upiId = settings.upiId,
                payeeName = settings.upiPayeeName,
                amountInr = member.monthlyAmount,
                note = upiNote,
                txnRef = txnRef
            )

            item {
                MonthSelectorBar(
                    billingMonth = selectedMonth,
                    dueDayOfMonth = settings.dueDayOfMonth,
                    onPreviousMonth = onPrevMonth,
                    onNextMonth = onNextMonth
                )
            }

            // Festive Member Due Hero Card
            item {
                Card(
                    shape = MaterialTheme.shapes.extraLarge,
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Image(
                            painter = painterResource(id = R.drawable.img_hero_banner),
                            contentDescription = stringResource(R.string.cd_hero_banner),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(210.dp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(210.dp)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0xDC64110D),
                                            Color(0xEA8C1D18)
                                        )
                                    )
                                )
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Namaskaram, ${member.fullName}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color(0xFFFFEBC2),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                if (isActive && !isPaid && !isPendingReview) {
                                    Button(
                                        onClick = {
                                            lastLaunchedTxnRef = txnRef
                                            val upiIntent = Intent(Intent.ACTION_VIEW, upiUri)
                                            val chooser = Intent.createChooser(upiIntent, "Pay Vinayagar Contribution via UPI")
                                            if (upiIntent.resolveActivity(context.packageManager) != null) {
                                                upiLauncher.launch(chooser)
                                            } else {
                                                onShowMessage("No UPI app detected on this device. Scan the QR code or copy the UPI ID and submit your UTR.")
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFF4C44E),
                                            contentColor = Color(0xFF321E00)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("header_pay_upi_button")
                                    ) {
                                        Icon(Icons.Rounded.QrCode2, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = stringResource(R.string.action_upi_pay),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                } else {
                                    StatusPill(
                                        label = member.statusEnum().label,
                                        containerColor = Color.White.copy(alpha = 0.2f),
                                        contentColor = Color.White
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = DateAndReportUtils.formatInr(member.monthlyAmount),
                                style = MaterialTheme.typography.displaySmall,
                                color = Color.White
                            )
                            Text(
                                text = "${DateAndReportUtils.formatBillingMonth(selectedMonth)} Contribution • Due $dueDateStr",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.88f)
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            when {
                                isPaid -> StatusPill(
                                    label = "Paid & Verified",
                                    containerColor = extended.successContainer,
                                    contentColor = extended.onSuccessContainer
                                )
                                isPendingReview -> StatusPill(
                                    label = "Submitted • Pending Admin Verification",
                                    containerColor = extended.warningContainer,
                                    contentColor = extended.onWarningContainer
                                )
                                !isActive -> StatusPill(
                                    label = "Membership ${member.statusEnum().label}",
                                    containerColor = Color.White.copy(alpha = 0.22f),
                                    contentColor = Color.White
                                )
                                else -> StatusPill(
                                    label = "Pending • $deadlineLabel",
                                    containerColor = Color(0xFFF4C44E),
                                    contentColor = Color(0xFF321E00)
                                )
                            }
                        }
                    }
                }
            }

            // Actionable UPI Pay Section
            if (isActive && !isPaid && !isPendingReview) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                lastLaunchedTxnRef = txnRef
                                val upiIntent = Intent(Intent.ACTION_VIEW, upiUri)
                                val chooser = Intent.createChooser(upiIntent, "Pay Vinayagar Contribution via UPI")
                                if (upiIntent.resolveActivity(context.packageManager) != null) {
                                    upiLauncher.launch(chooser)
                                } else {
                                    onShowMessage("No UPI app detected on this device. Scan the QR code or copy the UPI ID and submit your UTR.")
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("pay_upi_button")
                        ) {
                            Icon(Icons.Rounded.AccountBalanceWallet, contentDescription = null)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "${stringResource(R.string.action_pay_upi)} (${DateAndReportUtils.formatInr(member.monthlyAmount)})",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        OutlinedButton(
                            onClick = { showUtrDialog = true },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("submit_utr_button")
                        ) {
                            Text(stringResource(R.string.action_submit_utr))
                        }
                    }
                }

                item {
                    UpiQrCard(
                        upiUriString = upiUri.toString(),
                        upiId = settings.upiId,
                        payeeName = settings.upiPayeeName,
                        amountInr = member.monthlyAmount,
                        onCopyUpiId = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("UPI ID", settings.upiId))
                            onShowMessage("Copied UPI ID: ${settings.upiId}")
                        }
                    )
                }
            } else if (isPaid && monthPayment != null) {
                item {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = extended.successContainer),
                        border = BorderStroke(1.dp, extended.success.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Verified,
                                contentDescription = null,
                                tint = extended.success,
                                modifier = Modifier.size(32.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Thank you for your contribution!",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = extended.onSuccessContainer
                                )
                                Text(
                                    text = buildString {
                                        append("Receipt: ${DateAndReportUtils.formatInr(monthPayment.amount)} via ${monthPayment.methodEnum().label}")
                                        if (monthPayment.upiRef.isNotBlank()) append(" • Ref: ${monthPayment.upiRef}")
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = extended.onSuccessContainer.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(72.dp)) }
    }

    if (showUtrDialog && member != null) {
        SubmitUpiRefDialog(
            member = member,
            billingMonth = selectedMonth,
            onSubmit = { utr, notes ->
                onSubmitUpiPayment(
                    member,
                    selectedMonth,
                    member.monthlyAmount,
                    utr,
                    PaymentStatus.PENDING_VERIFICATION,
                    notes.ifBlank { "Member submitted UPI reference" }
                )
                showUtrDialog = false
            },
            onDismiss = { showUtrDialog = false }
        )
    }
}

@Composable
fun MemberReceiptsScreen(
    snapshot: MemberPortalSnapshot,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val extended = LocalExtendedColors.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "My Payment Receipts (${snapshot.payments.size})",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = "Real-time verified receipts from ${snapshot.settings.festivalName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (snapshot.payments.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Rounded.ReceiptLong,
                    title = "No payment receipts yet",
                    subtitle = "Once you pay your monthly contribution via UPI or committee admin records it, your receipt appears here instantly."
                )
            }
        } else {
            items(snapshot.payments, key = { it.id }) { payment ->
                val (bg, fg) = when (payment.statusEnum()) {
                    PaymentStatus.PAID -> extended.successContainer to extended.onSuccessContainer
                    PaymentStatus.PENDING_VERIFICATION -> extended.warningContainer to extended.onWarningContainer
                    PaymentStatus.REJECTED -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
                }
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = DateAndReportUtils.formatBillingMonth(payment.billingMonth),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                StatusPill(
                                    label = payment.statusEnum().label,
                                    containerColor = bg,
                                    contentColor = fg
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${DateAndReportUtils.formatInr(payment.amount)} • ${payment.methodEnum().label}" +
                                    if (payment.upiRef.isNotBlank()) " • UTR: ${payment.upiRef}" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Date: ${DateAndReportUtils.formatPaymentDate(payment)} • Recorded ${DateAndReportUtils.formatTimestamp(payment.createdAt)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = {
                                val receiptText = buildString {
                                    appendLine("*${snapshot.settings.festivalName} - Payment Receipt*")
                                    appendLine("Member: ${payment.memberName}")
                                    appendLine("Month : ${DateAndReportUtils.formatBillingMonth(payment.billingMonth)}")
                                    appendLine("Date  : ${DateAndReportUtils.formatPaymentDate(payment)}")
                                    appendLine("Amount: ${DateAndReportUtils.formatInr(payment.amount)}")
                                    appendLine("Mode  : ${payment.methodEnum().label}")
                                    if (payment.upiRef.isNotBlank()) appendLine("Ref   : ${payment.upiRef}")
                                    appendLine("Status: ${payment.statusEnum().label}")
                                }
                                DateAndReportUtils.shareText(context, "Vinayagar Contribution Receipt", receiptText)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Share,
                                contentDescription = "Share Receipt",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(72.dp)) }
    }
}

@Composable
private fun UnlinkedMemberJoinCard(
    userDisplayName: String,
    userEmail: String,
    festivalName: String,
    existingRequestStatus: JoinRequestStatus?,
    onSubmitJoinRequest: (String, String, String) -> Unit
) {
    var name by remember(userDisplayName) { mutableStateOf(userDisplayName) }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val extended = LocalExtendedColors.current

    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (existingRequestStatus == JoinRequestStatus.PENDING) {
                            Icons.Rounded.HourglassTop
                        } else {
                            Icons.Rounded.CheckCircle
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
                Column {
                    Text(
                        text = festivalName,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = userEmail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (existingRequestStatus == JoinRequestStatus.PENDING) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = extended.warningContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Your membership request has been sent to the committee administrator. Once approved, your monthly UPI payment card will appear here automatically in real time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = extended.onWarningContainer,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            } else {
                Text(
                    text = "Your Google account isn't linked to a committee member record yet. Submit your details below so the administrator can activate your monthly membership.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("Full Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it; error = null },
                    label = { Text("WhatsApp Mobile Number *") },
                    placeholder = { Text("+91 9876543210") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Street / House / Ward") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Text(error ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Button(
                    onClick = {
                        when {
                            name.trim().isEmpty() -> error = "Enter your full name."
                            phone.filter { it.isDigit() }.length < 7 -> error = "Enter a valid mobile number."
                            else -> onSubmitJoinRequest(name.trim(), phone.trim(), address.trim())
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("submit_join_request_button")
                ) {
                    Text("Request Committee Membership")
                }
            }
        }
    }
}

@Composable
private fun SubmitUpiRefDialog(
    member: Member,
    billingMonth: String,
    onSubmit: (utr: String, notes: String) -> Unit,
    onDismiss: () -> Unit
) {
    var utr by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Submit UPI Transaction Ref", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Paid ${DateAndReportUtils.formatInr(member.monthlyAmount)} for ${DateAndReportUtils.formatBillingMonth(billingMonth)} via QR or another phone? Enter your 12-digit UPI UTR reference for admin verification.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = utr,
                    onValueChange = { utr = it; error = null },
                    label = { Text("UPI Reference / UTR Number *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("member_utr_input")
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Optional Note") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Text(error ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (utr.trim().length < 4) {
                        error = "Please enter a valid UPI transaction reference."
                    } else {
                        onSubmit(utr.trim(), notes.trim())
                    }
                },
                modifier = Modifier.testTag("confirm_utr_button")
            ) {
                Text("Submit for Verification")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
