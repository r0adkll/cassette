package com.r0adkll.cassette.capture.impl

import com.r0adkll.cassette.capture.api.CaptureController
import com.r0adkll.cassette.capture.api.RefreshProgress
import com.r0adkll.cassette.capture.api.SharedRefresh
import com.r0adkll.cassette.core.CassetteDispatchers
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class DefaultSharedRefresh(
  private val capture: CaptureController,
  dispatchers: CassetteDispatchers,
) : SharedRefresh {
  /** Lives as long as the process, so a refresh finishes even if whoever asked goes away. */
  private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)
  private val lock = Mutex()
  private var running: Deferred<RefreshProgress>? = null

  private val _progress = MutableStateFlow<RefreshProgress?>(null)
  override val progress: StateFlow<RefreshProgress?> = _progress.asStateFlow()

  override suspend fun run(): RefreshProgress = start { capture.refresh() }

  override suspend fun demo(): RefreshProgress = start { capture.demoRefresh() }

  private suspend fun start(refresh: () -> Flow<RefreshProgress>): RefreshProgress {
    val job = lock.withLock {
      running?.takeIf { it.isActive } ?: scope.async { refreshNow(refresh()) }.also { running = it }
    }
    return job.await()
  }

  private suspend fun refreshNow(refresh: Flow<RefreshProgress>): RefreshProgress {
    var last: RefreshProgress = RefreshProgress.NotReady
    try {
      refresh.collect {
        last = it
        _progress.value = it.takeIf { p ->
          p is RefreshProgress.Scanning || p is RefreshProgress.NotReady
        }
      }
    } finally {
      _progress.value = null
    }
    return last
  }
}
