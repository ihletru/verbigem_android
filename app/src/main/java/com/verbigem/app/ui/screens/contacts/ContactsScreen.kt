package com.verbigem.app.ui.screens.contacts

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import com.verbigem.app.R
import com.verbigem.app.data.repository.ContactMatch
import com.verbigem.app.data.repository.ContactMatchFailure
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verbigem.app.data.AppLinks
import com.verbigem.app.data.InviteLinks
import com.verbigem.app.data.openUrl
import com.verbigem.app.data.shareText
import com.verbigem.app.ui.theme.VerbigemTheme

@Composable
fun ContactsScreen(
    viewModel: ContactsViewModel,
    onOpenChat: (String) -> Unit,
    onOpenContactCard: (String) -> Unit
) {
    val friends by viewModel.friends.collectAsState()
    val incoming by viewModel.incoming.collectAsState()
    val searchTerm by viewModel.searchTerm.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val sentRequests by viewModel.sentRequests.collectAsState()
    val showDisclosure by viewModel.showPermissionDisclosure.collectAsState()
    val phoneContacts by viewModel.phoneContacts.collectAsState()
    val permissionDenied by viewModel.permissionDenied.collectAsState()
    val phoneMatches by viewModel.phoneMatches.collectAsState()
    val isMatching by viewModel.isMatching.collectAsState()
    val matchFailure by viewModel.matchFailure.collectAsState()
    val currentUid = viewModel.currentUid

    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.onPermissionResult(granted) }

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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(VerbigemTheme.colors.bg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.contacts_title),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = VerbigemTheme.colors.ink
            )
        }

        // Zaproszenia przychodzące
        if (incoming.isNotEmpty()) {
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
                        text = stringResource(R.string.invitations),
                        color = VerbigemTheme.colors.muted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    incoming.forEach { f ->
                        val senderName = if (f.requestedBy == f.uidA) f.nicknameA else f.nicknameB
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("👤 $senderName", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = VerbigemTheme.colors.ink)
                            Row {
                                IconButton(onClick = { viewModel.acceptFriend(f) }) {
                                    Icon(Icons.Default.Check, contentDescription = stringResource(R.string.accept), tint = VerbigemTheme.colors.success)
                                }
                                IconButton(onClick = { viewModel.declineFriend(f) }) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.decline), tint = VerbigemTheme.colors.danger)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Wyszukiwarka ludzi
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
                        onValueChange = { viewModel.onSearchTermChanged(it) },
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
                        onClick = { viewModel.searchUsers() },
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
                                    onClick = { viewModel.sendRequest(user.uid, user.nickname) },
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

        // Znajomi z książki telefonicznej.
        // Przycisk NIE prosi o uprawnienie wprost — najpierw idzie ekran
        // prominent disclosure (`ContactsPermissionScreen`), potem system.
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
                    onClick = { viewModel.onFindFromPhoneClicked() },
                    colors = ButtonDefaults.buttonColors(containerColor = VerbigemTheme.colors.accent),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.contacts_find_phone))
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
                val match = viewModel.matchFor(contact.phone)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(VerbigemTheme.colors.surface)
                        .border(1.dp, VerbigemTheme.colors.border, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = contact.name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = VerbigemTheme.colors.ink
                        )
                        // When the number has an account we show THAT name, not the
                        // address-book spelling — it is the name they chose here.
                        Text(
                            text = match?.nickname?.takeIf { it.isNotBlank() } ?: contact.phone,
                            fontSize = 12.sp,
                            color = if (match != null) VerbigemTheme.colors.accent
                            else VerbigemTheme.colors.muted
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
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
                            onClick = {
                                // Najpierw zapisujemy zaproszenie pod skrótem numeru,
                                // potem otwieramy arkusz udostępniania. Jedno bez
                                // drugiego jest niepełne: zaproszenie działa tylko
                                // jeśli ta osoba kiedyś potwierdzi numer, a link
                                // tylko jeśli go kliknie od razu.
                                viewModel.inviteContact(contact)
                                val link = InviteLinks.forUser(currentUid)
                                context.shareText(
                                    context.getString(R.string.contacts_invite_message, link)
                                )
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = VerbigemTheme.colors.accent)
                        ) {
                            Text(
                                text = if (viewModel.isInvited(contact.phone)) {
                                    stringResource(R.string.contacts_invite_saved)
                                } else {
                                    stringResource(R.string.contacts_perm_invite)
                                },
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        // Lista znajomych
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
                    text = stringResource(R.string.friends),
                    color = VerbigemTheme.colors.muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (friends.isEmpty()) {
                    Text(stringResource(R.string.no_friends), color = VerbigemTheme.colors.muted, fontSize = 13.sp)
                }
            }
        }

        items(friends) { f ->
            val otherUid = if (f.uidA == currentUid) f.uidB else f.uidA
            val friendName = if (f.uidA == currentUid) f.nicknameB else f.nicknameA

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(VerbigemTheme.colors.surface)
                    .border(1.dp, VerbigemTheme.colors.border, RoundedCornerShape(12.dp))
                    .clickable { onOpenChat(otherUid) }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = VerbigemTheme.colors.accent
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "💬 $friendName",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = VerbigemTheme.colors.ink,
                    modifier = Modifier.weight(1f)
                )
                // Separate target from the row itself: tapping the row starts a chat,
                // tapping the info button opens the card (alias, language, block…).
                IconButton(onClick = { onOpenContactCard(otherUid) }) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = stringResource(R.string.action_open_contact_card),
                        tint = VerbigemTheme.colors.muted
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
