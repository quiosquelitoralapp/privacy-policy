package com.quiosquelitoral.gunboundguide

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.*

class TrajectoryView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var angle: Float = 45f
        set(value) { field = value.coerceIn(0f, 90f); invalidate() }

    var power: Float = 50f
        set(value) { field = value.coerceIn(0f, 100f); invalidate() }

    var windSpeed: Float = 0f
        set(value) { field = value.coerceIn(-10f, 10f); invalidate() }

    var facingRight: Boolean = true
        set(value) { field = value; invalidate() }

    var selectedMobile: Mobile = MobileData.mobiles[0]
        set(value) { field = value; invalidate() }

    private val trajPaintGreen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00FF88")
        strokeWidth = 3.5f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(12f, 6f), 0f)
    }

    private val trajPaintYellow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD700")
        strokeWidth = 3.5f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(12f, 6f), 0f)
    }

    private val trajPaints = listOf(trajPaintGreen, trajPaintYellow)

    private val groundPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val gridPaint = Paint().apply {
        color = Color.parseColor("#1A3355")
        strokeWidth = 1f
    }

    private val cannonBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD700")
        style = Paint.Style.FILL
    }

    private val cannonBarrelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFA000")
        strokeWidth = 10f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val wheelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8B6914")
        style = Paint.Style.FILL
    }

    private val landingStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF4444")
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }

    private val landingFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#55FF4444")
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 32f
        typeface = Typeface.MONOSPACE
    }

    private val windTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#87CEEB")
        textSize = 30f
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
    }

    private val windArrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#87CEEB")
        strokeWidth = 4f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val windArrowFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#87CEEB")
        style = Paint.Style.FILL
    }

    private val groundLinePaint = Paint().apply {
        color = Color.parseColor("#4CAF50")
        strokeWidth = 2f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val groundY = h * 0.80f

        drawSky(canvas, w, groundY)
        drawGrid(canvas, w, groundY)
        drawGround(canvas, w, h, groundY)

        val shooterX = if (facingRight) w * 0.12f else w * 0.88f
        val shooterY = groundY

        val results = GunboundPhysics.simulate(
            shooterX, shooterY, angle, power, windSpeed,
            selectedMobile, facingRight, groundY, w
        )

        results.forEachIndexed { idx, result ->
            drawTrajectoryPath(canvas, result.points, trajPaints[idx % trajPaints.size])
        }

        results.forEach { result ->
            if (result.points.isNotEmpty()) {
                drawLandingMarker(canvas, result.landingX, groundY)
            }
        }

        drawCannon(canvas, shooterX, shooterY)
        drawWindIndicator(canvas, w)
        drawDistanceInfo(canvas, results, shooterX, groundY, w)
        drawAngleIndicator(canvas, shooterX, shooterY)
    }

    private fun drawSky(canvas: Canvas, w: Float, groundY: Float) {
        val gradient = LinearGradient(
            0f, 0f, 0f, groundY,
            intArrayOf(
                Color.parseColor("#050A14"),
                Color.parseColor("#0D1B2A"),
                Color.parseColor("#142840")
            ),
            floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP
        )
        val paint = Paint().apply { shader = gradient }
        canvas.drawRect(0f, 0f, w, groundY, paint)
    }

    private fun drawGrid(canvas: Canvas, w: Float, groundY: Float) {
        val cols = 10
        val rows = 6
        val colStep = w / cols
        val rowStep = groundY / rows
        for (i in 0..cols) canvas.drawLine(i * colStep, 0f, i * colStep, groundY, gridPaint)
        for (i in 0..rows) canvas.drawLine(0f, i * rowStep, w, i * rowStep, gridPaint)
    }

    private fun drawGround(canvas: Canvas, w: Float, h: Float, groundY: Float) {
        val gradient = LinearGradient(
            0f, groundY, 0f, h,
            intArrayOf(Color.parseColor("#2E7D32"), Color.parseColor("#1A4A1A")),
            null, Shader.TileMode.CLAMP
        )
        groundPaint.shader = gradient
        canvas.drawRect(0f, groundY, w, h, groundPaint)
        canvas.drawLine(0f, groundY, w, groundY, groundLinePaint)
    }

    private fun drawCannon(canvas: Canvas, x: Float, y: Float) {
        val direction = if (facingRight) 1f else -1f
        val angleRad = Math.toRadians(angle.toDouble()).toFloat()
        val baseY = y - 14f

        // Rodas
        canvas.drawCircle(x - direction * 16f, y - 7f, 10f, wheelPaint)
        canvas.drawCircle(x + direction * 4f, y - 7f, 10f, wheelPaint)

        // Corpo do canhão
        canvas.drawCircle(x, baseY, 18f, cannonBodyPaint)

        // Cano do canhão
        val barrelLen = 44f
        val bx = x + direction * barrelLen * cos(angleRad)
        val by = baseY - barrelLen * sin(angleRad)
        canvas.drawLine(x, baseY, bx, by, cannonBarrelPaint)
    }

    private fun drawTrajectoryPath(canvas: Canvas, points: List<TrajectoryPoint>, paint: Paint) {
        if (points.size < 2) return
        val path = Path()
        path.moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) {
            path.lineTo(points[i].x, points[i].y)
        }
        canvas.drawPath(path, paint)
    }

    private fun drawLandingMarker(canvas: Canvas, landX: Float, groundY: Float) {
        val r = 16f
        canvas.drawCircle(landX, groundY, r, landingFillPaint)
        canvas.drawCircle(landX, groundY, r, landingStrokePaint)
        canvas.drawLine(landX - r + 4, groundY - r + 4, landX + r - 4, groundY + r - 4, landingStrokePaint)
        canvas.drawLine(landX + r - 4, groundY - r + 4, landX - r + 4, groundY + r - 4, landingStrokePaint)
    }

    private fun drawWindIndicator(canvas: Canvas, w: Float) {
        val cx = w / 2f
        val speed = abs(windSpeed)
        val dirSymbol = when {
            windSpeed > 0f -> "→"
            windSpeed < 0f -> "←"
            else -> "•"
        }
        val windLabel = "VENTO $dirSymbol ${speed.toInt()}"
        canvas.drawText(windLabel, cx, 38f, windTextPaint)

        if (speed > 0f) {
            val arrowLen = (speed / 10f) * 55f
            val arrowY = 55f
            val dir = if (windSpeed > 0f) 1f else -1f
            val startX = cx - dir * arrowLen / 2f
            val endX = cx + dir * arrowLen / 2f

            canvas.drawLine(startX, arrowY, endX - dir * 12f, arrowY, windArrowPaint)

            val head = Path()
            head.moveTo(endX, arrowY)
            head.lineTo(endX - dir * 14f, arrowY - 7f)
            head.lineTo(endX - dir * 14f, arrowY + 7f)
            head.close()
            canvas.drawPath(head, windArrowFillPaint)
        }
    }

    private fun drawAngleIndicator(canvas: Canvas, x: Float, y: Float) {
        val angleRad = Math.toRadians(angle.toDouble()).toFloat()
        val direction = if (facingRight) 1f else -1f
        val r = 30f
        val baseY = y - 14f

        val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#44FFFFFF")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }

        val startAngleDeg = if (facingRight) -angle else -(180f - angle)
        val rect = RectF(x - r, baseY - r, x + r, baseY + r)
        canvas.drawArc(rect, startAngleDeg, if (facingRight) angle else -angle, false, arcPaint)
    }

    private fun drawDistanceInfo(canvas: Canvas, results: List<TrajectoryResult>, shooterX: Float, groundY: Float, w: Float) {
        val yBase = groundY + 28f
        results.forEachIndexed { idx, result ->
            val dist = result.distance.toInt()
            val labelPart = if (result.label.isNotEmpty()) "${result.label}: " else ""
            val text = "$labelPart${dist}px"
            textPaint.textSize = 28f
            textPaint.color = if (idx == 0) Color.parseColor("#00FF88") else Color.parseColor("#FFD700")
            canvas.drawText(text, 12f, yBase + idx * 34f, textPaint)
        }

        // Mobile name top-left
        textPaint.textSize = 26f
        textPaint.color = Color.parseColor("#AAAAAA")
        canvas.drawText(selectedMobile.displayName.toUpperCase(), 12f, 30f, textPaint)
    }
}
