package com.justnpcx.starveil.feature.rules

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.justnpcx.starveil.core.ui.components.StarveilTextField
import com.justnpcx.starveil.core.model.Profile
import com.justnpcx.starveil.core.model.ProxyGroup
import com.justnpcx.starveil.core.model.RoutePreset
import com.justnpcx.starveil.core.model.Scene
import com.justnpcx.starveil.core.model.SceneTarget
import com.justnpcx.starveil.core.model.SceneTrigger
import com.justnpcx.starveil.core.model.displayName
import com.justnpcx.starveil.core.ui.components.ChipSurface
import com.justnpcx.starveil.core.ui.components.AddActionRow
import com.justnpcx.starveil.core.ui.components.StarveilAddFab
import com.justnpcx.starveil.core.ui.components.StarveilAddSheet
import com.justnpcx.starveil.core.ui.components.StarveilButton
import com.justnpcx.starveil.core.ui.components.StarveilCard
import com.justnpcx.starveil.core.ui.components.StarveilChip
import com.justnpcx.starveil.core.ui.components.EmptyState
import com.justnpcx.starveil.core.ui.icons.StarveilIcons
import com.justnpcx.starveil.core.ui.theme.StarveilColors
import com.justnpcx.starveil.core.ui.theme.StarveilSpacing
import com.justnpcx.starveil.core.ui.theme.StarveilShapes
import com.justnpcx.starveil.core.ui.theme.StarveilType

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
    /** A name from the Nodes page's "new scene" dialog, or null. */
    draftName: String? = null,
    viewModel: ScenesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var editing by remember { mutableStateOf<Scene?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    var addPanel by remember { mutableStateOf(false) }
    var sceneExpanded by remember { mutableStateOf(false) }
    var sceneName by remember { mutableStateOf("") }
    // Arrived from the Nodes page with a name already typed: open the existing
    // editor on a draft, so the trigger and preset are chosen in one place.
    LaunchedEffect(draftName) {
        val name = draftName?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        editing = Scene(
            id = 0L,
            name = name,
            trigger = SceneTrigger.Wifi(),
            preset = RoutePreset.BYPASS_MAINLAND,
        )
        showDialog = true
    }
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

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        // Transparent: the shell paints the page behind every screen.
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
                            style = StarveilType.headline,
                            color = StarveilColors.ink,
                        )
                        Text(
                            text = stringResource(R.string.scenes_permission_body),
                            style = StarveilType.label,
                            color = StarveilColors.ink2,
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
                // A definition is not an example. The card shows the shape of a
                // scene with a real preset, and the button is where the eye
                // already is — the FAB alone left the middle of the screen
                // saying nothing and the only way forward in a corner.
                SceneEmptyState(
                    onCreate = {
                        editing = null
                        showDialog = true
                    },
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = StarveilSpacing.lg,
                        top = StarveilSpacing.lg,
                        end = StarveilSpacing.lg,
                        bottom = StarveilSpacing.lg + WindowInsets.navigationBars
                            .asPaddingValues().calculateBottomPadding(),
                    ),
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

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(16.dp),
        ) {
            StarveilAddFab(onClick = { addPanel = true })
        }
    }

    StarveilAddSheet(visible = addPanel, onDismissRequest = { addPanel = false }) {
        Text(
            text = stringResource(R.string.add_panel_title_scene),
            style = StarveilType.title,
            color = StarveilColors.ink,
            modifier = Modifier.padding(bottom = StarveilSpacing.sm),
        )
        // A scene starts as a name. The trigger and the preset are chosen in
        // the existing editor, which is where they already live — the panel
        // only replaces the dialog that used to ask for the name.
        AddActionRow(
            icon = StarveilIcons.Scenes,
            title = stringResource(R.string.add_row_new_scene),
            subtitle = stringResource(R.string.add_row_new_scene_sub),
            expanded = sceneExpanded,
            onClick = { sceneExpanded = !sceneExpanded },
        )
        AnimatedVisibility(visible = sceneExpanded) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = StarveilSpacing.md),
                verticalArrangement = Arrangement.spacedBy(StarveilSpacing.sm),
            ) {
                StarveilTextField(
                    value = sceneName,
                    onValueChange = { sceneName = it },
                    label = stringResource(R.string.add_scene_name),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    StarveilButton(
                        text = stringResource(R.string.add_action_create),
                        enabled = sceneName.isNotBlank(),
                        onClick = {
                            val name = sceneName.trim()
                            addPanel = false
                            sceneExpanded = false
                            sceneName = ""
                            editing = Scene(
                                id = 0L,
                                name = name,
                                trigger = SceneTrigger.Wifi(),
                                preset = RoutePreset.BYPASS_MAINLAND,
                            )
                            showDialog = true
                        },
                    )
                }
            }
        }
    }

    if (showDialog) {
        SceneSheet(
            initial = editing,
            profiles = state.profiles,
            groups = state.groups,
            ssidReadable = ssidReadable,
            knownSsids = remember(state.scenes, ssidReadable, context) {
                buildList {
                    if (ssidReadable) currentSsid(context)?.let { add(it) }
                    state.scenes.forEach { scene ->
                        (scene.trigger as? SceneTrigger.Wifi)?.ssids?.forEach { add(it) }
                    }
                }.distinct()
            },
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
    StarveilCard(onClick = onClick) {
        Row(
            modifier = Modifier.padding(StarveilSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = scene.name, style = StarveilType.headline, color = StarveilColors.ink)
                Text(
                    text = sceneSummary(scene, profiles, groups),
                    style = StarveilType.label,
                    color = StarveilColors.ink2,
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

/**
 * The empty state: one example, one button.
 *
 * "A scene applies a route preset when the network matches" is a definition,
 * and a definition is not a starting point. The example uses the preset the
 * app actually ships as its default, so what it describes is what the first
 * scene will do.
 */
@Composable
private fun SceneEmptyState(onCreate: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(StarveilSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StarveilCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(StarveilSpacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.scenes_empty_title),
                    style = StarveilType.headline,
                    color = StarveilColors.ink,
                )
                Spacer(modifier = Modifier.height(StarveilSpacing.sm))
                Text(
                    text = stringResource(R.string.scenes_empty_body),
                    style = StarveilType.label,
                    color = StarveilColors.ink2,
                )
                Spacer(modifier = Modifier.height(StarveilSpacing.md))
                Text(
                    text = stringResource(R.string.scenes_empty_example),
                    style = StarveilType.tech,
                    color = StarveilColors.accentText,
                )
                Spacer(modifier = Modifier.height(StarveilSpacing.lg))
                StarveilButton(
                    text = stringResource(R.string.scenes_empty_action),
                    onClick = onCreate,
                )
            }
        }
    }
}

/**
 * Creating or editing a scene, as a sheet.
 *
 * A five-group form in a centred dialog put the keyboard on top of the save
 * button. A sheet that takes most of the screen, scrolls its fields and keeps
 * the buttons in a footer above the keyboard is the same form with room to
 * fill it in.
 *
 * "Keep the current node" is spelled out: "keep" alone did not say what was
 * being kept. Wi-Fi names can be picked from the networks this device already
 * knows about when the SSID is readable — the current network, and the ones
 * other scenes use — and the text field stays for anything else, which is also
 * the whole input when the permission is missing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SceneSheet(
    initial: Scene?,
    profiles: List<Profile>,
    groups: List<ProxyGroup>,
    ssidReadable: Boolean,
    knownSsids: List<String>,
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

    fun chosenSsids(): List<String> = ssids.split(',', '，').map { it.trim() }.filter { it.isNotEmpty() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = StarveilColors.card,
        shape = StarveilShapes.sheet,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.94f)
                .imePadding()
                .navigationBarsPadding(),
        ) {
            Text(
                text = stringResource(if (initial == null) R.string.scenes_new else R.string.scenes_edit),
                style = StarveilType.headline,
                color = StarveilColors.ink,
                modifier = Modifier.padding(
                    start = StarveilSpacing.pageMargin,
                    end = StarveilSpacing.pageMargin,
                    top = StarveilSpacing.xl,
                    bottom = StarveilSpacing.md,
                ),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = StarveilSpacing.pageMargin),
                verticalArrangement = Arrangement.spacedBy(StarveilSpacing.sm),
            ) {
                StarveilTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = stringResource(R.string.scenes_name),
                    placeholder = stringResource(R.string.scenes_name_example),
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(StarveilSpacing.sm)) {
                    StarveilChip(
                        label = stringResource(R.string.scenes_trigger_wifi),
                        selected = !mobile,
                        onClick = { mobile = false },
                        enabled = ssidReadable,
                        surface = ChipSurface.Dialog,
                    )
                    StarveilChip(
                        label = stringResource(R.string.scenes_trigger_mobile),
                        selected = mobile,
                        onClick = { mobile = true },
                        surface = ChipSurface.Dialog,
                    )
                }
                if (!ssidReadable) {
                    Text(
                        text = stringResource(R.string.scenes_ssid_unavailable),
                        style = StarveilType.label,
                        color = StarveilColors.errText,
                    )
                }
                if (!mobile) {
                    StarveilTextField(
                        value = ssids,
                        onValueChange = { ssids = it },
                        label = stringResource(R.string.scenes_ssids),
                        supportingText = stringResource(R.string.scenes_ssids_hint),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (ssidReadable && knownSsids.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.scenes_ssids_known),
                            style = StarveilType.tech,
                            color = StarveilColors.ink2,
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(StarveilSpacing.sm),
                        ) {
                            knownSsids.forEach { ssid ->
                                val selected = chosenSsids().any { it.equals(ssid, ignoreCase = true) }
                                StarveilChip(
                                    label = ssid,
                                    selected = selected,
                                    surface = ChipSurface.Dialog,
                                    onClick = {
                                        val current = chosenSsids()
                                        ssids = if (selected) {
                                            current.filterNot { it.equals(ssid, ignoreCase = true) }
                                                .joinToString(", ")
                                        } else {
                                            (current + ssid).joinToString(", ")
                                        }
                                    },
                                )
                            }
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.scenes_preset),
                    style = StarveilType.label,
                    color = StarveilColors.ink,
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
                    style = StarveilType.label,
                    color = StarveilColors.ink,
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
            // Outside the scroll area and above the keyboard: the one control
            // the form exists for is never the thing that has to be scrolled to.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = StarveilSpacing.pageMargin,
                        end = StarveilSpacing.pageMargin,
                        top = StarveilSpacing.md,
                        bottom = StarveilSpacing.lg,
                    ),
                horizontalArrangement = Arrangement.spacedBy(StarveilSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StarveilButton(
                    text = stringResource(R.string.scenes_save),
                    enabled = name.isNotBlank(),
                    onClick = {
                        onSave(
                            Scene(
                                id = initial?.id ?: System.currentTimeMillis(),
                                name = name.trim(),
                                trigger = if (mobile) {
                                    SceneTrigger.Mobile
                                } else {
                                    SceneTrigger.Wifi(chosenSsids())
                                },
                                preset = preset,
                                target = target,
                            ),
                        )
                    },
                )
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.scenes_cancel))
                }
            }
        }
    }
}

/**
 * The network the phone is on now, or null.
 *
 * Deprecated and still the only way to read an SSID without a system app:
 * Android 13's transport info carries it only when the caller holds location,
 * which is the same condition this checks. A failure returns null, which the
 * sheet reads as "no suggestions" rather than as an error.
 */
@SuppressLint("MissingPermission")
private fun currentSsid(context: android.content.Context): String? {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        return null
    }
    return runCatching {
        val manager = context.applicationContext.getSystemService(android.net.wifi.WifiManager::class.java)
            ?: return null
        @Suppress("DEPRECATION")
        val raw = manager.connectionInfo?.ssid ?: return null
        raw.removeSurrounding("\"").takeIf { it.isNotBlank() && it != android.net.wifi.WifiManager.UNKNOWN_SSID }
    }.getOrNull()
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Bound to the tokens rather than left to Material's default: the two
        // must not be able to disagree after a library bump.
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = StarveilColors.accent,
                unselectedColor = StarveilColors.ink2,
            ),
        )
        Text(text = label, style = StarveilType.label, color = StarveilColors.ink)
    }
}
