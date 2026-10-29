package com.conreo.couchytv.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ProvideTextStyle
import androidx.tv.material3.SelectableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import androidx.compose.ui.graphics.vector.ImageVector
import com.conreo.couchytv.Actions
import com.conreo.couchytv.R
import com.conreo.couchytv.data.AppEntry
import com.conreo.couchytv.data.AppRepository
import com.conreo.couchytv.data.CategoryCfg
import com.conreo.couchytv.data.ConfigStore
import com.conreo.couchytv.data.DATE_FORMATS
import com.conreo.couchytv.data.LAYOUT_DOCK
import com.conreo.couchytv.data.LAYOUT_GRID
import com.conreo.couchytv.data.LauncherConfig
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlinx.serialization.json.Json

private val format = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

private enum class SettingsScreen { Main, Wallpaper, Display, StatusBar, Apps, Categories, Launcher, About }

/**
 * Settings panel built entirely from focusable rows and buttons — every
 * element is reachable with the D-pad alone. Text entry only ever happens
 * inside a dedicated dialog (opened with the pencil button).
 */
@Composable
fun SettingsSheet(
    config: LauncherConfig,
    apps: List<AppEntry>,
    store: ConfigStore,
    onDismiss: () -> Unit,
    onWallpaperChanged: () -> Unit,
    onRerunWizard: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf(SettingsScreen.Main) }

    // Same document-picker flow as videos: opens the system file manager,
    // works with USB drives and network storage providers.
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        File(context.filesDir, "wallpaper.jpg").outputStream().use { out ->
                            input.copyTo(out)
                        }
                    }
                }
                store.update {
                    it.copy(
                        useCustomWallpaper = true,
                        useVideoWallpaper = false,
                        useBuiltinAerials = false,
                    )
                }
                onWallpaperChanged()
            }
        }
    }

    // Video wallpaper: keep a persistable read grant and stream in place —
    // aerial files are hundreds of MB, never copied.
    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            scope.launch {
                store.update {
                    it.copy(
                        videoUri = uri.toString(),
                        useVideoWallpaper = true,
                        useCustomWallpaper = false,
                        useBuiltinAerials = false,
                    )
                }
            }
        }
    }

    Dialog(
        onDismissRequest = {
            if (screen != SettingsScreen.Main) screen = SettingsScreen.Main else onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // Force the dialog window to fill the screen — Compose dialogs otherwise
        // keep a small platform margin, so the left sheet wouldn't sit flush
        // against the screen edge.
        val dialogView = androidx.compose.ui.platform.LocalView.current
        androidx.compose.runtime.SideEffect {
            ((dialogView.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window)
                ?.setLayout(
                    android.view.WindowManager.LayoutParams.MATCH_PARENT,
                    android.view.WindowManager.LayoutParams.MATCH_PARENT,
                )
        }
        // A Dialog spawns its own window that re-provides the platform density,
        // so the launcher's ScaledUi doesn't reach here — re-apply it. The
        // dialog's LocalConfiguration is the real (unscaled) device width.
        ScaledUi(uiScaleFactor(config.uiScale, androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
            Surface(
                shape = RoundedCornerShape(0.dp),
                colors = SurfaceDefaults.colors(containerColor = SettingsColors.pageBg),
                modifier = Modifier
                    .width(557.dp)
                    .fillMaxHeight()
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(0.dp),
                        clip = false,
                        ambientColor = SettingsColors.sheetShadow,
                        spotColor = SettingsColors.sheetShadow,
                    ),
            ) {
                // Slide left when entering a sub-screen, right when going back
                // to Main (the root); cross-fade so it never looks abrupt.
                androidx.compose.animation.AnimatedContent(
                    targetState = screen,
                    transitionSpec = {
                        val forward = targetState != SettingsScreen.Main
                        val dir = if (forward) 1 else -1
                        (androidx.compose.animation.slideInHorizontally(tween(260)) { w -> dir * w } +
                            androidx.compose.animation.fadeIn(tween(260))) togetherWith
                            (androidx.compose.animation.slideOutHorizontally(tween(260)) { w -> -dir * w } +
                                androidx.compose.animation.fadeOut(tween(260)))
                    },
                    label = "settingsScreen",
                ) { target ->
                when (target) {
                    SettingsScreen.Main -> MainScreen(
                        onWallpaper = { screen = SettingsScreen.Wallpaper },
                        onDisplay = { screen = SettingsScreen.Display },
                        onStatusBar = { screen = SettingsScreen.StatusBar },
                        onApps = { screen = SettingsScreen.Apps },
                        onCategories = { screen = SettingsScreen.Categories },
                        onLauncher = { screen = SettingsScreen.Launcher },
                        onAndroidSettings = { Actions.openSystemSettings(context) },
                        onAbout = { screen = SettingsScreen.About },
                    )
                    SettingsScreen.Wallpaper -> WallpaperScreen(
                        config = config,
                        onSelectBuiltinAerials = {
                            scope.launch {
                                store.update {
                                    it.copy(
                                        useBuiltinAerials = true,
                                        useVideoWallpaper = false,
                                        useCustomWallpaper = false,
                                    )
                                }
                            }
                        },
                        onSelectBuiltinSource = { v ->
                            scope.launch { store.update { it.copy(builtinSource = v) } }
                        },
                        onSetScrim = { v ->
                            scope.launch { store.update { it.copy(scrimMode = v) } }
                        },
                        onSetSpeed = { v ->
                            scope.launch { store.update { it.copy(videoSpeed = v) } }
                        },
                        onPreset = { i ->
                            scope.launch {
                                store.update {
                                    it.copy(
                                        wallpaper = i,
                                        useCustomWallpaper = false,
                                        useVideoWallpaper = false,
                                        useBuiltinAerials = false,
                                    )
                                }
                            }
                        },
                        onPickPhoto = {
                            runCatching { photoPicker.launch(arrayOf("image/*")) }
                                .onFailure { Actions.toast(context, context.getString(R.string.toast_no_picker)) }
                        },
                        onPickVideo = {
                            runCatching { videoPicker.launch(arrayOf("video/*")) }
                                .onFailure { Actions.toast(context, context.getString(R.string.toast_no_picker)) }
                        },
                    )
                    SettingsScreen.Display -> DisplayScreen(
                        config = config,
                        store = store,
                        onBack = { screen = SettingsScreen.Main },
                    )
                    SettingsScreen.StatusBar -> StatusBarScreen(
                        config = config,
                        apps = apps,
                        store = store,
                        onBack = { screen = SettingsScreen.Main },
                    )
                    SettingsScreen.Apps -> AppsScreen(
                        apps = apps,
                        config = config,
                        onBack = { screen = SettingsScreen.Main },
                        onLaunch = { pkg -> Actions.launchApp(context, pkg) },
                        onAppInfo = { pkg -> Actions.openAppInfo(context, pkg) },
                        onUninstall = { pkg -> Actions.uninstall(context, pkg) },
                        onToggleHide = { pkg ->
                            scope.launch {
                                store.update { c ->
                                    c.copy(hidden = if (pkg in c.hidden) c.hidden - pkg else c.hidden + pkg)
                                }
                            }
                        },
                    )
                    SettingsScreen.Categories -> CategoriesScreen(
                        config = config,
                        apps = apps,
                        store = store,
                        onBack = { screen = SettingsScreen.Main },
                    )
                    SettingsScreen.About -> AboutScreen(
                        onBack = { screen = SettingsScreen.Main },
                    )
                    SettingsScreen.Launcher -> LauncherSettingsSubscreen(
                        config = config,
                        store = store,
                        onBack = { screen = SettingsScreen.Main },
                        onRerunWizard = onRerunWizard,
                    )
                }
                }
            }
        }
        }
    }
}

