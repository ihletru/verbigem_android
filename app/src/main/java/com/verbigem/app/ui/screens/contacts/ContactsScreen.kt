package com.verbigem.app.ui.screens.contacts

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verbigem.app.ui.theme.VerbigemTheme

@Composable
fun ContactsScreen(
    viewModel: ContactsViewModel,
    onOpenChat: (String) -> Unit
) {
    val friends by viewModel.friends.collectAsState()
    val incoming by viewModel.incoming.collectAsState()
    val searchTerm by viewModel.searchTerm.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val sentRequests by viewModel.sentRequests.collectAsState()
    val currentUid = viewModel.currentUid

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
                            Icon(Icons.Default.Search, contentDescription = "Szukaj")
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
                    color = VerbigemTheme.colors.ink
                )
            }
        }
    }
}
