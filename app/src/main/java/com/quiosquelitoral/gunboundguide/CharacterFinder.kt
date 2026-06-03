package com.quiosquelitoral.gunboundguide

import android.graphics.Bitmap

object CharacterFinder {

    data class CharPosition(
        val labelX: Int,   // centro horizontal da etiqueta de nome
        val labelY: Int,   // y da etiqueta de nome
        val cannonX: Int,  // x estimado do canhão (à frente da etiqueta)
        val cannonY: Int,  // y estimado do canhão (abaixo da etiqueta)
        val groundY: Int   // y estimado do chão/plataforma
    )

    /**
     * Detecta a etiqueta de nome do personagem buscando faixas horizontais
     * com pixels de fundo azul/navy (cor do banner "NOITE4i20" no Gunbound Mobile).
     *
     * Critério de cor azul-dominante: b > 100 && b > r+30 && g > 40
     */
    fun find(bmp: Bitmap, facingRight: Boolean = true): CharPosition? {
        val w = bmp.width
        val h = bmp.height

        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)

        // Busca na área do jogo: 5% a 80% da altura (acima da barra de ação)
        val y1 = (h * 0.05).toInt()
        val y2 = (h * 0.80).toInt()

        var bestScore = 0
        var bestX = -1
        var bestY = -1

        var y = y1
        while (y < y2) {
            var maxRun = 0
            var runStart = 0
            var run = 0
            var bestRunCx = 0

            var x = 0
            while (x < w) {
                val p  = pixels[y * w + x]
                val r  = (p shr 16) and 0xFF
                val g  = (p shr 8)  and 0xFF
                val b  = p and 0xFF
                val gray = (r * 299 + g * 587 + b * 114) / 1000

                // Pixel de etiqueta: azul-dominante (fundo do banner) OU branco brilhante (texto)
                val isLabel = (b > 100 && b > r + 30 && g > 40) || gray > 190

                if (isLabel) {
                    run++
                } else {
                    if (run > maxRun) {
                        maxRun = run
                        bestRunCx = runStart + run / 2
                    }
                    run = 0
                    runStart = x + 1
                }
                x += 2   // amostra a cada 2px para velocidade
            }
            if (run > maxRun) {
                maxRun = run
                bestRunCx = runStart + run / 2
            }

            // Etiqueta de nome: 18-130 px de largura consecutiva
            if (maxRun in 18..130 && maxRun > bestScore) {
                bestScore = maxRun
                bestX = bestRunCx
                bestY = y
            }
            y += 3
        }

        if (bestX < 0) return null

        // Canhão fica abaixo e à frente da etiqueta
        val offsetX = if (facingRight) (w * 0.06).toInt() else -(w * 0.06).toInt()
        val cannonX = (bestX + offsetX).coerceIn(0, w - 1)
        val cannonY = (bestY + h * 0.08).toInt().coerceIn(0, h - 1)
        val groundY = (bestY + h * 0.18).toInt().coerceIn(0, h - 1)

        return CharPosition(bestX, bestY, cannonX, cannonY, groundY)
    }
}
