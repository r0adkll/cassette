import SwiftUI

/// The watch app's screens (decision 0017, iteration 17 B): a page per bike, its colour filling the
/// screen, and the components behind a button. Nothing here is watch-only, so the iPhone's render
/// tests draw them at a watch's size.

/// The phone's refresh as the watch shows it.
enum RefreshShown: Equatable {
  case idle
  /// The phone is listening until then; the watch counts down on its own clock.
  case scanning(until: Date)
}

/// Why Refresh didn't start: the watch couldn't reach the phone, or the phone can't scan.
enum PhoneProblem: Equatable {
  case unreachable, notReady

  var title: String {
    switch self {
    case .unreachable: "Can't reach your iPhone."
    case .notReady: "Bluetooth is off on your iPhone."
    }
  }

  var detail: String {
    switch self {
    case .unreachable: "Refresh runs on the phone. Bring it closer, then try again."
    case .notReady: "Turn it on, or let Cassette use it, then try again."
    }
  }
}

/// The watch is always dark.
private let palette = Palette(dark: true)

/// B1, B2, B4: the bike's status block as the whole page, with Components and Refresh at its foot,
/// or the countdown while the phone listens.
struct BikePage: View {
  var bike: WidgetBike
  var date: Date
  var refresh: RefreshShown = .idle
  var problem: PhoneProblem? = nil
  /// Pass a time to freeze the countdown (the render tests); nil counts down live.
  var countdownAt: Date? = nil
  var onComponents: () -> Void = {}
  var onRefresh: () -> Void = {}
  var onDismissProblem: () -> Void = {}

  var body: some View {
    let status = bike.status(at: date)
    let block = palette.block(status.tone)
    VStack(alignment: .leading, spacing: 4) {
      Text(bike.name.uppercased())
        .font(.condensed(12))
        .tracking(2.4)
        .lineLimit(1)
        // The clock sits at the top right.
        .padding(.trailing, 44)
        .opacity(status.tone == .stale ? 1 : 0.85)
      Spacer(minLength: 4)
      Icon(name: status.tone.icon, size: 30)
      Text(status.headline)
        .font(.condensed(36))
        .lineLimit(3)
        .minimumScaleFactor(0.6)
        .fixedSize(horizontal: false, vertical: true)
        // An old reading's panel keeps its headline white, the rest muted.
        .foregroundStyle(status.tone == .stale ? palette.text : block.content)
      Text(detail(status))
        .font(.barlow(13))
        .lineLimit(3)
        // Below the clock, a watch has less height than it looks: the words win over the gaps.
        .fixedSize(horizontal: false, vertical: true)
        .layoutPriority(1)
        .foregroundStyle(status.tone == .stale ? palette.muted : block.content)
      Spacer(minLength: 8)
      foot(block)
    }
    .foregroundStyle(block.content)
    .padding(.horizontal, 14)
    .padding(.top, 4)
    .padding(.bottom, 6)
    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    .background {
      ZStack {
        block.fill
        if let outline = block.outline {
          ContainerRelativeShape().inset(by: 1).stroke(outline, lineWidth: 2)
        }
      }
      .ignoresSafeArea()
    }
    .overlay(alignment: .bottom) {
      if let problem { ProblemCard(problem: problem, onDismiss: onDismissProblem) }
    }
    .accessibilityElement(children: .contain)
  }

  @ViewBuilder private func foot(_ block: (fill: Color, content: Color, outline: Color?)) -> some View {
    switch refresh {
    case .scanning(let until):
      ScanPill(until: until, now: countdownAt, ink: block.outline == nil ? block.content : .black)
    case .idle:
      HStack {
        RoundButton(icon: "list", label: "Components", tint: block.content, solid: false, action: onComponents)
        Spacer()
        // The one solid button, so it keeps its contrast on any colour.
        RoundButton(
          icon: "refresh-cw", label: "Refresh", tint: block.outline == nil ? block.fill : .black,
          solid: true, fill: block.outline == nil ? block.content : palette.text, action: onRefresh)
      }
    }
  }

  private func detail(_ status: WidgetStatus) -> String {
    if case .scanning = refresh { return "Listening on your iPhone. Shift once to wake the bike." }
    switch status.tone {
    case .stale: return "Readings may be out of date."
    case .unknown: return status.summary
    default:
      let weakest = status.weakest.map { "\($0.name) \($0.value). " } ?? ""
      return weakest + bike.seen(at: date) + "."
    }
  }
}

/// A 40 pt circle: glass on watchOS 26, a translucent fill before it; or solid for Refresh.
struct RoundButton: View {
  var icon: String
  var label: String
  var tint: Color
  var solid: Bool
  var fill: Color = .clear
  var action: () -> Void

  var body: some View {
    Button(action: action) {
      Icon(name: icon, size: 20)
        .foregroundStyle(tint)
        .frame(width: 40, height: 40)
        .background { background }
        .contentShape(Circle())
    }
    .buttonStyle(.plain)
    .accessibilityLabel(label)
  }

