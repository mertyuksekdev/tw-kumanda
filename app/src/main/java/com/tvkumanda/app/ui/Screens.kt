package com.tvkumanda.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tvkumanda.app.MainViewModel
import com.tvkumanda.app.Screen
import com.tvkumanda.app.tv.Keys

private val ButtonBg = Color(0xFF2A2A2E)

@Composable
fun App(vm: MainViewModel) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(16.dp)
    ) {
        when (vm.screen) {
            Screen.DEVICES -> DevicesScreen(vm)
            Screen.PAIRING -> PairingScreen(vm)
            Screen.REMOTE -> RemoteScreen(vm)
        }
    }
}

@Composable
private fun StatusLine(vm: MainViewModel) {
    if (vm.busy) {
        LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
    }
    vm.status?.let {
        Text(it, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(vertical = 8.dp))
    }
}

@Composable
private fun ColumnScope.DevicesScreen(vm: MainViewModel) {
    var manualIp by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Google TV Kumanda", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        IconButton(onClick = vm::refresh) { Icon(Icons.Default.Refresh, "Yenile") }
    }
    Text(
        "Telefon ve TV aynı Wi-Fi ağında olmalı.",
        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp
    )
    StatusLine(vm)
    Spacer(Modifier.height(12.dp))
    Text("Ağdaki TV'ler", fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    if (vm.devices.isEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 12.dp)) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Text("Aranıyor…")
        }
    }
    LazyColumn(Modifier.weight(1f, fill = false)) {
        items(vm.devices, key = { it.host }) { d ->
            Card(
                Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    .clickable(enabled = !vm.busy) { vm.connect(d.host, d.name) }
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(d.name, fontWeight = FontWeight.SemiBold)
                        Text(d.host, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(20.dp))
    Text("Bulamadın mı? IP adresini gir", fontWeight = FontWeight.SemiBold)
    Text(
        "TV'de: Ayarlar › Ağ ve İnternet › bağlı ağ",
        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = manualIp,
            onValueChange = { manualIp = it.filter { c -> c.isDigit() || c == '.' } },
            placeholder = { Text("192.168.1.50") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Button(onClick = { vm.connect(manualIp.trim()) }, enabled = manualIp.count { it == '.' } == 3 && !vm.busy) {
            Text("Bağlan")
        }
    }
}

@Composable
private fun PairingScreen(vm: MainViewModel) {
    var code by remember { mutableStateOf("") }
    Text("Eşleştirme", fontSize = 24.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    Text("${vm.tvName} ekranında 6 karakterli bir kod göründü. O kodu buraya gir.")
    Spacer(Modifier.height(20.dp))
    OutlinedTextField(
        value = code,
        onValueChange = { v -> code = v.uppercase().filter { it in "0123456789ABCDEF" }.take(6) },
        label = { Text("Kod") },
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(fontSize = 28.sp, letterSpacing = 6.sp, textAlign = TextAlign.Center),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii
        ),
        modifier = Modifier.fillMaxWidth()
    )
    StatusLine(vm)
    Spacer(Modifier.height(12.dp))
    Button(
        onClick = { vm.submitCode(code) },
        enabled = code.length == 6 && !vm.busy,
        modifier = Modifier.fillMaxWidth().height(52.dp)
    ) { Text("Eşleştir") }
    TextButton(onClick = vm::cancelPairing, modifier = Modifier.fillMaxWidth()) { Text("İptal") }
}

@Composable
private fun RemoteScreen(vm: MainViewModel) {
    var showNumbers by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(vm.tvName ?: "", fontWeight = FontWeight.SemiBold, maxLines = 1)
                val info = buildString {
                    append(when (vm.powerOn) { true -> "Açık"; false -> "Bekleme modunda"; null -> "Bağlı" })
                    vm.volume?.let { (l, m) -> append(" · Ses $l/$m") }
                }
                Text(info, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = vm::disconnect) { Text("TV değiştir") }
        }
        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            RoundKey(text = "⏻", bg = Color(0xFFB3261E)) { vm.key(Keys.POWER) }
            RoundKey(text = "⇄", label = "Kaynak") { vm.key(Keys.INPUT) }
            RoundKey(icon = Icons.Default.Search) { vm.key(Keys.SEARCH) }
            RoundKey(icon = Icons.Default.Settings) { vm.key(Keys.SETTINGS) }
        }
        Spacer(Modifier.height(24.dp))

        DPad(vm)
        Spacer(Modifier.height(20.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            RoundKey(icon = Icons.AutoMirrored.Filled.ArrowBack) { vm.key(Keys.BACK) }
            RoundKey(icon = Icons.Default.Home) { vm.key(Keys.HOME) }
            RoundKey(text = "123") { showNumbers = !showNumbers }
        }
        Spacer(Modifier.height(20.dp))

        if (showNumbers) {
            NumberPad(vm)
            Spacer(Modifier.height(20.dp))
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Rocker("SES", { vm.key(Keys.VOLUME_UP) }, { vm.key(Keys.VOLUME_DOWN) })
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                RoundKey(text = "🔇", label = "Sessiz") { vm.key(Keys.MUTE) }
            }
            Rocker("KANAL", { vm.key(Keys.CHANNEL_UP) }, { vm.key(Keys.CHANNEL_DOWN) })
        }
        Spacer(Modifier.height(20.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            RoundKey(text = "⏮", size = 52.dp) { vm.key(Keys.PREVIOUS) }
            RoundKey(text = "⏪", size = 52.dp) { vm.key(Keys.REWIND) }
            RoundKey(text = "⏯", size = 52.dp, bg = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)) {
                vm.key(Keys.PLAY_PAUSE)
            }
            RoundKey(text = "⏩", size = 52.dp) { vm.key(Keys.FAST_FORWARD) }
            RoundKey(text = "⏭", size = 52.dp) { vm.key(Keys.NEXT) }
        }
        Spacer(Modifier.height(24.dp))

        Text("Uygulamalar", fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            APPS.forEach { (name, link) ->
                AssistChip(onClick = { vm.app(link) }, label = { Text(name) }, modifier = Modifier.padding(end = 8.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

private val APPS = listOf(
    "YouTube" to "https://www.youtube.com",
    "Netflix" to "https://www.netflix.com/title",
    "Prime Video" to "https://app.primevideo.com",
    "Disney+" to "https://www.disneyplus.com",
    "Spotify" to "spotify://",
    "Play Store" to "market://details?id=com.google.android.youtube.tv",
)

@Composable
private fun DPad(vm: MainViewModel) {
    Box(
        Modifier.size(260.dp).clip(CircleShape).background(ButtonBg),
        contentAlignment = Alignment.Center
    ) {
        PadArrow(Icons.Default.KeyboardArrowUp, Modifier.align(Alignment.TopCenter)) { vm.key(Keys.UP) }
        PadArrow(Icons.Default.KeyboardArrowDown, Modifier.align(Alignment.BottomCenter)) { vm.key(Keys.DOWN) }
        PadArrow(Icons.Default.KeyboardArrowLeft, Modifier.align(Alignment.CenterStart)) { vm.key(Keys.LEFT) }
        PadArrow(Icons.Default.KeyboardArrowRight, Modifier.align(Alignment.CenterEnd)) { vm.key(Keys.RIGHT) }
        val haptic = LocalHapticFeedback.current
        Box(
            Modifier.size(100.dp).clip(CircleShape).background(Color(0xFF3C3C42))
                .clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.key(Keys.OK) },
            contentAlignment = Alignment.Center
        ) { Text("OK", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun PadArrow(icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier.size(80.dp).clip(CircleShape)
            .clickable { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() },
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, Modifier.size(40.dp)) }
}

@Composable
private fun RoundKey(
    text: String? = null,
    icon: ImageVector? = null,
    label: String? = null,
    size: Dp = 60.dp,
    bg: Color = ButtonBg,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(size).clip(CircleShape).background(bg)
                .clickable { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() },
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) Icon(icon, label) else Text(text ?: "", fontSize = 20.sp)
        }
        if (label != null) Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Rocker(title: String, onUp: () -> Unit, onDown: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Column(
        Modifier.width(72.dp).clip(RoundedCornerShape(36.dp)).background(ButtonBg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.fillMaxWidth().height(64.dp)
                .clickable { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onUp() },
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.Add, "$title artır") }
        Text(title, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(
            Modifier.fillMaxWidth().height(64.dp)
                .clickable { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onDown() },
            contentAlignment = Alignment.Center
        ) { Text("—", fontSize = 22.sp) }
    }
}

@Composable
private fun NumberPad(vm: MainViewModel) {
    val rows = listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9), listOf(-1, 0, -2))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                row.forEach { n ->
                    when (n) {
                        -1 -> Spacer(Modifier.size(60.dp))
                        -2 -> RoundKey(text = "⌫") { vm.key(Keys.DEL) }
                        else -> RoundKey(text = n.toString()) { vm.key(Keys.digit(n)) }
                    }
                }
            }
        }
    }
}
