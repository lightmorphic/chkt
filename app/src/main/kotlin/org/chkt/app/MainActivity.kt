package org.chkt.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.chkt.app.ui.ChktApp
import org.chkt.app.ui.theme.ChktTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ChktTheme {
                ChktApp()
            }
        }
    }
}
