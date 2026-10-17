package com.r0adkll.cassette.onboarding.ui

import com.r0adkll.cassette.capture.api.CaptureController
import com.r0adkll.cassette.capture.api.RefreshProgress
import com.r0adkll.cassette.common.screens.AddBikeScreen
import com.r0adkll.cassette.common.screens.BikeScreen
import com.r0adkll.cassette.common.screens.OnboardingScreen
import com.r0adkll.cassette.settings.test.InMemorySettings
import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.test
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest

class OnboardingPresenterTest {
  private var armed = 0
  private val capture =
    object : CaptureController {
      override val isReady = true

      override fun refresh(): Flow<RefreshProgress> = emptyFlow()

      override suspend fun ensureBackgroundCapture(reason: String) {
        armed++
      }
    }

  @Test
  fun stepsThroughThenAddsABikeOverTheBikeScreen() = runTest {
    val settings = InMemorySettings()
    val navigator = FakeNavigator(OnboardingScreen)
    OnboardingPresenter(navigator, settings, capture).test {
      val welcome = awaitItem()
      assertEquals(OnboardingStep.Welcome, welcome.step)
      welcome.eventSink(OnboardingEvent.Next)
      val nearby = awaitItem()
      assertEquals(OnboardingStep.Nearby, nearby.step)
      nearby.eventSink(OnboardingEvent.Next)
      val notifications = awaitItem()
      assertEquals(OnboardingStep.Notifications, notifications.step)
      notifications.eventSink(OnboardingEvent.Next)

      assertEquals(BikeScreen(), navigator.awaitResetRoot().newRoot)
      assertEquals(AddBikeScreen, navigator.awaitNextScreen())
      cancelAndIgnoreRemainingEvents()
    }
    assertTrue(settings.onboarded.value)
    assertEquals(1, armed)
  }
}
