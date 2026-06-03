package com.quiosquelitoral.gunboundguide

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.*

class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var btnStartOverlay: Button
    private lateinit var btnPermission: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText     = findViewById(R.id.tvStatus)        as TextView
        btnStartOverlay = findViewById(R.id.btnStartOverlay) as Button
        btnPermission  = findViewById(R.id.btnPermission)   as Button

        btnStartOverlay.setOnClickListener { toggleOverlay() }
        btnPermission.setOnClickListener { openOverlayPermission() }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun toggleOverlay() {
        if (!hasOverlayPermission()) {
            Toast.makeText(this, "Ative a permissao 'Exibir sobre outros apps' primeiro!", Toast.LENGTH_LONG).show()
            openOverlayPermission()
            return
        }
        if (FloatingWindowService.isRunning) {
            stopService(Intent(this, FloatingWindowService::class.java))
            Handler(Looper.getMainLooper()).postDelayed({ updateUI() }, 400)
        } else {
            startService(Intent(this, FloatingWindowService::class.java))
            Toast.makeText(this, "Iniciando overlay...", Toast.LENGTH_SHORT).show()
            Handler(Looper.getMainLooper()).postDelayed({ updateUI() }, 1000)
        }
    }

    private fun openOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, 100)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100) updateUI()
    }

    private fun updateUI() {
        val hasPermission = hasOverlayPermission()
        val overlayActive = FloatingWindowService.isRunning

        btnPermission.visibility = if (hasPermission) android.view.View.GONE else android.view.View.VISIBLE
        btnStartOverlay.isEnabled = hasPermission

        when {
            !hasPermission -> {
                statusText.text = "PASSO 1: Toque em\n'CONCEDER PERMISSAO'\ne ative 'Exibir sobre outros apps'"
                btnStartOverlay.text = "INICIAR OVERLAY"
            }
            overlayActive -> {
                statusText.text = "Overlay ATIVO\nVolte ao Gunbound!"
                btnStartOverlay.text = "PARAR OVERLAY"
                btnStartOverlay.setBackgroundColor(android.graphics.Color.parseColor("#FF4444"))
                btnStartOverlay.setTextColor(android.graphics.Color.WHITE)
            }
            else -> {
                statusText.text = "Pronto!\nToque para ativar o overlay."
                btnStartOverlay.text = "INICIAR OVERLAY"
                btnStartOverlay.setBackgroundColor(android.graphics.Color.parseColor("#00FF88"))
                btnStartOverlay.setTextColor(android.graphics.Color.BLACK)
            }
        }
    }
}
