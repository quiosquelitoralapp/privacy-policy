package com.quiosquelitoral.gunboundguide

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.*

class FloatingWindowService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var floatingParams: WindowManager.LayoutParams

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    companion object {
        const val ACTION_STOP = "STOP_OVERLAY"
        const val NOTIF_ID = 1
        const val CHANNEL_ID = "gunbound_overlay"
        var isRunning = false
        // TYPE_APPLICATION_OVERLAY = 2038, adicionado no API 26
        const val TYPE_OVERLAY = 2038
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        createFloatingWindow()
    }

    private fun createFloatingWindow() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_overlay, null)

        @Suppress("DEPRECATION")
        val windowType = if (Build.VERSION.SDK_INT >= 26) TYPE_OVERLAY
                         else WindowManager.LayoutParams.TYPE_PHONE

        floatingParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        )
        floatingParams.gravity = Gravity.TOP or Gravity.START
        floatingParams.x = 0
        floatingParams.y = 80

        setupDrag()
        setupControls()
        windowManager.addView(floatingView, floatingParams)
    }

    private fun setupDrag() {
        val header = floatingView.findViewById(R.id.floatingHeader)
        header.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = floatingParams.x
                    initialY = floatingParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    floatingParams.x = initialX + (event.rawX - initialTouchX).toInt()
                    floatingParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(floatingView, floatingParams)
                    true
                }
                else -> false
            }
        }
        floatingView.findViewById(R.id.btnCloseOverlay).setOnClickListener {
            stopSelf()
        }
    }

    private fun setupControls() {
        val tv  = floatingView.findViewById(R.id.overlayTrajectoryView)  as TrajectoryView
        val as_ = floatingView.findViewById(R.id.overlaySeekAngle)       as SeekBar
        val ps  = floatingView.findViewById(R.id.overlaySeekPower)       as SeekBar
        val ws  = floatingView.findViewById(R.id.overlaySeekWind)        as SeekBar
        val at  = floatingView.findViewById(R.id.overlayAngleValue)      as TextView
        val pt  = floatingView.findViewById(R.id.overlayPowerValue)      as TextView
        val wt  = floatingView.findViewById(R.id.overlayWindValue)       as TextView
        val db  = floatingView.findViewById(R.id.overlayBtnDirection)    as Button
        val mc  = floatingView.findViewById(R.id.overlayMobileContainer) as LinearLayout

        val btns = mutableListOf<Button>()
        MobileData.mobiles.forEachIndexed { i, mobile ->
            val btn = Button(this)
            btn.text = mobile.displayName
            btn.textSize = 9f
            btn.setAllCaps(false)
            btn.setPadding(10, 2, 10, 2)
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

        as_.max = 90;  as_.progress = 45
        ps.max  = 100; ps.progress  = 50
        ws.max  = 20;  ws.progress  = 10
        at.text = "45°"; pt.text = "50"; wt.text = "0"

        as_.setOnSeekBarChangeListener(seek { p -> tv.angle = p.toFloat();  at.text = "$p°" })
        ps.setOnSeekBarChangeListener( seek { p -> tv.power = p.toFloat();  pt.text = "$p"  })
        ws.setOnSeekBarChangeListener( seek { p ->
            val w = (p - 10).toFloat(); tv.windSpeed = w
            wt.text = when { w > 0f -> "->${ w.toInt()}" ; w < 0f -> "<-${(-w).toInt()}" ; else -> "0" }
        })

        var right = true
        db.text = "-> DIREITA"
        db.setOnClickListener {
            right = !right; tv.facingRight = right
            db.text = if (right) "-> DIREITA" else "<- ESQUERDA"
        }
    }

    private fun seek(block: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(sb: SeekBar, p: Int, f: Boolean) = block(p)
        override fun onStartTrackingTouch(sb: SeekBar) {}
        override fun onStopTrackingTouch(sb: SeekBar) {}
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        try {
            val chCls = Class.forName("android.app.NotificationChannel")
            val ch = chCls.getConstructor(String::class.java, CharSequence::class.java, Int::class.java)
                         .newInstance(CHANNEL_ID, "Gunbound Mira", 2)
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.javaClass.getMethod("createNotificationChannel", chCls).invoke(nm, ch)
        } catch (e: Exception) { /* ignore */ }
    }

    @Suppress("DEPRECATION")
    private fun buildNotification(): Notification {
        val si = Intent(this, FloatingWindowService::class.java).apply { action = ACTION_STOP }
        val sf = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                     PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                 else PendingIntent.FLAG_UPDATE_CURRENT
        val sp = PendingIntent.getService(this, 0, si, sf)

        val b = Notification.Builder(this)
            .setContentTitle("Gunbound Guia de Mira")
            .setContentText("Overlay ativo — arraste para mover")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Fechar", sp)

        if (Build.VERSION.SDK_INT >= 26) {
            try {
                b.javaClass.getMethod("setChannelId", String::class.java).invoke(b, CHANNEL_ID)
            } catch (e: Exception) {}
        }
        return b.build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        if (::floatingView.isInitialized) {
            try { windowManager.removeView(floatingView) } catch (e: Exception) {}
        }
    }
}
