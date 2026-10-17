package com.r0adkll.cassette.wear.ui.update

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.r0adkll.cassette.common.compose.icons.LucideIcons
import com.r0adkll.cassette.common.compose.theme.CassetteTheme
import com.r0adkll.cassette.wear.ui.AllowUpdatesScreen
import com.r0adkll.cassette.wear.ui.UpdateScreen
import com.r0adkll.cassette.wear.ui.WearTheme
import com.r0adkll.cassette.wear.updates.WatchUpdate
import com.r0adkll.cassette.wear.updates.megabytes
import com.slack.circuit.codegen.annotations.CircuitInject
import dev.zacsweers.metro.AppScope

/** The update (iteration 9, A): what's offered, then a ring while it downloads and installs. */
@CircuitInject(UpdateScreen::class, AppScope::class)
@Composable
fun UpdateUi(state: UpdateUiState, modifier: Modifier = Modifier) {
  val colors = CassetteTheme.colors
  when (val update = state.update) {
    is WatchUpdate.Downloading ->
      ProgressRing(
        update.progress,
        "DOWNLOADING",
        "${(update.progress * 100).toInt()}%",
        "${megabytes(update.downloaded).removeSuffix(" MB")} of ${megabytes(update.total)}",
        modifier,
      )
    // The app closes and reopens on the new build.
    is WatchUpdate.Installing ->
      ProgressRing(1f, "INSTALLING", "Build ${update.build}.", null, modifier)
    is WatchUpdate.Available ->
      Message(
        kicker = "WATCH APP",
        title = "Build ${update.build}.",
        body =
          "You have build ${state.installedBuild}. From Drop, ${update.channel} channel · " +
            megabytes(update.bytes) +
            ".",
        button = "INSTALL",
        buttonIcon = LucideIcons.Download,
        onButton = { state.eventSink(UpdateEvent.Install) },
        modifier = modifier,
      )
    is WatchUpdate.Failed ->
      Message(
        kicker = "WATCH APP",
        title = "Update failed.",
        body = update.message,
        button = "TRY AGAIN",
        buttonIcon = LucideIcons.RefreshCw,
        onButton = { state.eventSink(UpdateEvent.Install) },
        titleColor = colors.low,
        modifier = modifier,
      )
    WatchUpdate.None ->
      Message(
        kicker = "WATCH APP",
        title = "Up to date.",
        body = "Build ${state.installedBuild}.",
        modifier = modifier,
      )
  }
}

/** Allow updates, once (iteration 9): explain, show where the switch is, open App info. */
@CircuitInject(AllowUpdatesScreen::class, AppScope::class)
@Composable
fun AllowUpdatesUi(state: AllowUpdatesUiState, modifier: Modifier = Modifier) {
  LifecycleResumeEffect(Unit) {
    state.eventSink(AllowUpdatesEvent.Resumed)
    onPauseOrDispose {}
  }
  val colors = CassetteTheme.colors
  when (state.step) {
    AllowStep.Explain ->
      Message(
        kicker = "CASSETTE",
        icon = LucideIcons.Shield,
        title = "Allow updates.",
        body = "So the watch app can install its own updates, without a computer.",
        button = "OPEN SETTINGS",
        // The smaller sizes wrap it onto two lines.
        buttonSize = EdgeButtonSize.Large,
        onButton = { state.eventSink(AllowUpdatesEvent.OpenSettings) },
        modifier = modifier,
      )
    AllowStep.Path ->
      ScreenScaffold(modifier = modifier) {
        Column(
          Modifier.fillMaxSize().padding(horizontal = 28.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Text(
            "ON THE NEXT SCREEN",
            style = WearTheme.type.kicker,
            color = colors.muted,
            modifier = Modifier.padding(top = 40.dp),
          )
          PathStep("1", "Advanced", Modifier.padding(top = 14.dp))
          PathStep("2", "Install unknown apps", Modifier.padding(top = 8.dp))
          Text(
            "Turn it on, then swipe back.",
            style = WearTheme.type.body,
            color = colors.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp),
          )
        }
      }
    AllowStep.Allowed ->
      Message(
        icon = LucideIcons.CircleCheck,
        iconColor = colors.good,
        title = "Updates allowed.",
        body = "You won't be asked again.",
        modifier = modifier,
      )
  }
}

