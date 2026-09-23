package com.danngalann.livecaption.asr

import java.util.ArrayDeque

data class BufferedAudio(
    val startMs: Long,
    val endMs: Long,
    val pcm: ByteArray
)

class RollingAudioBuffer(
    private val capacityMs: Long,
    private val sampleRate: Int = 16_000
) {
    private val chunks = ArrayDeque<BufferedAudio>()
    var audioPositionMs: Long = 0
        private set

    @Synchronized
    fun append(pcm: ByteArray): BufferedAudio {
        val durationMs = pcm.size.toLong() * 1_000 / (sampleRate * 2)
        val chunk = BufferedAudio(audioPositionMs, audioPositionMs + durationMs, pcm.copyOf())
        audioPositionMs = chunk.endMs
        chunks.addLast(chunk)
        val oldestAllowed = audioPositionMs - capacityMs
        while (chunks.isNotEmpty() && chunks.first().endMs < oldestAllowed) {
            chunks.removeFirst()
        }
        return chunk
    }

    @Synchronized
    fun from(positionMs: Long): List<ByteArray> =
        chunks.asSequence()
            .filter { it.endMs >= positionMs }
            .map { it.pcm.copyOf() }
            .toList()

    @Synchronized
    fun clear() {
        chunks.clear()
        audioPositionMs = 0
    }
}

