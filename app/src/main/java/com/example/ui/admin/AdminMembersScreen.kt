package com.example.ui.admin

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.AddCard
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

private enum class MemberFilter(val label: String) {
    ALL("All"),
    PENDING("Pending Dues"),
    PAID("Paid"),
    ACTIVE("Active"),
    PAUSED("Paused"),
    INACTIVE("Inactive")
}

@Composable
fun AdminMembersScreen(
    snapshot: AdminSnapshot,
    selectedMonth: String,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onAddMember: () -> Unit,
    onEditMember: (Member) -> Unit,
    onRecordPaymentForMember: (Member) -> Unit,
    onSendWhatsAppReminder: (Member) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf(MemberFilter.ALL) }
    val extended = LocalExtendedColors.current

    val paidMemberIds = remember(selectedMonth, snapshot.payments) {
        snapshot.payments
            .filter { it.billingMonth == selectedMonth && it.statusEnum() == PaymentStatus.PAID }
            .map { it.memberId }
            .toSet()
    }
    val pendingReviewIds = remember(selectedMonth, snapshot.payments) {
        snapshot.payments
            .filter { it.billingMonth == selectedMonth && it.statusEnum() == PaymentStatus.PENDING_VERIFICATION }
            .map { it.memberId }
            .toSet()
    }

    val filteredMembers = remember(snapshot.members, searchQuery, activeFilter, paidMemberIds) {
        snapshot.members.filter { member ->
            val matchesQuery = searchQuery.isBlank() ||
                member.fullName.contains(searchQuery, ignoreCase = true) ||
                member.phone.contains(searchQuery, ignoreCase = true) ||
                member.email.contains(searchQuery, ignoreCase = true) ||
                member.address.contains(searchQuery, ignoreCase = true)

            val matchesFilter = when (activeFilter) {
                MemberFilter.ALL -> true
                MemberFilter.PENDING -> member.statusEnum() == MembershipStatus.ACTIVE && member.id !in paidMemberIds
                MemberFilter.PAID -> member.id in paidMemberIds
                MemberFilter.ACTIVE -> member.statusEnum() == MembershipStatus.ACTIVE
                MemberFilter.PAUSED -> member.statusEnum() == MembershipStatus.PAUSED
                MemberFilter.INACTIVE -> member.statusEnum() == MembershipStatus.INACTIVE
            }
            matchesQuery && matchesFilter
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            MonthSelectorBar(
                billingMonth = selectedMonth,
                dueDayOfMonth = snapshot.settings.dueDayOfMonth,
                onPreviousMonth = onPrevMonth,
                onNextMonth = onNextMonth
            )
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search member name, phone, email, street…") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("member_search_input")
            )
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(MemberFilter.entries) { filter ->
                    FilterChip(
                        selected = activeFilter == filter,
                        onClick = { activeFilter = filter },
                        label = { Text(filter.label) }
                    )
                }
            }
        }

        if (filteredMembers.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Rounded.Groups,
                    title = if (snapshot.members.isEmpty()) "No committee members yet" else "No matching members",
                    subtitle = if (snapshot.members.isEmpty()) {
                        "Add Vinayagar festival members with their WhatsApp number and Google email so they can also pay dues directly from the app."
                    } else {
                        "Try adjusting your search query or status filter."
                    },
                    actionLabel = if (snapshot.members.isEmpty()) "Add Member" else null,
                    onAction = if (snapshot.members.isEmpty()) onAddMember else null
                )
            }
        } else {
            items(filteredMembers, key = { it.id }) { member ->
                val isPaid = member.id in paidMemberIds
                val isPendingReview = member.id in pendingReviewIds
                val statusEnum = member.statusEnum()

                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .clickable { onEditMember(member) }
                        .testTag("member_card_${member.id}")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
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
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = member.fullName.take(2).uppercase(),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = member.fullName,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = member.phone + if (member.address.isNotBlank()) " • ${member.address}" else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = DateAndReportUtils.formatInr(member.monthlyAmount),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val (payLabel, payBg, payFg) = when {
                                    isPaid -> Triple("Paid", extended.successContainer, extended.onSuccessContainer)
                                    isPendingReview -> Triple("Review UPI", extended.warningContainer, extended.onWarningContainer)
                                    statusEnum != MembershipStatus.ACTIVE -> Triple(
                                        statusEnum.label,
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    else -> Triple("Pending", extended.warningContainer, extended.onWarningContainer)
                                }
                                StatusPill(label = payLabel, containerColor = payBg, contentColor = payFg)

                                if (statusEnum != MembershipStatus.ACTIVE) {
                                    StatusPill(
                                        label = "Status: ${statusEnum.label}",
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (member.lastRemindedMonth == selectedMonth && !isPaid) {
                                    StatusPill(
                                        label = "Reminded",
                                        containerColor = extended.whatsappContainer,
                                        contentColor = extended.whatsapp
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!isPaid && statusEnum == MembershipStatus.ACTIVE) {
                                    IconButton(
                                        onClick = { onSendWhatsAppReminder(member) },
                                        modifier = Modifier.testTag("whatsapp_member_${member.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.Chat,
                                            contentDescription = stringResource(R.string.cd_whatsapp_remind),
                                            tint = extended.whatsapp
                                        )
                                    }
                                    IconButton(
                                        onClick = { onRecordPaymentForMember(member) },
                                        modifier = Modifier.testTag("pay_member_${member.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.AddCard,
                                            contentDescription = stringResource(R.string.cd_record_payment),
                                            tint = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { onEditMember(member) },
                                    modifier = Modifier.testTag("edit_member_${member.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Edit,
                                        contentDescription = stringResource(R.string.cd_edit),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}
