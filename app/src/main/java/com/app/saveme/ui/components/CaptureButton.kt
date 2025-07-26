package com.app.saveme.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun CaptureButton(
    onClick: () -> Unit,
    icon: ImageVector = Icons.Rounded.PhotoCamera,
    contentDescription: String = "Capture",
    isRecording: Boolean = false,
    enabled: Boolean = true, // Add enabled parameter
    modifier: Modifier = Modifier
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier
            .size(80.dp)
            .border(
                4.dp,
                Color.White,
                CircleShape
            ),
        containerColor = when {
            !enabled -> MaterialTheme.colorScheme.surfaceVariant
            isRecording -> Color.Red
            else -> MaterialTheme.colorScheme.primary
        },
        contentColor = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(36.dp)
        )
    }
} 