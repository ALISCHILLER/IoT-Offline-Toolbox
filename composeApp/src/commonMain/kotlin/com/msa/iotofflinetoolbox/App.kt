package com.msa.iotofflinetoolbox

import com.msa.iotofflinetoolbox.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.msa.iotofflinetoolbox.core.model.APP_VERSION
import com.msa.iotofflinetoolbox.core.model.AppLanguage
import com.msa.iotofflinetoolbox.core.model.AppState
import com.msa.iotofflinetoolbox.core.model.ToolScreen
import com.msa.iotofflinetoolbox.core.store.ToolboxStore
import com.msa.iotofflinetoolbox.ui.components.LocalCompactMode
import com.msa.iotofflinetoolbox.ui.layout.LocalResponsiveLayout
import com.msa.iotofflinetoolbox.ui.layout.NavigationMode
import com.msa.iotofflinetoolbox.ui.layout.ResponsiveLayoutInfo
import com.msa.iotofflinetoolbox.ui.layout.responsiveLayout
import com.msa.iotofflinetoolbox.ui.localization.AppLocaleEnvironment
import com.msa.iotofflinetoolbox.ui.localization.localizedTitle
import com.msa.iotofflinetoolbox.ui.platform.PlatformSystemBars
import com.msa.iotofflinetoolbox.ui.screens.DashboardScreen
import com.msa.iotofflinetoolbox.ui.screens.DevicesScreen
import com.msa.iotofflinetoolbox.ui.screens.DiscoveryScreen
import com.msa.iotofflinetoolbox.ui.screens.GuideScreen
import com.msa.iotofflinetoolbox.ui.screens.HttpScreen
import com.msa.iotofflinetoolbox.ui.screens.LogsScreen
import com.msa.iotofflinetoolbox.ui.screens.NetworkToolsScreen
import com.msa.iotofflinetoolbox.ui.screens.PayloadScreen
import com.msa.iotofflinetoolbox.ui.screens.ProfilesScreen
import com.msa.iotofflinetoolbox.ui.screens.RealtimeScreen
import com.msa.iotofflinetoolbox.ui.screens.SettingsScreen
import com.msa.iotofflinetoolbox.ui.theme.ToolboxTheme
import com.msa.iotofflinetoolbox.ui.theme.icon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private data class NavigationGroup(
    val key: String,
    val label: StringResource,
    val screens: List<ToolScreen>,
)

private val navigationGroups = listOf(
    NavigationGroup(
        key = "tools",
        label = Res.string.nav_tools,
        screens = listOf(
            ToolScreen.DASHBOARD,
            ToolScreen.DISCOVERY,
            ToolScreen.NETWORK_TOOLS,
            ToolScreen.HTTP_CLIENT,
            ToolScreen.REALTIME,
            ToolScreen.PAYLOAD_TOOLS,
        ),
    ),
    NavigationGroup(
        key = "workspace",
        label = Res.string.nav_workspace,
        screens = listOf(
            ToolScreen.PROFILES,
            ToolScreen.DEVICES,
            ToolScreen.LOGS,
        ),
    ),
    NavigationGroup(
        key = "support",
        label = Res.string.nav_support,
        screens = listOf(ToolScreen.GUIDE, ToolScreen.SETTINGS),
    ),
)

