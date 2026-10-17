package com.justnpcx.starveil.feature.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.justnpcx.starveil.core.data.preferences.ConnectionTarget
import com.justnpcx.starveil.core.data.preferences.PreferencesRepository
import com.justnpcx.starveil.core.data.repository.GroupRepository
import com.justnpcx.starveil.core.data.repository.NodeHealthRepository
import com.justnpcx.starveil.core.data.repository.ProfileRepository
import com.justnpcx.starveil.core.model.GroupMode
import com.justnpcx.starveil.core.model.NodeHealth
import com.justnpcx.starveil.core.model.NodeRegions
import com.justnpcx.starveil.core.model.Profile
import com.justnpcx.starveil.core.model.ProxyGroup
import com.justnpcx.starveil.core.model.nodeHealthOf
import com.justnpcx.starveil.core.subscription.ImportOutcome
import com.justnpcx.starveil.core.subscription.LinkExporter
import com.justnpcx.starveil.core.subscription.LinkImporter
import com.justnpcx.starveil.core.subscription.SubscriptionRefreshScheduler
import com.justnpcx.starveil.core.subscription.SubscriptionUpdateOutcome
import com.justnpcx.starveil.core.subscription.SubscriptionUpdater
import com.justnpcx.starveil.core.ui.snackbar.UndoBus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfilesUiState(
    /** The nodes to show — already narrowed by [groupFilter]. */
    val profiles: List<Profile> = emptyList(),
    /** Every group, for the filter chips. */
    val groups: List<ProxyGroup> = emptyList(),
    /** null = show every group. */
    val groupFilter: Long? = null,
    /** Whether any node exists at all, filtered or not. */
    val hasProfiles: Boolean = false,
    /** Nodes exist, but the current filter hides them all. */
    val hasNoMatches: Boolean = false,
    val selectedId: Long = 0L,
    /** The group the tunnel goes through, when that is the target instead. */
    val selectedGroupId: Long = 0L,
    val loading: Boolean = true,
    val importing: Boolean = false,
    /** Result of the last import or refresh, as data. See [ProfilesMessage]. */
    val message: ProfilesMessage? = null,
    /** The node the share sheet is open for, with its link. */
    val share: ShareTarget? = null,
    /**
     * Per-node health over the sampling window (§6.3), keyed by node id.
     * A node with no samples is simply absent — the card draws it as untested.
     */
    val health: Map<Long, NodeHealth> = emptyMap(),
)

/**
 * What the screen is telling the user, as data rather than as a sentence.
 *
 * The view model has no Context, and copy written into it is copy the resource
 * system never sees: untranslatable, and invisible to a review of the app's
 * wording (C1). The reasons carried here come from the importer and the
 * updater, which already redact them.
 */
sealed interface ProfilesMessage {
    data class AddedNode(val name: String) : ProfilesMessage
    data class ImportedNodes(val count: Int) : ProfilesMessage
    data class ImportFailed(val reason: String) : ProfilesMessage
    data class Refreshed(val count: Int) : ProfilesMessage
    data class RefreshFailed(val reason: String) : ProfilesMessage

    /** How many region groups the last grouping pass created or refreshed. */
    data class GroupedByRegion(val regions: Int) : ProfilesMessage

    /** Nothing in the node names could be recognised as a region. */
    data object NoRegionsFound : ProfilesMessage

    /**
     * A message produced outside this feature — the latency tester, which is
     * in :app and already resolves its own string resources.
     */
    data class External(val text: String) : ProfilesMessage
}

/**
 * What the share sheet renders.
 *
 * [link] is null when the node's protocol has no share format — the sheet says
 * so rather than drawing a QR code for an empty string, which would scan into
 * nothing.
 */
data class ShareTarget(
    val name: String,
    val link: String?,
)

/**
 * The node list plus the import entry point. Import goes through
 * [LinkImporter], so this module never touches the kernel.
 */
