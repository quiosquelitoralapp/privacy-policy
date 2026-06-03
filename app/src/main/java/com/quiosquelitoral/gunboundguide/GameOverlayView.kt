package com.quiosquelitoral.gunboundguide

import android.content.Context
import android.graphics.*
import android.view.View

class GameOverlayView(context: Context) : View(context) {

    var charX   = 0f
    var charY   = 0f
    var groundY = 0f
    var angle   = 45f
    var power   = 50f
    var windH   = 0f
    var windV   = 0f
    var facingRight = true
    var mobile  = MobileData.mobiles[0]
    var screenW = 1f
    var active  = false

    init {
        setWillNotDraw(false)
        // Software rendering — compatível com todos dispositivos Android
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    // Arco branco: trajetória SEM vento
    private val noWindPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 8f
        style = Paint.Style.STROKE
        alpha = 180
    }

    // Arco ciano: trajetória COM vento (onde o tiro cai de verdade)
    private val windPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.CYAN
        strokeWidth = 10f
        style = Paint.Style.STROKE
    }

    private val landFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.CYAN; alpha = 120; style = Paint.Style.FILL
    }
    private val landStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.CYAN; strokeWidth = 6f; style = Paint.Style.STROKE
    }
    private val noLandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; alpha = 180; strokeWidth = 4f; style = Paint.Style.STROKE
    }
    private val hudBg = Paint().apply {
        color = Color.BLACK; alpha = 160; style = Paint.Style.FILL
    }
    private val hudText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.CYAN; textSize = 40f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    // Círculo vermelho no canto — prova que o overlay está ativo
    private val debugPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED; style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Indicador de vida: SEMPRE visível no canto superior direito
        // Se aparecer na tela, o overlay está funcionando
        val dw = width.toFloat()
        canvas.drawCircle(dw - 30f, 30f, 18f, debugPaint)

        if (!active || charX == 0f || groundY == 0f) return

        val sw = if (screenW > 1f) screenW else dw

        // Ponto amarelo na posição do personagem detectado (debug de coordenadas)
        val charDot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.YELLOW; style = Paint.Style.FILL }
        canvas.drawCircle(charX, charY, 14f, charDot)

        // Trajetória SEM vento (referência — branco)
        val noWind = GunboundPhysics.simulate(
            charX, charY, angle, power, 0f, 0f,
            mobile, facingRight, groundY, sw
        )
        noWind.forEach { r ->
            drawTraj(canvas, r.points, noWindPaint)
            if (r.points.isNotEmpty()) drawMarkerNoWind(canvas, r.landingX, groundY)
        }

        // Trajetória COM vento (ciano — onde cai de verdade)
        val withWind = GunboundPhysics.simulate(
            charX, charY, angle, power, windH, windV,
            mobile, facingRight, groundY, sw
        )
        withWind.forEach { r ->
            drawTraj(canvas, r.points, windPaint)
            if (r.points.isNotEmpty()) drawMarkerWind(canvas, r.landingX, groundY)
        }

        // HUD no topo: ângulo, vento, deriva
        val wH = kotlin.math.abs(windH).toInt()
        val wV = kotlin.math.abs(windV).toInt()
        val wHSym = if (windH > 0f) "→" else if (windH < 0f) "←" else "-"
        val wVSym = if (windV > 0f) "↓" else if (windV < 0f) "↑" else ""
        val drift = if (withWind.isNotEmpty() && noWind.isNotEmpty())
            (withWind[0].landingX - noWind[0].landingX).toInt() else 0
        val driftStr = if (drift >= 0) "+$drift" else "$drift"
        val label = "${angle.toInt()}° | $wHSym$wH $wVSym$wV | Δ$driftStr px"
        val cx = dw / 2f
        canvas.drawRect(cx - 280f, 8f, cx + 280f, 60f, hudBg)
        canvas.drawText(label, cx, 50f, hudText)
    }

    private fun drawTraj(canvas: Canvas, pts: List<TrajectoryPoint>, paint: Paint) {
        if (pts.size < 2) return
        val step = maxOf(1, pts.size / 200)
        var i = step
        while (i < pts.size) {
            canvas.drawLine(pts[i - step].x, pts[i - step].y, pts[i].x, pts[i].y, paint)
            i += step
        }
    }

    private fun drawMarkerWind(canvas: Canvas, x: Float, y: Float) {
        val r = 28f
        canvas.drawCircle(x, y, r, landFill)
        canvas.drawCircle(x, y, r, landStroke)
        canvas.drawLine(x - r + 8, y - r + 8, x + r - 8, y + r - 8, landStroke)
        canvas.drawLine(x + r - 8, y - r + 8, x - r + 8, y + r - 8, landStroke)
    }

    private fun drawMarkerNoWind(canvas: Canvas, x: Float, y: Float) {
        val r = 18f
        canvas.drawCircle(x, y, r, noLandPaint)
        canvas.drawLine(x - r, y, x + r, y, noLandPaint)
        canvas.drawLine(x, y - r, x, y + r, noLandPaint)
    }
}
