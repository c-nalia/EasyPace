package br.easypace.codec.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import br.easypace.codec.ui.theme.CodecColors
import kotlin.math.abs

/**
 * ============================================================================
 *  MEDIDOR DE DESVIO
 * ----------------------------------------------------------------------------
 *  Uma regua horizontal. O centro e a meta; a faixa verde no meio e a
 *  tolerancia. O cursor mostra onde voce esta:
 *
 *      esquerda = mais RAPIDO que a meta   |   direita = mais LENTO
 *
 *  A posicao e animada (150 ms) para o cursor deslizar em vez de pular, mesmo
 *  o motor atualizando 20x por segundo.
 * ============================================================================
 */
@Composable
fun PaceGauge(
    deltaSecPerKm: Double?,
    toleranceSecPerKm: Double,
    accent: Color,
    modifier: Modifier = Modifier,
    fullScaleSecPerKm: Double = 30.0
) {
    val target = if (deltaSecPerKm == null) 0f
    else (deltaSecPerKm / fullScaleSecPerKm).coerceIn(-1.0, 1.0).toFloat()

    val pos by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 150),
        label = "cursor"
    )

    val hasSignal = deltaSecPerKm != null

    Column(modifier) {
        Row(Modifier.fillMaxWidth()) {
            CodecLabel("< rapido", color = CodecColors.Cold.copy(alpha = 0.7f))
            Spacer(Modifier.weight(1f))
            CodecLabel("meta", color = CodecColors.Green)
            Spacer(Modifier.weight(1f))
            CodecLabel("lento >", color = CodecColors.Warm.copy(alpha = 0.7f))
        }
        Spacer(Modifier.height(6.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(46.dp)
        ) {
            val w = size.width
            val h = size.height
            val midY = h * 0.55f
            val halfBand = (toleranceSecPerKm / fullScaleSecPerKm).coerceIn(0.02, 1.0).toFloat()

            // --- trilho ---------------------------------------------------
            drawLine(
                CodecColors.GreenFaint,
                Offset(0f, midY), Offset(w, midY),
                strokeWidth = 2f
            )

            // --- marcas a cada 1/6 ----------------------------------------
            for (i in 0..6) {
                val x = w * i / 6f
                val alto = i == 3
                drawLine(
                    if (alto) CodecColors.Green else CodecColors.GreenFaint,
                    Offset(x, midY - (if (alto) 14f else 6f)),
                    Offset(x, midY + (if (alto) 14f else 6f)),
                    strokeWidth = if (alto) 3f else 2f
                )
            }

            // --- faixa de tolerancia --------------------------------------
            val bandLeft = w * (0.5f - halfBand / 2f)
            val bandRight = w * (0.5f + halfBand / 2f)
            drawRect(
                color = CodecColors.Green.copy(alpha = 0.16f),
                topLeft = Offset(bandLeft, midY - 12f),
                size = androidx.compose.ui.geometry.Size(bandRight - bandLeft, 24f)
            )

            // --- cursor ---------------------------------------------------
            if (hasSignal) {
                val x = w * (0.5f + pos / 2f)
                val cor = if (abs(pos) <= halfBand / 2f) CodecColors.Green else accent

                // haste
                drawLine(cor, Offset(x, midY - 20f), Offset(x, midY + 20f), strokeWidth = 4f)

                // triangulo apontando para baixo
                val tri = Path().apply {
                    moveTo(x, midY - 20f)
                    lineTo(x - 9f, midY - 34f)
                    lineTo(x + 9f, midY - 34f)
                    close()
                }
                drawPath(tri, cor)
            }
        }
    }
}
