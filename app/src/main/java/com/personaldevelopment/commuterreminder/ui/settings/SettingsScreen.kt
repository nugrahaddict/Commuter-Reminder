package com.personaldevelopment.commuterreminder.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.personaldevelopment.commuterreminder.R
import com.personaldevelopment.commuterreminder.data.AlarmSoundStore
import kotlinx.coroutines.launch

/** Alur nav dari drawer: beranda, pengaturan, editor stasiun. */
enum class AppSection {
    HOME,
    SETTINGS,
    STATION_EDITOR
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenStationEditor: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentSoundName by remember { mutableStateOf(currentSoundLabel(context)) }

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                AlarmSoundStore.saveSound(context, uri)
                currentSoundName = currentSoundLabel(context)
            }
        }
    }

    var showResetDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = context.getString(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = context.getString(R.string.nav_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = context.getString(R.string.settings_section_general),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )

            ListItem(
                headlineContent = {
                    Text(text = context.getString(R.string.settings_update_station))
                },
                supportingContent = {
                    Text(text = context.getString(R.string.settings_update_station_desc))
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenStationEditor() }
            )

            HorizontalDivider()

            Text(
                text = context.getString(R.string.settings_section_alarm),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )

            ListItem(
                headlineContent = {
                    Text(text = context.getString(R.string.settings_alarm_ringtone))
                },
                supportingContent = { Text(text = currentSoundName) },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Filled.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        audioPicker.launch(
                            arrayOf("audio/*", "application/ogg", "application/x-flac")
                        )
                    }
            )

            OutlinedButton(
                onClick = { showResetDialog = true },
                modifier = Modifier.align(Alignment.Start)
            ) {
                Text(text = context.getString(R.string.settings_ringtone_reset))
            }

            Text(
                text = context.getString(R.string.settings_ringtone_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(text = context.getString(R.string.settings_ringtone_reset_title)) },
            text = { Text(text = context.getString(R.string.settings_ringtone_reset_text)) },
            confirmButton = {
                Button(
                    onClick = {
                        showResetDialog = false
                        scope.launch {
                            AlarmSoundStore.clearSound(context)
                            currentSoundName = currentSoundLabel(context)
                        }
                    }
                ) {
                    Text(text = context.getString(R.string.settings_ringtone_reset_confirm))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showResetDialog = false }) {
                    Text(text = context.getString(R.string.cancel))
                }
            }
        )
    }
}

private fun currentSoundLabel(context: android.content.Context): String {
    val custom = AlarmSoundStore.soundUri(context)
    return if (custom != null) {
        queryDisplayName(context, custom)
            ?: context.getString(R.string.settings_ringtone_custom)
    } else {
        context.getString(R.string.settings_ringtone_default)
    }
}

private fun queryDisplayName(context: android.content.Context, uri: Uri): String? =
    runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    }.getOrNull()
