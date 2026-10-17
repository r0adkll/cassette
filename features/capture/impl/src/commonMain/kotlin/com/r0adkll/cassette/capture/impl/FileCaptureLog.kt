package com.r0adkll.cassette.capture.impl

import com.r0adkll.cassette.capture.api.CaptureEvent
import com.r0adkll.cassette.capture.api.CaptureLog
import com.r0adkll.cassette.core.CassetteDispatchers
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import okio.buffer
import okio.use

/** Where the platform keeps the log: an absolute path. */
class CaptureLogFile(val path: String)

/**
 * JSON lines on disk, capped: past [MAX_EVENTS] the oldest are dropped, so it's always on without
 * growing forever. Lines that don't parse (from an older build) are skipped.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class FileCaptureLog(file: CaptureLogFile, private val dispatchers: CassetteDispatchers) :
  CaptureLog {
  private val path = file.path.toPath()
  private val fs = FileSystem.SYSTEM
  private val json = Json { ignoreUnknownKeys = true }
  private val mutex = Mutex()
  private val state = MutableStateFlow<List<CaptureEvent>?>(null)

  override val events: Flow<List<CaptureEvent>> = flow {
    load()
    emitAll(state.filterNotNull())
  }

  override suspend fun append(event: CaptureEvent) {
    mutex.withLock {
      val current = loadLocked()
      val updated = current + event
      withContext(dispatchers.io) {
        if (updated.size > MAX_EVENTS) {
          val kept = updated.takeLast(KEEP_AFTER_TRIM)
          writeAll(kept)
          state.value = kept
        } else {
          fs.appendingSink(path, mustExist = false).buffer().use {
            it.writeUtf8(encode(event) + "\n")
          }
          state.value = updated
        }
      }
    }
  }

  override suspend fun clear() {
    mutex.withLock {
      withContext(dispatchers.io) { fs.delete(path, mustExist = false) }
      state.value = emptyList()
    }
  }

  override suspend fun export(): String = mutex.withLock {
    withContext(dispatchers.io) { if (fs.exists(path)) fs.read(path) { readUtf8() } else "" }
  }

  private suspend fun load(): List<CaptureEvent> = mutex.withLock { loadLocked() }

  private suspend fun loadLocked(): List<CaptureEvent> =
    state.value
      ?: withContext(dispatchers.io) {
          if (!fs.exists(path)) emptyList()
          else
            fs
              .read(path) { readUtf8() }
              .lineSequence()
              .filter { it.isNotBlank() }
              .mapNotNull {
                runCatching { json.decodeFromString(CaptureEvent.serializer(), it) }.getOrNull()
              }
              .toList()
        }
        .also { state.value = it }

  private fun writeAll(events: List<CaptureEvent>) {
    path.parent?.let { fs.createDirectories(it) }
    fs.write(path) { events.forEach { writeUtf8(encode(it) + "\n") } }
  }

  private fun encode(event: CaptureEvent) = json.encodeToString(CaptureEvent.serializer(), event)

  private companion object {
    const val MAX_EVENTS = 5_000
    const val KEEP_AFTER_TRIM = 4_000
  }
}
