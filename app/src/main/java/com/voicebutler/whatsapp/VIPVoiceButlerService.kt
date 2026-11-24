package com.voicebutler.whatsapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.Locale
import java.util.UUID

class VIPVoiceButlerService : NotificationListenerService(), TextToSpeech.OnInitListener {

    private lateinit var settings: ButlerSettings
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var activeReplyAction: Notification.Action? = null
    private var activeSenderName: String = ""
    private var audioFocusRequest: AudioFocusRequest? = null

    private var isButlerPaused = false
    private val notificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }

    private val controlReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_STOP_BUTLER -> pauseButler()
                ACTION_RESUME_BUTLER -> resumeButler()
            }
        }
    }

    companion object {
        private const val TAG = "VIPVoiceButler"
        private const val TTS_UTTERANCE_ID = "vip_voice_butler_utterance"
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "vip_voice_butler_channel"

        const val ACTION_STOP_BUTLER = "com.voicebutler.whatsapp.ACTION_STOP_BUTLER"
        const val ACTION_RESUME_BUTLER = "com.voicebutler.whatsapp.ACTION_RESUME_BUTLER"

        var isServiceConnected = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        settings = ButlerSettings(this)
        tts = TextToSpeech(this, this)
        initSpeechRecognizerOnMainThread()

        val filter = IntentFilter().apply {
            addAction(ACTION_STOP_BUTLER)
            addAction(ACTION_RESUME_BUTLER)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(controlReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(controlReceiver, filter)
        }

        createNotificationChannel()
        if (settings.isButlerActive) {
            showStatusNotification()
        }
        Log.d(TAG, "VIPVoiceButlerService created")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isServiceConnected = true
        if (!settings.isButlerActive) {
            isButlerPaused = true
            Log.d(TAG, "Notification Listener connected but Butler is OFF in settings. Staying dormant.")
            return
        }
        isButlerPaused = false
        showStatusNotification()
        Log.d(TAG, "Notification Listener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isServiceConnected = false
        notificationManager.cancel(NOTIFICATION_ID)
        Log.d(TAG, "Notification Listener disconnected")
    }

    private fun pauseButler() {
        settings.isButlerActive = false
        isButlerPaused = true
        tts?.stop()
        speechRecognizer?.cancel()
        abandonAudioFocus()
        showStatusNotification()
        Toast.makeText(this, "VIP Voice Butler Stopped", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "Butler paused via notification stop button")
    }

    private fun resumeButler() {
        settings.isButlerActive = true
        isButlerPaused = false
        showStatusNotification()
        Toast.makeText(this, "VIP Voice Butler Resumed", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "Butler resumed via notification resume button")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Voice Butler Status & Controls",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active status with quick Stop and Resume controls"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showStatusNotification() {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            this, 0, openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openPending)
            .setOngoing(!isButlerPaused)

        if (isButlerPaused) {
            val resumeIntent = Intent(ACTION_RESUME_BUTLER).apply { setPackage(packageName) }
            val resumePending = PendingIntent.getBroadcast(
                this, 2, resumeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setContentTitle("VIP Voice Butler: Stopped")
                .setContentText("All functions halted. Tap Resume to activate.")
                .addAction(android.R.drawable.ic_media_play, "▶ RESUME", resumePending)
        } else {
            val stopIntent = Intent(ACTION_STOP_BUTLER).apply { setPackage(packageName) }
            val stopPending = PendingIntent.getBroadcast(
                this, 1, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setContentTitle("VIP Voice Butler: Active")
                .setContentText("Monitoring VIP messages. Tap Stop to shut off.")
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "🛑 STOP BUTLER", stopPending)
        }

        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.getDefault()
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d(TAG, "TTS started speaking")
                }

                override fun onDone(utteranceId: String?) {
                    abandonAudioFocus()
                    if (settings.isVoiceReplyEnabled && activeReplyAction != null && !isButlerPaused) {
                        mainHandler.post { startVoiceReplyCapture() }
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    abandonAudioFocus()
                }
            })
            isTtsReady = true
            Log.d(TAG, "TTS Engine initialized successfully")
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || isButlerPaused || !settings.isButlerActive) return

        val pkg = sbn.packageName ?: return
        if (pkg != "com.whatsapp" && pkg != "com.whatsapp.w4b") return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        // Extract sender and message body
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        if (title.isBlank() || text.isBlank()) return

        // Check headphone restriction if enabled
        if (settings.isHeadphonesOnly && !isHeadsetConnected()) {
            Log.d(TAG, "Skipping readout: headphones-only mode is active and no headset detected")
            return
        }

        // Check VIP whitelist or read-all setting
        val isVip = isSenderVip(title)
        if (!settings.isReadAllContacts && !isVip) {
            Log.d(TAG, "Skipping message from '$title' (not in VIP list)")
            return
        }

        activeReplyAction = VoiceReplyDispatcher.findQuickReplyAction(notification)
        activeSenderName = title

        val spokenText = if (settings.isVoiceReplyEnabled && activeReplyAction != null) {
            "Message from $title: $text. Please say your reply now."
        } else {
            "Message from $title: $text."
        }

        settings.addLog(
            ButlerLogItem(
                id = UUID.randomUUID().toString(),
                sender = title,
                messageText = text,
                replyText = null
            )
        )

        speakMessage(spokenText)
    }

    private fun isSenderVip(title: String): Boolean {
        val vips = settings.getVipContacts()
        val normalizedTitleDigits = title.filter { it.isDigit() }
        val titleLast10 = if (normalizedTitleDigits.length >= 10) normalizedTitleDigits.takeLast(10) else normalizedTitleDigits

        for (vip in vips) {
            // 1. Direct name / substring match
            if (title.contains(vip, ignoreCase = true) || vip.contains(title, ignoreCase = true)) {
                Log.i(TAG, "VIP matched by contact name: '$title' matches '$vip'")
                return true
            }

            // 2. Normalized 10-digit phone number match (handles +91, spaces, hyphens)
            val normalizedVipDigits = vip.filter { it.isDigit() }
            if (normalizedVipDigits.isNotEmpty()) {
                val vipLast10 = if (normalizedVipDigits.length >= 10) normalizedVipDigits.takeLast(10) else normalizedVipDigits
                if (titleLast10.isNotEmpty() && (titleLast10 == vipLast10 || normalizedTitleDigits.endsWith(vipLast10) || normalizedVipDigits.endsWith(titleLast10))) {
                    Log.i(TAG, "VIP matched by normalized phone: '$title' ($titleLast10) matches '$vip' ($vipLast10)")
                    return true
                }
            }
        }
        return false
    }

    private fun speakMessage(text: String) {
        if (!isTtsReady || tts == null || isButlerPaused) return

        requestAudioFocus()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, TTS_UTTERANCE_ID)
        Log.i(TAG, "Speaking: $text")
    }

    private fun initSpeechRecognizerOnMainThread() {
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
                if (SpeechRecognizer.isRecognitionAvailable(this)) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                        setRecognitionListener(object : RecognitionListener {
                            override fun onReadyForSpeech(params: Bundle?) {
                                Log.i(TAG, "Listening for voice reply...")
                                Toast.makeText(applicationContext, "Listening for voice reply...", Toast.LENGTH_SHORT).show()
                            }

                            override fun onResults(results: Bundle?) {
                                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                if (!matches.isNullOrEmpty()) {
                                    val reply = matches[0]
                                    handleSpokenReply(reply)
                                }
                            }

                            override fun onError(error: Int) {
                                Log.w(TAG, "Speech recognition error: $error. Re-initializing engine.")
                                mainHandler.postDelayed({ initSpeechRecognizerOnMainThread() }, 800L)
                            }

                            override fun onBeginningOfSpeech() {}
                            override fun onRmsChanged(rmsdB: Float) {}
                            override fun onBufferReceived(buffer: ByteArray?) {}
                            override fun onEndOfSpeech() {}
                            override fun onPartialResults(partialResults: Bundle?) {}
                            override fun onEvent(eventType: Int, params: Bundle?) {}
                        })
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing SpeechRecognizer: ${e.message}")
            }
        }
    }

    private fun startVoiceReplyCapture() {
        if (isButlerPaused) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say reply for $activeSenderName")
        }
        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting voice capture: ${e.message}")
            initSpeechRecognizerOnMainThread()
        }
    }

    private fun handleSpokenReply(spokenText: String) {
        if (isButlerPaused) return
        val cleanReply = spokenText.removePrefix("reply ").removePrefix("Reply ").trim()
        val action = activeReplyAction

        if (action != null && cleanReply.isNotBlank()) {
            val sent = VoiceReplyDispatcher.dispatchReply(this, action, cleanReply)
            if (sent) {
                tts?.speak("Reply sent to $activeSenderName", TextToSpeech.QUEUE_ADD, null, null)
                Toast.makeText(this, "Reply dispatched: '$cleanReply'", Toast.LENGTH_LONG).show()
            }
        }
        activeReplyAction = null
    }

    private fun isHeadsetConnected(): Boolean {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return audioManager.isWiredHeadsetOn || audioManager.isBluetoothA2dpOn
    }

    private fun requestAudioFocus() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAcceptsDelayedFocusGain(true)
                .build()
            audioFocusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        }
    }

    private fun abandonAudioFocus() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(controlReceiver)
        } catch (_: Exception) {}
        notificationManager.cancel(NOTIFICATION_ID)
        tts?.stop()
        tts?.shutdown()
        speechRecognizer?.destroy()
    }
}
