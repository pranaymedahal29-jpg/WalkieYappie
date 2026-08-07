package com.walkieyappie.app.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.InputStream

enum class AudioOutputMode {
    SPEAKER,  // Main Loudspeaker / Stereo Speaker
    EARPIECE  // Private Phone Earpiece Speaker
}

class AudioEngine {

    companion object {
        private const val TAG = "AudioEngine"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
        private const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val CHUNK_SIZE = 1920 // 60ms frame at 16kHz PCM 16bit MONO for optimal low-latency network framing
    }

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null

    private var recordingJob: Job? = null
    private var playbackJob: Job? = null

    @Volatile
    private var isRecording = false

    var currentOutputMode: AudioOutputMode = AudioOutputMode.SPEAKER
        private set

    // Callback invoked when a PCM chunk is recorded from the microphone
    var onAudioChunkCaptured: ((ByteArray) -> Unit)? = null

    init {
        initAudioTrack(AudioOutputMode.SPEAKER)
    }

    /**
     * Initializes low-latency AudioTrack with minimal hardware buffer size.
     */
    private fun initAudioTrack(mode: AudioOutputMode) {
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (ignored: Exception) {}

        val minBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, ENCODING)
        val bufferSize = minBufferSize // Smallest possible buffer size for minimum playback latency

        val usage = if (mode == AudioOutputMode.SPEAKER) {
            AudioAttributes.USAGE_MEDIA
        } else {
            AudioAttributes.USAGE_VOICE_COMMUNICATION
        }

        val contentType = if (mode == AudioOutputMode.SPEAKER) {
            AudioAttributes.CONTENT_TYPE_MUSIC
        } else {
            AudioAttributes.CONTENT_TYPE_SPEECH
        }

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(usage)
            .setContentType(contentType)
            .build()

        val audioFormat = AudioFormat.Builder()
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(CHANNEL_OUT)
            .setEncoding(ENCODING)
            .build()

        try {
            audioTrack = AudioTrack(
                audioAttributes,
                audioFormat,
                bufferSize,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            ).apply {
                play()
            }
            Log.i(TAG, "Low-latency AudioTrack initialized successfully with minBufferSize=$minBufferSize")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AudioTrack", e)
        }
    }

    /**
     * Toggles hardware audio output routing between Stereo Loudspeaker and Phone Earpiece.
     */
    fun setAudioOutputMode(context: Context, mode: AudioOutputMode) {
        currentOutputMode = mode
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return

        try {
            initAudioTrack(mode)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                val devices = audioManager.availableCommunicationDevices
                val targetDeviceType = if (mode == AudioOutputMode.SPEAKER) {
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                } else {
                    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                }

                val targetDevice = devices.find { it.type == targetDeviceType }
                if (targetDevice != null) {
                    val success = audioManager.setCommunicationDevice(targetDevice)
                    Log.i(TAG, "setCommunicationDevice($targetDeviceType) result: $success")
                } else {
                    audioManager.clearCommunicationDevice()
                    audioManager.mode = if (mode == AudioOutputMode.SPEAKER) AudioManager.MODE_NORMAL else AudioManager.MODE_IN_COMMUNICATION
                    audioManager.isSpeakerphoneOn = (mode == AudioOutputMode.SPEAKER)
                }
            } else {
                if (mode == AudioOutputMode.SPEAKER) {
                    audioManager.mode = AudioManager.MODE_NORMAL
                    audioManager.isSpeakerphoneOn = true
                } else {
                    audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                    audioManager.isSpeakerphoneOn = false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to route audio output mode", e)
        }
    }

    /**
     * Starts background microphone recording via AudioRecord when PTT is pressed.
     */
    @SuppressLint("MissingPermission")
    fun startRecording(scope: CoroutineScope) {
        if (isRecording) return

        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, ENCODING)
        val bufferSize = maxOf(minBufferSize, CHUNK_SIZE)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_IN,
                ENCODING,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                return
            }

            audioRecord?.startRecording()
            isRecording = true

            recordingJob = scope.launch(Dispatchers.IO) {
                val buffer = ByteArray(CHUNK_SIZE)
                Log.d(TAG, "Low-latency audio recording loop started")

                while (isActive && isRecording) {
                    val readBytes = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (readBytes > 0) {
                        val chunk = buffer.copyOf(readBytes)
                        onAudioChunkCaptured?.invoke(chunk)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting audio recording", e)
            stopRecording()
        }
    }

    /**
     * Stops recording and releases AudioRecord instance.
     */
    fun stopRecording() {
        isRecording = false
        recordingJob?.cancel()
        recordingJob = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            Log.d(TAG, "Audio recording stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioRecord", e)
        }
    }

    /**
     * Flushes hardware buffer to instantly clear audio queue lag.
     */
    fun flushAudioBuffer() {
        try {
            audioTrack?.flush()
        } catch (ignored: Exception) {}
    }

    /**
     * Feeds incoming PCM audio payload chunk into AudioTrack using NON_BLOCKING write.
     */
    fun playAudioChunk(chunk: ByteArray) {
        try {
            if (audioTrack?.playState != AudioTrack.PLAYSTATE_PLAYING) {
                audioTrack?.play()
            }
            audioTrack?.write(chunk, 0, chunk.size, AudioTrack.WRITE_NON_BLOCKING)
        } catch (e: Exception) {
            Log.e(TAG, "Error writing audio chunk to AudioTrack", e)
        }
    }

    /**
     * Streams incoming continuous PCM audio stream into AudioTrack.
     */
    fun playAudioStream(inputStream: InputStream, scope: CoroutineScope) {
        playbackJob?.cancel()
        playbackJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(CHUNK_SIZE)
            try {
                if (audioTrack?.playState != AudioTrack.PLAYSTATE_PLAYING) {
                    audioTrack?.play()
                }
                var bytesRead: Int = 0
                while (isActive && inputStream.read(buffer).also { bytesRead = it } != -1) {
                    if (bytesRead > 0) {
                        audioTrack?.write(buffer, 0, bytesRead, AudioTrack.WRITE_NON_BLOCKING)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading from incoming audio stream", e)
            } finally {
                try {
                    inputStream.close()
                } catch (ignored: Exception) {}
            }
        }
    }

    /**
     * Releases audio track and resources.
     */
    fun release() {
        stopRecording()
        playbackJob?.cancel()
        playbackJob = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
            Log.i(TAG, "AudioEngine released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioTrack", e)
        }
    }
}
