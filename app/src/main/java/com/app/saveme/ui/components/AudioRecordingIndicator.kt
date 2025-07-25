package com.app.saveme.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

@Composable
fun AudioRecordingIndicator(
    isRecording: Boolean,
    duration: Float,
    amplitude: Int,
    modifier: Modifier = Modifier
) {
    if (isRecording) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color.Red),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Mic,
                            contentDescription = "Recording",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Text(
                        text = "Recording: ${String.format("%.1f", duration)}s",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Amplitude visualization
                AmplitudeVisualization(
                    amplitude = amplitude,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                )
            }
        }
    }
}

@Composable
private fun AmplitudeVisualization(
    amplitude: Int,
    modifier: Modifier = Modifier
) {
    val normalizedAmplitude = min(amplitude.toFloat() / 32767f, 1f)
    val animatedAmplitude by animateFloatAsState(
        targetValue = normalizedAmplitude,
        animationSpec = tween(durationMillis = 100),
        label = "amplitude"
    )
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(20) { index ->
            val barHeight = max(
                0.1f,
                animatedAmplitude * (0.5f + 0.5f * kotlin.random.Random.nextFloat())
            )
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height((24 * barHeight).dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(
                        MaterialTheme.colorScheme.primary.copy(
                            alpha = 0.3f + 0.7f * barHeight
                        )
                    )
            )
        }
    }
} 