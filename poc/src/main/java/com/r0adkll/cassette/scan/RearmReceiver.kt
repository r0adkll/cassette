package com.r0adkll.cassette.scan

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.r0adkll.cassette.CassetteApp
import kotlinx.coroutines.launch

/**
 * System-held scans don't survive a reboot or an app update, so re-arm the background scan after
 * either.
 */
class RearmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (
            intent.action != Intent.ACTION_BOOT_COMPLETED &&
                intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        )
            return
        val app = context.applicationContext as CassetteApp
        val pending = goAsync()
        app.scope.launch {
            try {
                app.rearmBackgroundIfEnabled(
                    reason = intent.action.orEmpty().substringAfterLast('.')
                )
            } finally {
                pending.finish()
            }
        }
    }
}
