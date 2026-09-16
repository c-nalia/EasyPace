package br.easypace.codec.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.easypace.codec.core.Config
import br.easypace.codec.core.PaceMath
import br.easypace.codec.core.PaceSource
import br.easypace.codec.core.PaceState
import br.easypace.codec.core.RunSnapshot
import br.easypace.codec.ui.components.CodecButton
import br.easypace.codec.ui.components.CodecLabel
import br.easypace.codec.ui.components.CodecPanel
import br.easypace.codec.ui.components.PaceGauge
import br.easypace.codec.ui.components.ScanlineOverlay
import br.easypace.codec.ui.components.Waveform
import br.easypace.codec.ui.theme.CodecColors
import kotlin.math.abs

/**
 * ============================================================================
 *  TELA PRINCIPAL — o "codec"
 * ----------------------------------------------------------------------------
 *  Esta funcao e PURA em relacao ao app: recebe o snapshot e callbacks, e nao
 *  conhece servico nem sensores. Isso deixa a tela facil de testar e de trocar.
 *
 *  Ordem visual:
 *      cabecalho (frequencia)  ->  paineis VOCE/META  ->  pace gigante  ->
 *      medidor de desvio  ->  caixa de mensagem  ->  numeros  ->  controles
 * ============================================================================
 */
@Composable
fun CodecScreen(
    snapshot: RunSnapshot,
    targetSecPerKm: Int,
    active: Boolean,
    sensorMode: String,
    permissionsOk: Boolean,
    audioReady: Boolean,
    onChangeTarget: (Int) -> Unit,
    onToggleRun: () -> Unit,
    onRequestPermissions: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val accent = accentFor(snapshot.state)

    // Pulsacao suave usada nas bordas quando ha alerta ativo.
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulseAlpha"
    )
    val alerta = snapshot.state == PaceState.RAPIDO_DEMAIS || snapshot.state == PaceState.LENTO_DEMAIS
    val borda = if (alerta) accent.copy(alpha = pulse) else accent.copy(alpha = 0.55f)

    Box(
        Modifier
            .fillMaxSize()
            .background(CodecColors.Background)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Header(sensorMode, snapshot)

            // Aviso so aparece quando o motor de audio nao subiu. Melhor um
            // alerta feio na tela do que voce descobrir na rua que o app esta
            // mudo.
            if (!audioReady) {
                CodecPanel(Modifier.fillMaxWidth(), accent = CodecColors.Warm, contentPadding = 10.dp) {
                    Column {
                        CodecLabel("audio indisponivel", color = CodecColors.Warm)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "o aparelho recusou criar o canal de som",
                            color = CodecColors.GreenDim,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // ---------- paineis VOCE / META --------------------------------
            Row(
                Modifier.fillMaxWidth().height(104.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CodecPanel(Modifier.weight(1f).fillMaxHeight(), accent = borda) {
                    Column(Modifier.fillMaxSize()) {
                        CodecLabel("voce", color = accent)
                        Spacer(Modifier.height(4.dp))
                        Waveform(
                            amplitude = waveAmplitude(snapshot),
                            cadenceSpm = snapshot.cadenceSpm,
                            color = accent,
                            modifier = Modifier.fillMaxWidth().weight(1f)
                        )
                        Text(
                            "${snapshot.cadenceSpm.toInt()} ppm",
                            color = CodecColors.GreenDim,
                            fontSize = 11.sp
                        )
                    }
                }
                CodecPanel(Modifier.weight(1f).fillMaxHeight(), accent = CodecColors.GreenDim) {
                    Column(
                        Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        CodecLabel("meta")
                        Text(
                            PaceMath.formatPace(targetSecPerKm.toDouble()),
                            color = CodecColors.Green,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "+/- ${Config.toleranceSecPerKm.toInt()} s/km",
                            color = CodecColors.GreenDim,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // ---------- leitura gigante ------------------------------------
            CodecPanel(Modifier.fillMaxWidth(), accent = borda, contentPadding = 14.dp) {
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CodecLabel("pace atual", color = accent)
                        Spacer(Modifier.weight(1f))
                        CodecLabel(fonteTexto(snapshot), color = CodecColors.GreenDim)
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            PaceMath.formatPace(snapshot.paceSecPerKm),
                            color = accent,
                            fontSize = 72.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-3).sp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "min/km",
                            color = CodecColors.GreenDim,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 14.dp)
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            PaceMath.formatDelta(snapshot.deltaSecPerKm),
                            color = accent,
                            fontSize = 22.sp,
                            modifier = Modifier.padding(bottom = 14.dp)
                        )
                    }
                }
            }

            // ---------- medidor --------------------------------------------
            CodecPanel(Modifier.fillMaxWidth(), accent = CodecColors.GreenDim) {
                PaceGauge(
                    deltaSecPerKm = snapshot.deltaSecPerKm,
                    toleranceSecPerKm = Config.toleranceSecPerKm,
                    accent = accent,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ---------- caixa de mensagem -----------------------------------
            CodecPanel(Modifier.fillMaxWidth().height(74.dp), accent = borda) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                    Text(
                        mensagem(snapshot.state, permissionsOk, active),
                        color = accent,
                        fontSize = 16.sp,
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        submensagem(snapshot.state),
                        color = CodecColors.GreenDim,
                        fontSize = 11.sp
                    )
                }
            }

            // ---------- numeros ---------------------------------------------
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Stat(Modifier.weight(1f), "tempo", PaceMath.formatClock(snapshot.elapsedMs))
                Stat(Modifier.weight(1f), "dist", PaceMath.formatDistance(snapshot.distanceM))
                Stat(Modifier.weight(1f), "medio", PaceMath.formatPace(snapshot.averagePaceSecPerKm))
            }

            // ---------- ajuste da meta --------------------------------------
            CodecPanel(Modifier.fillMaxWidth(), accent = CodecColors.GreenDim) {
                Column(Modifier.fillMaxWidth()) {
                    CodecLabel("ajustar meta (segundos por km)")
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CodecButton("-10", { onChangeTarget(targetSecPerKm + 10) }, Modifier.weight(1f), fontSize = 13.sp)
                        CodecButton("-5", { onChangeTarget(targetSecPerKm + 5) }, Modifier.weight(1f), fontSize = 13.sp)
                        CodecButton("+5", { onChangeTarget(targetSecPerKm - 5) }, Modifier.weight(1f), fontSize = 13.sp)
                        CodecButton("+10", { onChangeTarget(targetSecPerKm - 10) }, Modifier.weight(1f), fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "  -  = mais lento          +  = mais rapido",
                        color = CodecColors.GreenFaint,
                        fontSize = 10.sp
                    )
                }
            }

            // ---------- controles -------------------------------------------
            if (!permissionsOk) {
                CodecButton(
                    "conceder permissoes",
                    onRequestPermissions,
                    Modifier.fillMaxWidth(),
                    accent = CodecColors.Warm
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CodecButton(
                    text = if (active) "encerrar" else "iniciar",
                    onClick = onToggleRun,
                    modifier = Modifier.weight(2f),
                    enabled = permissionsOk,
                    accent = if (active) CodecColors.Warm else CodecColors.Green
                )
                CodecButton("ajustes", onOpenSettings, Modifier.weight(1f), fontSize = 13.sp)
            }

            Spacer(Modifier.height(6.dp))
        }

        // As scanlines ficam por cima de tudo, sem capturar toques.
        ScanlineOverlay()
    }
}

// ===========================================================================
//  PEDACOS MENORES
// ===========================================================================

@Composable
private fun Header(sensorMode: String, s: RunSnapshot) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("<", color = CodecColors.GreenDim, fontSize = 18.sp)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                Config.CODEC_FREQUENCY,
                color = CodecColors.Green,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp
            )
            CodecLabel("easypace  -  canal aberto")
        }
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            CodecLabel(sensorMode)
            CodecLabel(
                s.gpsAccuracyM?.let { "gps ${it.toInt()} m" } ?: "gps --",
                color = if (s.gpsAccuracyM != null) CodecColors.Green else CodecColors.Muted
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(">", color = CodecColors.GreenDim, fontSize = 18.sp)
    }
}

