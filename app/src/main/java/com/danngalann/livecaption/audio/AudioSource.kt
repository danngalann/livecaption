package com.danngalann.livecaption.audio

interface AudioSource {
    fun start(onAudio: (ByteArray) -> Unit)

    fun stop()
}

