package com.r0adkll.cassette.app.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.r0adkll.cassette.common.compose.gallery.DesignGallery
import com.r0adkll.cassette.common.compose.theme.CassetteTheme
import com.r0adkll.cassette.common.screens.DesignGalleryScreen
import com.slack.circuit.codegen.annotations.CircuitInject
import dev.zacsweers.metro.AppScope

/** The design-system gallery, with its own light/dark toggle. */
@CircuitInject(DesignGalleryScreen::class, AppScope::class)
@Composable
fun DesignGalleryUi(modifier: Modifier = Modifier) {
  val systemDark = isSystemInDarkTheme()
  var dark by rememberSaveable { mutableStateOf(systemDark) }
  CassetteTheme(darkTheme = dark) {
    DesignGallery(
      onToggleTheme = { dark = !dark },
      modifier = modifier.fillMaxSize().background(CassetteTheme.colors.background),
    )
  }
}
