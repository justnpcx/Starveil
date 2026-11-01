package com.justnpcx.starveil.feature.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.justnpcx.starveil.core.data.preferences.ConnectionTarget
import com.justnpcx.starveil.core.data.preferences.PreferencesRepository
import com.justnpcx.starveil.core.data.repository.GroupRepository
import com.justnpcx.starveil.core.data.repository.NodeHealthRepository
import com.justnpcx.starveil.core.data.geo.CountryResolver
import com.justnpcx.starveil.core.data.repository.ProfileRepository
import com.justnpcx.starveil.core.model.GroupMode
import com.justnpcx.starveil.core.model.NodeHealth
import com.justnpcx.starveil.core.model.NodeSelectionMode
import com.justnpcx.starveil.core.model.NodeRegions
import com.justnpcx.starveil.core.model.Profile
import com.justnpcx.starveil.core.model.ProxyGroup
import com.justnpcx.starveil.core.model.nodeHealthOf
import com.justnpcx.starveil.core.subscription.ImportFailure
import com.justnpcx.starveil.core.subscription.ImportOutcome
import com.justnpcx.starveil.core.subscription.LinkPreview
import com.justnpcx.starveil.core.subscription.LinkExporter
import com.justnpcx.starveil.core.subscription.LinkImporter
import com.justnpcx.starveil.core.subscription.SubscriptionRefreshScheduler
import com.justnpcx.starveil.core.subscription.SubscriptionFailure
import com.justnpcx.starveil.core.subscription.SubscriptionUpdateOutcome
import com.justnpcx.starveil.core.subscription.SubscriptionUpdater
import com.justnpcx.starveil.core.ui.snackbar.UndoBus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import java.security.MessageDigest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What narrows the node list.
 *
 * One value, not two flags. The old model kept "favorites only" and "group id"
 * as separate booleans, so turning one on had to remember to turn the other
 * off — and the group chip never did. Two chips could be lit at once, the list
 * showed the intersection, and the intersection was usually nothing.
 */
sealed interface NodeFilter {
    /** Everything. */
    data object All : NodeFilter
    /** Starred nodes, from any group. */
    data object Favorites : NodeFilter
    /** One group — a folder, a subscription, or a derived region. */
    data class Group(val id: Long) : NodeFilter
}

