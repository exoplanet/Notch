package com.exoplanet.notch

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.exoplanet.notch.dsp.NotchSettings
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * A quiet comparison tone. It does not pass through the notch filters.
 * Sine is a pure tone. Narrow is noise through a band about a tenth of an octave wide.
 */
class MatchPlayer(private val sampleRate: Int = SAMPLE_RATE) : AutoCloseable {
    @Volatile var frequencyHz: Int = 4_000
    @Volatile var narrow: Boolean = false

    private val lock = Any()
    private var thread: Thread? = null
    private var track: AudioTrack? = null
    @Volatile private var running = false

    val isPlaying: Boolean get() = running

    fun start(): Boolean {
        synchronized(lock) {
            if (running) return true
            val buffer = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            if (buffer <= 0) return false
            val created = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(buffer.coerceAtLeast(FRAMES * 4))
                .build()
            if (created.state != AudioTrack.STATE_INITIALIZED) {
                created.release()
                return false
            }
            track = created
            running = true
            thread = Thread({ render(created) }, "notch-match").also { it.start() }
            return true
        }
    }

    fun stop() {
        val ending = synchronized(lock) {
            running = false
            val current = thread
            thread = null
            current
        }
        ending?.join(1_000)
        synchronized(lock) {
            track?.run {
                runCatching {
                    pause()
                    flush()
                    release()
                }
            }
            track = null
        }
    }

    override fun close() {
        stop()
    }

    private fun render(audio: AudioTrack) {
        val band = NarrowBandpass(sampleRate)
        val random = Random(42)
        val frame = ShortArray(FRAMES * 2)
        var phase = 0.0
        audio.play()
        while (running) {
            val hz = frequencyHz.coerceIn(NotchSettings.MIN_HZ, NotchSettings.MAX_HZ)
            val useNoise = narrow
            if (useNoise) band.retune(hz)
            val step = 2.0 * PI * hz / sampleRate
            for (index in 0 until FRAMES) {
                val sample = if (useNoise) {
                    band.process((random.nextDouble() * 2.0 - 1.0) * NOISE_LEVEL)
                } else {
                    phase += step
                    if (phase > 2.0 * PI) phase -= 2.0 * PI
                    sin(phase) * SINE_LEVEL
                }
                val pcm = (sample * 32767.0).toInt().coerceIn(-32768, 32767).toShort()
                frame[index * 2] = pcm
                frame[index * 2 + 1] = pcm
            }
            if (!running) break
            audio.write(frame, 0, frame.size)
        }
    }

    companion object {
        const val SAMPLE_RATE = 48_000
        private const val FRAMES = 256
        private const val SINE_LEVEL = 0.06
        private const val NOISE_LEVEL = 0.35
    }
}

/** Constant-peak bandpass, about a tenth of an octave wide. */
internal class NarrowBandpass(private val sampleRate: Int) {
    private var b0 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0
    private var x1 = 0.0
    private var x2 = 0.0
    private var y1 = 0.0
    private var y2 = 0.0
    private var tunedHz = -1

    fun retune(frequencyHz: Int) {
        if (frequencyHz == tunedHz) return
        tunedHz = frequencyHz
        val hz = frequencyHz.coerceIn(1, sampleRate / 2 - 1).toDouble()
        val width = hz * (2.0.pow(0.05) - 2.0.pow(-0.05))
        val q = (hz / width).coerceAtLeast(0.5)
        val w0 = 2.0 * PI * hz / sampleRate
        val alpha = sin(w0) / (2.0 * q)
        val a0 = 1.0 + alpha
        b0 = alpha / a0
        b2 = -alpha / a0
        a1 = (-2.0 * cos(w0)) / a0
        a2 = (1.0 - alpha) / a0
    }

    fun process(input: Double): Double {
        val output = b0 * input + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1
        x1 = input
        y2 = y1
        y1 = output
        return output
    }
}
