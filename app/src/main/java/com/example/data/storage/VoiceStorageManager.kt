package com.example.data.storage

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.io.File
import java.util.Locale

/**
 * Handles on-device voice recording, Android native Speech-to-Text conversion,
 * and audio storage strictly within the app's private phone storage sandbox (`context.filesDir`).
 */
class VoiceStorageManager(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var mediaPlayer: MediaPlayer? = null
    private var isRecordingActive = false

    private var currentRecordingFile: File? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var playbackRunnable: Runnable? = null

    private val voiceDir: File by lazy {
        val dir = File(context.filesDir, "voice_vault")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        dir
    }

    interface VoiceRecordingListener {
        fun onRmsChanged(rmsDb: Float)
        fun onLiveTranscript(text: String, isFinal: Boolean)
        fun onSpeechStatus(status: String)
        fun onError(errorMessage: String)
    }

    /**
     * Creates standard system SpeechRecognizer intent.
     */
    fun createSpeechIntent(prompt: String = "Speak to write your diary..."): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
    }

    /**
     * Starts native Speech-to-Text and in-app internal audio file recording simultaneously.
     */
    fun startRecording(listener: VoiceRecordingListener): String {
        isRecordingActive = true

        // 1. Prepare in-app phone storage file
        val filename = "memo_${System.currentTimeMillis()}.m4a"
        val outputFile = File(voiceDir, filename)
        currentRecordingFile = outputFile

        // 2. Start MediaRecorder (Audio capture)
        try {
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context.applicationContext)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }
            mediaRecorder = recorder
        } catch (e: Exception) {
            Log.e("VoiceStorageManager", "MediaRecorder notice: ${e.message}")
        }

        // 3. Start SpeechRecognizer on Main thread with auto-reconnect
        mainHandler.post {
            startSpeechRecognizerInternal(listener)
        }

        return outputFile.name
    }

    private fun startSpeechRecognizerInternal(listener: VoiceRecordingListener) {
        if (!isRecordingActive) return

        try {
            speechRecognizer?.destroy()
            speechRecognizer = null

            val recognizer = if (SpeechRecognizer.isRecognitionAvailable(context.applicationContext)) {
                SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
            } else {
                SpeechRecognizer.createSpeechRecognizer(context)
            }

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    listener.onSpeechStatus("Listening... Speak into microphone")
                }

                override fun onBeginningOfSpeech() {
                    listener.onSpeechStatus("Speech detected... Transcribing")
                }

                override fun onRmsChanged(rmsdB: Float) {
                    val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.05f, 1f)
                    listener.onRmsChanged(normalized)
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    listener.onSpeechStatus("Processing speech...")
                }

                override fun onError(error: Int) {
                    val message = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording active"
                        SpeechRecognizer.ERROR_CLIENT -> "Listening paused"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
                        SpeechRecognizer.ERROR_NETWORK -> "Offline mode active"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech timeout"
                        SpeechRecognizer.ERROR_NO_MATCH -> "Waiting for speech..."
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Voice engine reconnecting..."
                        SpeechRecognizer.ERROR_SERVER -> "Recognition server error"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Listening..."
                        else -> "Status: $error"
                    }
                    listener.onSpeechStatus(message)

                    // Auto-restart recognizer if user is still actively recording
                    if (isRecordingActive && (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || error == SpeechRecognizer.ERROR_CLIENT)) {
                        mainHandler.postDelayed({
                            if (isRecordingActive) {
                                startSpeechRecognizerInternal(listener)
                            }
                        }, 400)
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val text = matches[0]
                        listener.onLiveTranscript(text, true)
                        listener.onSpeechStatus("Captured: \"$text\"")
                    }
                    // Keep listening if recording is still active
                    if (isRecordingActive) {
                        mainHandler.postDelayed({
                            if (isRecordingActive) {
                                startSpeechRecognizerInternal(listener)
                            }
                        }, 200)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val partialText = matches[0]
                        listener.onLiveTranscript(partialText, false)
                        listener.onSpeechStatus("Transcribing: \"$partialText\"")
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            }
            recognizer.startListening(intent)
            speechRecognizer = recognizer
        } catch (e: Exception) {
            Log.e("VoiceStorageManager", "SpeechRecognizer error: ${e.message}")
            listener.onSpeechStatus("Speak or tap System Mic button")
        }
    }

    /**
     * Stops audio recording and speech recognition, returning the relative internal file path.
     */
    fun stopRecording(): String {
        isRecordingActive = false
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            Log.e("VoiceStorageManager", "Error stopping MediaRecorder: ${e.message}")
        } finally {
            mediaRecorder = null
        }

        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.destroy()
            } catch (e: Exception) {
                Log.e("VoiceStorageManager", "Error stopping SpeechRecognizer: ${e.message}")
            } finally {
                speechRecognizer = null
            }
        }

        return currentRecordingFile?.name ?: ""
    }

    /**
     * Cancels active recording and deletes temp audio file from app phone storage.
     */
    fun cancelRecording() {
        isRecordingActive = false
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            // ignore
        } finally {
            mediaRecorder = null
        }

        mainHandler.post {
            try {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
            } catch (e: Exception) {
                // ignore
            } finally {
                speechRecognizer = null
            }
        }

        currentRecordingFile?.let {
            if (it.exists()) {
                it.delete()
            }
        }
        currentRecordingFile = null
    }

    /**
     * Plays back audio stored in internal app phone storage sandbox.
     */
    fun playAudio(
        fileName: String,
        onProgress: (Float) -> Unit,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) {
        stopPlayback()

        val file = if (fileName.startsWith("/")) File(fileName) else File(voiceDir, fileName)
        if (!file.exists() || file.length() == 0L) {
            onError("Audio recording file not found in local app storage.")
            return
        }

        try {
            val player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                start()
            }
            mediaPlayer = player

            val duration = player.duration.coerceAtLeast(1)
            playbackRunnable = object : Runnable {
                override fun run() {
                    mediaPlayer?.let { p ->
                        if (p.isPlaying) {
                            val current = p.currentPosition
                            onProgress((current.toFloat() / duration).coerceIn(0f, 1f))
                            mainHandler.postDelayed(this, 100)
                        }
                    }
                }
            }
            mainHandler.post(playbackRunnable!!)

            player.setOnCompletionListener {
                stopPlayback()
                onComplete()
            }

            player.setOnErrorListener { _, what, extra ->
                stopPlayback()
                onError("Playback error ($what, $extra)")
                true
            }
        } catch (e: Exception) {
            stopPlayback()
            onError("Cannot play audio: ${e.localizedMessage}")
        }
    }

    fun stopPlayback() {
        playbackRunnable?.let { mainHandler.removeCallbacks(it) }
        playbackRunnable = null
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (e: Exception) {
            // ignore
        } finally {
            mediaPlayer = null
        }
    }

    /**
     * Retrieves the File handle for an audio recording.
     */
    fun getAudioFile(fileName: String): File {
        return if (fileName.startsWith("/")) File(fileName) else File(voiceDir, fileName)
    }

    /**
     * Reads the raw audio bytes for uploading to Gemini Multimodal Audio API.
     */
    fun getAudioFileBytes(fileName: String): ByteArray? {
        if (fileName.isBlank()) return null
        return try {
            val file = getAudioFile(fileName)
            if (file.exists() && file.length() > 0) {
                file.readBytes()
            } else null
        } catch (e: Exception) {
            Log.e("VoiceStorageManager", "Failed to read audio bytes: ${e.message}")
            null
        }
    }

    /**
     * Deletes an audio file from the app-internal phone storage when a memory is removed.
     */
    fun deleteAudioFile(fileName: String): Boolean {
        if (fileName.isBlank()) return false
        val file = if (fileName.startsWith("/")) File(fileName) else File(voiceDir, fileName)
        return if (file.exists()) file.delete() else false
    }

    /**
     * Calculates total in-app voice recordings size in bytes.
     */
    fun getVoiceStorageBytes(): Long {
        return voiceDir.listFiles()?.sumOf { it.length() } ?: 0L
    }
}