@HiltViewModel
class ProfilesViewModel @Inject constructor(
    private val profiles: ProfileRepository,
    private val groups: GroupRepository,
    private val preferences: PreferencesRepository,
    private val importer: LinkImporter,
    private val exporter: LinkExporter,
    private val updater: SubscriptionUpdater,
    private val undo: UndoBus,
    private val refreshScheduler: SubscriptionRefreshScheduler,
    private val nodeHealth: NodeHealthRepository,
) : ViewModel() {

    private val importing = MutableStateFlow(false)
    private val message = MutableStateFlow<ProfilesMessage?>(null)
    private val groupFilter = MutableStateFlow<Long?>(null)
    private val share = MutableStateFlow<ShareTarget?>(null)

    /** Paired so the two transient signals count as one flow to [combine]. */
    private val transient = combine(importing, message) { isImporting, msg -> isImporting to msg }

    /**
     * The list itself. Combined first because `combine` has no overload for six
     * flows, and the share sheet is the one input that is not part of the list.
     */
    private val listState = combine(
        profiles.observeAll(),
        groups.observeAll(),
        // One atomic value rather than two ids: the two are alternatives, and
        // reading them separately can catch a half-applied change.
        preferences.connectionTarget,
        transient,
        groupFilter,
    ) { list, groupList, target, (isImporting, msg), filter ->
        val selectedId = (target as? ConnectionTarget.Node)?.id ?: 0L
        // A node imported from a share link has no group, so it only ever
        // shows under "All".
        val visible = if (filter == null) list else list.filter { it.groupId == filter }
        ProfilesUiState(
            profiles = visible,
            groups = groupList,
            groupFilter = filter,
            hasProfiles = list.isNotEmpty(),
            hasNoMatches = visible.isEmpty() && list.isNotEmpty(),
            selectedId = selectedId,
            selectedGroupId = (target as? ConnectionTarget.Group)?.id ?: 0L,
            loading = false,
            importing = isImporting,
            message = msg,
        )
    }

    /**
     * Per-node health, derived from the one query the sampler writes to.
     *
     * The windowing and grouping happen in the repository; this is the pure
     * part — samples in, a score out.
     */
    private val health: Flow<Map<Long, NodeHealth>> =
        nodeHealth.observeAll().map { byNode -> byNode.mapValues { (_, s) -> nodeHealthOf(s) } }

    val uiState: StateFlow<ProfilesUiState> = combine(
        listState,
        share,
        health,
    ) { state, sharing, healthByNode ->
        state.copy(share = sharing, health = healthByNode)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProfilesUiState(),
    )

    fun select(id: Long) {
        viewModelScope.launch { preferences.setSelectedProfileId(id) }
    }

    /**
     * Point the tunnel through [group] instead of a single node.
     *
     * The repository clears the node selection as part of this, so the two can
     * never both be set.
     */
    fun selectGroup(group: ProxyGroup) {
        viewModelScope.launch { preferences.setSelectedGroupId(group.id) }
    }

    fun stopUsingGroup() {
        viewModelScope.launch { preferences.setSelectedGroupId(0L) }
    }

    /**
     * Changing the mode can invalidate the selection: a manual group is a
     * folder, not something to connect through, so if it was the target it
     * stops being one.
     */
    fun setGroupMode(group: ProxyGroup, mode: GroupMode) {
        if (group.mode == mode) return
        viewModelScope.launch {
            groups.upsert(group.copy(mode = mode))
            if (mode == GroupMode.MANUAL && preferences.selectedGroupId.first() == group.id) {
                preferences.setSelectedGroupId(0L)
            }
        }
    }

    /** [groupId] of null shows every group. */
    fun filterByGroup(groupId: Long?) {
        groupFilter.value = groupId
    }

    fun renameGroup(group: ProxyGroup, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed == group.name) return
        viewModelScope.launch { groups.upsert(group.copy(name = trimmed)) }
    }

    /**
     * A plain folder. Subscription imports are the only other way a group
     * appears, so without this a hand-added node can never be grouped.
     */
    fun createGroup(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { groups.upsert(ProxyGroup(name = trimmed)) }
    }

    /** [groupId] of null takes the node out of its group. */
    fun moveToGroup(profile: Profile, groupId: Long?) {
        val target = groupId ?: 0L
        if (profile.groupId == target) return
        viewModelScope.launch { profiles.upsert(profile.copy(groupId = target)) }
    }

    /**
     * Delete a group and the nodes it brought in.
     *
     * A subscription's nodes *are* the group — they refresh from its URL — so
     * keeping them would leave orphans nothing can update or remove as a set.
     * The caller confirms first, and the dialog names the count.
     */
    fun deleteGroup(group: ProxyGroup) {
        viewModelScope.launch {
            // Read before deleting: the ids are gone once the profiles are.
            val removedIds = group.profileIds.toSet()
            // A region group owns no profiles: its members are derived from
            // node names and belong to their subscription. Deleting them would
            // take the user's nodes with it, which is the opposite of what
            // "delete this group" means here.
            if (group.region == null) {
                profiles.deleteByGroup(group.id)
                // A subscription group's refresh work outlives the row
                // otherwise, and would keep re-creating nodes for a group that
                // is gone.
                refreshScheduler.cancel(group.id)
            }
            groups.deleteById(group.id)
            // Same rule as deleting one node: the target must not outlive the
            // thing it points at.
            if (preferences.selectedGroupId.first() == group.id) {
                preferences.setSelectedGroupId(0L)
            }
            // Stop filtering by something that no longer exists.
            if (groupFilter.value == group.id) groupFilter.value = null
            // Same rule as deleting one node: the selection must not outlive
            // the node it points at. Only ordinary groups removed nodes.
            if (group.region == null && preferences.selectedProfileId.first() in removedIds) {
                preferences.setSelectedProfileId(0L)
            }
        }
    }

    /**
     * One URL_TEST group per region found in the node names.
     *
     * Only the group rows are created — the nodes are never moved, because
     * membership is derived from the name at read time. That is what makes this
     * safe to run again: a subscription refresh replaces its group's profiles,
     * and the region groups simply recompute from whatever is there.
     *
     * Idempotent by region rather than by name: a group someone renamed is
     * still the 香港 group, because the region is its identity and the name is
     * only a label.
     */
    fun groupByRegion() {
        viewModelScope.launch {
            // Cleared first so the same answer is still an answer: a StateFlow
            // does not emit an equal value, and without this a second tap on a
            // node list that matches nothing looked like the button was dead.
            message.value = null
            val regions = profiles.observeAll().first()
                .mapNotNull { NodeRegions.classify(it.name)?.region }
                .distinct()
            if (regions.isEmpty()) {
                message.value = ProfilesMessage.NoRegionsFound
                return@launch
            }
            regions.forEach { region ->
                val existing = groups.findByRegion(region)
                groups.upsert(
                    existing?.copy(mode = GroupMode.URL_TEST)
                        ?: ProxyGroup(
                            name = region.displayName,
                            mode = GroupMode.URL_TEST,
                            region = region,
                        ),
                )
            }
            message.value = ProfilesMessage.GroupedByRegion(regions.size)
        }
    }

    /**
     * Remove a node.
     *
     * When the deleted node is the selected one, clear the selection too:
     * leaving it set would have the dashboard name a node that no longer
     * exists, and the next connect would build a config from a missing
     * profile.
     */
    fun delete(id: Long) {
        viewModelScope.launch {
            profiles.deleteById(id)
            if (preferences.selectedProfileId.first() == id) {
                preferences.setSelectedProfileId(0L)
            }
        }
    }

    /**
     * Remove a node, offering the way back.
     *
     * Deletion is one interaction app-wide (K2): it happens at once and the
     * Snackbar offers the reversal, rather than a dialog asking permission
     * first. That is what makes a swipe acceptable for something that carries
     * credentials — the credentials are not lost, they are one tap away.
     */
    fun deleteWithUndo(profile: Profile) {
        viewModelScope.launch {
            delete(profile.id)
            undo.show(R.string.profiles_deleted, listOf(profile.name)) { restore(profile) }
        }
    }

    /**
     * Put a node back exactly as it was.
     *
     * `upsert` with the stored id restores the same row, credentials included —
     * the settings blob is re-encoded from the profile the caller held, so an
     * undo is a real reversal rather than a re-import.
     */
    fun restore(profile: Profile) {
        viewModelScope.launch { profiles.upsert(profile) }
    }

    /** Paste a share link, or a subscription URL. */
    fun import(text: String) {
        viewModelScope.launch {
            importing.value = true
            message.value = when (val outcome = importer.import(text)) {
                is ImportOutcome.AddedNode -> ProfilesMessage.AddedNode(outcome.name)
                is ImportOutcome.AddedSubscription -> ProfilesMessage.ImportedNodes(outcome.nodeCount)
                is ImportOutcome.Failed -> ProfilesMessage.ImportFailed(outcome.reason)
            }
            importing.value = false
        }
    }

    /**
     * Re-pull a subscription-backed group from its URL.
     *
     * The periodic worker is the automatic path, but it only exists for groups
     * imported after the scheduler got its first caller — an older group has a
     * URL and no work, so nothing would ever refresh it. Re-arming the schedule
     * here is what makes a manual refresh also repair the group's cadence.
     *
     * A group without a URL is a folder; there is nothing to fetch.
     */
    fun refreshSubscription(group: ProxyGroup) {
        val url = group.subscriptionUrl
        if (url.isNullOrBlank()) return
        viewModelScope.launch {
            importing.value = true
            refreshScheduler.schedule(group.id)
            message.value = when (val outcome = updater.update(group.id, url)) {
                is SubscriptionUpdateOutcome.Success -> ProfilesMessage.Refreshed(outcome.profileCount)
                is SubscriptionUpdateOutcome.Failure -> ProfilesMessage.RefreshFailed(outcome.reason)
            }
            importing.value = false
        }
    }

    /**
     * Opens the share sheet for [profile].
     *
     * The link is built here rather than inside the sheet so the exporter stays
     * the single place that knows the link formats, and so a protocol with no
     * share format resolves to a null link once instead of on every
     * recomposition.
     */
    fun shareNode(profile: Profile) {
        share.value = ShareTarget(name = profile.name, link = exporter.export(profile))
    }

    fun dismissShare() {
        share.value = null
    }

    fun dismissMessage() {
        message.value = null
    }

    /** Surfaces a message produced outside this feature (e.g. the latency tester). */
    fun postMessage(text: String) {
        message.value = ProfilesMessage.External(text)
    }
}
