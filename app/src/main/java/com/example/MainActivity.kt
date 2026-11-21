package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.authStateFlow
import com.example.auth.signOutUser
import com.example.data.CommitteeRepository
import com.example.data.Member
import com.example.data.MembershipStatus
import com.example.data.Payment
import com.example.data.PaymentStatus
import com.example.data.UiState
import com.example.reminders.DueReminderScheduler
import com.example.ui.AdminSnapshot
import com.example.ui.AuthScreen
import com.example.ui.RoleResolution
import com.example.ui.VinayagarViewModel
import com.example.ui.admin.AdminHomeScreen
import com.example.ui.admin.AdminLockScreen
import com.example.ui.admin.AdminMembersScreen
import com.example.ui.admin.AdminRemindersScreen
import com.example.ui.admin.AdminReportsScreen
import com.example.ui.admin.CommitteeSettingsDialog
import com.example.ui.admin.MemberEditorDialog
import com.example.ui.admin.PaymentEditorDialog
import com.example.ui.components.DestructiveConfirmDialog
import com.example.ui.member.MemberDuesScreen
import com.example.ui.member.MemberReceiptsScreen
import com.example.ui.theme.VinayagarFundTheme
import com.example.util.DateAndReportUtils
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val initialTabExtra = intent?.getStringExtra("open_tab")
        setContent {
            VinayagarFundTheme {
                AppContent(initialTabExtra = initialTabExtra)
            }
        }
    }
}

private enum class AdminTab(
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME(R.string.nav_home, Icons.Rounded.Dashboard, Icons.Outlined.Dashboard),
    MEMBERS(R.string.nav_members, Icons.Rounded.Groups, Icons.Outlined.Groups),
    REMINDERS(R.string.nav_reminders, Icons.Rounded.NotificationsActive, Icons.Outlined.NotificationsActive),
    REPORTS(R.string.nav_reports, Icons.Rounded.Assessment, Icons.Outlined.Assessment)
}

private enum class MemberTab(
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    DUES(R.string.nav_my_dues, Icons.Rounded.Payments, Icons.Outlined.Payments),
    RECEIPTS(R.string.nav_receipts, Icons.Rounded.ReceiptLong, Icons.Outlined.ReceiptLong)
}

