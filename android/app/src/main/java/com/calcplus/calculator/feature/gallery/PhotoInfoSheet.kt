package com.calcplus.calculator.feature.gallery

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.calcplus.calculator.R
import com.calcplus.calculator.core.domain.model.Photo

/**
 * Details sheet for the pager's current photo (N2): a modal bottom sheet titled
 * `photo_info_title` with one labeled row per decided field, in decided order.
 * The sheet's default window insets already keep the rows clear of the system bars.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoInfoSheet(
    photo: Photo,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    // Duration is a video-only row (decisions §8): pass it only for a video, so
    // a photo carrying a stray duration could never grow one.
    val info = remember(photo, context) {
        PhotoInfo.from(context, photo, durationMs = if (photo.isVideo) photo.durationMs else null)
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.photo_info_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            info.entries.forEach { entry ->
                PhotoInfoRow(label = stringResource(entry.labelRes), value = entry.value)
            }
        }
    }
}

@Composable
private fun PhotoInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.End,
        )
    }
}
