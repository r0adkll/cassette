package com.r0adkll.cassette.onboarding.ui

import com.r0adkll.cassette.capture.api.CaptureController
import com.r0adkll.cassette.capture.api.RefreshProgress
import com.r0adkll.cassette.common.screens.AddBikeScreen
import com.r0adkll.cassette.common.screens.BikeScreen
import com.r0adkll.cassette.common.screens.OnboardingPermissionsScreen
import com.r0adkll.cassette.common.screens.OnboardingScreen
import com.r0adkll.cassette.settings.test.InMemorySettings
import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.test
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
  fun welcomeGoesToPermissions() = runTest {
    val navigator = FakeNavigator(OnboardingScreen)
    WelcomePresenter(navigator).test {
      awaitItem().eventSink(WelcomeEvent.GetStarted)
      assertEquals(OnboardingPermissionsScreen, navigator.awaitNextScreen())
    }
  }

  @Test
  fun continueFinishesThenAddsABikeOverTheBikeScreen() = runTest {
    val settings = InMemorySettings()
    val navigator = FakeNavigator(OnboardingPermissionsScreen)
    PermissionsPresenter(navigator, settings, capture).test {
      awaitItem().eventSink(PermissionsEvent.Continue)

      assertEquals(BikeScreen(), navigator.awaitResetRoot().newRoot)
      assertEquals(AddBikeScreen(), navigator.awaitNextScreen())
      cancelAndIgnoreRemainingEvents()
    }
    assertTrue(settings.onboarded.value)
    assertEquals(1, armed)
  }

  @Test
  fun backReturnsToTheWelcomeWithoutFinishing() = runTest {
    val settings = InMemorySettings()
    val navigator = FakeNavigator(OnboardingPermissionsScreen)
    PermissionsPresenter(navigator, settings, capture).test {
      awaitItem().eventSink(PermissionsEvent.Back)
      navigator.awaitPop()
    }
    assertFalse(settings.onboarded.value)
    assertEquals(0, armed)
  }
}
