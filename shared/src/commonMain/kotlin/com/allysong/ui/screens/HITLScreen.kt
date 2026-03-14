package com.allysong.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.allysong.domain.model.DisasterEvent
import com.allysong.domain.model.DisasterType
import com.allysong.ui.theme.*
import kotlinx.coroutines.delay

private const val COUNTDOWN_SECONDS = 60

@Composable
fun HITLScreen(
    event: DisasterEvent,
    onSafe: () -> Unit,
    onSos: () -> Unit
) {
    var secondsRemaining by remember { mutableStateOf(COUNTDOWN_SECONDS) }
    var hasResponded by remember { mutableStateOf(false) }

    val progress by animateFloatAsState(
        targetValue = secondsRemaining.toFloat() / COUNTDOWN_SECONDS,
        animationSpec = tween(durationMillis = 1000),
        label = "countdown_progress"
    )

    // Countdown timer
    LaunchedEffect(Unit) {
        while (secondsRemaining > 0) {
            delay(1000L)
            if (!hasResponded) {
                secondsRemaining--
            }
        }
        if (!hasResponded) {
            hasResponded = true
            onSos()
        }
    }

    // Pulsing border animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "border_pulse"
    )

    // Critical countdown pulse (activates when <= 10 seconds)
    val criticalPulse by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "critical_pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0000)),
        contentAlignment = Alignment.Center
    ) {
        // Pulsing red border container
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .border(
                    width = 3.dp,
                    color = EmergencyRed.copy(alpha = pulseAlpha),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // ── Warning Icon ───────────────────────────────────────────
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + scaleIn()
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = "Warning",
                        tint = EmergencyRed,
                        modifier = Modifier.size(64.dp)
                    )
                }

                // ── Alert Header ───────────────────────────────────────────
                Text(
                    text = "DISASTER DETECTED",
                    style = MaterialTheme.typography.headlineLarge,
                    color = EmergencyRed,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 26.sp,
                    textAlign = TextAlign.Center
                )

                // ── Detection Details Card ─────────────────────────────────
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = Color(0xFF1A0505)
                    ),
                    border = BorderStroke(1.dp, EmergencyRed.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Type: ",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                            Text(
                                text = event.type.displayName(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = AlertOrange,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Confidence: ",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                            Text(
                                text = "${(event.confidence * 100).toInt()}%",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = event.type.detectionDescription(),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // ── Countdown Ring ─────────────────────────────────────────
                val isCritical = secondsRemaining <= 10
                val ringAlpha = if (isCritical) criticalPulse else 1f

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(160.dp)
                ) {
                    // Background track
                    CircularProgressIndicator(
                        progress = 1f,
                        modifier = Modifier.fillMaxSize(),
                        color = Color(0xFF3D0000),
                        strokeWidth = 10.dp
                    )
                    // Animated countdown ring
                    CircularProgressIndicator(
                        progress = progress,
                        modifier = Modifier.fillMaxSize(),
                        color = when {
                            secondsRemaining > 30 -> AlertOrange
                            else -> EmergencyRed.copy(alpha = ringAlpha)
                        },
                        strokeWidth = 10.dp
                    )
                    // Seconds label
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$secondsRemaining",
                            fontSize = 52.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isCritical) EmergencyRed.copy(alpha = ringAlpha) else Color.White
                        )
                        Text(
                            text = "seconds",
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }
                }

                // ── Prompt ─────────────────────────────────────────────────
                Text(
                    text = "Are you safe?",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "SOS will auto-broadcast when the timer reaches zero.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(4.dp))

                // ── Action Buttons ─────────────────────────────────────────
                Button(
                    onClick = {
                        hasResponded = true
                        onSafe()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SafeGreen,
                        contentColor = Color.Black
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "YES \u2013 I'M SAFE",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )
                }

                Button(
                    onClick = {
                        hasResponded = true
                        onSos()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .then(
                            if (isCritical) Modifier.border(
                                2.dp,
                                EmergencyRed.copy(alpha = criticalPulse),
                                RoundedCornerShape(16.dp)
                            ) else Modifier
                        ),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmergencyRed,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "NO \u2013 I NEED HELP",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

private fun DisasterType.displayName(): String = when (this) {
    DisasterType.EARTHQUAKE_P_WAVE -> "Earthquake (P-Wave)"
    DisasterType.EARTHQUAKE_S_WAVE -> "Earthquake (S-Wave)"
    DisasterType.EARTHQUAKE_COMBINED -> "Earthquake (P+S Waves)"
    DisasterType.TYPHOON -> "Typhoon / Cyclone"
    DisasterType.FIRE -> "Fire"
    DisasterType.FLOOD -> "Flood"
    DisasterType.KIDNAPPING -> "Kidnapping / Abduction"
    DisasterType.MEDICAL_EMERGENCY -> "Medical Emergency"
    DisasterType.OTHER -> "Other Emergency"
}

private fun DisasterType.detectionDescription(): String = when (this) {
    DisasterType.EARTHQUAKE_P_WAVE ->
        "The on-device AI has detected compressional P-wave signatures consistent with earthquake activity."
    DisasterType.EARTHQUAKE_S_WAVE ->
        "The on-device AI has detected shear S-wave signatures consistent with earthquake activity."
    DisasterType.EARTHQUAKE_COMBINED ->
        "The on-device AI has detected both P-wave and S-wave signatures consistent with significant earthquake activity."
    DisasterType.TYPHOON ->
        "The on-device AI has detected rapid barometric pressure drops consistent with approaching typhoon/cyclone activity."
    else ->
        "Emergency event detected by on-device sensors."
}
