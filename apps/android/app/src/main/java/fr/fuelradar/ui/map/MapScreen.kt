package fr.fuelradar.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.clustering.Clustering
import com.google.maps.android.compose.rememberCameraPositionState
import fr.fuelradar.BuildConfig
import fr.fuelradar.domain.priceColor

@OptIn(MapsComposeExperimentalApi::class)
@Composable
fun MapScreen(
    onOpenStation: (Long) -> Unit,
    viewModel: MapViewModel = viewModel(),
) {
    if (BuildConfig.MAPS_API_KEY.isBlank()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Carte indisponible", style = MaterialTheme.typography.titleMedium)
            Text(
                "Clé Google Maps manquante. Ajoutez MAPS_API_KEY dans local.properties.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(48.8566, 2.3522), 11f)
    }

    LaunchedEffect(cameraPositionState.isMoving) {
        if (!cameraPositionState.isMoving) {
            val c = cameraPositionState.position.target
            viewModel.load(c.latitude, c.longitude, 25.0)
        }
    }

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
    ) {
        Clustering(
            items = state.items,
            onClusterItemClick = { item ->
                onOpenStation(item.station.id)
                true
            },
            clusterItemContent = { item ->
                val color = item.price?.let { priceColor(it, state.pMin, state.pMax) } ?: Color.Gray
                PriceMarker(color)
            },
        )
    }
}

@Composable
private fun PriceMarker(color: Color) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .background(color, CircleShape)
            .border(2.dp, Color.White, CircleShape),
    )
}
