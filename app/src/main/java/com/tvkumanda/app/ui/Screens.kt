@file:Suppress("DEPRECATION")

package com.tvkumanda.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import com.tvkumanda.app.CustomApp
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tvkumanda.app.MainViewModel
import com.tvkumanda.app.Screen
import com.tvkumanda.app.tv.Keys
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ---------- palette ----------
private val BgTop = Color(0xFF161B2E)
private val BgBottom = Color(0xFF0A0C13)
private val Key = Color(0xFF1F2536)
private val KeyLight = Color(0xFF2A3147)
private val TextDim = Color(0xFF8E97B0)
private val Accent = Brush.linearGradient(listOf(Color(0xFF4F8CFF), Color(0xFF7B5CFF)))
private val PowerRed = Color(0xFFFF4D5E)
private val Online = Color(0xFF3DDC97)
private const val MODEL = "Dijitsu Apollo Premium 4K"

@Composable
fun App(vm: MainViewModel) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(BgTop, BgBottom)))) {
        Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(horizontal = 20.dp)) {
            AnimatedContent(vm.screen, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "screen") {
                when (it) {
                    Screen.DEVICES -> DevicesScreen(vm)
                    Screen.PAIRING -> PairingScreen(vm)
                    Screen.REMOTE -> RemoteScreen(vm)
                }
            }
        }
    }
}

// ---------- building blocks ----------

/** A button with press animation, haptics and optional hold-to-repeat. */
@Composable
private fun Pressable(
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    color: Color = Key,
    brush: Brush? = null,
    outline: Boolean = true,
    repeat: Boolean = false,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val click by rememberUpdatedState(onClick)
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, label = "press")
    val bg = if (brush != null) Modifier.background(brush, shape) else Modifier.background(color, shape)
    val border = if (outline) Modifier.border(1.dp, Color.White.copy(alpha = 0.07f), shape) else Modifier
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .then(bg)
            .then(border)
            .background(if (pressed) Color.White.copy(alpha = 0.08f) else Color.Transparent, shape)
            .pointerInput(repeat) {
                detectTapGestures(onPress = {
                    pressed = true
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    click()
                    val job = if (repeat) scope.launch {
                        delay(450)
                        while (true) { click(); delay(110) }
                    } else null
                    tryAwaitRelease()
                    job?.cancel()
                    pressed = false
                })
            },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
private fun IconKey(
    icon: ImageVector,
    label: String? = null,
    size: Dp = 56.dp,
    color: Color = Key,
    tint: Color = Color.White,
    repeat: Boolean = false,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Pressable(Modifier.size(size), color = color, repeat = repeat, onClick = onClick) {
            Icon(icon, label, tint = tint, modifier = Modifier.size(size * 0.42f))
        }
        if (label != null) {
            Spacer(Modifier.height(6.dp))
            Text(label, fontSize = 11.sp, color = TextDim)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(), fontSize = 12.sp, letterSpacing = 1.5.sp, color = TextDim,
        fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
    )
}

@Composable
private fun StatusLine(vm: MainViewModel) {
    if (vm.busy) {
        LinearProgressIndicator(
            Modifier.fillMaxWidth().padding(vertical = 10.dp).clip(CircleShape),
            color = Color(0xFF6C7CFF), trackColor = Key
        )
    }
    vm.status?.let {
        Box(
            Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(14.dp))
                .background(Color(0x33FF4D5E)).padding(12.dp)
        ) { Text(it, fontSize = 13.sp, color = Color(0xFFFFC2C8)) }
    }
}

// ---------- device list ----------

