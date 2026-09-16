package br.easypace.codec

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.easypace.codec.audio.AudioCoach
import br.easypace.codec.service.RunController
import br.easypace.codec.ui.CodecScreen
import br.easypace.codec.ui.SettingsDialog
import br.easypace.codec.ui.theme.CodecTheme

/**
 * ============================================================================
 *  ACTIVITY — so faz tres coisas
 * ----------------------------------------------------------------------------
 *   1. pedir permissoes
 *   2. desenhar a tela a partir dos StateFlows do [RunController]
 *   3. mandar comandos (iniciar/parar/mudar meta) de volta para o controller
 *
 *  Nenhuma logica de corrida mora aqui: se a Activity morrer, o servico segue.
 * ============================================================================
 */
class MainActivity : ComponentActivity() {

    /** AudioCoach proprio da tela, usado so nos botoes "testar som". */
    private var previewCoach: AudioCoach? = null

    private var permissoesOk = mutableStateOf(false)

    private val pedirPermissoes =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            permissoesOk.value = temPermissaoEssencial()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // O botao de volume do aparelho passa a mexer no volume de MIDIA
        // enquanto esta tela estiver aberta — que e o canal onde os bipes
        // saem. Sem isto, o botao mexeria no volume de toque e voce ajustaria
        // o canal errado achando que ajustou o certo.
        volumeControlStream = AudioManager.STREAM_MUSIC

        RunController.ensureLoaded(this)
        previewCoach = AudioCoach()
        RunController.setAudioReadyFromUi(previewCoach?.isReady ?: false)
        permissoesOk.value = temPermissaoEssencial()

        setContent {
            CodecTheme {
                val snapshot by RunController.snapshot.collectAsStateWithLifecycle()
                val target by RunController.targetSecPerKm.collectAsStateWithLifecycle()
                val active by RunController.active.collectAsStateWithLifecycle()
                val sensorMode by RunController.sensorMode.collectAsStateWithLifecycle()
                val audioOk by RunController.audioReady.collectAsStateWithLifecycle()

                var mostrarAjustes by remember { mutableStateOf(false) }

                CodecScreen(
                    snapshot = snapshot,
                    targetSecPerKm = target,
                    active = active,
                    sensorMode = sensorMode,
                    permissionsOk = permissoesOk.value,
                    audioReady = audioOk,
                    onChangeTarget = { RunController.setTarget(this, it) },
                    onToggleRun = {
                        if (active) RunController.stop(this) else RunController.start(this)
                    },
                    onRequestPermissions = { solicitar() },
                    onOpenSettings = { mostrarAjustes = true }
                )

                if (mostrarAjustes) {
                    SettingsDialog(
                        onDismiss = { mostrarAjustes = false },
                        onPreview = { cue -> previewCoach?.preview(cue) },
                        onPersist = { RunController.persistSettings(this) }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        permissoesOk.value = temPermissaoEssencial()
    }

    override fun onDestroy() {
        previewCoach?.release()
        previewCoach = null
        super.onDestroy()
    }

    // =======================================================================
    //  PERMISSOES
    // =======================================================================

    /** Sem localizacao nao ha pace real — e a unica obrigatoria. */
    private fun temPermissaoEssencial(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun solicitar() {
        val lista = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            lista += Manifest.permission.ACTIVITY_RECOGNITION
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            lista += Manifest.permission.POST_NOTIFICATIONS
        }
        pedirPermissoes.launch(lista.toTypedArray())
    }
}
