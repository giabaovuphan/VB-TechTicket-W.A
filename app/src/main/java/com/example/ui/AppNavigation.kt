package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.R
import com.example.data.AccountApprovalStatus
import com.example.data.InventoryRepository
import com.example.data.InvoiceRepository
import com.example.data.NotificationRepository
import com.example.data.ProductRepository
import com.example.data.TicketRepository
import com.example.data.UiState
import com.example.data.UserAccount
import com.example.data.UserRole
import com.example.data.UserRoleRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOut
import com.example.ui.components.RoleBadge
import com.example.ui.screens.AdminRolesScreen
import com.example.ui.screens.CreateTicketSheet
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.ProductCatalogScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.TicketDetailScreen
import com.example.ui.screens.TicketListScreen
import com.example.ui.viewmodel.AdminViewModel
import com.example.ui.viewmodel.AppSessionViewModel
import com.example.ui.viewmodel.ProductCatalogViewModel
import com.example.ui.viewmodel.TicketDetailViewModel
import com.example.ui.viewmodel.TicketListViewModel
import com.example.util.SystemNotificationHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

const val ROUTE_HOME = "home"
const val ROUTE_TICKET_DETAIL = "ticket_detail/{ticketId}"

private fun ticketDetailRoute(ticketId: String): String = "ticket_detail/$ticketId"

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

private enum class HomeTab(
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    TICKETS(
        R.string.nav_tickets,
        Icons.Filled.ConfirmationNumber,
        Icons.Outlined.ConfirmationNumber,
        "tab_tickets"
    ),
    PRODUCTS(
        R.string.nav_products,
        Icons.Filled.Inventory2,
        Icons.Outlined.Inventory2,
        "tab_products"
    ),
    NOTIFICATIONS(
        R.string.nav_notifications,
        Icons.Filled.Notifications,
        Icons.Outlined.Notifications,
        "tab_notifications"
    ),
    REPORTS(
        R.string.nav_reports,
        Icons.Filled.Assessment,
        Icons.Outlined.Assessment,
        "tab_reports"
    ),
    ADMIN(
        R.string.nav_admin,
        Icons.Filled.AdminPanelSettings,
        Icons.Outlined.AdminPanelSettings,
        "tab_admin"
    )
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            AuthenticatedAppShell(currentUserId = user.uid)
        }
    }
}

