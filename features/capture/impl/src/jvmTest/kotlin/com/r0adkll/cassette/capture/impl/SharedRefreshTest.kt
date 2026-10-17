package com.r0adkll.cassette.capture.impl

import com.r0adkll.cassette.capture.api.CaptureController
import com.r0adkll.cassette.capture.api.RefreshProgress
import com.r0adkll.cassette.core.CassetteDispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class SharedRefreshTest {
  private class FakeCapture(private val progress: () -> Flow<RefreshProgress>) : CaptureController {
    var refreshes = 0
    override val isReady = true

    override fun refresh(): Flow<RefreshProgress> {
      refreshes++
      return progress()
    }

    override suspend fun ensureBackgroundCapture(reason: String) = Unit
  }

  @Test
  fun secondCallerJoinsTheRunningRefresh() = runTest {
    val finish = CompletableDeferred<Unit>()
    val capture = FakeCapture {
      flow {
        emit(RefreshProgress.Scanning(60, 0))
        finish.await()
        emit(RefreshProgress.Done(5, hubRead = true))
      }
    }
    val dispatcher = StandardTestDispatcher(testScheduler)
    val refresh =
      DefaultSharedRefresh(capture, CassetteDispatchers(dispatcher, dispatcher, dispatcher))

    val tile = async { refresh.run() }
    val watch = async { refresh.run() }
    runCurrent()
    assertEquals(RefreshProgress.Scanning(60, 0), refresh.progress.first())

    finish.complete(Unit)
    assertEquals(RefreshProgress.Done(5, hubRead = true), tile.await())
    assertEquals(RefreshProgress.Done(5, hubRead = true), watch.await())
    assertEquals(1, capture.refreshes)
    assertNull(refresh.progress.value)
  }

  @Test
  fun notReadyIsTheOutcomeAndAFreshRunTriesAgain() = runTest {
    val capture = FakeCapture { flowOf(RefreshProgress.NotReady) }
    val dispatcher = StandardTestDispatcher(testScheduler)
    val refresh =
      DefaultSharedRefresh(capture, CassetteDispatchers(dispatcher, dispatcher, dispatcher))

    assertEquals(RefreshProgress.NotReady, refresh.run())
    assertEquals(RefreshProgress.NotReady, refresh.run())
    assertEquals(2, capture.refreshes)
  }
}
