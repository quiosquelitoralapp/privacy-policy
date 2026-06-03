package com.quiosquelitoral.gunboundguide

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

data class TrajectoryPoint(val x: Float, val y: Float)

data class TrajectoryResult(
    val points: List<TrajectoryPoint>,
    val landingX: Float,
    val distance: Float,
    val label: String = ""
)

object GunboundPhysics {

    private const val MAX_STEPS = 2000

    fun simulate(
        startX: Float,
        startY: Float,
        angleDeg: Float,
        power: Float,
        windValue: Float,
        mobile: Mobile,
        facingRight: Boolean,
        groundY: Float,
        canvasWidth: Float
    ): List<TrajectoryResult> {
        return when (mobile.shotType) {
            ShotType.SPREAD -> listOf(
                simulateSingle(startX, startY, angleDeg + mobile.spreadAngle / 2f, power, windValue, mobile, facingRight, groundY, canvasWidth, "Tiro 1"),
                simulateSingle(startX, startY, angleDeg - mobile.spreadAngle / 2f, power, windValue, mobile, facingRight, groundY, canvasWidth, "Tiro 2")
            )
            else -> listOf(
                simulateSingle(startX, startY, angleDeg, power, windValue, mobile, facingRight, groundY, canvasWidth, "")
            )
        }
    }

    private fun simulateSingle(
        startX: Float,
        startY: Float,
        angleDeg: Float,
        power: Float,
        windValue: Float,
        mobile: Mobile,
        facingRight: Boolean,
        groundY: Float,
        canvasWidth: Float,
        label: String
    ): TrajectoryResult {
        val angleRad = Math.toRadians(angleDeg.toDouble()).toFloat()
        val speed = (power / 100f) * mobile.maxSpeed
        val direction = if (facingRight) 1f else -1f

        var vx = speed * cos(angleRad) * direction
        var vy = -speed * sin(angleRad)

        var x = startX
        var y = startY
        val points = mutableListOf<TrajectoryPoint>()
        var bouncesDone = 0
        val maxBounces = if (mobile.shotType == ShotType.BOUNCE) 1 else 0

        for (step in 0 until MAX_STEPS) {
            vx += windValue * mobile.windFactor

            // Boomer: projétil curva de volta após o apex
            if (mobile.shotType == ShotType.CURVE && step > 40) {
                val curveForce = 0.003f * (step - 40)
                vx -= direction * curveForce.coerceAtMost(0.15f)
            }

            vy += mobile.gravity
            x += vx
            y += vy

            x = x.coerceIn(0f, canvasWidth)

            points.add(TrajectoryPoint(x, y))

            if (y >= groundY) {
                if (bouncesDone < maxBounces) {
                    // Quica: reflete vy com perda de energia
                    y = groundY
                    vy = -vy * 0.55f
                    vx *= 0.75f
                    bouncesDone++
                } else {
                    break
                }
            }
        }

        val landingX = if (points.isNotEmpty()) points.last().x else startX
        return TrajectoryResult(points, landingX, abs(landingX - startX), label)
    }
}