@Composable
fun App() {
    val store = remember { ToolboxStore(scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)) }
    val state by store.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    DisposableEffect(store) { onDispose(store::close) }

    val effectiveDirection = if (state.settings.rtl || state.settings.language == AppLanguage.PERSIAN) {
        LayoutDirection.Rtl
    } else {
        LayoutDirection.Ltr
    }
    val effectiveDarkMode = if (state.settings.useSystemTheme) {
        isSystemInDarkTheme()
    } else {
        state.settings.darkMode
    }

    state.noticeMessage?.let { message ->
        LaunchedEffect(message) {
            store.dismissNotice()
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short,
            )
        }
    }

    AppLocaleEnvironment(state.settings.language) {
        CompositionLocalProvider(
            LocalLayoutDirection provides effectiveDirection,
            LocalCompactMode provides state.settings.compactMode,
        ) {
            ToolboxTheme(effectiveDarkMode) {
                PlatformSystemBars(effectiveDarkMode)
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val layout = remember(maxWidth, maxHeight) {
                            ResponsiveLayoutInfo.calculate(maxWidth, maxHeight)
                        }
                        CompositionLocalProvider(LocalResponsiveLayout provides layout) {
                            Box(Modifier.fillMaxSize()) {
                                when (layout.navigationMode) {
                                    NavigationMode.SIDEBAR -> DesktopShell(
                                        current = state.currentScreen,
                                        language = state.settings.language,
                                        onNavigate = store::navigate,
                                    ) {
                                        AdaptiveContentFrame { ScreenContent(store, state) }
                                    }

                                    NavigationMode.RAIL -> NavigationRailShell(
                                        current = state.currentScreen,
                                        language = state.settings.language,
                                        onNavigate = store::navigate,
                                    ) {
                                        AdaptiveContentFrame { ScreenContent(store, state) }
                                    }

                                    NavigationMode.DRAWER -> MobileShell(
                                        current = state.currentScreen,
                                        language = state.settings.language,
                                        onNavigate = store::navigate,
                                    ) {
                                        AdaptiveContentFrame { ScreenContent(store, state) }
                                    }
                                }

                                SnackbarHost(
                                    hostState = snackbarHostState,
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .widthIn(max = 560.dp)
                                        .padding(16.dp),
                                )

                                if (state.isBusy) {
                                    OperationOverlay(
                                        title = state.operationTitle ?: stringResource(Res.string.working_8f42b0eb),
                                        onCancel = store::cancelOperation,
                                    )
                                }

                                state.errorMessage?.let { message ->
                                    ErrorDialog(message = message, onDismiss = store::dismissError)
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
private fun AdaptiveContentFrame(content: @Composable () -> Unit) {
    val layout = responsiveLayout()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = layout.maxContentWidth)
                .fillMaxWidth()
                .fillMaxHeight(),
        ) {
            content()
        }
    }
}

@Composable
private fun ScreenContent(store: ToolboxStore, state: AppState) {
    when (state.currentScreen) {
        ToolScreen.DASHBOARD -> DashboardScreen(state, store::navigate)
        ToolScreen.DISCOVERY -> DiscoveryScreen(
            state = state,
            onDiscover = store::discover,
            onDiscoverSsdp = store::discoverSsdp,
            onDiscoverMdns = store::discoverMdns,
            onSave = { host, name -> store.saveDevice(host, name) },
            onSaveSsdp = store::saveSsdpDevice,
        )

        ToolScreen.NETWORK_TOOLS -> NetworkToolsScreen(
            state = state,
            onPing = store::ping,
            onScanPorts = store::scanPorts,
            onCalculateSubnet = store::calculateSubnet,
            onResolve = store::resolve,
            onTcpExchange = store::exchangeTcp,
            onUdpExchange = store::exchangeUdp,
        )

        ToolScreen.HTTP_CLIENT -> HttpScreen(state, store::executeHttp)
        ToolScreen.REALTIME -> RealtimeScreen(state, store::executeWebSocket, store::executeMqtt, store::executeCoap)
        ToolScreen.PAYLOAD_TOOLS -> PayloadScreen(state, store::transformPayload, store::saveTemplate, store::deleteTemplate)
        ToolScreen.PROFILES -> ProfilesScreen(
            state = state,
            onSaveProfile = store::saveProfile,
            onDeleteProfile = store::deleteProfile,
            onExport = store::exportBackup,
            onInspect = store::inspectBackup,
            onImport = store::importBackup,
            onClearHistory = store::clearHistory,
        )

        ToolScreen.DEVICES -> DevicesScreen(state.savedDevices, store::updateDevice, store::deleteDevice)
        ToolScreen.LOGS -> LogsScreen(state.logs, store::clearLogs)
        ToolScreen.GUIDE -> GuideScreen(state.capabilities)
        ToolScreen.SETTINGS -> SettingsScreen(state.settings, state.capabilities, store::updateSettings)
    }
}

@Composable
private fun DesktopShell(
    current: ToolScreen,
    language: AppLanguage,
    onNavigate: (ToolScreen) -> Unit,
    content: @Composable () -> Unit,
) {
    Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Surface(
            modifier = Modifier.width(284.dp).fillMaxHeight(),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 0.dp,
        ) {
            Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 14.dp)) {
                BrandHeader(compact = false)
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    navigationGroups.forEachIndexed { index, group ->
                        if (index > 0) {
                            item("divider-$index") {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                                )
                            }
                        }
                        item("label-${group.key}") {
                            NavigationGroupLabel(group)
                        }
                        items(group.screens, key = { it.name }) { screen ->
                            NavButton(screen, language, current == screen) { onNavigate(screen) }
                        }
                    }
                }
                VersionFooter()
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight()) { content() }
    }
}