@Composable
private fun AuthenticatedAppShell(currentUserId: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val sessionViewModel: AppSessionViewModel = viewModel(
        key = "session_$currentUserId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                AppSessionViewModel(
                    userRoleRepository = UserRoleRepository(db),
                    notificationRepository = NotificationRepository(db),
                    currentUserId = currentUserId
                )
            }
        }
    )

    val ticketListViewModel: TicketListViewModel = viewModel(
        key = "tickets_$currentUserId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                TicketListViewModel(
                    ticketRepository = TicketRepository(db),
                    currentUserId = currentUserId
                )
            }
        }
    )

    val productCatalogViewModel: ProductCatalogViewModel = viewModel(
        key = "products_$currentUserId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                ProductCatalogViewModel(
                    productRepository = ProductRepository(db),
                    inventoryRepository = InventoryRepository(db)
                )
            }
        }
    )

    val adminViewModel: AdminViewModel = viewModel(
        key = "admin_$currentUserId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                AdminViewModel(
                    userRoleRepository = UserRoleRepository(db),
                    inventoryRepository = InventoryRepository(db)
                )
            }
        }
    )

    val profileState by sessionViewModel.currentUserProfile.collectAsStateWithLifecycle()
    val notificationsState by sessionViewModel.notificationsState.collectAsStateWithLifecycle()
    val syncError by sessionViewModel.syncMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(sessionViewModel) {
        sessionViewModel.incomingNotification.collect { notif ->
            SystemNotificationHelper.showStatusNotification(
                context = context,
                notificationKey = notif.id,
                title = notif.title,
                body = notif.message
            )
            snackbarHostState.showSnackbar("${notif.title}: ${notif.message}")
        }
    }

    when (val state = profileState) {
        is UiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Đang đồng bộ quyền truy cập...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (syncError != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = syncError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = { sessionViewModel.retryProfileSync() }) {
                                Text("Thử lại")
                            }
                            Button(onClick = { signOut(credentialManager, scope) }) {
                                Text("Đăng xuất")
                            }
                        }
                    }
                }
            }
        }

        is UiState.Error -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { signOut(credentialManager, scope) }) {
                        Text(stringResource(R.string.sign_out))
                    }
                }
            }
        }

        is UiState.Success -> {
            val account = state.data
            when {
                account.parsedApprovalStatus == AccountApprovalStatus.REJECTED -> {
                    RejectedAccountScreen(
                        account = account,
                        onSignOut = { signOut(credentialManager, scope) }
                    )
                }

                account.parsedRole == UserRole.PENDING ||
                    account.parsedApprovalStatus == AccountApprovalStatus.PENDING -> {
                    PendingApprovalScreen(
                        account = account,
                        syncMessage = syncError,
                        onRefresh = { sessionViewModel.retryProfileSync() },
                        onSignOut = { signOut(credentialManager, scope) }
                    )
                }

                !account.active -> {
                    DisabledAccountScreen(
                        account = account,
                        onSignOut = { signOut(credentialManager, scope) }
                    )
                }

                else -> {
                    LaunchedEffect(account.parsedRole) {
                        ticketListViewModel.setRole(account.parsedRole)
                        productCatalogViewModel.setRole(account.parsedRole)
                        adminViewModel.setRole(account.parsedRole)
                    }

                    ActiveWorkspaceNavHost(
                        currentUser = account,
                        currentUserId = currentUserId,
                        ticketListViewModel = ticketListViewModel,
                        productCatalogViewModel = productCatalogViewModel,
                        sessionViewModel = sessionViewModel,
                        adminViewModel = adminViewModel,
                        notificationsState = notificationsState,
                        snackbarHostState = snackbarHostState,
                        onSignOut = { signOut(credentialManager, scope) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActiveWorkspaceNavHost(
    currentUser: UserAccount,
    currentUserId: String,
    ticketListViewModel: TicketListViewModel,
    productCatalogViewModel: ProductCatalogViewModel,
    sessionViewModel: AppSessionViewModel,
    adminViewModel: AdminViewModel,
    notificationsState: UiState<List<com.example.data.TicketNotification>>,
    snackbarHostState: SnackbarHostState,
    onSignOut: () -> Unit
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(HomeTab.TICKETS) }
    var showCreateSheet by remember { mutableStateOf(false) }

    val ticketsState by ticketListViewModel.ticketsState.collectAsStateWithLifecycle()
    val selectedStatus by ticketListViewModel.selectedStatusFilter.collectAsStateWithLifecycle()
    val selectedPriority by ticketListViewModel.selectedPriorityFilter.collectAsStateWithLifecycle()
    val searchQuery by ticketListViewModel.searchQuery.collectAsStateWithLifecycle()
    val createActionState by ticketListViewModel.createActionState.collectAsStateWithLifecycle()

    val inventoryState by productCatalogViewModel.inventoryState.collectAsStateWithLifecycle()
    val productsState by productCatalogViewModel.productsState.collectAsStateWithLifecycle()
    val productSaving by productCatalogViewModel.isSaving.collectAsStateWithLifecycle()
    val productFeedback by productCatalogViewModel.feedback.collectAsStateWithLifecycle()

    val allUsersState by adminViewModel.allUsersState.collectAsStateWithLifecycle()
    val assignmentsState by adminViewModel.roleAssignmentsState.collectAsStateWithLifecycle()
    val adminSaving by adminViewModel.isSaving.collectAsStateWithLifecycle()
    val adminFeedback by adminViewModel.feedback.collectAsStateWithLifecycle()

    val unreadCount = (notificationsState as? UiState.Success)?.data?.count { !it.readFlag } ?: 0

    val availableTabs = remember(currentUser.parsedRole) {
        when (currentUser.parsedRole) {
            UserRole.PENDING -> emptyList()
            UserRole.USER, UserRole.TECHNICIAN -> listOf(HomeTab.TICKETS, HomeTab.NOTIFICATIONS)
            UserRole.TEAM_LEAD -> listOf(HomeTab.TICKETS, HomeTab.PRODUCTS, HomeTab.NOTIFICATIONS, HomeTab.REPORTS)
            UserRole.ADMIN -> listOf(HomeTab.TICKETS, HomeTab.PRODUCTS, HomeTab.NOTIFICATIONS, HomeTab.REPORTS, HomeTab.ADMIN)
        }
    }

    LaunchedEffect(availableTabs) {
        if (selectedTab !in availableTabs) {
            selectedTab = HomeTab.TICKETS
        }
    }

    NavHost(
        navController = navController,
        startDestination = ROUTE_HOME
    ) {
        composable(ROUTE_HOME) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isWide = maxWidth >= 600.dp

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    contentWindowInsets = WindowInsets.navigationBars,
                    topBar = {
                        Column {
                            TopAppBar(
                                title = {
                                    Column {
                                        Text(
                                            text = stringResource(R.string.app_name),
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = currentUser.displayName,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            RoleBadge(role = currentUser.parsedRole)
                                        }
                                    }
                                },
                                actions = {
                                    IconButton(
                                        onClick = onSignOut,
                                        modifier = Modifier.testTag("sign_out_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Logout,
                                            contentDescription = stringResource(R.string.sign_out)
                                        )
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    },
                    floatingActionButton = {
                        if (selectedTab == HomeTab.TICKETS) {
                            ExtendedFloatingActionButton(
                                onClick = {
                                    ticketListViewModel.resetCreateState()
                                    showCreateSheet = true
                                },
                                icon = {
                                    Icon(Icons.Filled.Add, contentDescription = null)
                                },
                                text = { Text(stringResource(R.string.create_ticket)) },
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.testTag("create_ticket_fab")
                            )
                        }
                    },
                    bottomBar = {
                        if (!isWide) {
                            Column {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                NavigationBar(
                                    windowInsets = WindowInsets.navigationBars,
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    tonalElevation = 0.dp
                                ) {
                                    availableTabs.forEach { tab ->
                                        val selected = selectedTab == tab
                                        NavigationBarItem(
                                            selected = selected,
                                            onClick = { selectedTab = tab },
                                            icon = {
                                                TabIconWithBadge(
                                                    tab = tab,
                                                    selected = selected,
                                                    unreadCount = unreadCount
                                                )
                                            },
                                            label = {
                                                Text(
                                                    text = stringResource(tab.labelRes),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                            ),
                                            modifier = Modifier.testTag(tab.testTag)
                                        )
                                    }
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (isWide) {
                            NavigationRail(
                                containerColor = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxHeight()
                            ) {
                                availableTabs.forEach { tab ->
                                    val selected = selectedTab == tab
                                    NavigationRailItem(
                                        selected = selected,
                                        onClick = { selectedTab = tab },
                                        icon = {
                                            TabIconWithBadge(
                                                tab = tab,
                                                selected = selected,
                                                unreadCount = unreadCount
                                            )
                                        },
                                        label = { Text(stringResource(tab.labelRes)) },
                                        colors = NavigationRailItemDefaults.colors(
                                            selectedIconColor = MaterialTheme.colorScheme.primary,
                                            selectedTextColor = MaterialTheme.colorScheme.primary,
                                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        modifier = Modifier.testTag(tab.testTag)
                                    )
                                }
                            }
                            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .widthIn(max = 880.dp)
                                    .fillMaxWidth()
                            ) {
                                when (selectedTab) {
                                    HomeTab.TICKETS -> TicketListScreen(
                                        currentUser = currentUser,
                                        ticketsState = ticketsState,
                                        selectedStatus = selectedStatus,
                                        selectedPriority = selectedPriority,
                                        searchQuery = searchQuery,
                                        onStatusSelect = ticketListViewModel::setStatusFilter,
                                        onPrioritySelect = ticketListViewModel::setPriorityFilter,
                                        onSearchChange = ticketListViewModel::setSearchQuery,
                                        onTicketClick = { ticketId ->
                                            navController.navigate(ticketDetailRoute(ticketId))
                                        }
                                    )

                                    HomeTab.PRODUCTS -> ProductCatalogScreen(
                                        currentUser = currentUser,
                                        inventoryState = inventoryState,
                                        productsState = productsState,
                                        isSaving = productSaving,
                                        feedback = productFeedback,
                                        onSaveInventory = productCatalogViewModel::saveInventoryItem,
                                        onAdjustInventoryStock = productCatalogViewModel::adjustInventoryStock,
                                        onToggleInventoryActive = productCatalogViewModel::toggleInventoryActive,
                                        onDeleteInventory = productCatalogViewModel::deleteInventoryItem,
                                        onSeedStarterData = { productCatalogViewModel.seedStarterDatabase(currentUser) },
                                        onSaveProduct = productCatalogViewModel::saveProduct,
                                        onToggleActive = productCatalogViewModel::toggleActive,
                                        onDeleteProduct = productCatalogViewModel::deleteProduct,
                                        onClearFeedback = productCatalogViewModel::clearFeedback
                                    )

                                    HomeTab.NOTIFICATIONS -> NotificationsScreen(
                                        notificationsState = notificationsState,
                                        onNotificationClick = { notif ->
                                            if (!notif.readFlag && notif.id.isNotBlank()) {
                                                sessionViewModel.markNotificationRead(notif.id)
                                            }
                                            if (notif.ticketId.isNotBlank()) {
                                                navController.navigate(ticketDetailRoute(notif.ticketId))
                                            }
                                        },
                                        onMarkAllRead = sessionViewModel::markAllNotificationsRead
                                    )

                                    HomeTab.REPORTS -> ReportsScreen(
                                        ticketsState = ticketsState
                                    )

                                    HomeTab.ADMIN -> AdminRolesScreen(
                                        currentUser = currentUser,
                                        usersState = allUsersState,
                                        assignmentsState = assignmentsState,
                                        isSaving = adminSaving,
                                        feedback = adminFeedback,
                                        onAssignByEmail = adminViewModel::assignRoleByEmail,
                                        onUpdateUser = adminViewModel::updateUserRoleAndActive,
                                        onApprovePending = adminViewModel::approvePendingUser,
                                        onRejectPending = adminViewModel::rejectPendingUser,
                                        onRemoveAssignment = adminViewModel::removeAssignment,
                                        onSeedStarterData = { adminViewModel.seedStarterDatabase(currentUser) },
                                        onClearFeedback = adminViewModel::clearFeedback
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (showCreateSheet) {
                CreateTicketSheet(
                    currentUser = currentUser,
                    createState = createActionState,
                    onDismiss = {
                        showCreateSheet = false
                        ticketListViewModel.resetCreateState()
                    },
                    onSubmit = { title, desc, category, priority, photo ->
                        ticketListViewModel.createTicket(
                            title = title,
                            description = desc,
                            category = category,
                            priority = priority,
                            creator = currentUser,
                            defectPhotoBase64 = photo
                        )
                    }
                )
            }
        }

        composable(
            route = ROUTE_TICKET_DETAIL,
            arguments = listOf(navArgument("ticketId") { type = NavType.StringType })
        ) { backStackEntry ->
            val ticketId = backStackEntry.arguments?.getString("ticketId") ?: ""
            val detailViewModel: TicketDetailViewModel = viewModel(
                key = "detail_${currentUserId}_$ticketId",
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY])
                        val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                        TicketDetailViewModel(
                            ticketRepository = TicketRepository(db),
                            userRoleRepository = UserRoleRepository(db),
                            invoiceRepository = InvoiceRepository(db),
                            productRepository = ProductRepository(db),
                            inventoryRepository = InventoryRepository(db),
                            ticketId = ticketId
                        )
                    }
                }
            )

            LaunchedEffect(currentUser.parsedRole) {
                detailViewModel.setCurrentUserRole(currentUser.parsedRole)
            }

            val ticketState by detailViewModel.ticketState.collectAsStateWithLifecycle()
            val invoiceState by detailViewModel.invoiceState.collectAsStateWithLifecycle()
            val technicians by detailViewModel.techniciansState.collectAsStateWithLifecycle()
            val activeProducts by detailViewModel.activeProductsState.collectAsStateWithLifecycle()
            val activeInventory by detailViewModel.activeInventoryState.collectAsStateWithLifecycle()
            val isProcessing by detailViewModel.isProcessing.collectAsStateWithLifecycle()
            val ticketDeleted by detailViewModel.ticketDeleted.collectAsStateWithLifecycle()
            val feedback by detailViewModel.actionFeedback.collectAsStateWithLifecycle()

            TicketDetailScreen(
                currentUser = currentUser,
                ticketState = ticketState,
                invoiceState = invoiceState,
                technicians = technicians,
                activeProducts = activeProducts,
                activeInventory = activeInventory,
                isProcessing = isProcessing,
                ticketDeleted = ticketDeleted,
                feedback = feedback,
                onBack = { navController.popBackStack() },
                onTicketDeleted = {
                    navController.popBackStack()
                    scope.launch {
                        snackbarHostState.showSnackbar("Đã xóa ticket khỏi hệ thống.")
                    }
                },
                onAssignTicket = detailViewModel::assignTicket,
                onStartProgress = detailViewModel::startProgress,
                onCompleteTicket = detailViewModel::completeTicket,
                onAdminOverride = detailViewModel::adminOverrideStatus,
                onDeleteTicket = detailViewModel::deleteTicket,
                onSaveInvoice = detailViewModel::saveInvoice,
                onMarkInvoicePaid = detailViewModel::markInvoicePaid,
                onClearFeedback = detailViewModel::clearFeedback
            )
        }
    }
}

@Composable
private fun TabIconWithBadge(
    tab: HomeTab,
    selected: Boolean,
    unreadCount: Int
) {
    val icon = if (selected) tab.selectedIcon else tab.unselectedIcon
    if (tab == HomeTab.NOTIFICATIONS && unreadCount > 0) {
        BadgedBox(
            badge = {
                Badge(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Text(if (unreadCount > 99) "99+" else "$unreadCount")
                }
            }
        ) {
            Icon(imageVector = icon, contentDescription = stringResource(tab.labelRes))
        }
    } else {
        Icon(imageVector = icon, contentDescription = stringResource(tab.labelRes))
    }
}

@Composable
private fun PendingApprovalScreen(
    account: UserAccount,
    syncMessage: String?,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
            modifier = Modifier.widthIn(max = 460.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
                RoleBadge(role = UserRole.PENDING)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Tài khoản đang chờ cấp quyền",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${account.displayName} (${account.email})",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tài khoản Google của bạn đã được ghi nhận. Chỉ Quản trị viên mới có quyền cấp vai trò (Người dùng, Kỹ thuật viên, Trưởng nhóm). Ứng dụng sẽ tự động mở ngay khi Quản trị viên phê duyệt.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                if (!syncMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = syncMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onSignOut,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("pending_sign_out_button")
                    ) {
                        Text("Đổi tài khoản")
                    }
                    Button(
                        onClick = onRefresh,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("pending_refresh_button")
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Kiểm tra lại")
                    }
                }
            }
        }
    }
}

@Composable
private fun RejectedAccountScreen(
    account: UserAccount,
    onSignOut: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
            modifier = Modifier.widthIn(max = 440.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Block,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Yêu cầu truy cập bị từ chối",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Quản trị viên đã từ chối cấp quyền cho tài khoản ${account.email}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                account.rejectionReason?.takeIf { it.isNotBlank() }?.let { reason ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Lý do: $reason",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onSignOut,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("rejected_sign_out_button")
                ) {
                    Text(stringResource(R.string.sign_out))
                }
            }
        }
    }
}

@Composable
private fun DisabledAccountScreen(
    account: UserAccount,
    onSignOut: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 420.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Block,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Tài khoản đang bị tạm khóa",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tài khoản ${account.email} hiện không có quyền truy cập hệ thống. Vui lòng liên hệ Quản trị viên để kích hoạt lại.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(onClick = onSignOut) {
                Text(stringResource(R.string.sign_out))
            }
        }
    }
}
