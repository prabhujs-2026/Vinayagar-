package com.example.ui.admin

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.Member
import com.example.data.MembershipStatus
import com.example.data.PaymentStatus
import com.example.ui.AdminSnapshot
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.MonthSelectorBar
import com.example.ui.components.StatusPill
import com.example.ui.theme.LocalExtendedColors
import com.example.util.DateAndReportUtils

@Composable
fun AdminRemindersScreen(
    snapshot: AdminSnapshot,
    selectedMonth: String,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSendWhatsAppReminder: (Member) -> Unit,
    onOpenUpiOptions: (Member) -> Unit,
    onShareGroupNotice: (List<Member>) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val extended = LocalExtendedColors.current

    var notifGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    val notifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        notifGranted = granted
    }

    val paidMemberIds = remember(selectedMonth, snapshot.payments) {
        snapshot.payments
            .filter { it.billingMonth == selectedMonth && it.statusEnum() == PaymentStatus.PAID }
            .map { it.memberId }
            .toSet()
    }

    val pendingMembers = remember(snapshot.members, paidMemberIds) {
        snapshot.members.filter {
            it.statusEnum() == MembershipStatus.ACTIVE && it.id !in paidMemberIds
        }
    }

    val unremindedMembers = remember(pendingMembers, selectedMonth) {
        pendingMembers.filter { it.lastRemindedMonth != selectedMonth }
    }

    val monthLogs = remember(snapshot.reminderLogs, selectedMonth) {
        snapshot.reminderLogs.filter { it.billingMonth == selectedMonth }
    }

    val dueDateFormatted = remember(selectedMonth, snapshot.settings.dueDayOfMonth) {
        DateAndReportUtils.formatDueDate(selectedMonth, snapshot.settings.dueDayOfMonth)
    }
    val daysRemaining = remember(selectedMonth, snapshot.settings.dueDayOfMonth) {
        DateAndReportUtils.daysUntilDue(selectedMonth, snapshot.settings.dueDayOfMonth)
    }
    val inWindow = daysRemaining <= snapshot.settings.reminderDaysBefore.toInt()
    val deadlineLabel = remember(selectedMonth, snapshot.settings.dueDayOfMonth) {
        DateAndReportUtils.deadlineStatusLabel(selectedMonth, snapshot.settings.dueDayOfMonth)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            MonthSelectorBar(
                billingMonth = selectedMonth,
                dueDayOfMonth = snapshot.settings.dueDayOfMonth,
                onPreviousMonth = onPrevMonth,
                onNextMonth = onNextMonth
            )
        }

        // 5-Day Due Date Automation Status Card
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = if (inWindow) extended.warningContainer else MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(
                    1.dp,
                    if (inWindow) extended.warning.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(extended.whatsapp.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Alarm,
                                contentDescription = null,
                                tint = extended.whatsapp
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Automated ${snapshot.settings.reminderDaysBefore}-Day Reminder Schedule",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Deadline: $dueDateFormatted • $deadlineLabel",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = if (snapshot.settings.autoReminderEnabled) {
                            "Daily automated check is active. Gentle reminders trigger ${snapshot.settings.reminderDaysBefore} days before the ${snapshot.settings.dueDayOfMonth}th of every month."
                        } else {
                            "Automated alerts are currently paused in Committee Settings."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!notifGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            Button(
                                onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("enable_notifications_button")
                            ) {
                                Icon(Icons.Rounded.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Enable Alerts")
                            }
                        }
                        OutlinedButton(
                            onClick = onOpenSettings,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Edit Template & Due Day")
                        }
                    }
                }
            }
        }

        // Batch WhatsApp Actions Card
        if (pendingMembers.isNotEmpty()) {
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = extended.whatsappContainer),
                    border = BorderStroke(1.dp, extended.whatsapp.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "WhatsApp Gentle Reminder Queue",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${unremindedMembers.size} pending members not yet reminded for ${DateAndReportUtils.formatBillingMonth(selectedMonth)} (${pendingMembers.size} pending total).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val nextTarget = unremindedMembers.firstOrNull() ?: pendingMembers.first()
                            Button(
                                onClick = { onSendWhatsAppReminder(nextTarget) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = extended.whatsapp,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("send_next_whatsapp_button")
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (unremindedMembers.isNotEmpty()) "Remind Next" else "Re-Remind",
                                    maxLines = 1
                                )
                            }
                            OutlinedButton(
                                onClick = { onShareGroupNotice(pendingMembers) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("share_group_reminder_button")
                            ) {
                                Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Group Notice", maxLines = 1)
                            }
                        }
                    }
                }
            }
        }

        // Pending Members List
        item {
            Text(
                text = "Pending Members (${pendingMembers.size})",
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (pendingMembers.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Rounded.CheckCircle,
                    title = "All active members are paid!",
                    subtitle = "No pending reminders needed for ${DateAndReportUtils.formatBillingMonth(selectedMonth)}."
                )
            }
        } else {
            items(pendingMembers, key = { "rem_pending_${it.id}" }) { member ->
                val alreadyReminded = member.lastRemindedMonth == selectedMonth
                Card(
                    shape = MaterialTheme.shapes.medium,
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
                                    text = member.fullName,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (alreadyReminded) {
                                    StatusPill(
                                        label = "Sent",
                                        containerColor = extended.successContainer,
                                        contentColor = extended.onSuccessContainer
                                    )
                                }
                            }
                            Text(
                                text = "${DateAndReportUtils.formatInr(member.monthlyAmount)} • ${member.phone}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onOpenUpiOptions(member) },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.secondary
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("queue_upi_${member.id}")
                            ) {
                                Icon(Icons.Rounded.QrCode2, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.action_upi_pay),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                            Button(
                                onClick = { onSendWhatsAppReminder(member) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (alreadyReminded) MaterialTheme.colorScheme.surfaceVariant else extended.whatsapp,
                                    contentColor = if (alreadyReminded) MaterialTheme.colorScheme.onSurfaceVariant else Color.White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("queue_whatsapp_${member.id}")
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (alreadyReminded) "Resend" else "Remind",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
            }
        }

        // Sent Reminder Audit Logs
        if (monthLogs.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Sent Reminder Log (${monthLogs.size})",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            items(monthLogs.take(15), key = { it.id }) { log ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(log.memberName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = DateAndReportUtils.formatTimestamp(log.createdAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = log.messagePreview,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(72.dp)) }
    }
}
