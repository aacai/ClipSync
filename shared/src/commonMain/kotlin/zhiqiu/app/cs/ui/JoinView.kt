package zhiqiu.app.cs.ui

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import org.jetbrains.compose.resources.stringResource
import zhiqiu.app.cs.core.AppSettings
import zhiqiu.app.cs.core.ClipSyncEngine
import zhiqiu.app.cs.resources.*

@Composable
internal fun JoinView(model: AppModel, settings: AppSettings, onBrowseOffline: () -> Unit) {
    val roomCode by model.roomCode.collectAsState()
    val password by model.password.collectAsState()
    val connecting by model.connecting.collectAsState()
    val failure by model.failure.collectAsState()
    val status by model.status.collectAsState()
    val hazeState = rememberHazeState()
    val codeFocus = remember { FocusRequester() }
    var settingsOpen by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { codeFocus.requestFocus() }

    CompositionLocalProvider(LocalHazeState provides hazeState) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            BackdropGlow(modifier = Modifier.hazeSource(hazeState))

            GlassPanel(
                    modifier = Modifier.widthIn(max = 430.dp).fillMaxWidth().padding(horizontal = 20.dp),
                    shape = MaterialTheme.shapes.large,
                    alpha = 0.72f,
            ) {
                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(AppIcons.Clipboard, size = 38.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                    "ClipSync",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                            )
                            Text(
                                    stringResource(Res.string.tagline),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Tip(stringResource(Res.string.cd_settings)) {
                            IconButton(onClick = { settingsOpen = true }) {
                                Icon(
                                        AppIcons.Settings,
                                        contentDescription = stringResource(Res.string.cd_settings),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(22.dp))
                    SectionLabel(Res.string.room_code)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                                value = roomCode,
                                onValueChange = model::updateRoomCode,
                                singleLine = true,
                                enabled = !connecting,
                                leadingIcon = { FieldIcon(AppIcons.Key) },
                                keyboardOptions =
                                        KeyboardOptions(
                                                keyboardType = KeyboardType.Ascii,
                                                capitalization = KeyboardCapitalization.Characters,
                                        ),
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.weight(1f).focusRequester(codeFocus),
                        )
                        Tip(stringResource(Res.string.cd_random_room)) {
                            IconButton(onClick = model::regenerateRoomCode, enabled = !connecting) {
                                Icon(
                                        AppIcons.Refresh,
                                        contentDescription = stringResource(Res.string.cd_random_room),
                                )
                            }
                        }
                    }
                    Text(
                            stringResource(Res.string.room_code_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                    )

                    Spacer(Modifier.height(14.dp))
                    SectionLabel(Res.string.room_password)
                    OutlinedTextField(
                            value = password,
                            onValueChange = model::updatePassword,
                            singleLine = true,
                            enabled = !connecting,
                            placeholder = { Text(stringResource(Res.string.room_password_placeholder)) },
                            leadingIcon = { FieldIcon(AppIcons.Lock) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(20.dp))
                    Button(
                            onClick = model::connect,
                            enabled = !connecting && roomCode.isNotBlank(),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                    ) {
                        if (connecting) {
                            CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(stringResource(Res.string.connecting))
                        } else {
                            Icon(AppIcons.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(Res.string.enter_room))
                        }
                    }

                    val failureText = failure?.let { feedbackText(it) }
                            ?: (status as? ClipSyncEngine.Status.Offline)?.reason
                    if (!failureText.isNullOrBlank()) {
                        InlineMessage(AppIcons.Warning, failureText, isError = true, modifier = Modifier.padding(top = 12.dp))
                    }

                    Column(
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (!model.supportsClipboardAutoSync) {
                            Text(
                                    stringResource(Res.string.hint_manual_send),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                            )
                        }
                        TextButton(onClick = onBrowseOffline, contentPadding = PaddingValues(horizontal = 4.dp)) {
                            Text(stringResource(Res.string.browse_offline), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    if (settingsOpen) {
        SettingsDialog(settings, onDismiss = { settingsOpen = false })
    }
}
