package com.justnpcx.starveil.feature.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.justnpcx.starveil.core.ui.R as CoreR
import com.justnpcx.starveil.core.ui.components.ChainProxySheet
import com.justnpcx.starveil.core.ui.components.StarveilButton
import com.justnpcx.starveil.core.ui.icons.StarveilIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import com.justnpcx.starveil.core.ui.components.StarveilTopAppBar
import com.justnpcx.starveil.core.ui.components.StarveilTextField
import com.justnpcx.starveil.core.ui.components.StarveilChip
import com.justnpcx.starveil.core.ui.components.StarveilSwitch
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.justnpcx.starveil.core.model.Profile
import com.justnpcx.starveil.core.model.ProfileType
import com.justnpcx.starveil.core.model.SecurityMode
import com.justnpcx.starveil.core.model.TransportType
import com.justnpcx.starveil.core.model.TuicCongestionControl
import com.justnpcx.starveil.core.model.TuicUdpRelayMode
import com.justnpcx.starveil.core.model.XhttpMode
import com.justnpcx.starveil.core.ui.theme.StarveilColors
import com.justnpcx.starveil.core.ui.theme.StarveilSpacing
import com.justnpcx.starveil.core.ui.theme.StarveilType

/**
 * The node editor, shared by all seven protocols.
 *
 * Which blocks appear is decided by [EditorForm]'s `supports*` properties, not
 * by a `when` over the protocol in the layout — that is what keeps this from
 * being seven screens. Only the protocol-specific section switches on type.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditorScreen(
    type: ProfileType,
    profileId: Long = 0L,
    onDone: () -> Unit = {},
    viewModel: ProfileEditorViewModel = hiltViewModel(),
) {
    LaunchedEffect(type, profileId) { viewModel.load(type, profileId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showChainEditor by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.saved) { if (state.saved) onDone() }

    val form = state.form
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            StarveilTopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (profileId == 0L) R.string.editor_title_new else R.string.editor_title_edit,
                            type.displayName(),
                        ),
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onDone) { Text(stringResource(R.string.editor_cancel)) }
                },
            )
        },
    ) { padding ->
        if (form == null) {
            Text(
                text = stringResource(
                    if (state.missing) R.string.editor_missing else R.string.editor_title_new,
                    type.displayName(),
                ),
                color = StarveilColors.ink,
                modifier = Modifier.padding(padding).padding(StarveilSpacing.lg),
            )
            return@Scaffold
        }

        val requesters = remember {
            EditorField.entries.associateWith { FocusRequester() }
        }
        var advancedOpen by rememberSaveable { mutableStateOf(false) }
        val errors = form.validate()

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            CompositionLocalProvider(LocalFieldFocus provides requesters) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(StarveilSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(StarveilSpacing.sm),
                ) {
                    // The protocol is a chip in a row, not a page: switching it
                    // swaps the field set and leaves the three shared fields
                    // alone.
                    ProtocolChips(selected = form.type, onSelect = viewModel::setType)

                    PasteLinkRow(
                        autofilled = state.autofilled,
                        onPaste = viewModel::pasteLink,
                        onUndo = viewModel::undoAutofill,
                    )

                    BasicFields(form, state.errors, viewModel::update)
                    val currentFront = state.allProfiles.firstOrNull { it.nodeKey == form.frontNodeKey }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) { showChainEditor = true }
                            .padding(vertical = StarveilSpacing.sm),
                    ) {
                        Column {
                            Text(
                                text = stringResource(CoreR.string.core_chain_field),
                                style = StarveilType.body,
                                color = StarveilColors.ink,
                            )
                            Text(
                                text = currentFront?.name ?: stringResource(CoreR.string.core_chain_none),
                                style = StarveilType.label,
                                color = StarveilColors.ink2,
                            )
                        }
                    }
                    Section(stringResource(R.string.editor_section_protocol, form.type.displayName()))
                    ProtocolFields(form, state.errors, viewModel::update)

                    // Everything a working link already carries is behind one
                    // row: the common case is name, address, port and a
                    // credential, and a wall of transport options in front of
                    // that is what made this page feel long.
                    if (form.supportsSecurity || form.supportsTransport) {
                        AdvancedHeader(
                            open = advancedOpen,
                            onToggle = { advancedOpen = !advancedOpen },
                        )
                        AnimatedVisibility(visible = advancedOpen) {
                            Column(verticalArrangement = Arrangement.spacedBy(StarveilSpacing.sm)) {
                                if (form.supportsSecurity) {
                                    Section(stringResource(R.string.editor_section_security))
                                    SecurityFields(form, state.errors, viewModel::update)
                                }
                                if (form.supportsTransport) {
                                    Section(stringResource(R.string.editor_section_transport))
                                    TransportFields(form, viewModel::update)
                                }
                            }
                        }
                    }
                }
            }

            // Sticky: the save action does not scroll away, and the line beside
            // it says what is still missing instead of greying the button out
            // and saying nothing.
            SaveBar(
                missing = errors.keys.firstOrNull(),
                saving = state.saving,
                onSave = {
                    val first = EditorField.entries.firstOrNull { it in errors }
                    if (first == null) {
                        viewModel.save()
                    } else {
                        if (first in ADVANCED_FIELDS) advancedOpen = true
                        runCatching { requesters[first]?.requestFocus() }
                    }
                },
            )
        }
        if (showChainEditor) {
            ChainProxySheet(
                exit = Profile(
                    id = form.id,
                    name = form.name.ifBlank { form.server },
                    type = form.type,
                    server = form.server,
                    port = form.port.toIntOrNull() ?: 0,
                    groupId = form.groupId,
                    frontNodeKey = form.frontNodeKey,
                ),
                all = state.allProfiles,
                groupNames = state.groupNames,
                onPick = { front ->
                    viewModel.update(form.copy(frontNodeKey = front?.nodeKey))
                    showChainEditor = false
                },
                onDismiss = { showChainEditor = false },
            )
        }
    }
}

// --- shared blocks --------------------------------------------------------

@Composable
private fun BasicFields(
    form: EditorForm,
    errors: Map<EditorField, EditorError>,
    onChange: (EditorForm) -> Unit,
) {
    Section(stringResource(R.string.editor_section_basic))
    Field(
        label = R.string.editor_field_name,
        value = form.name,
        onValueChange = { onChange(form.copy(name = it)) },
    )
    Field(
        label = R.string.editor_field_server,
        value = form.server,
        error = errors[EditorField.SERVER],
        onValueChange = { onChange(form.copy(server = it)) },
    )
    Field(
        label = R.string.editor_field_port,
        value = form.port,
        error = errors[EditorField.PORT],
        onValueChange = { onChange(form.copy(port = it)) },
    )
}

@Composable
private fun SecurityFields(
    form: EditorForm,
    errors: Map<EditorField, EditorError>,
    onChange: (EditorForm) -> Unit,
) {
    ChipRow(
        // Hysteria2 and TUIC are QUIC: TLS is not something you can turn off,
        // so "None" is not offered for them at all.
        options = SecurityMode.entries.filter { form.allowsNoSecurity || it != SecurityMode.NONE },
        selected = form.security,
        label = { stringResource(it.labelRes()) },
        onSelect = { onChange(form.copy(security = it)) },
    )

    if (form.security != SecurityMode.NONE) {
        Field(
            label = R.string.editor_field_sni,
            value = form.sni,
            onValueChange = { onChange(form.copy(sni = it)) },
        )
        Field(
            label = R.string.editor_field_fingerprint,
            value = form.fingerprint,
            onValueChange = { onChange(form.copy(fingerprint = it)) },
        )
        Toggle(
            label = R.string.editor_field_allow_insecure,
            checked = form.allowInsecure,
            onCheckedChange = { onChange(form.copy(allowInsecure = it)) },
        )
    }

    if (form.security == SecurityMode.REALITY && form.supportsReality) {
        Field(
            label = R.string.editor_field_reality_public_key,
            value = form.realityPublicKey,
            error = errors[EditorField.REALITY_PUBLIC_KEY],
            onValueChange = { onChange(form.copy(realityPublicKey = it)) },
        )
        Field(
            label = R.string.editor_field_reality_short_id,
            value = form.realityShortId,
            onValueChange = { onChange(form.copy(realityShortId = it)) },
        )
        Toggle(
            label = R.string.editor_field_reality_mlkem,
            checked = form.realityMlKem,
            onCheckedChange = { onChange(form.copy(realityMlKem = it)) },
        )
    }
}

@Composable
private fun TransportFields(form: EditorForm, onChange: (EditorForm) -> Unit) {
    ChipRow(
        options = TransportType.entries,
        selected = form.transport,
        label = { stringResource(it.labelRes()) },
        onSelect = { onChange(form.copy(transport = it)) },
    )
    when (form.transport) {
        // TCP emits no transport block at all.
        TransportType.TCP -> Unit

        TransportType.GRPC -> Field(
            label = R.string.editor_field_transport_service,
            value = form.transportServiceName,
            onValueChange = { onChange(form.copy(transportServiceName = it)) },
        )

        // ws / http / xhttp: a path, and an optional host. For xhttp the host
        // travels as a Host header, which is the emitter's concern.
        else -> {
            Field(
                label = R.string.editor_field_transport_path,
                value = form.transportPath,
                onValueChange = { onChange(form.copy(transportPath = it)) },
            )
            Field(
                label = R.string.editor_field_transport_host,
                value = form.transportHost,
                onValueChange = { onChange(form.copy(transportHost = it)) },
            )
        }
    }

    // xhttp only: how the two directions are split. Null is "auto" — the core
    // picks, and it treats an absent mode and `auto` the same way.
    if (form.transport == TransportType.XHTTP) {
        ChipRow(
            options = listOf<XhttpMode?>(null) + XhttpMode.entries,
            selected = form.transportMode,
            label = { it?.wireName ?: stringResource(R.string.editor_mode_auto) },
            onSelect = { onChange(form.copy(transportMode = it)) },
        )
    }
}

@Composable
private fun ProtocolFields(
    form: EditorForm,
    errors: Map<EditorField, EditorError>,
    onChange: (EditorForm) -> Unit,
) {
    when (form.type) {
        ProfileType.VLESS -> {
            UuidField(form, errors, onChange)
            Field(
                label = R.string.editor_field_flow,
                value = form.flow,
                onValueChange = { onChange(form.copy(flow = it)) },
            )
        }

        ProfileType.VMESS -> {
            UuidField(form, errors, onChange)
            Field(
                label = R.string.editor_field_alter_id,
                value = form.alterId,
                error = errors[EditorField.ALTER_ID],
                onValueChange = { onChange(form.copy(alterId = it)) },
            )
            Field(
                label = R.string.editor_field_vmess_security,
                value = form.vmessSecurity,
                onValueChange = { onChange(form.copy(vmessSecurity = it)) },
            )
        }

        ProfileType.TROJAN -> PasswordField(form, errors, onChange)

        ProfileType.SHADOWSOCKS -> {
            Field(
                label = R.string.editor_field_method,
                value = form.ssMethod,
                error = errors[EditorField.METHOD],
                onValueChange = { onChange(form.copy(ssMethod = it)) },
            )
            PasswordField(form, errors, onChange)
        }

        ProfileType.HYSTERIA2 -> {
            PasswordField(form, errors, onChange)
            ChipRow(
                options = listOf("", "salamander", "gecko"),
                selected = form.obfsType,
                label = { if (it.isEmpty()) stringResource(R.string.editor_obfs_none) else it },
                onSelect = { onChange(form.copy(obfsType = it)) },
            )
            if (form.obfsType.isNotEmpty()) {
                Field(
                    label = R.string.editor_field_obfs_password,
                    value = form.obfsPassword,
                    onValueChange = { onChange(form.copy(obfsPassword = it)) },
                )
            }
            Field(
                label = R.string.editor_field_up_mbps,
                value = form.upMbps,
                error = errors[EditorField.UP_MBPS],
                onValueChange = { onChange(form.copy(upMbps = it)) },
            )
            Field(
                label = R.string.editor_field_down_mbps,
                value = form.downMbps,
                error = errors[EditorField.DOWN_MBPS],
                onValueChange = { onChange(form.copy(downMbps = it)) },
            )
        }

        ProfileType.TUIC -> {
            UuidField(form, errors, onChange)
            PasswordField(form, errors, onChange)
            ChipRow(
                options = listOf<TuicCongestionControl?>(null) + TuicCongestionControl.entries,
                selected = form.tuicCongestion,
                label = { it?.wireName ?: "auto" },
                onSelect = { onChange(form.copy(tuicCongestion = it)) },
            )
            ChipRow(
                options = listOf<TuicUdpRelayMode?>(null) + TuicUdpRelayMode.entries,
                selected = form.tuicUdpRelay,
                label = { it?.wireName ?: "auto" },
                onSelect = { onChange(form.copy(tuicUdpRelay = it)) },
            )
        }

        ProfileType.WIREGUARD -> WireGuardFields(form, errors, onChange)

        ProfileType.OTHER -> Text(
            text = stringResource(R.string.editor_missing),
            style = StarveilType.label,
            color = StarveilColors.ink,
        )
    }
}

@Composable
private fun WireGuardFields(
    form: EditorForm,
    errors: Map<EditorField, EditorError>,
    onChange: (EditorForm) -> Unit,
) {
    // WireGuard is an endpoint in sing-box 1.13+, not an outbound; the hint is
    // here because "why is this not in the outbound list" is the obvious
    // question when reading the generated config.
    Text(
        text = stringResource(R.string.editor_hint_wireguard),
        style = StarveilType.label,
        color = StarveilColors.ink2,
    )
    Field(
        label = R.string.editor_field_wg_private_key,
        value = form.wgPrivateKey,
        error = errors[EditorField.WG_PRIVATE_KEY],
        onValueChange = { onChange(form.copy(wgPrivateKey = it)) },
    )
    Field(
        label = R.string.editor_field_wg_address,
        value = form.wgAddress,
        error = errors[EditorField.WG_ADDRESS],
        onValueChange = { onChange(form.copy(wgAddress = it)) },
    )
    Field(
        label = R.string.editor_field_wg_peer_public_key,
        value = form.wgPeerPublicKey,
        error = errors[EditorField.WG_PEER_PUBLIC_KEY],
        onValueChange = { onChange(form.copy(wgPeerPublicKey = it)) },
    )
    Field(
        label = R.string.editor_field_wg_peer_endpoint,
        value = form.wgPeerEndpoint,
        error = errors[EditorField.WG_PEER_ENDPOINT],
        onValueChange = { onChange(form.copy(wgPeerEndpoint = it)) },
    )
    Field(
        label = R.string.editor_field_wg_peer_port,
        value = form.wgPeerPort,
        error = errors[EditorField.WG_PEER_PORT],
        onValueChange = { onChange(form.copy(wgPeerPort = it)) },
    )
    Field(
        label = R.string.editor_field_wg_allowed_ips,
        value = form.wgAllowedIps,
        onValueChange = { onChange(form.copy(wgAllowedIps = it)) },
    )
    Field(
        label = R.string.editor_field_wg_mtu,
        value = form.wgMtu,
        error = errors[EditorField.WG_MTU],
        onValueChange = { onChange(form.copy(wgMtu = it)) },
    )
}

@Composable
private fun UuidField(
    form: EditorForm,
    errors: Map<EditorField, EditorError>,
    onChange: (EditorForm) -> Unit,
) {
    Field(
        label = R.string.editor_field_uuid,
        value = form.uuid,
        error = errors[EditorField.UUID],
        onValueChange = { onChange(form.copy(uuid = it)) },
    )
}

@Composable
private fun PasswordField(
    form: EditorForm,
    errors: Map<EditorField, EditorError>,
    onChange: (EditorForm) -> Unit,
) {
    Field(
        label = R.string.editor_field_password,
        value = form.password,
        error = errors[EditorField.PASSWORD],
        onValueChange = { onChange(form.copy(password = it)) },
    )
}

// --- small pieces ---------------------------------------------------------

@Composable
private fun Section(title: String) {
    Text(
        text = title,
        style = StarveilType.headline,
        color = StarveilColors.ink,
        modifier = Modifier.padding(top = StarveilSpacing.md),
    )
}

@Composable
private fun Field(
    label: Int,
    value: String,
    error: EditorError? = null,
    onValueChange: (String) -> Unit,
) {
    // The focus target is looked up from the label, so a field can be scrolled
    // to and focused without threading a requester through every call site.
    val requester = fieldFor(label)?.let { LocalFieldFocus.current[it] }
    StarveilTextField(
        value = value,
        onValueChange = onValueChange,
        label = stringResource(label),
        isError = error != null,
        supportingText = error?.let { stringResource(it.messageRes()) },
        modifier = Modifier
            .fillMaxWidth()
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier),
    )
}

/** Which field a given label renders, for the focus lookup. */
private fun fieldFor(label: Int): EditorField? = when (label) {
    R.string.editor_field_server -> EditorField.SERVER
    R.string.editor_field_port -> EditorField.PORT
    R.string.editor_field_uuid -> EditorField.UUID
    R.string.editor_field_password -> EditorField.PASSWORD
    R.string.editor_field_reality_public_key -> EditorField.REALITY_PUBLIC_KEY
    R.string.editor_field_method -> EditorField.METHOD
    R.string.editor_field_alter_id -> EditorField.ALTER_ID
    R.string.editor_field_up_mbps -> EditorField.UP_MBPS
    R.string.editor_field_down_mbps -> EditorField.DOWN_MBPS
    R.string.editor_field_wg_private_key -> EditorField.WG_PRIVATE_KEY
    R.string.editor_field_wg_address -> EditorField.WG_ADDRESS
    R.string.editor_field_wg_peer_public_key -> EditorField.WG_PEER_PUBLIC_KEY
    R.string.editor_field_wg_peer_endpoint -> EditorField.WG_PEER_ENDPOINT
    R.string.editor_field_wg_peer_port -> EditorField.WG_PEER_PORT
    R.string.editor_field_wg_mtu -> EditorField.WG_MTU
    else -> null
}

