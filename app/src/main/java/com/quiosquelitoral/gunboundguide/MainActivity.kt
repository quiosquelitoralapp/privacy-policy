package com.quiosquelitoral.gunboundguide

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
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

        statusText      = findViewById(R.id.tvStatus)        as TextView
        btnStartOverlay = findViewById(R.id.btnStartOverlay) as Button
        btnPermission   = findViewById(R.id.btnPermission)   as Button

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
            // Pede permissão de captura de tela antes de iniciar
            try {
                val pm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
                if (pm != null) {
                    startActivityForResult(pm.createScreenCaptureIntent(), 101)
                } else {
                    startOverlayService(null, 0)
                }
            } catch (e: Throwable) {
                startOverlayService(null, 0)
            }
        }
    }

    private fun startOverlayService(projData: Intent?, resultCode: Int) {
        val intent = Intent(this, FloatingWindowService::class.java)
        if (projData != null) {
            intent.putExtra("proj_result", resultCode)
            intent.putExtra("proj_data", projData)
        }
        startService(intent)
        Toast.makeText(this, "Iniciando overlay...", Toast.LENGTH_SHORT).show()
        Handler(Looper.getMainLooper()).postDelayed({ updateUI() }, 1000)
    }

    private fun openOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            startActivityForResult(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
                100
            )
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            100 -> updateUI()
            101 -> startOverlayService(data, resultCode)
        }
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
