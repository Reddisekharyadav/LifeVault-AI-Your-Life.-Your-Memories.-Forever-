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
     * Starts native Speech-to-Text and in-app internal audio file recording simultaneously.
     */
    fun startRecording(listener: VoiceRecordingListener): String {
        // 1. Prepare in-app phone storage file
        val filename = "memo_${System.currentTimeMillis()}.m4a"
        val outputFile = File(voiceDir, filename)
        currentRecordingFile = outputFile

        // 2. Start MediaRecorder (Audio capture)
        try {
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
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
            Log.e("VoiceStorageManager", "MediaRecorder failed: ${e.message}", e)
            // Continue speech recognition even if audio file recording faces simulator limitation
        }

        // 3. Start SpeechRecognizer (Speech-to-Text transcription)
        mainHandler.post {
            try {
                if (SpeechRecognizer.isRecognitionAvailable(context)) {
                    speechRecognizer?.destroy()
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                        setRecognitionListener(object : RecognitionListener {
                            override fun onReadyForSpeech(params: Bundle?) {
                                listener.onSpeechStatus("Listening... Speak clearly into microphone")
                            }

                            override fun onBeginningOfSpeech() {
                                listener.onSpeechStatus("Speech detected... Transcribing live")
                            }

                            override fun onRmsChanged(rmsdB: Float) {
                                val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.05f, 1f)
                                listener.onRmsChanged(normalized)
                            }

                            override fun onBufferReceived(buffer: ByteArray?) {}

                            override fun onEndOfSpeech() {
                                listener.onSpeechStatus("Processing speech audio...")
                            }

                            override fun onError(error: Int) {
                                val message = when (error) {
                                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                                    SpeechRecognizer.ERROR_CLIENT -> "Client speech recognition error"
                                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
                                    SpeechRecognizer.ERROR_NETWORK -> "Network issue (Offline STT active)"
                                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech timeout"
                                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected yet. Keep speaking!"
                                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech service busy"
                                    SpeechRecognizer.ERROR_SERVER -> "Recognition server error"
                                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech heard"
                                    else -> "Speech recognizer status code: $error"
                                }
                                Log.w("VoiceStorageManager", "SpeechRecognizer error: $message ($error)")
                                listener.onSpeechStatus(message)
                            }

                            override fun onResults(results: Bundle?) {
                                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                if (!matches.isNullOrEmpty()) {
                                    val text = matches[0]
                                    listener.onLiveTranscript(text, true)
                                    listener.onSpeechStatus("Speech transcribed successfully!")
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
                        startListening(intent)
                    }
                } else {
                    listener.onSpeechStatus("Android STT engine ready (Type or speak)")
                }
            } catch (e: Exception) {
                Log.e("VoiceStorageManager", "SpeechRecognizer setup failed: ${e.message}", e)
                listener.onError("Voice transcription initial notice: ${e.localizedMessage}")
            }
        }

        return outputFile.name
    }

    /**
     * Stops audio recording and speech recognition, returning the relative internal file path.
     */
    fun stopRecording(): String {
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
