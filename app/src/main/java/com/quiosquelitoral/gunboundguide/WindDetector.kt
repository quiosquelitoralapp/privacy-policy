package com.quiosquelitoral.gunboundguide

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.max

object WindDetector {

    data class WindResult(
        val windH: Float,      // -10..10, positivo = direita
        val windV: Float,      // -10..10, positivo = baixo
        val magnitude: Int,    // 0..10
        val dirChar: Char,     // L R U D ?
        val confidence: Float  // 0..1
    )

    private const val BRIGHT = 130  // limiar de pixel "aceso" (reduzido para capturar mais)
    private const val MIN_PIXELS = 8

    /**
     * Recebe o crop do indicador de vento (faixa superior central da tela)
     * e retorna força + direção detectadas.
     */
    fun detect(bmp: Bitmap): WindResult? {
        val w = bmp.width
        val h = bmp.height
        if (w < 20 || h < 10) return null

        val gray = toGray(bmp, w, h)

        // --- 1. Encontra bounding box de todos os pixels acesos ---
        var litCount = 0
        var bx0 = w; var bx1 = 0; var by0 = h; var by1 = 0
        for (y in 0 until h) for (x in 0 until w) {
            if (gray[y][x] > BRIGHT) {
                litCount++
                if (x < bx0) bx0 = x
                if (x > bx1) bx1 = x
                if (y < by0) by0 = y
                if (y > by1) by1 = y
            }
        }
        if (litCount < MIN_PIXELS || bx1 <= bx0) return null

        val roiW = bx1 - bx0 + 1
        val roiH = by1 - by0 + 1

        // --- 2. Separa região do ícone (1/3 esquerda) da região do dígito ---
        val iconX1 = bx0
        val iconX2 = bx0 + roiW / 3
        val digitX1 = bx0 + roiW / 2
        val digitX2 = bx1

        // --- 3. Detecta direção pelo ícone (forma assimétrica) ---
        val dir = detectDirection(gray, iconX1, by0, iconX2, by1, w, h)

        // --- 4. Detecta magnitude pelo dígito ---
        val mag = detectDigit(gray, digitX1, by0, digitX2, by1, w, h)

        val conf = if (mag > 0) 0.70f else 0.28f
        val strength = mag.toFloat()

        return when (dir) {
            'R' -> WindResult(strength, 0f, mag, 'R', conf)
            'L' -> WindResult(-strength, 0f, mag, 'L', conf)
            'D' -> WindResult(0f, strength, mag, 'D', conf)
            'U' -> WindResult(0f, -strength, mag, 'U', conf)
            else -> WindResult(0f, 0f, mag, '?', 0.3f)
        }
    }

    // ------------------------------------------------------------
    // Direção: olha qual metade (top/bot ou left/right) do ícone
    // tem mais pixels acesos → a ponta da seta aponta para lá.
    // Pedra caindo tem bottom-heavy, seta direita tem right-heavy.
    // ------------------------------------------------------------
    private fun detectDirection(
        gray: Array<IntArray>,
        x1: Int, y1: Int, x2: Int, y2: Int,
        w: Int, h: Int
    ): Char {
        if (x2 <= x1 || y2 <= y1) return '?'
        val midY = (y1 + y2) / 2
        val midX = (x1 + x2) / 2
        var top = 0; var bot = 0; var left = 0; var right = 0
        for (y in y1..y2.coerceAtMost(h - 1)) for (x in x1..x2.coerceAtMost(w - 1)) {
            if (gray[y][x] > BRIGHT) {
                if (y < midY) top++ else bot++
                if (x < midX) left++ else right++
            }
        }
        val totalV = top + bot + 1f
        val totalH = left + right + 1f
        val vBias = abs(top - bot) / totalV
        val hBias = abs(left - right) / totalH
        return when {
            vBias >= hBias && bot > top -> 'D'
            vBias >= hBias && top > bot -> 'U'
            hBias > vBias && right > left -> 'R'
            hBias > vBias && left > right -> 'L'
            else -> 'D'   // Gunbound Mobile frequentemente tem vento pra baixo
        }
    }

    // ------------------------------------------------------------
    // Magnitude: analisa o dígito por densidade em 5 bandas
    // horizontais e 5 fatias verticais.
    // ------------------------------------------------------------
    private fun detectDigit(
        gray: Array<IntArray>,
        x1: Int, y1: Int, x2: Int, y2: Int,
        w: Int, h: Int
    ): Int {
        if (x2 <= x1 || y2 <= y1) return 0

        // Bounding box apertada
        var ax0 = x2; var ax1 = x1; var ay0 = y2; var ay1 = y1
        for (y in y1..y2.coerceAtMost(h - 1)) for (x in x1..x2.coerceAtMost(w - 1)) {
            if (gray[y][x] > BRIGHT) {
                if (x < ax0) ax0 = x; if (x > ax1) ax1 = x
                if (y < ay0) ay0 = y; if (y > ay1) ay1 = y
            }
        }
        if (ax1 <= ax0 || ay1 <= ay0) return 0

        val dw = ax1 - ax0 + 1
        val dh = ay1 - ay0 + 1
        val aspect = dw.toFloat() / dh

        if (aspect < 0.25f) return 1   // muito estreito → 1

        // 5 bandas horizontais (densidade de pixels por banda)
        val bh = max(1, dh / 5)
        val band = FloatArray(5) { b ->
            var lit = 0; var total = 0
            for (y in ay0 + b * bh until (ay0 + (b + 1) * bh).coerceAtMost(h)) {
                for (x in ax0..ax1.coerceAtMost(w - 1)) {
                    total++; if (gray[y][x] > BRIGHT) lit++
                }
            }
            if (total > 0) lit.toFloat() / total else 0f
        }

        // 5 fatias verticais
        val sw = max(1, dw / 5)
        val slice = FloatArray(5) { s ->
            var lit = 0; var total = 0
            for (x in ax0 + s * sw until (ax0 + (s + 1) * sw).coerceAtMost(w)) {
                for (y in ay0..ay1.coerceAtMost(h - 1)) {
                    total++; if (gray[y][x] > BRIGHT) lit++
                }
            }
            if (total > 0) lit.toFloat() / total else 0f
        }

        val T = band[0]; val M = band[2]; val B = band[4]
        val L = slice[0]; val R = slice[4]

        val hasT = T > 0.35f
        val hasM = M > 0.35f
        val hasB = B > 0.35f
        val hasL = L > 0.35f
        val hasR = R > 0.35f

        // Classificação por padrão de segmentos
        return when {
            aspect < 0.45f && hasT && hasB && !hasM -> 1
            hasT && hasM && hasB && hasL && hasR   -> if (aspect > 0.75f) 8 else 0
            hasT && !hasM && hasB && hasL && hasR  -> 0
            hasT && hasM && hasB && !hasL && hasR  -> 3
            hasT && hasM && hasB && hasL && !hasR  -> 6
            hasT && hasM && !hasB && hasL && hasR  -> 9
            !hasT && hasM && hasB && hasL && !hasR -> 2
            hasT && !hasM && !hasB && !hasL && hasR -> 7
            hasT && hasM && hasB                   -> 5
            else                                   -> 4
        }
    }

    private fun toGray(bmp: Bitmap, w: Int, h: Int): Array<IntArray> =
        Array(h) { y ->
            IntArray(w) { x ->
                val p = bmp.getPixel(x, y)
                ((p shr 16 and 0xFF) * 299 + (p shr 8 and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
            }
        }
}
