import Foundation

/// The Kotlin side's times are epoch milliseconds. A file of its own so the watch targets, which
/// build `Snapshot/` but not the Live Activity, can take it too.
extension Date {
  init(epochMillis: Int64) { self.init(timeIntervalSince1970: Double(epochMillis) / 1_000) }
}
