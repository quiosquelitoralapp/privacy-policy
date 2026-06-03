package com.quiosquelitoral.gunboundguide

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
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

    companion object {
        const val ACTION_STOP = "STOP_OVERLAY"
        const val NOTIF_ID = 1
        const val CHANNEL_ID = "gb_overlay"
        // TYPE_APPLICATION_OVERLAY (API 26) = 2038; TYPE_PHONE (legado) = 2002
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
        } catch (e: Throwable) {
            // Android 14 may reject startForeground without type — continues without notification
        }
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
    }

    private fun setupControls(v: View) {
        val tv  = v.findViewById(R.id.overlayTrajectoryView)  as TrajectoryView
        val as_ = v.findViewById(R.id.overlaySeekAngle)       as SeekBar
        val ps  = v.findViewById(R.id.overlaySeekPower)       as SeekBar
        val ws  = v.findViewById(R.id.overlaySeekWind)        as SeekBar
        val at  = v.findViewById(R.id.overlayAngleValue)      as TextView
        val pt  = v.findViewById(R.id.overlayPowerValue)      as TextView
        val wt  = v.findViewById(R.id.overlayWindValue)       as TextView
        val db  = v.findViewById(R.id.overlayBtnDirection)    as Button
        val mc  = v.findViewById(R.id.overlayMobileContainer) as LinearLayout

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

        as_.setOnSeekBarChangeListener(seek { p -> tv.angle = p.toFloat(); at.text = "$p°" })
        ps.setOnSeekBarChangeListener( seek { p -> tv.power = p.toFloat(); pt.text = "$p"  })
        ws.setOnSeekBarChangeListener( seek { p ->
            val w = (p - 10).toFloat(); tv.windSpeed = w
            wt.text = when { w > 0f -> "->${ w.toInt()}"; w < 0f -> "<-${(-w).toInt()}"; else -> "0" }
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
        if (intent?.action == ACTION_STOP) stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        val v = floatingView
        val wm = windowManager
        if (v != null && wm != null) {
            try { wm.removeView(v) } catch (e: Throwable) {}
        }
    }
}
