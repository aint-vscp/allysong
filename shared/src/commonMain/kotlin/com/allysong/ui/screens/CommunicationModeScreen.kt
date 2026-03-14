package com.allysong.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.allysong.domain.model.MeshMessage
import com.allysong.domain.model.MeshMessageType
import com.allysong.domain.model.PeerDevice
import com.allysong.ui.components.Base64Image
import com.allysong.ui.components.rememberImagePicker
import com.allysong.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun CommunicationModeScreen(
    localAlias: String,
    peers: List<PeerDevice>,
    messages: List<MeshMessage>,
    hasLocation: Boolean,
    onSendMessage: (String) -> Unit,
    onSendLocation: () -> Unit,
    onSendImage: (String) -> Unit,
    onBackToDashboard: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    var showImagePickerDialog by remember { mutableStateOf(false) }
    var showActionButtons by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Image picker (platform-specific: gallery + camera)
    val imagePicker = rememberImagePicker { base64 ->
        onSendImage(base64)
    }

    // Auto-scroll to latest message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ── Header Banner ──────────────────────────────────────────────────
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = EmergencyRed.copy(alpha = 0.15f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "COMMUNICATION MODE",
                        style = MaterialTheme.typography.labelLarge,
                        color = EmergencyRed
                    )
                    Text(
                        text = "${peers.size} peer(s) \u2022 $localAlias",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
                TextButton(onClick = onBackToDashboard) {
                    Icon(
                        imageVector = Icons.Outlined.ArrowBack,
                        contentDescription = "Back",
                        modifier = Modifier.size(16.dp),
                        tint = MeshBlue
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Dashboard", color = MeshBlue)
                }
            }
        }

        // ── Connected Peers Strip ──────────────────────────────────────────
        if (peers.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(peers, key = { it.endpointId }) { peer ->
                    PeerChip(peer = peer)
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "No peers connected",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }

        Divider(color = SurfaceCardBorder)

        // ── Message Feed ───────────────────────────────────────────────────
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.ChatBubbleOutline,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No messages yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Type a message below to broadcast\nto connected peers.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                state = listState,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    val isLocal = message.senderAlias == localAlias
                    MessageBubble(message = message, isLocal = isLocal)
                }
            }
        }

        // ── Input Bar ──────────────────────────────────────────────────────
        Divider(color = SurfaceCardBorder)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // ">" toggle button — expands to show GPS + image actions
            IconButton(
                onClick = { showActionButtons = !showActionButtons },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (showActionButtons) Icons.Outlined.Close else Icons.Outlined.ChevronRight,
                    contentDescription = if (showActionButtons) "Close" else "More actions",
                    tint = MeshBlue,
                    modifier = Modifier.size(22.dp)
                )
            }

            // GPS + Image buttons — slide in/out horizontally
            AnimatedVisibility(
                visible = showActionButtons,
                enter = expandHorizontally(expandFrom = Alignment.Start),
                exit = shrinkHorizontally(shrinkTowards = Alignment.Start)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                    IconButton(
                        onClick = {
                            onSendLocation()
                            showActionButtons = false
                        },
                        enabled = hasLocation,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MyLocation,
                            contentDescription = "Send Location",
                            tint = if (hasLocation) MeshBlue else TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            showImagePickerDialog = true
                            showActionButtons = false
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CameraAlt,
                            contentDescription = "Send Image",
                            tint = MeshBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Text field — takes all remaining space
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MeshBlue,
                    unfocusedBorderColor = SurfaceCardBorder,
                    cursorColor = MeshBlue,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                placeholder = {
                    Text("Type a message...", color = TextMuted)
                },
                singleLine = true
            )

            // Send button — compact circular icon
            IconButton(
                onClick = {
                    if (inputText.isNotBlank()) {
                        onSendMessage(inputText.trim())
                        inputText = ""
                        scope.launch {
                            if (messages.isNotEmpty()) {
                                listState.animateScrollToItem(messages.lastIndex)
                            }
                        }
                    }
                },
                enabled = inputText.isNotBlank(),
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (inputText.isNotBlank()) MeshBlue else MeshBlue.copy(alpha = 0.3f)
                    )
            ) {
                Icon(
                    imageVector = Icons.Outlined.Send,
                    contentDescription = "Send",
                    tint = if (inputText.isNotBlank()) Color.White else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }

    // ── Image picker dialog (Camera / Gallery) ──────────────────────────
    if (showImagePickerDialog) {
        AlertDialog(
            onDismissRequest = { showImagePickerDialog = false },
            title = { Text("Send Image") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            showImagePickerDialog = false
                            imagePicker.launchCamera()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Outlined.PhotoCamera,
                            contentDescription = null,
                            tint = MeshBlue,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Take Photo", color = MeshBlue)
                    }
                    TextButton(
                        onClick = {
                            showImagePickerDialog = false
                            imagePicker.launchGallery()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Outlined.Image,
                            contentDescription = null,
                            tint = MeshBlue,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Choose from Gallery", color = MeshBlue)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showImagePickerDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = SurfaceCard
        )
    }
}

// ── Helper composables ─────────────────────────────────────────────────────

@Composable
private fun PeerChip(peer: PeerDevice) {
    val isSos = peer.isSos
    val hasLocation = peer.latitude != 0.0 || peer.longitude != 0.0
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isSos) EmergencyRed.copy(alpha = 0.2f) else MeshBlue.copy(alpha = 0.15f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isSos) EmergencyRed else SafeGreen)
                )
                Text(
                    text = peer.alias,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
                if (isSos) {
                    Text(
                        text = "SOS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = EmergencyRed
                    )
                }
            }
            if (hasLocation) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.LocationOn,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(10.dp)
                    )
                    Text(
                        text = formatCoordinate(peer.latitude, peer.longitude),
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 9.sp,
                        color = TextMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: MeshMessage,
    isLocal: Boolean
) {
    val isSos = message.type == MeshMessageType.SOS_BROADCAST
    val isLocationShare = message.type == MeshMessageType.LOCATION_SHARE
    val isImageShare = message.type == MeshMessageType.IMAGE_SHARE
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isLocal) Alignment.End else Alignment.Start
    ) {
        // Sender label
        Text(
            text = if (isLocal) "You" else message.senderAlias,
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
        // Bubble
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isLocal) 16.dp else 4.dp,
                bottomEnd = if (isLocal) 4.dp else 16.dp
            ),
            color = when {
                isSos -> EmergencyRed.copy(alpha = 0.3f)
                isLocationShare || isImageShare -> MeshBlue.copy(alpha = 0.15f)
                isLocal -> MeshBlue.copy(alpha = 0.3f)
                else -> SurfaceCard
            }
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                if (isSos) {
                    Text(
                        text = "\u26A0\uFE0F SOS ${message.payload}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = EmergencyRed,
                        fontWeight = FontWeight.Bold
                    )
                    // SOS always shows location if available
                    if (message.latitude != 0.0 || message.longitude != 0.0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.clickable {
                                uriHandler.openUri(
                                    "geo:${message.latitude},${message.longitude}?q=${message.latitude},${message.longitude}"
                                )
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.LocationOn,
                                contentDescription = "Open in Maps",
                                tint = EmergencyRed.copy(alpha = 0.8f),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = formatCoordinate(message.latitude, message.longitude),
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = EmergencyRed.copy(alpha = 0.8f),
                                textDecoration = TextDecoration.Underline
                            )
                        }
                        Text(
                            text = "Tap to open in Maps",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 9.sp,
                            color = TextMuted
                        )
                    }
                } else if (isLocationShare) {
                    // Dedicated location share — big tappable map pin
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                uriHandler.openUri(
                                    "geo:${message.latitude},${message.longitude}?q=${message.latitude},${message.longitude}"
                                )
                            }
                            .padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.LocationOn,
                            contentDescription = "Location",
                            tint = MeshBlue,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = formatCoordinate(message.latitude, message.longitude),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MeshBlue,
                            fontWeight = FontWeight.Medium,
                            textDecoration = TextDecoration.Underline
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Tap to open in Maps",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 10.sp,
                            color = TextMuted
                        )
                    }
                } else if (isImageShare) {
                    // Inline image — compressed JPEG from camera/gallery
                    Base64Image(
                        base64Data = message.payload,
                        contentDescription = "Shared image from ${message.senderAlias}",
                        modifier = Modifier
                            .widthIn(max = 240.dp)
                            .heightIn(max = 240.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    Text(
                        text = message.payload,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        // Timestamp
        Text(
            text = formatTimestamp(message.timestampMs),
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}

private fun formatTimestamp(epochMs: Long): String {
    return try {
        val instant = Instant.fromEpochMilliseconds(epochMs)
        val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        val hour = local.hour.toString().padStart(2, '0')
        val minute = local.minute.toString().padStart(2, '0')
        "$hour:$minute"
    } catch (_: Exception) {
        ""
    }
}

private fun formatCoordinate(lat: Double, lng: Double): String {
    val latDir = if (lat >= 0) "N" else "S"
    val lngDir = if (lng >= 0) "E" else "W"
    val latAbs = kotlin.math.abs(lat)
    val lngAbs = kotlin.math.abs(lng)
    // Format to 4 decimal places (~11m accuracy)
    return "${formatDeg(latAbs)}$latDir, ${formatDeg(lngAbs)}$lngDir"
}

private fun formatDeg(value: Double): String {
    val str = value.toString()
    val dot = str.indexOf('.')
    return if (dot == -1) "$str.0000"
    else {
        val decimals = str.length - dot - 1
        if (decimals >= 4) str.substring(0, dot + 5)
        else str + "0".repeat(4 - decimals)
    }
}
