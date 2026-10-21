package com.r0adkll.cassette.settings.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.r0adkll.cassette.common.compose.AppInfo
import com.r0adkll.cassette.common.compose.AppPlatform
import com.r0adkll.cassette.common.compose.LocalAppInfo
import com.r0adkll.cassette.common.compose.LocalAppPlatform
import com.r0adkll.cassette.common.compose.components.Panel
import com.r0adkll.cassette.common.compose.components.SettingsRow
import com.r0adkll.cassette.common.compose.components.SettingsSection
import com.r0adkll.cassette.common.compose.components.SettingsSwitchRow
import com.r0adkll.cassette.common.compose.icons.LucideIcons
import com.r0adkll.cassette.common.compose.theme.CassetteTheme

/**
 * About: the installed version, who made Cassette and where its source lives, where to report a
 * bug, crash reports and the privacy policy, and the legal pages. Links open in the browser;
 * licences open their own screen.
 */
@Composable
internal fun AboutBody(
  crashReports: Boolean,
  onCrashReports: (Boolean) -> Unit,
  onLicences: () -> Unit,
) {
  val app = LocalAppInfo.current
  val platform = LocalAppPlatform.current
  Panel {
    SettingsRow(
      "Version",
      subtitle = app.label,
      icon = LucideIcons.Info,
      divider = false,
      trailing = {},
    )
  }
  SettingsSection("Made by") {
    LinkRow(
      "The Scavenger's Software",
      "thescavengers.software",
      LucideIcons.Globe,
      AboutLinks.COMPANY,
    )
    LinkRow(
      "Source code",
      "Open source on GitHub",
      LucideIcons.Code,
      AboutLinks.SOURCE,
      divider = false,
    )
  }
  SettingsSection("Help") {
    LinkRow(
      "Report a bug",
      "A GitHub issue, with this version filled in",
      LucideIcons.Bug,
      AboutLinks.bugReport(app, platform),
    )
    LinkRow(
      "Questions and known issues",
      "GitHub issues",
      LucideIcons.CircleHelp,
      AboutLinks.ISSUES,
      divider = false,
    )
  }
  // Decision 0020: the only thing Cassette sends anywhere, and it can be turned off.
  SettingsSection("Privacy") {
    SettingsSwitchRow(
      "Send crash reports",
      subtitle =
        "Crashes, and a note at each start so crash rates can be counted, go to Firebase " +
          "Crashlytics. Never your bikes or where you ride.",
      icon = LucideIcons.TriangleAlert,
      checked = crashReports,
      onCheckedChange = onCrashReports,
    )
    LinkRow("Privacy policy", null, LucideIcons.Shield, AboutLinks.PRIVACY, divider = false)
  }
  SettingsSection("Legal") {
    LinkRow("Terms of service", null, LucideIcons.Scale, AboutLinks.TERMS)
    SettingsRow(
      "Open-source licences",
      subtitle = "Barlow, JetBrains Mono, Lucide",
      icon = LucideIcons.FileText,
      onClick = onLicences,
      divider = false,
    )
  }
}

/** A row that opens [url] in the browser, marked as leaving the app. */
@Composable
private fun LinkRow(
  title: String,
  subtitle: String?,
  icon: ImageVector,
  url: String,
  divider: Boolean = true,
) {
  val uriHandler = LocalUriHandler.current
  SettingsRow(
    title,
    subtitle = subtitle,
    icon = icon,
    onClick = { uriHandler.openUri(url) },
    divider = divider,
    trailing = {
      Icon(
        LucideIcons.ExternalLink,
        null,
        tint = CassetteTheme.colors.muted,
        modifier = Modifier.size(18.dp),
      )
    },
  )
}

internal object AboutLinks {
  const val COMPANY = "https://thescavengers.software"
  const val SOURCE = "https://github.com/r0adkll/cassette"
  const val ISSUES = "$SOURCE/issues"
  const val PRIVACY = "https://thescavengers.software/cassette/privacy"
  const val TERMS = "https://thescavengers.software/cassette/terms"

  /** A new issue labelled bug, its body a template that already names this build. */
  fun bugReport(app: AppInfo, platform: AppPlatform): String {
    val os =
      when (platform) {
        AppPlatform.Android -> "Android"
        AppPlatform.Ios -> "iOS"
      }
    val body =
      """
      |**What happened**
      |
      |
      |**What you expected**
      |
      |
      |**Your bike's components** (e.g. Red AXS rear derailleur, Quarq power meter)
      |
      |
      |If you can, attach a diagnostics report: Settings → Diagnostics → Share diagnostics.
      |
      |Cassette ${app.label} on $os
      """
        .trimMargin()
    return "$ISSUES/new?labels=bug&body=${body.percentEncoded()}"
  }
}

/** Percent-encodes everything outside RFC 3986's unreserved characters, as UTF-8. */
internal fun String.percentEncoded(): String = buildString {
  for (byte in this@percentEncoded.encodeToByteArray()) {
    val c = byte.toInt() and 0xFF
    if (c.toChar().let { it.isLetterOrDigit() && c < 0x80 || it in "-._~" }) append(c.toChar())
    else append('%').append(HEX[c shr 4]).append(HEX[c and 0xF])
  }
}

private const val HEX = "0123456789ABCDEF"