/* ------------------------------ main menu ------------------------------ */

@Composable
private fun MainScreen(
    onWallpaper: () -> Unit,
    onDisplay: () -> Unit,
    onStatusBar: () -> Unit,
    onApps: () -> Unit,
    onCategories: () -> Unit,
    onLauncher: () -> Unit,
    onAndroidSettings: () -> Unit,
    onAbout: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PageTitle(stringResource(R.string.settings_title))
        val f = initialFocus()

        // ---- Content: what shows on the home screen ----
        SectionLabel(stringResource(R.string.group_content))
        SettingsItem(
            selected = false, onClick = onApps,
            headlineContent = { Text(stringResource(R.string.item_app)) },
            supportingContent = { Text(stringResource(R.string.item_app_sub)) },
            leadingContent = { SettingsIcon(AppIcons.Apps, contentDescription = null) },
            modifier = Modifier.focusRequester(f),
        )
        SettingsItem(
            selected = false, onClick = onCategories,
            headlineContent = { Text(stringResource(R.string.item_sections)) },
            supportingContent = { Text(stringResource(R.string.item_sections_sub)) },
            leadingContent = { SettingsIcon(AppIcons.Folder, contentDescription = null) },
        )

        // ---- Appearance: how it looks ----
        SectionLabel(stringResource(R.string.group_appearance))
        SettingsItem(
            selected = false, onClick = onDisplay,
            headlineContent = { Text(stringResource(R.string.item_display)) },
            supportingContent = { Text(stringResource(R.string.item_display_sub)) },
            leadingContent = { SettingsIcon(AppIcons.Display, contentDescription = null) },
        )
        SettingsItem(
            selected = false, onClick = onWallpaper,
            headlineContent = { Text(stringResource(R.string.item_wallpaper)) },
            supportingContent = { Text(stringResource(R.string.item_wallpaper_sub)) },
            leadingContent = { SettingsIcon(AppIcons.Image, contentDescription = null) },
        )
        SettingsItem(
            selected = false, onClick = onStatusBar,
            headlineContent = { Text(stringResource(R.string.item_statusbar)) },
            supportingContent = { Text(stringResource(R.string.item_statusbar_sub)) },
            leadingContent = { SettingsIcon(AppIcons.Wifi, contentDescription = null) },
        )

        // ---- System ----
        SectionLabel(stringResource(R.string.group_system))
        SettingsItem(
            selected = false, onClick = onLauncher,
            headlineContent = { Text(stringResource(R.string.item_launcher_settings)) },
            supportingContent = { Text(stringResource(R.string.item_launcher_settings_sub)) },
            leadingContent = { Icon(painter = androidx.compose.ui.res.painterResource(R.drawable.ic_couch), contentDescription = null, modifier = Modifier.size(24.dp), tint = LocalContentColor.current) },
        )
        SettingsItem(
            selected = false, onClick = onAndroidSettings,
            headlineContent = { Text(stringResource(R.string.item_android_settings)) },
            supportingContent = { Text(stringResource(R.string.item_android_settings_sub)) },
            leadingContent = { SettingsIcon(AppIcons.Gear, contentDescription = null) },
        )
        SettingsItem(
            selected = false, onClick = onAbout,
            headlineContent = { Text(stringResource(R.string.item_about)) },
            leadingContent = { SettingsIcon(AppIcons.Info, contentDescription = null) },
        )
    }
}

/* ------------------------------ about ------------------------------ */

@Composable
private fun AboutScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PageTitle(stringResource(R.string.item_about))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(start = 27.dp, top = 4.dp),
        ) {
            Image(
                painter = androidx.compose.ui.res.painterResource(R.drawable.ic_couch),
                contentDescription = null,
                modifier = Modifier.size(44.dp),
            )
            Column {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                    color = SettingsColors.title,
                )
                Text(
                    text = "v" + (runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrDefault("?")),
                    style = MaterialTheme.typography.bodySmall,
                    color = SettingsColors.subtitle,
                )
            }
        }
        Text(
            stringResource(R.string.about_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = SettingsColors.subtitle,
            modifier = Modifier.padding(start = 27.dp, top = 12.dp),
        )

        SectionLabel(stringResource(R.string.about_foss))
        Text(
            stringResource(R.string.about_license),
            style = MaterialTheme.typography.bodyMedium,
            color = SettingsColors.subtitle,
            modifier = Modifier.padding(start = 27.dp),
        )
        // Source code
        Text(
            stringResource(R.string.about_source) + "  ·  " + stringResource(R.string.github_url),
            style = MaterialTheme.typography.bodyMedium,
            color = SettingsColors.iconGlyphFocused,
            modifier = Modifier.padding(start = 27.dp, top = 8.dp),
        )

        // Support — scannable QR to Buy me a coffee (Cookie script font; brand name, not localized)
        Text(
            stringResource(R.string.about_coffee),
            fontFamily = androidx.compose.ui.text.font.FontFamily(
                androidx.compose.ui.text.font.Font(R.font.cookie)
            ),
            fontSize = 30.sp,
            color = SettingsColors.title,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 14.dp),
        )
        Image(
            painter = androidx.compose.ui.res.painterResource(R.drawable.qr_coffee),
            contentDescription = stringResource(R.string.about_coffee),
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 6.dp)
                .size(150.dp)
                .clip(RoundedCornerShape(10.dp)),
        )

        Text(
            stringResource(R.string.about_thirdparty),
            style = MaterialTheme.typography.bodySmall,
            color = SettingsColors.subtitle,
            modifier = Modifier.padding(start = 27.dp, top = 14.dp, bottom = 8.dp),
        )
    }
}