@Composable
private fun PathStep(number: String, label: String, modifier: Modifier = Modifier) {
  val colors = CassetteTheme.colors
  Row(
    modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(26.dp))
      .background(colors.track)
      .padding(horizontal = 14.dp, vertical = 9.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(number, style = WearTheme.type.code.copy(fontSize = 15.sp), color = colors.muted)
    Text(
      label,
      style = WearTheme.type.body.copy(fontSize = 14.sp),
      color = colors.text,
      modifier = Modifier.padding(start = 10.dp),
    )
  }
}

/** A centred message with an optional edge button, as iteration 9 draws them. */
@Composable
private fun Message(
  title: String,
  body: String,
  modifier: Modifier = Modifier,
  kicker: String? = null,
  icon: ImageVector? = null,
  iconColor: Color = CassetteTheme.colors.text,
  titleColor: Color = CassetteTheme.colors.text,
  button: String? = null,
  buttonIcon: ImageVector? = null,
  buttonSize: EdgeButtonSize = EdgeButtonSize.Small,
  onButton: () -> Unit = {},
) {
  val colors = CassetteTheme.colors
  val type = WearTheme.type
  val list = rememberTransformingLazyColumnState()
  val content: @Composable (PaddingValues) -> Unit = { padding ->
    TransformingLazyColumn(state = list, contentPadding = padding) {
      if (kicker != null) item { Text(kicker, style = type.kicker, color = colors.muted) }
      if (icon != null)
        item {
          Icon(
            icon,
            contentDescription = null,
            tint = iconColor,
            modifier =
              Modifier.padding(top = if (kicker == null) 24.dp else 0.dp)
                .size(if (kicker == null) 40.dp else 30.dp),
          )
        }
      item {
        Text(
          title,
          style = type.headline.copy(fontSize = 28.sp),
          color = titleColor,
          textAlign = TextAlign.Center,
        )
      }
      item {
        Text(
          body,
          style = type.body,
          color = colors.muted,
          textAlign = TextAlign.Center,
          modifier = Modifier.padding(horizontal = 18.dp),
        )
      }
    }
  }
  if (button == null) ScreenScaffold(scrollState = list, modifier = modifier) { content(it) }
  else
    ScreenScaffold(
      scrollState = list,
      modifier = modifier,
      edgeButton = {
        EdgeButton(
          onClick = onButton,
          buttonSize = buttonSize,
          colors =
            ButtonDefaults.buttonColors(
              containerColor = colors.track,
              contentColor = colors.text,
              iconColor = colors.text,
            ),
        ) {
          if (buttonIcon != null)
            Icon(buttonIcon, contentDescription = null, modifier = Modifier.size(16.dp))
          Text(button, style = type.button, modifier = Modifier.padding(start = 6.dp))
        }
      },
    ) {
      content(it)
    }
}

@Composable
private fun ProgressRing(
  progress: Float,
  kicker: String,
  value: String,
  detail: String?,
  modifier: Modifier = Modifier,
) {
  val colors = CassetteTheme.colors
  val type = WearTheme.type
  Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    // Drawn here rather than Wear's indicator, which animates from its first value and lags
    // behind a download's frequent updates.
    val track = colors.line
    val indicator = colors.text
    Canvas(Modifier.fillMaxSize().padding(7.dp)) {
      val stroke = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
      drawArc(track, startAngle = 0f, sweepAngle = 360f, useCenter = false, style = stroke)
      drawArc(
        indicator,
        startAngle = -90f,
        sweepAngle = 360f * progress,
        useCenter = false,
        style = stroke,
      )
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(kicker, style = type.kicker, color = colors.muted)
      Text(value, style = type.headline.copy(fontSize = 40.sp), color = colors.text)
      if (detail != null) Text(detail, style = type.body, color = colors.muted)
    }
  }
}