@Composable
private fun DevicesScreen(vm: MainViewModel) {
    var manualIp by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Accent),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.Tv, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Apollo Kumanda", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(MODEL, fontSize = 13.sp, color = TextDim)
            }
            IconKey(Icons.Rounded.Refresh, size = 44.dp, onClick = vm::refresh)
        }
        Spacer(Modifier.height(28.dp))
        StatusLine(vm)
        if (vm.failedHost != null && !vm.busy) {
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pressable(
                    Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(14.dp),
                    brush = Accent, onClick = vm::retry
                ) { Text("Tekrar dene", fontWeight = FontWeight.SemiBold, color = Color.White) }
                Pressable(
                    Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(14.dp), onClick = vm::pairAgain
                ) { Text("Yeniden eşleştir", fontWeight = FontWeight.SemiBold, color = Color.White) }
            }
        }

        SectionTitle("Ağdaki televizyonlar")
        if (vm.devices.isEmpty()) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Key).padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color(0xFF6C7CFF))
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("TV aranıyor…", fontWeight = FontWeight.SemiBold)
                    Text("Telefon ve TV aynı Wi-Fi ağında olmalı", fontSize = 12.sp, color = TextDim)
                }
            }
        }
        vm.devices.forEach { d ->
            Pressable(
                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                shape = RoundedCornerShape(20.dp),
                onClick = { if (!vm.busy) vm.connect(d.host, d.name) }
            ) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(KeyLight),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Rounded.Tv, null, tint = Color(0xFF8FA8FF)) }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(d.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text(d.host, fontSize = 12.sp, color = TextDim)
                    }
                    Icon(Icons.Rounded.ChevronRight, null, tint = TextDim)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        SectionTitle("IP adresiyle bağlan")
        Text(
            "TV'de: Ayarlar › Ağ ve İnternet › bağlı olduğunuz ağ",
            fontSize = 12.sp, color = TextDim, modifier = Modifier.padding(bottom = 10.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = manualIp,
                onValueChange = { manualIp = it.filter { c -> c.isDigit() || c == '.' } },
                placeholder = { Text("192.168.0.48") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(10.dp))
            val ok = manualIp.count { it == '.' } == 3 && !vm.busy
            Pressable(
                Modifier.height(56.dp).width(96.dp), shape = RoundedCornerShape(16.dp),
                brush = if (ok) Accent else null, color = Key,
                onClick = { if (ok) vm.connect(manualIp.trim()) }
            ) { Text("Bağlan", fontWeight = FontWeight.SemiBold, color = if (ok) Color.White else TextDim) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ---------- pairing ----------

@Composable
private fun PairingScreen(vm: MainViewModel) {
    var code by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(40.dp))
        Box(
            Modifier.size(72.dp).clip(RoundedCornerShape(22.dp)).background(Accent)
                .align(Alignment.CenterHorizontally),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Rounded.Link, null, tint = Color.White, modifier = Modifier.size(36.dp)) }
        Spacer(Modifier.height(20.dp))
        Text(
            "TV ile eşleştir", fontSize = 26.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "${vm.tvName} ekranında görünen 6 karakterli kodu girin.",
            color = TextDim, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))
        OutlinedTextField(
            value = code,
            onValueChange = { v -> code = v.uppercase().filter { it in "0123456789ABCDEF" }.take(6) },
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(
                fontSize = 30.sp, letterSpacing = 14.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold
            ),
            shape = RoundedCornerShape(20.dp),
            placeholder = {
                Text(
                    "••••••", fontSize = 30.sp, letterSpacing = 14.sp, color = KeyLight,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
                )
            },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii
            ),
            modifier = Modifier.fillMaxWidth()
        )
        StatusLine(vm)
        Spacer(Modifier.height(16.dp))
        val ok = code.length == 6 && !vm.busy
        Pressable(
            Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp),
            brush = if (ok) Accent else null, onClick = { if (ok) vm.submitCode(code) }
        ) {
            Text("Eşleştir", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (ok) Color.White else TextDim)
        }
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = vm::cancelPairing, modifier = Modifier.fillMaxWidth()) {
            Text("Vazgeç", color = TextDim)
        }
    }
}

// ---------- remote ----------