/* ------------------------------ display ------------------------------ */

@Composable
private fun DisplayScreen(
    config: LauncherConfig,
    store: ConfigStore,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    fun update(transform: (LauncherConfig) -> LauncherConfig) {
        scope.launch { store.update(transform) }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PageTitle(stringResource(R.string.item_display))
        SectionLabel(stringResource(R.string.section_layout))
        val f = initialFocus()
        SelectorRow(
            modifier = Modifier.focusRequester(f),
            label = stringResource(R.string.view_mode),
            value = listOf(stringResource(R.string.mode_carousel), stringResource(R.string.mode_grid), stringResource(R.string.mode_dock))[config.layout.coerceIn(0, 2)],
            description = when (config.layout) {
                LAYOUT_GRID -> stringResource(R.string.mode_grid_desc)
                LAYOUT_DOCK -> stringResource(R.string.mode_dock_desc)
                else -> stringResource(R.string.mode_carousel_desc)
            },
            steps = 3,
            current = config.layout.coerceIn(0, 2),
            onSelect = { v -> update { it.copy(layout = v) } },
        )
        // Section names are only drawn in carousel/grid — hide the toggle in dock.
        if (config.layout != LAYOUT_DOCK) {
            SectionLabel(stringResource(R.string.section_elements))
            SettingsItem(
                selected = false,
                onClick = { update { it.copy(showCategoryNames = !it.showCategoryNames) } },
                headlineContent = { Text(stringResource(R.string.section_names_label)) },
                trailingContent = { Switch(checked = config.showCategoryNames) },
            )
        }
        SectionLabel(stringResource(R.string.section_size))
        run {
            val labels = listOf(stringResource(R.string.scale_auto), "75%", "90%", "100%", "115%", "130%")
            val idx = config.uiScale.coerceIn(0, labels.size - 1)
            SelectorRow(
                label = stringResource(R.string.ui_scale),
                value = labels[idx],
                description = if (idx == 0) stringResource(R.string.ui_scale_auto_sub) else null,
                steps = labels.size,
                current = idx,
                onSelect = { v -> update { it.copy(uiScale = v) } },
            )
        }
        SelectorRow(
            label = stringResource(R.string.roundness),
            value = listOf(stringResource(R.string.size_xs), stringResource(R.string.size_s), stringResource(R.string.size_n), stringResource(R.string.size_l), stringResource(R.string.size_xl))[config.cornerRadius.coerceIn(0, 4)],
            steps = 5,
            current = config.cornerRadius.coerceIn(0, 4),
            onSelect = { v -> update { it.copy(cornerRadius = v) } },
        )
        SelectorRow(
            label = stringResource(R.string.icon_size),
            value = listOf(stringResource(R.string.size_xs), stringResource(R.string.size_s), stringResource(R.string.size_n), stringResource(R.string.size_l), stringResource(R.string.size_xl))[config.iconScale.coerceIn(0, 4)],
            steps = 5,
            current = config.iconScale.coerceIn(0, 4),
            onSelect = { v -> update { it.copy(iconScale = v) } },
        )
        SelectorRow(
            label = stringResource(R.string.spacing),
            value = listOf(stringResource(R.string.size_xs), stringResource(R.string.size_s), stringResource(R.string.size_n), stringResource(R.string.size_l), stringResource(R.string.size_xl))[config.spacing.coerceIn(0, 4)],
            steps = 5,
            current = config.spacing.coerceIn(0, 4),
            onSelect = { v -> update { it.copy(spacing = v) } },
        )
    }
}

/**
 * D-pad selector row: Left/Right changes the value directly, OK cycles.
 * The dots show the position within the available steps.
 */
@Composable
private fun SelectorRow(
    label: String,
    value: String,
    steps: Int,
    current: Int,
    onSelect: (Int) -> Unit,
    description: String? = null,
    modifier: Modifier = Modifier,
) {
    val sub = if (description != null) "$value · $description" else value
    SettingsItem(
        selected = false,
        onClick = { onSelect((current + 1) % steps) },
        headlineContent = { Text(label) },
        supportingContent = { Text(sub) },
        modifier = modifier.onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (e.key) {
                Key.DirectionLeft -> { onSelect((current - 1 + steps) % steps); true }
                Key.DirectionRight -> { onSelect((current + 1) % steps); true }
                else -> false
            }
        },
    )
}

/* --------------- app list: icon + name launches, ⓘ = app info --------------- */

