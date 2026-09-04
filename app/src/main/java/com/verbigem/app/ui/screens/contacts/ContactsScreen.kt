package com.verbigem.app.ui.screens.contacts

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.verbigem.app.R
import com.verbigem.app.data.local.ExternalContactEntity
import com.verbigem.app.data.model.Friendship
import com.verbigem.app.data.repository.ContactMatch
import com.verbigem.app.data.repository.ContactMatchFailure
import com.verbigem.app.data.repository.FriendSuggestion
import com.verbigem.app.data.AppLinks
import com.verbigem.app.data.InviteLinks
import com.verbigem.app.data.OutboundChannel
import com.verbigem.app.data.OutboundChannels
import com.verbigem.app.data.OutboundTarget
import com.verbigem.app.data.PhoneContact
import com.verbigem.app.data.openUrl
import com.verbigem.app.ui.theme.VerbigemTheme
import kotlinx.coroutines.launch

@Composable
fun ContactsScreen(
    viewModel: ContactsViewModel,
    onOpenChat: (String) -> Unit,
    onOpenContactCard: (String) -> Unit,
    /** Numer z książki, dla którego otwieramy wątek jednokierunkowy (3.6). */
    onOpenExternalThread: (String) -> Unit
) {
    val friends by viewModel.friends.collectAsState()
    val incoming by viewModel.incoming.collectAsState()
    val searchTerm by viewModel.searchTerm.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val sentRequests by viewModel.sentRequests.collectAsState()
    val showDisclosure by viewModel.showPermissionDisclosure.collectAsState()
    val phoneContacts by viewModel.phoneContacts.collectAsState()
    val importedContacts by viewModel.importedContacts.collectAsState()
    val permissionDenied by viewModel.permissionDenied.collectAsState()
    val phoneMatches by viewModel.phoneMatches.collectAsState()
    val isMatching by viewModel.isMatching.collectAsState()
    val matchFailure by viewModel.matchFailure.collectAsState()
    val externalContacts by viewModel.externalContacts.collectAsState()
    val suggestedFriends by viewModel.suggestedFriends.collectAsState()
    val vcfCount by viewModel.vcfImportedCount.collectAsState()
    val vcfError by viewModel.vcfReadError.collectAsState()
    val currentUid = viewModel.currentUid

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Tylko dla wejścia do wątku jednokierunkowego: zapis w Room musi zdążyć
    // przed nawigacją, a to jedno `suspend`, nie stan ekranu.
    val openExternal: (PhoneContact) -> Unit = { contact ->
        scope.launch {
            viewModel.rememberExternal(contact)
            onOpenExternalThread(contact.phone)
        }
    }

    // Dla którego kontaktu otwarty jest wybór kanału (3.5). Null = dialog zamknięty.
    var pendingInvite by remember { mutableStateOf<PhoneContact?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.onPermissionResult(granted) }

    // Picker pliku .vcf (3.7). `OpenDocument` daje trwały dostęp do wybranego URI,
    // więc `VcfImporter.parseUri` może go odczytać przez `contentResolver`.
    val vcfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> if (uri != null) viewModel.importVcf(uri) }

    // Prominent disclosure MUSI być przed systemowym dialogiem uprawnień —
    // wymóg Google Play dla READ_CONTACTS (reguła „in-app disclosure first").
    if (showDisclosure) {
        ContactsPermissionScreen(
            onContinue = { permissionLauncher.launch(Manifest.permission.READ_CONTACTS) },
            onDismiss = { viewModel.dismissDisclosure() },
            onOpenPolicy = { context.openUrl(AppLinks.privacyPolicyFor(context)) }
        )
        return
    }

    val tabs = listOf(
        R.string.tab_friends,
        R.string.tab_invites,
        R.string.tab_phone,
        R.string.tab_external
    )
    var selectedTab by remember { mutableStateOf(0) }

    // Wyszukiwanie po wszystkich zakładkach naraz (3.8): gdy coś wpisano, schodzimy
    // z układu zakładek na jedną listę trafień. Puste pole wraca do zakładek.
    var search by remember { mutableStateOf("") }

    // Scalona lista książki + importu .vcf — obie dają PhoneContact do wątku.
    val allPhoneContacts = phoneContacts + importedContacts

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VerbigemTheme.colors.bg)
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.contacts_title),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = VerbigemTheme.colors.ink
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            placeholder = { Text(stringResource(R.string.search_hint), fontSize = 13.sp) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = VerbigemTheme.colors.accent,
                unfocusedBorderColor = VerbigemTheme.colors.border
            ),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = VerbigemTheme.colors.muted) }
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (search.isNotBlank()) {
            CombinedSearch(
                query = search,
                friends = friends,
                phoneContacts = allPhoneContacts,
                externalContacts = externalContacts,
                currentUid = currentUid,
                matchFor = viewModel::matchFor,
                onOpenChat = onOpenChat,
                onOpenExternalContact = openExternal,
                onOpenExternalThread = onOpenExternalThread
            )
        } else {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = VerbigemTheme.colors.bg,
                contentColor = VerbigemTheme.colors.accent
            ) {
                tabs.forEachIndexed { index, res ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(stringResource(res), fontSize = 13.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (selectedTab) {
                0 -> FriendsTab(
                    friends = friends,
                    currentUid = currentUid,
                    searchTerm = searchTerm,
                    searchResults = searchResults,
                    isSearching = isSearching,
                    sentRequests = sentRequests,
                    suggestedFriends = suggestedFriends,
                    onSearchTermChanged = viewModel::onSearchTermChanged,
                    onSearchUsers = viewModel::searchUsers,
                    onOpenChat = onOpenChat,
                    onOpenContactCard = onOpenContactCard,
                    onSendRequest = viewModel::sendRequest,
                    onDismissSuggestion = viewModel::dismissSuggestion
                )
                1 -> InvitesTab(
                    incoming = incoming,
                    currentUid = currentUid,
                    onAccept = viewModel::acceptFriend,
                    onDecline = viewModel::declineFriend
                )
                2 -> PhoneTab(
                    phoneContacts = allPhoneContacts,
                    permissionDenied = permissionDenied,
                    isMatching = isMatching,
                    matchFailure = matchFailure,
                    phoneMatches = phoneMatches,
                    vcfCount = vcfCount,
                    vcfError = vcfError,
                    currentUid = currentUid,
                    onFindFromPhone = viewModel::onFindFromPhoneClicked,
                    onPermissionResult = viewModel::onPermissionResult,
                    permissionLauncher = { permissionLauncher.launch(Manifest.permission.READ_CONTACTS) },
                    onImportVcf = { vcfLauncher.launch(arrayOf("text/vcard", "text/x-vcard", "application/vcard", "*/*")) },
                    matchFor = viewModel::matchFor,
                    onOpenChat = onOpenChat,
                    onOpenExternalThread = openExternal,
                    onInvite = { pendingInvite = it },
                    isInvited = viewModel::isInvited
                )
                3 -> ExternalTab(
                    contacts = externalContacts,
                    onOpenExternalThread = onOpenExternalThread
                )
            }
        }
    }

    // Wybór kanału dla zaproszenia (3.5). Renderuje się jako nakładka, więc
    // pozycja w drzewie nie ma znaczenia — jest tu, bo to koniec ekranu.
    pendingInvite?.let { contact ->
        val target = OutboundTarget.from(contact)
        // Liczone raz na kontakt: `isAvailable` pyta PackageManagera, a to nie
        // jest coś, co chcemy robić przy każdej rekompozycji.
        val channels = remember(contact) { OutboundChannels.availableFor(context, target) }

        ChannelPickerDialog(
            contactName = contact.name,
            channels = channels,
            onPick = { channel ->
                pendingInvite = null
                // Zaproszenie pod skrótem numeru zapisujemy NIEZALEŻNIE od kanału.
                // To ten sam numer, a link i zaproszenie to dwa różne sposoby na
                // ten sam cel: zaproszenie działa, gdy ta osoba kiedyś potwierdzi
                // numer, link — gdy kliknie go od razu. Skipping jednego z nich
                // zostawia połowę szansy.
                viewModel.inviteContact(contact)
                val link = InviteLinks.forUser(currentUid)
                val text = context.getString(R.string.contacts_invite_text)
                channel.handOff(context, target, text, link)
            },
            onDismiss = { pendingInvite = null }
        )
    }
}

