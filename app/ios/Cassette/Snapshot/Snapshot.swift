import Foundation

/// What the app writes to the App Group for the widgets: `WidgetSnapshot` in `:surfaces:widgetkit`
/// (decision 0017). The app words it with the shared summary code; this side only words the clock.
struct WidgetSnapshot: Codable {
  var version: Int
  var writtenAt: Int64
  var defaultBikeId: Int64?
  var bikes: [WidgetBike]

  /// The bike a widget shows: the one picked, else the default, else the first.
  func bike(_ id: Int64?) -> WidgetBike? {
    bikes.first { $0.id == id } ?? bikes.first { $0.id == defaultBikeId } ?? bikes.first
  }
}

struct WidgetBike: Codable, Identifiable {
  var id: Int64
  var name: String
  var lastSeenAt: Int64?
  var current: WidgetStatus
  /// When `stale` takes over from `current`.
  var staleAt: Int64?
  var stale: WidgetStatus?

  /// The status as it reads at `date`, with the stale headline's day count kept current.
  func status(at date: Date) -> WidgetStatus {
    var status = current
    if let staleAt, let stale, date >= Date(epochMillis: staleAt) { status = stale }
    // BikeSummary's stale headline.
    if status.tone == .stale, let seen = lastSeenDate {
      status.headline = "Not seen in \(Int(date.timeIntervalSince(seen) / 86_400)) days."
    }
    return status
  }

  var lastSeenDate: Date? { lastSeenAt.map(Date.init(epochMillis:)) }

  /// "2 min ago", or nil if the bike was never seen.
  func ago(at date: Date) -> String? {
    lastSeenDate.map { agoText(date.timeIntervalSince($0)) }
  }

  /// "Seen 2 min ago", or "Never seen", as `BikeSummary` words the seen line.
  func seen(at date: Date) -> String { ago(at: date).map { "Seen \($0)" } ?? "Never seen" }

  var link: URL { URL(string: "cassette://bike/\(id)")! }

  func link(_ component: WidgetComponent) -> URL {
    URL(string: "cassette://bike/\(id)/component/\(component.id)")!
  }
}

struct WidgetStatus: Codable {
  var tone: Tone
  var word: String
  var headline: String
  var summary: String
  var weakestId: Int64?
  var components: [WidgetComponent]

  var weakest: WidgetComponent? { components.first { $0.id == weakestId } }

  /// The status in a few characters: "GOOD", "FD LOW", "OLD". Low and charge now name the
  /// component, as Android's all-bikes widget does.
  var short: String {
    switch tone {
    case .low, .critical: weakest.map { "\($0.code) \($0.word)" } ?? word
    default: word
    }
  }
}

struct WidgetComponent: Codable, Identifiable {
  var id: Int64
  var code: String
  var name: String
  var value: String
  var level: Double
  var tone: Tone
  var word: String

  /// Android's compact value for the medium widget: "78", "≈11", "2.91" (an estimate keeps its ≈).
  var compact: String {
    if value.hasSuffix("%") { return String(value.dropLast()) }
    if value.hasSuffix(" V") { return String(value.dropLast(2)) }
    return value
  }

  /// The bar's length: at least a sliver, as on Android.
  var fill: Double { min(max(level, 0.06), 1) }
}

/// `StatusTone`'s names. An unknown name (a newer app) reads as unknown.
enum Tone: String, Codable {
  case good = "Good", low = "Low", critical = "Critical", stale = "Stale", unknown = "Unknown"

  init(from decoder: Decoder) throws {
    self = Tone(rawValue: try decoder.singleValueContainer().decode(String.self)) ?? .unknown
  }

  /// The Lucide status icon (decision 0012), in the extension's asset catalog.
  var icon: String {
    switch self {
    case .good: "circle-check"
    case .low: "battery-low"
    case .critical: "battery-warning"
    case .stale: "history"
    case .unknown: "circle-help"
    }
  }
}

/// `ago` in `BikeSummary`.
func agoText(_ elapsed: TimeInterval) -> String {
  let minutes = Int(elapsed / 60)
  let hours = Int(elapsed / 3_600)
  let days = Int(elapsed / 86_400)
  if elapsed < 60 { return "just now" }
  if minutes < 60 { return "\(minutes) min ago" }
  if hours < 24 { return "\(hours) h ago" }
  if days == 1 { return "yesterday" }
  return "\(days) days ago"
}


/// The App Group file the iPhone app writes (`WidgetPublisher`). On the watch, the watch app writes
/// it from what the phone sends, for the complications.
enum SnapshotStore {
  static func load() -> WidgetSnapshot? {
    guard let file, let data = try? Data(contentsOf: file) else { return nil }
    return decode(data)
  }

  static func save(_ data: Data) {
    guard let file else { return }
    try? data.write(to: file, options: .atomic)
  }

  static func decode(_ data: Data) -> WidgetSnapshot? {
    try? JSONDecoder().decode(WidgetSnapshot.self, from: data)
  }

  private static var file: URL? {
    guard
      let group = Bundle.main.object(forInfoDictionaryKey: "CassetteAppGroup") as? String,
      let container = FileManager.default.containerURL(
        forSecurityApplicationGroupIdentifier: group)
    else { return nil }
    return container.appendingPathComponent("widgets.json")
  }
}

/// When the clock words change for bikes seen at `seen` ("2 min ago" becoming "3 min ago", the
/// bike going stale), over the next day: the timeline's entries after now.
func clockChanges(after now: Date, seen: [Date], staleAt: [Date], limit: Int = 90) -> [Date] {
  let end = now.addingTimeInterval(86_400)
  var dates = Set(staleAt)
  for last in seen {
    for minute in 1...60 { dates.insert(last.addingTimeInterval(Double(minute) * 60)) }
    for hour in 2...24 { dates.insert(last.addingTimeInterval(Double(hour) * 3_600)) }
    for day in 2...60 { dates.insert(last.addingTimeInterval(Double(day) * 86_400)) }
  }
  return Array(dates.filter { $0 > now && $0 <= end }.sorted().prefix(limit))
}
