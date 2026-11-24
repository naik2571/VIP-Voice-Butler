package com.voicebutler.whatsapp

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class ButlerLogItem(
    val id: String,
    val sender: String,
    val messageText: String,
    val replyText: String?,
    val timestamp: Long = System.currentTimeMillis()
)

class ButlerSettings(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )
    private val gson = Gson()

    companion object {
        private const val PREFS_NAME = "whatsapp_voice_butler_prefs"
        private const val KEY_VIP_CONTACTS = "vip_contacts_set"
        private const val KEY_READ_ALL = "read_all_contacts"
        private const val KEY_HEADPHONES_ONLY = "headphones_only"
        private const val KEY_VOICE_REPLY = "voice_reply_enabled"
        private const val KEY_LOGS = "recent_readout_logs"
        private const val KEY_BUTLER_ACTIVE = "is_butler_active"
    }

    var isButlerActive: Boolean
        get() = prefs.getBoolean(KEY_BUTLER_ACTIVE, false)
        set(value) = prefs.edit().putBoolean(KEY_BUTLER_ACTIVE, value).apply()

    @Synchronized
    fun getVipContacts(): Set<String> {
        return prefs.getStringSet(KEY_VIP_CONTACTS, emptySet()) ?: emptySet()
    }

    @Synchronized
    fun addVipContact(name: String) {
        val current = getVipContacts().toMutableSet()
        current.add(name.trim())
        prefs.edit().putStringSet(KEY_VIP_CONTACTS, current).apply()
    }

    @Synchronized
    fun removeVipContact(name: String) {
        val current = getVipContacts().toMutableSet()
        current.remove(name.trim())
        prefs.edit().putStringSet(KEY_VIP_CONTACTS, current).apply()
    }

    var isReadAllContacts: Boolean
        get() = prefs.getBoolean(KEY_READ_ALL, false)
        set(value) = prefs.edit().putBoolean(KEY_READ_ALL, value).apply()

    var isHeadphonesOnly: Boolean
        get() = prefs.getBoolean(KEY_HEADPHONES_ONLY, false)
        set(value) = prefs.edit().putBoolean(KEY_HEADPHONES_ONLY, value).apply()

    var isVoiceReplyEnabled: Boolean
        get() = prefs.getBoolean(KEY_VOICE_REPLY, true)
        set(value) = prefs.edit().putBoolean(KEY_VOICE_REPLY, value).apply()

    @Synchronized
    fun getRecentLogs(): List<ButlerLogItem> {
        val json = prefs.getString(KEY_LOGS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<ButlerLogItem>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun addLog(log: ButlerLogItem) {
        val current = getRecentLogs().toMutableList()
        current.add(0, log)
        if (current.size > 50) current.removeAt(current.size - 1)
        val json = gson.toJson(current)
        prefs.edit().putString(KEY_LOGS, json).apply()
    }
}
