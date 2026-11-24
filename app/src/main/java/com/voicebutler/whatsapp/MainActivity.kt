package com.voicebutler.whatsapp

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.text.TextUtils
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.voicebutler.whatsapp.databinding.ActivityMainBinding
import java.util.Locale
import java.util.UUID

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var settings: ButlerSettings
    private lateinit var logAdapter: ButlerLogAdapter
    private var testTts: TextToSpeech? = null

    private val requestRecordAudioLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Toast.makeText(this, "Microphone access granted for Voice Reply", Toast.LENGTH_SHORT).show()
        }
        updatePermissionButtons()
    }

    private val pickVipContactLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val contactUri = result.data?.data ?: return@registerForActivityResult
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            try {
                contentResolver.query(contactUri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                        val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        val name = cursor.getString(nameIdx) ?: ""
                        val num = cursor.getString(numIdx) ?: ""

                        if (name.isNotEmpty()) {
                            settings.addVipContact(name)
                        }
                        if (num.isNotEmpty()) {
                            val clean = num.replace(Regex("[^0-9+]"), "")
                            if (clean.isNotEmpty()) {
                                settings.addVipContact(clean)
                            }
                        }
                        refreshVipChips()
                        Toast.makeText(this, "Added VIP: $name", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to read contact: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val requestContactsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchContactPicker()
        } else {
            Toast.makeText(this, "Contacts permission required to choose VIP contact", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchContactPicker() {
        val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        pickVipContactLauncher.launch(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settings = ButlerSettings(this)
        testTts = TextToSpeech(this, this)

        setupRecyclerView()
        setupSwitches()
        setupVipInput()
        setupTestButton()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionButtons()
        refreshVipChips()
        refreshLogs()
    }

    private fun setupRecyclerView() {
        logAdapter = ButlerLogAdapter(emptyList())
        binding.rvLogs.layoutManager = LinearLayoutManager(this)
        binding.rvLogs.adapter = logAdapter
    }

    private fun setupSwitches() {
        binding.switchMasterButler.isChecked = settings.isButlerActive
        updateButlerMasterUI(settings.isButlerActive)

        binding.switchMasterButler.setOnCheckedChangeListener { _, isChecked ->
            settings.isButlerActive = isChecked
            updateButlerMasterUI(isChecked)
            if (isChecked) {
                val resumeIntent = Intent(VIPVoiceButlerService.ACTION_RESUME_BUTLER).apply {
                    setPackage(packageName)
                }
                sendBroadcast(resumeIntent)
                Toast.makeText(this, "Voice Butler Activated", Toast.LENGTH_SHORT).show()
            } else {
                val stopIntent = Intent(VIPVoiceButlerService.ACTION_STOP_BUTLER).apply {
                    setPackage(packageName)
                }
                sendBroadcast(stopIntent)
                Toast.makeText(this, "Voice Butler Deactivated (Dormant)", Toast.LENGTH_SHORT).show()
            }
        }

        binding.switchReadAll.isChecked = settings.isReadAllContacts
        binding.switchReadAll.setOnCheckedChangeListener { _, isChecked ->
            settings.isReadAllContacts = isChecked
        }

        binding.switchHeadphonesOnly.isChecked = settings.isHeadphonesOnly
        binding.switchHeadphonesOnly.setOnCheckedChangeListener { _, isChecked ->
            settings.isHeadphonesOnly = isChecked
        }

        binding.switchVoiceReply.isChecked = settings.isVoiceReplyEnabled
        binding.switchVoiceReply.setOnCheckedChangeListener { _, isChecked ->
            settings.isVoiceReplyEnabled = isChecked
        }
    }

    private fun updateButlerMasterUI(active: Boolean) {
        if (active) {
            binding.tvButlerStatus.text = "Status: ACTIVE (Listening)"
            binding.tvButlerStatus.setTextColor(Color.parseColor("#10B981"))
        } else {
            binding.tvButlerStatus.text = "Status: OFF (Dormant)"
            binding.tvButlerStatus.setTextColor(Color.parseColor("#94A3B8"))
        }
    }

    private fun setupVipInput() {
        binding.btnPickVipContact.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                launchContactPicker()
            } else {
                requestContactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            }
        }

        binding.btnAddVip.setOnClickListener {
            val name = binding.etVipName.text?.toString()?.trim() ?: ""
            if (name.isNotEmpty()) {
                settings.addVipContact(name)
                binding.etVipName.text?.clear()
                refreshVipChips()
                Toast.makeText(this, "Added '$name' to VIP list", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun refreshVipChips() {
        binding.chipGroupVips.removeAllViews()
        val vips = settings.getVipContacts()
        for (vip in vips) {
            val chip = Chip(this).apply {
                text = vip
                isCloseIconVisible = true
                setOnCloseIconClickListener {
                    settings.removeVipContact(vip)
                    refreshVipChips()
                }
            }
            binding.chipGroupVips.addView(chip)
        }
    }

    private fun setupTestButton() {
        binding.btnTestSpeech.setOnClickListener {
            val testMsg = "WhatsApp message from Mom: 'Have you had dinner yet?' Please say your reply now."
            testTts?.speak(testMsg, TextToSpeech.QUEUE_FLUSH, null, "test_utterance")
            settings.addLog(
                ButlerLogItem(
                    id = UUID.randomUUID().toString(),
                    sender = "Mom (Test)",
                    messageText = "Have you had dinner yet?",
                    replyText = null
                )
            )
            refreshLogs()
            Toast.makeText(this, "Testing voice readout...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updatePermissionButtons() {
        // 1. Notification Listener
        val notifGranted = isNotificationServiceEnabled(this)
        if (notifGranted) {
            binding.btnNotifPermission.text = "Active ✓"
            binding.btnNotifPermission.setTextColor(Color.parseColor("#10B981"))
            binding.btnNotifPermission.isEnabled = false
        } else {
            binding.btnNotifPermission.text = "Enable"
            binding.btnNotifPermission.setTextColor(Color.parseColor("#F59E0B"))
            binding.btnNotifPermission.isEnabled = true
            binding.btnNotifPermission.setOnClickListener {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                Toast.makeText(this, "Enable 'VIP Voice Butler' in Notification Access", Toast.LENGTH_LONG).show()
            }
        }

        // 2. Microphone
        val micGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (micGranted) {
            binding.btnMicPermission.text = "Granted ✓"
            binding.btnMicPermission.setTextColor(Color.parseColor("#10B981"))
            binding.btnMicPermission.isEnabled = false
        } else {
            binding.btnMicPermission.text = "Allow"
            binding.btnMicPermission.setTextColor(Color.parseColor("#38BDF8"))
            binding.btnMicPermission.isEnabled = true
            binding.btnMicPermission.setOnClickListener {
                requestRecordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun refreshLogs() {
        val logs = settings.getRecentLogs()
        logAdapter.updateLogs(logs)
    }

    private fun isNotificationServiceEnabled(context: Context): Boolean {
        val pkgName = context.packageName
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        if (!TextUtils.isEmpty(flat)) {
            val names = flat.split(":".toRegex()).toTypedArray()
            for (name in names) {
                val cn = ComponentName.unflattenFromString(name)
                if (cn != null && TextUtils.equals(pkgName, cn.packageName)) {
                    return true
                }
            }
        }
        return false
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            testTts?.language = Locale.getDefault()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        testTts?.stop()
        testTts?.shutdown()
    }
}
