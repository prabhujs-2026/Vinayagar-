package com.example.ui.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AddCard
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CurrencyRupee
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PendingActions
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.JoinRequest
import com.example.data.JoinRequestStatus
import com.example.data.Payment
import com.example.data.PaymentStatus
import com.example.ui.AdminSnapshot
import com.example.ui.components.CollectionProgressRing
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.MetricStatCard
import com.example.ui.components.MonthSelectorBar
import com.example.ui.components.StatusPill
import com.example.ui.theme.LocalExtendedColors
import com.example.util.DateAndReportUtils

@Composable
fun AdminHomeScreen(
    snapshot: AdminSnapshot,
    selectedMonth: String,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onAddMemberClick: () -> Unit,
    onRecordPaymentClick: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenReports: () -> Unit,
    onEditPayment: (Payment) -> Unit,
    onApprovePayment: (Payment) -> Unit,
    onApproveJoinRequest: (JoinRequest) -> Unit,
    onRejectJoinRequest: (JoinRequest) -> Unit,
    modifier: Modifier = Modifier
) {
    val extended = LocalExtendedColors.current
    val report = remember(selectedMonth, snapshot.members, snapshot.payments) {
        DateAndReportUtils.computeMonthlyReport(selectedMonth, snapshot.members, snapshot.payments)
    }
    val inReminderWindow = remember(selectedMonth, snapshot.settings) {
        DateAndReportUtils.isInReminderWindow(
            selectedMonth,
            snapshot.settings.dueDayOfMonth,
            snapshot.settings.reminderDaysBefore
        )
    }
    val deadlineLabel = remember(selectedMonth, snapshot.settings.dueDayOfMonth) {
        DateAndReportUtils.deadlineStatusLabel(selectedMonth, snapshot.settings.dueDayOfMonth)
    }
    val pendingVerifications = remember(snapshot.payments) {
        snapshot.payments.filter { it.statusEnum() == PaymentStatus.PENDING_VERIFICATION }
    }
    val pendingJoinRequests = remember(snapshot.joinRequests) {
        snapshot.joinRequests.filter { it.statusEnum() == JoinRequestStatus.PENDING }
    }
    val monthTransactions = remember(selectedMonth, snapshot.payments) {
        snapshot.payments.filter { it.billingMonth == selectedMonth }.take(6)
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

        // Festive Hero Collection Card
        item {
            Card(
                shape = MaterialTheme.shapes.extraLarge,
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(194.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Image(
                        painter = painterResource(id = R.drawable.img_hero_banner),
                        contentDescription = stringResource(R.string.cd_hero_banner),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xE864110D),
                                        Color(0xD48C1D18),
                                        Color(0x8A5E140B)
                                    )
                                )
                            )
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Surface(
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.18f)
                            ) {
                                Text(
                                    text = snapshot.settings.festivalName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFFFEBC2),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = DateAndReportUtils.formatInr(report.collectedAmount),
                                style = MaterialTheme.typography.displaySmall,
                                color = Color.White
                            )
                            Text(
                                text = "collected of ${DateAndReportUtils.formatInr(report.expectedAmount)} target",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.88f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatusPill(
                                    label = "${report.paidMemberCount}/${report.activeMemberCount} Paid",
                                    containerColor = Color.White.copy(alpha = 0.2f),
                                    contentColor = Color.White
                                )
                                StatusPill(
                                    label = deadlineLabel,
                                    containerColor = Color(0xFFF4C44E),
                                    contentColor = Color(0xFF321E00)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        CollectionProgressRing(progress = report.collectionRate)
                    }
                }
            }
        }

        // 5-Day Due Reminder Alert Banner
        if (inReminderWindow && report.unpaidMemberCount > 0) {
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = extended.warningContainer),
                    border = BorderStroke(1.dp, extended.warning.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(extended.warning.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.NotificationsActive,
                                contentDescription = null,
                                tint = extended.warning
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${snapshot.settings.reminderDaysBefore}-Day Reminder Window Active",
                                style = MaterialTheme.typography.titleSmall,
                                color = extended.onWarningContainer
                            )
                            Text(
                                text = "${report.unpaidMemberCount} active members have pending dues (${DateAndReportUtils.formatInr(report.pendingAmount)}). Send gentle WhatsApp reminders now.",
                                style = MaterialTheme.typography.bodySmall,
                                color = extended.onWarningContainer.copy(alpha = 0.85f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = onOpenReminders,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = extended.whatsapp,
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("banner_send_reminders_button")
                            ) {
                                Text("Open WhatsApp Queue", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }

        // Key Metrics Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricStatCard(
                    label = "Pending Dues",
                    value = DateAndReportUtils.formatInr(report.pendingAmount),
                    subtitle = "${report.unpaidMemberCount} members remaining",
                    accentColor = extended.warning,
                    icon = Icons.Rounded.PendingActions,
                    modifier = Modifier.weight(1f)
                )
                MetricStatCard(
                    label = "UPI Collected",
                    value = DateAndReportUtils.formatInr(report.upiTotal),
                    subtitle = "Cash ${DateAndReportUtils.formatInr(report.cashTotal)}",
                    accentColor = extended.success,
                    icon = Icons.Rounded.AccountBalanceWallet,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Quick Actions
        item {
            Text(
                text = "Quick Admin Actions",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickActionTile(
                    icon = Icons.Rounded.PersonAdd,
                    label = "Add Member",
                    accent = MaterialTheme.colorScheme.primary,
                    onClick = onAddMemberClick,
                    tag = "quick_add_member",
                    modifier = Modifier.weight(1f)
                )
                QuickActionTile(
                    icon = Icons.Rounded.AddCard,
                    label = "Record Pay",
                    accent = MaterialTheme.colorScheme.secondary,
                    onClick = onRecordPaymentClick,
                    tag = "quick_record_payment",
                    modifier = Modifier.weight(1f)
                )
                QuickActionTile(
                    icon = Icons.Rounded.NotificationsActive,
                    label = "Reminders",
                    accent = extended.whatsapp,
                    onClick = onOpenReminders,
                    tag = "quick_reminders",
                    modifier = Modifier.weight(1f)
                )
                QuickActionTile(
                    icon = Icons.Rounded.Assessment,
                    label = "Reports",
                    accent = extended.goldAccent,
                    onClick = onOpenReports,
                    tag = "quick_reports",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Pending Member UPI Verification Approvals
        if (pendingVerifications.isNotEmpty()) {
            item {
                Text(
                    text = "Pending UPI Verifications (${pendingVerifications.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            items(pendingVerifications, key = { "verify_${it.id}" }) { payment ->
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)),
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
                            Text(
                                text = payment.memberName,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "${DateAndReportUtils.formatInr(payment.amount)} • ${DateAndReportUtils.formatPaymentDate(payment)} (${DateAndReportUtils.formatShortMonth(payment.billingMonth)})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (payment.upiRef.isNotBlank()) {
                                Text(
                                    text = "UTR: ${payment.upiRef}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = { onEditPayment(payment) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("Review", style = MaterialTheme.typography.labelMedium)
                            }
                            Button(
                                onClick = { onApprovePayment(payment) },
                                colors = ButtonDefaults.buttonColors(containerColor = extended.success),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("approve_payment_${payment.id}")
                            ) {
                                Icon(Icons.Rounded.Verified, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Approve", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }

        // Pending Join Requests
        if (pendingJoinRequests.isNotEmpty()) {
            item {
                Text(
                    text = "New Member Requests (${pendingJoinRequests.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            items(pendingJoinRequests, key = { "join_${it.id}" }) { req ->
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(req.fullName, style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "${req.phone} • ${req.emailLower}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (req.address.isNotBlank()) {
                            Text(
                                text = req.address,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onApproveJoinRequest(req) },
                                colors = ButtonDefaults.buttonColors(containerColor = extended.success)
                            ) {
                                Text("Approve Member")
                            }
                            OutlinedButton(onClick = { onRejectJoinRequest(req) }) {
                                Text("Decline")
                            }
                        }
                    }
                }
            }
        }

        // Recent Transactions for Selected Month
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Contributions",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${monthTransactions.size} shown",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (monthTransactions.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Rounded.CurrencyRupee,
                    title = "No contributions recorded for ${report.formattedMonth}",
                    subtitle = if (snapshot.members.isEmpty()) {
                        "Add your Vinayagar festival members first to begin tracking monthly dues and UPI payments."
                    } else {
                        "Record a payment or send gentle WhatsApp reminders to members."
                    },
                    actionLabel = if (snapshot.members.isEmpty()) "Add First Member" else "Record Payment",
                    onAction = if (snapshot.members.isEmpty()) onAddMemberClick else onRecordPaymentClick
                )
            }
        } else {
            items(monthTransactions, key = { it.id }) { payment ->
                TransactionRowCard(
                    payment = payment,
                    onClick = { onEditPayment(payment) }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(72.dp)) }
    }
}

@Composable
private fun QuickActionTile(
    icon: ImageVector,
    label: String,
    accent: Color,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .testTag(tag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun TransactionRowCard(
    payment: Payment,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val extended = LocalExtendedColors.current
    val (pillBg, pillFg) = when (payment.statusEnum()) {
        PaymentStatus.PAID -> extended.successContainer to extended.onSuccessContainer
        PaymentStatus.PENDING_VERIFICATION -> extended.warningContainer to extended.onWarningContainer
        PaymentStatus.REJECTED -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(pillBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (payment.statusEnum() == PaymentStatus.PAID) {
                            Icons.Rounded.CheckCircle
                        } else {
                            Icons.Rounded.Groups
                        },
                        contentDescription = null,
                        tint = pillFg,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = payment.memberName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildString {
                            append(payment.methodEnum().label)
                            if (payment.upiRef.isNotBlank()) append(" • ${payment.upiRef}")
                            append(" • ${DateAndReportUtils.formatPaymentDate(payment)}")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = DateAndReportUtils.formatInr(payment.amount),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                StatusPill(
                    label = payment.statusEnum().label,
                    containerColor = pillBg,
                    contentColor = pillFg
                )
            }
        }
    }
}
