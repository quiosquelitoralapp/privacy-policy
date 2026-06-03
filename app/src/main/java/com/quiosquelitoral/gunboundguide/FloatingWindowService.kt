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

    // ── WindowManager ──────────────────────────────────────────────
    private var windowManager: WindowManager? = null

    // Overlay de controles (pequeno, arrastável)
    private var controlView: View? = null
    private var controlParams: WindowManager.LayoutParams? = null
    private var ctrlInitialX = 0; private var ctrlInitialY = 0
    private var ctrlTouchX = 0f; private var ctrlTouchY = 0f

    // Overlay full-screen transparente (trajetória)
    private var gameOverlayView: GameOverlayView? = null
    private var gameOverlayParams: WindowManager.LayoutParams? = null

    // ── MediaProjection / VirtualDisplay ───────────────────────────
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var screenW = 1080; private var screenH = 1920; private var screenDpi = 480

    // ── Estado do jogo ─────────────────────────────────────────────
    private var angle    = 45
    private var power    = 50
    private var windH    = 0; private var windHDir = 1
    private var windV    = 0; private var windVDir = 1
    private var facingRight = true
    private var autoAngle   = false   // true quando AimDetector encontrou ângulo

    // ── Views do painel de controle ────────────────────────────────
    private var tvAngle: TextView? = null
    private var tvPower: TextView? = null
    private var tvWindH: TextView? = null
    private var tvWindV: TextView? = null

    // ── Loop de scan ───────────────────────────────────────────────
    private val mainHandler  = Handler(Looper.getMainLooper())
    private val bgExecutor   = Executors.newSingleThreadExecutor()
    private var scanRunnable: Runnable? = null
    private var scanActive = false

    companion object {
        const val ACTION_STOP = "STOP_OVERLAY"
        const val NOTIF_ID = 1
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

    // ── Criação dos dois overlays ──────────────────────────────────

    private fun createOverlays() {
        try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager = wm

            @Suppress("DEPRECATION")
            val type = if (Build.VERSION.SDK_INT >= 26) TYPE_OVERLAY_VALUE
                       else WindowManager.LayoutParams.TYPE_PHONE

            // 1) Overlay full-screen transparente (jogo)
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
            gameOverlayParams = gp
            wm.addView(gov, gp)

            // 2) Painel de controle (260dp, arrastável)
            val ctrlView = LayoutInflater.from(this).inflate(R.layout.floating_overlay, null)
            controlView = ctrlView
            val cp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            )
            cp.gravity = Gravity.TOP or Gravity.START
            cp.x = 16; cp.y = 100
            controlParams = cp
            setupControlDrag(ctrlView, wm, cp)
            setupControls(ctrlView)
            wm.addView(ctrlView, cp)

            toast("Overlay ativo! Abra o Gunbound.")
        } catch (e: Throwable) {
            toast("Erro overlay: ${e.javaClass.simpleName}: ${e.message?.take(60)}")
            isRunning = false
            stopSelf()
        }
    }

    // ── Drag do painel de controle ─────────────────────────────────

    private fun setupControlDrag(v: View, wm: WindowManager, p: WindowManager.LayoutParams) {
        v.findViewById(R.id.floatingHeader).setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    ctrlInitialX = p.x; ctrlInitialY = p.y
                    ctrlTouchX = event.rawX; ctrlTouchY = event.rawY; true
                }
                MotionEvent.ACTION_MOVE -> {
                    p.x = ctrlInitialX + (event.rawX - ctrlTouchX).toInt()
                    p.y = ctrlInitialY + (event.rawY - ctrlTouchY).toInt()
                    try { wm.updateViewLayout(v, p) } catch (e: Throwable) {}; true
                }
                else -> false
            }
        }
        v.findViewById(R.id.btnCloseOverlay).setOnClickListener { stopSelf() }

        var collapsed = false
        val collapseBtn = v.findViewById(R.id.btnCollapseOverlay) as Button
        val content     = v.findViewById(R.id.overlayContent) as ViewGroup
        collapseBtn.setOnClickListener {
            collapsed = !collapsed
            content.visibility = if (collapsed) View.GONE else View.VISIBLE
            collapseBtn.text   = if (collapsed) "▼" else "▲"
        }
    }

    // ── Controles do painel ────────────────────────────────────────

    private fun setupControls(v: View) {
        tvAngle = v.findViewById(R.id.overlayAngleValue)    as TextView
        tvPower = v.findViewById(R.id.overlayPowerValue)    as TextView
        tvWindH = v.findViewById(R.id.overlayWindValue)     as TextView
        tvWindV = v.findViewById(R.id.overlayWindVertValue) as TextView

        val tv  = gameOverlayView!!
        val db  = v.findViewById(R.id.overlayBtnDirection) as Button
        val mc  = v.findViewById(R.id.overlayMobileContainer) as LinearLayout

        // Mobile buttons
        val btns = mutableListOf<Button>()
        MobileData.mobiles.forEachIndexed { i, mobile ->
            val btn = Button(this).apply {
                text = mobile.displayName; textSize = 9f; setAllCaps(false)
                setPadding(8, 2, 8, 2)
                setBackgroundColor(Color.parseColor("#1E3A5A"))
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.setMargins(2, 2, 2, 2) }
            }
            btn.setOnClickListener {
                btns.forEachIndexed { j, b ->
                    if (j == i) { b.setBackgroundColor(Color.parseColor("#00FF88")); b.setTextColor(Color.BLACK) }
                    else        { b.setBackgroundColor(Color.parseColor("#1E3A5A")); b.setTextColor(Color.WHITE) }
                }
                tv.mobile = MobileData.mobiles[i]
                tv.invalidate()
            }
            btns.add(btn); mc.addView(btn)
        }
        btns[0].setBackgroundColor(Color.parseColor("#00FF88"))
        btns[0].setTextColor(Color.BLACK)

        fun pushUpdate() {
            tv.angle = angle.toFloat(); tv.power = power.toFloat()
            tv.windH = (windH * windHDir).toFloat()
            tv.windV = (windV * windVDir).toFloat()
            tv.facingRight = facingRight
            tv.invalidate()
            tvAngle?.text = "$angle°"
            tvPower?.text = "$power"
            tvWindH?.text = "${if (windHDir > 0) "→" else "←"}$windH"
            tvWindV?.text = "${if (windVDir > 0) "↓" else "↑"}$windV"
        }
        pushUpdate()

        v.findViewById(R.id.btnAngleMinus5).setOnClickListener { angle = (angle-5).coerceAtLeast(0); pushUpdate() }
        v.findViewById(R.id.btnAngleMinus1).setOnClickListener { angle = (angle-1).coerceAtLeast(0); pushUpdate() }
        v.findViewById(R.id.btnAnglePlus1) .setOnClickListener { angle = (angle+1).coerceAtMost(90); pushUpdate() }
        v.findViewById(R.id.btnAnglePlus5) .setOnClickListener { angle = (angle+5).coerceAtMost(90); pushUpdate() }

        v.findViewById(R.id.btnPowerMinus5).setOnClickListener { power = (power-5).coerceAtLeast(0);   pushUpdate() }
        v.findViewById(R.id.btnPowerMinus1).setOnClickListener { power = (power-1).coerceAtLeast(0);   pushUpdate() }
        v.findViewById(R.id.btnPowerPlus1) .setOnClickListener { power = (power+1).coerceAtMost(100);  pushUpdate() }
        v.findViewById(R.id.btnPowerPlus5) .setOnClickListener { power = (power+5).coerceAtMost(100);  pushUpdate() }

        v.findViewById(R.id.btnWindLeft) .setOnClickListener { windHDir = -1; pushUpdate() }
        v.findViewById(R.id.btnWindRight).setOnClickListener { windHDir =  1; pushUpdate() }
        v.findViewById(R.id.btnWindMinus).setOnClickListener { windH = (windH-1).coerceAtLeast(0); pushUpdate() }
        v.findViewById(R.id.btnWindPlus) .setOnClickListener { windH = (windH+1).coerceAtMost(10); pushUpdate() }

        v.findViewById(R.id.btnWindUp)       .setOnClickListener { windVDir = -1; pushUpdate() }
        v.findViewById(R.id.btnWindDown)     .setOnClickListener { windVDir =  1; pushUpdate() }
        v.findViewById(R.id.btnWindVertMinus).setOnClickListener { windV = (windV-1).coerceAtLeast(0); pushUpdate() }
        v.findViewById(R.id.btnWindVertPlus) .setOnClickListener { windV = (windV+1).coerceAtMost(10); pushUpdate() }

        var right = true
        db.text = "-> DIREITA"
        db.setOnClickListener {
            right = !right; facingRight = right
            db.text = if (right) "-> DIREITA" else "<- ESQUERDA"
            pushUpdate()
        }

        // Botão 📷: scan manual imediato
        v.findViewById(R.id.btnScanWind).setOnClickListener { doScan() }
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

            gameOverlayView?.screenW = screenW.toFloat()

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
            val image = imageReader?.acquireLatestImage() ?: return null
            val plane = image.planes[0]
            val bmp = Bitmap.createBitmap(
                plane.rowStride / plane.pixelStride, screenH, Bitmap.Config.ARGB_8888
            )
            bmp.copyPixelsFromBuffer(plane.buffer)
            image.close()
            bmp
        } catch (e: Throwable) { null }
    }

    // ── Loop de scan automático (a cada 900ms) ─────────────────────

    private fun startScanLoop() {
        scanActive = true
        val run = object : Runnable {
            override fun run() {
                if (scanActive) {
                    doScan()
                    mainHandler.postDelayed(this, 900)
                }
            }
        }
        scanRunnable = run
        mainHandler.postDelayed(run, 800)
    }

    private fun stopScanLoop() {
        scanActive = false
        scanRunnable?.let { mainHandler.removeCallbacks(it) }
    }

    private fun doScan() {
        // Captura no main thread (necessário para VirtualDisplay)
        val bmp = captureFrame() ?: return
        bgExecutor.execute { processScan(bmp) }
    }

    /**
     * Roda em background thread. Detecta: vento, personagem, ângulo.
     * Atualiza o GameOverlayView no main thread.
     */
    private fun processScan(bmp: Bitmap) {
        // 1. Vento — faixa superior central
        val cx = screenW / 4
        val cw = screenW / 2
        val ch = screenH / 8
        val windCrop = try {
            Bitmap.createBitmap(bmp, cx, 0, cw, ch)
        } catch (e: Throwable) { null }

        val windResult = windCrop?.let { WindDetector.detect(it) }

        // 2. Personagem pela etiqueta de nome
        val charPos = CharacterFinder.find(bmp)

        // 3. Ângulo de mira (varredura radial a partir do personagem)
        val detectedAngle = if (charPos != null)
            AimDetector.detect(bmp, charPos.x, charPos.groundY, facingRight)
        else null

        // 4. Atualiza UI no main thread
        mainHandler.post {
            var changed = false

            if (windResult != null && windResult.confidence >= 0.45f) {
                windH    = windResult.magnitude
                windHDir = if (windResult.windH >= 0f) 1 else -1
                windV    = kotlin.math.abs(windResult.windV).toInt()
                windVDir = if (windResult.windV >= 0f) 1 else -1
                tvWindH?.text = "${if (windHDir > 0) "→" else "←"}$windH"
                tvWindV?.text = "${if (windVDir > 0) "↓" else "↑"}$windV"
                changed = true
            }

            if (charPos != null) {
                val gov = gameOverlayView ?: return@post
                gov.charX   = charPos.x.toFloat()
                gov.charY   = charPos.groundY.toFloat()
                gov.groundY = charPos.groundY.toFloat()
                gov.active  = true
                changed = true
            }

            if (detectedAngle != null) {
                angle = detectedAngle.toInt()
                autoAngle = true
                tvAngle?.text = "~$angle°"
                changed = true
            }

            if (changed) {
                val gov = gameOverlayView ?: return@post
                gov.windH = (windH * windHDir).toFloat()
                gov.windV = (windV * windVDir).toFloat()
                gov.angle = angle.toFloat()
                gov.power = power.toFloat()
                gov.facingRight = facingRight
                gov.invalidate()
            }
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
                    // Pequeno delay para VirtualDisplay inicializar
                    mainHandler.postDelayed({
                        initCapture()
                        startScanLoop()
                    }, 500)
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
        controlView?.let    { try { wm?.removeView(it) } catch (e: Throwable) {} }
    }

    // ── Notificação ────────────────────────────────────────────────

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
            .setContentText("Overlay ativo – detectando vento e mira")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Fechar", pi)
        if (Build.VERSION.SDK_INT >= 26) {
            try { b.javaClass.getMethod("setChannelId", String::class.java).invoke(b, CHANNEL_ID) }
            catch (e: Throwable) {}
        }
        return b.build()
    }
}
