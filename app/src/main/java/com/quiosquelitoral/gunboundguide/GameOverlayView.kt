package com.quiosquelitoral.gunboundguide

import android.content.Context
import android.graphics.*
import android.view.View

/**
 * View full-screen transparente adicionada por cima do jogo.
 * Não intercepta toques (FLAG_NOT_TOUCHABLE no WindowManager).
 * Desenha:
 *   – Arco BRANCO tracejado : trajetória SEM vento (onde a mira aponta)
 *   – Arco CIANO  contínuo  : trajetória COM vento (onde o tiro cai de verdade)
 */
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

    private val noWindPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#BBFFFFFF")
        strokeWidth = 5f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(18f, 10f), 0f)
    }

    private val windPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF00FFFF")    // ciano
        strokeWidth = 6f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(24f, 8f), 0f)
    }

    private val landFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6600FFFF")
        style = Paint.Style.FILL
    }
    private val landStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF00FFFF")
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }
    private val noLandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#99FFFFFF")
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF00FFFF")
        textSize = 36f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val labelBgPaint = Paint().apply {
        color = Color.parseColor("#AA000000")
        style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!active || charX == 0f || groundY == 0f) return

        // Trajetória SEM vento (referência)
        val noWind = GunboundPhysics.simulate(
            charX, charY, angle, power, 0f, 0f,
            mobile, facingRight, groundY, screenW
        )
        noWind.forEach { r ->
            drawTraj(canvas, r.points, noWindPaint)
            if (r.points.isNotEmpty()) drawMarkerNoWind(canvas, r.landingX, groundY)
        }

        // Trajetória COM vento (onde cai de verdade)
        val withWind = GunboundPhysics.simulate(
            charX, charY, angle, power, windH, windV,
            mobile, facingRight, groundY, screenW
        )
        withWind.forEach { r ->
            drawTraj(canvas, r.points, windPaint)
            if (r.points.isNotEmpty()) drawMarkerWind(canvas, r.landingX, groundY)
        }

        // HUD: ângulo, vento, deriva
        val wHSym = when { windH > 0f -> "→"; windH < 0f -> "←"; else -> "•" }
        val wVSym = when { windV > 0f -> "↓"; windV < 0f -> "↑"; else -> "" }
        val drift = if (withWind.isNotEmpty() && noWind.isNotEmpty())
            withWind[0].landingX - noWind[0].landingX else 0f
        val driftStr = if (drift > 0) "+${drift.toInt()}" else "${drift.toInt()}"
        val wStr = "${wHSym}${kotlin.math.abs(windH).toInt()} ${wVSym}${kotlin.math.abs(windV).toInt()}"
        val label = "${angle.toInt()}° | V:$wStr | Δ$driftStr"
        val lx = width / 2f
        val ly = 52f
        canvas.drawRect(lx - 260f, ly - 38f, lx + 260f, ly + 10f, labelBgPaint)
        canvas.drawText(label, lx, ly, labelPaint)
    }

    private fun drawTraj(canvas: Canvas, pts: List<TrajectoryPoint>, paint: Paint) {
        if (pts.size < 2) return
        val step = maxOf(1, pts.size / 180)
        val path = Path()
        path.moveTo(pts[0].x, pts[0].y)
        var i = step
        while (i < pts.size) { path.lineTo(pts[i].x, pts[i].y); i += step }
        path.lineTo(pts.last().x, pts.last().y)
        canvas.drawPath(path, paint)
    }

    private fun drawMarkerWind(canvas: Canvas, x: Float, y: Float) {
        val r = 26f
        canvas.drawCircle(x, y, r, landFill)
        canvas.drawCircle(x, y, r, landStroke)
        canvas.drawLine(x - r + 7, y - r + 7, x + r - 7, y + r - 7, landStroke)
        canvas.drawLine(x + r - 7, y - r + 7, x - r + 7, y + r - 7, landStroke)
    }

    private fun drawMarkerNoWind(canvas: Canvas, x: Float, y: Float) {
        val r = 16f
        canvas.drawCircle(x, y, r, noLandPaint)
        canvas.drawLine(x - r, y, x + r, y, noLandPaint)
        canvas.drawLine(x, y - r, x, y + r, noLandPaint)
    }
}
