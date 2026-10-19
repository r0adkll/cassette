import AppIntents
import WidgetKit

/// The widget's edit menu: which bike. None picked (or a deleted bike) is the default bike. The
/// iPhone's widgets and the watch's complications both use it.
struct PickBike: WidgetConfigurationIntent {
  static let title: LocalizedStringResource = "Bike"
  static let description = IntentDescription("Which bike the widget shows.")

  @Parameter(title: "Bike") var bike: BikeEntity?

  init() {}

  init(bike: BikeEntity?) { self.bike = bike }
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
