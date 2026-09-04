package com.mbk.hayplan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mbk.hayplan.ui.HayPlanApp
import com.mbk.hayplan.ui.theme.HayPlanTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HayPlanTheme {
                HayPlanApp()
            }
        }
    }
}