  @ViewBuilder private var background: some View {
    if solid {
      Circle().fill(fill)
    } else {
      #if os(watchOS)
        if #available(watchOS 26, *) {
          Circle().fill(.clear).glassEffect(.regular.interactive(), in: Circle())
        } else {
          Circle().fill(tint.opacity(0.14))
        }
      #else
        Circle().fill(tint.opacity(0.14))
      #endif
    }
  }
}

/// B4: Refresh gives way to the countdown, draining in red.
struct ScanPill: View {
  var until: Date
  var now: Date?
  var ink: Color

  var body: some View {
    if let now {
      pill(now)
    } else {
      TimelineView(.periodic(from: .now, by: 1)) { pill($0.date) }
    }
  }

  private func pill(_ now: Date) -> some View {
    let left = max(0, Int(until.timeIntervalSince(now).rounded(.up)))
    return HStack(spacing: 6) {
      Circle().fill(Color.cassetteRed).frame(width: 7, height: 7)
      Text("LISTENING").font(.condensed(13)).tracking(1.3)
      Spacer(minLength: 4)
      Text(String(format: "%d:%02d", left / 60, left % 60)).font(.condensed(17)).monospacedDigit()
    }
    .foregroundStyle(palette.text)
    .padding(.horizontal, 14)
    .frame(height: 38)
    .background(ink, in: Capsule())
    .overlay(alignment: .bottomLeading) {
      GeometryReader { size in
        Rectangle()
          .fill(Color.cassetteRed)
          .frame(width: size.size.width * min(1, Double(left) / 60), height: 2)
          .frame(maxHeight: .infinity, alignment: .bottom)
      }
    }
    .clipShape(Capsule())
    .accessibilityElement(children: .ignore)
    .accessibilityLabel("Listening on your iPhone, \(left) seconds left")
  }
}

/// A4: why Refresh didn't run. The readings stay; only Refresh needs the phone.
struct ProblemCard: View {
  var problem: PhoneProblem
  var onDismiss: () -> Void

  var body: some View {
    VStack(spacing: 5) {
      Icon(name: "smartphone", size: 22).foregroundStyle(palette.muted)
      Text(problem.title).font(.condensed(18)).multilineTextAlignment(.center)
      Text(problem.detail)
        .font(.barlow(12))
        .foregroundStyle(palette.muted)
        .multilineTextAlignment(.center)
        .fixedSize(horizontal: false, vertical: true)
      Button(action: onDismiss) {
        Text("OK")
          .font(.condensed(15))
          .tracking(1.5)
          .frame(maxWidth: .infinity, minHeight: 36)
          .background(palette.text.opacity(0.16), in: Capsule())
      }
      .buttonStyle(.plain)
    }
    .foregroundStyle(palette.text)
    .padding(12)
    .background(Color(hex: 0x1E1E22), in: RoundedRectangle(cornerRadius: 24, style: .continuous))
    .padding(.horizontal, 6)
    .padding(.bottom, 2)
  }
}

/// B3: the large widget's thick bars, one per component with its name and value inside. The app
/// puts it in a scroll view.
struct ComponentsPage: View {
  var bike: WidgetBike
  var date: Date

  var body: some View {
    let status = bike.status(at: date)
    VStack(alignment: .leading, spacing: 5) {
      ForEach(status.components) { component in
        ThickRow(component: component, stale: status.tone == .stale)
      }
      Text("Unofficial. Not made by SRAM.")
        .font(.barlow(11))
        .foregroundStyle(palette.muted)
        .frame(maxWidth: .infinity)
        .padding(.top, 6)
    }
    .padding(.horizontal, 4)
    .foregroundStyle(palette.text)
  }
}

private struct ThickRow: View {
  var component: WidgetComponent
  var stale: Bool

  var body: some View {
    HStack {
      Text(component.name).font(.barlow(13, semibold: true)).lineLimit(1)
      Spacer(minLength: 4)
      Text(component.value).font(.condensed(17))
    }
    .padding(.horizontal, 10)
    .frame(height: 32)
    .background {
      GeometryReader { size in
        ZStack(alignment: .leading) {
          Color(hex: 0x1E1E22)
          palette.thickFill(stale ? .stale : component.tone).frame(width: size.size.width * component.fill)
        }
      }
    }
    .clipShape(RoundedRectangle(cornerRadius: 11, style: .continuous))
    .accessibilityElement(children: .combine)
  }
}

/// No bikes yet, or nothing from the phone yet.
struct EmptyPage: View {
  /// Whether the phone has sent anything.
  var synced: Bool

  var body: some View {
    VStack(alignment: .leading, spacing: 6) {
      Text("CASSETTE").font(.condensed(12)).tracking(2.4).foregroundStyle(palette.muted)
      Spacer(minLength: 4)
      Text(synced ? "No bike yet." : "Waiting for your iPhone.").font(.condensed(30))
      Text(synced ? "Add one in Cassette on your iPhone." : "Open Cassette on your iPhone to send your bikes.")
        .font(.barlow(13))
        .foregroundStyle(palette.muted)
      Spacer(minLength: 4)
      Text("Unofficial. Not made by SRAM.").font(.barlow(11)).foregroundStyle(palette.muted)
    }
    .foregroundStyle(palette.text)
    .padding(.horizontal, 14)
    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
  }
}
