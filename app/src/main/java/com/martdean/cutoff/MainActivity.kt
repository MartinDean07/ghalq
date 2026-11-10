package com.martdean.cutoff

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.martdean.cutoff.ui.CutoffApp
import com.martdean.cutoff.ui.theme.CutoffTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(0x00000000, 0x00000000),
            navigationBarStyle = SystemBarStyle.auto(0x00000000, 0x00000000),
        )
        super.onCreate(savedInstanceState)
        setContent {
            CutoffTheme {
                CutoffApp()
            }
        }
    }
}
