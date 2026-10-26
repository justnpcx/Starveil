package com.justnpcx.starveil.feature.editor

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

    LaunchedEffect(state.saved) { if (state.saved) onDone() }

    val form = state.form
    Scaffold(
        topBar = {
            TopAppBar(
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
                modifier = Modifier.padding(padding).padding(StarveilSpacing.lg),
            )
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(StarveilSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(StarveilSpacing.sm),
        ) {
            BasicFields(form, state.errors, viewModel::update)

            if (form.supportsSecurity) {
                Section(stringResource(R.string.editor_section_security))
                SecurityFields(form, state.errors, viewModel::update)
            }
            if (form.supportsTransport) {
                Section(stringResource(R.string.editor_section_transport))
                TransportFields(form, viewModel::update)
            }

            Section(stringResource(R.string.editor_section_protocol, type.displayName()))
            ProtocolFields(form, state.errors, viewModel::update)

            Button(
                onClick = viewModel::save,
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth().padding(top = StarveilSpacing.lg),
            ) {
                Text(stringResource(R.string.editor_save))
            }
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
    StarveilTextField(
        value = value,
        onValueChange = onValueChange,
        label = stringResource(label),
        isError = error != null,
        supportingText = error?.let { stringResource(it.messageRes()) },
        modifier = Modifier.fillMaxWidth(),
    )
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
