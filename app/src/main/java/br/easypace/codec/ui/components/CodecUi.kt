package br.easypace.codec.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.easypace.codec.ui.theme.CodecColors

/**
 * ============================================================================
 *  PECAS VISUAIS REUTILIZAVEIS DO CODEC
 * ----------------------------------------------------------------------------
 *  Tudo aqui e generico: molduras, linhas de varredura, botoes. A tela em si
 *  (CodecScreen) so monta essas pecas.
 * ============================================================================
 */

/**
 * Linhas horizontais escuras sobre o conteudo — o efeito CRT.
 * @param spacing distancia entre linhas (menor = mais denso)
 */
fun Modifier.scanlines(
    spacing: Dp = 3.dp,
    color: Color = CodecColors.Scanline
): Modifier = this.drawWithContent {
    drawContent()
    val step = spacing.toPx().coerceAtLeast(1f)
    var y = 0f
    while (y < size.height) {
        drawLine(
            color = color,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1f
        )
        y += step
    }
}

/** Cantos em "L" desenhados por cima da moldura, como nas HUDs do jogo. */
fun Modifier.cornerBrackets(
    color: Color = CodecColors.Green,
    length: Dp = 10.dp,
    thickness: Dp = 2.dp
): Modifier = this.drawWithContent {
    drawContent()
    val l = length.toPx()
    val t = thickness.toPx()
    val w = size.width
    val h = size.height
    // superior esquerdo
    drawLine(color, Offset(0f, 0f), Offset(l, 0f), t)
    drawLine(color, Offset(0f, 0f), Offset(0f, l), t)
    // superior direito
    drawLine(color, Offset(w, 0f), Offset(w - l, 0f), t)
    drawLine(color, Offset(w, 0f), Offset(w, l), t)
    // inferior esquerdo
    drawLine(color, Offset(0f, h), Offset(l, h), t)
    drawLine(color, Offset(0f, h), Offset(0f, h - l), t)
    // inferior direito
    drawLine(color, Offset(w, h), Offset(w - l, h), t)
    drawLine(color, Offset(w, h), Offset(w, h - l), t)
}

/** Caixa padrao do codec: fundo, moldura fina e cantos marcados. */
@Composable
fun CodecPanel(
    modifier: Modifier = Modifier,
    accent: Color = CodecColors.GreenDim,
    contentPadding: Dp = 12.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .background(CodecColors.PanelFill)
            .border(1.dp, accent.copy(alpha = 0.55f))
            .cornerBrackets(accent)
            .padding(contentPadding),
        content = content
    )
}

/** Rotulo pequeno em caixa alta, usado como titulo de cada painel. */
@Composable
fun CodecLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = CodecColors.GreenDim
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        color = color,
        fontSize = 10.sp,
        letterSpacing = 2.sp,
        maxLines = 1
    )
}

/** Botao retangular estilo terminal. */
@Composable
fun CodecButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Color = CodecColors.Green,
    fontSize: androidx.compose.ui.unit.TextUnit = 16.sp
) {
    val c = if (enabled) accent else CodecColors.Muted
    Box(
        modifier = modifier
            .background(c.copy(alpha = 0.10f))
            .border(1.5.dp, c.copy(alpha = if (enabled) 0.9f else 0.4f))
            .cornerBrackets(c, length = 8.dp, thickness = 2.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.uppercase(),
            color = c,
            fontSize = fontSize,
            letterSpacing = 3.sp,
            textAlign = TextAlign.Center
        )
    }
}

/** Camada que cobre a tela inteira com as scanlines (fica por cima de tudo). */
@Composable
fun ScanlineOverlay(spacing: Dp = 3.dp) {
    Box(Modifier.fillMaxSize().scanlines(spacing))
}
