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

data class ConnectionRequest(
    val endpointId: String,
    val requesterName: String
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
        private const val MAX_PACKET_AGE_MS = 800L

        private fun serializePeerAnnouncement(peers: List<PeerDevice>): ByteArray {
            val baos = ByteArrayOutputStream()
            val dos = DataOutputStream(baos)
            dos.writeByte(0x50) // Magic byte 'P' for Peer Announcement
            dos.writeShort(peers.size)
            for (peer in peers) {
                val idBytes = peer.endpointId.toByteArray(Charsets.UTF_8)
                dos.writeShort(idBytes.size)
                dos.write(idBytes)

                val nameBytes = peer.name.toByteArray(Charsets.UTF_8)
                dos.writeShort(nameBytes.size)
                dos.write(nameBytes)
            }
            dos.flush()
            return baos.toByteArray()
        }

        private fun deserializePeerAnnouncement(bytes: ByteArray): List<PeerDevice>? {
            return try {
                if (bytes.isEmpty() || bytes[0] != 0x50.toByte()) return null
                val bais = ByteArrayInputStream(bytes)
                val dis = DataInputStream(bais)
                dis.readByte() // Skip 'P'

                val size = dis.readShort().toInt()
                val list = mutableListOf<PeerDevice>()
                for (i in 0 until size) {
                    val idLen = dis.readShort().toInt()
                    val idBytes = ByteArray(idLen)
                    dis.readFully(idBytes)
                    val endpointId = String(idBytes, Charsets.UTF_8)

                    val nameLen = dis.readShort().toInt()
                    val nameBytes = ByteArray(nameLen)
                    dis.readFully(nameBytes)
                    val name = String(nameBytes, Charsets.UTF_8)

                    list.add(PeerDevice(endpointId, name))
                }
                list
            } catch (e: Exception) {
                null
            }
        }
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

    // Set of pre-announced mesh endpoints for full-mesh direct auto-interconnection
    private val knownMeshEndpoints = Collections.synchronizedSet(HashSet<String>())

    // Deduplication cache to prevent multi-hop echo loops
    private val seenPacketKeys = Collections.synchronizedSet(LinkedHashSet<String>())

    // Discovered available peers waiting for manual connection request
    private val _discoveredPeers = MutableStateFlow<List<PeerDevice>>(emptyList())
    val discoveredPeers: StateFlow<List<PeerDevice>> = _discoveredPeers.asStateFlow()

    // Incoming connection requests waiting for recipient approval
    private val _incomingRequests = MutableStateFlow<List<ConnectionRequest>>(emptyList())
    val incomingRequests: StateFlow<List<ConnectionRequest>> = _incomingRequests.asStateFlow()

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

    // 1. Connection Lifecycle Callback (Request-Accept + Full Mesh Direct Auto-Interconnection)
    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            Log.d(TAG, "Connection initiated by callsign '${info.endpointName}' ($endpointId)")
            pendingEndpointNames[endpointId] = info.endpointName

            if (knownMeshEndpoints.contains(endpointId)) {
                // Pre-announced mesh peer attempting direct 1-hop interconnection: Auto-accept direct link
                Log.i(TAG, "Peer '$endpointId' is pre-announced in mesh cluster. Auto-accepting direct link...")
                connectionsClient.acceptConnection(endpointId, payloadCallback)
            } else {
                // New initial device request: Requires explicit recipient user approval
                val request = ConnectionRequest(endpointId = endpointId, requesterName = info.endpointName)
                addIncomingRequest(request)
            }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            removeIncomingRequest(endpointId)
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    val peerName = pendingEndpointNames[endpointId]
                        ?: _discoveredPeers.value.find { it.endpointId == endpointId }?.name
                        ?: "Peer ${endpointId.take(4)}"

                    Log.i(TAG, "Connection APPROVED & Established with peer '$peerName' ($endpointId)")
                    addConnectedPeer(PeerDevice(endpointId = endpointId, name = peerName))
                    removeDiscoveredPeer(endpointId)
                    knownMeshEndpoints.add(endpointId)

                    // Announce existing mesh peers to new endpoint, and announce new endpoint to existing mesh peers for full-mesh direct links
                    syncMeshPeerAnnouncements(newEndpointId = endpointId, newPeerName = peerName)
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                    Log.w(TAG, "Connection rejected for $endpointId")
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
            removeIncomingRequest(endpointId)
            _connectedPeers.value = _connectedPeers.value.filter { it.endpointId != endpointId }
            updateStatusMessage()
        }
    }

    // 2. Endpoint Discovery Callback (No Auto-Connecting for unknown peers)
    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            val callsignName = info.endpointName
            Log.d(TAG, "Discovered nearby endpoint: '$callsignName' ($endpointId)")
            val newDiscoveredPeer = PeerDevice(endpointId = endpointId, name = callsignName)
            addDiscoveredPeer(newDiscoveredPeer)
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d(TAG, "Lost endpoint: $endpointId")
            removeDiscoveredPeer(endpointId)
        }
    }

    // 3. Payload Callback for processing incoming voice data & mesh control announcements
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
     * Announces existing mesh nodes to a newly joined endpoint, and announces the new endpoint
     * to all existing mesh nodes to trigger direct 1-hop interconnections if in radio range.
     */
    private fun syncMeshPeerAnnouncements(newEndpointId: String, newPeerName: String) {
        val existingPeers = _connectedPeers.value.filter { it.endpointId != newEndpointId && !it.endpointId.startsWith("lan_") }

        if (existingPeers.isNotEmpty()) {
            // 1. Send existing peer list to new endpoint
            val announceToNewBytes = serializePeerAnnouncement(existingPeers)
            connectionsClient.sendPayload(newEndpointId, Payload.fromBytes(announceToNewBytes))

            // 2. Send new peer info to existing endpoints
            val announceNewBytes = serializePeerAnnouncement(listOf(PeerDevice(newEndpointId, newPeerName)))
            val targetEndpoints = existingPeers.map { it.endpointId }
            connectionsClient.sendPayload(targetEndpoints, Payload.fromBytes(announceNewBytes))
        }
    }

    /**
     * User A sends an explicit connection request to discovered endpoint ID.
     */
    fun connectToPeer(endpointId: String) {
        connectionsClient.requestConnection(
            localUsername,
            endpointId,
            connectionLifecycleCallback
        ).addOnSuccessListener {
            Log.d(TAG, "Connection request sent to $endpointId under username '$localUsername'")
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed to send connection request to $endpointId", e)
        }
    }

    /**
     * User B explicitly APPROVES an incoming connection request.
     */
    fun acceptConnectionRequest(endpointId: String) {
        connectionsClient.acceptConnection(endpointId, payloadCallback)
            .addOnSuccessListener {
                Log.d(TAG, "Explicitly accepted connection request from $endpointId")
                removeIncomingRequest(endpointId)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to accept connection request from $endpointId", e)
                removeIncomingRequest(endpointId)
            }
    }

    /**
     * User B explicitly REJECTS an incoming connection request.
     */
    fun rejectConnectionRequest(endpointId: String) {
        connectionsClient.rejectConnection(endpointId)
            .addOnSuccessListener {
                Log.d(TAG, "Explicitly rejected connection request from $endpointId")
                removeIncomingRequest(endpointId)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to reject connection request from $endpointId", e)
                removeIncomingRequest(endpointId)
            }
    }

    /**
     * Sends connection requests to all available discovered devices.
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
     * Processes incoming mesh voice bytes or peer announcement control packets, drops stale buffered packets,
     * checks deduplication cache, plays audio locally, and performs Multi-Hop Relay forwarding.
     */
    private fun handleIncomingMeshBytes(rawBytes: ByteArray, sourceEndpointId: String) {
        // Check for Peer Announcement Control Packet (Magic byte 0x50 'P')
        if (rawBytes.isNotEmpty() && rawBytes[0] == 0x50.toByte()) {
            val announcedPeers = deserializePeerAnnouncement(rawBytes)
            if (announcedPeers != null) {
                announcedPeers.forEach { peer ->
                    knownMeshEndpoints.add(peer.endpointId)
                    if (peer.endpointId != localDeviceUuid &&
                        !_connectedPeers.value.any { it.endpointId == peer.endpointId }) {
                        Log.i(TAG, "Received Mesh Peer Announcement for '${peer.name}' (${peer.endpointId}). Attempting direct 1-hop connection...")
                        connectToPeer(peer.endpointId)
                    }
                }
            }
            return
        }

        val meshPacket = MeshPacket.deserialize(rawBytes)
        if (meshPacket == null) {
            _isReceivingAudio.value = true
            onAudioPayloadReceived?.invoke(rawBytes)
            _isReceivingAudio.value = false
            return
        }

        // Stale Packet Drop Filter: Drop packets delayed >800ms in network/OS buffers
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

        // 1. Play audio locally on current device
        onAudioPayloadReceived?.invoke(meshPacket.audioData)

        // 2. Multi-Hop Relay Forwarding (for out-of-range nodes): Increment hop count and forward to neighboring nodes
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

    private fun addIncomingRequest(request: ConnectionRequest) {
        val current = _incomingRequests.value.toMutableList()
        if (current.none { it.endpointId == request.endpointId }) {
            current.add(request)
            _incomingRequests.value = current
        }
    }

    private fun removeIncomingRequest(endpointId: String) {
        _incomingRequests.value = _incomingRequests.value.filter { it.endpointId != endpointId }
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
            _incomingRequests.value = emptyList()
            _connectedPeers.value = emptyList()
            knownMeshEndpoints.clear()
            _connectionStatus.value = "Disconnected"
            Log.i(TAG, "Mesh network stopped completely")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping mesh network", e)
        }
    }
}
