package com.example.ui.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.Payment
import com.example.data.PaymentStatus
import com.example.ui.AdminSnapshot
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.MetricStatCard
import com.example.ui.components.MonthSelectorBar
import com.example.ui.theme.LocalExtendedColors
import com.example.util.DateAndReportUtils
import com.example.util.MonthTrendPoint

@Composable
fun AdminReportsScreen(
    snapshot: AdminSnapshot,
    selectedMonth: String,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onShareReport: () -> Unit,
    onExportCsv: () -> Unit,
    onEditPayment: (Payment) -> Unit,
    onRecordPayment: () -> Unit,
    modifier: Modifier = Modifier
) {
    val extended = LocalExtendedColors.current
    val report = remember(selectedMonth, snapshot.members, snapshot.payments) {
        DateAndReportUtils.computeMonthlyReport(selectedMonth, snapshot.members, snapshot.payments)
    }
    val sixMonthTrend = remember(selectedMonth, snapshot.members, snapshot.payments) {
        DateAndReportUtils.computeSixMonthTrend(selectedMonth, snapshot.members, snapshot.payments)
    }
    val allTimeCollected = remember(snapshot.payments) {
        snapshot.payments
            .filter { it.statusEnum() == PaymentStatus.PAID }
            .sumOf { it.amount }
    }
    val monthPayments = remember(selectedMonth, snapshot.payments) {
        snapshot.payments.filter { it.billingMonth == selectedMonth }
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

        // Export & Share Automated Report Actions
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onShareReport,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("share_report_button")
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.action_share_report))
                }
                OutlinedButton(
                    onClick = onExportCsv,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("export_csv_button")
                ) {
                    Icon(Icons.Rounded.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.action_export_csv))
                }
            }
        }

        // Automated Report Summary Metrics
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricStatCard(
                    label = "This Month",
                    value = DateAndReportUtils.formatInr(report.collectedAmount),
                    subtitle = "${(report.collectionRate * 100).toInt()}% of ${DateAndReportUtils.formatInr(report.expectedAmount)}",
                    accentColor = extended.success,
                    icon = Icons.Rounded.TrendingUp,
                    modifier = Modifier.weight(1f)
                )
                MetricStatCard(
                    label = "Total Fund Pool",
                    value = DateAndReportUtils.formatInr(allTimeCollected),
                    subtitle = "All verified receipts",
                    accentColor = MaterialTheme.colorScheme.primary,
                    icon = Icons.Rounded.AccountBalance,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Payment Mode Breakdown Card
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Collection Breakdown by Mode",
                        style = MaterialTheme.typography.titleMedium
                    )
                    val total = report.collectedAmount.coerceAtLeast(1L).toFloat()
                    ModeBreakdownRow(
                        label = "UPI Payments",
                        amount = report.upiTotal,
                        fraction = report.upiTotal / total,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    ModeBreakdownRow(
                        label = "Cash Contributions",
                        amount = report.cashTotal,
                        fraction = report.cashTotal / total,
                        color = extended.success
                    )
                    ModeBreakdownRow(
                        label = "Bank Transfers",
                        amount = report.bankTotal,
                        fraction = report.bankTotal / total,
                        color = extended.goldAccent
                    )
                }
            }
        }

        // 6-Month Collection Trend Chart
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "6-Month Collection Trend",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Collected vs monthly target across recent billing cycles",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    SixMonthBarChart(
                        points = sixMonthTrend,
                        barColor = MaterialTheme.colorScheme.primary,
                        targetTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }

        // Complete Monthly Transaction Ledger (Admin Edit / Delete)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Transaction Ledger (${monthPayments.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Tap any transaction to edit status, UTR, or delete",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (monthPayments.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Rounded.Payments,
                    title = "No transactions for ${report.formattedMonth}",
                    subtitle = "Recorded UPI, cash, and bank payments will appear here in real time.",
                    actionLabel = "Record Payment",
                    onAction = onRecordPayment
                )
            }
        } else {
            items(monthPayments, key = { "ledger_${it.id}" }) { payment ->
                TransactionRowCard(
                    payment = payment,
                    onClick = { onEditPayment(payment) }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
private fun ModeBreakdownRow(
    label: String,
    amount: Long,
    fraction: Float,
    color: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Text(label, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                text = DateAndReportUtils.formatInr(amount),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }
        LinearProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            color = color,
            trackColor = color.copy(alpha = 0.14f),
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
        )
    }
}

@Composable
private fun SixMonthBarChart(
    points: List<MonthTrendPoint>,
    barColor: Color,
    targetTrackColor: Color
) {
    val maxVal = points.maxOfOrNull { maxOf(it.collected, it.expected) }?.coerceAtLeast(1L) ?: 1L

    Column(modifier = Modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp)
        ) {
            val count = points.size.coerceAtLeast(1)
            val slotWidth = size.width / count
            val barWidth = (slotWidth * 0.46f).coerceAtMost(36.dp.toPx())
            val corner = CornerRadius(8.dp.toPx(), 8.dp.toPx())

            points.forEachIndexed { idx, pt ->
                val centerX = slotWidth * idx + slotWidth / 2f
                val left = centerX - barWidth / 2f

                // Target background track
                val targetHeight = (pt.expected.toFloat() / maxVal.toFloat()) * size.height
                drawRoundRect(
                    color = targetTrackColor,
                    topLeft = Offset(left, size.height - targetHeight),
                    size = Size(barWidth, targetHeight.coerceAtLeast(6.dp.toPx())),
                    cornerRadius = corner
                )

                // Collected foreground bar
                val collectedHeight = (pt.collected.toFloat() / maxVal.toFloat()) * size.height
                if (pt.collected > 0L) {
                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(left, size.height - collectedHeight),
                        size = Size(barWidth, collectedHeight.coerceAtLeast(6.dp.toPx())),
                        cornerRadius = corner
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            points.forEach { pt ->
                Text(
                    text = pt.shortLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