@Composable
private fun RemoteScreen(vm: MainViewModel) {
    var showMenu by remember { mutableStateOf(false) }
    var showAddApp by remember { mutableStateOf(false) }
    var showKeyboard by remember { mutableStateOf(false) }
    val pager = rememberPagerState { 3 }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        Header(vm, onMenu = { showMenu = true })
        vm.status?.let {
            Pressable(
                Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(14.dp),
                color = Color(0x33FF4D5E), outline = false, onClick = vm::clearStatus
            ) { Text(it, fontSize = 13.sp, color = Color(0xFFFFC2C8), modifier = Modifier.padding(12.dp)) }
        }
        Spacer(Modifier.height(12.dp))
        AppStrip(vm, onAdd = { showAddApp = true })
        Spacer(Modifier.height(8.dp))

        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { page ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                when (page) {
                    0 -> DPadPage(vm)
                    1 -> NumberPage(vm)
                    else -> ExtrasPage(vm)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            listOf("Yön", "Sayılar", "TV tuşları").forEachIndexed { i, title ->
                val active = pager.currentPage == i
                Pressable(
                    Modifier.padding(horizontal = 4.dp).height(28.dp), outline = false,
                    color = if (active) KeyLight else Color.Transparent,
                    onClick = { scope.launch { pager.animateScrollToPage(i) } }
                ) {
                    Text(
                        title, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp),
                        color = if (active) Color.White else TextDim, fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        BottomBar(vm, onKeyboard = { showKeyboard = true })
        Spacer(Modifier.height(8.dp))
    }

    if (showMenu) MenuDialog(vm, onDismiss = { showMenu = false }, onAddApp = { showMenu = false; showAddApp = true })
    if (showAddApp) AddAppDialog(vm) { showAddApp = false }
    if (showKeyboard) KeyboardDialog(vm) { showKeyboard = false }
}

@Composable
private fun Header(vm: MainViewModel, onMenu: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        IconKey(Icons.Rounded.Settings, size = 44.dp, onClick = onMenu)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val on = vm.powerOn != false && !vm.reconnecting
                Box(Modifier.size(9.dp).clip(CircleShape).background(if (on) Online else Color(0xFFFFB547)))
                Spacer(Modifier.width(8.dp))
                Text(
                    vm.tvName ?: "TV", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Text(if (vm.reconnecting) "Yeniden bağlanıyor…" else MODEL, fontSize = 11.sp, color = TextDim)
        }
        Pressable(
            Modifier.size(48.dp), color = PowerRed.copy(alpha = 0.18f),
            onClick = { vm.key(Keys.POWER) }
        ) { Icon(Icons.Rounded.PowerSettingsNew, "Güç", tint = PowerRed, modifier = Modifier.size(26.dp)) }
    }
}

// ---------- app strip ----------

private data class BuiltInApp(val name: String, val target: String, val c1: Long, val c2: Long, val short: String)

private val BUILT_IN_APPS = listOf(
    BuiltInApp("Netflix", "https://www.netflix.com/title", 0xFF1A1A1A, 0xFF000000, "N"),
    BuiltInApp("YouTube", "https://www.youtube.com", 0xFFFF2D2D, 0xFFC00000, "▶"),
    BuiltInApp("Prime Video", "https://app.primevideo.com", 0xFF1A98FF, 0xFF0B4F8A, "prime"),
    BuiltInApp("Disney+", "https://www.disneyplus.com", 0xFF1D5B78, 0xFF0B1A45, "D+"),
    BuiltInApp("HBO Max", "https://play.max.com", 0xFF2B1F7A, 0xFF0B0B26, "max"),
    BuiltInApp("Apple TV", "https://tv.apple.com", 0xFF2A2A2E, 0xFF111114, "tv"),
    BuiltInApp("Spotify", "spotify://", 0xFF1ED760, 0xFF0B6B2E, "♪"),
    BuiltInApp("Play Store", "com.android.vending", 0xFF34A853, 0xFF1A5C2B, "▷"),
)

private val CUSTOM_COLORS = listOf(
    0xFF7B5CFF to 0xFF3B1F99, 0xFFFF8A3D to 0xFFB34A00, 0xFF00BFA5 to 0xFF00695C,
    0xFFE91E63 to 0xFF880E4F, 0xFFFFC107 to 0xFFB37F00,
)

@Composable
private fun AppStrip(vm: MainViewModel, onAdd: () -> Unit) {
    var toDelete by remember { mutableStateOf<CustomApp?>(null) }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        vm.customApps.forEachIndexed { i, app ->
            val (c1, c2) = CUSTOM_COLORS[i % CUSTOM_COLORS.size]
            AppTile(
                app.name, app.name.take(2).uppercase(), Color(c1), Color(c2),
                onClick = { vm.openApp(app.target) }, onLongClick = { toDelete = app }
            )
        }
        BUILT_IN_APPS.forEach { app ->
            AppTile(app.name, app.short, Color(app.c1), Color(app.c2), onClick = { vm.openApp(app.target) })
        }
        AppTile("Ekle", "+", KeyLight, Key, onClick = onAdd)
    }
    toDelete?.let { app ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("${app.name} kaldırılsın mı?") },
            text = { Text("Kısayol listeden çıkarılır. TV'deki uygulama silinmez.") },
            confirmButton = { TextButton(onClick = { vm.removeApp(app); toDelete = null }) { Text("Kaldır") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Vazgeç") } },
        )
    }
}

@Composable
private fun AppTile(
    name: String, short: String, c1: Color, c2: Color,
    onClick: () -> Unit, onLongClick: (() -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    Column(Modifier.width(72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(64.dp).clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(listOf(c1, c2)))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
                .pointerInput(onLongClick) {
                    detectTapGestures(
                        onTap = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() },
                        onLongPress = onLongClick?.let { lc ->
                            { _: Offset -> haptic.performHapticFeedback(HapticFeedbackType.LongPress); lc() }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                short, color = if (name == "Netflix") Color(0xFFE50914) else Color.White,
                fontWeight = FontWeight.Black, fontSize = if (short.length > 3) 15.sp else 22.sp
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(name, fontSize = 11.sp, color = TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---------- pages ----------

@Composable
private fun DPadPage(vm: MainViewModel) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val size = minOf(maxWidth, maxHeight) * 0.94f
        DPad(vm, size)
    }
}

@Composable
private fun DPad(vm: MainViewModel, size: Dp) {
    Box(
        Modifier.size(size)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(KeyLight, Key)))
            .border(2.dp, Accent, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        val arrow = size * 0.3f
        DPadArrow(Icons.Rounded.KeyboardArrowUp, arrow, Modifier.align(Alignment.TopCenter)) { vm.key(Keys.UP) }
        DPadArrow(Icons.Rounded.KeyboardArrowDown, arrow, Modifier.align(Alignment.BottomCenter)) { vm.key(Keys.DOWN) }
        DPadArrow(Icons.Rounded.KeyboardArrowLeft, arrow, Modifier.align(Alignment.CenterStart)) { vm.key(Keys.LEFT) }
        DPadArrow(Icons.Rounded.KeyboardArrowRight, arrow, Modifier.align(Alignment.CenterEnd)) { vm.key(Keys.RIGHT) }
        Pressable(Modifier.size(size * 0.36f), brush = Accent, onClick = { vm.key(Keys.OK) }) {
            Text("OK", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
private fun DPadArrow(icon: ImageVector, size: Dp, modifier: Modifier, onClick: () -> Unit) {
    Pressable(
        modifier.padding(4.dp).size(size),
        color = Color.Transparent, outline = false, repeat = true, onClick = onClick
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.45f)) }
}

@Composable
private fun NumberPage(vm: MainViewModel) {
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "TV", "0", "INFO")
    Column(
        Modifier.fillMaxSize().padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)
    ) {
        keys.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().weight(1f, fill = false), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { k ->
                    val special = k == "TV" || k == "INFO"
                    Pressable(
                        Modifier.weight(1f).height(60.dp), shape = RoundedCornerShape(18.dp),
                        color = if (special) Color.Transparent else Key,
                        onClick = {
                            when (k) {
                                "TV" -> vm.key(Keys.LIVE_TV)
                                "INFO" -> vm.key(Keys.INFO)
                                else -> vm.key(Keys.digit(k.toInt()))
                            }
                        }
                    ) {
                        Text(
                            k, color = Color.White, fontWeight = FontWeight.SemiBold,
                            fontSize = if (special) 18.sp else 26.sp
                        )
                    }
                }
            }
        }
    }
}

/** The rest of the Dijitsu remote: source, colour keys, EXIT, record, SUBT, AUDIO, TXT… */
@Composable
private fun ExtrasPage(vm: MainViewModel) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            IconKey(Icons.Rounded.Input, "Kaynak") { vm.key(Keys.INPUT) }
            IconKey(Icons.Rounded.Apps, "Uygulamalar") { vm.key(Keys.ALL_APPS) }
            IconKey(Icons.Rounded.Person, "Profil") { vm.key(Keys.PROFILE) }
            IconKey(Icons.Rounded.Tune, "Ayarlar") { vm.key(Keys.SETTINGS) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(
                Keys.RED to Color(0xFFE53935), Keys.GREEN to Color(0xFF43A047),
                Keys.YELLOW to Color(0xFFFDD835), Keys.BLUE to Color(0xFF1E88E5)
            ).forEach { (code, c) ->
                Pressable(
                    Modifier.weight(1f).height(38.dp), shape = CircleShape,
                    color = c.copy(alpha = 0.9f), onClick = { vm.key(code) }
                ) {}
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallKey("EXIT", Modifier.weight(1f)) { vm.key(Keys.EXIT) }
            SmallKey(null, Modifier.weight(1f), Icons.Rounded.FiberManualRecord, Color(0xFFE53935)) { vm.key(Keys.RECORD) }
            SmallKey(null, Modifier.weight(1f), Icons.Rounded.PlayArrow) { vm.key(Keys.PLAY_PAUSE) }
            SmallKey(null, Modifier.weight(1f), Icons.Rounded.Stop) { vm.key(Keys.STOP) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallKey(null, Modifier.weight(1f), Icons.Rounded.FastRewind) { vm.key(Keys.REWIND) }
            SmallKey(null, Modifier.weight(1f), Icons.Rounded.FastForward) { vm.key(Keys.FAST_FORWARD) }
            SmallKey("SUBT", Modifier.weight(1f)) { vm.key(Keys.CAPTIONS) }
            SmallKey("AUDIO", Modifier.weight(1f)) { vm.key(Keys.AUDIO_TRACK) }
            SmallKey("TXT", Modifier.weight(1f)) { vm.key(Keys.TELETEXT) }
        }
    }
}

@Composable
private fun SmallKey(
    text: String?, modifier: Modifier, icon: ImageVector? = null, tint: Color = Color.White,
    onClick: () -> Unit,
) {
    Pressable(modifier.height(46.dp), shape = CircleShape, onClick = onClick) {
        if (icon != null) Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        else Text(text ?: "", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

// ---------- bottom bar: VOL · 6 keys · CH ----------

@Composable
private fun BottomBar(vm: MainViewModel, onKeyboard: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(196.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
    ) {
        Rocker(
            "VOL", vm.volume?.first?.toString(), Icons.Rounded.Add, Icons.Rounded.Remove,
            { vm.key(Keys.VOLUME_UP) }, { vm.key(Keys.VOLUME_DOWN) },
            level = vm.volume?.let { (l, m) -> if (m > 0) l.toFloat() / m else 0f }
        )
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconKey(Icons.Rounded.Keyboard, size = 60.dp, onClick = onKeyboard)
                IconKey(Icons.Rounded.Home, size = 60.dp) { vm.key(Keys.HOME) }
                IconKey(Icons.Rounded.Input, size = 60.dp) { vm.key(Keys.INPUT) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconKey(Icons.Rounded.VolumeOff, size = 60.dp) { vm.key(Keys.MUTE) }
                MicKey(vm)
                IconKey(Icons.Rounded.ArrowBack, size = 60.dp) { vm.key(Keys.BACK) }
            }
        }
        Rocker(
            "CH", null, Icons.Rounded.KeyboardArrowUp, Icons.Rounded.KeyboardArrowDown,
            { vm.key(Keys.CHANNEL_UP) }, { vm.key(Keys.CHANNEL_DOWN) }
        )
    }
}

@Composable
private fun Rocker(
    title: String, subtitle: String?, upIcon: ImageVector, downIcon: ImageVector,
    onUp: () -> Unit, onDown: () -> Unit, level: Float? = null,
) {
    Box(
        Modifier.width(66.dp).fillMaxHeight().clip(CircleShape).background(Key)
            .border(1.dp, Color.White.copy(alpha = 0.07f), CircleShape)
    ) {
        if (level != null) {
            Box(
                Modifier.fillMaxWidth().fillMaxHeight(level.coerceIn(0f, 1f)).align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color(0x004F8CFF), Color(0xAA4F8CFF))))
            )
        }
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Pressable(
                Modifier.fillMaxWidth().weight(1f), color = Color.Transparent, outline = false,
                repeat = true, onClick = onUp
            ) { Icon(upIcon, "$title artır", tint = Color.White, modifier = Modifier.size(28.dp)) }
            Text(title, fontSize = 12.sp, letterSpacing = 1.sp, color = TextDim, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) Text(subtitle, fontSize = 14.sp, color = Color.White)
            Pressable(
                Modifier.fillMaxWidth().weight(1f), color = Color.Transparent, outline = false,
                repeat = true, onClick = onDown
            ) { Icon(downIcon, "$title azalt", tint = Color.White, modifier = Modifier.size(28.dp)) }
        }
    }
}

