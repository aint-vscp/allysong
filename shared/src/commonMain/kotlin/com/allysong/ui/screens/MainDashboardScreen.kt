package com.allysong.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.SensorsOff
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.allysong.domain.model.AccelerometerReading
import com.allysong.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun MainDashboardScreen(
    latestReading: AccelerometerReading?,
    isSensorActive: Boolean,
    isBarometerActive: Boolean,
    isBarometerAvailable: Boolean,
    latestPressureHpa: Float?,
    isMeshActive: Boolean,
    peerCount: Int,
    aiStatus: String,
    onToggleSensor: () -> Unit,
    onToggleBarometer: () -> Unit,
    onToggleMesh: () -> Unit,
    onOpenCommunications: () -> Unit,
    onManualSos: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(GradientStart, GradientEnd, SurfaceDark)
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Header ─────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "AllySong",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MeshBlue
                )
                Text(
                    text = "Decentralized Disaster Response",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
            val dotColor = when {
                isSensorActive && isMeshActive -> SafeGreen
                isSensorActive || isMeshActive || isBarometerActive -> AlertOrange
                else -> TextMuted
            }
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
        }

        // Accent divider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(MeshBlue, Color.Transparent)
                    )
                )
        )

        // ── Sensor Card ────────────────────────────────────────────────────
        MonitoringCard(
            title = "Accelerometer",
            icon = if (isSensorActive) Icons.Outlined.Sensors else Icons.Outlined.SensorsOff,
            isActive = isSensorActive,
            onToggle = onToggleSensor
        ) {
            if (isSensorActive && latestReading != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    AxisValue(label = "X", value = formatFloat(latestReading.x))
                    AxisValue(label = "Y", value = formatFloat(latestReading.y))
                    AxisValue(label = "Z", value = formatFloat(latestReading.z))
                }

                Spacer(modifier = Modifier.height(12.dp))

                val magnitude = latestReading.magnitude
                val barFraction = (magnitude / 20f).coerceIn(0f, 1f)
                val barColor = when {
                    magnitude > 14f -> EmergencyRed
                    magnitude > 11f -> AlertOrange
                    else -> SafeGreen
                }

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Magnitude",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                        Text(
                            text = "${formatFloat(magnitude)} m/s\u00B2",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = barColor
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(SurfaceCardBorder)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(barFraction)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(barColor)
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SensorsOff,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Toggle to begin monitoring",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                }
            }
        }

        // ── Barometer Card ────────────────────────────────────────────────
        if (isBarometerAvailable) {
            MonitoringCard(
                title = "Barometer (Typhoon)",
                icon = Icons.Outlined.Speed,
                isActive = isBarometerActive,
                onToggle = onToggleBarometer
            ) {
                if (isBarometerActive && latestPressureHpa != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "${String.format("%.1f", latestPressureHpa)} hPa",
                            style = MaterialTheme.typography.titleMedium,
                            color = when {
                                latestPressureHpa < 980f -> EmergencyRed
                                latestPressureHpa < 1000f -> AlertOrange
                                else -> SafeGreen
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when {
                            latestPressureHpa < 950f -> "DANGER: Typhoon-level pressure"
                            latestPressureHpa < 980f -> "WARNING: Rapid pressure drop"
                            latestPressureHpa < 1000f -> "Low pressure – monitoring"
                            else -> "Normal atmospheric pressure"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                } else if (!isBarometerActive) {
                    Text(
                        text = "Toggle to monitor atmospheric pressure",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                }
            }
        }

        // ── Mesh Network Card ──────────────────────────────────────────────
        MonitoringCard(
            title = "BLE Mesh Network",
            icon = if (isMeshActive) Icons.Outlined.Hub else Icons.Outlined.WifiOff,
            isActive = isMeshActive,
            onToggle = onToggleMesh
        ) {
            if (isMeshActive) {
                val pulseTransition = rememberInfiniteTransition(label = "mesh_pulse")
                val pulseAlpha by pulseTransition.animateFloat(
                    initialValue = 0.4f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "mesh_dot_pulse"
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(SafeGreen.copy(alpha = pulseAlpha))
                    )
                    Text(
                        text = "$peerCount peer(s) connected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.WifiOff,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Mesh inactive",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                }
            }
        }

        // ── AI Engine Card ─────────────────────────────────────────────────
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.outlinedCardColors(containerColor = SurfaceCard),
            border = BorderStroke(1.dp, SurfaceCardBorder),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Psychology,
                        contentDescription = "AI Engine",
                        tint = MeshBlue,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Edge AI Engine",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                val statusColor = when {
                    "alert" in aiStatus.lowercase() -> EmergencyRed
                    "monitor" in aiStatus.lowercase() -> SafeGreen
                    "error" in aiStatus.lowercase() -> EmergencyRed
                    else -> TextSecondary
                }
                Text(
                    text = aiStatus,
                    style = MaterialTheme.typography.bodyMedium,
                    color = statusColor
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // ── Action Buttons ─────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onOpenCommunications,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MeshBlue),
                border = BorderStroke(1.dp, MeshBlue)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Forum,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Comms", fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = onManualSos,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmergencyRed,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("MANUAL SOS", fontWeight = FontWeight.Bold)
            }
        }

        // ── Bottom Status Bar ──────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusDot(label = "SEN", isActive = isSensorActive)
                StatusDot(label = "MESH", isActive = isMeshActive)
                StatusDot(label = "AI", isActive = "monitor" in aiStatus.lowercase())
            }
            Text(
                text = "AllySong v0.1.0",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
    }
}

// ── Reusable composables ───────────────────────────────────────────────────

@Composable
private fun MonitoringCard(
    title: String,
    icon: ImageVector,
    isActive: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, SurfaceCardBorder),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = MeshBlue,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Switch(
                    checked = isActive,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = SafeGreen,
                        checkedTrackColor = SafeGreen.copy(alpha = 0.3f)
                    )
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun AxisValue(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MeshBlue
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun StatusDot(label: String, isActive: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (isActive) SafeGreen else TextMuted)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isActive) MaterialTheme.colorScheme.onSurface else TextMuted,
            fontSize = 10.sp
        )
    }
}

private fun formatFloat(value: Float, decimals: Int = 2): String {
    var factor = 1f
    repeat(decimals) { factor *= 10f }
    val rounded = (value * factor).roundToInt() / factor
    val str = rounded.toString()
    val dotIndex = str.indexOf('.')
    return if (dotIndex == -1) {
        "$str.${"0".repeat(decimals)}"
    } else {
        val existingDecimals = str.length - dotIndex - 1
        if (existingDecimals >= decimals) str.substring(0, dotIndex + decimals + 1)
        else str + "0".repeat(decimals - existingDecimals)
    }
}