/** The fields that live behind "advanced", so a failed save can open the drawer. */
private val ADVANCED_FIELDS = setOf(
    EditorField.REALITY_PUBLIC_KEY,
)

/** Where each field's focus target lives, provided once for the whole form. */
private val LocalFieldFocus = androidx.compose.runtime.compositionLocalOf<
    Map<EditorField, FocusRequester>,
    > { emptyMap() }

/** All protocols as one chip row. Switching only swaps the field set. */
@Composable
private fun ProtocolChips(selected: ProfileType, onSelect: (ProfileType) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(StarveilSpacing.sm),
    ) {
        ProfileType.entries.filter { it != ProfileType.OTHER }.forEach { type ->
            StarveilChip(
                label = type.displayName(),
                selected = type == selected,
                onClick = { onSelect(type) },
            )
        }
    }
}

/**
 * Paste a link, fill the form.
 *
 * Once it has filled, the input collapses into a one-line receipt with an undo:
 * a paste that silently rewrote six fields is a paste the user will want to
 * take back.
 */
@Composable
private fun PasteLinkRow(
    autofilled: Boolean,
    onPaste: (String) -> Boolean,
    onUndo: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    var rejected by remember { mutableStateOf(false) }

    if (autofilled) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = StarveilSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.editor_paste_filled),
                style = StarveilType.label,
                color = StarveilColors.accent,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onUndo) { Text(stringResource(R.string.editor_paste_undo)) }
        }
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = StarveilSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(StarveilSpacing.sm),
    ) {
        StarveilTextField(
            value = text,
            onValueChange = {
                text = it
                rejected = false
            },
            label = stringResource(R.string.editor_paste_hint),
            isError = rejected,
            supportingText = if (rejected) stringResource(R.string.editor_paste_failed) else null,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = androidx.compose.ui.text.input.ImeAction.Done,
            ),
            modifier = Modifier.weight(1f),
        )
        StarveilButton(
            text = stringResource(R.string.editor_paste_fill),
            enabled = text.isNotBlank(),
            onClick = {
                if (onPaste(text)) {
                    text = ""
                    rejected = false
                } else {
                    rejected = true
                }
            },
        )
    }
}

