package com.voicebutler.whatsapp

import android.app.Notification
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log

object VoiceReplyDispatcher {

    private const val TAG = "VoiceReplyDispatcher"

    fun findQuickReplyAction(notification: Notification): Notification.Action? {
        // 1. Check direct actions
        val directActions = notification.actions
        if (directActions != null) {
            for (action in directActions) {
                val remoteInputs = action.remoteInputs ?: continue
                for (remoteInput in remoteInputs) {
                    if (remoteInput.allowFreeFormInput) {
                        Log.i(TAG, "Found WhatsApp quick reply action in direct actions: ${action.title}")
                        return action
                    }
                }
            }
        }

        // 2. Check WearableExtender nested actions (common on Android 11+ / ColorOS)
        try {
            val wearableExtender = Notification.WearableExtender(notification)
            for (action in wearableExtender.actions) {
                val remoteInputs = action.remoteInputs ?: continue
                for (remoteInput in remoteInputs) {
                    if (remoteInput.allowFreeFormInput) {
                        Log.i(TAG, "Found WhatsApp quick reply action in WearableExtender: ${action.title}")
                        return action
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking wearable actions: ${e.message}")
        }

        return null
    }

    fun dispatchReply(context: Context, action: Notification.Action, text: String): Boolean {
        val remoteInputs = action.remoteInputs ?: return false
        val intent = Intent()
        val bundle = Bundle()

        for (input in remoteInputs) {
            bundle.putCharSequence(input.resultKey, text)
        }

        RemoteInput.addResultsToIntent(remoteInputs, intent, bundle)

        return try {
            action.actionIntent.send(context, 0, intent)
            Log.i(TAG, "Quick reply successfully sent to WhatsApp: '$text'")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send quick reply: ${e.message}", e)
            false
        }
    }
}