@Composable
private fun Stat(modifier: Modifier, label: String, value: String) {
    CodecPanel(modifier, accent = CodecColors.GreenDim, contentPadding = 8.dp) {
        Column {
            CodecLabel(label)
            Spacer(Modifier.height(2.dp))
            Text(value, color = CodecColors.Green, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Cor de destaque de cada estado. Trocar aqui muda o app inteiro. */
fun accentFor(state: PaceState): Color = when (state) {
    PaceState.RAPIDO_DEMAIS -> CodecColors.Cold
    PaceState.LENTO_DEMAIS -> CodecColors.Warm
    PaceState.NO_PACE -> CodecColors.Green
    PaceState.AQUECENDO -> CodecColors.GreenDim
    PaceState.PARADO -> CodecColors.Muted
}

private fun mensagem(state: PaceState, permissionsOk: Boolean, active: Boolean): String = when {
    !permissionsOk -> "PERMISSOES PENDENTES"
    !active -> "CANAL EM ESPERA"
    state == PaceState.AQUECENDO -> "CALIBRANDO SENSORES..."
    state == PaceState.PARADO -> "SEM MOVIMENTO DETECTADO"
    state == PaceState.NO_PACE -> "RITMO CONFIRMADO"
    state == PaceState.RAPIDO_DEMAIS -> "ACIMA DA META - SEGURE"
    else -> "ABAIXO DA META - ACELERE"
}

private fun submensagem(state: PaceState): String = when (state) {
    PaceState.AQUECENDO -> "aguardando o gps travar a posicao"
    PaceState.PARADO -> "os alertas voltam quando voce se mover"
    PaceState.NO_PACE -> "sinal de meta a cada 10 s"
    PaceState.RAPIDO_DEMAIS -> "voce esta gastando reserva cedo demais"
    PaceState.LENTO_DEMAIS -> "aumente a cadencia, nao a passada"
}

/**
 * Mostra de onde veio o numero E se ele ja e a leitura estavel.
 * "media 12s" = pace medio da janela deslizante (confiavel).
 * "instantaneo" = ainda nos primeiros segundos, o numero vai balancar.
 */
private fun fonteTexto(s: RunSnapshot): String {
    val fonte = when (s.source) {
        PaceSource.NENHUMA -> "sem sinal"
        PaceSource.GPS -> "gps"
        PaceSource.CADENCIA -> "cadencia"
        PaceSource.FUSAO -> "gps+cadencia"
    }
    val modo = if (s.paceFromWindow) "media ${Config.paceWindowSec.toInt()}s" else "instantaneo"
    return "$fonte - $modo"
}

/** Amplitude da onda: 0.15 parado, ate 1.0 bem fora do pace. */
private fun waveAmplitude(s: RunSnapshot): Float {
    if (!s.running || s.paceSecPerKm == null) return 0.12f
    val d = abs(s.deltaSecPerKm ?: 0.0)
    return (0.35 + (d / 30.0).coerceAtMost(1.0) * 0.65).toFloat()
}