@Composable
private fun AppsScreen(
    apps: List<AppEntry>,
    config: LauncherConfig,
    onBack: () -> Unit,
    onLaunch: (String) -> Unit,
    onAppInfo: (String) -> Unit,
    onUninstall: (String) -> Unit,
    onToggleHide: (String) -> Unit,
) {
    val catNames = remember(config.categories) { config.categories.associate { it.id to it.name } }

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp).padding(bottom = 20.dp)) {
        PageTitle(stringResource(R.string.item_app), modifier = Modifier.padding(bottom = 4.dp))
        Text(
            stringResource(R.string.app_hint),
            style = MaterialTheme.typography.bodySmall,
            color = SettingsColors.subtitle,
            modifier = Modifier.padding(bottom = 12.dp, start = 27.dp),
        )
        val f = initialFocus()
        LazyColumn(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(apps.size, key = { apps[it].pkg }) { i ->
                val app = apps[i]
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        SettingsItem(
                            selected = false,
                            onClick = { onLaunch(app.pkg) },
                            modifier = if (i == 0) Modifier.focusRequester(f) else Modifier,
                            headlineContent = { Text(app.label) },
                            supportingContent = {
                                val names = AppRepository.sectionsOf(app, config)
                                    .mapNotNull { catNames[it] }
                                val suffix = if (app.pkg in config.hidden) " · " + stringResource(R.string.hidden_tag) else ""
                                Text(names.joinToString(" · ").ifEmpty { stringResource(R.string.no_section) } + suffix)
                            },
                            leadingContent = { AppThumb(app) },
                        )
                    }
                    val isHidden = app.pkg in config.hidden
                    SmallIconButton(
                        if (isHidden) AppIcons.Hide else AppIcons.Show,
                        stringResource(if (isHidden) R.string.menu_unhide else R.string.menu_hide),
                    ) { onToggleHide(app.pkg) }
                    SmallIconButton(AppIcons.Info, stringResource(R.string.menu_app_info)) { onAppInfo(app.pkg) }
                    SmallIconButton(AppIcons.Delete, stringResource(R.string.menu_uninstall)) { onUninstall(app.pkg) }
                }
            }
        }
    }
}

/** Small leading artwork for list rows: banner if the app ships one, else icon. */
@Composable
private fun AppThumb(app: AppEntry) {
    when {
        app.banner != null -> Image(
            bitmap = app.banner,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 52.dp, height = 30.dp)
                .clip(RoundedCornerShape(5.dp)),
        )
        app.icon != null -> Image(
            bitmap = app.icon,
            contentDescription = null,
            modifier = Modifier.size(30.dp),
        )
        else -> Box(
            Modifier
                .size(width = 52.dp, height = 30.dp)
                .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(5.dp))
        )
    }
}

/* ---------- section apps picker: vertical grid, checked = included ---------- */

@Composable
private fun SectionAppsDialog(
    cat: CategoryCfg,
    apps: List<AppEntry>,
    config: LauncherConfig,
    onToggle: (AppEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val dialogView = androidx.compose.ui.platform.LocalView.current
        androidx.compose.runtime.SideEffect {
            ((dialogView.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window)
                ?.setLayout(
                    android.view.WindowManager.LayoutParams.MATCH_PARENT,
                    android.view.WindowManager.LayoutParams.MATCH_PARENT,
                )
        }
        ScaledUi(uiScaleFactor(config.uiScale, androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
            Surface(
                shape = RoundedCornerShape(0.dp),
                colors = SurfaceDefaults.colors(containerColor = SettingsColors.pageBg),
                modifier = Modifier
                    .width(720.dp)
                    .fillMaxHeight()
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(0.dp),
                        clip = false,
                        ambientColor = SettingsColors.sheetShadow,
                        spotColor = SettingsColors.sheetShadow,
                    ),
            ) {
                Column(Modifier.fillMaxSize().padding(horizontal = 14.dp).padding(bottom = 20.dp)) {
                    PageTitle(cat.name)
                    Text(
                        stringResource(R.string.section_apps_sub),
                        style = MaterialTheme.typography.bodySmall,
                        color = SettingsColors.subtitle,
                        modifier = Modifier.padding(bottom = 12.dp, start = 27.dp),
                    )
                    val f = initialFocus()
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(apps.size, key = { apps[it].pkg }) { i ->
                            val app = apps[i]
                            val inSection = cat.id in AppRepository.sectionsOf(app, config)
                            Surface(
                                onClick = { onToggle(app) },
                                modifier = if (i == 0) Modifier.focusRequester(f) else Modifier,
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = Color.White.copy(alpha = 0.06f),
                                    focusedContainerColor = SettingsColors.focusPill,
                                    contentColor = SettingsColors.title,
                                    focusedContentColor = SettingsColors.titleFocused,
                                ),
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
                            ) {
                                Box(Modifier.fillMaxWidth().padding(8.dp)) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        if (app.banner != null) {
                                            Image(
                                                bitmap = app.banner,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .aspectRatio(16f / 9f)
                                                    .clip(RoundedCornerShape(6.dp)),
                                            )
                                        } else {
                                            Box(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .aspectRatio(16f / 9f)
                                                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(6.dp)),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                if (app.icon != null) {
                                                    Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.size(40.dp))
                                                }
                                            }
                                        }
                                        Text(
                                            app.label,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = SettingsColors.title,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(top = 6.dp),
                                        )
                                    }
                                    Box(Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                                        CheckMark(checked = inSection)
                                    }
                                }
                            }
                        }
                    }
                    Button(onClick = onDismiss, modifier = Modifier.padding(top = 12.dp, start = 27.dp)) {
                        Text(stringResource(R.string.done))
                    }
                }
            }
        }
        }
    }
}


/* ------------------------------ launcher settings ------------------------------ */