/** The one row that hides the transport and TLS blocks. */
@Composable
private fun AdvancedHeader(open: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onToggle)
            .padding(vertical = StarveilSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.editor_advanced),
            style = StarveilType.headline,
            color = StarveilColors.ink,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = if (open) StarveilIcons.Up else StarveilIcons.Chevron,
            contentDescription = null,
            tint = StarveilColors.ink2,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * The save bar.
 *
 * The button is never disabled: a dead button with no explanation is the thing
 * that makes a form feel broken. Pressing it with something missing scrolls to
 * that field and focuses it.
 */
@Composable
private fun SaveBar(missing: EditorField?, saving: Boolean, onSave: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(StarveilColors.card)
            .navigationBarsPadding()
            .padding(horizontal = StarveilSpacing.lg, vertical = StarveilSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(StarveilSpacing.md),
    ) {
        Text(
            text = missing?.let {
                stringResource(R.string.editor_missing_field, stringResource(it.labelRes()))
            }.orEmpty(),
            style = StarveilType.label,
            color = StarveilColors.ink2,
            modifier = Modifier.weight(1f),
        )
        StarveilButton(
            text = stringResource(R.string.editor_save),
            enabled = !saving,
            onClick = onSave,
        )
    }
}

/** The label a field is known by, for the "still missing: port" line. */
private fun EditorField.labelRes(): Int = when (this) {
    EditorField.SERVER -> R.string.editor_field_server
    EditorField.PORT -> R.string.editor_field_port
    EditorField.UUID -> R.string.editor_field_uuid
    EditorField.PASSWORD -> R.string.editor_field_password
    EditorField.REALITY_PUBLIC_KEY -> R.string.editor_field_reality_public_key
    EditorField.METHOD -> R.string.editor_field_method
    EditorField.ALTER_ID -> R.string.editor_field_alter_id
    EditorField.UP_MBPS -> R.string.editor_field_up_mbps
    EditorField.DOWN_MBPS -> R.string.editor_field_down_mbps
    EditorField.WG_PRIVATE_KEY -> R.string.editor_field_wg_private_key
    EditorField.WG_ADDRESS -> R.string.editor_field_wg_address
    EditorField.WG_PEER_PUBLIC_KEY -> R.string.editor_field_wg_peer_public_key
    EditorField.WG_PEER_ENDPOINT -> R.string.editor_field_wg_peer_endpoint
    EditorField.WG_PEER_PORT -> R.string.editor_field_wg_peer_port
    EditorField.WG_MTU -> R.string.editor_field_wg_mtu
}

