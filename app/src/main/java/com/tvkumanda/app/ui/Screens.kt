@file:Suppress("DEPRECATION")

package com.tvkumanda.app.ui

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

private enum class Tab(val title: String) { REMOTE("Kumanda"), APPS("Uygulamalar"), TV("TV") }

@Composable
private fun RemoteScreen(vm: MainViewModel) {
    var tab by rememberSaveable { mutableStateOf(Tab.REMOTE) }
    Column(Modifier.fillMaxSize()) {
        Header(vm)
        Spacer(Modifier.height(14.dp))
        TabBar(tab) { tab = it }
        Spacer(Modifier.height(18.dp))
        Box(Modifier.weight(1f)) {
            AnimatedContent(tab, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "tab") { t ->
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (t) {
                        Tab.REMOTE -> RemoteTab(vm)
                        Tab.APPS -> AppsTab(vm)
                        Tab.TV -> TvTab(vm)
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
private fun Header(vm: MainViewModel) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                vm.tvName ?: "TV", fontSize = 22.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                val on = vm.powerOn != false
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (on) Online else Color(0xFFFFB547)))
                Spacer(Modifier.width(6.dp))
                val info = buildString {
                    append(if (on) "Bağlı" else "Bekleme modunda")
                    vm.volume?.let { (l, _) -> append(" · Ses $l") }
                }
                Text(info, fontSize = 12.sp, color = TextDim)
            }
        }
        IconKey(Icons.Rounded.SwapHoriz, size = 44.dp, onClick = vm::disconnect)
        Spacer(Modifier.width(10.dp))
        Pressable(
            Modifier.size(52.dp), color = PowerRed.copy(alpha = 0.18f),
            onClick = { vm.key(Keys.POWER) }
        ) { Icon(Icons.Rounded.PowerSettingsNew, "Güç", tint = PowerRed, modifier = Modifier.size(26.dp)) }
    }
}

@Composable
private fun TabBar(selected: Tab, onSelect: (Tab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(CircleShape).background(Key).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Tab.entries.forEach { t ->
            val active = t == selected
            Pressable(
                Modifier.weight(1f).height(40.dp), outline = false,
                brush = if (active) Accent else null, color = Color.Transparent,
                onClick = { onSelect(t) }
            ) {
                Text(
                    t.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    color = if (active) Color.White else TextDim
                )
            }
        }
    }
}

@Composable
private fun RemoteTab(vm: MainViewModel) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        IconKey(Icons.Rounded.Input, "Kaynak") { vm.key(Keys.INPUT) }
        IconKey(Icons.Rounded.LiveTv, "Canlı TV") { vm.key(Keys.LIVE_TV) }
        IconKey(Icons.Rounded.Search, "Ara") { vm.key(Keys.SEARCH) }
        IconKey(Icons.Rounded.Settings, "Ayarlar") { vm.key(Keys.SETTINGS) }
    }
    Spacer(Modifier.height(26.dp))
    DPad(vm)
    Spacer(Modifier.height(26.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        IconKey(Icons.Rounded.ArrowBack, "Geri", size = 60.dp) { vm.key(Keys.BACK) }
        IconKey(Icons.Rounded.Home, "Ana ekran", size = 60.dp) { vm.key(Keys.HOME) }
        IconKey(Icons.Rounded.Menu, "Menü", size = 60.dp) { vm.key(Keys.MENU) }
    }
    Spacer(Modifier.height(24.dp))
    Row(
        Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Rocker("SES", Icons.Rounded.Add, Icons.Rounded.Remove, { vm.key(Keys.VOLUME_UP) }, { vm.key(Keys.VOLUME_DOWN) })
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconKey(Icons.Rounded.VolumeOff, "Sessiz", size = 60.dp) { vm.key(Keys.MUTE) }
            Spacer(Modifier.height(16.dp))
            IconKey(Icons.Rounded.History, "Son kanal", size = 60.dp) { vm.key(Keys.LAST_CHANNEL) }
        }
        Rocker(
            "KANAL", Icons.Rounded.KeyboardArrowUp, Icons.Rounded.KeyboardArrowDown,
            { vm.key(Keys.CHANNEL_UP) }, { vm.key(Keys.CHANNEL_DOWN) }
        )
    }
    Spacer(Modifier.height(24.dp))
    MediaBar(vm)
}

