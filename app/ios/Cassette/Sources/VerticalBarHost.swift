import CassetteKit
import UIKit

/// The iPhone Duo's vertical bar (iOS 27.1, decision 0017) for the Compose screens' top-bar
/// buttons: Kotlin's UIKit predates it, so the bar items are made here. The system only moves a
/// visible navigation bar's items into the vertical bar, so the navigation bar shows while a
/// screen has items there, and screens only send items where the window has the bar.
///
/// The iOS 27.1 calls are only in the SDK of Xcode 27, which brings Swift 6.4: built with an
/// older Xcode, as CI's is, the app never reports the bar, and the buttons float.
final class VerticalBarHost: UINavigationController, VerticalBar {
  private var onChange: ((KotlinBoolean) -> Void)?

  /// After each layout, for what follows the content's geometry (the tabletop fold).
  var onLayout: (() -> Void)?

  override func viewDidLoad() {
    super.viewDidLoad()
    setNavigationBarHidden(true, animated: false)
    #if compiler(>=6.4)
      if #available(iOS 27.1, *) {
        registerForTraitChanges(UITraitCollection.systemTraitsAffectingVerticalBarEdge) {
          (host: VerticalBarHost, _: UITraitCollection) in host.report()
        }
      }
    #endif
  }

  override func viewDidLayoutSubviews() {
    super.viewDidLayoutSubviews()
    // With its items in the vertical bar, the navigation bar has no height but still sits below a
    // top margin, which it adds to the content's top inset (24 pt on the Duo open). Take that back,
    // so the content doesn't move as the bar shows and hides between screens.
    let vertical = !isNavigationBarHidden && navigationBar.frame.height == 0
    let extra = vertical ? navigationBar.frame.maxY - view.safeAreaInsets.top : 0
    if let content = viewControllers.first, content.additionalSafeAreaInsets.top != -extra {
      content.additionalSafeAreaInsets.top = -extra
    }
    onLayout?()
  }

  override func viewIsAppearing(_ animated: Bool) {
    super.viewIsAppearing(animated)
    report()
  }

  func observe(onChange: @escaping (KotlinBoolean) -> Void) {
    self.onChange = onChange
    report()
  }

  func show(navigation: [VerticalBarItem], actions: [VerticalBarItem]) {
    let item = viewControllers.first?.navigationItem
    // Apple's guidance: back or close at the top, as leading items (there's no back button to
    // supplement), then the actions. The bar lists them top to bottom; trailing items run from
    // the end.
    item?.leftBarButtonItems = separated(navigation.map(button))
    item?.rightBarButtonItems = separated(actions.reversed().map(button))
    setNavigationBarHidden(navigation.isEmpty && actions.isEmpty, animated: false)
  }

  private func button(_ item: VerticalBarItem) -> UIBarButtonItem {
    let button = UIBarButtonItem(image: item.image, primaryAction: UIAction { _ in item.onClick() })
    button.accessibilityLabel = item.label
    #if compiler(>=6.4)
      if #available(iOS 27.1, *) { button.axisBehavior = .verticalPreferred }
    #endif
    // A count (the bikes needing a charge, in their status colour) or a dot.
    if #available(iOS 26.0, *), let background = item.badgeBackground {
      var badge = item.badgeText.map { UIBarButtonItem.Badge.string($0) } ?? .indicator()
      badge.backgroundColor = background
      badge.foregroundColor = item.badgeForeground
      button.badge = badge
    }
    return button
  }

  /// Each on its own glass, as the floating circles are elsewhere: adjacent items share one
  /// background, which a press lights up whole, and these actions aren't related.
  private func separated(_ buttons: [UIBarButtonItem]) -> [UIBarButtonItem] {
    Array(buttons.map { [$0] }.joined(separator: [UIBarButtonItem.fixedSpace(0)]))
  }

  private func report() {
    var shown = false
    #if compiler(>=6.4)
      if #available(iOS 27.1, *) { shown = traitCollection.verticalBarEdge != .unspecified }
    #endif
    onChange?(KotlinBoolean(value: shown))
  }
}
