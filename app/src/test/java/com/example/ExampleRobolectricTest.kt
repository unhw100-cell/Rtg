package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.rust.RustBridge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Rustgram", appName)
  }

  @Test
  fun `rust jni core loads in robolectric`() {
    assertTrue(RustBridge.isNativeLibraryLoaded())
    val info = RustBridge.coreInfo()
    assertEquals("rustgram-core", info.engine)
    assertEquals(15, info.cratesCount)
  }
}
