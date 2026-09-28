package com.example.core.utils

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.core.settings.SettingsManager
import java.util.*

class VoiceManager(
    private val context: Context,
    private val settings: SettingsManager,
    private val onSpeechRecognized: (String) -> Unit,
    private val onSpeechStateChanged: (VoiceState) -> Unit
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var isTtsReady = false
    private var isListening = false
    private var lastUtteranceId: String? = null

    enum class VoiceState {
        IDLE,
        LISTENING,
        THINKING,
        SPEAKING,
        ERROR
    }

    init {
        initializeTts()
        initializeSpeechRecognizer()
    }

    private fun initializeTts() {
        try {
            tts = TextToSpeech(context, this)
        } catch (e: Exception) {
            Log.e("VoiceManager", "Failed to init TTS: ${e.message}")
        }
    }

    private fun initializeSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        isListening = true
                        onSpeechStateChanged(VoiceState.LISTENING)
                    }

                    override fun onBeginningOfSpeech() {
                        // User started speaking - support interruptions by stopping active playback instantly!
                        stopSpeaking()
                    }

                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    
                    override fun onEndOfSpeech() {
                        isListening = false
                        onSpeechStateChanged(VoiceState.THINKING)
                    }

                    override fun onError(error: Int) {
                        isListening = false
                        val errorMessage = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                            SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permission missing"
                            SpeechRecognizer.ERROR_NETWORK -> "Network error"
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                            SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                            SpeechRecognizer.ERROR_SERVER -> "Server error"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                            else -> "Unknown recognizer error"
                        }
                        Log.w("VoiceManager", "SpeechRecognizer error: $errorMessage ($error)")
                        onSpeechStateChanged(VoiceState.IDLE)
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: ""
                        if (text.isNotBlank()) {
                            onSpeechRecognized(text)
                        } else {
                            onSpeechStateChanged(VoiceState.IDLE)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: ""
                        if (text.isNotBlank()) {
                            // Instant interruption check
                            stopSpeaking()
                        }
                    }
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        } else {
            Log.w("VoiceManager", "Speech recognition not available on this device.")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsReady = true
            configureTtsParameters()
            
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    onSpeechStateChanged(VoiceState.SPEAKING)
                }

                override fun onDone(utteranceId: String?) {
                    onSpeechStateChanged(VoiceState.IDLE)
                    // If continuous listening mode is on, resume listening automatically after TTS finishes speaking!
                    if (settings.isAutoSpeakEnabled) {
                        startListening()
                    }
                }

                override fun onError(utteranceId: String?) {
                    onSpeechStateChanged(VoiceState.IDLE)
                }
            })
        } else {
            Log.e("VoiceManager", "TTS initialization failed.")
        }
    }

    fun configureTtsParameters() {
        if (!isTtsReady) return
        val currentTts = tts ?: return

        // Set Language based on settings
        val selectedLanguage = when (settings.voiceLanguage) {
            "Hindi" -> Locale("hi", "IN")
            "Hinglish" -> Locale("hi", "IN") // Hinglish utilizes Hindi pronunciation engine for natural dialect
            else -> Locale.ENGLISH
        }
        
        try {
            currentTts.language = selectedLanguage
        } catch (e: Exception) {
            currentTts.language = Locale.getDefault()
        }

        // Apply Speed & Pitch parameters from Settings
        currentTts.setSpeechRate(settings.speechSpeed)
        currentTts.setPitch(settings.speechPitch)

        // Select Female voice if enabled (Iterate through system voices to find a natural female model)
        if (settings.isFemaleVoice) {
            try {
                val voices = currentTts.voices
                if (voices != null) {
                    val femaleVoice = voices.find { voice ->
                        voice.name.contains("female", ignoreCase = true) ||
                        voice.name.contains("f-network", ignoreCase = true) ||
                        voice.name.contains("en-us-x-sfg", ignoreCase = true)
                    }
                    if (femaleVoice != null) {
                        currentTts.voice = femaleVoice
                    }
                }
            } catch (e: Exception) {
                Log.w("VoiceManager", "Error selecting custom female voice model: ${e.message}")
            }
        }
    }

    fun speak(text: String) {
        if (!isTtsReady) {
            Log.e("VoiceManager", "TTS is not ready yet.")
            return
        }
        // Stop current speaking before starting a new one
        stopSpeaking()
        stopListening()
        
        onSpeechStateChanged(VoiceState.SPEAKING)
        configureTtsParameters()

        val cleanText = text.replace(Regex("[*#`_]"), "") // Strip out markdown formatting for TTS reading
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, settings.voiceVolume)
        }
        
        lastUtteranceId = UUID.randomUUID().toString()
        tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, params, lastUtteranceId)
    }

    fun startListening() {
        if (speechRecognizer == null) {
            Log.w("VoiceManager", "Speech recognizer is null.")
            return
        }
        stopSpeaking()
        stopListening()

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            
            // Set language dynamically
            val localeCode = when (settings.voiceLanguage) {
                "Hindi" -> "hi-IN"
                "Hinglish" -> "hi-IN"
                else -> "en-US"
            }
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeCode)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, localeCode)
            putExtra(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES, arrayListOf("hi-IN", "en-US"))
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500)
        }

        try {
            speechRecognizer?.startListening(intent)
            isListening = true
            onSpeechStateChanged(VoiceState.LISTENING)
        } catch (e: Exception) {
            Log.e("VoiceManager", "Failed to start listening: ${e.message}")
            onSpeechStateChanged(VoiceState.IDLE)
        }
    }

    fun stopListening() {
        if (isListening) {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.e("VoiceManager", "Error stopping listening: ${e.message}")
            }
            isListening = false
        }
    }

    fun stopSpeaking() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.e("VoiceManager", "Error stopping TTS: ${e.message}")
        }
    }

    fun destroy() {
        try {
            stopListening()
            speechRecognizer?.destroy()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.e("VoiceManager", "Error destroying VoiceManager: ${e.message}")
        }
    }
}
