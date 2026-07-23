package fr.fuelradar.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.fuelradar.R

/** Branded launch screen: teal gradient, the pump logo, the app name + tagline. */
@Composable
fun SplashScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF00857A), Color(0xFF00564E))),
            ),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(148.dp),
        )
        Text(
            stringResource(R.string.app_name),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 30.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.app_tagline),
            color = Color.White.copy(alpha = 0.82f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
    }
}
