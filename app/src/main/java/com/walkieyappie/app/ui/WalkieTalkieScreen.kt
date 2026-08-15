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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextAlign.Companion.Center
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.walkieyappie.app.audio.AudioOutputMode
import com.walkieyappie.app.data.ConnectionRequest
import com.walkieyappie.app.data.PeerDevice
import com.walkieyappie.app.ui.theme.LedUnlitDot
import com.walkieyappie.app.ui.theme.MatrixGreenButtonFill
import com.walkieyappie.app.ui.theme.MatrixGreenIncomingBlock
import com.walkieyappie.app.ui.theme.MatrixGreenOutline
import com.walkieyappie.app.ui.theme.MatrixGreenText
import com.walkieyappie.app.ui.theme.PitchBlack
import com.walkieyappie.app.ui.theme.StatusBlue
import com.walkieyappie.app.ui.theme.StatusRed
import com.walkieyappie.app.util.PermissionsUtils

// -----------------------------------------------------------------------------
// 5x7 Dot Matrix Character Font Renderer Component
// -----------------------------------------------------------------------------

/**
 * Returns a 35-element BooleanArray (5 cols x 7 rows) representing a 5x7 LED dot matrix character.
 */
fun get5x7Matrix(char: Char): BooleanArray {
    val upper = char.uppercaseChar()
    val matrix = BooleanArray(35)

    val patternStr = when (upper) {
        'A' -> "01110 10001 10001 11111 10001 10001 10001"
        'B' -> "11110 10001 10001 11110 10001 10001 11110"
        'C' -> "01111 10000 10000 10000 10000 10000 01111"
        'D' -> "11110 10001 10001 10001 10001 10001 11110"
        'E' -> "11111 10000 10000 11110 10000 10000 11111"
        'F' -> "11111 10000 10000 11110 10000 10000 10000"
        'G' -> "01111 10000 10000 10011 10001 10001 01111"
        'H' -> "10001 10001 10001 11111 10001 10001 10001"
        'I' -> "11111 00100 00100 00100 00100 00100 11111"
        'J' -> "00111 00010 00010 00010 10010 10010 01100"
        'K' -> "10001 10010 10100 11000 10100 10010 10001"
        'L' -> "10000 10000 10000 10000 10000 10000 11111"
        'M' -> "10001 11011 10101 10001 10001 10001 10001"
        'N' -> "10001 11001 10101 10011 10001 10001 10001"
        'O' -> "01110 10001 10001 10001 10001 10001 01110"
        'P' -> "11110 10001 10001 11110 10000 10000 10000"
        'Q' -> "01110 10001 10001 10001 10101 10010 01101"
        'R' -> "11110 10001 10001 11110 10100 10010 10001"
        'S' -> "01111 10000 10000 01110 00001 00001 11110"
        'T' -> "11111 00100 00100 00100 00100 00100 00100"
        'U' -> "10001 10001 10001 10001 10001 10001 01110"
        'V' -> "10001 10001 10001 10001 10001 01010 00100"
        'W' -> "10001 10001 10001 10101 10101 11011 10001"
        'X' -> "10001 10001 01010 00100 01010 10001 10001"
        'Y' -> "10001 10001 01010 00100 00100 00100 00100"
        'Z' -> "11111 00001 00010 00100 01000 10000 11111"
        '0' -> "01110 10001 10011 10101 11001 10001 01110"
        '1' -> "00100 01100 00100 00100 00100 00100 01110"
        '2' -> "01110 10001 00001 00110 01000 10000 11111"
        '3' -> "11111 00010 00100 00010 00001 10001 01110"
        '4' -> "00010 00110 01010 10010 11111 00010 00010"
        '5' -> "11111 10000 11110 00001 00001 10001 01110"
        '6' -> "00110 01000 10000 11110 10001 10001 01110"
        '7' -> "11111 00001 00010 00100 01000 01000 01000"
        '8' -> "01110 10001 10001 01110 10001 10001 01110"
        '9' -> "01110 10001 10001 01111 00001 00010 01100"
        '-' -> "00000 00000 00000 11111 00000 00000 00000"
        ':' -> "00000 00100 00100 00000 00100 00100 00000"
        '(' -> "00010 00100 01000 01000 01000 00100 00010"
        ')' -> "01000 00100 00010 00010 00010 00100 01000"
        '?' -> "01110 10001 00001 00010 00100 00000 00100"
        '!' -> "00100 00100 00100 00100 00100 00000 00100"
        else -> "00000 00000 00000 00000 00000 00000 00000"
    }

    var idx = 0
    for (charC in patternStr) {
        if (charC == '1') {
            matrix[idx++] = true
        } else if (charC == '0') {
            matrix[idx++] = false
        }
    }
    return matrix
}

