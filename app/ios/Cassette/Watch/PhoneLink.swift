import Foundation
import WatchConnectivity
import WidgetKit

/// The watch's side of WatchConnectivity (decision 0017): the iPhone's `WatchPublisher` sends one
/// application context (`WatchContext` in `:surfaces:watchkit`) with the widgets' snapshot and the
/// refresh's state. The snapshot is kept in the watch's App Group for the complications. The watch
/// never scans: Refresh asks the phone.
@MainActor
final class PhoneLink: NSObject, ObservableObject {
  static let shared = PhoneLink()

  @Published private(set) var snapshot: WidgetSnapshot? = SnapshotStore.load()
  @Published private(set) var phoneRefresh: PhoneRefresh?
  @Published var problem: PhoneProblem?
  /// From asking until the phone's own state arrives, so the countdown starts at once.
  @Published private var askedUntil: Date?

  func activate() {
    guard WCSession.isSupported() else { return }
    WCSession.default.delegate = self
    WCSession.default.activate()
  }

  func refresh(at date: Date) -> RefreshShown {
    if let phoneRefresh, phoneRefresh.state == "Scanning",
      let until = phoneRefresh.until.map(Date.init(epochMillis:)), until > date
    {
      return .scanning(until: until)
    }
    if let askedUntil, askedUntil > date { return .scanning(until: askedUntil) }
    return .idle
  }

  /// When the watch last asked, in the phone's epoch milliseconds.
  private var askedAt: Int64 = 0

  func requestRefresh() {
    problem = nil
    let session = WCSession.default
    guard session.activationState == .activated, session.isReachable else {
      problem = .unreachable
      return
    }
    askedAt = Int64(Date.now.timeIntervalSince1970 * 1_000)
    askedUntil = .now.addingTimeInterval(60)
    session.sendMessage(
      ["request": "refresh"],
      replyHandler: { _ in },
      errorHandler: { _ in
        Task { @MainActor in
          self.askedUntil = nil
          self.problem = .unreachable
        }
      })
  }

  /// A background wake for WatchConnectivity lasts until everything pending has arrived.
  func finishPending() async {
    while WCSession.default.hasContentPending {
      try? await Task.sleep(for: .milliseconds(200))
    }
  }

  fileprivate func apply(_ context: [String: Any]) {
    if let json = context["snapshot"] as? String, let data = json.data(using: .utf8),
      let snapshot = SnapshotStore.decode(data)
    {
      self.snapshot = snapshot
      SnapshotStore.save(data)
      WidgetCenter.shared.reloadAllTimelines()
    }
    if let json = context["refresh"] as? String, let data = json.data(using: .utf8),
      let refresh = try? JSONDecoder().decode(PhoneRefresh.self, from: data)
    {
      phoneRefresh = refresh
      // The phone's answer to this ask (both clocks are the network's) replaces the guess.
      if refresh.at >= askedAt {
        askedUntil = nil
        if refresh.state == "NotReady" { problem = .notReady }
      }
    }
  }
}

extension PhoneLink: WCSessionDelegate {
  nonisolated func session(
    _ session: WCSession, activationDidCompleteWith state: WCSessionActivationState, error: Error?
  ) {
    let context = session.receivedApplicationContext
    Task { @MainActor in self.apply(context) }
  }

  nonisolated func session(_ session: WCSession, didReceiveApplicationContext context: [String: Any]) {
    Task { @MainActor in self.apply(context) }
  }

  /// The complications' push: the same context, sent when what they show changed.
  nonisolated func session(_ session: WCSession, didReceiveUserInfo userInfo: [String: Any]) {
    Task { @MainActor in self.apply(userInfo) }
  }
}

/// `WearRefresh` from `:surfaces:wear:sync`: Idle, Scanning (until) or NotReady.
struct PhoneRefresh: Codable {
  var state: String
  var until: Int64?
  var at: Int64
}
