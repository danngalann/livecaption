package com.danngalann.livecaption.asr

import com.danngalann.livecaption.audio.AudioSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HybridAsrManagerTest {
    @Test
    fun `home failure replays buffered audio to ElevenLabs`() {
        val audio = FakeAudioSource()
        val home = FakeProvider(ProviderId.HOME_SERVER)
        val elevenLabs = FakeProvider(ProviderId.ELEVENLABS)
        val moonshine = FakeProvider(ProviderId.MOONSHINE)
        val manager = HybridAsrManager(
            audioRecorder = audio,
            providers = listOf(home, elevenLabs, moonshine),
            developmentController = NoOpDevelopmentController,
            config = AsrConfig(providerSilenceTimeoutMs = Long.MAX_VALUE)
        )

        manager.start(ProviderEventListener {})
        val chunk = ByteArray(3_200) { 1 }
        audio.emit(chunk)
        home.emit(TranscriptionEvent.Partial("Podemos quedar"))
        home.emit(TranscriptionEvent.Error("disconnect", true))

        assertEquals(1, elevenLabs.starts)
        assertTrue(elevenLabs.audio.any { it.contentEquals(chunk) })
        assertEquals(ProviderId.ELEVENLABS, manager.diagnostics.value.activeProvider)
        manager.stop()
    }

    @Test
    fun `forced provider failure does not cascade`() {
        val audio = FakeAudioSource()
        val home = FakeProvider(ProviderId.HOME_SERVER)
        val elevenLabs = FakeProvider(ProviderId.ELEVENLABS)
        val moonshine = FakeProvider(ProviderId.MOONSHINE)
        val manager = HybridAsrManager(
            audioRecorder = audio,
            providers = listOf(home, elevenLabs, moonshine),
            developmentController = NoOpDevelopmentController
        )

        manager.setMode(ProviderMode.HOME_SERVER)
        manager.start(ProviderEventListener {})
        home.emit(TranscriptionEvent.Error("disconnect", true))

        assertEquals(0, elevenLabs.starts)
        assertEquals(ProviderConnectionState.FAILED, manager.diagnostics.value.connectionState)
        manager.stop()
    }
}

private class FakeAudioSource : AudioSource {
    private var callback: ((ByteArray) -> Unit)? = null

    override fun start(onAudio: (ByteArray) -> Unit) {
        callback = onAudio
    }

    override fun stop() {
        callback = null
    }

    fun emit(pcm: ByteArray) {
        callback?.invoke(pcm)
    }
}

private class FakeProvider(override val id: ProviderId) : SpeechRecognitionProvider {
    var starts = 0
    val audio = mutableListOf<ByteArray>()
    private var listener: ProviderEventListener? = null

    override fun prepare() = Unit

    override fun start(listener: ProviderEventListener) {
        starts++
        this.listener = listener
        listener.onEvent(TranscriptionEvent.State(ProviderConnectionState.READY))
    }

    override fun sendAudio(pcm: ByteArray) {
        audio += pcm.copyOf()
    }

    override fun stop() {
        listener = null
    }

    override fun checkAvailability(callback: (Boolean, Long?) -> Unit) {
        callback(true, 1)
    }

    fun emit(event: TranscriptionEvent) {
        listener?.onEvent(event)
    }
}

private object NoOpDevelopmentController : DevelopmentController {
    override fun onSessionStarted() = Unit

    override fun shouldFail(provider: ProviderId, audioBytes: Int): Boolean = false
}
