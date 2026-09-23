package com.danngalann.livecaption.asr

data class AsrConfig(
    val rollingBufferMs: Long = 10_000,
    val replayPreRollMs: Long = 400,
    val providerSilenceTimeoutMs: Long = 4_000,
    val maxBacklogMs: Long = 2_500,
    val slowResultLimit: Int = 3,
    val homeProbeIntervalMs: Long = 5_000,
    val homeRecoveryHealthyMs: Long = 15_000
)

