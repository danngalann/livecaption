package com.danngalann.livecaption

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.danngalann.livecaption.audio.MediaPipeSoundClassifier
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SoundClassifierInstrumentedTest {
    @Test
    fun bundledModelClassifiesSharedPcmWithoutOpeningMicrophone() {
        val classifier = MediaPipeSoundClassifier(
            InstrumentationRegistry.getInstrumentation().targetContext
        )
        try {
            classifier.start()
            repeat(3) { classifier.onAudio(ByteArray(32_000)) }
            for (attempt in 0 until 40) {
                if (classifier.classificationsCompleted.get() > 0 || classifier.error.value != null) {
                    break
                }
                Thread.sleep(250)
            }
            assertNull(classifier.error.value)
            assertTrue(classifier.classificationsCompleted.get() > 0)
        } finally {
            classifier.close()
        }
    }
}
