package fr.fuelradar

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import fr.fuelradar.ui.SplashScreen
import fr.fuelradar.ui.navigation.AppNav
import fr.fuelradar.ui.theme.FuelRadarTheme
import kotlinx.coroutines.delay

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FuelRadarTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var showSplash by remember { mutableStateOf(true) }
                    LaunchedEffect(Unit) {
                        delay(1200)
                        showSplash = false
                    }
                    AppNav()
                    // Branded splash fades out over the app once it's ready.
                    AnimatedVisibility(visible = showSplash, exit = fadeOut()) {
                        SplashScreen()
                    }
                }
            }
        }
    }
}
