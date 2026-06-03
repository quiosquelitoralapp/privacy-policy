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

class FloatingWindowService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var floatingParams: WindowManager.LayoutParams? = null

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private var mediaProjection: MediaProjection? = null

    // Views kept at class level so the scan loop can update them
    private var trajectoryView: TrajectoryView? = null
    private var windHText: TextView? = null
    private var windVText: TextView? = null
    private var windPreviewImg: ImageView? = null

    // Wind state (horizontal)
    private var windH = 0
    private var windHDir = 1   // 1=direita, -1=esquerda

    // Wind state (vertical)
    private var windV = 0
    private var windVDir = 1   // 1=baixo, -1=cima

    private val scanHandler = Handler(Looper.getMainLooper())
    private var scanRunnable: Runnable? = null

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
        toast("Servico iniciado!")
        try {
            createNotificationChannel()
            startForeground(NOTIF_ID, buildNotification())
        } catch (e: Throwable) {}
        createFloatingWindow()
    }

    private fun createFloatingWindow() {
        try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager = wm

            val view = LayoutInflater.from(this).inflate(R.layout.floating_overlay, null)
            floatingView = view

            @Suppress("DEPRECATION")
            val type = if (Build.VERSION.SDK_INT >= 26) TYPE_OVERLAY_VALUE
                       else WindowManager.LayoutParams.TYPE_PHONE

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.TOP or Gravity.START
            params.x = 16
            params.y = 100
            floatingParams = params

            setupDrag(view, wm, params)
            setupControls(view)

            wm.addView(view, params)

            toast("Overlay ativo! Abra o Gunbound.")
        } catch (e: Throwable) {
            toast("Erro overlay: ${e.javaClass.simpleName}: ${e.message?.take(60)}")
            isRunning = false
            stopSelf()
        }
    }

    private fun setupDrag(view: View, wm: WindowManager, params: WindowManager.LayoutParams) {
        view.findViewById(R.id.floatingHeader).setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x; initialY = params.y
                    initialTouchX = event.rawX; initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    try { wm.updateViewLayout(view, params) } catch (e: Throwable) {}
                    true
                }
                else -> false
            }
        }
        view.findViewById(R.id.btnCloseOverlay).setOnClickListener { stopSelf() }

        var collapsed = false
        val collapseBtn  = view.findViewById(R.id.btnCollapseOverlay) as android.widget.Button
        val content      = view.findViewById(R.id.overlayContent)     as android.view.ViewGroup
        collapseBtn.setOnClickListener {
            collapsed = !collapsed
            content.visibility = if (collapsed) android.view.View.GONE else android.view.View.VISIBLE
            collapseBtn.text   = if (collapsed) "▼" else "▲"
        }

        windPreviewImg = view.findViewById(R.id.overlayWindPreview) as ImageView
        view.findViewById(R.id.btnScanWind).setOnClickListener {
            windPreviewImg?.let { captureWindRegion(it, applyResult = true) }
        }
    }

    private fun setupControls(v: View) {
        val tv  = v.findViewById(R.id.overlayTrajectoryView)  as TrajectoryView
        val at  = v.findViewById(R.id.overlayAngleValue)      as TextView
        val pt  = v.findViewById(R.id.overlayPowerValue)      as TextView
        val wt  = v.findViewById(R.id.overlayWindValue)       as TextView
        val wvt = v.findViewById(R.id.overlayWindVertValue)   as TextView
        val db  = v.findViewById(R.id.overlayBtnDirection)    as Button
        val mc  = v.findViewById(R.id.overlayMobileContainer) as LinearLayout

        trajectoryView = tv
        windHText = wt
        windVText = wvt

        // Mobile selection
        val btns = mutableListOf<Button>()
        MobileData.mobiles.forEachIndexed { i, mobile ->
            val btn = Button(this)
            btn.text = mobile.displayName
            btn.textSize = 9f
            btn.setAllCaps(false)
            btn.setPadding(8, 2, 8, 2)
            btn.setBackgroundColor(Color.parseColor("#1E3A5A"))
            btn.setTextColor(Color.WHITE)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(2, 2, 2, 2)
            btn.layoutParams = lp
            btn.setOnClickListener {
                btns.forEachIndexed { j, b ->
                    if (j == i) { b.setBackgroundColor(Color.parseColor("#00FF88")); b.setTextColor(Color.BLACK) }
                    else        { b.setBackgroundColor(Color.parseColor("#1E3A5A")); b.setTextColor(Color.WHITE) }
                }
                tv.selectedMobile = MobileData.mobiles[i]
            }
            btns.add(btn)
            mc.addView(btn)
        }
        btns[0].setBackgroundColor(Color.parseColor("#00FF88"))
        btns[0].setTextColor(Color.BLACK)

        var angle = 45; var power = 50

        fun updateAngle() { tv.angle = angle.toFloat(); at.text = "$angle°" }
        fun updatePower() { tv.power = power.toFloat(); pt.text = "$power" }

        updateAngle(); updatePower()
        applyWindToView()

        // Angle buttons
        v.findViewById(R.id.btnAngleMinus5).setOnClickListener { angle = (angle - 5).coerceAtLeast(0); updateAngle() }
        v.findViewById(R.id.btnAngleMinus1).setOnClickListener { angle = (angle - 1).coerceAtLeast(0); updateAngle() }
        v.findViewById(R.id.btnAnglePlus1) .setOnClickListener { angle = (angle + 1).coerceAtMost(90); updateAngle() }
        v.findViewById(R.id.btnAnglePlus5) .setOnClickListener { angle = (angle + 5).coerceAtMost(90); updateAngle() }

        // Power buttons
        v.findViewById(R.id.btnPowerMinus5).setOnClickListener { power = (power - 5).coerceAtLeast(0);   updatePower() }
        v.findViewById(R.id.btnPowerMinus1).setOnClickListener { power = (power - 1).coerceAtLeast(0);   updatePower() }
        v.findViewById(R.id.btnPowerPlus1) .setOnClickListener { power = (power + 1).coerceAtMost(100);  updatePower() }
        v.findViewById(R.id.btnPowerPlus5) .setOnClickListener { power = (power + 5).coerceAtMost(100);  updatePower() }

        // Horizontal wind buttons
        v.findViewById(R.id.btnWindLeft) .setOnClickListener { windHDir = -1; applyWindToView() }
        v.findViewById(R.id.btnWindRight).setOnClickListener { windHDir =  1; applyWindToView() }
        v.findViewById(R.id.btnWindMinus).setOnClickListener { windH = (windH - 1).coerceAtLeast(0); applyWindToView() }
        v.findViewById(R.id.btnWindPlus) .setOnClickListener { windH = (windH + 1).coerceAtMost(10); applyWindToView() }

        // Vertical wind buttons
        v.findViewById(R.id.btnWindUp)       .setOnClickListener { windVDir = -1; applyWindToView() }
        v.findViewById(R.id.btnWindDown)     .setOnClickListener { windVDir =  1; applyWindToView() }
        v.findViewById(R.id.btnWindVertMinus).setOnClickListener { windV = (windV - 1).coerceAtLeast(0); applyWindToView() }
        v.findViewById(R.id.btnWindVertPlus) .setOnClickListener { windV = (windV + 1).coerceAtMost(10); applyWindToView() }

        // Character direction
        var right = true
        db.text = "-> DIREITA"
        db.setOnClickListener {
            right = !right; tv.facingRight = right
            db.text = if (right) "-> DIREITA" else "<- ESQUERDA"
        }
    }

    private fun applyWindToView() {
        val tv = trajectoryView ?: return
        tv.windSpeed  = (windH * windHDir).toFloat()
        tv.windSpeedY = (windV * windVDir).toFloat()
        windHText?.text = "${if (windHDir > 0) "→" else "←"}$windH"
        windVText?.text = "${if (windVDir > 0) "↓" else "↑"}$windV"
    }

    // ---------------------------------------------------------------
    // Continuous auto-scan loop (runs every 1.2 s when projection OK)
    // ---------------------------------------------------------------
    private fun startWindScanLoop() {
        val run = object : Runnable {
            override fun run() {
                val img = windPreviewImg
                if (img != null && mediaProjection != null) {
                    captureWindRegion(img, applyResult = true)
                }
                scanHandler.postDelayed(this, 1200)
            }
        }
        scanRunnable = run
        scanHandler.postDelayed(run, 1200)
    }

    private fun stopWindScanLoop() {
        scanRunnable?.let { scanHandler.removeCallbacks(it) }
        scanRunnable = null
    }

    fun captureWindRegion(previewImage: ImageView, applyResult: Boolean = false) {
        val proj = mediaProjection ?: return
        try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
            val sw = metrics.widthPixels
            val sh = metrics.heightPixels
            val density = metrics.densityDpi

            val reader = ImageReader.newInstance(sw, sh, PixelFormat.RGBA_8888, 2)
            val vd: VirtualDisplay = proj.createVirtualDisplay(
                "GBWindScan", sw, sh, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface, null, null
            )

            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    val image = reader.acquireLatestImage()
                    if (image != null) {
                        val plane = image.planes[0]
                        val buf = plane.buffer
                        val ps = plane.pixelStride
                        val rs = plane.rowStride
                        val bmp = Bitmap.createBitmap(rs / ps, sh, Bitmap.Config.ARGB_8888)
                        bmp.copyPixelsFromBuffer(buf)
                        image.close()

                        val cx = sw / 4
                        val cw = sw / 2
                        val ch = sh / 8
                        val crop = Bitmap.createBitmap(bmp, cx, 0, cw, ch)

                        Handler(Looper.getMainLooper()).post {
                            previewImage.setImageBitmap(crop)
                            previewImage.visibility = View.VISIBLE

                            if (applyResult) {
                                val result = WindDetector.detect(crop)
                                if (result != null && result.confidence >= 0.5f) {
                                    windH    = result.magnitude
                                    windHDir = if (result.windH >= 0f) 1 else -1
                                    windV    = kotlin.math.abs(result.windV).toInt()
                                    windVDir = if (result.windV >= 0f) 1 else -1
                                    applyWindToView()
                                }
                            }
                        }
                    }
                } catch (e: Throwable) {
                    // silently skip failed captures in the auto-loop
                } finally {
                    try { vd.release() } catch (e: Throwable) {}
                    try { reader.close() } catch (e: Throwable) {}
                }
            }, 300)
        } catch (e: Throwable) {
            // projection may have been revoked; loop will retry next cycle
        }
    }

    private fun toast(msg: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, msg, Toast.LENGTH_LONG).show()
        }
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
            .setContentText("Overlay ativo")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Fechar", pi)

        if (Build.VERSION.SDK_INT >= 26) {
            try { b.javaClass.getMethod("setChannelId", String::class.java).invoke(b, CHANNEL_ID) }
            catch (e: Throwable) {}
        }
        return b.build()
    }

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
                    startWindScanLoop()
                }
            }
        } catch (e: Throwable) {}
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        stopWindScanLoop()
        try { mediaProjection?.stop() } catch (e: Throwable) {}
        val v = floatingView
        val wm = windowManager
        if (v != null && wm != null) {
            try { wm.removeView(v) } catch (e: Throwable) {}
        }
    }
}