@Composable
private fun Toggle(label: Int, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = StarveilSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(label),
            style = StarveilType.label,
            color = StarveilColors.ink,
            modifier = Modifier.weight(1f),
        )
        StarveilSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun <T> ChipRow(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(StarveilSpacing.sm),
    ) {
        options.forEach { option ->
            StarveilChip(
                label = label(option),
                selected = option == selected,
                onClick = { onSelect(option) },
            )
        }
    }
}

private fun EditorError.messageRes(): Int = when (this) {
    EditorError.REQUIRED -> R.string.editor_error_required
    EditorError.INVALID_NUMBER -> R.string.editor_error_number
}

private fun SecurityMode.labelRes(): Int = when (this) {
    SecurityMode.NONE -> R.string.editor_security_none
    SecurityMode.TLS -> R.string.editor_security_tls
    SecurityMode.REALITY -> R.string.editor_security_reality
}

private fun TransportType.labelRes(): Int = when (this) {
    TransportType.TCP -> R.string.editor_transport_tcp
    TransportType.WS -> R.string.editor_transport_ws
    TransportType.GRPC -> R.string.editor_transport_grpc
    TransportType.HTTP -> R.string.editor_transport_http
    TransportType.XHTTP -> R.string.editor_transport_xhttp
}

private fun ProfileType.displayName(): String = when (this) {
    ProfileType.VLESS -> "VLESS"
    ProfileType.VMESS -> "VMess"
    ProfileType.TROJAN -> "Trojan"
    ProfileType.SHADOWSOCKS -> "Shadowsocks"
    ProfileType.HYSTERIA2 -> "Hysteria2"
    ProfileType.TUIC -> "TUIC"
    ProfileType.WIREGUARD -> "WireGuard"
    ProfileType.OTHER -> "Node"
}
