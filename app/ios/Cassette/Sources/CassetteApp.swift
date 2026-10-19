import CassetteKit
import SwiftUI
import UIKit
import WidgetKit

/// The Swift half stays thin (decision 0017): everything else is the CassetteKit framework.
final class AppDelegate: NSObject, UIApplicationDelegate {
  func application(
    _ application: UIApplication,
    didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
  ) -> Bool {
    // Before launch ends, so iOS can restore the Bluetooth scan it relaunched the app for. The
    // widgets reload whenever the app writes their snapshot; the refresh's Live Activity follows
    // the running refresh, and its buttons reach it.
    RefreshActivityController.endLeftovers()
    RefreshControls.stop = { Cassette.shared.stopRefresh() }
    RefreshControls.more = { Cassette.shared.addRefreshTime() }
    Cassette.shared.didFinishLaunching(
      reloadWidgets: { WidgetCenter.shared.reloadAllTimelines() },
      showRefresh: { RefreshActivityController.show($0) })
    return true
  }
}

@main
struct CassetteApp: App {
  @UIApplicationDelegateAdaptor(AppDelegate.self) private var delegate
  @Environment(\.scenePhase) private var phase

  var body: some Scene {
    WindowGroup {
      ComposeView()
        .ignoresSafeArea()
        .onOpenURL { Cassette.shared.open(url: $0.absoluteString) }
    }
    .onChange(of: phase) { _, phase in
      if phase == .background { Cassette.shared.didEnterBackground() }
    }
  }
}

/// The shared Compose UI.
private struct ComposeView: UIViewControllerRepresentable {
  func makeUIViewController(context: Context) -> UIViewController {
    Cassette.shared.mainViewController()
  }

  func updateUIViewController(_ controller: UIViewController, context: Context) {}
}