/** Google mic: tap, speak, it stops by itself when you go quiet (or tap again). */
@Composable
private fun MicKey(vm: MainViewModel) {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) vm.toggleVoice() else vm.micDenied()
    }
    val pulse = rememberInfiniteTransition(label = "mic")
    val glow by pulse.animateFloat(1f, 1.15f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "glow")
    Pressable(
        Modifier.size(60.dp).graphicsLayer {
            val k = if (vm.listening) glow else 1f
            scaleX = k; scaleY = k
        },
        brush = if (vm.listening) Accent else null, color = KeyLight,
        onClick = {
            val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            if (granted || vm.listening) vm.toggleVoice() else launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    ) { Icon(Icons.Rounded.Mic, "Sesli komut", tint = Color.White, modifier = Modifier.size(28.dp)) }
}

// ---------- dialogs ----------

@Composable
private fun MenuDialog(vm: MainViewModel, onDismiss: () -> Unit, onAddApp: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Key,
        title = { Text("Ayarlar") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MenuRow(Icons.Rounded.AddCircle, "Uygulama kısayolu ekle", onAddApp)
                MenuRow(Icons.Rounded.SwapHoriz, "Başka bir TV'ye bağlan") { onDismiss(); vm.disconnect() }
                Text(
                    "İpucu: Telefonun ses tuşları da TV'nin sesini değiştirir. " +
                        "Uygulama kısayolunu kaldırmak için kutucuğa basılı tutun.",
                    fontSize = 12.sp, color = TextDim
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Kapat") } },
    )
}

@Composable
private fun MenuRow(icon: ImageVector, text: String, onClick: () -> Unit) {
    Pressable(Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp), color = KeyLight, onClick = onClick) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color(0xFF8FA8FF))
            Spacer(Modifier.width(12.dp))
            Text(text, color = Color.White)
        }
    }
}

