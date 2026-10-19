package com.r0adkll.cassette.settings.ui

import com.r0adkll.cassette.common.compose.AppInfo
import com.r0adkll.cassette.common.compose.AppPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AboutTest {
  @Test
  fun percentEncodesReservedAndNonAscii() {
    assertEquals("a-b_c.d~e", "a-b_c.d~e".percentEncoded())
    assertEquals(
      "Settings%20%E2%86%92%20Diagnostics%0A%26%3F",
      "Settings → Diagnostics\n&?".percentEncoded(),
    )
  }

  @Test
  fun bugReportNamesTheBuild() {
    val url = AboutLinks.bugReport(AppInfo(version = "0.1.0", build = "40"), AppPlatform.Ios)
    assertTrue(url.startsWith("https://github.com/r0adkll/cassette/issues/new?labels=bug&body="))
    assertTrue(url.endsWith("Cassette%200.1.0%20%28build%2040%29%20on%20iOS"), url)
  }
}
