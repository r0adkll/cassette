package com.r0adkll.cassette.app.ios

import com.r0adkll.cassette.alerts.api.Alerts
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import platform.BackgroundTasks.BGAppRefreshTask
import platform.BackgroundTasks.BGAppRefreshTaskRequest
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSinceNow

/**
 * The daily not-seen check (14 and 30 days), Android's ReminderWorker on iOS: a background app
 * refresh that iOS runs when it sees fit, so a reminder may come late (decision 0016).
 */
@OptIn(ExperimentalForeignApi::class)
internal class IosReminders(private val alerts: () -> Alerts, private val scope: CoroutineScope) {
  /** Before launch finishes, as BGTaskScheduler requires; then asks for the next run. */
  fun register() {
    BGTaskScheduler.sharedScheduler.registerForTaskWithIdentifier(ID, usingQueue = null) { task ->
      val refresh = task as? BGAppRefreshTask ?: return@registerForTaskWithIdentifier
      schedule()
      val job = scope.launch {
        val ok = runCatching { alerts().checkReminders() }.isSuccess
        // Once expired, the expiration handler has already completed the task.
        if (isActive) refresh.setTaskCompletedWithSuccess(ok)
      }
      refresh.expirationHandler = {
        job.cancel()
        refresh.setTaskCompletedWithSuccess(false)
      }
    }
    schedule()
  }

  /** Replaces any pending request: no sooner than a day from now. */
  private fun schedule() {
    val request = BGAppRefreshTaskRequest(ID)
    request.earliestBeginDate = NSDate.dateWithTimeIntervalSinceNow(DAY_SECONDS)
    BGTaskScheduler.sharedScheduler.submitTaskRequest(request, null)
  }

  private companion object {
    /** Also listed under BGTaskSchedulerPermittedIdentifiers in the Xcode project. */
    const val ID = "com.r0adkll.cassette.reminders"
    const val DAY_SECONDS = 24.0 * 60 * 60
  }
}
