import AppIntents
import SwiftUI
import WidgetKit

/// Cassette's widgets (decision 0017, iteration 15) and the refresh's Live Activity (iteration 16).
/// The widgets read the snapshot the app writes to the App Group; the app reloads them whenever it
/// writes one, and starts, updates and ends the Live Activity itself.
@main
struct CassetteWidgets: WidgetBundle {
  var body: some Widget {
    BikeWidget()
    AllBikesWidget()
    RefreshActivityWidget()
  }
}

// MARK: The bike widget

struct BikeWidget: Widget {
  var body: some WidgetConfiguration {
    AppIntentConfiguration(kind: "bike", intent: PickBike.self, provider: BikeProvider()) { entry in
      BikeEntryView(entry: entry)
    }
    .configurationDisplayName("Bike")
    .description("A bike's batteries: the weakest, or every component.")
    .supportedFamilies([
      .systemSmall, .systemMedium, .systemLarge,
      .accessoryCircular, .accessoryRectangular, .accessoryInline,
    ])
    .contentMarginsDisabled()
  }
}

/// The widget's edit menu: which bike. None picked (or a deleted bike) is the default bike.
struct PickBike: WidgetConfigurationIntent {
  static let title: LocalizedStringResource = "Bike"
  static let description = IntentDescription("Which bike the widget shows.")

  @Parameter(title: "Bike") var bike: BikeEntity?
}

struct BikeEntity: AppEntity {
  static let typeDisplayRepresentation: TypeDisplayRepresentation = "Bike"
  static let defaultQuery = BikeQuery()

  /// The bike's id, as text (AppIntents' identifiers).
  var id: String
  var name: String

  var displayRepresentation: DisplayRepresentation { DisplayRepresentation(title: "\(name)") }
}

struct BikeQuery: EntityQuery {
  func entities(for identifiers: [String]) async throws -> [BikeEntity] {
    try await suggestedEntities().filter { identifiers.contains($0.id) }
  }

  func suggestedEntities() async throws -> [BikeEntity] {
    (SnapshotStore.load()?.bikes ?? []).map { BikeEntity(id: String($0.id), name: $0.name) }
  }
}

struct BikeEntry: TimelineEntry {
  var date: Date
  var bike: WidgetBike?
}

struct BikeProvider: AppIntentTimelineProvider {
  func placeholder(in context: Context) -> BikeEntry { BikeEntry(date: Samples.now, bike: Samples.road()) }

  func snapshot(for configuration: PickBike, in context: Context) async -> BikeEntry {
    // The widget gallery shows the sample bike until there's a real one.
    let bike = SnapshotStore.load()?.bike(configuration.bike.flatMap { Int64($0.id) })
    if bike == nil && context.isPreview { return placeholder(in: context) }
    return BikeEntry(date: .now, bike: bike)
  }

  func timeline(for configuration: PickBike, in context: Context) async -> Timeline<BikeEntry> {
    let bike = SnapshotStore.load()?.bike(configuration.bike.flatMap { Int64($0.id) })
    let now = Date.now
    // A step each time the clock's words change; the app reloads when the data does.
    let changes = clockChanges(
      after: now, seen: [bike?.lastSeenDate].compactMap { $0 },
      staleAt: [bike?.staleAt].compactMap { $0 }.map(Date.init(epochMillis:)))
    let entries = ([now] + changes).map { BikeEntry(date: $0, bike: bike) }
    return Timeline(entries: entries, policy: .atEnd)
  }
}

struct BikeEntryView: View {
  var entry: BikeEntry
  @Environment(\.widgetFamily) private var family
  @Environment(\.colorScheme) private var scheme
  @Environment(\.widgetRenderingMode) private var mode
  @Environment(\.showsWidgetContainerBackground) private var background

  var body: some View {
    let look = Look(dark: scheme == .dark, accented: mode == .accented, background: background)
    if let bike = entry.bike {
      content(bike, look).widgetURL(bike.link)
    } else {
      NoBikeView(look: look).containerBackground(for: .widget) { look.palette.surface }
    }
  }

  @ViewBuilder private func content(_ bike: WidgetBike, _ look: Look) -> some View {
    switch family {
    case .systemSmall:
      SmallView(bike: bike, date: entry.date, look: look)
        .containerBackground(for: .widget) {
          SmallBackground(tone: bike.status(at: entry.date).tone, look: look)
        }
    case .systemMedium:
      MediumView(bike: bike, date: entry.date, look: look)
        .containerBackground(for: .widget) { look.palette.surface }
    case .accessoryCircular:
      CircularView(bike: bike, date: entry.date).containerBackground(for: .widget) {}
    case .accessoryRectangular:
      RectangularView(bike: bike, date: entry.date).containerBackground(for: .widget) {}
    case .accessoryInline:
      InlineView(bike: bike, date: entry.date).containerBackground(for: .widget) {}
    default:
      LargeView(bike: bike, date: entry.date, look: look)
        .containerBackground(for: .widget) { look.palette.surface }
    }
  }
}

// MARK: All bikes

struct AllBikesWidget: Widget {
  var body: some WidgetConfiguration {
    StaticConfiguration(kind: "all-bikes", provider: AllBikesProvider()) { entry in
      AllBikesEntryView(entry: entry)
    }
    .configurationDisplayName("All bikes")
    .description("Every bike, each with its weakest battery.")
    .supportedFamilies([.systemMedium, .systemLarge])
    .contentMarginsDisabled()
  }
}

struct AllBikesEntry: TimelineEntry {
  var date: Date
  var bikes: [WidgetBike]
}

struct AllBikesProvider: TimelineProvider {
  func placeholder(in context: Context) -> AllBikesEntry {
    AllBikesEntry(date: Samples.now, bikes: Samples.snapshot.bikes)
  }

  func getSnapshot(in context: Context, completion: @escaping (AllBikesEntry) -> Void) {
    let bikes = SnapshotStore.load()?.bikes ?? []
    completion(bikes.isEmpty && context.isPreview ? placeholder(in: context) : AllBikesEntry(date: .now, bikes: bikes))
  }

  func getTimeline(in context: Context, completion: @escaping (Timeline<AllBikesEntry>) -> Void) {
    let bikes = SnapshotStore.load()?.bikes ?? []
    let now = Date.now
    let changes = clockChanges(
      after: now, seen: bikes.compactMap(\.lastSeenDate),
      staleAt: bikes.compactMap(\.staleAt).map(Date.init(epochMillis:)))
    completion(Timeline(entries: ([now] + changes).map { AllBikesEntry(date: $0, bikes: bikes) }, policy: .atEnd))
  }
}

struct AllBikesEntryView: View {
  var entry: AllBikesEntry
  @Environment(\.widgetFamily) private var family
  @Environment(\.colorScheme) private var scheme
  @Environment(\.widgetRenderingMode) private var mode

  var body: some View {
    let look = Look(dark: scheme == .dark, accented: mode == .accented)
    AllBikesView(bikes: entry.bikes, date: entry.date, look: look, large: family == .systemLarge)
      .containerBackground(for: .widget) { look.palette.surface }
  }
}
