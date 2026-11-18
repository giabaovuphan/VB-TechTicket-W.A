package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.ui.AppNavigation
import com.example.ui.theme.TechTicketTheme
import com.example.util.SystemNotificationHelper

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SystemNotificationHelper.ensureChannel(this)
        setContent {
            TechTicketTheme {
                AppNavigation()
            }
        }
    }
}
