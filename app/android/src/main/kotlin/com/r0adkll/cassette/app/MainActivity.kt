package com.r0adkll.cassette.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.r0adkll.cassette.common.compose.gallery.DesignGallery
import com.r0adkll.cassette.common.compose.theme.CassetteTheme

/** For now, the design-system gallery; screens arrive with the features. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val systemDark = isSystemInDarkTheme()
            var dark by rememberSaveable { mutableStateOf(systemDark) }
            CassetteTheme(darkTheme = dark) {
                // The background runs under the system bars; content stays inside them.
                Box(Modifier.fillMaxSize().background(CassetteTheme.colors.background)) {
                    DesignGallery(
                        onToggleTheme = { dark = !dark },
                        modifier = Modifier.safeDrawingPadding(),
                    )
                }
            }
        }
    }
}
