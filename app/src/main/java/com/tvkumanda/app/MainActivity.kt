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
        super.onCreate(savedInstanceState)
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
