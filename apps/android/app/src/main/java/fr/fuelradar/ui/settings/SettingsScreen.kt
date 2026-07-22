package fr.fuelradar.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.fuelradar.data.ServiceLocator
import fr.fuelradar.data.prefs.AppSettings
import kotlinx.coroutines.launch

private val STARTUP_TABS = listOf(
    "map" to "Carte",
    "stations" to "Stations",
    "favorites" to "Favoris",
    "trends" to "Tendances",
)

private const val PRIVACY_URL = "https://lingelo.github.io/fuel-radar/privacy.html"

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val store = ServiceLocator.settings
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val settings by store.settings.collectAsStateWithLifecycle(AppSettings())

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Réglages", style = MaterialTheme.typography.headlineSmall)

        Text("Écran de démarrage", style = MaterialTheme.typography.titleMedium)
        STARTUP_TABS.forEach { (route, label) ->
            Row(
                modifier = Modifier.fillMaxWidth().selectable(
                    selected = settings.startupTab == route,
                    onClick = { scope.launch { store.setStartupTab(route) } },
                ).padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = settings.startupTab == route,
                    onClick = { scope.launch { store.setStartupTab(route) } },
                )
                Text(label, modifier = Modifier.padding(start = 8.dp))
            }
        }

        HorizontalDivider()

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Avertir si données anciennes", style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = settings.staleWarning,
                onCheckedChange = { scope.launch { store.setStaleWarning(it) } },
            )
        }

        HorizontalDivider()

        Text(
            "Politique de confidentialité",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().clickable {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_URL)))
                }
            }.padding(vertical = 8.dp),
        )

        HorizontalDivider()

        Text(
            "Données : prix-carburants.gouv.fr (France), Ministerio para la Transición " +
                "Ecológica (Espagne), DGEG (Portugal). Marques enrichies via OpenStreetMap.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
