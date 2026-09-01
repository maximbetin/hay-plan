package com.mbk.outing

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mbk.outing.ui.OutingApp
import com.mbk.outing.ui.theme.OutingTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OutingTheme {
                OutingApp()
            }
        }
    }
}
