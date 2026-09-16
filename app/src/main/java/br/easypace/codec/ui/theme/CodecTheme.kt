package br.easypace.codec.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * ============================================================================
 *  PALETA DO CODEC
 * ----------------------------------------------------------------------------
 *  Fosforo verde sobre preto, com dois desvios de cor para os alertas.
 *  Trocar a identidade visual do app inteiro = trocar as cores daqui.
 * ============================================================================
 */
object CodecColors {
    /** Fundo: preto com um leve verde, como um CRT desligado mas quente. */
    val Background = Color(0xFF04120A)
    val PanelFill = Color(0xFF07200F)

    /** Verde de fosforo principal. */
    val Green = Color(0xFF7DF9A6)
    /** Verde apagado: molduras, textos secundarios. */
    val GreenDim = Color(0xFF35815A)
    /** Verde quase invisivel: grades e trilhos. */
    val GreenFaint = Color(0xFF1B4630)

    /** RAPIDO DEMAIS — ciano frio, "segure". */
    val Cold = Color(0xFF9FE8FF)
    /** LENTO DEMAIS — ambar quente, "acelere". */
    val Warm = Color(0xFFFFC24B)
    /** Alerta critico / parado. */
    val Muted = Color(0xFF6E7B72)

    /** Linhas de varredura sobrepostas na tela toda. */
    val Scanline = Color(0x14000000)
}

/** Tudo em monoespacado, com espacamento de letras estilo terminal. */
private val CodecTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 76.sp,
        letterSpacing = (-2).sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        letterSpacing = 2.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        letterSpacing = 1.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
        letterSpacing = 1.5.sp
    )
)

@Composable
fun CodecTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // O codec e sempre escuro — nao existe "modo claro" num radio militar.
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = CodecColors.Green,
            onPrimary = CodecColors.Background,
            background = CodecColors.Background,
            onBackground = CodecColors.Green,
            surface = CodecColors.PanelFill,
            onSurface = CodecColors.Green
        ),
        typography = CodecTypography,
        content = content
    )
}