@Composable
private fun LauncherSettingsSubscreen(
    config: LauncherConfig,
    store: ConfigStore,
    onBack: () -> Unit,
    onRerunWizard: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var langScreen by remember { mutableStateOf(false) }

    if (langScreen) {
        LanguageScreen(config = config, store = store, onBack = { langScreen = false })
        return
    }

    fun saveConfig() {
        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                    dir.mkdirs()
                    File(dir, "CouchyBackup.json").writeText(format.encodeToString(LauncherConfig.serializer(), config))
                }
            }
            Actions.toast(context, "Saved to Downloads/CouchyBackup.json")
        }
    }

    val loadPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val loaded = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            format.decodeFromString(LauncherConfig.serializer(), String(input.readBytes()))
                        }
                    }.getOrNull()
                }
                if (loaded != null) {
                    store.update { loaded.copy(knownApps = config.knownApps, setupDone = true) }
                    Actions.toast(context, context.getString(R.string.toast_config_loaded))
                } else {
                    Actions.toast(context, context.getString(R.string.toast_config_bad))
                }
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PageTitle(stringResource(R.string.item_launcher_settings))
        SettingsItem(
            selected = false,
            onClick = { langScreen = true },
            headlineContent = { Text(stringResource(R.string.item_language)) },
            supportingContent = { Text(stringResource(R.string.item_language_sub)) },
            leadingContent = { SettingsIcon(AppIcons.Language, contentDescription = null) },
        )
        SettingsItem(
            selected = false,
            onClick = { saveConfig() },
            headlineContent = { Text(stringResource(R.string.item_save_config)) },
            supportingContent = { Text(stringResource(R.string.item_save_config_sub)) },
            leadingContent = { SettingsIcon(AppIcons.Save, contentDescription = null) },
        )
        SettingsItem(
            selected = false,
            onClick = { runCatching { loadPicker.launch(arrayOf("application/json", "*/*")) }.onFailure { Actions.toast(context, context.getString(R.string.toast_no_picker)) } },
            headlineContent = { Text(stringResource(R.string.item_load_config)) },
            supportingContent = { Text(stringResource(R.string.item_load_config_sub)) },
            leadingContent = { SettingsIcon(AppIcons.Folder, contentDescription = null) },
        )
        SettingsItem(
            selected = false,
            onClick = onRerunWizard,
            headlineContent = { Text(stringResource(R.string.rerun_wizard)) },
            supportingContent = { Text(stringResource(R.string.rerun_wizard_sub)) },
            leadingContent = { SettingsIcon(AppIcons.Play, contentDescription = null) },
        )
    }
}

/* ------------------------------ language ------------------------------ */

@Composable
private fun LanguageScreen(
    config: LauncherConfig,
    store: ConfigStore,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PageTitle(stringResource(R.string.item_language))
        val f = initialFocus()
        com.conreo.couchytv.data.LANGUAGES.forEachIndexed { i, code ->
            // Endonym: each language named in its own tongue (no per-language strings)
            val label = if (code.isEmpty()) stringResource(R.string.lang_system)
            else java.util.Locale(code).let { it.getDisplayName(it) }
                .replaceFirstChar { it.uppercase() }
            SettingsItem(
                selected = false,
                onClick = {
                    // Just persist it; LauncherApp mirrors it and recreates.
                    if (code != config.language) scope.launch {
                        store.update { it.copy(language = code) }
                    }
                },
                headlineContent = { Text(label) },
                trailingContent = { CheckMark(checked = config.language == code) },
                modifier = if (i == 0) Modifier.focusRequester(f) else Modifier,
            )
        }
    }
}

/* ------------------------------ status bar ------------------------------ */

