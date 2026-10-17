package com.r0adkll.cassette.capture.impl

import com.r0adkll.cassette.capture.api.CaptureEvent
import com.r0adkll.cassette.core.CassetteDispatchers
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class FileCaptureLogTest {
  private val file = File.createTempFile("capture", ".jsonl").apply { delete() }
  private val dispatchers =
    CassetteDispatchers(Dispatchers.Unconfined, Dispatchers.Unconfined, Dispatchers.Unconfined)

  @Test
  fun persistsAcrossInstancesAndSkipsBadLines() = runTest {
    val log = FileCaptureLog(CaptureLogFile(file.absolutePath), dispatchers)
    log.append(CaptureEvent.SessionStarted(1, "Ride", "AllLowPower", 80))
    log.append(CaptureEvent.BudgetExceeded(2))
    file.appendText("not json\n")

    val reopened = FileCaptureLog(CaptureLogFile(file.absolutePath), dispatchers)
    assertEquals(listOf(1L, 2L), reopened.events.first().map { it.at })
    assertTrue(reopened.export().contains("\"type\":\"session_started\""))

    reopened.clear()
    assertEquals(emptyList(), reopened.events.first())
  }
}
