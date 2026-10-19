import ActivityKit
import AppIntents
import Foundation

/// The Live Activity while a refresh runs (decision 0017, iteration 16). Built into the app, which
/// starts, updates and ends it, and the widget extension, which draws it.
struct RefreshActivityAttributes: ActivityAttributes {
  typealias ContentState = RefreshState

  /// Which refresh, as `RefreshState.startedAt`.
  var startedAt: Int64
}

/// `RefreshActivityState` in `:surfaces:widgetkit`: worded by the app, so the views only lay it out
/// and count down to `endsAt`. Times are epoch milliseconds.
struct RefreshState: Codable, Hashable {
  var startedAt: Int64
  var phase: RefreshPhase
  var endsAt: Int64
  var totalSeconds: Int
  var bikeId: Int64?
  var label: String
  var line: String
  var headline: String
  var detail: String
  /// The bike's `StatusTone` once read.
  var tone: String
  var heard: Int
  var components: [RefreshComponent]
  var ended: Bool
  var dismissAfterSeconds: Int

  var start: Date { Date(epochMillis: startedAt) }
  var end: Date { Date(epochMillis: endsAt) }
  /// The scan's span for the ring and the countdown; Add 60 s lengthens it.
  var span: ClosedRange<Date> { end.addingTimeInterval(-Double(totalSeconds))...end }

  var link: URL { URL(string: bikeId.map { "cassette://bike/\($0)" } ?? "cassette://")! }

  var scanning: Bool { phase == .listening || phase == .reading }
}

struct RefreshComponent: Codable, Hashable {
  var code: String
  var value: String
  var level: Double
  /// `StatusTone`'s name.
  var tone: String
  var answered: Bool
}

enum RefreshPhase: String, Codable {
  case listening = "Listening", reading = "Reading", done = "Done", partial = "Partial"
  case silent = "Silent", notReady = "NotReady", stopped = "Stopped"

  /// A newer app's phase reads as reading.
  init(from decoder: Decoder) throws {
    self = RefreshPhase(rawValue: try decoder.singleValueContainer().decode(String.self)) ?? .reading
  }
}

/// What the expanded island's buttons do: set by the app to the running refresh's Stop and Add
/// 60 s. A `LiveActivityIntent` runs in the app's process, so the extension's copy stays a no-op.
enum RefreshControls {
  nonisolated(unsafe) static var stop: () -> Void = {}
  nonisolated(unsafe) static var more: () -> Void = {}
}

struct StopRefreshIntent: LiveActivityIntent {
  static let title: LocalizedStringResource = "Stop refresh"

  func perform() async throws -> some IntentResult {
    await MainActor.run { RefreshControls.stop() }
    return .result()
  }
}

struct AddRefreshTimeIntent: LiveActivityIntent {
  static let title: LocalizedStringResource = "Add 60 seconds"

  func perform() async throws -> some IntentResult {
    await MainActor.run { RefreshControls.more() }
    return .result()
  }
}

extension Date {
  init(epochMillis: Int64) { self.init(timeIntervalSince1970: Double(epochMillis) / 1_000) }
}