/**
 * Renders a single 5x7 dot-matrix character using square pixels with subtle unlit background dots.
 */
@Composable
fun DotMatrixChar(
    char: Char,
    litColor: Color = MatrixGreenText,
    unlitColor: Color = LedUnlitDot,
    dotSize: Dp = 2.5.dp,
    dotSpacing: Dp = 1.dp
) {
    val matrix = remember(char) { get5x7Matrix(char) }

    Canvas(
        modifier = Modifier.size(
            width = (dotSize * 5) + (dotSpacing * 4),
            height = (dotSize * 7) + (dotSpacing * 6)
        )
    ) {
        val dotPx = dotSize.toPx()
        val spacePx = dotSpacing.toPx()

        for (row in 0 until 7) {
            for (col in 0 until 5) {
                val isLit = matrix[(row * 5) + col]
                val x = col * (dotPx + spacePx)
                val y = row * (dotPx + spacePx)

                drawRoundRect(
                    color = if (isLit) litColor else unlitColor,
                    topLeft = Offset(x, y),
                    size = Size(dotPx, dotPx),
                    cornerRadius = CornerRadius(dotPx * 0.25f, dotPx * 0.25f)
                )
            }
        }
    }
}

/**
 * Renders a line of text using the grid-based 5x7 dot-matrix character generator.
 */
@Composable
fun DotMatrixText(
    text: String,
    litColor: Color = MatrixGreenText,
    unlitColor: Color = LedUnlitDot,
    dotSize: Dp = 2.5.dp,
    dotSpacing: Dp = 1.dp,
    charSpacing: Dp = 3.dp,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(charSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (ch in text) {
            DotMatrixChar(
                char = ch,
                litColor = litColor,
                unlitColor = unlitColor,
                dotSize = dotSize,
                dotSpacing = dotSpacing
            )
        }
    }
}

// -----------------------------------------------------------------------------
// Main WalkieYappie Screen (#000000 Pitch Black + Matrix Lime Green Theme)
// -----------------------------------------------------------------------------

