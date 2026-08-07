package com.walkieyappie.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.walkieyappie.app.audio.AudioOutputMode
import com.walkieyappie.app.data.PeerDevice
import com.walkieyappie.app.ui.theme.DarkSurface
import com.walkieyappie.app.ui.theme.DeepObsidian
import com.walkieyappie.app.ui.theme.ElectricBlue
import com.walkieyappie.app.ui.theme.NeonCyan
import com.walkieyappie.app.ui.theme.ReceiveGreen
import com.walkieyappie.app.ui.theme.SurfaceBorder
import com.walkieyappie.app.ui.theme.TextPrimary
import com.walkieyappie.app.ui.theme.TextSecondary
import com.walkieyappie.app.ui.theme.TransmitRed
import com.walkieyappie.app.util.PermissionsUtils

@Composable
fun WalkieTalkieScreen(viewModel: WalkieTalkieViewModel) {
    val context = LocalContext.current

    val hasPermissions by viewModel.hasPermissions.collectAsState()
    val username by viewModel.username.collectAsState()
    val showUsernameDialog by viewModel.showUsernameDialog.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val discoveredPeers by viewModel.discoveredPeers.collectAsState()
    val connectedPeers by viewModel.connectedPeers.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val isTransmitting by viewModel.isTransmitting.collectAsState()
    val isReceiving by viewModel.isReceivingAudio.collectAsState()
    val activeSpeakerName by viewModel.activeSpeakerName.collectAsState()
    val audioOutputMode by viewModel.audioOutputMode.collectAsState()

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        viewModel.onPermissionsResult(allGranted)
    }

    LaunchedEffect(Unit) {
        if (!hasPermissions) {
            permissionLauncher.launch(PermissionsUtils.getRequiredPermissions())
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = DeepObsidian
    ) {
        if (!hasPermissions) {
            PermissionGuardView {
                permissionLauncher.launch(PermissionsUtils.getRequiredPermissions())
            }
        } else {
            // Username Onboarding Prompt Dialog
            if (showUsernameDialog) {
                UsernameOnboardingDialog(
                    initialUsername = username,
                    onSave = { newName -> viewModel.saveUsername(newName) }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header & Status Section
                HeaderAndStatusSection(
                    username = username,
                    connectionStatus = connectionStatus,
                    peerCount = connectedPeers.size,
                    isScanning = isScanning,
                    audioOutputMode = audioOutputMode,
                    onEditUsername = { viewModel.openUsernameDialog() },
                    onToggleScan = { viewModel.toggleScanning() },
                    onToggleAudioOutput = { viewModel.toggleAudioOutputMode() }
                )

                // Central Active Visualizer & Multi-Device Lists
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(10.dp))

                    AudioStateVisualizer(
                        isTransmitting = isTransmitting,
                        isReceiving = isReceiving,
                        activeSpeakerName = activeSpeakerName
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Discovered & Connected Multi-Device Mesh Panels
                    MultiDeviceMeshPanel(
                        discoveredPeers = discoveredPeers,
                        connectedPeers = connectedPeers,
                        activeSpeakerName = activeSpeakerName,
                        onConnectPeer = { id -> viewModel.connectToPeer(id) },
                        onConnectAll = { viewModel.connectToAllDiscoveredPeers() }
                    )
                }

                // Interactive Push-To-Talk (PTT) Button
                PttButtonSection(
                    isTransmitting = isTransmitting,
                    isReceiving = isReceiving,
                    onPress = { viewModel.onPttPressed() },
                    onRelease = { viewModel.onPttReleased() }
                )
            }
        }
    }
}

