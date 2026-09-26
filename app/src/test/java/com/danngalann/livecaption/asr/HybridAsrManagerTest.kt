package com.danngalann.livecaption.asr

import com.danngalann.livecaption.audio.AudioSource
import com.danngalann.livecaption.audio.SoundEvent
import com.danngalann.livecaption.audio.SoundEventClassifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
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

    @Test
    fun `clear restarts only the provider and ignores late results`() {
        val audio = FakeAudioSource()
        val home = FakeProvider(ProviderId.HOME_SERVER)
        val manager = HybridAsrManager(
            audioRecorder = audio,
            providers = listOf(home, FakeProvider(ProviderId.ELEVENLABS), FakeProvider(ProviderId.MOONSHINE)),
            developmentController = NoOpDevelopmentController
        )
        val received = mutableListOf<TranscriptionEvent>()
        manager.start(ProviderEventListener(received::add))
        home.emit(TranscriptionEvent.Final("Borrado"))
        manager.clearTranscript()
        assertEquals(2, home.starts)
        home.emitToPrevious(TranscriptionEvent.Final("Borrado otra vez"))
        assertFalse(received.any { it is TranscriptionEvent.Final && it.text == "Borrado otra vez" })
        home.emit(TranscriptionEvent.Final("Nuevo"))
        assertTrue(received.any { it is TranscriptionEvent.Final && it.text == "Nuevo" })
        audio.emit(ByteArray(3_200))
        assertTrue(home.audio.isNotEmpty())
        manager.stop()
    }

    @Test
    fun `sound indicators run throughout listening and stop with transcription`() {
        val audio = FakeAudioSource()
        val home = FakeProvider(ProviderId.HOME_SERVER)
        val sounds = FakeSoundClassifier()
        val manager = HybridAsrManager(
            audioRecorder = audio,
            providers = listOf(home, FakeProvider(ProviderId.ELEVENLABS), FakeProvider(ProviderId.MOONSHINE)),
            developmentController = NoOpDevelopmentController,
            soundClassifier = sounds
        )
        manager.start(ProviderEventListener {})
        audio.emit(ByteArray(3_200))
        assertEquals(1, sounds.received)
        assertEquals(1, sounds.starts)
        manager.stop()
        audio.emit(ByteArray(3_200))
        assertEquals(1, sounds.received)
        assertEquals(1, sounds.stops)
        manager.start(ProviderEventListener {})
        audio.emit(ByteArray(3_200))
        assertEquals(2, sounds.received)
        assertEquals(2, sounds.starts)
        assertEquals(2, home.audio.size)
        manager.release()
        assertEquals(2, sounds.stops)
        assertEquals(1, sounds.closed)
    }
}

private class FakeSoundClassifier : SoundEventClassifier {
    override val events: StateFlow<SoundEvent?> = MutableStateFlow(null)
    override val error: StateFlow<String?> = MutableStateFlow(null)
    var received = 0
    var starts = 0
    var stops = 0
    var closed = 0
    override fun start() { starts++ }
    override fun onAudio(pcm: ByteArray) { received++ }
    override fun stop() { stops++ }
    override fun close() { closed++ }
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
    private val previousListeners = mutableListOf<ProviderEventListener>()

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
        listener?.let(previousListeners::add)
        listener = null
    }

    override fun checkAvailability(callback: (Boolean, Long?) -> Unit) {
        callback(true, 1)
    }

    fun emit(event: TranscriptionEvent) {
        listener?.onEvent(event)
    }

    fun emitToPrevious(event: TranscriptionEvent) {
        previousListeners.last().onEvent(event)
    }
}

private object NoOpDevelopmentController : DevelopmentController {
    override fun onSessionStarted() = Unit

    override fun shouldFail(provider: ProviderId, audioBytes: Int): Boolean = false
}
