package com.r0adkll.cassette.common.screens

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Opens [link] in the app's own launcher activity: brought forward if it's running (it gets the
 * link through onNewIntent), started if not.
 */
fun Context.deepLinkIntent(link: String): Intent =
  requireNotNull(packageManager.getLaunchIntentForPackage(packageName))
    .setData(Uri.parse(link))
    .addFlags(
      Intent.FLAG_ACTIVITY_NEW_TASK or
        Intent.FLAG_ACTIVITY_CLEAR_TOP or
        Intent.FLAG_ACTIVITY_SINGLE_TOP
    )
