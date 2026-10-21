package com.justnpcx.starveil.feature.rules

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.justnpcx.starveil.core.model.Profile
import com.justnpcx.starveil.core.model.ProxyGroup
import com.justnpcx.starveil.core.model.RoutePreset
import com.justnpcx.starveil.core.model.Scene
import com.justnpcx.starveil.core.model.SceneTarget
import com.justnpcx.starveil.core.model.SceneTrigger
import com.justnpcx.starveil.core.model.displayName
import com.justnpcx.starveil.core.ui.components.StarveilCard
import com.justnpcx.starveil.core.ui.components.EmptyState
import com.justnpcx.starveil.core.ui.theme.StarveilSpacing

/**
 * Scenes: the network decides which policy is in force.
 *
 * The screen exists to make that legible. A scene is a trigger, a preset and
 * optionally a target; anything more would need a debugger, and the place
 * people debug this is a train with no signal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScenesScreen(
    onBack: () -> Unit,
    viewModel: ScenesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var editing by remember { mutableStateOf<Scene?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    var ssidReadable by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    // Both, because Android 12+ shows one dialog with two choices and a build
    // that asks for only the precise one is flagged by lint; the SSID still
    // needs the precise grant, so that is what the flag follows.
    val requestLocation = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        ssidReadable = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.scenes_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.scenes_back))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editing = null
                    showDialog = true
                },
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.scenes_add))
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (!ssidReadable) {
                // The rationale, in the place the feature lives: what the
                // permission is for, what happens without it, and no claim that
                // anything leaves the phone.
                StarveilCard(
                    modifier = Modifier.fillMaxWidth().padding(StarveilSpacing.lg),
                ) {
                    Column(modifier = Modifier.padding(StarveilSpacing.lg)) {
                        Text(
                            text = stringResource(R.string.scenes_permission_title),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = stringResource(R.string.scenes_permission_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = StarveilSpacing.xs),
                        )
                        TextButton(
                            onClick = {
                                requestLocation.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                    ),
                                )
                            },
                        ) {
                            Text(stringResource(R.string.scenes_permission_grant))
                        }
                    }
                }
            }

            if (state.isEmpty) {
                EmptyState(
                    title = stringResource(R.string.scenes_empty_title),
                    body = stringResource(R.string.scenes_empty_body),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(StarveilSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(StarveilSpacing.sm),
                ) {
                    items(state.scenes, key = { it.id }) { scene ->
                        SceneCard(
                            scene = scene,
                            profiles = state.profiles,
                            groups = state.groups,
                            onClick = {
                                editing = scene
                                showDialog = true
                            },
                            onDelete = { viewModel.delete(scene.id) },
                        )
                    }
                }
            }
        }
    }

    if (showDialog) {
        SceneDialog(
            initial = editing,
            profiles = state.profiles,
            groups = state.groups,
            ssidReadable = ssidReadable,
            onDismiss = { showDialog = false },
            onSave = { scene ->
                viewModel.save(scene)
                showDialog = false
            },
        )
    }
}

@Composable
private fun SceneCard(
    scene: Scene,
    profiles: List<Profile>,
    groups: List<ProxyGroup>,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    StarveilCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(StarveilSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = scene.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = sceneSummary(scene, profiles, groups),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = StarveilSpacing.xs),
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.scenes_delete),
                )
            }
        }
    }
}

@Composable
private fun sceneSummary(
    scene: Scene,
    profiles: List<Profile>,
    groups: List<ProxyGroup>,
): String {
    val trigger = when (val t = scene.trigger) {
        SceneTrigger.Mobile -> stringResource(R.string.scenes_trigger_mobile)
        is SceneTrigger.Wifi -> when {
            t.ssids.isEmpty() -> stringResource(R.string.scenes_any_wifi)
            else -> t.ssids.joinToString(" / ")
        }
    }
    val preset = stringResource(scene.preset.labelRes())
    val target = when (val t = scene.target) {
        null -> null
        is SceneTarget.Node -> profiles.firstOrNull { it.id == t.profileId }?.displayName()
        is SceneTarget.Group -> groups.firstOrNull { it.id == t.groupId }?.name
    }
    return if (target == null) "$trigger → $preset" else "$trigger → $preset · $target"
}

private fun RoutePreset.labelRes(): Int = when (this) {
    RoutePreset.BYPASS_MAINLAND -> R.string.rules_preset_title
    RoutePreset.GLOBAL_PROXY -> R.string.rules_preset_proxy_title
    RoutePreset.GLOBAL_DIRECT -> R.string.rules_preset_direct_title
}

@Composable
private fun SceneDialog(
    initial: Scene?,
    profiles: List<Profile>,
    groups: List<ProxyGroup>,
    ssidReadable: Boolean,
    onDismiss: () -> Unit,
    onSave: (Scene) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var mobile by remember {
        mutableStateOf(initial?.trigger is SceneTrigger.Mobile)
    }
    var ssids by remember {
        mutableStateOf(
            (initial?.trigger as? SceneTrigger.Wifi)?.ssids?.joinToString(", ").orEmpty(),
        )
    }
    var preset by remember { mutableStateOf(initial?.preset ?: RoutePreset.BYPASS_MAINLAND) }
    var target by remember { mutableStateOf(initial?.target) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(if (initial == null) R.string.scenes_new else R.string.scenes_edit),
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(StarveilSpacing.sm),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.scenes_name)) },
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(StarveilSpacing.sm)) {
                    FilterChip(
                        selected = !mobile,
                        onClick = { mobile = false },
                        enabled = ssidReadable,
                        label = { Text(stringResource(R.string.scenes_trigger_wifi)) },
                    )
                    FilterChip(
                        selected = mobile,
                        onClick = { mobile = true },
                        label = { Text(stringResource(R.string.scenes_trigger_mobile)) },
                    )
                }
                if (!ssidReadable) {
                    Text(
                        text = stringResource(R.string.scenes_ssid_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (!mobile) {
                    OutlinedTextField(
                        value = ssids,
                        onValueChange = { ssids = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.scenes_ssids)) },
                        supportingText = { Text(stringResource(R.string.scenes_ssids_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Text(
                    text = stringResource(R.string.scenes_preset),
                    style = MaterialTheme.typography.labelLarge,
                )
                listOf(
                    RoutePreset.BYPASS_MAINLAND,
                    RoutePreset.GLOBAL_PROXY,
                    RoutePreset.GLOBAL_DIRECT,
                ).forEach { option ->
                    RadioRow(
                        label = stringResource(option.labelRes()),
                        selected = preset == option,
                        onClick = { preset = option },
                    )
                }

                Text(
                    text = stringResource(R.string.scenes_target),
                    style = MaterialTheme.typography.labelLarge,
                )
                Column(modifier = Modifier.heightIn(max = 220.dp)) {
                    RadioRow(
                        label = stringResource(R.string.scenes_target_keep),
                        selected = target == null,
                        onClick = { target = null },
                    )
                    groups.forEach { group ->
                        RadioRow(
                            label = group.name,
                            selected = target == SceneTarget.Group(group.id),
                            onClick = { target = SceneTarget.Group(group.id) },
                        )
                    }
                    profiles.forEach { profile ->
                        RadioRow(
                            label = profile.displayName(),
                            selected = target == SceneTarget.Node(profile.id),
                            onClick = { target = SceneTarget.Node(profile.id) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        Scene(
                            id = initial?.id ?: System.currentTimeMillis(),
                            name = name.trim(),
                            trigger = if (mobile) {
                                SceneTrigger.Mobile
                            } else {
                                SceneTrigger.Wifi(
                                    ssids.split(',', '，')
                                        .map { it.trim() }
                                        .filter { it.isNotEmpty() },
                                )
                            },
                            preset = preset,
                            target = target,
                        ),
                    )
                },
            ) {
                Text(stringResource(R.string.scenes_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.scenes_cancel))
            }
        },
    )
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}
