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
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import zhiqiu.app.cs.core.AppLanguage
import zhiqiu.app.cs.core.AppLanguageSupport
import zhiqiu.app.cs.core.AppSettings
import zhiqiu.app.cs.core.RecordMode
import zhiqiu.app.cs.core.ClipSyncEngine
import zhiqiu.app.cs.core.ScreenshotHotkey
import zhiqiu.app.cs.core.ThemeMode
import zhiqiu.app.cs.core.UpdateState
import zhiqiu.app.cs.core.appLanguageSupport
import zhiqiu.app.cs.core.looksLikeTypeName
import zhiqiu.app.cs.files.humanSize
import zhiqiu.app.cs.resources.*

@Composable
internal fun SettingsView(
        model: AppModel,
        settings: AppSettings,
        roomCode: String?,
        onBack: () -> Unit,
        onLeave: () -> Unit,
        onQuit: (() -> Unit)? = null,
) {
    val status by model.status.collectAsState()
    val localCount by model.localCount.collectAsState()
    val themeMode by settings.themeMode.collectAsState()
    val language by settings.language.collectAsState()
    val rememberRoom by settings.rememberRoom.collectAsState()
    val watchClipboard by settings.watchClipboard.collectAsState()
    val watchImages by settings.watchImages.collectAsState()
    val watchFiles by settings.watchFiles.collectAsState()
    val autoCopyIncoming by settings.autoCopyIncoming.collectAsState()
    val autoCopyIncomingFile by settings.autoCopyIncomingFile.collectAsState()
    val deleteExpired by settings.deleteExpired.collectAsState()
    val retentionDays by settings.retentionDays.collectAsState()
    val residentSync by model.residentSync.collectAsState()
    val launchAtLogin by model.launchAtLogin.collectAsState()
    val deviceName by model.deviceName.collectAsState()
    val nickname by settings.nickname.collectAsState()
    val screenshotHotkey by model.screenshotHotkey.collectAsState()
    val screenshotKeepWindow by settings.screenshotKeepWindow.collectAsState()
    val recordHotkey by settings.recordHotkey.collectAsState()
    val recordMode by settings.recordMode.collectAsState()
    val recordMic by settings.recordMic.collectAsState()
    val recordSystemAudio by settings.recordSystemAudio.collectAsState()
    val recordLongEdge by settings.recordLongEdge.collectAsState()
    val recordFps by settings.recordFps.collectAsState()
    val recordMicDevice by settings.recordMicDevice.collectAsState()
    val aboutOpen by model.aboutPrompt.collectAsState()
    val canWatch = model.supportsClipboardAutoSync
    val languageSupport = appLanguageSupport
    val showLanguage = languageSupport != AppLanguageSupport.Unsupported

    val density = LocalDensity.current
    val rootFocus = remember { FocusRequester() }
    var barHeight by remember { mutableStateOf(0.dp) }
    var pickingTheme by remember { mutableStateOf(false) }
    var pickingLanguage by remember { mutableStateOf(false) }
    var pickingRetention by remember { mutableStateOf(false) }
    var editingNickname by remember { mutableStateOf(false) }
    var editingRoom by remember { mutableStateOf(false) }
    var recordingHotkey by remember { mutableStateOf(false) }
    var recordingRecordHotkey by remember { mutableStateOf(false) }
    var pickingRecordMode by remember { mutableStateOf(false) }
    var pickingRecordSize by remember { mutableStateOf(false) }
    var pickingRecordFps by remember { mutableStateOf(false) }
    var pickingRecordMicDevice by remember { mutableStateOf(false) }
    var recordInputs by remember { mutableStateOf<List<RecordInput>>(emptyList()) }

    LaunchedEffect(Unit) { rootFocus.requestFocus() }

    // 进设置页就枚举一次：存着的设备名要能直接显示出来，不能等用户点开对话框才认。
    LaunchedEffect(Unit) { recordInputs = recordingInputs() }

    LaunchedEffect(roomCode) { if (roomCode != null) editingRoom = false }

    DisposableEffect(Unit) {
        val removeChords =
                installKeyChords { name, _, _ ->
                        if (name == "Escape" &&
                                        !pickingTheme &&
                                        !pickingLanguage &&
                                        !pickingRetention &&
                                        !editingNickname &&
                                        !editingRoom &&
                                        !aboutOpen &&
                                        !recordingHotkey &&
                                        !recordingRecordHotkey &&
                                        !pickingRecordMode &&
                                        !pickingRecordSize &&
                                        !pickingRecordFps &&
                                        !pickingRecordMicDevice) {
                                onBack()
                                true
                        } else {
                                false
                        }
                }
        onDispose(removeChords)
    }

    AppBackground(
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
                                            !editingRoom &&
                                            !aboutOpen &&
                                            !recordingHotkey &&
                                            !recordingRecordHotkey &&
                                            !pickingRecordMode &&
                                            !pickingRecordSize &&
                                            !pickingRecordFps &&
                                            !pickingRecordMicDevice &&
                                            run {
                                                    onBack()
                                                    true
                                            }
                            },
    ) {
        Box(
                modifier =
                        Modifier.align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .widthIn(max = 640.dp)
                                .fillMaxSize()
                                .padding(start = 6.dp, end = 6.dp, top = MacChromeInset.top),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = barHeight + 10.dp, bottom = 28.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
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

                        if (model.supportsRecording) {
                            item(key = "record") {
                                SettingGroup(Res.string.settings_group_record) {
                                    NavRow(
                                            title = Res.string.setting_record_hotkey,
                                            value = recordHotkey,
                                            onClick = { recordingRecordHotkey = true },
                                    )
                                    GroupFootnote(Res.string.setting_record_hint)
                                    RowDivider()
                                    NavRow(
                                            title = Res.string.setting_record_mode,
                                            value =
                                                    if (recordMode == RecordMode.Region) {
                                                        stringResource(Res.string.record_mode_region)
                                                    } else {
                                                        stringResource(Res.string.record_mode_screen)
                                                    },
                                            onClick = { pickingRecordMode = true },
                                    )
                                    RowDivider()
                                    NavRow(
                                            title = Res.string.setting_record_size,
                                            value =
                                                    when (recordLongEdge) {
                                                        1920 -> stringResource(Res.string.record_size_1080)
                                                        1280 -> stringResource(Res.string.record_size_720)
                                                        else -> stringResource(Res.string.record_size_native)
                                                    },
                                            onClick = { pickingRecordSize = true },
                                    )
                                    RowDivider()
                                    NavRow(
                                            title = Res.string.setting_record_fps,
                                            value = "$recordFps fps",
                                            onClick = { pickingRecordFps = true },
                                    )
                                    RowDivider()
                                    SwitchRow(
                                            Res.string.setting_record_mic,
                                            Res.string.setting_record_mic_hint,
                                            recordMic,
                                            settings::setRecordMic,
                                    )
                                    if (recordMic) {
                                        RowDivider()
                                        NavRow(
                                                title = Res.string.setting_record_mic_device,
                                                value = recordMicLabel(recordMicDevice, recordInputs),
                                                onClick = {
                                                    // 每次打开都重新枚举：中途插上的麦克风不该等到重启才出现
                                                    recordInputs = recordingInputs()
                                                    pickingRecordMicDevice = true
                                                },
                                        )
                                    }
                                    RowDivider()
                                    SwitchRow(
                                            Res.string.setting_record_system,
                                            Res.string.setting_record_system_hint,
                                            recordSystemAudio,
                                            settings::setRecordSystemAudio,
                                    )
                                }
                            }
                        }

                        if (model.supportsScreenshot) {
                            item(key = "screenshot") {
                                SettingGroup(Res.string.settings_group_screenshot) {
                                    NavRow(
                                            title = Res.string.setting_screenshot_hotkey,
                                            value = screenshotHotkey,
                                            onClick = { recordingHotkey = true },
                                    )
                                    GroupFootnote(Res.string.setting_screenshot_hint)
                                    RowDivider()
                                    SwitchRow(
                                            Res.string.setting_screenshot_keep_window,
                                            Res.string.setting_screenshot_keep_window_hint,
                                            screenshotKeepWindow,
                                            settings::setScreenshotKeepWindow,
                                    )
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
                                        onClick = { editingRoom = true },
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
                                    RowDivider()
                                    SwitchRow(
                                            Res.string.setting_auto_copy,
                                            Res.string.setting_auto_copy_hint,
                                            autoCopyIncoming,
                                            settings::setAutoCopyIncoming,
                                    )
                                    // 只有桌面端能把文件写成 javaFileList，手机上这颗开关点了没作用
                                    if (model.supportsClipboardFiles) {
                                        RowDivider()
                                        SwitchRow(
                                                Res.string.setting_auto_copy_file,
                                                Res.string.setting_auto_copy_file_hint,
                                                autoCopyIncomingFile,
                                                settings::setAutoCopyIncomingFile,
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
                                RowDivider()
                                NavRow(
                                        title = Res.string.setting_about,
                                        value = stringResource(Res.string.setting_about_hint),
                                        onClick = model::requestAbout,
                                )
                                if (onQuit != null) {
                                    RowDivider()
                                    ActionRow(Res.string.app_quit, onQuit)
                                }
                            }
                        }
                    }
                }

                GlassCard(
                        modifier =
                                Modifier.align(Alignment.TopStart)
                                        .fillMaxWidth()
                                        .onSizeChanged { barHeight = with(density) { it.height.toDp() } },
                        shape = RoundedCornerShape(20.dp),
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
    if (editingRoom) {
        ShareDialog(model, onDismiss = { editingRoom = false })
    }
    if (recordingHotkey) {
        HotkeyDialog(
                title = Res.string.setting_screenshot_hotkey,
                current = screenshotHotkey,
                onDismiss = { recordingHotkey = false },
                onPick = { spec -> model.setScreenshotHotkey(spec) },
        )
    }
    if (recordingRecordHotkey) {
        HotkeyDialog(
                title = Res.string.setting_record_hotkey,
                current = recordHotkey,
                onDismiss = { recordingRecordHotkey = false },
                onPick = { spec ->
                    val saved = model.setRecordHotkey(spec)
                    if (saved) recordingRecordHotkey = false
                    saved
                },
        )
    }
    if (pickingRecordMode) {
        ChoiceDialog(
                title = Res.string.setting_record_mode,
                options = RecordMode.entries,
                labels =
                        listOf(
                                stringResource(Res.string.record_mode_region),
                                stringResource(Res.string.record_mode_screen),
                        ),
                selected = recordMode,
                onDismiss = { pickingRecordMode = false },
                onSelect = {
                    settings.setRecordMode(it)
                    pickingRecordMode = false
                },
        )
    }
    if (pickingRecordSize) {
        ChoiceDialog(
                title = Res.string.setting_record_size,
                options = listOf(0, 1920, 1280),
                labels =
                        listOf(
                                stringResource(Res.string.record_size_native),
                                stringResource(Res.string.record_size_1080),
                                stringResource(Res.string.record_size_720),
                        ),
                selected = if (recordLongEdge == 1920 || recordLongEdge == 1280) recordLongEdge else 0,
                onDismiss = { pickingRecordSize = false },
                onSelect = {
                    settings.setRecordLongEdge(it)
                    pickingRecordSize = false
                },
        )
    }
    if (pickingRecordFps) {
        ChoiceDialog(
                title = Res.string.setting_record_fps,
                options = listOf(60, 30, 15),
                labels = listOf("60 fps", "30 fps", "15 fps"),
                selected = if (recordFps == 60 || recordFps == 15) recordFps else 30,
                onDismiss = { pickingRecordFps = false },
                onSelect = {
                    settings.setRecordFps(it)
                    pickingRecordFps = false
                },
        )
    }
    if (pickingRecordMicDevice) {
        val ids = listOf("") + recordInputs.map { it.id }
        ChoiceDialog(
                title = Res.string.setting_record_mic_device,
                options = ids,
                labels =
                        listOf(stringResource(Res.string.record_mic_default)) +
                                recordInputs.map { it.name },
                selected = recordMicDevice.takeIf { it in ids } ?: "",
                onDismiss = { pickingRecordMicDevice = false },
                onSelect = {
                    settings.setRecordMicDevice(it)
                    pickingRecordMicDevice = false
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
        if (model.updateBrowseUrl.isNotBlank()) {
            RowAction(AppIcons.OpenInNew, stringResource(Res.string.update_open_browser)) {
                model.openUpdateInBrowser()
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
            is UpdateState.Failed ->
                    if (state.message.isBlank() || looksLikeTypeName(state.message)) {
                        stringResource(Res.string.fb_generic_failed)
                    } else {
                        stringResource(Res.string.update_failed, state.message)
                    }
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
private fun SettingGroup(title: StringResource, rows: @Composable ColumnScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        Text(
                stringResource(title),
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 14.dp, bottom = 6.dp),
        )
        GlassCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth()) { rows() }
        }
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
            modifier = Modifier.padding(horizontal = 14.dp),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f),
    )
}

@Composable
private fun GroupFootnote(resource: StringResource) {
    Text(
            stringResource(resource),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
    )
}

private val RowHeight = 48.dp

@Composable
private fun RowScaffold(
        onClick: (() -> Unit)?,
        content: @Composable RowScope.() -> Unit,
) {
    Row(
            modifier =
                    Modifier.fillMaxWidth()
                            .heightIn(min = RowHeight)
                            .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
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
        onClick: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    RowScaffold(onClick = onClick) {
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
        if (onClick != null) {
            Spacer(Modifier.width(2.dp))
            Icon(AppIcons.ChevronRight, contentDescription = null, tint = scheme.outline, modifier = Modifier.size(18.dp))
        }
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
                            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
        Spacer(Modifier.height(2.dp))
        Text(
                stringResource(summary),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
        )
    }
}

/** 当前录音设备名；存着的设备拔掉了就按系统默认显示，和原生层的回退一致。 */
@Composable
private fun recordMicLabel(device: String, inputs: List<RecordInput>): String {
    if (device.isBlank()) return stringResource(Res.string.record_mic_default)
    return inputs.firstOrNull { it.id == device }?.name
            ?: stringResource(Res.string.record_mic_default)
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
    GlassAlertDialog(
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
    )
}

/** 录快捷键的弹窗：按下即生效，所以没有确认按钮，取消或 Escape 就退回原来的键。 */
@Composable
private fun HotkeyDialog(
        title: StringResource,
        current: String,
        onDismiss: () -> Unit,
        onPick: (String) -> Boolean,
) {
    var invalid by remember { mutableStateOf(false) }
    var rejected by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { focus.requestFocus() }

    GlassAlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(title), fontSize = 17.sp) },
            text = {
                Column(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .focusRequester(focus)
                                        .focusable()
                                        .onPreviewKeyEvent { event ->
                                                if (event.type != KeyEventType.KeyDown) {
                                                        false
                                                } else if (event.key == Key.Escape) {
                                                        onDismiss()
                                                        true
                                                } else {
                                                        // 每按一次都先把两条提示清空：否则上一次的失败会一直挂在这儿
                                                        val spec = chordOf(event)
                                                        rejected = null
                                                        invalid = spec == null
                                                        when {
                                                            spec == null -> Unit
                                                            onPick(spec) -> onDismiss()
                                                            else -> rejected = spec
                                                        }
                                                        true
                                                }
                                        },
                ) {
                    Text(current, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(10.dp))
                    val rejectedSpec = rejected
                    val failed = rejectedSpec != null || invalid
                    Text(
                            when {
                                rejectedSpec != null ->
                                        stringResource(Res.string.fb_hotkey_failed, rejectedSpec)
                                invalid ->
                                        stringResource(Res.string.setting_screenshot_record_invalid)
                                else -> stringResource(Res.string.setting_screenshot_record_hint)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color =
                                    if (failed) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
            },
    )
}

/** 一次按键 → 「Command+Shift+A」这种组合串；裸键和认不出的键都返回 null。 */
private fun chordOf(event: KeyEvent): String? {
    val key = chordKeyName(event.key) ?: return null
    return ScreenshotHotkey.of(
                    key,
                    command = event.isMetaPressed,
                    control = event.isCtrlPressed,
                    alt = event.isAltPressed,
                    shift = event.isShiftPressed,
            )
            ?.toString()
}

/** 键名用 Rust 端那套 token，两端的同一条「Alt+A」才是同一个键。 */
private fun chordKeyName(key: Key): String? =
        when (key) {
            Key.A -> "A"
            Key.B -> "B"
            Key.C -> "C"
            Key.D -> "D"
            Key.E -> "E"
            Key.F -> "F"
            Key.G -> "G"
            Key.H -> "H"
            Key.I -> "I"
            Key.J -> "J"
            Key.K -> "K"
            Key.L -> "L"
            Key.M -> "M"
            Key.N -> "N"
            Key.O -> "O"
            Key.P -> "P"
            Key.Q -> "Q"
            Key.R -> "R"
            Key.S -> "S"
            Key.T -> "T"
            Key.U -> "U"
            Key.V -> "V"
            Key.W -> "W"
            Key.X -> "X"
            Key.Y -> "Y"
            Key.Z -> "Z"
            Key.Zero -> "0"
            Key.One -> "1"
            Key.Two -> "2"
            Key.Three -> "3"
            Key.Four -> "4"
            Key.Five -> "5"
            Key.Six -> "6"
            Key.Seven -> "7"
            Key.Eight -> "8"
            Key.Nine -> "9"
            Key.F1 -> "F1"
            Key.F2 -> "F2"
            Key.F3 -> "F3"
            Key.F4 -> "F4"
            Key.F5 -> "F5"
            Key.F6 -> "F6"
            Key.F7 -> "F7"
            Key.F8 -> "F8"
            Key.F9 -> "F9"
            Key.F10 -> "F10"
            Key.F11 -> "F11"
            Key.F12 -> "F12"
            Key.Spacebar -> "Space"
            Key.Enter -> "Enter"
            Key.Tab -> "Tab"
            Key.Backspace -> "Backspace"
            Key.Delete -> "Delete"
            Key.MoveHome -> "Home"
            Key.MoveEnd -> "End"
            Key.PageUp -> "PageUp"
            Key.PageDown -> "PageDown"
            Key.DirectionUp -> "ArrowUp"
            Key.DirectionDown -> "ArrowDown"
            Key.DirectionLeft -> "ArrowLeft"
            Key.DirectionRight -> "ArrowRight"
            Key.Minus -> "Minus"
            Key.Equals -> "Equal"
            Key.LeftBracket -> "BracketLeft"
            Key.RightBracket -> "BracketRight"
            Key.Backslash -> "Backslash"
            Key.Semicolon -> "Semicolon"
            Key.Apostrophe -> "Quote"
            Key.Comma -> "Comma"
            Key.Period -> "Period"
            Key.Slash -> "Slash"
            Key.Grave -> "Backquote"
            else -> null
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
