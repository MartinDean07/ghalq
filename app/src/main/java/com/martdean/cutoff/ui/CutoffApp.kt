package com.martdean.cutoff.ui

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.martdean.cutoff.R
import com.martdean.cutoff.data.AppInfo
import com.martdean.cutoff.data.AppRepository
import com.martdean.cutoff.ui.theme.CutoffColors
import com.martdean.cutoff.ui.theme.LocalTokens

@Composable
fun CutoffApp(vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val t = LocalTokens.current
    val context = LocalContext.current
    var showInfo by rememberSaveable { mutableStateOf(false) }

    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) vm.turnOn()
        else Toast.makeText(context, R.string.toast_consent_denied, Toast.LENGTH_LONG).show()
    }

    // Apps installed or removed while Cutoff was in the background.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshApps() }

    val onPower: () -> Unit = {
        when {
            state.running -> vm.turnOff()
            state.blocked.isEmpty() ->
                Toast.makeText(context, R.string.toast_pick_first, Toast.LENGTH_SHORT).show()
            else -> {
                val request = VpnService.prepare(context)
                if (request != null) consent.launch(request) else vm.turnOn()
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(t.bg)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Header(running = state.running, onInfo = { showInfo = true })
            Text(
                stringResource(R.string.title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            Text(
                stringResource(R.string.subtitle),
                style = MaterialTheme.typography.bodyMedium.copy(color = t.muted),
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
            )
            SearchField(state.query, vm::setQuery)
            Spacer(Modifier.height(12.dp))
            FilterRow(state.filter, state.blocked.size, vm::setFilter)
            Spacer(Modifier.height(4.dp))
            AppList(
                state = state,
                repository = vm.repository,
                onToggle = vm::toggle,
                modifier = Modifier.weight(1f),
            )
            Footer(state = state, onPower = onPower)
        }
    }

    if (showInfo) InfoSheet(onDismiss = { showInfo = false })
}

@Composable
private fun Header(running: Boolean, onInfo: () -> Unit) {
    val t = LocalTokens.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusPill(running)
        Spacer(Modifier.weight(1f))
        GhostIconButton(R.drawable.ic_info, stringResource(R.string.cd_about), onInfo)
    }
}

@Composable
private fun StatusPill(running: Boolean) {
    val t = LocalTokens.current
    val pulse = rememberInfiniteTransition(label = "pulse")
    val a by pulse.animateFloat(
        initialValue = 1f, targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "dot",
    )
    val color = if (running) t.danger else t.faint
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(t.surface)
            .border(1.dp, t.line, RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .alpha(if (running) a else 1f)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(if (running) R.string.status_on else R.string.status_off),
            style = MaterialTheme.typography.labelMedium.copy(color = if (running) t.text else t.muted),
        )
    }
}

@Composable
private fun GhostIconButton(icon: Int, description: String, onClick: () -> Unit) {
    val t = LocalTokens.current
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = t.muted, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    val t = LocalTokens.current
    Row(
        Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(t.surface)
            .border(1.dp, t.line, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_search), null, tint = t.faint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(stringResource(R.string.search_hint), style = MaterialTheme.typography.bodyLarge.copy(color = t.faint))
            }
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = t.text),
                cursorBrush = SolidColor(t.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "search" },
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                painterResource(R.drawable.ic_close), stringResource(R.string.cd_clear), tint = t.muted,
                modifier = Modifier
                    .size(18.dp)
                    .clickable { onChange("") },
            )
        }
    }
}

@Composable
private fun FilterRow(filter: Filter, blockedCount: Int, onChange: (Filter) -> Unit) {
    val t = LocalTokens.current
    Row(
        Modifier
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(t.surface)
            .padding(4.dp),
    ) {
        Segment(stringResource(R.string.filter_all), filter == Filter.All) { onChange(Filter.All) }
        Segment(stringResource(R.string.filter_blocked, latin(blockedCount)), filter == Filter.Blocked) { onChange(Filter.Blocked) }
    }
}

@Composable
private fun Segment(label: String, selected: Boolean, onClick: () -> Unit) {
    val t = LocalTokens.current
    val bg by animateColorAsState(if (selected) t.surface2 else Color.Transparent, label = "seg")
    Box(
        Modifier
            .then(if (selected) Modifier.shadow(2.dp, RoundedCornerShape(9.dp)) else Modifier)
            .clip(RoundedCornerShape(9.dp))
            .background(bg)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium.copy(color = if (selected) t.text else t.muted),
        )
    }
}

