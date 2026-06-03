package com.quiosquelitoral.gunboundguide

import android.graphics.Bitmap

object CharacterFinder {

    data class CharPosition(val x: Int, val nameY: Int, val groundY: Int)

    private const val BRIGHT = 185   // limiar de pixel "aceso" para texto de nome
    private const val MIN_RUN = 18   // mín de pixels consecutivos (letra de nome)
    private const val MAX_RUN = 160  // máx (evita pegar HUD inteiro)

    /**
     * Varre a porção inferior da tela (30%-82%) procurando a faixa horizontal
     * de pixels brilhantes que forma a etiqueta de nome acima do personagem.
     * Retorna a posição estimada do personagem ou null se não encontrado.
     */
    fun find(bmp: Bitmap): CharPosition? {
        val w = bmp.width
        val h = bmp.height

        // Extrai todos os pixels de uma vez (muito mais rápido que getPixel em loop)
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)

        val y1 = (h * 0.30).toInt()
        val y2 = (h * 0.82).toInt()

        var bestScore = 0
        var bestX = w / 2
        var bestY = -1

        // Amostra a cada 3 linhas para velocidade
        var y = y1
        while (y < y2) {
            var maxRun = 0
            var bestRunCenter = w / 2
            var run = 0
            var runStart = 0

            // Amostra a cada 2 colunas
            var x = 0
            while (x < w) {
                val p = pixels[y * w + x]
                val gray = ((p shr 16 and 0xFF) * 299 + (p shr 8 and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
                if (gray > BRIGHT) {
                    run++
                } else {
                    if (run > maxRun) {
                        maxRun = run
                        bestRunCenter = runStart + run
                    }
                    run = 0
                    runStart = x + 1
                }
                x += 2
            }
            if (run > maxRun) {
                maxRun = run
                bestRunCenter = runStart + run
            }

            if (maxRun in MIN_RUN..MAX_RUN && maxRun > bestScore) {
                bestScore = maxRun
                bestX = bestRunCenter
                bestY = y
            }
            y += 3
        }

        if (bestY < 0) return null

        // O tanque está logo abaixo da etiqueta do nome; chão ~70px mais baixo
        val groundY = (bestY + 70).coerceAtMost(h - 1)
        return CharPosition(bestX, bestY, groundY)
    }
}
