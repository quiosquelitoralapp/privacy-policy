package com.quiosquelitoral.gunboundguide

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.*
import android.widget.*
import java.util.concurrent.Executors

class FloatingWindowService : Service() {

    private var windowManager: WindowManager? = null

    // Overlay full-screen transparente (trajetórias)
    private var gameOverlayView: GameOverlayView? = null

    // Barra de controle mínima (arrasto + mobile + direção + fechar)
    private var barView: View? = null
    private var barParams: WindowManager.LayoutParams? = null
    private var barIX = 0; private var barIY = 0
    private var barTX = 0f; private var barTY = 0f

    // MediaProjection
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var screenW = 1080; private var screenH = 1920; private var screenDpi = 480

    // Estado detectado automaticamente
    private var windH = 0f
    private var windV = 0f
    private var power = 70f        // valor padrão razoável
    private var facingRight = true
    private var mobileIndex = 0

    // Loop de scan
    private val mainHandler = Handler(Looper.getMainLooper())
    private val bgExecutor  = Executors.newSingleThreadExecutor()
    private var scanRunnable: Runnable? = null
    private var scanActive = false

    // Referência ao botão de mobile para atualizar o texto
    private var btnMobile: Button? = null

    companion object {
        const val ACTION_STOP = "STOP_OVERLAY"
        const val NOTIF_ID   = 1
        const val CHANNEL_ID = "gb_overlay"
        const val TYPE_OVERLAY_VALUE = 2038
        var isRunning = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        try {
            createNotificationChannel()
            startForeground(NOTIF_ID, buildNotification())
        } catch (e: Throwable) {}
        createOverlays()
    }

