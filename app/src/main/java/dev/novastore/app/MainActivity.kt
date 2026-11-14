package dev.novastore.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.novastore.app.ui.NovaStoreRoot
import dev.novastore.app.ui.theme.NovaStoreTheme

/** NovaStore tek-Activity mimarisi: tüm ekranlar Compose Navigation ile yönetilir. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            NovaStoreTheme {
                NovaStoreRoot()
            }
        }
    }
}
