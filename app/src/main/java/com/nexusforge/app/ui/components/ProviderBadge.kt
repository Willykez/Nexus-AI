package com.nexusforge.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexusforge.app.ui.theme.ErrorRed
import com.nexusforge.app.ui.theme.SuccessGreen

/**
 * "It should be clear which one I'm currently talking to at all times, right there in the
 * input area, not buried in settings." — this badge is that. Ready/needs-key reflects real
 * state, never a fake uptime dot.
 */
@Composable
fun ProviderBadge(providerLabel: String, model: String, isReady: Boolean, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                color = if (isReady) SuccessGreen else ErrorRed,
                shape = CircleShape,
                modifier = Modifier.size(7.dp)
            ) {}
            Text("$providerLabel · $model", style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}