@Composable
private fun NavigationRailShell(
    current: ToolScreen,
    language: AppLanguage,
    onNavigate: (ToolScreen) -> Unit,
    content: @Composable () -> Unit,
) {
    val layout = responsiveLayout()
    val railWidth = if (layout.isLowHeight) 72.dp else 96.dp
    Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Surface(
            modifier = Modifier.width(railWidth).fillMaxHeight(),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 0.dp,
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    horizontal = 6.dp,
                    vertical = if (layout.isLowHeight) 5.dp else 10.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(if (layout.isLowHeight) 0.dp else 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item {
                    BrandMark(compact = true)
                    Spacer(Modifier.height(if (layout.isLowHeight) 3.dp else 8.dp))
                }
                items(ToolScreen.entries, key = { it.name }) { screen ->
                    val screenTitle = screen.localizedTitle()
                    NavigationRailItem(
                        selected = current == screen,
                        onClick = { onNavigate(screen) },
                        icon = {
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = null,
                                modifier = Modifier.size(if (layout.isLowHeight) 21.dp else 24.dp),
                            )
                        },
                        label = if (layout.isLowHeight) {
                            null
                        } else {
                            {
                                Text(
                                    screenTitle,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        },
                        alwaysShowLabel = !layout.isLowHeight,
                        colors = NavigationRailItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = screenTitle },
                    )
                }
                item { VersionFooter(compact = true) }
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight()) { content() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MobileShell(
    current: ToolScreen,
    language: AppLanguage,
    onNavigate: (ToolScreen) -> Unit,
    content: @Composable () -> Unit,
) {
    val layout = responsiveLayout()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val menuDescription = stringResource(Res.string.open_navigation_menu_32d60639)
    val closeMenuDescription = stringResource(Res.string.close_navigation_menu_3bb889ec)
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.fillMaxHeight().widthIn(max = 372.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) { BrandHeader(compact = layout.isLowHeight) }
                    IconButton(
                        onClick = { scope.launch { drawerState.close() } },
                        modifier = Modifier.semantics {
                            contentDescription = closeMenuDescription
                        },
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = null)
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    navigationGroups.forEachIndexed { index, group ->
                        if (index > 0) {
                            item("drawer-divider-$index") {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                                )
                            }
                        }
                        item("drawer-label-${group.key}") { NavigationGroupLabel(group) }
                        items(group.screens, key = { it.name }) { screen ->
                            NavButton(screen, language, current == screen) {
                                onNavigate(screen)
                                scope.launch { drawerState.close() }
                            }
                        }
                    }
                }
                VersionFooter()
            }
        },
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                if (layout.isLowHeight) {
                    CompactTopBar(
                        title = current.localizedTitle(),
                        menuDescription = menuDescription,
                        onMenu = { scope.launch { drawerState.open() } },
                    )
                } else {
                    TopAppBar(
                        title = {
                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(
                                    current.localizedTitle(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    "MSA · IoT Offline Toolbox",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        navigationIcon = {
                            MenuButton(menuDescription) { scope.launch { drawerState.open() } }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                    )
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) { content() }
        }
    }
}

@Composable
private fun CompactTopBar(
    title: String,
    menuDescription: String,
    onMenu: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MenuButton(menuDescription, onMenu)
            Text(
                title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MenuButton(contentDescription: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .semantics { this.contentDescription = contentDescription },
    ) {
        Icon(Icons.Outlined.Menu, contentDescription = null)
    }
}

@Composable
private fun BrandHeader(compact: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(
            horizontal = if (compact) 16.dp else 14.dp,
            vertical = if (compact) 10.dp else 14.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandMark(compact = compact)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                "IoT Offline Toolbox",
                style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(Res.string.local_network_workbench_95b876e1),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BrandMark(compact: Boolean) {
    Surface(
        modifier = Modifier.size(if (compact) 40.dp else 46.dp),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = RoundedCornerShape(if (compact) 13.dp else 15.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                "M",
                style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun NavigationGroupLabel(group: NavigationGroup) {
    Text(
        text = stringResource(group.label),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
    )
}

@Composable
private fun NavButton(screen: ToolScreen, language: AppLanguage, selected: Boolean, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = {
            Text(
                screen.localizedTitle(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        selected = selected,
        onClick = onClick,
        icon = {
            Icon(
                imageVector = screen.icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
        },
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurface,
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun VersionFooter(compact: Boolean = false) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = if (compact) 4.dp else 10.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(14.dp),
    ) {
        if (compact) {
            Box(Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                Text(
                    "v$APP_VERSION",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(30.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = CircleShape,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Shield, contentDescription = null, modifier = Modifier.size(17.dp))
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text("MSA · v$APP_VERSION", style = MaterialTheme.typography.labelMedium)
                    Text(
                        stringResource(Res.string.local_first_no_analytics_c052aade),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun OperationOverlay(title: String, onCancel: () -> Unit) {
    val layout = responsiveLayout()
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.52f))
            .padding(layout.horizontalPadding)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = title
            },
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            Column(
                modifier = Modifier.padding(if (layout.isLowHeight) 20.dp else 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (layout.isLowHeight) 10.dp else 16.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(if (layout.isLowHeight) 36.dp else 44.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(Res.string.the_operation_is_running_locally_you_can_cancel_8cdfe1c9),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(Res.string.cancel_operation_c1f73ef6))
                }
            }
        }
    }
}

@Composable
private fun ErrorDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.close_88a26d81)) }
        },
        title = { Text(stringResource(Res.string.operation_failed_12de5896)) },
        text = { Text(message) },
    )
}
