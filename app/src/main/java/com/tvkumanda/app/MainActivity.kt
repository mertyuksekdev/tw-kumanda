package com.tvkumanda.app

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.tvkumanda.app.tv.Keys
import com.tvkumanda.app.ui.App

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installCrashLog()
        super.onCreate(savedInstanceState)
        showLastCrash()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF8AB4F8),
                    background = Color(0xFF121212),
                    surface = Color(0xFF121212),
                )
            ) {
                Surface(Modifier.fillMaxSize()) { App(vm) }
            }
        }
    }

    /** Saves the error of a crash so the next start can show it (and it can be sent to us). */
    private fun installCrashLog() {
        val file = java.io.File(filesDir, "crash.txt")
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                file.writeText(
                    "Sürüm ${BuildConfigInfo.version}\n" + android.util.Log.getStackTraceString(e).take(4000)
                )
            }
            previous?.uncaughtException(t, e)
        }
    }

    private fun showLastCrash() {
        val file = java.io.File(filesDir, "crash.txt")
        if (!file.exists()) return
        val text = runCatching { file.readText() }.getOrDefault("")
        file.delete()
        android.app.AlertDialog.Builder(this)
            .setTitle("Uygulama son kez hata verip kapandı")
            .setMessage("Bu ekranın fotoğrafını gönderirseniz sorunu düzeltebiliriz.\n\n" + text.take(1500))
            .setPositiveButton("Kopyala") { _, _ ->
                val cm = getSystemService(android.content.ClipboardManager::class.java)
                cm.setPrimaryClip(android.content.ClipData.newPlainText("hata", text))
            }
            .setNegativeButton("Kapat", null)
            .show()
    }

    override fun onStart() {
        super.onStart()
        vm.onForeground()
    }

    /** The phone's own volume buttons control the TV while connected. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (vm.isConnected) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> { vm.key(Keys.VOLUME_UP); return true }
                KeyEvent.KEYCODE_VOLUME_DOWN -> { vm.key(Keys.VOLUME_DOWN); return true }
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}

private object BuildConfigInfo {
    val version: String get() = runCatching {
        val ctx = Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null)
            as android.content.Context
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
    }.getOrNull() ?: "?"
}