@Composable
private fun AddAppDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    val current = vm.currentApp
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Key,
        title = { Text("Uygulama ekle") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "En kolayı: eklemek istediğiniz uygulamayı (ör. İnat Box) TV'de açın, " +
                        "sonra bu pencereye dönün.",
                    fontSize = 13.sp, color = TextDim
                )
                if (current != null) {
                    val label = current.second?.takeIf { it.isNotBlank() } ?: current.first
                    Pressable(
                        Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), brush = Accent,
                        onClick = { vm.addApp(name.ifBlank { label }, current.first); onDismiss() }
                    ) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Text("TV'de şu an açık", fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
                            Text(label, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(current.first, fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
                            Spacer(Modifier.height(6.dp))
                            Text("＋ Bunu kısayollara ekle", fontWeight = FontWeight.SemiBold, color = Color.White)
                        }
                    }
                } else {
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(KeyLight).padding(14.dp)
                    ) {
                        Text(
                            "TV'de açık uygulama henüz algılanmadı. Uygulamayı TV'de açıp birkaç saniye bekleyin.",
                            fontSize = 12.sp, color = TextDim
                        )
                    }
                }
                Text("veya elle girin", fontSize = 12.sp, color = TextDim)
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, singleLine = true,
                    label = { Text("Ad (ör. İnat Box)") }, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = target, onValueChange = { target = it.trim() }, singleLine = true,
                    label = { Text("Paket adı veya bağlantı") },
                    placeholder = { Text("com.ornek.uygulama") }, modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(enabled = target.isNotBlank(), onClick = { vm.addApp(name, target); onDismiss() }) {
                Text("Ekle")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}

@Composable
private fun KeyboardDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Key,
        title = { Text("TV'ye yaz") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (vm.imeReady) "Yazdıklarınız TV'deki yazı alanına anında gider."
                    else "Önce TV'de bir arama veya yazı kutusuna gelin (OK'ye basın), sonra yazın.",
                    fontSize = 12.sp, color = TextDim
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; vm.sendText(it) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { vm.key(Keys.ENTER) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus)
                )
            }
        },
        confirmButton = { TextButton(onClick = { vm.key(Keys.ENTER); onDismiss() }) { Text("Ara / Tamam") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Kapat") } },
    )
}
