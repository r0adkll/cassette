import SwiftUI

/// The Apple Watch app (decision 0017, iteration 17 B): a page per bike, the crown paging between
/// them, from what the iPhone sends.
@main
struct CassetteWatchApp: App {
  @StateObject private var link = PhoneLink.shared

  init() { PhoneLink.shared.activate() }

  var body: some Scene {
    WindowGroup {
      BikesScreen(link: link)
    }
    .backgroundTask(.watchConnectivity) { await PhoneLink.shared.finishPending() }
  }
}

struct BikesScreen: View {
  @ObservedObject var link: PhoneLink
  @State private var selection: Int64?
  @State private var path: [Int64] = []

  var body: some View {
    NavigationStack(path: $path) {
      // Every second while the phone listens, for the countdown's end; else as the clock words.
      TimelineView(.periodic(from: .now, by: scanning ? 1 : 30)) { context in
        if let bikes = link.snapshot?.bikes, !bikes.isEmpty {
          TabView(selection: $selection) {
            ForEach(bikes) { bike in
              BikePage(
                bike: bike, date: context.date, refresh: link.refresh(at: context.date),
                problem: link.problem,
                onComponents: { path.append(bike.id) },
                onRefresh: { link.requestRefresh() },
                onDismissProblem: { link.problem = nil }
              )
              .tag(Optional(bike.id))
              .containerBackground(
                Palette(dark: true).block(bike.status(at: context.date).tone).fill, for: .tabView)
            }
          }
          .tabViewStyle(.verticalPage)
        } else {
          EmptyPage(synced: link.snapshot != nil)
        }
      }
      .navigationDestination(for: Int64.self) { id in
        if let bike = link.snapshot?.bikes.first(where: { $0.id == id }) {
          ScrollView { ComponentsPage(bike: bike, date: .now) }.navigationTitle(bike.name)
        }
      }
    }
    .onAppear { selection = selection ?? link.snapshot?.defaultBikeId }
    // A complication's tap: cassette://bike/<id>.
    .onOpenURL { url in
      guard url.host == "bike", let id = url.pathComponents.dropFirst().first.flatMap({ Int64($0) })
      else { return }
      path = []
      selection = id
    }
  }

  private var scanning: Bool { link.refresh(at: .now) != .idle }
}