@Composable
fun WalkieTalkieScreen(viewModel: WalkieTalkieViewModel) {
    val hasPermissions by viewModel.hasPermissions.collectAsState()
    val username by viewModel.username.collectAsState()
    val showUsernameDialog by viewModel.showUsernameDialog.collectAsState()
    val discoveredPeers by viewModel.discoveredPeers.collectAsState()
    val incomingRequests by viewModel.incomingRequests.collectAsState()
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
        color = PitchBlack
    ) {
        if (!hasPermissions) {
            PermissionGuardView {
                permissionLauncher.launch(PermissionsUtils.getRequiredPermissions())
            }
        } else {
            if (showUsernameDialog) {
                UsernameOnboardingDialog(
                    initialUsername = username,
                    onSave = { newName -> viewModel.saveUsername(newName) }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 1. Blocky Pixel LED Title "WALKIE-YAPPIE"
                DotMatrixText(
                    text = "WALKIE-YAPPIE",
                    litColor = MatrixGreenText,
                    unlitColor = LedUnlitDot,
                    dotSize = 3.5.dp,
                    dotSpacing = 1.dp,
                    charSpacing = 4.dp,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 2. LED Message Screen Box (#000000 interior with #008E00 outline border)
                LedMessageScreenBox(
                    isTransmitting = isTransmitting,
                    isReceiving = isReceiving,
                    activeSpeakerName = activeSpeakerName,
                    incomingRequests = incomingRequests,
                    discoveredPeers = discoveredPeers,
                    connectedPeers = connectedPeers,
                    isScanning = isScanning,
                    localUsername = username
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 3. Interactive Connection Requests & Active Connections Panel (#002F00 block background)
                ConnectionRequestsAndPeersPanel(
                    discoveredPeers = discoveredPeers,
                    incomingRequests = incomingRequests,
                    connectedPeers = connectedPeers,
                    onRequestConnect = { id -> viewModel.connectToPeer(id) },
                    onAcceptRequest = { id -> viewModel.acceptConnectionRequest(id) },
                    onRejectRequest = { id -> viewModel.rejectConnectionRequest(id) }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 4. Status Indicator Row (SPEAKING, LISTENING, FINDING NEARBY DEVICES)
                StatusIndicatorRow(
                    isTransmitting = isTransmitting,
                    isReceiving = isReceiving,
                    isScanning = isScanning
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 5. Scan Button & Earpiece / Speaker Dual-Icon Toggle Row
                ControlsRow(
                    isScanning = isScanning,
                    audioOutputMode = audioOutputMode,
                    onToggleScan = { viewModel.toggleScanning() },
                    onToggleAudioOutput = { viewModel.toggleAudioOutputMode() }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 6. Clean, Plain Tactile Push To Talk (PTT) Circle Button (No background hue)
                CleanPttCircleButton(
                    isTransmitting = isTransmitting,
                    onPress = { viewModel.onPttPressed() },
                    onRelease = { viewModel.onPttReleased() }
                )

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Sub-Composables
// -----------------------------------------------------------------------------

/**
 * Rectangular LED readout screen panel with #008E00 rounded outline border and live dot-matrix display.
 */
@Composable
fun LedMessageScreenBox(
    isTransmitting: Boolean,
    isReceiving: Boolean,
    activeSpeakerName: String?,
    incomingRequests: List<ConnectionRequest>,
    discoveredPeers: List<PeerDevice>,
    connectedPeers: List<PeerDevice>,
    isScanning: Boolean,
    localUsername: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(165.dp)
            .border(2.dp, MatrixGreenOutline, RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(containerColor = PitchBlack),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.Start
        ) {
            val line1 = when {
                isTransmitting -> "SPEAKING"
                isReceiving && activeSpeakerName != null -> "RX: ${activeSpeakerName.take(6).uppercase()}"
                isReceiving -> "RECEIVING"
                incomingRequests.isNotEmpty() -> "REQ: ${incomingRequests.first().requesterName.take(6).uppercase()}"
                connectedPeers.isNotEmpty() -> "CONN: ${connectedPeers.first().name.take(6).uppercase()}"
                discoveredPeers.isNotEmpty() -> "DISC: ${discoveredPeers.first().name.take(6).uppercase()}"
                isScanning -> "SEARCHING"
                else -> "STANDBY"
            }

            val line2 = when {
                isTransmitting -> "TX ACTIVE"
                isReceiving -> "VOICE STREAM"
                incomingRequests.isNotEmpty() -> "INCOMING REQ"
                connectedPeers.isNotEmpty() -> "${connectedPeers.size} PEER MESH"
                discoveredPeers.isNotEmpty() -> "SEND REQUEST"
                else -> "CALL: ${localUsername.take(6).uppercase()}"
            }

            val line3 = when {
                incomingRequests.isNotEmpty() -> "ACCEPT / REJECT"
                connectedPeers.isNotEmpty() -> "CH 01 ACTIVE"
                discoveredPeers.isNotEmpty() -> "TAP TO PAIR"
                isScanning -> "BLE DISCOVERY"
                else -> "IDLE MESH"
            }

            DotMatrixText(
                text = line1,
                litColor = MatrixGreenText,
                unlitColor = LedUnlitDot,
                dotSize = 2.2.dp,
                dotSpacing = 1.dp,
                charSpacing = 3.dp
            )

            DotMatrixText(
                text = line2,
                litColor = MatrixGreenText,
                unlitColor = LedUnlitDot,
                dotSize = 2.2.dp,
                dotSpacing = 1.dp,
                charSpacing = 3.dp
            )

            DotMatrixText(
                text = line3,
                litColor = MatrixGreenText,
                unlitColor = LedUnlitDot,
                dotSize = 2.2.dp,
                dotSpacing = 1.dp,
                charSpacing = 3.dp
            )
        }
    }
}

/**
 * Interactive connection requests list and active connected devices list panel (#002F00 block background).
 * Implements explicit Request and Accept model (NO auto-connecting).
 */
@Composable
fun ConnectionRequestsAndPeersPanel(
    discoveredPeers: List<PeerDevice>,
    incomingRequests: List<ConnectionRequest>,
    connectedPeers: List<PeerDevice>,
    onRequestConnect: (String) -> Unit,
    onAcceptRequest: (String) -> Unit,
    onRejectRequest: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // 1. INCOMING CONNECTION REQUESTS (Recipient Explicit Approval Block)
        if (incomingRequests.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .border(2.dp, MatrixGreenOutline, RoundedCornerShape(8.dp)),
                colors = CardDefaults.cardColors(containerColor = MatrixGreenIncomingBlock),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    DotMatrixText(
                        text = "INCOMING REQ (${incomingRequests.size})",
                        litColor = MatrixGreenText,
                        unlitColor = LedUnlitDot,
                        dotSize = 1.5.dp,
                        dotSpacing = 0.8.dp,
                        charSpacing = 2.dp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    incomingRequests.forEach { req ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .background(PitchBlack, RoundedCornerShape(6.dp))
                                .border(1.dp, MatrixGreenOutline.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            DotMatrixText(
                                text = req.requesterName.take(8).uppercase(),
                                litColor = MatrixGreenText,
                                unlitColor = LedUnlitDot,
                                dotSize = 1.6.dp,
                                dotSpacing = 0.8.dp,
                                charSpacing = 2.dp
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = { onAcceptRequest(req.endpointId) },
                                    colors = ButtonDefaults.buttonColors(containerColor = MatrixGreenButtonFill),
                                    border = BorderStroke(1.dp, MatrixGreenOutline),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    DotMatrixText(
                                        text = "ACCEPT",
                                        litColor = MatrixGreenText,
                                        unlitColor = LedUnlitDot,
                                        dotSize = 1.2.dp,
                                        dotSpacing = 0.6.dp,
                                        charSpacing = 1.5.dp
                                    )
                                }

                                Button(
                                    onClick = { onRejectRequest(req.endpointId) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x66FF3D00)),
                                    border = BorderStroke(1.dp, StatusRed),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    DotMatrixText(
                                        text = "REJECT",
                                        litColor = StatusRed,
                                        unlitColor = LedUnlitDot,
                                        dotSize = 1.2.dp,
                                        dotSpacing = 0.6.dp,
                                        charSpacing = 1.5.dp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. DISCOVERED NEARBY PEERS (Send Request List)
        if (discoveredPeers.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .border(1.5.dp, MatrixGreenOutline.copy(alpha = 0.8f), RoundedCornerShape(8.dp)),
                colors = CardDefaults.cardColors(containerColor = MatrixGreenIncomingBlock),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    DotMatrixText(
                        text = "DISCOVERED NEARBY (${discoveredPeers.size})",
                        litColor = MatrixGreenText,
                        unlitColor = LedUnlitDot,
                        dotSize = 1.4.dp,
                        dotSpacing = 0.7.dp,
                        charSpacing = 1.8.dp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    discoveredPeers.forEach { peer ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .background(PitchBlack, RoundedCornerShape(6.dp))
                                .border(1.dp, MatrixGreenOutline.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            DotMatrixText(
                                text = peer.name.take(8).uppercase(),
                                litColor = MatrixGreenText,
                                unlitColor = LedUnlitDot,
                                dotSize = 1.5.dp,
                                dotSpacing = 0.7.dp,
                                charSpacing = 1.8.dp
                            )

                            Button(
                                onClick = { onRequestConnect(peer.endpointId) },
                                colors = ButtonDefaults.buttonColors(containerColor = MatrixGreenButtonFill),
                                border = BorderStroke(1.dp, MatrixGreenOutline),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                DotMatrixText(
                                    text = "CONNECT",
                                    litColor = MatrixGreenText,
                                    unlitColor = LedUnlitDot,
                                    dotSize = 1.2.dp,
                                    dotSpacing = 0.6.dp,
                                    charSpacing = 1.5.dp
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. ACTIVE CONNECTED MESH NODES
        if (connectedPeers.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, MatrixGreenOutline, RoundedCornerShape(8.dp)),
                colors = CardDefaults.cardColors(containerColor = MatrixGreenIncomingBlock),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    DotMatrixText(
                        text = "CONNECTED (${connectedPeers.size})",
                        litColor = MatrixGreenText,
                        unlitColor = LedUnlitDot,
                        dotSize = 1.5.dp,
                        dotSpacing = 0.8.dp,
                        charSpacing = 2.dp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    connectedPeers.forEach { peer ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MatrixGreenText)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            DotMatrixText(
                                text = "PEER: ${peer.name.take(8).uppercase()}",
                                litColor = MatrixGreenText,
                                unlitColor = LedUnlitDot,
                                dotSize = 1.4.dp,
                                dotSpacing = 0.7.dp,
                                charSpacing = 1.8.dp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Status indicator row with 3 circles and pixel font labels.
 */
@Composable
fun StatusIndicatorRow(
    isTransmitting: Boolean,
    isReceiving: Boolean,
    isScanning: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "scanBlink")
    val blinkAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blinkAlpha"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.Top
    ) {
        // Red - SPEAKING
        StatusCircleItem(
            label = "SPEAKING",
            color = StatusRed,
            isActive = isTransmitting
        )

        // Matrix Green - LISTENING
        StatusCircleItem(
            label = "LISTENING",
            color = MatrixGreenText,
            isActive = isReceiving || (!isTransmitting && !isScanning)
        )

        // Blue - FINDING NEARBY DEVICES (BLINKING)
        StatusCircleItem(
            label = "FINDING\nNEARBY\nDEVICES\n(BLINKING)",
            color = StatusBlue,
            isActive = isScanning,
            alphaModifier = if (isScanning) blinkAlpha else 1.0f
        )
    }
}

@Composable
fun StatusCircleItem(
    label: String,
    color: Color,
    isActive: Boolean,
    alphaModifier: Float = 1.0f
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(90.dp)
    ) {
        // Labeled Circle Indicator
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(
                    if (isActive) color.copy(alpha = alphaModifier) else Color.Transparent
                )
                .border(
                    2.dp,
                    if (isActive) color else MatrixGreenOutline.copy(alpha = 0.5f),
                    CircleShape
                )
        )

        Spacer(modifier = Modifier.height(8.dp))

        // LED-style caps-lock label in pixel font
        for (line in label.split("\n")) {
            DotMatrixText(
                text = line,
                litColor = MatrixGreenText,
                unlitColor = LedUnlitDot,
                dotSize = 1.2.dp,
                dotSpacing = 0.6.dp,
                charSpacing = 1.5.dp
            )
            Spacer(modifier = Modifier.height(2.dp))
        }
    }
}

/**
 * Scan Button & Dual-Icon Earpiece / Speaker Pill Switch Toggle Controls Row.
 */
@Composable
fun ControlsRow(
    isScanning: Boolean,
    audioOutputMode: AudioOutputMode,
    onToggleScan: () -> Unit,
    onToggleAudioOutput: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Transparent Green Scan Button (#005F00 with outline #008E00 & DotMatrixText)
        Button(
            onClick = onToggleScan,
            colors = ButtonDefaults.buttonColors(containerColor = MatrixGreenButtonFill),
            border = BorderStroke(1.5.dp, MatrixGreenOutline),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .width(115.dp)
                .height(42.dp)
        ) {
            DotMatrixText(
                text = if (isScanning) "STOP" else "SCAN",
                litColor = MatrixGreenText,
                unlitColor = LedUnlitDot,
                dotSize = 1.8.dp,
                dotSpacing = 0.8.dp,
                charSpacing = 2.dp
            )
        }

        // Dual-Icon Earpiece / Speaker Dual Pill Switch Toggle
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                onClick = onToggleAudioOutput,
                shape = RoundedCornerShape(20.dp),
                color = MatrixGreenIncomingBlock,
                border = BorderStroke(1.5.dp, MatrixGreenOutline),
                modifier = Modifier.height(38.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp)
                ) {
                    // Earpiece Side Icon Button
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (audioOutputMode == AudioOutputMode.EARPIECE) MatrixGreenButtonFill else Color.Transparent)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = "Earpiece Mode",
                                tint = MatrixGreenText,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Speaker Side Icon Button
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (audioOutputMode == AudioOutputMode.SPEAKER) MatrixGreenButtonFill else Color.Transparent)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = "Speaker Mode",
                                tint = MatrixGreenText,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            DotMatrixText(
                text = "EAR/SPK",
                litColor = MatrixGreenText,
                unlitColor = LedUnlitDot,
                dotSize = 1.1.dp,
                dotSpacing = 0.5.dp,
                charSpacing = 1.2.dp
            )
        }
    }
}

/**
 * Clean, Plain Push To Talk (PTT) Circle Button (No background hue or gradients).
 */
@Composable
fun CleanPttCircleButton(
    isTransmitting: Boolean,
    onPress: () -> Unit,
    onRelease: () -> Unit
) {
    var isTouched by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isTouched || isTransmitting) 0.96f else 1.0f,
        animationSpec = tween(100),
        label = "scale"
    )

    val activeColor = if (isTransmitting) StatusRed else MatrixGreenText
    val borderColor = if (isTransmitting) StatusRed else MatrixGreenOutline

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        // Plain, Clean Circle Button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(205.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(PitchBlack)
                .border(2.dp, borderColor, CircleShape)
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
                    imageVector = if (isTransmitting) Icons.Default.GraphicEq else Icons.Default.Mic,
                    contentDescription = "Push To Talk",
                    tint = activeColor,
                    modifier = Modifier.size(42.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Pixel LED Dot Matrix Typography
                if (isTransmitting) {
                    DotMatrixText(
                        text = "TRANSMITTING",
                        litColor = StatusRed,
                        unlitColor = LedUnlitDot,
                        dotSize = 1.6.dp,
                        dotSpacing = 0.8.dp,
                        charSpacing = 2.dp
                    )
                } else {
                    DotMatrixText(
                        text = "PUSH TO TALK",
                        litColor = MatrixGreenText,
                        unlitColor = LedUnlitDot,
                        dotSize = 1.6.dp,
                        dotSpacing = 0.8.dp,
                        charSpacing = 2.dp
                    )
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Onboarding & Permissions Helpers
// -----------------------------------------------------------------------------

@Composable
fun UsernameOnboardingDialog(
    initialUsername: String,
    onSave: (String) -> Unit
) {
    var textInput by remember { mutableStateOf(initialUsername) }

    AlertDialog(
        onDismissRequest = { },
        containerColor = MatrixGreenIncomingBlock,
        title = {
            DotMatrixText(
                text = "ENTER CALLSIGN",
                litColor = MatrixGreenText,
                unlitColor = LedUnlitDot,
                dotSize = 2.2.dp,
                dotSpacing = 1.dp,
                charSpacing = 3.dp
            )
        },
        text = {
            Column {
                Text(
                    text = "Choose your radio callsign for the CB WalkieYappie mesh:",
                    fontSize = 12.sp,
                    color = MatrixGreenText
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    singleLine = true,
                    placeholder = { Text("e.g. ALPHA-1", color = MatrixGreenOutline) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MatrixGreenText,
                        unfocusedBorderColor = MatrixGreenOutline,
                        focusedTextColor = MatrixGreenText,
                        unfocusedTextColor = MatrixGreenText
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (textInput.trim().isNotEmpty()) onSave(textInput) },
                colors = ButtonDefaults.buttonColors(containerColor = MatrixGreenButtonFill),
                border = BorderStroke(1.dp, MatrixGreenOutline),
                shape = RoundedCornerShape(8.dp)
            ) {
                DotMatrixText(
                    text = "SAVE CALLSIGN",
                    litColor = MatrixGreenText,
                    unlitColor = LedUnlitDot,
                    dotSize = 1.4.dp,
                    dotSpacing = 0.7.dp,
                    charSpacing = 1.8.dp
                )
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
            tint = StatusRed,
            modifier = Modifier.size(64.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        DotMatrixText(
            text = "PERMISSIONS REQUIRED",
            litColor = MatrixGreenText,
            unlitColor = LedUnlitDot,
            dotSize = 2.0.dp,
            dotSpacing = 1.0.dp,
            charSpacing = 2.5.dp
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "WalkieYappie requires Microphone, Bluetooth, and Nearby Wi-Fi permissions to establish CB radio mesh connections.",
            fontSize = 13.sp,
            color = MatrixGreenText,
            textAlign = Center
        )

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = onRequestPermissions,
            colors = ButtonDefaults.buttonColors(containerColor = MatrixGreenButtonFill),
            border = BorderStroke(1.dp, MatrixGreenOutline),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            DotMatrixText(
                text = "GRANT PERMISSIONS",
                litColor = MatrixGreenText,
                unlitColor = LedUnlitDot,
                dotSize = 1.6.dp,
                dotSpacing = 0.8.dp,
                charSpacing = 2.0.dp
            )
        }
    }
}
