package com.danngalann.livecaption.asr

interface SpeechRecognitionProvider {
    val id: ProviderId

    fun prepare()

    fun start(listener: ProviderEventListener)

    fun sendAudio(pcm: ByteArray)

    fun stop()

    fun checkAvailability(callback: (Boolean, Long?) -> Unit)
}