/** Jeden wiersz listy — wspólny dla zakładek i wyszukiwania. */
@Composable
private fun ContactListRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(VerbigemTheme.colors.surface)
            .border(1.dp, VerbigemTheme.colors.border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = VerbigemTheme.colors.ink,
                maxLines = 1
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = VerbigemTheme.colors.muted,
                    maxLines = 1
                )
            }
        }
        if (trailing != null) trailing()
    }
}

@Composable
private fun FriendsTab(
    friends: List<Friendship>,
    currentUid: String,
    searchTerm: String,
    searchResults: List<SearchResultUser>,
    isSearching: Boolean,
    sentRequests: Set<String>,
    suggestedFriends: List<FriendSuggestion>,
    onSearchTermChanged: (String) -> Unit,
    onSearchUsers: () -> Unit,
    onOpenChat: (String) -> Unit,
    onOpenContactCard: (String) -> Unit,
    onSendRequest: (String, String) -> Unit,
    onDismissSuggestion: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // „Możesz znać" (3.9): znajomi moich znajomych, liczeni po stronie serwera.
        // Sekcja na szczycie zakładki — najbardziej naturalne miejsce na sugestie.
        if (suggestedFriends.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(VerbigemTheme.colors.surface)
                        .border(1.dp, VerbigemTheme.colors.border, RoundedCornerShape(20.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.people_you_may_know),
                        color = VerbigemTheme.colors.muted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    suggestedFriends.forEach { s ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${s.photoURL} ${s.nickname}",
                                    fontSize = 14.sp,
                                    color = VerbigemTheme.colors.ink,
                                    maxLines = 1
                                )
                                if (s.mutualCount > 0) {
                                    Text(
                                        text = stringResource(R.string.people_you_may_know_mutual, s.mutualCount),
                                        fontSize = 12.sp,
                                        color = VerbigemTheme.colors.muted,
                                        maxLines = 1
                                    )
                                }
                            }
                            IconButton(onClick = { onDismissSuggestion(s.uid) }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.dismiss),
                                    tint = VerbigemTheme.colors.muted
                                )
                            }
                            Button(
                                onClick = { onSendRequest(s.uid, s.nickname) },
                                colors = ButtonDefaults.buttonColors(containerColor = VerbigemTheme.colors.accent),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(stringResource(R.string.add_friend), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // Szukanie ludzi — dodawanie nowych znajomych mieszka w zakładce Znajomi,
        // bo to tu trafiają po przyjęciu zaproszenia.
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(VerbigemTheme.colors.surface)
                    .border(1.dp, VerbigemTheme.colors.border, RoundedCornerShape(20.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.find_people),
                    color = VerbigemTheme.colors.muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = searchTerm,
                        onValueChange = onSearchTermChanged,
                        placeholder = { Text(stringResource(R.string.search_hint), fontSize = 13.sp) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VerbigemTheme.colors.accent,
                            unfocusedBorderColor = VerbigemTheme.colors.border
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onSearchUsers,
                        enabled = searchTerm.trim().length >= 2 && !isSearching,
                        colors = ButtonDefaults.buttonColors(containerColor = VerbigemTheme.colors.accent),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isSearching) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                        } else {
                            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.action_search))
                        }
                    }
                }
                if (searchResults.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    searchResults.forEach { user ->
                        val isSent = sentRequests.contains(user.uid)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "${user.photoURL} ${user.nickname}", fontSize = 14.sp, color = VerbigemTheme.colors.ink)
                            if (isSent) {
                                Text(stringResource(R.string.sent_request), fontSize = 12.sp, color = VerbigemTheme.colors.muted)
                            } else {
                                Button(
                                    onClick = { onSendRequest(user.uid, user.nickname) },
                                    colors = ButtonDefaults.buttonColors(containerColor = VerbigemTheme.colors.accent),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(stringResource(R.string.add_friend), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = stringResource(R.string.friends),
                color = VerbigemTheme.colors.muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (friends.isEmpty()) {
            item { Text(stringResource(R.string.no_friends), color = VerbigemTheme.colors.muted, fontSize = 13.sp) }
        }

        items(friends) { f ->
            val otherUid = if (f.uidA == currentUid) f.uidB else f.uidA
            val friendName = if (f.uidA == currentUid) f.nicknameB else f.nicknameA
            ContactListRow(
                title = "💬 $friendName",
                subtitle = "",
                onClick = { onOpenChat(otherUid) },
                trailing = {
                    IconButton(onClick = { onOpenContactCard(otherUid) }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = stringResource(R.string.action_open_contact_card),
                            tint = VerbigemTheme.colors.muted
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun InvitesTab(
    incoming: List<Friendship>,
    currentUid: String,
    onAccept: (Friendship) -> Unit,
    onDecline: (Friendship) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (incoming.isEmpty()) {
            item {
                Text(stringResource(R.string.no_invites), color = VerbigemTheme.colors.muted, fontSize = 13.sp)
            }
        }
        items(incoming) { f ->
            val senderName = if (f.requestedBy == f.uidA) f.nicknameA else f.nicknameB
            ContactListRow(
                title = "👤 $senderName",
                subtitle = "",
                onClick = {},
                trailing = {
                    Row {
                        IconButton(onClick = { onAccept(f) }) {
                            Icon(Icons.Default.Check, contentDescription = stringResource(R.string.accept), tint = VerbigemTheme.colors.success)
                        }
                        IconButton(onClick = { onDecline(f) }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.decline), tint = VerbigemTheme.colors.danger)
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun PhoneTab(
    phoneContacts: List<PhoneContact>,
    permissionDenied: Boolean,
    isMatching: Boolean,
    matchFailure: ContactMatchFailure?,
    phoneMatches: Map<String, ContactMatch>,
    vcfCount: Int?,
    vcfError: Boolean,
    currentUid: String,
    onFindFromPhone: () -> Unit,
    onPermissionResult: (Boolean) -> Unit,
    permissionLauncher: () -> Unit,
    onImportVcf: () -> Unit,
    matchFor: (String) -> ContactMatch?,
    onOpenChat: (String) -> Unit,
    onOpenExternalThread: (PhoneContact) -> Unit,
    onInvite: (PhoneContact) -> Unit,
    isInvited: (String) -> Boolean
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(VerbigemTheme.colors.surface)
                    .border(1.dp, VerbigemTheme.colors.border, RoundedCornerShape(20.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.contacts_from_phone_label),
                    color = VerbigemTheme.colors.muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Button(
                    onClick = onFindFromPhone,
                    colors = ButtonDefaults.buttonColors(containerColor = VerbigemTheme.colors.accent),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.contacts_find_phone))
                }
                Button(
                    onClick = onImportVcf,
                    colors = ButtonDefaults.buttonColors(containerColor = VerbigemTheme.colors.accent),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.contacts_import_vcf))
                }
                // Komunikat o wyniku importu — inline, nie modal, żeby nie zasłaniał
                // listy, którą właśnie powiększyliśmy. `vcfCount` to `by`-delegat,
                // więc kopiujemy do lokalnej val — Kotlin nie smart-castuje delegatów.
                val imported = vcfCount
                when {
                    vcfError -> Text(
                        stringResource(R.string.contacts_import_vcf_failed),
                        fontSize = 12.sp, color = VerbigemTheme.colors.danger
                    )
                    imported == 0 -> Text(
                        stringResource(R.string.contacts_import_vcf_none),
                        fontSize = 12.sp, color = VerbigemTheme.colors.muted
                    )
                    imported != null -> Text(
                        stringResource(R.string.contacts_import_vcf_done, imported),
                        fontSize = 12.sp, color = VerbigemTheme.colors.accent
                    )
                }
                if (permissionDenied) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.contacts_perm_denied),
                        fontSize = 12.sp,
                        color = VerbigemTheme.colors.danger
                    )
                }
            }
        }

        if (phoneContacts.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.contacts_perm_found, phoneContacts.size),
                    fontSize = 12.sp,
                    color = VerbigemTheme.colors.muted
                )
            }
            // Status of the `matchContacts` lookup. Shown inline rather than as a
            // dialog: a failed lookup must not hide the address book behind a modal.
            if (isMatching) {
                item {
                    Text(
                        text = stringResource(R.string.contacts_match_checking),
                        fontSize = 12.sp,
                        color = VerbigemTheme.colors.muted
                    )
                }
            } else if (matchFailure != null) {
                // Copied into a local val first: `matchFailure` is a `by` delegated
                // property, and Kotlin will not smart-cast those.
                val failure = matchFailure ?: ContactMatchFailure.UNKNOWN
                item {
                    Text(
                        text = stringResource(failure.toMessageRes()),
                        fontSize = 12.sp,
                        color = VerbigemTheme.colors.danger
                    )
                }
            } else if (phoneMatches.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.contacts_match_found, phoneMatches.size),
                        fontSize = 12.sp,
                        color = VerbigemTheme.colors.accent
                    )
                }
            }
            items(phoneContacts) { contact ->
                val match = matchFor(contact.phone)
                ContactListRow(
                    title = contact.name,
                    subtitle = match?.nickname?.takeIf { it.isNotBlank() } ?: contact.phone,
                    onClick = {
                        if (match != null) {
                            onOpenChat(match.uid)
                        } else {
                            onOpenExternalThread(contact)
                        }
                    },
                    trailing = {
                        if (match != null) {
                            Button(
                                onClick = { onOpenChat(match.uid) },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = VerbigemTheme.colors.accent)
                            ) {
                                Text(stringResource(R.string.contacts_match_write), fontSize = 12.sp)
                            }
                        } else {
                            Button(
                                onClick = { onInvite(contact) },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = VerbigemTheme.colors.accent)
                            ) {
                                Text(
                                    text = if (isInvited(contact.phone)) {
                                        stringResource(R.string.contacts_invite_saved)
                                    } else {
                                        stringResource(R.string.contacts_perm_invite)
                                    },
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ExternalTab(
    contacts: List<ExternalContactEntity>,
    onOpenExternalThread: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (contacts.isEmpty()) {
            item {
                Text(stringResource(R.string.contacts_external_empty), fontSize = 13.sp, color = VerbigemTheme.colors.muted)
            }
        }
        items(contacts, key = { it.phone }) { entity ->
            ContactListRow(
                title = entity.name.takeIf { it.isNotBlank() } ?: entity.phone,
                subtitle = entity.email.takeIf { it.isNotBlank() } ?: entity.phone,
                onClick = { onOpenExternalThread(entity.phone) }
            )
        }
    }
}

@Composable
private fun CombinedSearch(
    query: String,
    friends: List<Friendship>,
    phoneContacts: List<PhoneContact>,
    externalContacts: List<ExternalContactEntity>,
    currentUid: String,
    matchFor: (String) -> ContactMatch?,
    onOpenChat: (String) -> Unit,
    onOpenExternalContact: (PhoneContact) -> Unit,
    onOpenExternalThread: (String) -> Unit
) {
    val q = query.trim().lowercase()
    val friendHits = friends.filter {
        (if (it.uidA == currentUid) it.nicknameB else it.nicknameA).lowercase().contains(q)
    }
    val phoneHits = phoneContacts.filter {
        it.name.lowercase().contains(q) || it.phone.contains(q)
    }
    val externalHits = externalContacts.filter {
        it.name.lowercase().contains(q) || it.phone.contains(q)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (friendHits.isNotEmpty()) {
            item { Text(stringResource(R.string.tab_friends), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = VerbigemTheme.colors.muted) }
            items(friendHits) { f ->
                val otherUid = if (f.uidA == currentUid) f.uidB else f.uidA
                val name = if (f.uidA == currentUid) f.nicknameB else f.nicknameA
                ContactListRow(title = "💬 $name", subtitle = "", onClick = { onOpenChat(otherUid) })
            }
        }
        if (phoneHits.isNotEmpty()) {
            item { Text(stringResource(R.string.tab_phone), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = VerbigemTheme.colors.muted) }
            items(phoneHits) { contact ->
                val match = matchFor(contact.phone)
                ContactListRow(
                    title = contact.name,
                    subtitle = match?.nickname?.takeIf { it.isNotBlank() } ?: contact.phone,
                onClick = {
                    if (match != null) onOpenChat(match.uid) else onOpenExternalContact(contact)
                }
                )
            }
        }
        if (externalHits.isNotEmpty()) {
            item { Text(stringResource(R.string.tab_external), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = VerbigemTheme.colors.muted) }
            items(externalHits, key = { it.phone }) { entity ->
                ContactListRow(
                    title = entity.name.takeIf { it.isNotBlank() } ?: entity.phone,
                    subtitle = entity.email.takeIf { it.isNotBlank() } ?: entity.phone,
                    onClick = { onOpenExternalThread(entity.phone) }
                )
            }
        }
        if (friendHits.isEmpty() && phoneHits.isEmpty() && externalHits.isEmpty()) {
            item { Text(stringResource(R.string.no_results), fontSize = 13.sp, color = VerbigemTheme.colors.muted) }
        }
    }
}

/**
 * Wybór kanału, którym oddajemy zaproszenie (3.5).
 *
 * Pokazujemy tylko te kanały, które mają sens dla tego wpisu — „SMS" znika, gdy
 * kontakt nie ma numeru, „E-mail", gdy nie ma adresu. Pusta lista nie może się
 * zdarzyć w praktyce (systemowy arkusz udostępniania jest zawsze), ale gdyby się
 * zdarzyła, mówimy to wprost zamiast pokazywać pusty dialog.
 */
@Composable
private fun ChannelPickerDialog(
    contactName: String,
    channels: List<OutboundChannel>,
    onPick: (OutboundChannel) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = VerbigemTheme.colors.surface,
            border = BorderStroke(1.dp, VerbigemTheme.colors.border)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(R.string.channel_pick_title),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = VerbigemTheme.colors.ink
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = contactName,
                    fontSize = 12.sp,
                    color = VerbigemTheme.colors.muted
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (channels.isEmpty()) {
                    Text(
                        text = stringResource(R.string.channel_none_available),
                        fontSize = 13.sp,
                        color = VerbigemTheme.colors.muted
                    )
                } else {
                    channels.forEach { channel ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onPick(channel) }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(channel.labelRes),
                                fontSize = 15.sp,
                                color = VerbigemTheme.colors.ink,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VerbigemTheme.colors.bg
                    )
                ) {
                    Text(
                        stringResource(R.string.cancel),
                        color = VerbigemTheme.colors.ink,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

/**
 * Maps a matching failure to a message.
 *
 * "Rate limited" is the only one the user can act on (wait), so it says so. The rest
 * are deliberately vague — a client does not need to know that the server is missing
 * a secret.
 */
private fun ContactMatchFailure.toMessageRes(): Int = when (this) {
    ContactMatchFailure.RATE_LIMITED -> R.string.contacts_match_rate_limited
    ContactMatchFailure.NOT_CONFIGURED -> R.string.contacts_match_error
    ContactMatchFailure.UNAUTHENTICATED -> R.string.contacts_match_unauthenticated
    ContactMatchFailure.UNKNOWN -> R.string.contacts_match_error
}
