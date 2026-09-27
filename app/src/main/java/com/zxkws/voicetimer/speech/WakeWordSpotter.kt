package com.zxkws.voicetimer.speech

import android.content.res.AssetManager
import com.k2fsa.sherpa.onnx.*

class WakeWordSpotter(assets: AssetManager) : AutoCloseable {
    private val modelDir = "fastvoice/sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01"
    private val spotter: KeywordSpotter
    private val stream: OnlineStream

    init {
        val transducer = OnlineTransducerModelConfig(
            "$modelDir/encoder-epoch-99-avg-1-chunk-16-left-64.int8.onnx",
            "$modelDir/decoder-epoch-99-avg-1-chunk-16-left-64.int8.onnx",
            "$modelDir/joiner-epoch-99-avg-1-chunk-16-left-64.int8.onnx",
        )
        val model = OnlineModelConfig(
            transducer, OnlineParaformerModelConfig(), OnlineZipformer2CtcModelConfig(),
            OnlineNeMoCtcModelConfig(), OnlineToneCtcModelConfig(), "$modelDir/tokens.txt",
            1, false, "", "zipformer2", "", "",
        )
        spotter = KeywordSpotter(
            assets,
            KeywordSpotterConfig(
                FeatureConfig(16_000, 80, 0.0f), model, 4,
                "voice_timer/keywords.txt", 2.0f, 0.12f, 1,
            ),
        )
        stream = spotter.createStream()
    }

    @Synchronized
    fun accept(samples: ShortArray, size: Int): Boolean {
        if (size <= 0) return false
        stream.acceptWaveform(FloatArray(size) { samples[it] / 32768.0f }, 16_000)
        while (spotter.isReady(stream)) {
            spotter.decode(stream)
            val keyword = spotter.getResult(stream).keyword
            if (!keyword.isNullOrBlank()) {
                spotter.reset(stream)
                return keyword == "计时助手"
            }
        }
        return false
    }

    @Synchronized fun reset() = spotter.reset(stream)

    @Synchronized
    override fun close() {
        stream.release()
        spotter.release()
    }
}
