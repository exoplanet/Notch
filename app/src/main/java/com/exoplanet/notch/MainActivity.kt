package com.exoplanet.notch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.exoplanet.notch.ui.NotchApp
import com.exoplanet.notch.ui.NotchTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NotchTheme {
                NotchApp()
            }
        }
    }
}
