import ActivityKit
import UIKit

/// Starts, updates and ends the refresh's Live Activity from the states Kotlin sends
/// (`RefreshActivityPublisher`). All local: no push token.
enum RefreshActivityController {
  private static var task: UIBackgroundTaskIdentifier = .invalid

  /// At launch: an activity left by a process that was killed mid-scan.
  static func endLeftovers() {
    for activity in Activity<RefreshActivityAttributes>.activities {
      Task { await activity.end(nil, dismissalPolicy: .immediate) }
    }
  }

  /// Returns nil, or why ActivityKit refused, for the capture log.
  static func show(_ json: String) -> String? {
    guard let state = try? JSONDecoder().decode(RefreshState.self, from: Data(json.utf8)) else {
      return "couldn't read the state"
    }
    let activities = Activity<RefreshActivityAttributes>.activities
    let current = activities.first { $0.attributes.startedAt == state.startedAt }
    // Marked stale a little after the scan should have ended, in case the app is gone by then.
    let content = ActivityContent(
      state: state, staleDate: state.ended ? nil : state.end.addingTimeInterval(15))

    if state.ended {
      endBackgroundTask()
      guard let current else { return nil }
      let policy: ActivityUIDismissalPolicy =
        state.dismissAfterSeconds == 0
        ? .immediate : .after(.now.addingTimeInterval(Double(state.dismissAfterSeconds)))
      Task { await current.end(content, dismissalPolicy: policy) }
      return nil
    }
    if let current {
      Task { await current.update(content) }
      return nil
    }

    // A new refresh: one activity at a time.
    for old in activities { Task { await old.end(nil, dismissalPolicy: .immediate) } }
    guard ActivityAuthorizationInfo().areActivitiesEnabled else {
      return "Live Activities are off for Cassette"
    }
    do {
      _ = try Activity.request(
        attributes: RefreshActivityAttributes(startedAt: state.startedAt), content: content,
        pushType: nil)
    } catch {
      return error.localizedDescription
    }
    beginBackgroundTask()
    return nil
  }

  /// Keeps the scan going for a while once the phone is locked or the app is left; iOS decides
  /// how long (about 30 s), and Bluetooth wakes may keep it longer. Unchecked on an iPhone.
  private static func beginBackgroundTask() {
    endBackgroundTask()
    task = UIApplication.shared.beginBackgroundTask(withName: "refresh") { endBackgroundTask() }
  }

  private static func endBackgroundTask() {
    guard task != .invalid else { return }
    UIApplication.shared.endBackgroundTask(task)
    task = .invalid
  }
}
