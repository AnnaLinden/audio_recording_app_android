package com.example.audio_lab

import android.Manifest
import android.content.pm.PackageManager
import android.media.*
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {

    // Audio Settings
    private val sampleRate = 44100 // Hz
    private val channelConfigIn = AudioFormat.CHANNEL_IN_MONO
    private val channelConfigOut = AudioFormat.CHANNEL_OUT_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    private var minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfigIn, audioFormat)

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null

    private var recordingJob: Job? = null
    private var playbackJob: Job? = null

    private val fileName: String by lazy {
        "${externalCacheDir?.absolutePath}/recording.pcm"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AudioApp()
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun AudioApp() {
        val context = LocalContext.current
        var isRecording by remember { mutableStateOf(false) }
        var isPlaying by remember { mutableStateOf(false) }
        var hasRecordPermission by remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            )
        }

        // Handle Permissions
        val requestPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            hasRecordPermission = granted
            if (!granted) {
                // Handle permission denial
                Toast.makeText(context, "Permission denied. Cannot record audio.", Toast.LENGTH_LONG).show()
            }
        }

        LaunchedEffect(key1 = hasRecordPermission) {
            if (!hasRecordPermission) {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Audio Recorder") }
                )
            }
        ) { paddingValues ->
            Surface(
                modifier = androidx.compose.ui.Modifier.padding(paddingValues)
            ) {
                Column(
                    modifier = androidx.compose.ui.Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Button(
                        onClick = {
                            if (isRecording) {
                                stopRecording()
                            } else {
                                startRecording()
                            }
                            isRecording = !isRecording
                        },
                        modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                        enabled = hasRecordPermission && !isPlaying // Disable if no permission or during playback
                    ) {
                        Text(if (isRecording) "Stop Recording" else "Start Recording")
                    }
                    Spacer(modifier = androidx.compose.ui.Modifier.height(16.dp))
                    Button(
                        onClick = {
                            if (isPlaying) {
                                stopPlayback()
                            } else {
                                startPlayback()
                            }
                            isPlaying = !isPlaying
                        },
                        modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                        enabled = !isRecording // Disable during recording
                    ) {
                        Text(if (isPlaying) "Stop Playback" else "Start Playback")
                    }
                }
            }
        }
    }

    private fun startRecording() {
        // Check if permission is granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted, handle accordingly
            Toast.makeText(this, "Permission denied. Cannot record audio.", Toast.LENGTH_LONG).show()
            return
        }

        val audioFormat = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(channelConfigIn)
            .build()

        audioRecord = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.MIC)
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(minBufferSize)
            .build()

        audioRecord?.startRecording()

        recordingJob = CoroutineScope(Dispatchers.IO).launch {
            val data = ByteArray(minBufferSize)
            val file = File(fileName)
            val outputStream = FileOutputStream(file)
            while (isActive) {
                val read = audioRecord?.read(data, 0, data.size) ?: 0
                if (read > 0) {
                    outputStream.write(data, 0, read)
                }
            }
            outputStream.close()
        }
    }

    private fun stopRecording() {
        recordingJob?.cancel()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    private fun startPlayback() {
        val audioFormat = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(channelConfigOut)
            .build()

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate, channelConfigOut, AudioFormat.ENCODING_PCM_16BIT
        )

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(minBufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.play()

        playbackJob = CoroutineScope(Dispatchers.IO).launch {
            val file = File(fileName)
            val inputStream = FileInputStream(file)
            val data = ByteArray(minBufferSize)
            var read: Int
            while (inputStream.read(data).also { read = it } != -1 && isActive) {
                audioTrack?.write(data, 0, read)
            }
            inputStream.close()
        }
    }

    private fun stopPlayback() {
        playbackJob?.cancel()
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRecording()
        stopPlayback()
    }
}