@Composable
fun HeaderAndStatusSection(
    username: String,
    connectionStatus: String,
    peerCount: Int,
    isScanning: Boolean,
    audioOutputMode: AudioOutputMode,
    onEditUsername: () -> Unit,
    onToggleScan: () -> Unit,
    onToggleAudioOutput: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        // App Title Branding
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Sensors,
                contentDescription = null,
                tint = NeonCyan,
                modifier = Modifier.size(26.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "WALKIE YAPPIE",
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                color = TextPrimary
            )
        }

        // Active Callsign / Username Banner
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(top = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (username.isNotEmpty()) "CALLSIGN: $username" else "SET CALLSIGN",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = NeonCyan
            )
            IconButton(
                onClick = onEditUsername,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit Callsign",
                    tint = TextSecondary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Connection Status & Scan Control Row
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, SurfaceBorder, RoundedCornerShape(14.dp)),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1.0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "alpha"
                    )

                    val statusColor = when {
                        peerCount > 0 -> ReceiveGreen
                        isScanning -> NeonCyan
                        else -> TransmitRed
                    }

                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(statusColor.copy(alpha = if (isScanning) alpha else 1.0f))
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = connectionStatus,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = if (peerCount > 0) "$peerCount active mesh node(s)" else if (isScanning) "Scanning frequencies..." else "Scanner idle",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }

                // Scan Action Toggle Button
                Button(
                    onClick = onToggleScan,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isScanning) TransmitRed.copy(alpha = 0.8f) else NeonCyan
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Radar,
                        contentDescription = null,
                        tint = if (isScanning) Color.White else Color.Black,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isScanning) "STOP" else "SCAN",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isScanning) Color.White else Color.Black
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Audio Output Switcher Chip
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onToggleAudioOutput() }
                .border(1.dp, SurfaceBorder, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (audioOutputMode == AudioOutputMode.SPEAKER) Icons.Default.VolumeUp else Icons.Default.Call,
                        contentDescription = "Audio Output Mode",
                        tint = NeonCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (audioOutputMode == AudioOutputMode.SPEAKER) "Output: Stereo Speaker" else "Output: Phone Earpiece",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Surface(
                    color = if (audioOutputMode == AudioOutputMode.SPEAKER) ElectricBlue else TransmitRed,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (audioOutputMode == AudioOutputMode.SPEAKER) "STEREO" else "EARPIECE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun MultiDeviceMeshPanel(
    discoveredPeers: List<PeerDevice>,
    connectedPeers: List<PeerDevice>,
    activeSpeakerName: String?,
    onConnectPeer: (String) -> Unit,
    onConnectAll: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Discovered Devices Section (If any)
        if (discoveredPeers.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .border(1.dp, NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "AVAILABLE NEARBY (${discoveredPeers.size})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "CONNECT ALL",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = NeonCyan,
                            modifier = Modifier.clickable { onConnectAll() }
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    LazyColumn(
                        modifier = Modifier.height(80.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(discoveredPeers) { peer ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(DeepObsidian, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = peer.name,
                                    fontSize = 13.sp,
                                    color = TextPrimary
                                )
                                Button(
                                    onClick = { onConnectPeer(peer.endpointId) },
                                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("CONNECT", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Active Connected Mesh Peers List
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .border(1.dp, SurfaceBorder, RoundedCornerShape(14.dp)),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "CONNECTED MESH NODES (${connectedPeers.size})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                if (connectedPeers.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Tap SCAN to discover & connect to N devices...",
                            fontSize = 12.sp,
                            color = TextSecondary.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(connectedPeers) { peer ->
                            val isPeerSpeaking = activeSpeakerName != null && peer.name.contains(activeSpeakerName, ignoreCase = true)

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isPeerSpeaking) ReceiveGreen.copy(alpha = 0.15f) else DeepObsidian,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (isPeerSpeaking) ReceiveGreen else Color.Transparent,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(ReceiveGreen)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = peer.name,
                                        fontSize = 13.sp,
                                        fontWeight = if (isPeerSpeaking) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isPeerSpeaking) ReceiveGreen else TextPrimary
                                    )
                                }

                                if (isPeerSpeaking) {
                                    Surface(
                                        color = ReceiveGreen,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.GraphicEq,
                                                contentDescription = null,
                                                tint = Color.Black,
                                                modifier = Modifier.size(10.dp)
                                            )
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text(
                                                text = "SPEAKING",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Black,
                                                color = Color.Black
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
    }
}

@Composable
fun AudioStateVisualizer(
    isTransmitting: Boolean,
    isReceiving: Boolean,
    activeSpeakerName: String?
) {
    val stateText = when {
        isTransmitting -> "TRANSMITTING VOICE (YOU)..."
        isReceiving && activeSpeakerName != null -> "RECEIVING FROM: ${activeSpeakerName.uppercase()}"
        isReceiving -> "RECEIVING INCOMING AUDIO..."
        else -> "HOLD PTT BUTTON TO SPEAK"
    }

    val stateColor = when {
        isTransmitting -> TransmitRed
        isReceiving -> ReceiveGreen
        else -> TextSecondary
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stateText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = stateColor,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Animated Audio Waveform Bars
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(28.dp)
        ) {
            val isAnimated = isTransmitting || isReceiving
            val infiniteTransition = rememberInfiniteTransition(label = "wave")

            for (i in 1..9) {
                val duration = 300 + (i * 70)
                val barHeight by infiniteTransition.animateFloat(
                    initialValue = 6f,
                    targetValue = if (isAnimated) 26f else 6f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(duration, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "barHeight"
                )

                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(barHeight.dp)
                        .clip(CircleShape)
                        .background(if (isAnimated) stateColor else SurfaceBorder)
                )
            }
        }
    }
}

@Composable
fun PttButtonSection(
    isTransmitting: Boolean,
    isReceiving: Boolean,
    onPress: () -> Unit,
    onRelease: () -> Unit
) {
    var isTouched by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isTouched || isTransmitting) 1.15f else 1.0f,
        animationSpec = tween(150),
        label = "scale"
    )

    val buttonColor = when {
        isTransmitting -> TransmitRed
        isReceiving -> ReceiveGreen
        else -> ElectricBlue
    }

    val glowColor = when {
        isTransmitting -> TransmitRed.copy(alpha = 0.4f)
        isReceiving -> ReceiveGreen.copy(alpha = 0.4f)
        else -> NeonCyan.copy(alpha = 0.2f)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.padding(bottom = 12.dp)
    ) {
        // Outer Glowing Pulse Ring
        Box(
            modifier = Modifier
                .size(170.dp)
                .scale(scale * 1.1f)
                .clip(CircleShape)
                .background(glowColor)
        )

        // Main PTT Tactile Button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(145.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(buttonColor, buttonColor.copy(alpha = 0.8f))
                    )
                )
                .border(4.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isTouched = true
                            onPress()
                            tryAwaitRelease()
                            isTouched = false
                            onRelease()
                        }
                    )
                }
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = when {
                        isReceiving -> Icons.Default.VolumeUp
                        isTransmitting -> Icons.Default.GraphicEq
                        else -> Icons.Default.Mic
                    },
                    contentDescription = "Push To Talk",
                    tint = Color.White,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = when {
                        isTransmitting -> "SPEAKING"
                        isReceiving -> "LISTENING"
                        else -> "PUSH TO TALK"
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun UsernameOnboardingDialog(
    initialUsername: String,
    onSave: (String) -> Unit
) {
    var textInput by remember { mutableStateOf(initialUsername) }

    AlertDialog(
        onDismissRequest = { /* Force confirmation */ },
        containerColor = DarkSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ENTER CALLSIGN",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = TextPrimary
                )
            }
        },
        text = {
            Column {
                Text(
                    text = "Choose your username/callsign to identify your device in the N-device walkie-talkie mesh cluster:",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    singleLine = true,
                    placeholder = { Text("e.g. Alpha-1, Vasav", color = TextSecondary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = SurfaceBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (textInput.trim().isNotEmpty()) onSave(textInput) },
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("CONFIRM CALLSIGN", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun PermissionGuardView(onRequestPermissions: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.WifiOff,
            contentDescription = null,
            tint = TransmitRed,
            modifier = Modifier.size(72.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "PERMISSIONS REQUIRED",
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.5.sp,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "WalkieYappie needs Microphone, Bluetooth, and Nearby Wi-Fi permissions to establish off-grid P2P walkie-talkie mesh connections without cellular or internet.",
            fontSize = 14.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onRequestPermissions,
            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text(
                text = "GRANT PERMISSIONS",
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
    }
}
