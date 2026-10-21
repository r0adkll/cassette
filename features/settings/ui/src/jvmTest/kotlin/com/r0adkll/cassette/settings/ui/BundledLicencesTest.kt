package com.r0adkll.cassette.settings.ui

import com.r0adkll.cassette.settings.ui.resources.Res
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** The bundled notices reach the module's resources (synced from thirdparty/licenses/). */
class BundledLicencesTest {
  @Test
  fun everyBundledNoticeLoads() = runTest {
    for (work in BUNDLED) {
      val text = Res.readBytes(work.file).decodeToString()
      assertTrue("License" in text || "LICENSE" in text, work.file)
    }
  }
}