@Composable
private fun StatusBarScreen(
    config: LauncherConfig,
    apps: List<AppEntry>,
    store: ConfigStore,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var pickVpn by remember { mutableStateOf(false) }
    val vpnLabel = apps.firstOrNull { it.pkg == config.vpnApp }?.label ?: stringResource(R.string.vpn_system)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PageTitle(stringResource(R.string.item_statusbar))
        val f = initialFocus()
        SettingsItem(
            selected = false,
            onClick = { scope.launch { store.update { it.copy(showStatusBar = !it.showStatusBar) } } },
            modifier = Modifier.focusRequester(f),
            headlineContent = { Text(stringResource(R.string.show_statusbar)) },
            supportingContent = { Text(stringResource(R.string.show_statusbar_sub)) },
            trailingContent = { Switch(checked = config.showStatusBar) },
        )
        SettingsItem(
            selected = false,
            onClick = { scope.launch { store.update { it.copy(statusBarGlass = !it.statusBarGlass) } } },
            headlineContent = { Text(stringResource(R.string.statusbar_glass)) },
            supportingContent = { Text(stringResource(R.string.statusbar_glass_sub)) },
            trailingContent = { Switch(checked = config.statusBarGlass) },
        )
        SettingsItem(
            selected = false,
            onClick = { scope.launch { store.update { it.copy(h24 = !it.h24) } } },
            headlineContent = { Text(stringResource(R.string.clock_24h)) },
            trailingContent = { Switch(checked = config.h24) },
        )
        run {
            val idx = config.dateFormat.coerceIn(0, DATE_FORMATS.size - 1)
            SelectorRow(
                label = stringResource(R.string.date_label),
                // Live preview of today's date in the selected format
                value = if (idx == 0) stringResource(R.string.off)
                else java.text.SimpleDateFormat(DATE_FORMATS[idx], java.util.Locale.getDefault())
                    .format(java.util.Date()),
                steps = DATE_FORMATS.size,
                current = idx,
                onSelect = { v -> scope.launch { store.update { it.copy(dateFormat = v) } } },
            )
        }
        SettingsItem(
            selected = false,
            onClick = { scope.launch { store.update { it.copy(showVpnButton = !it.showVpnButton) } } },
            headlineContent = { Text(stringResource(R.string.show_vpn_button)) },
            leadingContent = { SettingsIcon(AppIcons.Vpn, contentDescription = null) },
            trailingContent = { Switch(checked = config.showVpnButton) },
        )
        // Grayed out (and not focusable) while the VPN button is hidden
        SettingsItem(
            selected = false,
            enabled = config.showVpnButton,
            onClick = { pickVpn = true },
            headlineContent = { Text(stringResource(R.string.vpn_opens)) },
            supportingContent = { Text(vpnLabel) },
            leadingContent = { SettingsIcon(AppIcons.Vpn, contentDescription = null) },
        )
        SettingsItem(
            selected = false,
            onClick = { scope.launch { store.update { it.copy(showInputsButton = !it.showInputsButton) } } },
            headlineContent = { Text(stringResource(R.string.show_inputs_button)) },
            supportingContent = { Text(stringResource(R.string.show_inputs_button_sub)) },
            leadingContent = { SettingsIcon(AppIcons.Tv, contentDescription = null) },
            trailingContent = { Switch(checked = config.showInputsButton) },
        )
    }

    if (pickVpn) {
        Dialog(
            onDismissRequest = { pickVpn = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            val dialogView = androidx.compose.ui.platform.LocalView.current
            androidx.compose.runtime.SideEffect {
                ((dialogView.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window)
                    ?.setLayout(
                        android.view.WindowManager.LayoutParams.MATCH_PARENT,
                        android.view.WindowManager.LayoutParams.MATCH_PARENT,
                    )
            }
            ScaledUi(uiScaleFactor(config.uiScale, androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                Surface(
                    shape = RoundedCornerShape(0.dp),
                    colors = SurfaceDefaults.colors(containerColor = SettingsColors.pageBg),
                    modifier = Modifier
                        .width(557.dp)
                        .fillMaxHeight()
                        .shadow(
                            elevation = 8.dp,
                            shape = RoundedCornerShape(0.dp),
                            clip = false,
                            ambientColor = SettingsColors.sheetShadow,
                            spotColor = SettingsColors.sheetShadow,
                        ),
                ) {
                    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp).padding(bottom = 20.dp)) {
                        PageTitle(stringResource(R.string.vpn_dialog_title))
                        val fv = initialFocus()
                        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                SettingsItem(
                                    selected = false,
                                    onClick = {
                                        scope.launch { store.update { it.copy(vpnApp = "") } }
                                        pickVpn = false
                                    },
                                    headlineContent = { Text(stringResource(R.string.vpn_system)) },
                                    trailingContent = { CheckMark(checked = config.vpnApp.isEmpty()) },
                                    modifier = Modifier.focusRequester(fv),
                                )
                            }
                            items(apps.size, key = { apps[it].pkg }) { i ->
                                val app = apps[i]
                                SettingsItem(
                                    selected = false,
                                    onClick = {
                                        scope.launch { store.update { it.copy(vpnApp = app.pkg) } }
                                        pickVpn = false
                                    },
                                    headlineContent = { Text(app.label) },
                                    leadingContent = { AppThumb(app) },
                                    trailingContent = { CheckMark(checked = config.vpnApp == app.pkg) },
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

/* ------------------------------ sections ------------------------------ */

@Composable
private fun CategoriesScreen(
    config: LauncherConfig,
    apps: List<AppEntry>,
    store: ConfigStore,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var renameFor by remember { mutableStateOf<CategoryCfg?>(null) }
    var appsFor by remember { mutableStateOf<CategoryCfg?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PageTitle(stringResource(R.string.item_sections))
        val f = initialFocus()
        // Auto-filled "All apps" section: a single toggle, no per-app assignment.
        run {
            val hasAll = config.categories.any { it.id == AppRepository.ALL_APPS_ID }
            val allName = stringResource(R.string.cat_all_apps)
            SettingsItem(
                selected = false,
                onClick = {
                    scope.launch {
                        store.update { cfg ->
                            val rest = cfg.categories.filter { it.id != AppRepository.ALL_APPS_ID }
                            cfg.copy(
                                categories = if (hasAll) rest
                                else rest + CategoryCfg(AppRepository.ALL_APPS_ID, allName),
                            )
                        }
                    }
                },
                modifier = Modifier.focusRequester(f),
                headlineContent = { Text(allName) },
                supportingContent = { Text(stringResource(R.string.all_apps_sub)) },
                leadingContent = { SettingsIcon(AppIcons.Apps, contentDescription = null) },
                trailingContent = { Switch(checked = hasAll) },
            )
        }
        config.categories.forEachIndexed { i, cat ->
            if (cat.id == AppRepository.ALL_APPS_ID) return@forEachIndexed
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    SettingsItem(
                        selected = false,
                        onClick = { appsFor = cat },
                        headlineContent = { Text(cat.name) },
                        supportingContent = { Text(stringResource(R.string.section_row_sub)) },
                        leadingContent = { SettingsIcon(AppIcons.Folder, contentDescription = null) },
                    )
                }
                SmallIconButton(AppIcons.Pencil, stringResource(R.string.rename_section)) { renameFor = cat }
                SmallIconButton(AppIcons.Up, stringResource(R.string.cd_move_up)) {
                    if (i > 0) scope.launch {
                        store.update { cfg ->
                            val l = cfg.categories.toMutableList()
                            val tmp = l[i - 1]; l[i - 1] = l[i]; l[i] = tmp
                            cfg.copy(categories = l)
                        }
                    }
                }
                SmallIconButton(AppIcons.Down, stringResource(R.string.cd_move_down)) {
                    if (i < config.categories.size - 1) scope.launch {
                        store.update { cfg ->
                            val l = cfg.categories.toMutableList()
                            val tmp = l[i + 1]; l[i + 1] = l[i]; l[i] = tmp
                            cfg.copy(categories = l)
                        }
                    }
                }
                if (config.categories.size > 1) {
                    SmallIconButton(AppIcons.Delete, stringResource(R.string.cd_delete_section)) {
                        scope.launch {
                            store.update { cfg ->
                                cfg.copy(categories = cfg.categories.filter { it.id != cat.id })
                            }
                        }
                    }
                }
            }
        }
        val newSectionName = stringResource(R.string.new_section)
        SettingsItem(
            selected = false,
            onClick = {
                scope.launch {
                    store.update {
                        it.copy(
                            categories = it.categories +
                                CategoryCfg("c${System.currentTimeMillis()}", newSectionName)
                        )
                    }
                }
            },
            headlineContent = { Text(stringResource(R.string.add_section)) },
            leadingContent = { SettingsIcon(AppIcons.Add, contentDescription = null) },
        )
    }

    appsFor?.let { cat ->
        // Keep the dialog's category object in sync with the live config
        val liveCat = config.categories.firstOrNull { it.id == cat.id } ?: cat
        SectionAppsDialog(
            cat = liveCat,
            apps = apps,
            config = config,
            onToggle = { app ->
                scope.launch {
                    store.update { cfg ->
                        val current = AppRepository.sectionsOf(app, cfg)
                        val next = if (liveCat.id in current) current - liveCat.id
                        else current + liveCat.id
                        cfg.copy(sections = cfg.sections + (app.pkg to next))
                    }
                }
            },
            onDismiss = { appsFor = null },
        )
    }

    renameFor?.let { cat ->
        RenameDialog(
            initial = cat.name,
            onSave = { newName ->
                scope.launch {
                    store.update { cfg ->
                        cfg.copy(categories = cfg.categories.map {
                            if (it.id == cat.id) it.copy(name = newName) else it
                        })
                    }
                }
                renameFor = null
            },
            onDismiss = { renameFor = null },
        )
    }
}

/** Pencil dialog: the ONLY place with a text field, so D-pad browsing never lands in an editor. */
@Composable
private fun RenameDialog(
    initial: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val dialogView = androidx.compose.ui.platform.LocalView.current
        androidx.compose.runtime.SideEffect {
            ((dialogView.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window)
                ?.setLayout(
                    android.view.WindowManager.LayoutParams.MATCH_PARENT,
                    android.view.WindowManager.LayoutParams.MATCH_PARENT,
                )
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
            Surface(
                shape = RoundedCornerShape(0.dp),
                colors = SurfaceDefaults.colors(containerColor = SettingsColors.pageBg),
                modifier = Modifier
                    .width(557.dp)
                    .fillMaxHeight()
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(0.dp),
                        clip = false,
                        ambientColor = SettingsColors.sheetShadow,
                        spotColor = SettingsColors.sheetShadow,
                    ),
            ) {
                Column(Modifier.fillMaxSize().padding(horizontal = 14.dp).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    PageTitle(stringResource(R.string.rename_section))
                    val f = initialFocus()
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 27.dp)
                            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                    ) {
                        BasicTextField(
                            value = name,
                            onValueChange = { name = it },
                            singleLine = true,
                            textStyle = TextStyle(color = SettingsColors.title, fontSize = 18.7.sp),
                            cursorBrush = SolidColor(SettingsColors.iconGlyphFocused),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = { if (name.isNotBlank()) onSave(name.trim()) }
                            ),
                            modifier = Modifier.fillMaxWidth().focusRequester(f),
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(start = 27.dp),
                    ) {
                        Button(onClick = { if (name.isNotBlank()) onSave(name.trim()) }) { Text(stringResource(R.string.save)) }
                        Button(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    }
                }
            }
        }
    }
}

/* ------------------------------ wallpaper ------------------------------ */

/**
 * ONE row per background mode, checkmark = active. Selecting any mode turns
 * the others off. Two groups: Static (color, photo) and Video (aerials, video).
 */
@Composable
private fun WallpaperScreen(
    config: LauncherConfig,
    onSelectBuiltinAerials: () -> Unit,
    onSelectBuiltinSource: (Int) -> Unit,
    onSetScrim: (Int) -> Unit,
    onSetSpeed: (Int) -> Unit,
    onPreset: (Int) -> Unit,
    onPickPhoto: () -> Unit,
    onPickVideo: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PageTitle(stringResource(R.string.item_wallpaper))
        val f = initialFocus()
        val staticActive = !config.useBuiltinAerials &&
            !config.useVideoWallpaper && !config.useCustomWallpaper

        // Dimming — how much the wallpaper is darkened for icon readability.
        // Applies to any background; "Top & bottom" keeps the centre clear.
        run {
            val names = listOf(
                stringResource(R.string.dim_top_bottom),
                stringResource(R.string.dim_top),
                stringResource(R.string.dim_bottom),
                stringResource(R.string.dim_full),
                stringResource(R.string.dim_off),
            )
            val idx = config.scrimMode.coerceIn(0, names.size - 1)
            SelectorRow(
                modifier = Modifier.focusRequester(f),
                label = stringResource(R.string.dim_label),
                value = names[idx],
                steps = names.size,
                current = idx,
                onSelect = { v -> onSetScrim(v) },
            )
        }

        /* ---------------- STATIC: color gradient + your own photo ---------------- */
        SectionLabel(stringResource(R.string.wp_section_static))

        // Color gradient mode — checkmark like every other mode; the picker
        // below appears once it's active (mirrors the aerial collection row).
        val presetIdx = config.wallpaper.coerceIn(0, WALLPAPERS.size - 1)
        SettingsItem(
            selected = false, onClick = { onPreset(presetIdx) },
            headlineContent = { Text(stringResource(R.string.color_gradient)) },
            leadingContent = { SettingsIcon(AppIcons.Palette, contentDescription = null) },
            trailingContent = { Switch(checked = staticActive) },
        )
        if (staticActive) {
            val presetNames = listOf(
                stringResource(R.string.wp_midnight),
                stringResource(R.string.wp_aurora),
                stringResource(R.string.wp_sunset),
                stringResource(R.string.wp_deep),
                stringResource(R.string.wp_charcoal),
            )
            SelectorRow(
                label = stringResource(R.string.preset_label),
                value = presetNames[presetIdx],
                steps = WALLPAPERS.size,
                current = presetIdx,
                onSelect = { v -> onPreset(v) },
            )
        }

        // A photo of your own
        SettingsItem(
            selected = false, onClick = onPickPhoto,
            headlineContent = { Text(stringResource(R.string.pick_photo)) },
            supportingContent = { Text(stringResource(R.string.pick_photo_sub)) },
            leadingContent = { SettingsIcon(AppIcons.Image, contentDescription = null) },
            trailingContent = { Switch(checked = config.useCustomWallpaper) },
        )

        /* ---------------- VIDEO: built-in aerials + your own video ---------------- */
        SectionLabel(stringResource(R.string.wp_section_video))

        // Built-in aerial videos
        SettingsItem(
            selected = false, onClick = onSelectBuiltinAerials,
            headlineContent = { Text(stringResource(R.string.builtin_aerials)) },
            supportingContent = { Text(stringResource(R.string.builtin_aerials_sub)) },
            leadingContent = { SettingsIcon(AppIcons.Play, contentDescription = null) },
            trailingContent = { Switch(checked = config.useBuiltinAerials) },
        )
        if (config.useBuiltinAerials) {
            val sourceNames = listOf(
                stringResource(R.string.aerial_src_all),
                stringResource(R.string.aerial_src_apple),
                stringResource(R.string.aerial_src_amazon),
                stringResource(R.string.aerial_src_comm1),
                stringResource(R.string.aerial_src_comm2),
            )
            val idx = config.builtinSource.coerceIn(0, sourceNames.size - 1)
            SelectorRow(
                label = stringResource(R.string.aerial_src_label),
                value = sourceNames[idx],
                steps = sourceNames.size,
                current = idx,
                onSelect = { v -> onSelectBuiltinSource(v) },
            )
        }

        // A video file of your own
        SettingsItem(
            selected = false, onClick = onPickVideo,
            headlineContent = { Text(stringResource(R.string.pick_video)) },
            supportingContent = { Text(stringResource(R.string.pick_video_sub)) },
            leadingContent = { SettingsIcon(AppIcons.Image, contentDescription = null) },
            trailingContent = { Switch(checked = config.useVideoWallpaper) },
        )

        // Playback speed — applies to aerials and your own video.
        run {
            val labels = listOf("0.25×", "0.5×", "0.75×", "1×")
            val idx = config.videoSpeed.coerceIn(0, labels.size - 1)
            SelectorRow(
                label = stringResource(R.string.speed_label),
                value = labels[idx],
                steps = labels.size,
                current = idx,
                onSelect = { v -> onSetSpeed(v) },
            )
        }
    }
}

/* ------------------------------ widgets ------------------------------ */

/**
 * Every menu focuses its first item on entry (Back on the remote steps out,
 * so there are no on-screen Back buttons).
 */
@Composable
private fun initialFocus(): FocusRequester {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // Claim focus the moment the first item's node is attached (retry per
        // frame, stop on the first success) instead of after a fixed 300ms delay.
        // A late request would yank focus back to item 1 if the user had already
        // navigated down — the "quick navigation resets to the top" bug.
        repeat(30) {
            if (runCatching { fr.requestFocus() }.isSuccess) return@LaunchedEffect
            withFrameNanos {}
        }
    }
    return fr
}

@Composable
private fun PageTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 42.7.sp,
        fontWeight = FontWeight.Medium,
        color = SettingsColors.title,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(start = 27.dp, top = 32.dp, bottom = 16.dp),
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = SettingsColors.sectionLabel,
        letterSpacing = 0.5.sp,
        modifier = Modifier.padding(start = 27.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun CheckMark(checked: Boolean) {
    Box(
        Modifier
            .size(22.dp)
            .background(
                if (checked) SettingsColors.iconGlyphFocused else Color.Transparent,
                RoundedCornerShape(5.dp),
            )
            .border(
                width = 2.dp,
                color = if (checked) SettingsColors.iconGlyphFocused
                else SettingsColors.iconGlyph.copy(alpha = 0.6f),
                shape = RoundedCornerShape(5.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                AppIcons.Check,
                contentDescription = null,
                tint = Color.Black.copy(alpha = 0.8f),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * System-settings switch: muted slate track, large circular thumb that overhangs
 * the track, no gap. The ON state matches the reference screenshot; OFF is
 * derived with the same muted palette.
 */
@Composable
private fun Switch(checked: Boolean, modifier: Modifier = Modifier) {
    val trackWidth = 44.dp
    val trackHeight = 18.7.dp
    val thumbSize = 26.7.dp
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) trackWidth - thumbSize else 0.dp,
        label = "switchThumb",
    )
    Box(
        modifier = modifier.size(trackWidth, trackHeight),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (checked) SettingsColors.switchTrackOn else SettingsColors.switchTrackOff,
                    RoundedCornerShape(14.dp),
                )
        )
        Box(
            modifier = Modifier
                .size(thumbSize)
                .offset(x = thumbOffset)
                .background(
                    if (checked) SettingsColors.switchThumbOn else SettingsColors.switchThumbOff,
                    CircleShape,
                )
        )
    }
}

/**
 * System-settings icon: respects the LocalContentColor provided by [SettingsItem]
 * so idle glyphs are muted gray and focused glyphs turn accent blue. The
 * hand-defined [AppIcons] vectors have an explicit white fill, so tint must be
 * passed explicitly.
 */
@Composable
private fun SettingsIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        modifier = modifier,
        tint = LocalContentColor.current,
    )
}

