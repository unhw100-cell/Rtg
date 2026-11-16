package com.example

import com.example.data.repository.GeminiBotPresets
import com.example.data.repository.GeminiModels
import com.example.ui.i18n.StringsProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun stringsProvider_returnsCorrectLocalization() {
    val ru = StringsProvider.forLang("ru")
    val en = StringsProvider.forLang("en")
    assertEquals("Войти через Google", ru.signInWithGoogle)
    assertEquals("Sign in with Google", en.signInWithGoogle)
  }

  @Test
  fun geminiBotPresets_useSupportedModelsAndNonEmptyInstructions() {
    assertTrue(GeminiBotPresets.all.isNotEmpty())
    GeminiBotPresets.all.forEach { preset ->
      assertTrue(preset.defaultModel in GeminiModels.availableModels)
      assertTrue(preset.systemInstruction.isNotBlank())
    }
  }
}
