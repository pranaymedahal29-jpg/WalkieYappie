package com.walkieyappie.app.data

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class PeerDevice(
    val endpointId: String,
    val name: String
)

/**
 * Encapsulates mesh voice packet header for multi-hop deduplication, timestamp filtering, & forwarding.
 */
data class MeshPacket(
    val senderId: String,
    val senderName: String,
    val sequenceNumber: Long,
    val hopCount: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val audioData: ByteArray
) {
    fun serialize(): ByteArray {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        dos.writeByte(0x57) // Magic byte 'W'
        dos.writeLong(sequenceNumber)
        dos.writeInt(hopCount)
        dos.writeLong(timestamp)

        val idBytes = senderId.toByteArray(Charsets.UTF_8)
        dos.writeShort(idBytes.size)
        dos.write(idBytes)

        val nameBytes = senderName.toByteArray(Charsets.UTF_8)
        dos.writeShort(nameBytes.size)
        dos.write(nameBytes)

        dos.write(audioData)
        dos.flush()
        return baos.toByteArray()
    }

    companion object {
        fun deserialize(bytes: ByteArray): MeshPacket? {
            return try {
                if (bytes.size < 25) return null
                val bais = ByteArrayInputStream(bytes)
                val dis = DataInputStream(bais)
                val magic = dis.readByte()
                if (magic != 0x57.toByte()) return null

                val seqNum = dis.readLong()
                val hopCount = dis.readInt()
                val timestamp = dis.readLong()

                val idLen = dis.readShort().toInt()
                val idBytes = ByteArray(idLen)
                dis.readFully(idBytes)
                val senderId = String(idBytes, Charsets.UTF_8)

                val nameLen = dis.readShort().toInt()
                val nameBytes = ByteArray(nameLen)
                dis.readFully(nameBytes)
                val senderName = String(nameBytes, Charsets.UTF_8)

                val audioData = ByteArray(dis.available())
                dis.readFully(audioData)

                MeshPacket(senderId, senderName, seqNum, hopCount, timestamp, audioData)
            } catch (e: Exception) {
                null
            }
        }
    }
}

class NearbyManager(private val context: Context) {

    companion object {
        private const val TAG = "NearbyManager"
        private const val SERVICE_ID = "com.walkieyappie.walkietalkie"
        private val STRATEGY = Strategy.P2P_CLUSTER
        private const val UDP_PORT = 8888
        private const val MAX_HOPS = 10
        private const val MAX_PACKET_AGE_MS = 800L // Drop stale packets delayed >800ms to eliminate 5s queueing lag
    }

    private val connectionsClient: ConnectionsClient by lazy {
        Nearby.getConnectionsClient(context)
    }

    var localUsername: String = Build.MODEL ?: "WalkieYappie Device"
        private set

    private val localDeviceUuid = UUID.randomUUID().toString().take(8)
    private var outgoingSeqNumber = 0L

    // Pending peer names map (endpointId -> remote callsign name)
    private val pendingEndpointNames = ConcurrentHashMap<String, String>()

    // Deduplication cache to prevent multi-hop echo loops
    private val seenPacketKeys = Collections.synchronizedSet(LinkedHashSet<String>())

    // Discovered available peers waiting for connection
    private val _discoveredPeers = MutableStateFlow<List<PeerDevice>>(emptyList())
    val discoveredPeers: StateFlow<List<PeerDevice>> = _discoveredPeers.asStateFlow()

    // Connected active peers in N-device mesh
    private val _connectedPeers = MutableStateFlow<List<PeerDevice>>(emptyList())
    val connectedPeers: StateFlow<List<PeerDevice>> = _connectedPeers.asStateFlow()

    private val _connectionStatus = MutableStateFlow("Idle")
    val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private val _isReceivingAudio = MutableStateFlow(false)
    val isReceivingAudio: StateFlow<Boolean> = _isReceivingAudio.asStateFlow()

    // Active speaker callsign name state flow
    private val _activeSpeakerName = MutableStateFlow<String?>(null)
    val activeSpeakerName: StateFlow<String?> = _activeSpeakerName.asStateFlow()
    private var speakerResetJob: Job? = null

    // Callback listener for incoming audio payloads
    var onAudioPayloadReceived: ((ByteArray) -> Unit)? = null
    var onAudioStreamReceived: ((InputStream) -> Unit)? = null

    // UDP LAN Mesh Socket variables
    private var udpSocket: DatagramSocket? = null
    private var udpJob: Job? = null