    private fun createOverlays() {
        try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager = wm

            @Suppress("DEPRECATION")
            val type = if (Build.VERSION.SDK_INT >= 26) TYPE_OVERLAY_VALUE
                       else WindowManager.LayoutParams.TYPE_PHONE

            // 1) Overlay full-screen transparente para desenhar trajetórias
            val gov = GameOverlayView(this)
            gameOverlayView = gov
            val gp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            gp.gravity = Gravity.TOP or Gravity.START
            wm.addView(gov, gp)

            // 2) Barra mínima de controle
            val bar = LayoutInflater.from(this).inflate(R.layout.floating_overlay, null)
            barView = bar
            val bp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            )
            bp.gravity = Gravity.TOP or Gravity.START
            bp.x = 12; bp.y = 60
            barParams = bp
            setupBar(bar, wm, bp)
            wm.addView(bar, bp)

        } catch (e: Throwable) {
            toast("Erro overlay: ${e.javaClass.simpleName}: ${e.message?.take(60)}")
            isRunning = false; stopSelf()
        }
    }

    private fun setupBar(bar: View, wm: WindowManager, bp: WindowManager.LayoutParams) {
        // Drag pela barra inteira (exceto botões)
        bar.findViewById(R.id.floatingHeader).setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    barIX = bp.x; barIY = bp.y; barTX = ev.rawX; barTY = ev.rawY; true
                }
                MotionEvent.ACTION_MOVE -> {
                    bp.x = barIX + (ev.rawX - barTX).toInt()
                    bp.y = barIY + (ev.rawY - barTY).toInt()
                    try { wm.updateViewLayout(bar, bp) } catch (e: Throwable) {}; true
                }
                else -> false
            }
        }

        // Cicla mobiles
        val mob = bar.findViewById(R.id.btnMobileCycle) as Button
        btnMobile = mob
        mob.text = MobileData.mobiles[mobileIndex].displayName
        mob.setOnClickListener {
            mobileIndex = (mobileIndex + 1) % MobileData.mobiles.size
            val m = MobileData.mobiles[mobileIndex]
            mob.text = m.displayName
            gameOverlayView?.mobile = m
            gameOverlayView?.invalidate()
        }

        // Direção
        val faceBtn = bar.findViewById(R.id.btnFacing) as Button
        faceBtn.text = "→"
        faceBtn.setOnClickListener {
            facingRight = !facingRight
            faceBtn.text = if (facingRight) "→" else "←"
            gameOverlayView?.facingRight = facingRight
            gameOverlayView?.invalidate()
        }

        // Fechar
        bar.findViewById(R.id.btnCloseOverlay).setOnClickListener { stopSelf() }
    }

    // ── VirtualDisplay persistente ─────────────────────────────────

    private fun initCapture() {
        val proj = mediaProjection ?: return
        try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val m = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(m)
            screenW = m.widthPixels; screenH = m.heightPixels; screenDpi = m.densityDpi

            // Ativa overlay imediatamente com posição padrão antes de detectar
            gameOverlayView?.let { gov ->
                gov.screenW  = screenW.toFloat()
                gov.charX    = screenW * 0.25f
                gov.charY    = screenH * 0.55f
                gov.groundY  = screenH * 0.58f
                gov.angle    = 45f
                gov.power    = power
                gov.facingRight = facingRight
                gov.mobile   = MobileData.mobiles[mobileIndex]
                gov.active   = true
                gov.invalidate()
            }

            val ir = ImageReader.newInstance(screenW, screenH, PixelFormat.RGBA_8888, 2)
            imageReader = ir
            virtualDisplay = proj.createVirtualDisplay(
                "GBScan", screenW, screenH, screenDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                ir.surface, null, null
            )
        } catch (e: Throwable) {
            toast("Erro captura: ${e.javaClass.simpleName}")
        }
    }

    private fun captureFrame(): Bitmap? {
        return try {
            val img = imageReader?.acquireLatestImage() ?: return null
            val pl  = img.planes[0]
            val bmp = Bitmap.createBitmap(
                pl.rowStride / pl.pixelStride, screenH, Bitmap.Config.ARGB_8888
            )
            bmp.copyPixelsFromBuffer(pl.buffer)
            img.close()
            bmp
        } catch (e: Throwable) { null }
    }

    // ── Loop de scan ───────────────────────────────────────────────

    private fun startScanLoop() {
        scanActive = true
        val run = object : Runnable {
            override fun run() {
                if (!scanActive) return
                val bmp = captureFrame()
                if (bmp != null) bgExecutor.execute { processScan(bmp) }
                mainHandler.postDelayed(this, 900)
            }
        }
        scanRunnable = run
        mainHandler.postDelayed(run, 600)
    }

    private fun stopScanLoop() {
        scanActive = false
        scanRunnable?.let { mainHandler.removeCallbacks(it) }
    }

    private fun processScan(bmp: Bitmap) {
        // 1. Vento — tenta faixa superior central, depois faixa mais ampla
        var windResult: WindDetector.WindResult? = null
        try {
            val cx = screenW / 4; val cw = screenW / 2; val ch = (screenH * 0.15).toInt()
            windResult = WindDetector.detect(Bitmap.createBitmap(bmp, cx, 0, cw, ch))
        } catch (e: Throwable) {}
        if (windResult == null || windResult.confidence < 0.45f) {
            try {
                // Tenta faixa top completa
                val ch = (screenH * 0.20).toInt()
                windResult = WindDetector.detect(Bitmap.createBitmap(bmp, 0, 0, screenW, ch))
            } catch (e: Throwable) {}
        }

        // 2. Personagem — scan em tela inteira (5%-90%)
        val charPos = CharacterFinder.find(bmp)

        // 3. Ângulo de mira a partir do personagem detectado
        val charForAim = charPos
        val detectedAngle = if (charForAim != null)
            AimDetector.detect(bmp, charForAim.x, charForAim.groundY, facingRight)
        else null

        mainHandler.post {
            val gov = gameOverlayView ?: return@post

            // Vento
            if (windResult != null && windResult.confidence >= 0.45f) {
                windH = windResult.windH
                windV = windResult.windV
            }

            // Posição do personagem (atualiza se encontrou, senão mantém padrão)
            if (charPos != null) {
                gov.charX   = charPos.x.toFloat()
                gov.charY   = charPos.groundY.toFloat()
                gov.groundY = charPos.groundY.toFloat()
            }

            // Ângulo (atualiza se detectado)
            if (detectedAngle != null) {
                gov.angle = detectedAngle
            }

            // Sempre redesenha com dados atualizados
            gov.windH       = windH
            gov.windV       = windV
            gov.power       = power
            gov.facingRight = facingRight
            gov.mobile      = MobileData.mobiles[mobileIndex]
            gov.active      = true
            gov.invalidate()
        }
    }

    // ── onStartCommand ─────────────────────────────────────────────

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        try {
            val resultCode = intent?.getIntExtra("proj_result", 0) ?: 0
            @Suppress("DEPRECATION")
            val data = intent?.getParcelableExtra("proj_data") as? Intent
            if (resultCode == Activity.RESULT_OK && data != null) {
                val pm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
                mediaProjection = pm?.getMediaProjection(resultCode, data)
                if (mediaProjection != null) {
                    mainHandler.postDelayed({ initCapture(); startScanLoop() }, 500)
                }
            }
        } catch (e: Throwable) {}
        return START_NOT_STICKY
    }

    // ── onDestroy ──────────────────────────────────────────────────

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        stopScanLoop()
        bgExecutor.shutdownNow()
        try { virtualDisplay?.release() } catch (e: Throwable) {}
        try { imageReader?.close() }     catch (e: Throwable) {}
        try { mediaProjection?.stop() }  catch (e: Throwable) {}
        val wm = windowManager
        gameOverlayView?.let { try { wm?.removeView(it) } catch (e: Throwable) {} }
        barView?.let         { try { wm?.removeView(it) } catch (e: Throwable) {} }
    }

    private fun toast(msg: String) {
        mainHandler.post { Toast.makeText(applicationContext, msg, Toast.LENGTH_LONG).show() }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        try {
            val cls = Class.forName("android.app.NotificationChannel")
            val ch = cls.getConstructor(String::class.java, CharSequence::class.java, Int::class.java)
                .newInstance(CHANNEL_ID, "Gunbound Mira", 2)
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.javaClass.getMethod("createNotificationChannel", cls).invoke(nm, ch)
        } catch (e: Throwable) {}
    }

    @Suppress("DEPRECATION")
    private fun buildNotification(): Notification {
        val stop = Intent(this, FloatingWindowService::class.java).apply { action = ACTION_STOP }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        else PendingIntent.FLAG_UPDATE_CURRENT
        val pi = PendingIntent.getService(this, 0, stop, flags)
        val b = Notification.Builder(this)
            .setContentTitle("Gunbound Guia de Mira")
            .setContentText("Detectando vento e mira automaticamente")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Fechar", pi)
        if (Build.VERSION.SDK_INT >= 26) {
            try { b.javaClass.getMethod("setChannelId", String::class.java).invoke(b, CHANNEL_ID) }
            catch (e: Throwable) {}
        }
        return b.build()
    }
}