@Composable
private fun DPad(vm: MainViewModel) {
    Box(
        Modifier.size(276.dp)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(KeyLight, Key)))
            .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        DPadArrow(Icons.Rounded.KeyboardArrowUp, Modifier.align(Alignment.TopCenter)) { vm.key(Keys.UP) }
        DPadArrow(Icons.Rounded.KeyboardArrowDown, Modifier.align(Alignment.BottomCenter)) { vm.key(Keys.DOWN) }
        DPadArrow(Icons.Rounded.KeyboardArrowLeft, Modifier.align(Alignment.CenterStart)) { vm.key(Keys.LEFT) }
        DPadArrow(Icons.Rounded.KeyboardArrowRight, Modifier.align(Alignment.CenterEnd)) { vm.key(Keys.RIGHT) }
        Pressable(Modifier.size(108.dp), brush = Accent, onClick = { vm.key(Keys.OK) }) {
            Text("OK", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
private fun DPadArrow(icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Pressable(
        modifier.padding(6.dp).size(82.dp),
        color = Color.Transparent, outline = false, repeat = true, onClick = onClick
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(38.dp)) }
}

@Composable
private fun Rocker(
    title: String, upIcon: ImageVector, downIcon: ImageVector,
    onUp: () -> Unit, onDown: () -> Unit,
) {
    Column(
        Modifier.width(74.dp).clip(CircleShape).background(Key)
            .border(1.dp, Color.White.copy(alpha = 0.07f), CircleShape),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Pressable(Modifier.size(74.dp), color = Color.Transparent, outline = false, repeat = true, onClick = onUp) {
            Icon(upIcon, "$title artır", tint = Color.White, modifier = Modifier.size(30.dp))
        }
        Text(title, fontSize = 11.sp, letterSpacing = 1.sp, color = TextDim, fontWeight = FontWeight.SemiBold)
        Pressable(Modifier.size(74.dp), color = Color.Transparent, outline = false, repeat = true, onClick = onDown) {
            Icon(downIcon, "$title azalt", tint = Color.White, modifier = Modifier.size(30.dp))
        }
    }
}

@Composable
private fun MediaBar(vm: MainViewModel) {
    Row(
        Modifier.fillMaxWidth().clip(CircleShape).background(Key)
            .border(1.dp, Color.White.copy(alpha = 0.07f), CircleShape).padding(6.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
    ) {
        MediaKey(Icons.Rounded.SkipPrevious) { vm.key(Keys.PREVIOUS) }
        MediaKey(Icons.Rounded.FastRewind) { vm.key(Keys.REWIND) }
        MediaKey(Icons.Rounded.PlayArrow, main = true) { vm.key(Keys.PLAY_PAUSE) }
        MediaKey(Icons.Rounded.FastForward) { vm.key(Keys.FAST_FORWARD) }
        MediaKey(Icons.Rounded.SkipNext) { vm.key(Keys.NEXT) }
    }
}

@Composable
private fun MediaKey(icon: ImageVector, main: Boolean = false, onClick: () -> Unit) {
    Pressable(
        Modifier.size(if (main) 56.dp else 48.dp), outline = false,
        brush = if (main) Accent else null, color = Color.Transparent, onClick = onClick
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(if (main) 30.dp else 24.dp)) }
}

// ---------- apps ----------

private data class AppShortcut(val name: String, val pkg: String, val c1: Long, val c2: Long)

private val APPS = listOf(
    AppShortcut("YouTube", "com.google.android.youtube.tv", 0xFFFF2D2D, 0xFFB00000),
    AppShortcut("Netflix", "com.netflix.ninja", 0xFFE50914, 0xFF5C0006),
    AppShortcut("Prime Video", "com.amazon.amazonvideo.livingroom", 0xFF1A98FF, 0xFF0B4F8A),
    AppShortcut("Disney+", "com.disney.disneyplus", 0xFF1F3C8F, 0xFF0B1A45),
    AppShortcut("HBO Max", "com.wbd.stream", 0xFF5B2EFF, 0xFF1B0B66),
    AppShortcut("Spotify", "com.spotify.tv.android", 0xFF1ED760, 0xFF0B6B2E),
    AppShortcut("YouTube Music", "com.google.android.youtube.tvmusic", 0xFFFF4E45, 0xFF7A0A06),
    AppShortcut("Google Play", "com.android.vending", 0xFF34A853, 0xFF1A5C2B),
)

@Composable
private fun AppsTab(vm: MainViewModel) {
    Text(
        "Uygulama TV'de yüklüyse doğrudan açılır.",
        fontSize = 12.sp, color = TextDim, modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
    )
    APPS.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            row.forEach { app ->
                Pressable(
                    Modifier.weight(1f).height(96.dp), shape = RoundedCornerShape(22.dp),
                    brush = Brush.linearGradient(listOf(Color(app.c1), Color(app.c2))),
                    onClick = { vm.app("market://launch?id=${app.pkg}") }
                ) {
                    Text(
                        app.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White,
                        textAlign = TextAlign.Center, modifier = Modifier.padding(8.dp)
                    )
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

// ---------- TV panel keys ----------

@Composable
private fun TvTab(vm: MainViewModel) {
    SectionTitle("Kanal numarası")
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("⌫", "0", "OK"))
    rows.forEach { row ->
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { k ->
                Pressable(
                    Modifier.weight(1f).height(58.dp), shape = RoundedCornerShape(18.dp),
                    brush = if (k == "OK") Accent else null,
                    onClick = {
                        when (k) {
                            "⌫" -> vm.key(Keys.DEL)
                            "OK" -> vm.key(Keys.OK)
                            else -> vm.key(Keys.digit(k.toInt()))
                        }
                    }
                ) { Text(k, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Color.White) }
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    SectionTitle("Renkli tuşlar")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(
            Keys.RED to Color(0xFFE53935), Keys.GREEN to Color(0xFF43A047),
            Keys.YELLOW to Color(0xFFFDD835), Keys.BLUE to Color(0xFF1E88E5)
        ).forEach { (code, c) ->
            Pressable(
                Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(14.dp),
                color = c.copy(alpha = 0.85f), onClick = { vm.key(code) }
            ) {}
        }
    }
    Spacer(Modifier.height(24.dp))
    SectionTitle("TV işlevleri")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        IconKey(Icons.Rounded.ViewList, "Rehber") { vm.key(Keys.GUIDE) }
        IconKey(Icons.Rounded.Info, "Bilgi") { vm.key(Keys.INFO) }
        IconKey(Icons.Rounded.Subtitles, "Altyazı") { vm.key(Keys.CAPTIONS) }
        IconKey(Icons.Rounded.Audiotrack, "Ses dili") { vm.key(Keys.AUDIO_TRACK) }
    }
}
