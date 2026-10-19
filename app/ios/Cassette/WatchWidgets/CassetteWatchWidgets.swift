import AppIntents
import SwiftUI
import WidgetKit

/// The watch's complications and Smart Stack card (decision 0017, iteration 17). They read the
/// snapshot the watch app keeps in its App Group, which reloads them whenever the phone sends one.
@main
struct CassetteWatchWidgets: WidgetBundle {
  var body: some Widget {
    BikeStatusWidget()
    WeakestWidget()
  }
}

/// "Bike": the status word in a circle, the rectangular (the S1 card in the Smart Stack) and the
/// inline line.
struct BikeStatusWidget: Widget {
  var body: some WidgetConfiguration {
    AppIntentConfiguration(kind: "bike", intent: PickBike.self, provider: BikeProvider()) { entry in
      BikeStatusEntryView(entry: entry)
    }
    .configurationDisplayName("Bike")
    .description("A bike's status, from Cassette on your iPhone.")
    .supportedFamilies([.accessoryCircular, .accessoryRectangular, .accessoryInline])
  }
}

/// "Weakest battery": the weakest component's level, as a circular gauge or in a corner.
struct WeakestWidget: Widget {
  var body: some WidgetConfiguration {
    AppIntentConfiguration(kind: "weakest", intent: PickBike.self, provider: BikeProvider()) { entry in
      WeakestEntryView(entry: entry)
    }
    .configurationDisplayName("Weakest battery")
    .description("The lowest battery on a bike.")
    .supportedFamilies([.accessoryCircular, .accessoryCorner])
  }
}

struct BikeEntry: TimelineEntry {
  var date: Date
  var bike: WidgetBike?
  var relevance: TimelineEntryRelevance?
}

struct BikeProvider: AppIntentTimelineProvider {
  func placeholder(in context: Context) -> BikeEntry { BikeEntry(date: Samples.now, bike: Samples.road()) }

  func snapshot(for configuration: PickBike, in context: Context) async -> BikeEntry {
    let bike = SnapshotStore.load()?.bike(configuration.bike.flatMap { Int64($0.id) })
    if bike == nil && context.isPreview { return placeholder(in: context) }
    return BikeEntry(date: .now, bike: bike)
  }

  func timeline(for configuration: PickBike, in context: Context) async -> Timeline<BikeEntry> {
    let bike = SnapshotStore.load()?.bike(configuration.bike.flatMap { Int64($0.id) })
    let now = Date.now
    // A step each time the clock's words change; the watch app reloads when the data does.
    let changes = clockChanges(
      after: now, seen: [bike?.lastSeenDate].compactMap { $0 },
      staleAt: [bike?.staleAt].compactMap { $0 }.map(Date.init(epochMillis:)))
    let entries = ([now] + changes).map { date in
      BikeEntry(date: date, bike: bike, relevance: bike.flatMap { relevance($0, date: date) })
    }
    return Timeline(entries: entries, policy: .atEnd)
  }

  /// The face editor's choices: the default bike, then each bike by name.
  func recommendations() -> [AppIntentRecommendation<PickBike>] {
    let bikes = SnapshotStore.load()?.bikes ?? []
    return [AppIntentRecommendation(intent: PickBike(), description: "Default bike")]
      + bikes.map {
        AppIntentRecommendation(
          intent: PickBike(bike: BikeEntity(id: String($0.id), name: $0.name)), description: Text($0.name))
      }
  }

  /// The card rises in the Smart Stack when the bike wakes with something low (the pre-ride
  /// moment), and for a day while something needs charging now.
  private func relevance(_ bike: WidgetBike, date: Date) -> TimelineEntryRelevance? {
    let tone = bike.status(at: date).tone
    guard tone == .low || tone == .critical, let seen = bike.lastSeenDate else { return nil }
    let since = date.timeIntervalSince(seen)
    if since < 2 * 3_600 { return TimelineEntryRelevance(score: 50, duration: 2 * 3_600 - since) }
    if tone == .critical && since < 86_400 { return TimelineEntryRelevance(score: 20, duration: 86_400 - since) }
    return nil
  }
}

struct BikeStatusEntryView: View {
  var entry: BikeEntry
  @Environment(\.widgetFamily) private var family
  @Environment(\.showsWidgetContainerBackground) private var background

  var body: some View {
    if let bike = entry.bike {
      content(bike).widgetURL(bike.link)
    } else {
      NoBikeComplication().containerBackground(for: .widget) {}
    }
  }

  @ViewBuilder private func content(_ bike: WidgetBike) -> some View {
    switch family {
    case .accessoryCircular:
      StatusCircleView(bike: bike, date: entry.date).containerBackground(for: .widget) {}
    case .accessoryInline:
      StatusInlineView(bike: bike, date: entry.date).containerBackground(for: .widget) {}
    default:
      // The Smart Stack shows the container background: the S1 card in the bike's colour. A face
      // doesn't: the status line and bars.
      if background {
        StatusCardView(bike: bike, date: entry.date)
          .containerBackground(for: .widget) {
            Palette(dark: true).block(bike.status(at: entry.date).tone).fill
          }
      } else {
        StatusRectangleView(bike: bike, date: entry.date).containerBackground(for: .widget) {}
      }
    }
  }
}

struct WeakestEntryView: View {
  var entry: BikeEntry
  @Environment(\.widgetFamily) private var family

  var body: some View {
    if let bike = entry.bike {
      Group {
        if family == .accessoryCorner {
          CornerView(bike: bike, date: entry.date)
        } else {
          WeakestGaugeView(bike: bike, date: entry.date)
        }
      }
      .widgetURL(bike.link)
      .containerBackground(for: .widget) {}
    } else {
      NoBikeComplication().containerBackground(for: .widget) {}
    }
  }
}

/// Corner: the weakest value in the corner, its gauge curving along the bezel.
struct CornerView: View {
  var bike: WidgetBike
  var date: Date

  var body: some View {
    let status = bike.status(at: date)
    let weakest = status.weakest
    Text(weakest?.value ?? "–")
      .font(.condensed(20))
      .widgetCurvesContent()
      .widgetLabel {
        Gauge(value: weakest?.fill ?? 0) {
          Text(status.tone == .stale ? "OLD" : weakest?.code ?? "")
        } currentValueLabel: {
          Text(weakest?.code ?? "")
        }
        .tint(Palette(dark: true).status(status.tone))
      }
      .accessibilityLabel("\(bike.name): \(weakest.map { "\($0.name) \($0.value)" } ?? status.headline)")
  }
}
