package com.orbix.pixora.launcher.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log

/**
 * Tracks audio session IDs broadcast by music players.
 * Music apps (Spotify, YouTube, local players) broadcast
 * ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION when they start playing.
 */
object AudioSessionTracker {

    private const val TAG = "PixoraAudioTracker"

    // Active audio session IDs from music players
    private val activeSessions = mutableSetOf<Int>()
    private var receiver: BroadcastReceiver? = null

    val sessions: Set<Int> get() = activeSessions.toSet()

    fun register(context: Context) {
        if (receiver != null) return

        receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val sessionId = intent?.getIntExtra("android.media.extra.AUDIO_SESSION", -1) ?: -1
                val pkg = intent?.getStringExtra("android.media.extra.PACKAGE_NAME") ?: "unknown"

                when (intent?.action) {
                    "android.media.action.OPEN_AUDIO_EFFECT_CONTROL_SESSION" -> {
                        if (sessionId > 0) {
                            activeSessions.add(sessionId)
                            Log.d(TAG, "Session OPENED: id=$sessionId pkg=$pkg (total=${activeSessions.size})")
                        }
                    }
                    "android.media.action.CLOSE_AUDIO_EFFECT_CONTROL_SESSION" -> {
                        activeSessions.remove(sessionId)
                        Log.d(TAG, "Session CLOSED: id=$sessionId pkg=$pkg (total=${activeSessions.size})")
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction("android.media.action.OPEN_AUDIO_EFFECT_CONTROL_SESSION")
            addAction("android.media.action.CLOSE_AUDIO_EFFECT_CONTROL_SESSION")
        }
        context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        Log.d(TAG, "AudioSessionTracker registered")
    }

    fun unregister(context: Context) {
        receiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (_: Exception) { }
            receiver = null
        }
        activeSessions.clear()
    }
}
