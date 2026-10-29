package com.fzn.slotalarm

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var etServer: EditText
    private lateinit var etToken: EditText
    private lateinit var tvStatus: TextView
    private lateinit var tvMp3: TextView
    private lateinit var btnToggle: Button
    private lateinit var btnPickMp3: Button

    private val pickMp3 = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            // persist permission biar bisa dibaca lagi nanti
            contentResolver.takePersistableUriPermission(
                it, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            prefs.edit().putString("alarm_mp3", it.toString()).apply()
            val name = it.lastPathSegment ?: "custom"
            tvMp3.text = "Alarm: $name"
            Toast.makeText(this, "MP3 disimpan", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("slotalarm", MODE_PRIVATE)

        etServer = findViewById(R.id.etServer)
        etToken = findViewById(R.id.etToken)
        tvStatus = findViewById(R.id.tvStatus)
        tvMp3 = findViewById(R.id.tvMp3)
        btnToggle = findViewById(R.id.btnToggle)
        btnPickMp3 = findViewById(R.id.btnPickMp3)

        // load config tersimpan
        etServer.setText(prefs.getString("server", "ws://43.156.47.36/alarm"))
        etToken.setText(prefs.getString("token", ""))
        val savedMp3 = prefs.getString("alarm_mp3", null)
        tvMp3.text = if (savedMp3.isNullOrEmpty()) "Alarm: default sistem"
                      else "Alarm: ${Uri.parse(savedMp3).lastPathSegment}"

        requestNotifPermission()
        updateUI()

        btnPickMp3.setOnClickListener {
            pickMp3.launch("audio/*")
        }

        btnToggle.setOnClickListener {
            val server = etServer.text.toString().trim()
            val token = etToken.text.toString().trim()

            if (server.isEmpty() || token.isEmpty()) {
                Toast.makeText(this, "Server dan token harus diisi", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            prefs.edit()
                .putString("server", server)
                .putString("token", token)
                .apply()

            if (AlarmService.isRunning) {
                stopAlarmService()
            } else {
                startAlarmService()
            }
            updateUI()
        }
    }

    private fun startAlarmService() {
        val intent = Intent(this, AlarmService::class.java).apply {
            action = AlarmService.ACTION_START
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopAlarmService() {
        val intent = Intent(this, AlarmService::class.java).apply {
            action = AlarmService.ACTION_STOP
        }
        startService(intent)
    }

    private fun updateUI() {
        if (AlarmService.isRunning) {
            tvStatus.text = "● TERHUBUNG — alarm siap"
            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            btnToggle.text = "MATIKAN"
        } else {
            tvStatus.text = "○ MATI — tekan buat nyalakan"
            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            btnToggle.text = "NYALAKAN"
        }
    }

    private fun requestNotifPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }
}