    // 1. Connection Lifecycle Callback (Nearby Connections P2P)
    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            Log.d(TAG, "Connection initiated with callsign '${info.endpointName}' ($endpointId). Auto-accepting...")
            pendingEndpointNames[endpointId] = info.endpointName

            connectionsClient.acceptConnection(endpointId, payloadCallback)
                .addOnSuccessListener {
                    Log.d(TAG, "Accepted connection from ${info.endpointName} ($endpointId)")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to accept connection from $endpointId", e)
                }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    val peerName = pendingEndpointNames[endpointId]
                        ?: _discoveredPeers.value.find { it.endpointId == endpointId }?.name
                        ?: "Peer ${endpointId.take(4)}"

                    Log.i(TAG, "Connected successfully to peer '$peerName' ($endpointId)")
                    addConnectedPeer(PeerDevice(endpointId = endpointId, name = peerName))
                    removeDiscoveredPeer(endpointId)
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                    Log.w(TAG, "Connection rejected by $endpointId")
                    pendingEndpointNames.remove(endpointId)
                }
                ConnectionsStatusCodes.STATUS_ERROR -> {
                    Log.e(TAG, "Connection error with $endpointId")
                    pendingEndpointNames.remove(endpointId)
                }
                else -> {
                    Log.w(TAG, "Unknown connection result: ${result.status.statusCode}")
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.i(TAG, "Peer disconnected: $endpointId")
            pendingEndpointNames.remove(endpointId)
            _connectedPeers.value = _connectedPeers.value.filter { it.endpointId != endpointId }
            updateStatusMessage()
        }
    }

    // 2. Endpoint Discovery Callback
    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            val callsignName = info.endpointName
            Log.d(TAG, "Discovered nearby endpoint: '$callsignName' ($endpointId)")
            val newDiscoveredPeer = PeerDevice(endpointId = endpointId, name = callsignName)
            addDiscoveredPeer(newDiscoveredPeer)

            // Auto-connect to form seamless multi-device cluster
            connectToPeer(endpointId)
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d(TAG, "Lost endpoint: $endpointId")
            removeDiscoveredPeer(endpointId)
        }
    }

    // 3. Payload Callback for processing incoming voice data
    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTES -> {
                    payload.asBytes()?.let { rawBytes ->
                        handleIncomingMeshBytes(rawBytes, sourceEndpointId = endpointId)
                    }
                }
                Payload.Type.STREAM -> {
                    payload.asStream()?.asInputStream()?.let { inputStream ->
                        onAudioStreamReceived?.invoke(inputStream)
                    }
                }
                else -> Log.w(TAG, "Received unsupported payload type: ${payload.type}")
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            if (update.status == PayloadTransferUpdate.Status.SUCCESS ||
                update.status == PayloadTransferUpdate.Status.FAILURE ||
                update.status == PayloadTransferUpdate.Status.CANCELED) {
                _isReceivingAudio.value = false
            }
        }
    }

    /**
     * Connects to a specific discovered endpoint ID.
     */
    fun connectToPeer(endpointId: String) {
        connectionsClient.requestConnection(
            localUsername,
            endpointId,
            connectionLifecycleCallback
        ).addOnSuccessListener {
            Log.d(TAG, "Connection requested to $endpointId under username '$localUsername'")
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed to request connection to $endpointId", e)
        }
    }

    /**
     * Connects to all N available discovered devices simultaneously.
     */
    fun connectToAllDiscoveredPeers() {
        _discoveredPeers.value.forEach { peer ->
            connectToPeer(peer.endpointId)
        }
    }

    /**
     * Updates callsign and re-advertises with new username broadcast.
     */
    fun updateUsername(newUsername: String) {
        val trimmed = newUsername.trim()
        if (trimmed.isNotEmpty() && this.localUsername != trimmed) {
            this.localUsername = trimmed
            Log.i(TAG, "Updated local username callsign to '$trimmed'")
            if (_isAdvertising.value) {
                startAdvertising()
            }
        }
    }

    /**
     * Starts both Nearby P2P advertising/discovery AND UDP LAN socket broadcasting.
     */
    fun startMeshNetwork(scope: CoroutineScope, username: String = localUsername) {
        if (username.trim().isNotEmpty()) {
            this.localUsername = username.trim()
        }
        startAdvertising()
        startDiscovery()
        startUdpLanEngine(scope)
    }

    fun startAdvertising() {
        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        try {
            connectionsClient.stopAdvertising()
        } catch (ignored: Exception) {}

        connectionsClient.startAdvertising(
            localUsername,
            SERVICE_ID,
            connectionLifecycleCallback,
            options
        ).addOnSuccessListener {
            Log.i(TAG, "Started advertising under callsign '$localUsername' & service $SERVICE_ID")
            _isAdvertising.value = true
            updateStatusMessage()
        }.addOnFailureListener { e ->
            Log.e(TAG, "Advertising failed for callsign '$localUsername'", e)
            _isAdvertising.value = false
        }
    }

    fun startDiscovery() {
        val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        try {
            connectionsClient.stopDiscovery()
        } catch (ignored: Exception) {}

        connectionsClient.startDiscovery(
            SERVICE_ID,
            endpointDiscoveryCallback,
            options
        ).addOnSuccessListener {
            Log.i(TAG, "Started discovery under service $SERVICE_ID")
            _isScanning.value = true
            updateStatusMessage()
        }.addOnFailureListener { e ->
            Log.e(TAG, "Discovery failed", e)
            _isScanning.value = false
        }
    }

    fun stopDiscoveryAndAdvertising() {
        try {
            connectionsClient.stopAdvertising()
            connectionsClient.stopDiscovery()
            _isAdvertising.value = false
            _isScanning.value = false
            updateStatusMessage()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping discovery", e)
        }
    }

    /**
     * Starts background UDP receiver loop for Hotspot LAN Mesh.
     */
    private fun startUdpLanEngine(scope: CoroutineScope) {
        udpJob?.cancel()
        udpJob = scope.launch(Dispatchers.IO) {
            try {
                if (udpSocket == null || udpSocket?.isClosed == true) {
                    udpSocket = DatagramSocket(UDP_PORT).apply {
                        broadcast = true
                        reuseAddress = true
                    }
                }
                val buffer = ByteArray(2048)
                val packet = DatagramPacket(buffer, buffer.size)

                Log.i(TAG, "UDP LAN Engine listening on port $UDP_PORT")

                while (isActive && udpSocket?.isClosed == false) {
                    udpSocket?.receive(packet)
                    if (packet.length > 0) {
                        val senderIp = packet.address.hostAddress
                        val receivedBytes = packet.data.copyOf(packet.length)
                        handleIncomingMeshBytes(receivedBytes, sourceEndpointId = "lan_${senderIp}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "UDP LAN Socket error", e)
            }
        }
    }

    /**
     * Processes incoming mesh audio bytes, drops stale buffered packets, checks deduplication cache,
     * plays audio locally, and performs Multi-Hop Relay forwarding.
     */
    private fun handleIncomingMeshBytes(rawBytes: ByteArray, sourceEndpointId: String) {
        val meshPacket = MeshPacket.deserialize(rawBytes)
        if (meshPacket == null) {
            _isReceivingAudio.value = true
            onAudioPayloadReceived?.invoke(rawBytes)
            _isReceivingAudio.value = false
            return
        }

        // 1. Stale Packet Drop Filter: Drop packets delayed >800ms in network/OS buffers
        val packetAge = System.currentTimeMillis() - meshPacket.timestamp
        if (packetAge > MAX_PACKET_AGE_MS) {
            Log.d(TAG, "Dropped stale buffered packet (age=${packetAge}ms)")
            return
        }

        // Ignore self-sent packets
        if (meshPacket.senderId == localDeviceUuid) return

        val packetKey = "${meshPacket.senderId}:${meshPacket.sequenceNumber}"

        synchronized(seenPacketKeys) {
            if (seenPacketKeys.contains(packetKey)) {
                return
            }
            seenPacketKeys.add(packetKey)
            if (seenPacketKeys.size > 2000) {
                val iterator = seenPacketKeys.iterator()
                if (iterator.hasNext()) iterator.next()
                iterator.remove()
            }
        }

        val speakerName = meshPacket.senderName.ifEmpty { "Peer (${meshPacket.senderId.take(4)})" }

        // Add LAN or P2P Peer with their custom Callsign Name if not present
        if (sourceEndpointId.startsWith("lan_")) {
            addConnectedPeer(PeerDevice(endpointId = sourceEndpointId, name = speakerName))
        }

        // Update active transmitting device name in state flow
        _activeSpeakerName.value = speakerName
        _isReceivingAudio.value = true

        speakerResetJob?.cancel()
        speakerResetJob = CoroutineScope(Dispatchers.Main).launch {
            delay(500)
            if (_activeSpeakerName.value == speakerName) {
                _activeSpeakerName.value = null
                _isReceivingAudio.value = false
            }
        }

        // 2. Play audio locally on current device
        onAudioPayloadReceived?.invoke(meshPacket.audioData)

        // 3. Multi-Hop Relay Forwarding: Increment hop count and forward to neighboring nodes
        if (meshPacket.hopCount < MAX_HOPS) {
            val relayedPacket = meshPacket.copy(hopCount = meshPacket.hopCount + 1)
            val relayedBytes = relayedPacket.serialize()

            val targetEndpoints = _connectedPeers.value
                .filter { !it.endpointId.startsWith("lan_") && it.endpointId != sourceEndpointId }
                .map { it.endpointId }

            if (targetEndpoints.isNotEmpty()) {
                val payload = Payload.fromBytes(relayedBytes)
                connectionsClient.sendPayload(targetEndpoints, payload)
            }
        }
    }

    /**
     * Packs recorded mic audio into MeshPacket envelope with local callsign and broadcasts over P2P mesh & UDP.
     */
    fun sendAudioBytes(pcmAudioBytes: ByteArray, scope: CoroutineScope) {
        val seq = ++outgoingSeqNumber
        val meshPacket = MeshPacket(
            senderId = localDeviceUuid,
            senderName = localUsername,
            sequenceNumber = seq,
            hopCount = 1,
            timestamp = System.currentTimeMillis(),
            audioData = pcmAudioBytes
        )
        val packedBytes = meshPacket.serialize()

        val packetKey = "$localDeviceUuid:$seq"
        seenPacketKeys.add(packetKey)

        // 1. Send via Nearby Connections P2P
        val endpoints = _connectedPeers.value
            .filter { !it.endpointId.startsWith("lan_") }
            .map { it.endpointId }

        if (endpoints.isNotEmpty()) {
            val payload = Payload.fromBytes(packedBytes)
            connectionsClient.sendPayload(endpoints, payload).addOnFailureListener { e ->
                Log.e(TAG, "Failed to send Nearby payload", e)
            }
        }

        // 2. Broadcast via UDP Socket over Hotspot LAN
        scope.launch(Dispatchers.IO) {
            try {
                val broadcastAddr = InetAddress.getByName("255.255.255.255")
                val packet = DatagramPacket(packedBytes, packedBytes.size, broadcastAddr, UDP_PORT)
                udpSocket?.send(packet)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send UDP LAN broadcast packet", e)
            }
        }
    }

    private fun addDiscoveredPeer(peer: PeerDevice) {
        val current = _discoveredPeers.value.toMutableList()
        if (current.none { it.endpointId == peer.endpointId } && _connectedPeers.value.none { it.endpointId == peer.endpointId }) {
            current.add(peer)
            _discoveredPeers.value = current
        }
    }

    private fun removeDiscoveredPeer(endpointId: String) {
        _discoveredPeers.value = _discoveredPeers.value.filter { it.endpointId != endpointId }
    }

    private fun addConnectedPeer(peer: PeerDevice) {
        val currentList = _connectedPeers.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.endpointId == peer.endpointId }
        if (existingIndex != -1) {
            currentList[existingIndex] = peer
        } else {
            currentList.add(peer)
        }
        _connectedPeers.value = currentList
        updateStatusMessage()
    }

    private fun updateStatusMessage() {
        val count = _connectedPeers.value.size
        _connectionStatus.value = when {
            count > 0 -> "Connected to $count device(s)"
            _isScanning.value || _isAdvertising.value -> "Scanning for nearby peers..."
            else -> "Disconnected"
        }
    }

    /**
     * Tears down all active connections, discovery, advertising, and UDP socket.
     */
    fun stopMeshNetwork() {
        try {
            udpJob?.cancel()
            udpJob = null
            udpSocket?.close()
            udpSocket = null

            connectionsClient.stopAdvertising()
            connectionsClient.stopDiscovery()
            connectionsClient.stopAllEndpoints()
            _isAdvertising.value = false
            _isScanning.value = false
            _discoveredPeers.value = emptyList()
            _connectedPeers.value = emptyList()
            _connectionStatus.value = "Disconnected"
            Log.i(TAG, "Mesh network stopped completely")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping mesh network", e)
        }
    }
}
