package com.allysong.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Water
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Cyclone
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.allysong.domain.model.DisasterType
import com.allysong.ui.theme.*

// ============================================================================
// ManualSOSScreen.kt – commonMain
// ============================================================================
// Emergency type picker for manual SOS. The user selects the type of emergency
// they are experiencing, optionally adds a description, and confirms to
// immediately broadcast an SOS to the mesh network.
//
// This screen skips the HITL countdown because the user is actively
// requesting help — they already know they need assistance.
// ============================================================================

@Composable
fun ManualSOSScreen(
    onConfirm: (DisasterType, String) -> Unit,
    onCancel: () -> Unit
) {
    var selectedType by remember { mutableStateOf<DisasterType?>(null) }
    var description by remember { mutableStateOf("") }
    var showConfirmation by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0000))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Header ───────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "MANUAL SOS",
                    style = MaterialTheme.typography.headlineLarge,
                    color = EmergencyRed,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Select emergency type",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            }
            TextButton(onClick = onCancel) {
                Icon(
                    imageVector = Icons.Outlined.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.size(16.dp),
                    tint = MeshBlue
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Cancel", color = MeshBlue)
            }
        }

        if (!showConfirmation) {
            // ── Emergency Type Grid ──────────────────────────────────────
            val emergencyTypes = listOf(
                EmergencyTypeOption(DisasterType.EARTHQUAKE_COMBINED, "Earthquake", Icons.Filled.Terrain, AlertOrange),
                EmergencyTypeOption(DisasterType.TYPHOON, "Typhoon", Icons.Outlined.Cyclone, MeshBlue),
                EmergencyTypeOption(DisasterType.FIRE, "Fire", Icons.Filled.LocalFireDepartment, EmergencyRed),
                EmergencyTypeOption(DisasterType.FLOOD, "Flood", Icons.Filled.Water, MeshBlue),
                EmergencyTypeOption(DisasterType.KIDNAPPING, "Kidnapping", Icons.Filled.ReportProblem, AlertOrange),
                EmergencyTypeOption(DisasterType.MEDICAL_EMERGENCY, "Medical", Icons.Filled.MedicalServices, SafeGreen),
                EmergencyTypeOption(DisasterType.OTHER, "Other", Icons.Filled.Warning, TextSecondary)
            )

            // 2-column grid
            emergencyTypes.chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowItems.forEach { option ->
                        EmergencyTypeCard(
                            option = option,
                            isSelected = selectedType == option.type,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                selectedType = option.type
                                showConfirmation = true
                            }
                        )
                    }
                    // Fill remaining space if odd count
                    if (rowItems.size < 2) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Tap an emergency type to proceed.\nYour SOS will be broadcast immediately to nearby devices.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            // ── Confirmation Card ────────────────────────────────────────
            val type = selectedType ?: return@Column

            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = Color(0xFF1A0505)
                ),
                border = BorderStroke(2.dp, EmergencyRed.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = EmergencyRed,
                        modifier = Modifier.size(48.dp)
                    )

                    Text(
                        text = "Confirm SOS: ${type.displayLabel()}",
                        style = MaterialTheme.typography.titleLarge,
                        color = EmergencyRed,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "This will broadcast an emergency SOS to all nearby AllySong devices via the BLE mesh network.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted,
                        textAlign = TextAlign.Center
                    )

                    // Optional description field
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmergencyRed,
                            unfocusedBorderColor = SurfaceCardBorder,
                            cursorColor = EmergencyRed,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        placeholder = {
                            Text("Add details (optional)...", color = TextMuted)
                        },
                        maxLines = 3
                    )

                    // Action buttons
                    Button(
                        onClick = { onConfirm(type, description) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmergencyRed,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Send,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SEND SOS NOW",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            showConfirmation = false
                            selectedType = null
                            description = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = TextSecondary
                        ),
                        border = BorderStroke(1.dp, SurfaceCardBorder)
                    ) {
                        Text("Go Back", fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

// ── Helper composables and data ──────────────────────────────────────────────

private data class EmergencyTypeOption(
    val type: DisasterType,
    val label: String,
    val icon: ImageVector,
    val tint: Color
)

@Composable
private fun EmergencyTypeCard(
    option: EmergencyTypeOption,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OutlinedCard(
        modifier = modifier
            .height(120.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isSelected) option.tint.copy(alpha = 0.15f) else SurfaceCard
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) option.tint else SurfaceCardBorder
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = option.icon,
                contentDescription = option.label,
                tint = option.tint,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = option.label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun DisasterType.displayLabel(): String = when (this) {
    DisasterType.EARTHQUAKE_P_WAVE -> "Earthquake"
    DisasterType.EARTHQUAKE_S_WAVE -> "Earthquake"
    DisasterType.EARTHQUAKE_COMBINED -> "Earthquake"
    DisasterType.TYPHOON -> "Typhoon / Cyclone"
    DisasterType.FIRE -> "Fire"
    DisasterType.FLOOD -> "Flood"
    DisasterType.KIDNAPPING -> "Kidnapping / Abduction"
    DisasterType.MEDICAL_EMERGENCY -> "Medical Emergency"
    DisasterType.OTHER -> "Other Emergency"
}
