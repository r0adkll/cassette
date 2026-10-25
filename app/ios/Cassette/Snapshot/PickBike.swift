import AppIntents
import WidgetKit

/// The widget's edit menu: which bike, and which component (decision 0027). No bike picked (or a
/// deleted bike) is the default bike; "Weakest" (or none) is the weakest component. The iPhone's
/// widgets and the watch's complications both use it; the watch's face editor only offers the
/// component to the Battery complication.
struct PickBike: WidgetConfigurationIntent {
  static let title: LocalizedStringResource = "Bike"
  static let description = IntentDescription(
    "Which bike the widget shows, and which component it shows when small.")

  @Parameter(title: "Bike") var bike: BikeEntity?
  @Parameter(title: "Component") var component: ComponentEntity?

  init() {}

  init(bike: BikeEntity?, component: ComponentEntity? = nil) {
    self.bike = bike
    self.component = component
  }

  /// The picked bike's id; nil for the default bike.
  var bikeId: Int64? { bike.flatMap { Int64($0.id) } }
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

/// A component by its kind, so the pick outlives a replaced part, or "Weakest".
struct ComponentEntity: AppEntity {
  static let typeDisplayRepresentation: TypeDisplayRepresentation = "Component"
  static let defaultQuery = ComponentQuery()

  static let weakest = ComponentEntity(id: "weakest", name: "Weakest")

  /// The `ComponentKind` name, or `weakest`.
  var id: String
  var name: String

  /// The kind picked; nil for the weakest.
  var kind: String? { id == Self.weakest.id ? nil : id }

  var displayRepresentation: DisplayRepresentation { DisplayRepresentation(title: "\(name)") }

  /// "Weakest", then each of `bike`'s components, one per kind.
  static func choices(_ bike: WidgetBike?) -> [ComponentEntity] {
    var seen = Set<String>()
    let components = (bike?.current.components ?? []).compactMap { component -> ComponentEntity? in
      guard let kind = component.kind, seen.insert(kind).inserted else { return nil }
      return ComponentEntity(id: kind, name: component.name)
    }
    return [weakest] + components
  }
}

struct ComponentQuery: EntityQuery {
  /// The bike picked above it in the edit menu, whose components it lists.
  @IntentParameterDependency<PickBike>(\.$bike) var pick

  func entities(for identifiers: [String]) async throws -> [ComponentEntity] {
    // Any bike's: a pick keeps its name when the bike changes.
    let bikes = SnapshotStore.load()?.bikes ?? []
    let known = bikes.flatMap { ComponentEntity.choices($0) }
    return identifiers.compactMap { id in
      known.first { $0.id == id } ?? (id == ComponentEntity.weakest.id ? .weakest : nil)
    }
  }

  func suggestedEntities() async throws -> [ComponentEntity] {
    let bike: BikeEntity? = pick?.bike
    return ComponentEntity.choices(SnapshotStore.load()?.bike(bike.flatMap { Int64($0.id) }))
  }

  func defaultResult() async -> ComponentEntity? { .weakest }
}
