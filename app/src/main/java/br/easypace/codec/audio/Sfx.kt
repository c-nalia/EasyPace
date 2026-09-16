package br.easypace.codec.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log

/**
 * ============================================================================
 *  UM EFEITO SONORO PRONTO PARA TOCAR
 * ----------------------------------------------------------------------------
 *  Usa AudioTrack em MODE_STATIC: o buffer PCM inteiro fica carregado na
 *  memoria do driver de audio, entao `play()` dispara em poucos milissegundos.
 *
 *  POR QUE USAGE_MEDIA E NAO USAGE_ASSISTANCE_SONIFICATION
 *  -------------------------------------------------------
 *  A escolha do "usage" decide em qual canal de volume o som sai:
 *
 *    USAGE_ASSISTANCE_SONIFICATION -> STREAM_SYSTEM
 *        Parece a opcao certa ("aviso funcional"), mas o canal de sistema e
 *        silenciado pelo modo Nao Perturbe e pelo perfil silencioso/vibrar.
 *        Resultado: o app parece quebrado, sem nenhum erro em lugar nenhum.
 *
 *    USAGE_MEDIA -> STREAM_MUSIC   <== o que usamos
 *        Mesmo canal da sua musica. Nao e cortado pelo Nao Perturbe, toca por
 *        cima do que estiver tocando, e o volume e o mesmo botao que voce ja
 *        usa para a musica durante a corrida.
 *
 *  CONTENT_TYPE_SONIFICATION continua declarado: ele nao muda o canal, so
 *  informa ao sistema que isto e um bipe curto, e nao uma faixa de musica.
 * ============================================================================
 */
class Sfx(pcm: ShortArray, sampleRate: Int = ToneSynth.SAMPLE_RATE) {

    private val sizeBytes = (pcm.size * 2).coerceAtLeast(64)

    private val track: AudioTrack? = criar(pcm, sampleRate)

    /** false quando o aparelho recusou criar o AudioTrack (a UI avisa). */
    val isReady: Boolean get() = track != null

    private fun criar(pcm: ShortArray, sampleRate: Int): AudioTrack? = try {
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(sizeBytes)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        val escritos = t.write(pcm, 0, pcm.size)
        if (t.state != AudioTrack.STATE_INITIALIZED || escritos < pcm.size) {
            Log.e(TAG, "AudioTrack nao ficou pronto: state=${t.state}, escritos=$escritos/${pcm.size}")
            t.release()
            null
        } else {
            t
        }
    } catch (e: Exception) {
        Log.e(TAG, "Falha ao criar AudioTrack", e)
        null
    }

    /** Toca do inicio. Se ja estiver tocando, reinicia. */
    fun play(volume: Float) {
        val t = track ?: return
        try {
            t.setVolume(volume.coerceIn(0f, 1f))
            if (t.playState != AudioTrack.PLAYSTATE_STOPPED) t.stop()
            t.reloadStaticData()
            t.play()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "play() ignorado: ${e.message}")
        }
    }

    fun release() {
        try {
            track?.stop()
        } catch (_: IllegalStateException) {
        }
        track?.release()
    }

    private companion object {
        const val TAG = "Sfx"
    }
}
