import CassetteKit
import UIKit
import os

/// Tabletop on the iPhone Duo (iOS 27.1, decision 0017): standing half open with the fold across
/// the window, where the bike screen splits as it does on a half-open Android foldable. Compose's
/// window posture can't see the Duo's hinge, so its status comes from a hinge interaction and the
/// fold from the view's division region, here.
@available(iOS 27.1, *)
final class TabletopObserver {
  private weak var view: UIView?
  private var partlyOpen = false
  private var fold: CGRect?
  private let log = Logger(subsystem: "com.r0adkll.cassette", category: "tabletop")

  /// [view] is the Compose controller's, whose coordinates are Compose's window coordinates.
  init(view: UIView) {
    self.view = view
    view.addInteraction(
      UIHingeInteraction { [weak self] _, update in
        guard let self else { return }
        let hinge = update.hinge
        self.log.info(
          "hinge \(hinge.map { "\($0.status.rawValue) at \($0.angle)" } ?? "unavailable")")
        self.partlyOpen = hinge?.status == .partiallyOpen
        self.report()
      })
  }

  /// After the hinge moves, and after layout: the fold moves in the window as the Duo rotates.
  func report() {
    guard let view else { return }
    var fold: CGRect?
    if partlyOpen {
      // Across the window: the hinge horizontal, as Android's tabletop.
      fold = view.reservedRegions(kind: .division)
        .first { $0.isActive && $0.frame.width > $0.frame.height }?.frame
    }
    guard fold != self.fold else { return }
    self.fold = fold
    log.info("tabletop fold \(fold.map { "\($0)" } ?? "none")")
    Cassette.shared.showTabletop(
      fold: fold.map {
        TabletopFold(
          left: Double($0.minX), top: Double($0.minY), right: Double($0.maxX),
          bottom: Double($0.maxY))
      })
  }
}