@Composable
fun AppContent(initialTabExtra: String?) {
    val auth = Firebase.auth
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            VinayagarAppShell(
                currentUserId = user.uid,
                currentUserEmail = user.email ?: "",
                currentUserName = user.displayName ?: "Member",
                emailVerified = user.isEmailVerified,
                initialTabExtra = initialTabExtra
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VinayagarAppShell(
    currentUserId: String,
    currentUserEmail: String,
    currentUserName: String,
    emailVerified: Boolean,
    initialTabExtra: String?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    val viewModel: VinayagarViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                VinayagarViewModel(
                    repository = CommitteeRepository(db),
                    currentUserId = currentUserId,
                    currentUserEmail = currentUserEmail,
                    currentUserName = currentUserName,
                    emailVerified = emailVerified
                )
            }
        }
    )

    val roleResolution by viewModel.roleResolution.collectAsStateWithLifecycle()
    val selectedMonth by viewModel.selectedMonth.collectAsStateWithLifecycle()
    val previewMemberMode by viewModel.previewMemberMode.collectAsStateWithLifecycle()
    val adminConsoleUnlocked by viewModel.adminConsoleUnlocked.collectAsStateWithLifecycle()
    val adminState by viewModel.adminState.collectAsStateWithLifecycle()
    val memberState by viewModel.memberPortalState.collectAsStateWithLifecycle()

    var adminTab by remember {
        mutableStateOf(
            if (initialTabExtra == "reminders") AdminTab.REMINDERS else AdminTab.HOME
        )
    }
    var memberTab by remember { mutableStateOf(MemberTab.DUES) }

    // Admin Editor Modal States
    var showMemberEditor by remember { mutableStateOf(false) }
    var editingMember by remember { mutableStateOf<Member?>(null) }
    var confirmDeleteMember by remember { mutableStateOf<Member?>(null) }

    var showPaymentEditor by remember { mutableStateOf(false) }
    var editingPayment by remember { mutableStateOf<Payment?>(null) }
    var paymentTargetMember by remember { mutableStateOf<Member?>(null) }
    var confirmDeletePayment by remember { mutableStateOf<Payment?>(null) }

    var showSettingsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.feedback.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val isAdmin = (roleResolution as? RoleResolution.Resolved)?.admin == true
    val showAdminUi = isAdmin && !previewMemberMode
    val adminLockActive = showAdminUi &&
        ((adminState as? UiState.Success)?.data?.adminSecurity?.lockEnabled == true) &&
        !adminConsoleUnlocked
    val adminLockEnabledAndUnlocked = showAdminUi &&
        ((adminState as? UiState.Success)?.data?.adminSecurity?.lockEnabled == true) &&
        adminConsoleUnlocked

    BackHandler(enabled = showAdminUi && adminTab != AdminTab.HOME) {
        adminTab = AdminTab.HOME
    }
    BackHandler(enabled = !showAdminUi && memberTab != MemberTab.DUES) {
        memberTab = MemberTab.DUES
    }
    BackHandler(enabled = isAdmin && previewMemberMode && memberTab == MemberTab.DUES) {
        viewModel.toggleMemberPreview(false)
    }

    // Sync automated 5-day due reminder AlarmManager schedule with live settings
    LaunchedEffect(adminState, memberState, isAdmin, selectedMonth) {
        val currentMonth = DateAndReportUtils.currentBillingMonth()
        if (isAdmin) {
            val snap = (adminState as? UiState.Success)?.data
            if (snap != null) {
                val report = DateAndReportUtils.computeMonthlyReport(currentMonth, snap.members, snap.payments)
                DueReminderScheduler.syncSchedule(
                    context = context,
                    settings = snap.settings,
                    isAdmin = true,
                    pendingCount = report.unpaidMemberCount
                )
            }
        } else {
            val snap = (memberState as? UiState.Success)?.data
            if (snap != null) {
                DueReminderScheduler.syncSchedule(
                    context = context,
                    settings = snap.settings,
                    isAdmin = false,
                    pendingCount = 0
                )
            }
        }
    }

    if (roleResolution is RoleResolution.Loading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(12.dp))
                Text("Loading Vinayagar Fund ledger…", style = MaterialTheme.typography.bodyMedium)
            }
        }
        return
    }

    val currentTitle = when {
        showAdminUi -> when (adminTab) {
            AdminTab.HOME -> stringResource(R.string.app_name)
            AdminTab.MEMBERS -> "Committee Members"
            AdminTab.REMINDERS -> "Due Reminders"
            AdminTab.REPORTS -> "Collection Reports"
        }
        else -> when (memberTab) {
            MemberTab.DUES -> "My Monthly Dues"
            MemberTab.RECEIPTS -> "Payment Receipts"
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 680.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = currentTitle,
                                style = MaterialTheme.typography.headlineSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (showAdminUi) "Admin Real-Time Console" else "Member UPI Portal",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        if (isAdmin) {
                            IconButton(
                                onClick = { viewModel.toggleMemberPreview(!previewMemberMode) },
                                modifier = Modifier.testTag("toggle_view_mode_button")
                            ) {
                                Icon(
                                    imageVector = if (previewMemberMode) {
                                        Icons.Rounded.AdminPanelSettings
                                    } else {
                                        Icons.Rounded.Person
                                    },
                                    contentDescription = stringResource(R.string.cd_switch_view),
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            }
                            if (!previewMemberMode && !adminLockActive) {
                                if (adminLockEnabledAndUnlocked) {
                                    IconButton(
                                        onClick = { viewModel.lockAdminConsole() },
                                        modifier = Modifier.testTag("lock_admin_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Lock,
                                            contentDescription = "Lock Admin Console",
                                            tint = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { showSettingsDialog = true },
                                    modifier = Modifier.testTag("settings_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Settings,
                                        contentDescription = stringResource(R.string.cd_settings),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        IconButton(
                            onClick = {
                                signOutUser(credentialManager, scope)
                            },
                            modifier = Modifier.testTag("sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Logout,
                                contentDescription = stringResource(R.string.cd_sign_out),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            },
            bottomBar = {
                if (!useNavRail && !adminLockActive) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        if (showAdminUi) {
                            AdminTab.entries.forEach { tab ->
                                val selected = adminTab == tab
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { adminTab = tab },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = stringResource(tab.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(tab.labelRes)) },
                                    modifier = Modifier.testTag("nav_admin_${tab.name.lowercase()}")
                                )
                            }
                        } else {
                            MemberTab.entries.forEach { tab ->
                                val selected = memberTab == tab
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { memberTab = tab },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = stringResource(tab.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(tab.labelRes)) },
                                    modifier = Modifier.testTag("nav_member_${tab.name.lowercase()}")
                                )
                            }
                        }
                    }
                }
            },
            floatingActionButton = {
                if (showAdminUi && !adminLockActive && (adminTab == AdminTab.MEMBERS || adminTab == AdminTab.HOME)) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            if (adminTab == AdminTab.MEMBERS) {
                                editingMember = null
                                showMemberEditor = true
                            } else {
                                editingPayment = null
                                paymentTargetMember = null
                                showPaymentEditor = true
                            }
                        },
                        icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                        text = { Text(if (adminTab == AdminTab.MEMBERS) "Add Member" else "Record Payment") },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("primary_fab")
                    )
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail && !adminLockActive) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        if (showAdminUi) {
                            AdminTab.entries.forEach { tab ->
                                val selected = adminTab == tab
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { adminTab = tab },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = stringResource(tab.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(tab.labelRes)) }
                                )
                            }
                        } else {
                            MemberTab.entries.forEach { tab ->
                                val selected = memberTab == tab
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { memberTab = tab },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = stringResource(tab.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(tab.labelRes)) }
                                )
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    val contentModifier = Modifier
                        .widthIn(max = 760.dp)
                        .fillMaxSize()

                    if (showAdminUi) {
                        when (val state = adminState) {
                            UiState.Loading -> LoadingPane()
                            is UiState.Error -> ErrorPane(
                                message = state.message,
                                onRetry = { viewModel.refreshRole() }
                            )
                            is UiState.Success -> {
                                val snap = state.data
                                if (snap.adminSecurity.lockEnabled && !adminConsoleUnlocked) {
                                    AdminLockScreen(
                                        festivalName = snap.settings.festivalName,
                                        canResetViaGoogleAdmin = viewModel.isPrimaryGoogleAdmin,
                                        onUnlock = { u, p -> viewModel.unlockAdminConsole(u, p) },
                                        onSwitchToMemberPortal = { viewModel.toggleMemberPreview(true) },
                                        onResetLock = { viewModel.resetAdminLockViaGoogleAdmin() },
                                        modifier = contentModifier
                                    )
                                } else {
                                val sendWhatsAppForMember: (Member) -> Unit = { target ->
                                    val msg = DateAndReportUtils.buildGentleReminderMessage(
                                        member = target,
                                        billingMonth = selectedMonth,
                                        settings = snap.settings
                                    )
                                    DateAndReportUtils.launchWhatsAppReminder(context, target.phone, msg)
                                    viewModel.logWhatsAppReminder(target, selectedMonth, msg)
                                }

                                when (adminTab) {
                                    AdminTab.HOME -> AdminHomeScreen(
                                        snapshot = snap,
                                        selectedMonth = selectedMonth,
                                        onPrevMonth = viewModel::previousMonth,
                                        onNextMonth = viewModel::nextMonth,
                                        onAddMemberClick = {
                                            editingMember = null
                                            showMemberEditor = true
                                        },
                                        onRecordPaymentClick = {
                                            editingPayment = null
                                            paymentTargetMember = null
                                            showPaymentEditor = true
                                        },
                                        onOpenReminders = { adminTab = AdminTab.REMINDERS },
                                        onOpenReports = { adminTab = AdminTab.REPORTS },
                                        onEditPayment = { p ->
                                            editingPayment = p
                                            paymentTargetMember = null
                                            showPaymentEditor = true
                                        },
                                        onApprovePayment = { p ->
                                            viewModel.updatePayment(
                                                paymentId = p.id,
                                                billingMonth = p.billingMonth,
                                                amount = p.amount,
                                                method = p.methodEnum(),
                                                upiRef = p.upiRef,
                                                status = PaymentStatus.PAID,
                                                notes = p.notes.ifBlank { "Verified by committee admin" }
                                            )
                                        },
                                        onApproveJoinRequest = { req ->
                                            viewModel.approveJoinRequest(req, snap.settings.defaultMonthlyAmount)
                                        },
                                        onRejectJoinRequest = { req ->
                                            viewModel.rejectJoinRequest(req)
                                        },
                                        modifier = contentModifier
                                    )

                                    AdminTab.MEMBERS -> AdminMembersScreen(
                                        snapshot = snap,
                                        selectedMonth = selectedMonth,
                                        onPrevMonth = viewModel::previousMonth,
                                        onNextMonth = viewModel::nextMonth,
                                        onAddMember = {
                                            editingMember = null
                                            showMemberEditor = true
                                        },
                                        onEditMember = { m ->
                                            editingMember = m
                                            showMemberEditor = true
                                        },
                                        onRecordPaymentForMember = { m ->
                                            editingPayment = null
                                            paymentTargetMember = m
                                            showPaymentEditor = true
                                        },
                                        onSendWhatsAppReminder = sendWhatsAppForMember,
                                        modifier = contentModifier
                                    )

                                    AdminTab.REMINDERS -> AdminRemindersScreen(
                                        snapshot = snap,
                                        selectedMonth = selectedMonth,
                                        onPrevMonth = viewModel::previousMonth,
                                        onNextMonth = viewModel::nextMonth,
                                        onSendWhatsAppReminder = sendWhatsAppForMember,
                                        onShareGroupNotice = { pending ->
                                            val notice = DateAndReportUtils.buildGroupReminderMessage(
                                                billingMonth = selectedMonth,
                                                pendingMembers = pending,
                                                settings = snap.settings
                                            )
                                            DateAndReportUtils.shareText(
                                                context,
                                                "Vinayagar Contribution Reminder",
                                                notice
                                            )
                                        },
                                        onOpenSettings = { showSettingsDialog = true },
                                        modifier = contentModifier
                                    )

                                    AdminTab.REPORTS -> AdminReportsScreen(
                                        snapshot = snap,
                                        selectedMonth = selectedMonth,
                                        onPrevMonth = viewModel::previousMonth,
                                        onNextMonth = viewModel::nextMonth,
                                        onShareReport = {
                                            val summary = DateAndReportUtils.computeMonthlyReport(
                                                selectedMonth,
                                                snap.members,
                                                snap.payments
                                            )
                                            val reportText = DateAndReportUtils.buildFormattedCollectionReport(
                                                summary = summary,
                                                settings = snap.settings,
                                                members = snap.members,
                                                payments = snap.payments
                                            )
                                            DateAndReportUtils.shareText(
                                                context,
                                                "Collection Report - ${summary.formattedMonth}",
                                                reportText
                                            )
                                        },
                                        onExportCsv = {
                                            val csv = DateAndReportUtils.buildCsvReport(
                                                billingMonth = selectedMonth,
                                                members = snap.members,
                                                payments = snap.payments
                                            )
                                            DateAndReportUtils.shareText(
                                                context,
                                                "Vinayagar_Collection_$selectedMonth.csv",
                                                csv
                                            )
                                        },
                                        onEditPayment = { p ->
                                            editingPayment = p
                                            paymentTargetMember = null
                                            showPaymentEditor = true
                                        },
                                        onRecordPayment = {
                                            editingPayment = null
                                            paymentTargetMember = null
                                            showPaymentEditor = true
                                        },
                                        modifier = contentModifier
                                    )
                                }

                                AdminModalsHost(
                                    snapshot = snap,
                                    selectedMonth = selectedMonth,
                                    showMemberEditor = showMemberEditor,
                                    editingMember = editingMember,
                                    confirmDeleteMember = confirmDeleteMember,
                                    showPaymentEditor = showPaymentEditor,
                                    editingPayment = editingPayment,
                                    paymentTargetMember = paymentTargetMember,
                                    confirmDeletePayment = confirmDeletePayment,
                                    showSettingsDialog = showSettingsDialog,
                                    onDismissMemberEditor = { showMemberEditor = false },
                                    onSaveMember = { name, phone, email, addr, amt, status ->
                                        val existing = editingMember
                                        if (existing == null) {
                                            viewModel.addMember(name, phone, email, addr, amt, status)
                                        } else {
                                            viewModel.updateMember(existing.id, name, phone, email, addr, amt, status)
                                        }
                                        showMemberEditor = false
                                    },
                                    onRequestDeleteMember = { m ->
                                        showMemberEditor = false
                                        confirmDeleteMember = m
                                    },
                                    onConfirmDeleteMember = { m ->
                                        viewModel.deleteMember(m)
                                        confirmDeleteMember = null
                                    },
                                    onDismissDeleteMember = { confirmDeleteMember = null },
                                    onDismissPaymentEditor = { showPaymentEditor = false },
                                    onCreatePayment = { member, month, amt, method, ref, status, notes ->
                                        viewModel.recordPayment(member, month, amt, method, ref, status, notes)
                                        showPaymentEditor = false
                                    },
                                    onUpdatePayment = { id, month, amt, method, ref, status, notes ->
                                        viewModel.updatePayment(id, month, amt, method, ref, status, notes)
                                        showPaymentEditor = false
                                    },
                                    onRequestDeletePayment = { p ->
                                        showPaymentEditor = false
                                        confirmDeletePayment = p
                                    },
                                    onConfirmDeletePayment = { p ->
                                        viewModel.deletePayment(p)
                                        confirmDeletePayment = null
                                    },
                                    onDismissDeletePayment = { confirmDeletePayment = null },
                                    onDismissSettings = { showSettingsDialog = false },
                                    onSaveSettings = { s ->
                                        viewModel.saveSettings(s)
                                        showSettingsDialog = false
                                    },
                                    onSaveAdminLock = { enabled, username, newPass ->
                                        viewModel.saveAdminPasscodeLock(
                                            enabled = enabled,
                                            username = username,
                                            newPasscode = newPass,
                                            existingSecurity = snap.adminSecurity
                                        )
                                    },
                                    onAddCoAdmin = viewModel::addCoAdmin,
                                    onRemoveCoAdmin = viewModel::removeCoAdmin
                                )
                                }
                            }
                        }
                    } else {
                        when (val state = memberState) {
                            UiState.Loading -> LoadingPane()
                            is UiState.Error -> ErrorPane(
                                message = state.message,
                                onRetry = { viewModel.refreshRole() }
                            )
                            is UiState.Success -> {
                                val snap = state.data
                                when (memberTab) {
                                    MemberTab.DUES -> MemberDuesScreen(
                                        snapshot = snap,
                                        selectedMonth = selectedMonth,
                                        userDisplayName = currentUserName,
                                        userEmail = currentUserEmail,
                                        onPrevMonth = viewModel::previousMonth,
                                        onNextMonth = viewModel::nextMonth,
                                        onSubmitUpiPayment = viewModel::submitMemberUpiPayment,
                                        onSubmitJoinRequest = viewModel::submitJoinRequest,
                                        onShowMessage = { msg ->
                                            scope.launch { snackbarHostState.showSnackbar(msg) }
                                        },
                                        modifier = contentModifier
                                    )
                                    MemberTab.RECEIPTS -> MemberReceiptsScreen(
                                        snapshot = snap,
                                        modifier = contentModifier
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminModalsHost(
    snapshot: AdminSnapshot,
    selectedMonth: String,
    showMemberEditor: Boolean,
    editingMember: Member?,
    confirmDeleteMember: Member?,
    showPaymentEditor: Boolean,
    editingPayment: Payment?,
    paymentTargetMember: Member?,
    confirmDeletePayment: Payment?,
    showSettingsDialog: Boolean,
    onDismissMemberEditor: () -> Unit,
    onSaveMember: (String, String, String, String, Long, MembershipStatus) -> Unit,
    onRequestDeleteMember: (Member) -> Unit,
    onConfirmDeleteMember: (Member) -> Unit,
    onDismissDeleteMember: () -> Unit,
    onDismissPaymentEditor: () -> Unit,
    onCreatePayment: (Member, String, Long, com.example.data.PaymentMethod, String, PaymentStatus, String) -> Unit,
    onUpdatePayment: (String, String, Long, com.example.data.PaymentMethod, String, PaymentStatus, String) -> Unit,
    onRequestDeletePayment: (Payment) -> Unit,
    onConfirmDeletePayment: (Payment) -> Unit,
    onDismissDeletePayment: () -> Unit,
    onDismissSettings: () -> Unit,
    onSaveSettings: (com.example.data.CommitteeSettings) -> Unit,
    onSaveAdminLock: (Boolean, String, String) -> Unit,
    onAddCoAdmin: (String, String, String) -> Unit,
    onRemoveCoAdmin: (com.example.data.AdminUser) -> Unit
) {
    if (showMemberEditor) {
        MemberEditorDialog(
            initialMember = editingMember,
            defaultAmount = snapshot.settings.defaultMonthlyAmount,
            onSave = onSaveMember,
            onDelete = editingMember?.let { m -> { onRequestDeleteMember(m) } },
            onDismiss = onDismissMemberEditor
        )
    }

    if (confirmDeleteMember != null) {
        DestructiveConfirmDialog(
            title = "Delete ${confirmDeleteMember.fullName}?",
            message = "This permanently removes the member record from the live committee ledger.",
            onConfirm = { onConfirmDeleteMember(confirmDeleteMember) },
            onDismiss = onDismissDeleteMember
        )
    }

    if (showPaymentEditor) {
        PaymentEditorDialog(
            members = snapshot.members,
            initialMember = paymentTargetMember,
            initialPayment = editingPayment,
            billingMonth = selectedMonth,
            onCreate = onCreatePayment,
            onUpdate = onUpdatePayment,
            onDelete = editingPayment?.let { p -> { onRequestDeletePayment(p) } },
            onDismiss = onDismissPaymentEditor
        )
    }

    if (confirmDeletePayment != null) {
        DestructiveConfirmDialog(
            title = "Delete Transaction?",
            message = "Remove the ${DateAndReportUtils.formatInr(confirmDeletePayment.amount)} payment record for ${confirmDeletePayment.memberName}?",
            onConfirm = { onConfirmDeletePayment(confirmDeletePayment) },
            onDismiss = onDismissDeletePayment
        )
    }

    if (showSettingsDialog) {
        CommitteeSettingsDialog(
            currentSettings = snapshot.settings,
            currentSecurity = snapshot.adminSecurity,
            admins = snapshot.admins,
            onSaveSettings = onSaveSettings,
            onSaveAdminLock = onSaveAdminLock,
            onAddCoAdmin = onAddCoAdmin,
            onRemoveCoAdmin = onRemoveCoAdmin,
            onDismiss = onDismissSettings
        )
    }
}

@Composable
private fun LoadingPane() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(36.dp)
        )
    }
}

@Composable
private fun ErrorPane(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Unable to sync committee data",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onRetry) {
                Text(stringResource(R.string.action_retry))
            }
        }
    }
}
