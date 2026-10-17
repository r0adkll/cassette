package com.r0adkll.cassette.core

import kotlinx.coroutines.CoroutineDispatcher

/** Injected so tests can run everything on a test dispatcher. */
class CassetteDispatchers(
  val io: CoroutineDispatcher,
  val default: CoroutineDispatcher,
  val main: CoroutineDispatcher,
)
