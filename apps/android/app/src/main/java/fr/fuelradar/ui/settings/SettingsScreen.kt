package fr.fuelradar.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationDisabled
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import fr.fuelradar.R
import fr.fuelradar.data.ServiceLocator
import fr.fuelradar.data.prefs.AppSettings
import fr.fuelradar.ui.common.relativeTime
import kotlinx.coroutines.launch

private val STARTUP_TABS = listOf(
    "map" to R.string.tab_map,
    "stations" to R.string.tab_stations,
    "favorites" to R.string.tab_favorites,
    "trends" to R.string.tab_trends,
)

private val LANGUAGES = listOf(
    "" to R.string.lang_system,
    "fr" to R.string.lang_fr,
    "en" to R.string.lang_en,
    "es" to R.string.lang_es,
    "pt" to R.string.lang_pt,
)

private const val PRIVACY_URL = "https://lingelo.github.io/fuel-radar/privacy.html"
private const val APP_URL = "https://lingelo.github.io/fuel-radar/"
private const val AUTHOR_URL = "https://angelo-lima.fr"

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val store = ServiceLocator.settings
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val settings by store.settings.collectAsStateWithLifecycle(AppSettings())
    val filters by ServiceLocator.filters.filters.collectAsStateWithLifecycle(fr.fuelradar.data.prefs.Filters())
    val lastUpdate by produceState<String?>(initialValue = null) {
        value = ServiceLocator.stations.meta()?.lastUpdate
    }

    // Locate-me (reverse-geocode + share to the whole app), mirror of the map/list.
    val fused = androidx.compose.runtime.remember { LocationServices.getFusedLocationProviderClient(context) }
    val locationGranted = fr.fuelradar.ui.common.rememberLocationGranted()
    fun fetchLocation() {
        if (!fr.fuelradar.ui.common.hasFineLocation(context)) return
        runCatching {
            fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
                .addOnSuccessListener { loc ->
                    val use = loc
                    if (use != null) {
                        scope.launch {
                            val r = ServiceLocator.geocoder.reverse(use.latitude, use.longitude)
                            val label = r?.let {
                                listOf(it.postcode, it.city).filter { s -> s.isNotBlank() }.joinToString(" ")
                            }?.ifBlank { null }
                            ServiceLocator.filters.setLocation(use.latitude, use.longitude, label)
                        }
                    }
                }
        }
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        locationGranted.value = granted
        if (granted) fetchLocation()
    }
    val onLocateClick = {
        if (locationGranted.value) fetchLocation()
        else permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall)

        // Startup screen.
        SettingsCard(stringResource(R.string.startup_screen)) {
            ChoiceGrid(
                options = STARTUP_TABS.map { it.first to stringResource(it.second) },
                selected = settings.startupTab,
                onSelect = { scope.launch { store.setStartupTab(it) } },
            )
        }

        // Language.
        SettingsCard(stringResource(R.string.language), icon = Icons.Filled.Language) {
            val currentLang = AppCompatDelegate.getApplicationLocales().toLanguageTags()
                .split(",").firstOrNull()?.substringBefore('-') ?: ""
            ChoiceGrid(
                options = LANGUAGES.map { it.first to stringResource(it.second) },
                selected = currentLang,
                onSelect = { tag ->
                    AppCompatDelegate.setApplicationLocales(
                        if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList()
                        else LocaleListCompat.forLanguageTags(tag),
                    )
                },
            )
        }

        // Search: stale warning + current position + locate.
        SettingsCard(stringResource(R.string.settings_search)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.stale_warning), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.stale_warning_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.staleWarning,
                    onCheckedChange = { scope.launch { store.setStaleWarning(it) } },
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.current_position), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        filters.searchLabel ?: filters.userLocation?.let {
                            "%.3f, %.3f".format(it.lat, it.lng)
                        } ?: stringResource(R.string.position_unknown),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                FilledTonalButton(onClick = onLocateClick) {
                    Icon(
                        if (locationGranted.value) Icons.Filled.MyLocation
                        else Icons.Filled.LocationDisabled,
                        null,
                        modifier = Modifier.size(16.dp),
                    )
                    Text("  " + stringResource(R.string.refresh))
                }
            }
        }

        // Share app.
        SettingsCard(stringResource(R.string.share_app), icon = Icons.Filled.Share) {
            Text(
                stringResource(R.string.share_app_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Button(onClick = {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "FuelRadar — $APP_URL")
                }
                runCatching { context.startActivity(Intent.createChooser(intent, null)) }
            }) {
                Icon(Icons.Filled.Share, null, modifier = Modifier.size(18.dp))
                Text("  " + stringResource(R.string.share))
            }
        }

        // Data.
        SettingsCard(stringResource(R.string.settings_data)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.last_update, "").trimEnd(), style = MaterialTheme.typography.bodyLarge)
                Text(
                    lastUpdate?.let { relativeTime(it) } ?: "—",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.data_credits),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // About.
        SettingsCard(stringResource(R.string.settings_about)) {
            Text(
                stringResource(R.string.settings_about_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.designed_by, "Angelo Lima"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().clickable { openUrl(context, AUTHOR_URL) }.padding(vertical = 6.dp),
            )
            Text(
                stringResource(R.string.privacy_policy),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().clickable { openUrl(context, PRIVACY_URL) }.padding(vertical = 6.dp),
            )
        }
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

@Composable
private fun SettingsCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
                if (icon != null) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(8.dp))
                }
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            content()
        }
    }
}

/** Two-column grid of selectable chips (mirror of the web settings buttons). */
@Composable
private fun ChoiceGrid(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(2).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                rowItems.forEach { (value, label) ->
                    val isSel = selected == value
                    androidx.compose.material3.Surface(
                        onClick = { onSelect(value) },
                        modifier = Modifier.weight(1f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                        color = if (isSel) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surface,
                        border = if (isSel) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (isSel) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                        )
                    }
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