@Composable
private fun AppList(
    state: UiState,
    repository: AppRepository,
    onToggle: (String) -> Unit,
    modifier: Modifier,
) {
    val t = LocalTokens.current
    val visible = state.visible
    Box(modifier.fillMaxWidth()) {
        when {
            state.loading -> EmptyState(stringResource(R.string.loading))
            visible.isEmpty() && state.filter == Filter.Blocked -> EmptyState(stringResource(R.string.empty_blocked))
            visible.isEmpty() -> EmptyState(stringResource(R.string.empty_search))
            else -> LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                items(visible, key = { it.packageName }) { app ->
                    AppRow(
                        app = app,
                        blocked = app.packageName in state.blocked,
                        live = state.running,
                        attempts = state.attemptsByApp[app.packageName] ?: 0,
                        repository = repository,
                        onToggle = { onToggle(app.packageName) },
                        first = app == visible.first(),
                        tokens = t,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    val t = LocalTokens.current
    Column(
        Modifier
            .fillMaxSize()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(painterResource(R.drawable.ic_shield), null, tint = t.faint, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(12.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium.copy(color = t.muted))
    }
}

@Composable
private fun AppRow(
    app: AppInfo,
    blocked: Boolean,
    live: Boolean,
    attempts: Int,
    repository: AppRepository,
    onToggle: () -> Unit,
    first: Boolean,
    tokens: CutoffColors,
) {
    val t = tokens
    val sizePx = with(LocalDensity.current) { 40.dp.roundToPx() }
    val icon by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, app.packageName) {
        value = repository.icon(app.packageName, sizePx)
    }
    val stateText = stringResource(if (blocked) R.string.state_blocked else R.string.state_allowed)
    Column {
        if (!first) Box(
            Modifier
                .padding(start = 76.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(t.line)
        )
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Switch, onClick = onToggle)
                .semantics(mergeDescendants = true) { stateDescription = stateText }
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                icon?.let {
                    Image(it, null, Modifier.size(40.dp).alpha(if (blocked) 0.55f else 1f))
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    app.label, style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                val sub = when {
                    blocked && live && attempts > 0 ->
                        pluralStringResource(R.plurals.attempts_blocked, attempts, latin(attempts))
                    blocked && live -> stringResource(R.string.row_cut_off)
                    blocked -> stringResource(R.string.row_will_cut)
                    !app.usesInternet -> stringResource(R.string.row_no_internet)
                    else -> app.packageName
                }
                Text(
                    sub,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = if (blocked && live) t.danger else t.muted
                    ),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = blocked,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = t.danger,
                    checkedBorderColor = t.danger,
                    uncheckedThumbColor = t.muted,
                    uncheckedTrackColor = t.surface2,
                    uncheckedBorderColor = t.line,
                ),
            )
        }
    }
}

@Composable
private fun Footer(state: UiState, onPower: () -> Unit) {
    val t = LocalTokens.current
    val running = state.running
    val corner by animateDpAsState(if (running) 18.dp else 28.dp, label = "morph")
    val color by animateColorAsState(if (running) t.danger else t.accent, label = "power")
    Column(Modifier.fillMaxWidth().background(t.bg)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(t.line))
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(56.dp)
                    .shadow(16.dp, RoundedCornerShape(corner), ambientColor = color, spotColor = color)
                    .clip(RoundedCornerShape(corner))
                    .background(color)
                    .clickable(
                        role = Role.Button,
                        onClickLabel = stringResource(if (running) R.string.cd_turn_off else R.string.cd_turn_on),
                        onClick = onPower,
                    )
                    .semantics {
                        contentDescription = "power"
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_power), null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                val n = state.blocked.size
                Text(
                    when {
                        running -> pluralStringResource(R.plurals.footer_on, n, latin(n))
                        n == 0 -> stringResource(R.string.footer_pick)
                        else -> pluralStringResource(R.plurals.footer_ready, n, latin(n))
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    when {
                        running && state.attempts > 0 ->
                            pluralStringResource(R.plurals.footer_attempts, state.attempts, latin(state.attempts))
                        running -> stringResource(R.string.footer_on_sub)
                        else -> stringResource(R.string.footer_off_sub)
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(color = t.muted),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InfoSheet(onDismiss: () -> Unit) {
    val t = LocalTokens.current
    val context = LocalContext.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = t.surface,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(t.line)
            )
        },
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(stringResource(R.string.about_title), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.about_body), style = MaterialTheme.typography.bodyLarge.copy(color = t.muted))

            SectionLabel(stringResource(R.string.about_tips))
            Tip(R.drawable.ic_tile, stringResource(R.string.tip_tile))
            Tip(R.drawable.ic_shield, stringResource(R.string.tip_always_on))
            Tip(R.drawable.ic_info, stringResource(R.string.tip_one_vpn))

            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(t.accent)
                    .clickable {
                        runCatching { context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS)) }
                    }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.open_vpn_settings),
                    style = MaterialTheme.typography.titleMedium.copy(color = Color.White),
                )
            }

            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(t.surface2)
                    .padding(14.dp)
            ) {
                Text(stringResource(R.string.privacy_note), style = MaterialTheme.typography.bodyMedium.copy(color = t.muted))
            }
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.credit),
                style = MaterialTheme.typography.labelSmall.copy(color = t.faint),
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    val t = LocalTokens.current
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(color = t.faint),
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun Tip(icon: Int, text: String) {
    val t = LocalTokens.current
    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Icon(painterResource(icon), null, tint = t.accent, modifier = Modifier.size(20.dp).padding(top = 2.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium.copy(color = t.text))
    }
}

/** Numbers are always shown in Latin digits (0-9), also in the Arabic UI. */
internal fun latin(n: Int): String = n.toString()
