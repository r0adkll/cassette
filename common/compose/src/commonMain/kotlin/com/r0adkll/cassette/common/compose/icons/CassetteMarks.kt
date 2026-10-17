package com.r0adkll.cassette.common.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.unit.dp

/**
 * The launcher icon's tape (design iteration 8b, A2), without its red shell: drawn on SRAM red it
 * becomes the shell. Same paths as `app/android/src/main/res/drawable/ic_launcher_foreground.xml`,
 * cropped to the 60-unit square around the tape. Full colour: draw it with Image, not Icon.
 */
object CassetteMarks {
  val Tape: ImageVector by lazy {
    ImageVector.Builder(
        name = "Tape",
        defaultWidth = 60.dp,
        defaultHeight = 60.dp,
        viewportWidth = 60f,
        viewportHeight = 60f,
      )
      .apply {
        group(translationX = -24f, translationY = -26f) {
          addPath(
            pathData =
              PathParser().parsePathString("M41.5,74.5 L44.5,68.5 H63.5 L66.5,74.5 Z").toNodes(),
            fill = SolidColor(Color(0xFFB80023)),
            stroke = SolidColor(Color(0xFFB80023)),
            strokeLineWidth = 3f,
            strokeLineJoin = StrokeJoin.Round,
          )
          addPath(
            pathData =
              PathParser()
                .parsePathString(
                  "M36,35 H72 A5,5 0 0 1 77,40 V59 A5,5 0 0 1 72,64 H36 A5,5 0 0 1 31,59 V40 A5,5 0 0 1 36,35 Z"
                )
                .toNodes(),
            fill = SolidColor(Color(0xFFFFFFFF)),
          )
          addPath(
            pathData =
              PathParser()
                .parsePathString("M31,40 A5,5 0 0 1 36,35 H72 A5,5 0 0 1 77,40 Z")
                .toNodes(),
            fill = SolidColor(Color(0xFF0F0F11)),
            fillAlpha = 0.12f,
          )
          addPath(
            pathData =
              PathParser()
                .parsePathString(
                  "M42.5,44 H65.5 A7.5,7.5 0 0 1 73,51.5 V51.5 A7.5,7.5 0 0 1 65.5,59 H42.5 A7.5,7.5 0 0 1 35,51.5 V51.5 A7.5,7.5 0 0 1 42.5,44 Z"
                )
                .toNodes(),
            fill = SolidColor(Color(0xFF0F0F11)),
          )
          addPath(
            pathData =
              PathParser()
                .parsePathString(
                  "M42.666,46.231 L42.945,45.626 A5.900,5.900 0 0 1 44.055,45.626 L44.334,46.231 A0.834,0.834 0 0 0 45.922,46.747 L46.503,46.422 A5.900,5.900 0 0 1 47.402,47.074 L47.272,47.728 A0.834,0.834 0 0 0 48.253,49.078 L48.915,49.157 A5.900,5.900 0 0 1 49.258,50.213 L48.769,50.666 A0.834,0.834 0 0 0 48.769,52.334 L49.258,52.787 A5.900,5.900 0 0 1 48.915,53.843 L48.253,53.922 A0.834,0.834 0 0 0 47.272,55.272 L47.402,55.926 A5.900,5.900 0 0 1 46.503,56.578 L45.922,56.253 A0.834,0.834 0 0 0 44.334,56.769 L44.055,57.374 A5.900,5.900 0 0 1 42.945,57.374 L42.666,56.769 A0.834,0.834 0 0 0 41.078,56.253 L40.497,56.578 A5.900,5.900 0 0 1 39.598,55.926 L39.728,55.272 A0.834,0.834 0 0 0 38.747,53.922 L38.085,53.843 A5.900,5.900 0 0 1 37.742,52.787 L38.231,52.334 A0.834,0.834 0 0 0 38.231,50.666 L37.742,50.213 A5.900,5.900 0 0 1 38.085,49.157 L38.747,49.078 A0.834,0.834 0 0 0 39.728,47.728 L39.598,47.074 A5.900,5.900 0 0 1 40.497,46.422 L41.078,46.747 A0.834,0.834 0 0 0 42.666,46.231Z M43.5,49.6 A1.9,1.9 0 1 0 43.5,53.4 A1.9,1.9 0 1 0 43.5,49.6 Z"
                )
                .toNodes(),
            fill = SolidColor(Color(0xFFFFFFFF)),
            pathFillType = PathFillType.EvenOdd,
          )
          addPath(
            pathData =
              PathParser()
                .parsePathString(
                  "M63.666,46.231 L63.945,45.626 A5.900,5.900 0 0 1 65.055,45.626 L65.334,46.231 A0.834,0.834 0 0 0 66.922,46.747 L67.503,46.422 A5.900,5.900 0 0 1 68.402,47.074 L68.272,47.728 A0.834,0.834 0 0 0 69.253,49.078 L69.915,49.157 A5.900,5.900 0 0 1 70.258,50.213 L69.769,50.666 A0.834,0.834 0 0 0 69.769,52.334 L70.258,52.787 A5.900,5.900 0 0 1 69.915,53.843 L69.253,53.922 A0.834,0.834 0 0 0 68.272,55.272 L68.402,55.926 A5.900,5.900 0 0 1 67.503,56.578 L66.922,56.253 A0.834,0.834 0 0 0 65.334,56.769 L65.055,57.374 A5.900,5.900 0 0 1 63.945,57.374 L63.666,56.769 A0.834,0.834 0 0 0 62.078,56.253 L61.497,56.578 A5.900,5.900 0 0 1 60.598,55.926 L60.728,55.272 A0.834,0.834 0 0 0 59.747,53.922 L59.085,53.843 A5.900,5.900 0 0 1 58.742,52.787 L59.231,52.334 A0.834,0.834 0 0 0 59.231,50.666 L58.742,50.213 A5.900,5.900 0 0 1 59.085,49.157 L59.747,49.078 A0.834,0.834 0 0 0 60.728,47.728 L60.598,47.074 A5.900,5.900 0 0 1 61.497,46.422 L62.078,46.747 A0.834,0.834 0 0 0 63.666,46.231Z M64.5,49.6 A1.9,1.9 0 1 0 64.5,53.4 A1.9,1.9 0 1 0 64.5,49.6 Z"
                )
                .toNodes(),
            fill = SolidColor(Color(0xFFFFFFFF)),
            pathFillType = PathFillType.EvenOdd,
          )
          addPath(
            pathData =
              PathParser()
                .parsePathString(
                  "M30.860000000000003,32.06 A1.2,1.2 0 1 0 33.260000000000005,32.06 A1.2,1.2 0 1 0 30.860000000000003,32.06 Z M74.74,32.06 A1.2,1.2 0 1 0 77.14,32.06 A1.2,1.2 0 1 0 74.74,32.06 Z M27.990000000000002,73.09 A1.2,1.2 0 1 0 30.39,73.09 A1.2,1.2 0 1 0 27.990000000000002,73.09 Z M77.61,73.09 A1.2,1.2 0 1 0 80.01,73.09 A1.2,1.2 0 1 0 77.61,73.09 Z"
                )
                .toNodes(),
            fill = SolidColor(Color(0xFFB80023)),
          )
        }
      }
      .build()
  }
}
