package com.justnpcx.starveil.feature.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.justnpcx.starveil.core.data.repository.ProfileRepository
import com.justnpcx.starveil.core.model.ProfileType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Everything the editor screen renders. */
data class EditorUiState(
    /** Null until [ProfileEditorViewModel.load] has run. */
    val form: EditorForm? = null,
    /** Field-level errors, populated by a failed save. */
    val errors: Map<EditorField, EditorError> = emptyMap(),
    val saving: Boolean = false,
    /** Set once the node is stored, so the screen can leave. */
    val saved: Boolean = false,
    /** Editing an id that is no longer in the database. */
    val missing: Boolean = false,
)

/**
 * The node editor.
 *
 * Deliberately thin: the form is a value, and all of the mapping and validation
 * lives in [EditorForm] where it can be tested without a device. This class
 * only loads, holds, and saves it.
 */
@HiltViewModel
class ProfileEditorViewModel @Inject constructor(
    private val profiles: ProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private var loaded = false

    /**
     * @param profileId 0 to create a new node, otherwise the node to edit.
     *
     * Idempotent: the screen calls this from a `LaunchedEffect`, which can fire
     * more than once for the same arguments, and reloading would throw away
     * whatever the user has typed.
     */
    fun load(type: ProfileType, profileId: Long) {
        if (loaded) return
        loaded = true
        if (profileId == 0L) {
            _uiState.value = EditorUiState(form = EditorForm(type = type))
            return
        }
        viewModelScope.launch {
            val existing = profiles.getById(profileId)
            _uiState.value = if (existing == null) {
                EditorUiState(missing = true)
            } else {
                EditorUiState(form = EditorForm.from(existing))
            }
        }
    }

    /** Clears the error for a field as soon as the user edits it. */
    fun update(form: EditorForm) {
        _uiState.update { it.copy(form = form, errors = it.errors - touchedBy(form)) }
    }

    /**
     * Validates and stores. Errors are surfaced per field rather than as one
     * message, so the user is told which input to fix.
     */
    fun save() {
        val form = _uiState.value.form ?: return
        if (_uiState.value.saving) return

        val errors = form.validate()
        if (errors.isNotEmpty()) {
            _uiState.update { it.copy(errors = errors) }
            return
        }
        val profile = form.toProfile() ?: return

        _uiState.update { it.copy(saving = true, errors = emptyMap()) }
        viewModelScope.launch {
            profiles.upsert(profile)
            _uiState.update { it.copy(saving = false, saved = true) }
        }
    }

    /**
     * Which field a change touched, so its stale error can be dropped. Compared
     * field by field rather than assumed: the user may fix one thing and break
     * another in the same edit.
     */
    private fun touchedBy(form: EditorForm): Set<EditorField> {
        val previous = _uiState.value.form ?: return emptySet()
        return buildSet {
            if (form.server != previous.server) add(EditorField.SERVER)
            if (form.port != previous.port) add(EditorField.PORT)
            if (form.uuid != previous.uuid) add(EditorField.UUID)
            if (form.password != previous.password) add(EditorField.PASSWORD)
            if (form.realityPublicKey != previous.realityPublicKey) add(EditorField.REALITY_PUBLIC_KEY)
            if (form.ssMethod != previous.ssMethod) add(EditorField.METHOD)
            if (form.alterId != previous.alterId) add(EditorField.ALTER_ID)
            if (form.upMbps != previous.upMbps) add(EditorField.UP_MBPS)
            if (form.downMbps != previous.downMbps) add(EditorField.DOWN_MBPS)
            if (form.wgPrivateKey != previous.wgPrivateKey) add(EditorField.WG_PRIVATE_KEY)
            if (form.wgAddress != previous.wgAddress) add(EditorField.WG_ADDRESS)
            if (form.wgPeerPublicKey != previous.wgPeerPublicKey) add(EditorField.WG_PEER_PUBLIC_KEY)
            if (form.wgPeerEndpoint != previous.wgPeerEndpoint) add(EditorField.WG_PEER_ENDPOINT)
            if (form.wgPeerPort != previous.wgPeerPort) add(EditorField.WG_PEER_PORT)
            if (form.wgMtu != previous.wgMtu) add(EditorField.WG_MTU)
        }
    }
}
