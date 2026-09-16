package br.easypace.codec.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.sin

/**
 * ============================================================================
 *  OSCILOSCOPIO DO CODEC
 * ----------------------------------------------------------------------------
 *  A "voz" na tela do codec. Aqui ela e alimentada pelos dados reais:
 *
 *      amplitude  <- intensidade do desvio (quanto mais fora do pace, maior)
 *      frequencia <- cadencia (passos por minuto)
 *      cor        <- estado atual
 *
 *  E so enfeite funcional: da para ver de relance se o ritmo esta "calmo".
 * ============================================================================
 */
@Composable
fun Waveform(
    amplitude: Float,
    cadenceSpm: Double,
    color: Color,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing)
        ),
        label = "phase"
    )

    // 2 ciclos parados; ate ~6 ciclos numa cadencia alta.
    val cycles = (2.0 + (cadenceSpm / 60.0)).coerceIn(2.0, 7.0).toFloat()
    val amp = amplitude.coerceIn(0.05f, 1f)

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val midY = h / 2f
        val steps = 96

        // linha de base
        drawLine(color.copy(alpha = 0.18f), Offset(0f, midY), Offset(w, midY), strokeWidth = 1.5f)

        val path = Path()
        for (i in 0..steps) {
            val t = i / steps.toFloat()
            // Envelope: a onda "morre" nas bordas, como num display real.
            val env = sin(t * PI).toFloat()
            val y = midY + sin(t * cycles * 2 * PI + phase).toFloat() *
                (h * 0.38f) * amp * env
            if (i == 0) path.moveTo(0f, y) else path.lineTo(t * w, y)
        }
        drawPath(
            path = path,
            color = color,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
        )
    }
}
