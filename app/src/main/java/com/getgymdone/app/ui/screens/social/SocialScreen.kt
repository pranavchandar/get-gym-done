package com.getgymdone.app.ui.screens.social

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.getgymdone.app.data.db.entities.Friend
import com.getgymdone.app.data.social.FriendCode
import com.getgymdone.app.data.social.SocialRepository
import com.getgymdone.app.data.social.SocialStats
import com.getgymdone.app.ui.components.Avatar
import com.getgymdone.app.ui.components.BigCta
import com.getgymdone.app.ui.components.GhostCta
import com.getgymdone.app.ui.components.ProfileFields
import com.getgymdone.app.ui.components.QrCode
import com.getgymdone.app.ui.components.decodeQrFromUri
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SocialState(
    val loading: Boolean = true,
    val enabled: Boolean = false,
    val myCode: String? = null,        // encoded FriendCode for the QR
    val myHandle: String = "",
    val myStats: SocialStats? = null,  // my own leaderboard row
    val friends: List<Friend> = emptyList(),
    val syncing: Boolean = false,
    val message: String? = null,       // one-shot toast text
)

@HiltViewModel
class SocialViewModel @Inject constructor(
    private val social: SocialRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SocialState())
    val state: StateFlow<SocialState> = _state

    init {
        // Best-effort refresh on open so the leaderboard is current without a manual tap. No-op
        // (touches no network) while social is disabled.
        viewModelScope.launch { runCatching { social.syncAll() } }
        viewModelScope.launch {
            combine(social.observePrefs(), social.observeFriends()) { prefs, friends -> prefs to friends }
                .collect { (prefs, friends) ->
                    val code = social.myCode()?.encode()
                    val mine = social.myStats()
                    _state.update {
                        it.copy(
                            loading = false,
                            enabled = prefs?.socialEnabled == true,
                            myCode = code,
                            myHandle = prefs?.socialHandle.orEmpty(),
                            myStats = mine,
                            friends = friends,
                        )
                    }
                }
        }
    }

    fun enable(handle: String, color: String, photo: String?) = viewModelScope.launch {
        runCatching { social.enableSocial(handle.trim().ifBlank { "Athlete" }, color, photo) }
            .onFailure { _state.update { it.copy(message = "Couldn't reach the server. Check your connection.") } }
    }

    fun disable() = viewModelScope.launch { social.disableSocial() }

    fun removeFriend(userId: String) = viewModelScope.launch { social.removeFriend(userId) }

    fun onScanned(raw: String?) = viewModelScope.launch {
        val code = raw?.let { FriendCode.decode(it) }
        if (code == null) {
            _state.update { it.copy(message = "That isn't a Get Gym Done friend code.") }
            return@launch
        }
        runCatching { social.addFriend(code) }
        _state.update { it.copy(message = "Added ${code.handle}.") }
    }

    fun sync() = viewModelScope.launch {
        _state.update { it.copy(syncing = true) }
        runCatching { social.syncAll() }
            .onFailure { _state.update { it.copy(message = "Sync failed. Check your connection.") } }
        _state.update { it.copy(syncing = false) }
    }

    fun nudge(userId: String) = viewModelScope.launch {
        val friend = _state.value.friends.firstOrNull { it.userId == userId } ?: return@launch
        runCatching { social.nudge(friend) }
            .onSuccess { _state.update { it.copy(message = "Nudged ${friend.handle} 🔥") } }
            .onFailure { _state.update { it.copy(message = "Couldn't send the nudge.") } }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}

@Composable
fun SocialScreen(onBack: () -> Unit) {
    val vm: SocialViewModel = hiltViewModel()
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.consumeMessage()
        }
    }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        vm.onScanned(result.contents)
    }
    val uploadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val decoded = decodeQrFromUri(context, uri)
        if (decoded == null) Toast.makeText(context, "No QR code found in that image.", Toast.LENGTH_SHORT).show()
        else vm.onScanned(decoded)
    }
    val launchUpload: () -> Unit = {
        uploadLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    // Ask for notification permission when opting in, so friend nudges can actually reach you.
    val notifPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val onEnable: (String, String, String?) -> Unit = { handle, color, photo ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        vm.enable(handle, color, photo)
    }
    val launchScanner: () -> Unit = {
        scanLauncher.launch(
            ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setPrompt("Scan your friend's code")
                setBeepEnabled(false)
                setOrientationLocked(false)
                captureActivity = PortraitCaptureActivity::class.java
            },
        )
    }

    // userId pending removal — drives the confirm dialog (null = no dialog).
    var removeId by rememberSaveable { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
            .padding(top = 56.dp, bottom = 22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.clip(CircleShape).clickable(onClick = onBack).padding(4.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text("FRIENDS", style = MaterialTheme.typography.displaySmall)
        }
        Spacer(Modifier.height(20.dp))

        when {
            state.loading -> {}
            !state.enabled -> OptInPanel(onEnable = onEnable)
            else -> {
                MyCodeCard(
                    code = state.myCode,
                    handle = state.myHandle,
                    photo = state.myStats?.photo,
                    color = state.myStats?.color ?: "lime",
                    onScan = launchScanner,
                    onUpload = launchUpload,
                )
                Spacer(Modifier.height(20.dp))
                LeaderboardSection(
                    rows = leaderRows(state),
                    syncing = state.syncing,
                    onRefresh = vm::sync,
                    onRemove = { removeId = it },
                    onNudge = vm::nudge,
                )
                Spacer(Modifier.height(24.dp))
                GhostCta(label = "Turn off Friends", onClick = vm::disable)
            }
        }
    }

        removeId?.let { id ->
            val handle = state.friends.firstOrNull { it.userId == id }?.handle?.ifBlank { "this friend" } ?: "this friend"
            RemoveFriendDialog(
                handle = handle,
                onConfirm = {
                    vm.removeFriend(id)
                    removeId = null
                },
                onDismiss = { removeId = null },
            )
        }
    }
}

