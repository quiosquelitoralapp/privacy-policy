package com.quiosquelitoral.gunboundguide

import android.graphics.Bitmap
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

object AimDetector {

    private const val BRIGHT = 200
    private const val SCAN_RADIUS = 250  // px máx de varredura radial
    private const val MIN_SCORE = 6      // mín de pixels brilhantes para aceitar ângulo

    /**
     * Varre radialmente a partir da posição do personagem buscando a linha
     * da mira (pixels brilhantes em linha reta emanando do personagem).
     * Retorna ângulo em graus (0 = horizontal, 90 = vertical), ou null.
     */
    fun detect(bmp: Bitmap, charX: Int, charY: Int, facingRight: Boolean): Float? {
        val w = bmp.width
        val h = bmp.height

        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)

        val hDir = if (facingRight) 1f else -1f
        var bestAngle = -1
        var bestScore = MIN_SCORE.toFloat()

        for (angleDeg in 0..88 step 2) {
            val rad = Math.toRadians(angleDeg.toDouble())
            val dx = hDir * cos(rad).toFloat()
            val dy = -sin(rad).toFloat()  // y invertido na tela

            var score = 0f
            var r = 12
            while (r < SCAN_RADIUS) {
                val px = (charX + r * dx).roundToInt()
                val py = (charY + r * dy).roundToInt()
                if (px < 0 || px >= w || py < 0 || py >= h) break
                val p = pixels[py * w + px]
                val r2 = (p shr 16) and 0xFF
                val g2 = (p shr 8)  and 0xFF
                val b2 = p and 0xFF
                val gray = (r2 * 299 + g2 * 587 + b2 * 114) / 1000
                // Branco OU azul/ciano brilhante (mira parabólica do Gunbound)
                if (gray > BRIGHT || (b2 > 150 && b2 > r2 + 50)) score += 1f
                r += 3
            }

            if (score > bestScore) {
                bestScore = score
                bestAngle = angleDeg
            }
        }

        return if (bestAngle >= 0) bestAngle.toFloat() else null
    }
}
