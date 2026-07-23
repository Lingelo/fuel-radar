package fr.fuelradar.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import fr.fuelradar.R
import fr.fuelradar.data.geo.AddressResult

/**
 * Address field with a debounced autocomplete dropdown (mirror of the web
 * SearchBar). The caller supplies the current [query] and [suggestions]; the
 * ViewModel owns the debounced geocoding.
 */
@Composable
fun AddressSearchBar(
    query: String,
    suggestions: List<AddressResult>,
    onQueryChange: (String) -> Unit,
    onSelect: (AddressResult) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    Column(modifier = modifier) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = trailingIcon,
            placeholder = { Text(stringResource(R.string.search_hint)) },
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            shape = MaterialTheme.shapes.extraLarge,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                errorIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        if (suggestions.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 3.dp,
                shadowElevation = 4.dp,
            ) {
                Column {
                    suggestions.take(6).forEachIndexed { i, r ->
                        if (i > 0) HorizontalDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(r) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.Place,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Column(modifier = Modifier.padding(start = 10.dp)) {
                                Text(
                                    r.label.ifBlank { "${r.postcode} ${r.city}" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                )
                                if (r.city.isNotBlank()) {
                                    Text(
                                        "${r.postcode} ${r.city}".trim(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
