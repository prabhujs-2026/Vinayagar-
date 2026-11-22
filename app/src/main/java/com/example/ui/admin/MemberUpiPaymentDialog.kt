package com.example.ui.admin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.CommitteeSettings
import com.example.data.Member
import com.example.data.PaymentMethod
import com.example.data.PaymentStatus
import com.example.ui.components.StatusPill
import com.example.ui.components.UpiQrCard
import com.example.ui.theme.LocalExtendedColors
import com.example.util.DateAndReportUtils

@Composable
fun MemberUpiPaymentDialog(
    member: Member,
    billingMonth: String,
    settings: CommitteeSettings,
    isAlreadyPaid: Boolean,
    onRecordUpiPayment: (Member, String, Long, PaymentMethod, String, PaymentStatus, String) -> Unit,
    onSendWhatsAppUpi: (Member) -> Unit,
    onShowMessage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val extended = LocalExtendedColors.current

    var utrInput by remember(member.id, billingMonth) { mutableStateOf("") }
    var lastLaunchedTxnRef by remember(member.id, billingMonth) { mutableStateOf("") }

    val txnRef = remember(member.id, billingMonth) {
        "VF${billingMonth.replace("-", "")}${member.id.take(6).uppercase()}"
    }
    val upiNote = remember(settings.festivalName, billingMonth, member.fullName) {
        "${settings.festivalName.take(30)} ${DateAndReportUtils.formatShortMonth(billingMonth)} - ${member.fullName.take(24)}"
    }
    val upiUri = remember(settings.upiId, settings.upiPayeeName, member.monthlyAmount, upiNote, txnRef) {
        DateAndReportUtils.buildUpiPayUri(
            upiId = settings.upiId,
            payeeName = settings.upiPayeeName,
            amountInr = member.monthlyAmount,
            note = upiNote,
            txnRef = txnRef
        )
    }

    val billingDateIso = remember(billingMonth) {
        DateAndReportUtils.defaultBillingDateForMonth(billingMonth)
    }

    val upiLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val rawResponse = result.data?.getStringExtra("response")
        val parsed = DateAndReportUtils.parseUpiResponse(rawResponse)
        when {
            parsed.succeeded -> {
                onRecordUpiPayment(
                    member,
                    billingMonth,
                    member.monthlyAmount,
                    PaymentMethod.UPI,
                    parsed.bestReference.ifBlank { lastLaunchedTxnRef },
                    PaymentStatus.PAID,
                    DateAndReportUtils.attachBillingDateToNotes(billingDateIso, "Paid via UPI app")
                )
                onDismiss()
            }
            parsed.submitted -> {
                onRecordUpiPayment(
                    member,
                    billingMonth,
                    member.monthlyAmount,
                    PaymentMethod.UPI,
                    parsed.bestReference.ifBlank { lastLaunchedTxnRef },
                    PaymentStatus.PENDING_VERIFICATION,
                    DateAndReportUtils.attachBillingDateToNotes(billingDateIso, "UPI payment submitted — awaiting confirmation")
                )
                onDismiss()
            }
            else -> {
                onShowMessage("UPI app closed without confirmation. If paid, tap 'Mark UPI Paid' below.")
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = member.fullName,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${DateAndReportUtils.formatIsoDate(billingDateIso)} • ${DateAndReportUtils.formatInr(member.monthlyAmount)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${DateAndReportUtils.formatBillingMonth(billingMonth)} • Due ${DateAndReportUtils.formatDueDate(billingMonth, settings.dueDayOfMonth)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                StatusPill(
                    label = if (isAlreadyPaid) "Paid" else "UPI Pay",
                    containerColor = if (isAlreadyPaid) extended.successContainer else MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = if (isAlreadyPaid) extended.onSuccessContainer else MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isAlreadyPaid) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = extended.successContainer,
                        border = BorderStroke(1.dp, extended.success.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Verified,
                                contentDescription = null,
                                tint = extended.success,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Contribution for ${DateAndReportUtils.formatBillingMonth(billingMonth)} is already marked Paid. You can still scan or launch UPI for an additional contribution.",
                                style = MaterialTheme.typography.bodySmall,
                                color = extended.onSuccessContainer
                            )
                        }
                    }
                }

                // 1. Direct UPI App Launch Button
                Button(
                    onClick = {
                        lastLaunchedTxnRef = txnRef
                        val upiIntent = Intent(Intent.ACTION_VIEW, upiUri)
                        val chooser = Intent.createChooser(upiIntent, "Pay ${member.fullName} Contribution via UPI")
                        if (upiIntent.resolveActivity(context.packageManager) != null) {
                            upiLauncher.launch(chooser)
                        } else {
                            onShowMessage("No UPI app found on this device. Scan the QR code or copy the UPI ID.")
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("dialog_launch_upi_app")
                ) {
                    Icon(Icons.Rounded.AccountBalanceWallet, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${stringResource(R.string.action_pay_upi)} (${DateAndReportUtils.formatInr(member.monthlyAmount)})",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                // 2. Send UPI Details on WhatsApp
                OutlinedButton(
                    onClick = { onSendWhatsAppUpi(member) },
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, extended.whatsapp.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = extended.whatsapp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("dialog_whatsapp_upi")
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.action_share_upi_whatsapp),
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // 3. Live Scannable UPI QR Code for In-Person Collection
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

                // 4. Instant Mark UPI Paid after QR Scan
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    text = "Confirm In-Person UPI Payment",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                OutlinedTextField(
                    value = utrInput,
                    onValueChange = { utrInput = it },
                    label = { Text("UPI Ref / UTR Number (Optional)") },
                    placeholder = { Text("e.g. 432512345678") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_upi_utr_input")
                )
                Button(
                    onClick = {
                        onRecordUpiPayment(
                            member,
                            billingMonth,
                            member.monthlyAmount,
                            PaymentMethod.UPI,
                            utrInput.trim(),
                            PaymentStatus.PAID,
                            DateAndReportUtils.attachBillingDateToNotes(billingDateIso, "Paid via UPI QR / direct transfer")
                        )
                        onDismiss()
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = extended.success,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("dialog_mark_upi_paid")
                ) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${stringResource(R.string.action_mark_upi_paid)} (${DateAndReportUtils.formatInr(member.monthlyAmount)})",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("dialog_close_upi")
            ) {
                Text("Close")
            }
        }
    )
}
