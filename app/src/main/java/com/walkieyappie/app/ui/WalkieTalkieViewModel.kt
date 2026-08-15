package com.walkieyappie.app.ui

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.walkieyappie.app.audio.AudioEngine
import com.walkieyappie.app.audio.AudioOutputMode
import com.walkieyappie.app.data.ConnectionRequest
import com.walkieyappie.app.data.NearbyManager
import com.walkieyappie.app.data.PeerDevice
import com.walkieyappie.app.util.PermissionsUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WalkieTalkieViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("walkie_yappie_prefs", Context.MODE_PRIVATE)

    val nearbyManager = NearbyManager(application)
    val audioEngine = AudioEngine()

    private val _username = MutableStateFlow(
        prefs.getString("username", "") ?: ""
    )
    val username: StateFlow<String> = _username.asStateFlow()

    private val _showUsernameDialog = MutableStateFlow(_username.value.isEmpty())
    val showUsernameDialog: StateFlow<Boolean> = _showUsernameDialog.asStateFlow()

    private val _hasPermissions = MutableStateFlow(PermissionsUtils.hasPermissions(application))
    val hasPermissions: StateFlow<Boolean> = _hasPermissions.asStateFlow()

    private val _isTransmitting = MutableStateFlow(false)
    val isTransmitting: StateFlow<Boolean> = _isTransmitting.asStateFlow()

    private val _audioOutputMode = MutableStateFlow(AudioOutputMode.SPEAKER)
    val audioOutputMode: StateFlow<AudioOutputMode> = _audioOutputMode.asStateFlow()

    val connectionStatus: StateFlow<String> = nearbyManager.connectionStatus
    val discoveredPeers: StateFlow<List<PeerDevice>> = nearbyManager.discoveredPeers
    val incomingRequests: StateFlow<List<ConnectionRequest>> = nearbyManager.incomingRequests
    val connectedPeers: StateFlow<List<PeerDevice>> = nearbyManager.connectedPeers
    val isScanning: StateFlow<Boolean> = nearbyManager.isScanning
    val isReceivingAudio: StateFlow<Boolean> = nearbyManager.isReceivingAudio
    val activeSpeakerName: StateFlow<String?> = nearbyManager.activeSpeakerName

    init {
        audioEngine.setAudioOutputMode(application, _audioOutputMode.value)

        // Wire incoming audio bytes from Nearby Connections or UDP LAN to AudioEngine player
        nearbyManager.onAudioPayloadReceived = { bytes ->
            audioEngine.playAudioChunk(bytes)
        }

        // Wire incoming stream from Nearby Connections to AudioEngine player
        nearbyManager.onAudioStreamReceived = { inputStream ->
            audioEngine.playAudioStream(inputStream, viewModelScope)
        }

        // Wire mic recorded audio bytes to Nearby Connections P2P and UDP LAN broadcast
        audioEngine.onAudioChunkCaptured = { chunk ->
            nearbyManager.sendAudioBytes(chunk, viewModelScope)
        }

        if (_hasPermissions.value && _username.value.isNotEmpty()) {
            nearbyManager.startMeshNetwork(viewModelScope, _username.value)
        }
    }

    fun saveUsername(newUsername: String) {
        val trimmed = newUsername.trim()
        if (trimmed.isNotEmpty()) {
            _username.value = trimmed
            prefs.edit().putString("username", trimmed).apply()
            _showUsernameDialog.value = false

            nearbyManager.updateUsername(trimmed)
            if (_hasPermissions.value) {
                nearbyManager.startMeshNetwork(viewModelScope, trimmed)
            }
        }
    }

    fun openUsernameDialog() {
        _showUsernameDialog.value = true
    }

    fun toggleScanning() {
        if (isScanning.value) {
            nearbyManager.stopDiscoveryAndAdvertising()
        } else {
            val name = _username.value.ifEmpty { Build.MODEL ?: "WalkieYappie Device" }
            nearbyManager.startMeshNetwork(viewModelScope, name)
        }
    }

    fun connectToPeer(endpointId: String) {
        nearbyManager.connectToPeer(endpointId)
    }

    fun acceptConnectionRequest(endpointId: String) {
        nearbyManager.acceptConnectionRequest(endpointId)
    }

    fun rejectConnectionRequest(endpointId: String) {
        nearbyManager.rejectConnectionRequest(endpointId)
    }

    fun connectToAllDiscoveredPeers() {
        nearbyManager.connectToAllDiscoveredPeers()
    }

    fun toggleAudioOutputMode() {
        val nextMode = if (_audioOutputMode.value == AudioOutputMode.SPEAKER) {
            AudioOutputMode.EARPIECE
        } else {
            AudioOutputMode.SPEAKER
        }
        _audioOutputMode.value = nextMode
        audioEngine.setAudioOutputMode(getApplication(), nextMode)
    }

    fun onPermissionsResult(granted: Boolean) {
        _hasPermissions.value = granted
        if (granted && _username.value.isNotEmpty()) {
            nearbyManager.startMeshNetwork(viewModelScope, _username.value)
        }
    }

    fun startMeshNetwork() {
        if (PermissionsUtils.hasPermissions(getApplication())) {
            val name = _username.value.ifEmpty { Build.MODEL ?: "WalkieYappie Device" }
            nearbyManager.startMeshNetwork(viewModelScope, name)
        }
    }

    fun onPttPressed() {
        if (!_isTransmitting.value) {
            audioEngine.flushAudioBuffer()
            _isTransmitting.value = true
            audioEngine.startRecording(viewModelScope)
        }
    }

    fun onPttReleased() {
        if (_isTransmitting.value) {
            _isTransmitting.value = false
            audioEngine.stopRecording()
            audioEngine.flushAudioBuffer()
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioEngine.release()
        nearbyManager.stopMeshNetwork()
    }
}