data class ProfilesUiState(
    /** The nodes to show — already narrowed by [filter]. */
    val profiles: List<Profile> = emptyList(),
    /** Every group, for the filter chips. */
    val groups: List<ProxyGroup> = emptyList(),
    /** Groups that actually contain nodes (unfiltered), for the chip row. */
    val nonEmptyGroups: List<ProxyGroup> = emptyList(),
    /** What the list is narrowed to. Exactly one chip is lit by construction. */
    val filter: NodeFilter = NodeFilter.All,
    /** Whether any node exists at all, filtered or not. */
    val hasProfiles: Boolean = false,
    /** Nodes exist, but the current filter hides them all. */
    val hasNoMatches: Boolean = false,
    /**
     * What the user typed into the search field.
     *
     * Held in the state rather than in the screen: the filter has to survive
     * the node list re-emitting (a health sample arrives every few seconds),
     * and a `remember` in the screen would be reset by nothing but would also
     * be invisible to the ViewModel that does the filtering.
     */
    val query: String = "",
    /**
     * The search matched nothing, as opposed to a group filter hiding
     * everything. Two different sentences: "no node matches X" is about the
     * search, "this group is empty" is about the filter.
     */
    val hasNoSearchMatches: Boolean = false,
    /** True while the "Favorites" chip is the active filter. */

    /** Show the one-time "long-press a node" line. */
    val showLongPressHint: Boolean = false,
    val selectedId: Long = 0L,
    /** The group the tunnel goes through, when that is the target instead. */
    val selectedGroupId: Long = 0L,
    val loading: Boolean = true,
    val importing: Boolean = false,
    /** Result of the last import or refresh, as data. See [ProfilesMessage]. */
    val message: ProfilesMessage? = null,

    /** Quota reported by each subscription, keyed by group id. Usually empty. */
    val usage: Map<Long, com.justnpcx.starveil.core.model.SubscriptionUserInfo> = emptyMap(),
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

    /** [dropped] counts subscription entries that produced no node. */
    data class ImportedNodes(val count: Int, val dropped: Int = 0) : ProfilesMessage

    /** The link resolved to a node that is already in the list. */
    data class Existing(val name: String) : ProfilesMessage

    /**
     * A pasted block where some lines worked.
     *
     * Both halves are reported: "5 added" alone hides the two that did not, and
     * a failure message alone hides the five that did.
     */
    data class ImportedPartially(
        val count: Int,
        val failures: List<ImportFailure>,
    ) : ProfilesMessage
    data class ImportFailed(val failure: ImportFailure) : ProfilesMessage
    data class Refreshed(val count: Int, val dropped: Int = 0) : ProfilesMessage
    data class RefreshFailed(val reason: SubscriptionFailure) : ProfilesMessage

    /** How many region groups the last grouping pass created or refreshed. */
    data class GroupedByRegion(val regions: Int) : ProfilesMessage

    /** A folder the user just made, so the page can say so. */
    data class GroupCreated(val name: String) : ProfilesMessage

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
/** What a subscription import brought in, for the panel's result line. */
data class SubscriptionImported(
    val groupId: Long,
    val name: String,
    val count: Int,
)

/** A clipboard payload worth offering: what it holds, and how much of it. */
data class ClipboardSuggestion(
    val text: String,
    val nodeCount: Int,
    /** "host:port", or the subscription URL when that is what it holds. */
    val detail: String,
)

data class ShareTarget(
    val name: String,
    val link: String?,
    /**
     * True when the node is chained and the link therefore does not carry the
     * front.
     *
     * A share link is one node's grammar; a front is a second node. Saying so
     * on the sheet is the only honest option — dropping it silently would hand
     * someone a node that behaves differently from the one they were shown.
     */
    val omitsFront: Boolean = false,
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
    private val countryResolver: CountryResolver,
) : ViewModel() {

    private val importing = MutableStateFlow(false)
    private val message = MutableStateFlow<ProfilesMessage?>(null)
    private val nodeFilter = MutableStateFlow<NodeFilter>(NodeFilter.All)
    private val searchQuery = MutableStateFlow("")
    private val share = MutableStateFlow<ShareTarget?>(null)

    /**
     * A clipboard payload the page is offering to import.
     *
     * Kept out of [ProfilesUiState] on purpose: it is not part of the list, and
     * folding it into the six-way combine would re-run the whole list pipeline
     * every time the clipboard changes.
     */
    private val clipboard = MutableStateFlow<ClipboardSuggestion?>(null)

    /** The flow the nodes page reads for the in-page clipboard bar. */
    val clipboardSuggestion: StateFlow<ClipboardSuggestion?> = clipboard

    /**
     * The last subscription imported from the panel.
     *
     * The panel stays open after a subscription import — a subscription is a
     * batch, and closing the panel would leave the user looking at a list that
     * changed for reasons they have to reconstruct.
     */
    private val subscriptionImport = MutableStateFlow<SubscriptionImported?>(null)
    val lastSubscriptionImport: StateFlow<SubscriptionImported?> = subscriptionImport

    /**
     * The row an import just created or found.
     *
     * The list is long enough that "imported" is not self-evident; the page
     * scrolls to this row and flashes it once.
     */
    private val highlight = MutableStateFlow<Long?>(null)
    val highlightedNode: StateFlow<Long?> = highlight

    fun clearHighlight() {
        highlight.value = null
    }

    /**
     * The last payload offered, by hash.
     *
     * "Offer once" is the rule: the same clipboard content must not raise the
     * bar again on every resume, and a dismissed bar must stay dismissed.
     */
    private var offeredClipboardHash: String? = null

    init {
        // Nodes learn their country once. A GeoIP answer does not change, so the
        // result is written back to the row and that host is never looked up
        // again; a node whose address cannot be resolved simply keeps no flag.
        viewModelScope.launch {
            profiles.observeAll().collect { list ->
                list.filter { it.country == null }.forEach { profile ->
                    val country = countryResolver.countryOf(profile.server) ?: return@forEach
                    profiles.setCountry(profile.id, country)
                }
            }
        }
    }

    /** Paired so the two transient signals count as one flow to [combine]. */
    private val transient = combine(importing, message) { isImporting, msg -> isImporting to msg }

    /**
     * The three things that narrow the list, as one value for the same reason:
     * reading them separately can catch a half-applied change, and the
     * favorites chip and the group chips are alternatives.
     */
    private val filters = combine(nodeFilter, searchQuery) { filter, text ->
        filter to text
    }

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
        filters,
    ) { list, groupList, target, (isImporting, msg), (filter, query) ->
        val selectedId = (target as? ConnectionTarget.Node)?.id ?: 0L
        // A node imported from a share link has no group, so it only ever
        // shows under "All".
        //
        // A region group is the exception: it owns no nodes by design (its
        // members are derived from name and country at read time, so a
        // subscription refresh cannot leave it stale). Filtering it by id would
        // therefore always come out empty — the nodes still belong to the
        // subscription they came from. Ask the region instead; that is what the
        // group means.
        // One value decides, so the chips and the list can never disagree.
        val inGroup = when (filter) {
            NodeFilter.All -> list
            NodeFilter.Favorites -> list.filter { it.favorite }
            is NodeFilter.Group -> {
                val group = groupList.firstOrNull { it.id == filter.id }
                if (group?.region != null) {
                    list.filter { profile ->
                        NodeRegions.resolve(profile.name, profile.country) == group.region
                    }
                } else {
                    list.filter { it.groupId == filter.id }
                }
            }
        }
        // Favorites come first — "pin", not just "bookmark". The sort is
        // stable, so the order inside each half is the one the list already
        // had.
        val visible = inGroup.filterBy(query).sortedByDescending { it.favorite }
        val activeGroups = groupList.filter { group ->
            if (group.subscriptionUrl != null) return@filter false
            val region = group.region
            if (region != null) {
                list.any { NodeRegions.resolve(it.name, it.country) == region }
            } else {
                list.any { it.groupId == group.id }
            }
        }
        ProfilesUiState(
            profiles = visible,
            groups = groupList,
            nonEmptyGroups = activeGroups,
            filter = filter,
            hasProfiles = list.isNotEmpty(),
            // Two ways to end up with nothing, and they need different
            // sentences: a group that is empty, and a search that matched
            // nothing. Blank query means the group filter did it.
            hasNoMatches = visible.isEmpty() && list.isNotEmpty() && query.isBlank(),
            query = query,
            hasNoSearchMatches = visible.isEmpty() && list.isNotEmpty() && query.isNotBlank(),
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

    private val usage = preferences.subscriptionUsage

    val uiState: StateFlow<ProfilesUiState> = combine(
        listState,
        share,
        health,
        usage,
        preferences.nodeHintSeen,
    ) { state, sharing, healthByNode, usageByGroup, hintSeen ->
        state.copy(
            share = sharing,
            health = healthByNode,
            usage = usageByGroup,
            showLongPressHint = !hintSeen && state.profiles.isNotEmpty(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProfilesUiState(),
    )

    /** The hint has been read (or a long-press made it moot). */
    /**
     * Offers [text] as an import if it parses and has not been offered before.
     *
     * Called from the two foreground moments the design allows — the add panel
     * opening and the page resuming — and never from a timer. A null or
     * unparseable clipboard is not an error: it just means no bar.
     */
    fun offerClipboard(text: String?) {
        val payload = text?.takeIf { it.isNotBlank() } ?: return
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(payload.toByteArray())
            .joinToString("") { "%02x".format(it) }
        if (hash == offeredClipboardHash) return
        val preview = importer.preview(payload)
        if (preview.isEmpty) return
        offeredClipboardHash = hash
        clipboard.value = ClipboardSuggestion(
            text = payload,
            nodeCount = preview.nodes.size,
            detail = preview.nodes.firstOrNull()?.let { it.server + ":" + it.port }
                ?: preview.subscriptionUrl.orEmpty(),
        )
    }

    fun dismissClipboardSuggestion() {
        clipboard.value = null
    }

    /** Imports what the bar offered and clears it. */
    fun importClipboardSuggestion() {
        val suggestion = clipboard.value ?: return
        clipboard.value = null
        importMany(suggestion.text)
    }

    fun dismissNodeHint() {
        viewModelScope.launch { preferences.markNodeHintSeen() }
    }

    /** Star or unstar; the row is the same row, so nothing else moves. */
    fun toggleFavorite(profile: Profile) {
        viewModelScope.launch { profiles.setFavorite(profile.id, !profile.favorite) }
    }

    /**
     * The favorites chip.
     *
     * One assignment: a filter is a value, so choosing favorites cannot leave a
     * group lit behind it. Turning it off goes back to everything, which is
     * what the chip's own toggle means.
     */
    fun filterFavorites(on: Boolean) {
        nodeFilter.value = if (on) NodeFilter.Favorites else NodeFilter.All
    }

    fun select(id: Long) {
        viewModelScope.launch {
            // Picking a node by hand is what "manual" means. Writing it here
            // keeps the dashboard's segmented control honest even when the
            // choice was made on this screen.
            preferences.setNodeSelectionMode(NodeSelectionMode.MANUAL)
            preferences.setSelectedProfileId(id)
        }
    }

    /**
     * Point the tunnel through [group] instead of a single node.
     *
     * The repository clears the node selection as part of this, so the two can
     * never both be set.
     */
    fun selectGroup(group: ProxyGroup) {
        viewModelScope.launch {
            preferences.setNodeSelectionMode(NodeSelectionMode.MANUAL)
            preferences.setSelectedGroupId(group.id)
        }
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

    /** [groupId] of null shows everything; otherwise exactly that group. */
    fun filterByGroup(groupId: Long?) {
        nodeFilter.value = groupId?.let { NodeFilter.Group(it) } ?: NodeFilter.All
    }

    /**
     * Narrow the list to what matches [query].
     *
     * Matches the name and the server, case-insensitively, and treats the
     * query as a whole substring rather than a set of tokens: someone typing
     * `hk` means the two characters in that order, and splitting on spaces
     * would make `HK 01` also match a node called `01 香港`.
     */
    fun setQuery(query: String) {
        searchQuery.value = query
    }

    fun clearQuery() {
        searchQuery.value = ""
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
        viewModelScope.launch {
            val id = groups.upsert(ProxyGroup(name = trimmed))
            // Show the result, don't just create it: a new folder used to be
            // invisible because the chip row only drew groups that already had
            // nodes in them, so "new group" looked like it had done nothing.
            nodeFilter.value = NodeFilter.Group(id)
            message.value = ProfilesMessage.GroupCreated(trimmed)
        }
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
    /**
     * Delete a group. [deleteProfiles] is the user's answer to "and its
     * nodes?", and it only applies to a plain folder: a subscription's nodes
     * cannot outlive the address that refreshes them, and a region group owns
     * no nodes at all — its members are derived from their names.
     */
    fun deleteGroup(group: ProxyGroup, deleteProfiles: Boolean) {
        viewModelScope.launch {
            // Read before deleting: the ids are gone once the profiles are.
            val removedIds = group.profileIds.toSet()
            // A region group owns no profiles: its members are derived from
            // node names and belong to their subscription. Deleting them would
            // take the user's nodes with it, which is the opposite of what
            // "delete this group" means here.
            //
            // The row and its nodes go in one transaction: a half-finished
            // delete would leave an empty group that looks like a subscription
            // which refreshed to nothing.
            val removeNodes = when {
                group.region != null -> false
                group.subscriptionUrl != null -> true
                else -> deleteProfiles
            }
            groups.deleteWithProfiles(group.id, deleteProfiles = removeNodes)
            if (removeNodes) {
                // A subscription group's refresh work outlives the row
                // otherwise, and would keep re-creating nodes for a group that
                // is gone.
                refreshScheduler.cancel(group.id)
            }
            // Same rule as deleting one node: the target must not outlive the
            // thing it points at.
            if (preferences.selectedGroupId.first() == group.id) {
                preferences.setSelectedGroupId(0L)
            }
            // Stop filtering by something that no longer exists.
            if (nodeFilter.value == NodeFilter.Group(group.id)) nodeFilter.value = NodeFilter.All
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
                .mapNotNull { NodeRegions.resolve(it.name, it.country) }
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

    /**
     * What [text] would bring in, without importing anything.
     *
     * Pure: it parses, it does not touch the database. The add panel uses it
     * both to describe the clipboard and to decide whether the clipboard tile
     * is live.
     */
    fun preview(text: String): LinkPreview = importer.preview(text)

    /**
     * Imports every line of a pasted block.
     *
     * The result is a count plus the lines that failed, because a pasted block
     * is all-or-nothing only in the worst case: one broken link must not throw
     * away the other four.
     */
    fun importMany(text: String) {
        viewModelScope.launch {
            importing.value = true
            val outcome = importer.importMany(text)
            // The row to point at: what was created, or — when everything was
            // already there — the node that already existed.
            highlight.value = outcome.addedIds.firstOrNull() ?: outcome.duplicates.firstOrNull()
            message.value = when {
                outcome.added > 0 && outcome.failures.isEmpty() ->
                    ProfilesMessage.ImportedNodes(outcome.added)
                outcome.added > 0 ->
                    ProfilesMessage.ImportedPartially(outcome.added, outcome.failures)
                outcome.duplicates.isNotEmpty() ->
                    ProfilesMessage.Existing("")
                else -> ProfilesMessage.ImportFailed(
                    outcome.failures.firstOrNull() ?: ImportFailure.NothingToImport,
                )
            }
            importing.value = false
        }
    }

    /** Imports a subscription, under [name] when the user gave one. */
    fun importSubscription(url: String, name: String) {
        viewModelScope.launch {
            importing.value = true
            message.value = when (val outcome = importer.import(url, name)) {
                is ImportOutcome.AddedNode -> {
                    highlight.value = outcome.id
                    ProfilesMessage.AddedNode(outcome.name)
                }
                is ImportOutcome.Duplicate -> {
                    highlight.value = outcome.id
                    ProfilesMessage.Existing(outcome.name)
                }
                is ImportOutcome.AddedSubscription -> {
                    val group = groups.findBySubscriptionUrl(url)
                    if (group != null) {
                        subscriptionImport.value = SubscriptionImported(
                            groupId = group.id,
                            name = group.name,
                            count = outcome.nodeCount,
                        )
                    }
                    ProfilesMessage.ImportedNodes(outcome.nodeCount, outcome.dropped)
                }
                is ImportOutcome.Failed -> ProfilesMessage.ImportFailed(outcome.failure)
            }
            importing.value = false
        }
    }

    fun clearSubscriptionImport() {
        subscriptionImport.value = null
    }

    /** Paste a share link, or a subscription URL. */
    fun import(text: String) {
        viewModelScope.launch {
            importing.value = true
            message.value = when (val outcome = importer.import(text)) {
                is ImportOutcome.AddedNode -> {
                    highlight.value = outcome.id
                    ProfilesMessage.AddedNode(outcome.name)
                }
                is ImportOutcome.Duplicate -> {
                    highlight.value = outcome.id
                    ProfilesMessage.Existing(outcome.name)
                }
                is ImportOutcome.AddedSubscription ->
                    ProfilesMessage.ImportedNodes(outcome.nodeCount, outcome.dropped)
                is ImportOutcome.Failed -> ProfilesMessage.ImportFailed(outcome.failure)
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
                is SubscriptionUpdateOutcome.Success -> {
                    // The header only arrives with the response, so this is the
                    // one moment the quota can be recorded.
                    if (outcome.userInfo != null) {
                        preferences.setSubscriptionUsage(group.id, outcome.userInfo)
                    }
                    ProfilesMessage.Refreshed(outcome.profileCount, outcome.dropped)
                }
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
    /**
     * Nodes that may serve as [exit]'s front.
     *
     * Three exclusions, each for a reason the sheet has to state: the exit
     * itself (a node cannot dial through itself), any node that already has a
     * front (one hop only — this is what makes a loop impossible by
     * construction rather than by a runtime check), and anything that no longer
     * exists, which cannot appear in [all] in the first place.
     */
    fun frontCandidates(exit: Profile, all: List<Profile>): List<Profile> =
        all.filter { candidate ->
            candidate.nodeKey != exit.nodeKey && candidate.frontNodeKey == null
        }

    /** Sets or clears the front; the node keeps everything else it had. */
    fun setFrontNode(exit: Profile, frontKey: String?) {
        viewModelScope.launch {
            profiles.upsert(exit.copy(frontNodeKey = frontKey))
        }
    }

    fun shareNode(profile: Profile) {
        share.value = ShareTarget(
            name = profile.name,
            link = exporter.export(profile),
            omitsFront = profile.frontNodeKey != null,
        )
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

/**
 * The nodes whose name or server contains [query], ignoring case.
 *
 * [Profile.displayName] is not used: the flag is decoration, and a user
 * searching for a country types its name, which the display name carries but
 * so does the stored one. Matching the server as well is what makes `1.2.3`
 * find a node by its address.
 */
private fun List<Profile>.filterBy(query: String): List<Profile> {
    val needle = query.trim()
    if (needle.isEmpty()) return this
    return filter { profile ->
        profile.name.contains(needle, ignoreCase = true) ||
            profile.server.contains(needle, ignoreCase = true)
    }
}
