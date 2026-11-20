package com.ohmy.zfsync.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ohmy.zfsync.R

/**
 * A screen's headline, with the menu button (opening Settings) at its end. The button sits level
 * with the first line, and is nudged right by its own padding so the icon, not its touch area,
 * lines up with the screen margin.
 */
@Composable
internal fun TitleWithMenu(title: String, onOpenMenu: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.Top) {
        Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = onOpenMenu, modifier = Modifier.offset(x = 12.dp)) {
            Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.select_menu))
        }
    }
}