private data class LeaderRow(
    val userId: String?,   // null = me
    val handle: String,
    val colorKey: String,
    val photo: String?,
    val currentStreak: Int,
    val weekSessions: Int,
    val weekTarget: Int,
    val atRisk: Boolean,
)

private fun leaderRows(state: SocialState): List<LeaderRow> {
    val me = state.myStats?.let {
        LeaderRow(
            userId = null,
            handle = it.handle.ifBlank { state.myHandle }.ifBlank { "You" },
            colorKey = it.color,
            photo = it.photo,
            currentStreak = it.currentStreak,
            weekSessions = it.weekSessions,
            weekTarget = it.weekTarget,
            atRisk = it.streakAtRisk,
        )
    }
    val friends = state.friends.map {
        LeaderRow(it.userId, it.handle, it.color, it.photo, it.currentStreak, it.weekSessions, it.weekTarget, it.streakAtRisk)
    }
    return (listOfNotNull(me) + friends)
        .sortedWith(compareByDescending<LeaderRow> { it.currentStreak }.thenByDescending { it.weekSessions })
}

@Composable
private fun OptInPanel(onEnable: (String, String, String?) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var color by rememberSaveable { mutableStateOf("lime") }
    var photo by rememberSaveable { mutableStateOf<String?>(null) }
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(18.dp),
    ) {
        Text(
            "ADD FRIENDS, KEEP EACH OTHER HONEST",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Share your streak on a private leaderboard. Only your streak and session counts are " +
                "shared — never your weights or body stats. No email or password; you add friends by " +
                "scanning each other's code in person.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(18.dp))
        ProfileFields(
            name = name, onName = { name = it },
            color = color, onColor = { color = it },
            photo = photo, onPhoto = { photo = it },
        )
        Spacer(Modifier.height(18.dp))
        BigCta(label = "Turn on Friends", onClick = { onEnable(name, color, photo) })
    }
}

@Composable
private fun MyCodeCard(code: String?, handle: String, photo: String?, color: String, onScan: () -> Unit, onUpload: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("YOUR CODE", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(14.dp))
        if (code != null) {
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .padding(12.dp),
            ) {
                QrCode(content = code, modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(photo = photo, colorKey = color, name = handle, size = 32.dp)
                Spacer(Modifier.width(10.dp))
                Text(handle.ifBlank { "You" }, style = MaterialTheme.typography.titleMedium)
            }
        } else {
            Text("Setting up…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Have a friend scan this, then scan theirs.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        GhostCta(label = "Scan a friend's code", onClick = onScan)
        Spacer(Modifier.height(10.dp))
        GhostCta(label = "Upload a code from photo", onClick = onUpload)
    }
}

@Composable
private fun LeaderboardSection(
    rows: List<LeaderRow>,
    syncing: Boolean,
    onRefresh: () -> Unit,
    onRemove: (String) -> Unit,
    onNudge: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("LEADERBOARD", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (syncing) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
        } else {
            Text(
                "REFRESH",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .clickable(onClick = onRefresh)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
    Spacer(Modifier.height(10.dp))
    if (rows.size <= 1) {
        Text(
            "No friends yet. Scan someone's code to see them here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        rows.forEachIndexed { i, row ->
            LeaderRowItem(rank = i + 1, row = row, onRemove = onRemove, onNudge = onNudge)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun LeaderRowItem(rank: Int, row: LeaderRow, onRemove: (String) -> Unit, onNudge: (String) -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val isMe = row.userId == null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isMe) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, if (isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$rank",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(22.dp),
        )
        Avatar(photo = row.photo, colorKey = row.colorKey, name = row.handle, size = 28.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(if (isMe) "${row.handle} (you)" else row.handle, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            if (row.atRisk) {
                Text(
                    if (isMe) "Your streak is at risk today" else "Streak at risk today",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            } else {
                Text(
                    "This week ${row.weekSessions}/${row.weekTarget.coerceAtLeast(1)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // A friend with an at-risk streak gets a one-tap nudge; otherwise show the streak count.
        if (row.userId != null && row.atRisk) {
            NudgePill(onClick = { onNudge(row.userId) })
            Spacer(Modifier.width(8.dp))
        } else {
            Icon(Icons.Rounded.LocalFireDepartment, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("${row.currentStreak}d", style = MaterialTheme.typography.titleMedium)
        }
        if (row.userId != null) {
            Spacer(Modifier.width(if (row.atRisk) 0.dp else 10.dp))
            Icon(
                Icons.Rounded.Close,
                contentDescription = "Remove ${row.handle}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp).clip(CircleShape).clickable { onRemove(row.userId) },
            )
        }
    }
}

@Composable
private fun NudgePill(onClick: () -> Unit) {
    val shape = RoundedCornerShape(100.dp)
    Text(
        "NUDGE 🔥",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.primary, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun RemoveFriendDialog(handle: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(32.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable(enabled = false) {}
                .padding(24.dp),
        ) {
            Text("REMOVE FRIEND?", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(10.dp))
            Text(
                "Remove $handle from your leaderboard? You'll drop off theirs too — you'd each have to scan again to reconnect.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DialogButton("Cancel", filled = false, onClick = onDismiss, modifier = Modifier.weight(1f))
                DialogButton("Remove", filled = true, onClick = onConfirm, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DialogButton(label: String, filled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(shape)
            .then(
                if (filled) Modifier.background(MaterialTheme.colorScheme.primary, shape)
                else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, shape),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.titleMedium,
            color = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
        )
    }
}