/**
 * A system-TV-settings list row: transparent when idle, light pill with shadow
 * and 1.05× scale when focused, icon in a circular chip, text stacked under the
 * title. All metrics come from the measured reference.
 */
@Composable
internal fun SettingsItem(
    onClick: () -> Unit,
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    supportingContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable BoxScope.() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val shadowElevation by animateDpAsState(
        targetValue = if (focused) 24.dp else 0.dp,
        label = "settingsRowShadow",
    )

    Surface(
        onClick = onClick,
        enabled = enabled,
        selected = selected,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .then(
                if (focused) Modifier.shadow(
                    elevation = shadowElevation,
                    shape = RoundedCornerShape(10.7.dp),
                    clip = false,
                    ambientColor = Color(0x59000000),
                    spotColor = Color(0x59000000),
                ) else Modifier
            ),
        shape = SelectableSurfaceDefaults.shape(RoundedCornerShape(10.7.dp)),
        colors = SelectableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = SettingsColors.focusPill,
            contentColor = SettingsColors.title,
            focusedContentColor = SettingsColors.titleFocused,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = SettingsColors.title.copy(alpha = 0.58f),
        ),
        scale = SelectableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 81.dp)
                .padding(start = 27.dp, end = 40.dp)
                .alpha(if (enabled) 1f else 0.58f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (leadingContent != null) {
                Box(
                    modifier = Modifier
                        .size(53.dp)
                        .clip(CircleShape)
                        .background(
                            if (focused) SettingsColors.iconChipFocused else SettingsColors.iconChip,
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    CompositionLocalProvider(
                        LocalContentColor provides if (focused) SettingsColors.iconGlyphFocused else SettingsColors.iconGlyph
                    ) {
                        leadingContent()
                    }
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                ProvideTextStyle(
                    TextStyle(
                        color = if (focused) SettingsColors.titleFocused else SettingsColors.title,
                        fontSize = 18.7.sp,
                        fontWeight = FontWeight.Medium,
                    )
                ) {
                    headlineContent()
                }
                if (supportingContent != null) {
                    ProvideTextStyle(
                        TextStyle(
                            color = if (focused) SettingsColors.subtitleFocused else SettingsColors.subtitle,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Normal,
                        )
                    ) {
                        supportingContent()
                    }
                }
            }
            if (trailingContent != null) {
                Box(contentAlignment = Alignment.Center) {
                    trailingContent()
                }
            }
        }
    }
}

@Composable
private fun SmallIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.08f),
            focusedContainerColor = Color.White.copy(alpha = 0.25f),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.15f),
    ) {
        Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
            // label is the accessibility name announced by TalkBack.
            Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp))
        }
    }
}
