package br.easypace.codec.ui

import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.easypace.codec.audio.Cue
import br.easypace.codec.core.Config
import br.easypace.codec.ui.components.CodecButton
import br.easypace.codec.ui.components.CodecLabel
import br.easypace.codec.ui.theme.CodecColors

/**
 * ============================================================================
 *  AJUSTES
 * ----------------------------------------------------------------------------
 *  Todo controle aqui escreve direto nas `var` de [Config] e, ao fechar, chama
 *  `onPersist()` para gravar em disco.
 *
 *  Para adicionar um ajuste novo:
 *      1. crie a `var` em Config;
 *      2. leia/grave em Prefs;
 *      3. copie um bloco `Ajuste(...)` abaixo.
 * ============================================================================
 */
@Composable
fun SettingsDialog(
    onDismiss: () -> Unit,
    onPreview: (Cue) -> Unit,
    onPersist: () -> Unit
) {
    var janela by remember { mutableFloatStateOf(Config.paceWindowSec.toFloat()) }
    var tolerancia by remember { mutableFloatStateOf(Config.toleranceSecPerKm.toFloat()) }
    var histerese by remember { mutableFloatStateOf(Config.hysteresisSecPerKm.toFloat()) }
    var dwell by remember { mutableFloatStateOf(Config.minStateDwellMs / 1000f) }
    var tau by remember { mutableFloatStateOf(Config.speedFilterTauS.toFloat()) }
    var aquecimento by remember { mutableFloatStateOf(Config.warmupMs / 1000f) }
    var repMeta by remember { mutableFloatStateOf(Config.onPaceRepeatMs / 1000f) }
    var repCorrecao by remember { mutableFloatStateOf(Config.correctionRepeatMs / 1000f) }
    var volume by remember { mutableFloatStateOf(Config.audioVolume) }
    var inverter by remember { mutableStateOf(Config.invertAlertTones) }

    AlertDialog(
        onDismissRequest = {
            onPersist()
            onDismiss()
        },
        containerColor = CodecColors.PanelFill,
        titleContentColor = CodecColors.Green,
        textContentColor = CodecColors.Green,
        title = { Text("AJUSTES", color = CodecColors.Green, fontSize = 18.sp, letterSpacing = 3.sp) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // ---- diagnostico de audio -------------------------------
                // Os bipes saem no canal de MIDIA (o mesmo da musica). Se este
                // numero estiver em 0%, nenhum som vai sair por mais correto
                // que o app esteja — e o motivo mais comum de "nao toca nada".
                val ctx = LocalContext.current
                val am = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
                val volMidia = remember {
                    val atual = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                    val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                    atual * 100 / max
                }
                CodecLabel("volume de midia do aparelho", color = CodecColors.Green)
                Text(
                    if (volMidia == 0) "0% — SUBA O VOLUME, nada vai tocar"
                    else "$volMidia%  (os bipes saem neste canal)",
                    color = if (volMidia == 0) CodecColors.Warm else CodecColors.GreenDim,
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(8.dp))

                // ---- o ajuste mais importante para a estabilidade -------
                Ajuste(
                    "janela do pace",
                    "${janela.toInt()} s",
                    janela,
                    Config.MIN_PACE_WINDOW_SEC.toFloat()..Config.MAX_PACE_WINDOW_SEC.toFloat()
                ) {
                    janela = it; Config.paceWindowSec = it.toDouble()
                }
                Text(
                    "maior = numero mais firme, avisa mais tarde  |  " +
                        "menor = reage rapido, numero balanca mais",
                    color = CodecColors.GreenFaint,
                    fontSize = 10.sp
                )
                Spacer(Modifier.height(6.dp))

                Ajuste("tolerancia", "${tolerancia.toInt()} s/km", tolerancia, 2f..30f) {
                    tolerancia = it; Config.toleranceSecPerKm = it.toDouble()
                }
                Ajuste("histerese", "${histerese.toInt()} s/km", histerese, 0f..12f) {
                    histerese = it; Config.hysteresisSecPerKm = it.toDouble()
                }
                Ajuste("confirmacao do estado", "%.1f s".format(dwell), dwell, 0f..8f) {
                    dwell = it; Config.minStateDwellMs = (it * 1000).toLong()
                }
                Ajuste("suavizacao (tau)", "%.1f s".format(tau), tau, 0.5f..8f) {
                    tau = it; Config.speedFilterTauS = it.toDouble()
                }
                Ajuste("silencio inicial", "${aquecimento.toInt()} s", aquecimento, 0f..30f) {
                    aquecimento = it; Config.warmupMs = (it * 1000).toLong()
                }
                Ajuste("repetir 'meta'", "${repMeta.toInt()} s", repMeta, 3f..60f) {
                    repMeta = it; Config.onPaceRepeatMs = (it * 1000).toLong()
                }
                Ajuste("repetir correcao", "${repCorrecao.toInt()} s", repCorrecao, 2f..20f) {
                    repCorrecao = it; Config.correctionRepeatMs = (it * 1000).toLong()
                }
                Ajuste("volume", "${(volume * 100).toInt()}%", volume, 0f..1f) {
                    volume = it; Config.audioVolume = it
                }

                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        CodecLabel("inverter agudo/grave", color = CodecColors.Green)
                        Text(
                            if (inverter) "agudo = lento  |  grave = rapido"
                            else "agudo = rapido  |  grave = lento",
                            color = CodecColors.GreenDim,
                            fontSize = 10.sp
                        )
                    }
                    Switch(
                        checked = inverter,
                        onCheckedChange = { inverter = it; Config.invertAlertTones = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = CodecColors.Background,
                            checkedTrackColor = CodecColors.Green,
                            uncheckedThumbColor = CodecColors.GreenDim,
                            uncheckedTrackColor = CodecColors.PanelFill
                        )
                    )
                }

                Spacer(Modifier.height(8.dp))
                CodecLabel("testar sons")
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CodecButton("agudo", { onPreview(Cue.AGUDO) }, Modifier.weight(1f),
                        accent = CodecColors.Cold, fontSize = 12.sp)
                    CodecButton("meta", { onPreview(Cue.META) }, Modifier.weight(1f),
                        accent = CodecColors.Green, fontSize = 12.sp)
                    CodecButton("grave", { onPreview(Cue.GRAVE) }, Modifier.weight(1f),
                        accent = CodecColors.Warm, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            CodecButton("fechar", { onPersist(); onDismiss() }, fontSize = 13.sp)
        }
    )
}

/** Uma linha: rotulo + valor + slider, no visual do codec. */
@Composable
private fun Ajuste(
    label: String,
    valor: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            CodecLabel(label, color = CodecColors.Green)
            Spacer(Modifier.weight(1f))
            Text(valor, color = CodecColors.Green, fontSize = 11.sp)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = CodecColors.Green,
                activeTrackColor = CodecColors.Green,
                inactiveTrackColor = CodecColors.GreenFaint
            )
        )
    }
}
