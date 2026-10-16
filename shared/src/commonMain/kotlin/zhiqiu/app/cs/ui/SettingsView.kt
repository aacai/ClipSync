package zhiqiu.app.cs.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import zhiqiu.app.cs.core.AppLanguage
import zhiqiu.app.cs.core.AppLanguageSupport
import zhiqiu.app.cs.core.AppSettings
import zhiqiu.app.cs.core.ClipSyncEngine
import zhiqiu.app.cs.core.ThemeMode
import zhiqiu.app.cs.core.UpdateState
import zhiqiu.app.cs.core.appLanguageSupport
import zhiqiu.app.cs.files.humanSize
import zhiqiu.app.cs.resources.*

@Composable
internal fun SettingsView(
        model: AppModel,
        settings: AppSettings,
        roomCode: String?,
        onBack: () -> Unit,
        onLeave: () -> Unit,
) {
    val status by model.status.collectAsState()
    val localCount by model.localCount.collectAsState()
    val themeMode by settings.themeMode.collectAsState()
    val language by settings.language.collectAsState()
    val rememberRoom by settings.rememberRoom.collectAsState()
    val watchClipboard by settings.watchClipboard.collectAsState()
    val watchImages by settings.watchImages.collectAsState()
    val watchFiles by settings.watchFiles.collectAsState()
    val deleteExpired by settings.deleteExpired.collectAsState()
    val retentionDays by settings.retentionDays.collectAsState()
    val residentSync by model.residentSync.collectAsState()
    val launchAtLogin by model.launchAtLogin.collectAsState()
    val deviceName by model.deviceName.collectAsState()
    val nickname by settings.nickname.collectAsState()
    val canWatch = model.supportsClipboardAutoSync
    val languageSupport = appLanguageSupport
    val showLanguage = languageSupport != AppLanguageSupport.Unsupported

    val hazeState = rememberHazeState()
    val density = LocalDensity.current
    val rootFocus = remember { FocusRequester() }
    var barHeight by remember { mutableStateOf(0.dp) }
    var pickingTheme by remember { mutableStateOf(false) }
    var pickingLanguage by remember { mutableStateOf(false) }
    var pickingRetention by remember { mutableStateOf(false) }
    var editingNickname by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { rootFocus.requestFocus() }

    DisposableEffect(Unit) {
        val removeChords =
                installKeyChords { name, _, _ ->
                        if (name == "Escape" && !pickingTheme && !pickingLanguage && !pickingRetention && !editingNickname) {
                                onBack()
                                true
                        } else {
                                false
                        }
                }
        onDispose(removeChords)
    }

    CompositionLocalProvider(LocalHazeState provides hazeState) {
        Box(
                modifier =
                        Modifier.fillMaxSize()
                                .focusRequester(rootFocus)
                                .focusable()
                                .onPreviewKeyEvent { event ->
                                        event.type == KeyEventType.KeyDown &&
                                                event.key == Key.Escape &&
                                                !pickingTheme &&
                                                !pickingLanguage &&
                                                !pickingRetention &&
                                                !editingNickname &&
                                                run {
                                                        onBack()
                                                        true
                                                }
                                },
                contentAlignment = Alignment.TopCenter,
        ) {
            BackdropGlow()

            Box(modifier = Modifier.widthIn(max = 640.dp).fillMaxSize().padding(horizontal = 10.dp)) {
                Column(modifier = Modifier.fillMaxSize().hazeSource(hazeState)) {
                    LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = barHeight + 14.dp, bottom = 30.dp),
                            verticalArrangement = Arrangement.spacedBy(22.dp),
                    ) {
                        item(key = "appearance") {
                            SettingGroup(Res.string.settings_group_appearance) {
                                NavRow(
                                        title = Res.string.setting_theme,
                                        value = stringResource(themeLabel(themeMode)),
                                        onClick = { pickingTheme = true },
                                )
                                if (showLanguage) {
                                    RowDivider()
                                    Column {
                                        NavRow(
                                                title = Res.string.setting_language,
                                                value = stringResource(languageLabel(language)),
                                                onClick = { pickingLanguage = true },
                                        )
                                        if (languageSupport == AppLanguageSupport.AfterRestart) {
                                            GroupFootnote(Res.string.language_restart_hint)
                                        }
                                    }
                                }
                            }
                        }

                        item(key = "sync") {
                            SettingGroup(Res.string.settings_group_sync) {
                                ValueRow(
                                        title = Res.string.setting_room,
                                        value = roomCode ?: stringResource(Res.string.status_idle),
                                        detail = roomCode?.let { stringResource(statusLabel(status)) },
                                        monospace = roomCode != null,
                                        valueColor = roomColor(status, roomCode),
                                )
                                if (roomCode != null) {
                                    RowDivider()
                                    ActionRow(Res.string.action_leave_room, onLeave)
                                    if (model.supportsResidentSync) {
                                        RowDivider()
                                        SwitchRow(
                                                Res.string.setting_resident_sync,
                                                Res.string.setting_resident_sync_hint,
                                                residentSync,
                                                model::setResidentSync,
                                        )
                                    }
                                }
                                RowDivider()
                                SwitchRow(
                                        Res.string.setting_remember_room,
                                        Res.string.setting_remember_room_hint,
                                        rememberRoom,
                                        settings::setRememberRoom,
                                )
                                if (model.supportsLaunchAtLogin) {
                                    RowDivider()
                                    SwitchRow(
                                            Res.string.setting_launch_at_login,
                                            Res.string.setting_launch_at_login_hint,
                                            launchAtLogin,
                                            model::setLaunchAtLogin,
                                    )
                                }
                            }
                        }

                        if (canWatch) {
                            item(key = "clipboard") {
                                SettingGroup(Res.string.settings_group_clipboard) {
                                    SwitchRow(
                                            Res.string.setting_watch_clipboard,
                                            Res.string.setting_watch_clipboard_hint,
                                            watchClipboard,
                                            settings::setWatchClipboard,
                                    )
                                    val canReadImages = watchClipboard && model.supportsClipboardImageRead
                                    val canReadFiles = watchClipboard && model.supportsClipboardFilesRead
                                    if (canReadImages || canReadFiles) RowDivider()
                                    if (canReadImages) {
                                        SwitchRow(
                                                Res.string.setting_watch_images,
                                                Res.string.setting_watch_images_hint,
                                                watchImages,
                                                settings::setWatchImages,
                                        )
                                    }
                                    if (canReadFiles) {
                                        if (canReadImages) RowDivider()
                                        SwitchRow(
                                                Res.string.setting_watch_files,
                                                Res.string.setting_watch_files_hint,
                                                watchFiles,
                                                settings::setWatchFiles,
                                        )
                                    }
                                }
                            }
                        }

                        item(key = "about") {
                            SettingGroup(Res.string.settings_group_about) {
                                ValueRow(title = Res.string.setting_version, value = model.appVersion)
                                if (model.supportsUpdate) {
                                    RowDivider()
                                    UpdateRow(model)
                                }
                                RowDivider()
                                ValueRow(
                                        title = Res.string.local_history,
                                        value = pluralStringResource(Res.plurals.setting_item_count, localCount, localCount),
                                )
                                RowDivider()
                                SwitchRow(
                                        Res.string.setting_delete_expired,
                                        Res.string.setting_delete_expired_hint,
                                        deleteExpired,
                                        settings::setDeleteExpired,
                                )
                                if (deleteExpired) {
                                    RowDivider()
                                    NavRow(
                                            title = Res.string.setting_retention,
                                            value =
                                                    pluralStringResource(
                                                            Res.plurals.setting_retention_days,
                                                            retentionDays,
                                                            retentionDays,
                                                    ),
                                            onClick = { pickingRetention = true },
                                    )
                                }
                                RowDivider()
                                NavRow(
                                        title = Res.string.setting_nickname,
                                        value = deviceName,
                                        onClick = { editingNickname = true },
                                )
                                RowDivider()
                                ValueRow(title = Res.string.setting_os, value = model.deviceOs)
                            }
                        }
                    }
                }

                GlassPanel(
                        modifier =
                                Modifier.align(Alignment.TopStart)
                                        .fillMaxWidth()
                                        .onSizeChanged { barHeight = with(density) { it.height.toDp() } },
                        shape = MaterialTheme.shapes.large,
                ) {
                    Row(
                            modifier =
                                    Modifier.fillMaxWidth()
                                            .padding(start = 8.dp, end = 16.dp, top = 9.dp, bottom = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RowAction(AppIcons.ArrowBack, stringResource(Res.string.cd_back), onClick = onBack)
                        Spacer(Modifier.width(6.dp))
                        Text(
                                stringResource(Res.string.settings_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }

    if (pickingTheme) {
        ChoiceDialog(
                title = Res.string.setting_theme,
                options = ThemeMode.entries,
                labels = ThemeMode.entries.map { stringResource(themeLabel(it)) },
                selected = themeMode,
                onDismiss = { pickingTheme = false },
                onSelect = {
                    settings.setThemeMode(it)
                    pickingTheme = false
                },
        )
    }
    if (pickingLanguage) {
        ChoiceDialog(
                title = Res.string.setting_language,
                options = AppLanguage.entries,
                labels = AppLanguage.entries.map { stringResource(languageLabel(it)) },
                selected = language,
                onDismiss = { pickingLanguage = false },
                onSelect = {
                    settings.setLanguage(it)
                    pickingLanguage = false
                },
        )
    }
    if (pickingRetention) {
        ChoiceDialog(
                title = Res.string.setting_retention,
                options = AppSettings.RETENTION_CHOICES,
                labels =
                        AppSettings.RETENTION_CHOICES.map {
                            pluralStringResource(Res.plurals.setting_retention_days, it, it)
                        },
                selected = retentionDays,
                onDismiss = { pickingRetention = false },
                onSelect = {
                    settings.setRetentionDays(it)
                    pickingRetention = false
                },
        )
    }
    if (editingNickname) {
        TextEntryDialog(
                title = Res.string.setting_nickname,
                initial = nickname,
                placeholder = Res.string.nickname_placeholder,
                confirm = Res.string.save,
                supporting = Res.string.nickname_hint,
                allowBlank = true,
                minLines = 1,
                onDismiss = { editingNickname = false },
                onSubmit = {
                    model.setNickname(it)
                    editingNickname = false
                },
        )
    }
}

@Composable
private fun UpdateRow(model: AppModel) {
    val state by model.update.collectAsState()
    val busy = state is UpdateState.Checking || state is UpdateState.Downloading
    val asset = (state as? UpdateState.Available)?.asset
    RowScaffold(onClick = null) {
        Text(
                stringResource(Res.string.setting_update),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Text(
                updateLabel(state),
                style = MaterialTheme.typography.labelMedium,
                color = updateColor(state),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 220.dp),
        )
        Spacer(Modifier.width(2.dp))
        if (asset != null) {
            RowAction(AppIcons.Download, stringResource(Res.string.cd_download_update), enabled = !busy) {
                model.downloadUpdate()
            }
        }
        RowAction(AppIcons.Refresh, stringResource(Res.string.setting_update), enabled = !busy) {
            model.checkUpdates()
        }
    }
    if (state is UpdateState.Available && asset == null) {
        GroupFootnote(Res.string.update_no_asset)
    }
    if (state is UpdateState.Idle) {
        GroupFootnote(Res.string.update_hint)
    }
}

@Composable
private fun updateLabel(state: UpdateState): String =
        when (state) {
            UpdateState.Idle -> stringResource(Res.string.update_idle)
            UpdateState.Checking -> stringResource(Res.string.update_checking)
            is UpdateState.UpToDate -> stringResource(Res.string.update_current, state.version)
            is UpdateState.Available -> stringResource(Res.string.update_available, state.release.version)
            is UpdateState.Downloading ->
                    stringResource(
                            Res.string.update_downloading,
                            humanSize(state.received),
                            humanSize(state.asset.size),
                    )
            is UpdateState.Downloaded -> stringResource(Res.string.update_downloaded, state.name)
            is UpdateState.Failed -> stringResource(Res.string.update_failed, state.message)
        }

@Composable
private fun updateColor(state: UpdateState): Color {
    val dark = isAppDarkTheme()
    return when (state) {
        is UpdateState.Failed -> MaterialTheme.colorScheme.error
        is UpdateState.Available -> if (dark) PinAmberDark else PinAmber
        is UpdateState.Downloaded -> if (dark) SuccessGreenDark else SuccessGreen
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@Composable
private fun SettingGroup(title: StringResource, content: @Composable ColumnScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.large
    Column(Modifier.fillMaxWidth()) {
        Text(
                stringResource(title),
                style = MaterialTheme.typography.labelMedium,
                color = scheme.primary,
                modifier = Modifier.padding(start = 18.dp, bottom = 7.dp),
        )
        Column(
                modifier =
                        Modifier.fillMaxWidth()
                                .background(
                                        scheme.surface.copy(alpha = if (isAppDarkTheme()) 0.78f else 0.88f),
                                        shape,
                                )
                                .border(1.dp, glassBorder().copy(alpha = 0.5f), shape),
                content = content,
        )
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
    )
}

@Composable
private fun GroupFootnote(resource: StringResource) {
    Text(
            stringResource(resource),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 11.dp),
    )
}

private val RowHeight = 54.dp

@Composable
private fun RowScaffold(
        onClick: (() -> Unit)?,
        content: @Composable RowScope.() -> Unit,
) {
    Row(
            modifier =
                    Modifier.fillMaxWidth()
                            .heightIn(min = RowHeight)
                            .clip(MaterialTheme.shapes.small)
                            .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick))
                            .padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
    )
}

@Composable
private fun NavRow(
        title: StringResource,
        value: String,
        onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    RowScaffold(onClick) {
        Text(stringResource(title), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 200.dp),
        )
        Spacer(Modifier.width(2.dp))
        Icon(AppIcons.ChevronRight, contentDescription = null, tint = scheme.outline, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ValueRow(
        title: StringResource,
        value: String,
        detail: String? = null,
        monospace: Boolean = false,
        valueColor: Color? = null,
) {
    val scheme = MaterialTheme.colorScheme
    RowScaffold(onClick = null) {
        Text(stringResource(title), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        if (detail != null) {
            Text(detail, style = MaterialTheme.typography.labelMedium, color = valueColor ?: scheme.onSurfaceVariant)
            Spacer(Modifier.width(9.dp))
        }
        Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = valueColor ?: scheme.onSurfaceVariant,
                fontFamily = if (monospace) FontFamily.Monospace else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 200.dp),
        )
    }
}

@Composable
private fun ActionRow(
        title: StringResource,
        onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    RowScaffold(onClick) {
        Text(
                stringResource(title),
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.error,
                modifier = Modifier.weight(1f),
        )
        Icon(AppIcons.ChevronRight, contentDescription = null, tint = scheme.outline, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SwitchRow(
        title: StringResource,
        summary: StringResource,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
) {
    Column(
            modifier =
                    Modifier.fillMaxWidth()
                            .heightIn(min = RowHeight)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
        Spacer(Modifier.height(3.dp))
        Text(
                stringResource(summary),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun <T> ChoiceDialog(
        title: StringResource,
        options: List<T>,
        labels: List<String>,
        selected: T,
        onDismiss: () -> Unit,
        onSelect: (T) -> Unit,
) {
    AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(title), fontSize = 17.sp) },
            text = {
                Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    options.forEachIndexed { index, option ->
                        val active = option == selected
                        Row(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .clip(MaterialTheme.shapes.small)
                                                .clickable { onSelect(option) }
                                                .padding(vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = active, onClick = null)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                    labels[index],
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                    modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
            },
            shape = MaterialTheme.shapes.large,
    )
}

@Composable
private fun statusLabel(status: ClipSyncEngine.Status): StringResource =
        when (status) {
            is ClipSyncEngine.Status.Online -> Res.string.status_connected
            is ClipSyncEngine.Status.Connecting -> Res.string.connecting
            else -> Res.string.status_disconnected
        }

@Composable
private fun roomColor(status: ClipSyncEngine.Status, roomCode: String?): Color? {
    val dark = isAppDarkTheme()
    return when {
        status is ClipSyncEngine.Status.Online -> if (dark) SuccessGreenDark else SuccessGreen
        status is ClipSyncEngine.Status.Connecting -> if (dark) PinAmberDark else PinAmber
        roomCode == null -> null
        else -> MaterialTheme.colorScheme.error
    }
}

private fun themeLabel(mode: ThemeMode): StringResource =
        when (mode) {
            ThemeMode.System -> Res.string.theme_system
            ThemeMode.Light -> Res.string.theme_light
            ThemeMode.Dark -> Res.string.theme_dark
        }

private fun languageLabel(language: AppLanguage): StringResource =
        when (language) {
            AppLanguage.System -> Res.string.language_system
            AppLanguage.Chinese -> Res.string.language_chinese
            AppLanguage.English -> Res.string.language_english
        }
