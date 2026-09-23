package com.danngalann.livecaption.asr

interface DevelopmentController {
    fun onSessionStarted()

    fun shouldFail(provider: ProviderId, audioBytes: Int): Boolean
}

