import ActivityKit
import AppIntents
import SwiftUI
import WidgetKit

/// The Live Activity while a refresh runs (decision 0017, iteration 16, A · tabletop card): the
/// tabletop refresh's ring counting the scan down, and a column per component that fills in
/// colour as it answers. Taps open the bike; the expanded island has Stop and Add 60 s.
struct RefreshActivityWidget: Widget {
  var body: some WidgetConfiguration {
    ActivityConfiguration(for: RefreshActivityAttributes.self) { context in
      RefreshLockScreen(state: context.state)
    } dynamicIsland: { context in
      let state = context.state
      // The island is always black: the dark palette in light mode too.
      let palette = Palette(dark: true)
      return DynamicIsland {
        DynamicIslandExpandedRegion(.leading) {
          IslandExpandedLeading(state: state, palette: palette)
        }
        DynamicIslandExpandedRegion(.trailing) {
          RefreshDial(state: state, palette: palette, size: 64, line: 5, time: 20, count: nil)
            .padding(.trailing, 4)
        }
        DynamicIslandExpandedRegion(.bottom) {
          RefreshColumns(state: state, palette: palette, code: 11, spacing: 6)
            .frame(height: 38)
            .padding(.horizontal, 4)
        }
      } compactLeading: {
        IslandMark(state: state, palette: palette, size: 22)
      } compactTrailing: {
        IslandCount(state: state, palette: palette)
      } minimal: {
        IslandMark(state: state, palette: palette, size: 22)
      }
      .widgetURL(state.link)
      .keylineTint(.cassetteRed)
    }
  }
}

/// The Lock Screen, and StandBy (full screen, on black).
struct RefreshLockScreen: View {
  var state: RefreshState
  @Environment(\.colorScheme) private var scheme
  @Environment(\.isActivityFullscreen) private var fullscreen

  var body: some View {
    let palette = Palette(dark: fullscreen || scheme == .dark)
    Group {
      if fullscreen {
        RefreshStandBy(state: state, palette: palette)
      } else {
        RefreshCard(state: state, palette: palette)
      }
    }
    .widgetURL(state.link)
    .activityBackgroundTint(fullscreen ? .black : palette.surface)
    .activitySystemActionForegroundColor(palette.text)
  }
}

// MARK: Lock Screen

struct RefreshCard: View {
  var state: RefreshState
  var palette: Palette

  var body: some View {
    HStack(spacing: 14) {
      VStack(alignment: .leading, spacing: 6) {
        RefreshLabel(state: state, palette: palette, size: 12)
        Text(state.line)
          .font(.barlow(13))
          .foregroundStyle(palette.text)
          .lineLimit(2)
          .frame(maxWidth: .infinity, minHeight: 33, alignment: .topLeading)
        RefreshColumns(state: state, palette: palette, code: 10, spacing: 6)
      }
      RefreshDial(state: state, palette: palette, size: 100, line: 6, time: 30, count: 11)
    }
    .padding(.horizontal, 16)
    .padding(.vertical, 14)
    .frame(height: 150)
  }
}

// MARK: StandBy

struct RefreshStandBy: View {
  var state: RefreshState
  var palette: Palette

  var body: some View {
    HStack(spacing: 44) {
      RefreshDial(state: state, palette: palette, size: 250, line: 12, time: 88, count: 20)
      VStack(alignment: .leading, spacing: 8) {
        RefreshLabel(state: state, palette: palette, size: 18)
        Text(state.headline)
          .font(.condensed(60))
          .foregroundStyle(headlineColor)
          .lineLimit(1)
          .minimumScaleFactor(0.6)
        Text(state.detail)
          .font(.barlow(18))
          .foregroundStyle(palette.muted)
          .lineLimit(2)
        RefreshColumns(state: state, palette: palette, code: 16, spacing: 10, radius: 12)
          .padding(.top, 8)
      }
    }
    .padding(.horizontal, 48)
    .padding(.vertical, 40)
  }

  private var headlineColor: Color {
    state.phase == .done ? palette.status(Tone(rawValue: state.tone) ?? .unknown) : palette.text
  }
}

// MARK: Dynamic Island

struct IslandExpandedLeading: View {
  var state: RefreshState
  var palette: Palette

  var body: some View {
    VStack(alignment: .leading, spacing: 4) {
      RefreshLabel(state: state, palette: palette, size: 12)
      Text(state.headline)
        .font(.condensed(28))
        .foregroundStyle(palette.text)
        .lineLimit(1)
        .minimumScaleFactor(0.7)
      if state.scanning {
        HStack(spacing: 6) {
          Button(intent: StopRefreshIntent()) { Pill(text: "STOP", filled: false, palette: palette) }
          Button(intent: AddRefreshTimeIntent()) {
            Pill(text: "ADD 60 S", filled: true, palette: palette)
          }
        }
        .buttonStyle(.plain)
        .padding(.top, 2)
      } else {
        Text(state.detail)
          .font(.barlow(13))
          .foregroundStyle(palette.muted)
          .lineLimit(2)
      }
    }
    .padding(.leading, 4)
  }
}

/// The compact island's leading side and the minimal island: the ring while scanning, then how
/// it ended.
struct IslandMark: View {
  var state: RefreshState
  var palette: Palette
  var size: CGFloat

  var body: some View {
    if state.scanning {
      CountdownRing(span: state.span, track: Color(hex: 0x333338), width: 3)
        .frame(width: size, height: size)
    } else {
      Icon(name: endIcon, size: size).foregroundStyle(endTone(state, palette))
    }
  }

  private var endIcon: String {
    switch state.phase {
    case .notReady: "bluetooth-off"
    case .done, .partial: "circle-check"
    default: "circle-help"
    }
  }
}

/// The compact island's trailing side: who has answered ("2/5"), or OFF.
struct IslandCount: View {
  var state: RefreshState
  var palette: Palette

  var body: some View {
    Text(state.phase == .notReady ? "OFF" : "\(state.heard)/\(state.components.count)")
      .font(.condensed(18))
      .monospacedDigit()
      .foregroundStyle(state.scanning ? palette.text : endTone(state, palette))
  }
}

// MARK: Parts

/// The dot and "ROAD BIKE · READING".
struct RefreshLabel: View {
  var state: RefreshState
  var palette: Palette
  var size: CGFloat

  var body: some View {
    HStack(spacing: size * 0.6) {
      Circle()
        .fill(state.scanning ? Color.cassetteRed : endTone(state, palette))
        .frame(width: size * 0.58, height: size * 0.58)
      Text(state.label)
        .font(.condensed(size))
        .kerning(size * 0.16)
        .foregroundStyle(palette.muted)
        .lineLimit(1)
        .minimumScaleFactor(0.7)
    }
  }
}

/// The tabletop's circle: a red ring counting down with the time and "2 OF 5" inside; then the
/// green check (all read), amber (some), or grey (none, or Bluetooth off).
struct RefreshDial: View {
  var state: RefreshState
  var palette: Palette
  var size: CGFloat
  var line: CGFloat
  var time: CGFloat
  /// The "2 OF 5" size; nil leaves it out.
  var count: CGFloat?

  var body: some View {
    let total = state.components.count
    ZStack {
      switch state.phase {
      case .listening, .reading:
        CountdownRing(span: state.span, track: track, width: line)
        VStack(spacing: 2) {
          Text(timerInterval: state.span, countsDown: true)
            .font(.condensed(time))
            .monospacedDigit()
            .multilineTextAlignment(.center)
            .foregroundStyle(palette.text)
            .frame(width: size - line * 4)
          if let count {
            Text("\(state.heard) OF \(total)")
              .font(.condensed(count))
              .kerning(count * 0.14)
              .foregroundStyle(palette.muted)
          }
        }
      case .done, .partial:
        let done = state.phase == .done
        let content = done ? Color(hex: 0x03200F) : Color(hex: 0x1A1200)
        Circle().fill(done ? Color(hex: 0x2DB36A) : Color(hex: 0xFFB020))
        VStack(spacing: 2) {
          Icon(name: "check", size: size * 0.3).foregroundStyle(content)
          if let count {
            Text("\(state.heard) OF \(total)").font(.condensed(count + 1)).kerning(count * 0.14)
              .foregroundStyle(content)
          }
        }
      default:
        Circle().strokeBorder(track, lineWidth: line)
        VStack(spacing: 2) {
          if state.phase == .notReady {
            Icon(name: "bluetooth-off", size: size * 0.28).foregroundStyle(palette.muted)
          }
          if let count {
            Text(state.phase == .notReady ? "OFF" : "\(state.heard) OF \(total)")
              .font(.condensed(count)).kerning(count * 0.14)
              .foregroundStyle(palette.muted)
          }
        }
      }
    }
    .frame(width: size, height: size)
  }

  private var track: Color { palette.dark ? Color(hex: 0x26262B) : Color(hex: 0xE2E2DE) }
}

/// The scan's time left as a red ring. ActivityKit redraws a view only when its content changes,
/// so the ring is a timer progress view, which the system animates.
struct CountdownRing: View {
  var span: ClosedRange<Date>
  var track: Color
  var width: CGFloat
  @Environment(\.staticTimers) private var staticTimers

  var body: some View {
    ZStack {
      Circle().strokeBorder(track, lineWidth: width)
      if staticTimers {
        let left = span.upperBound.timeIntervalSinceNow
        let total = span.upperBound.timeIntervalSince(span.lowerBound)
        Circle()
          .trim(from: 0, to: min(max(left / total, 0), 1))
          .stroke(Color.cassetteRed, style: StrokeStyle(lineWidth: width, lineCap: .round))
          .rotationEffect(.degrees(-90))
          .padding(width / 2)
      } else {
        ProgressView(timerInterval: span, countsDown: true) {
          EmptyView()
        } currentValueLabel: {
          EmptyView()
        }
        .progressViewStyle(.circular)
        .tint(.cassetteRed)
      }
    }
  }
}

/// The render tests' ring: `ImageRenderer` can't draw a timer progress view, so it's drawn as it
/// stands now instead.
private struct StaticTimersKey: EnvironmentKey {
  static let defaultValue = false
}

extension EnvironmentValues {
  var staticTimers: Bool {
    get { self[StaticTimersKey.self] }
    set { self[StaticTimersKey.self] = newValue }
  }
}

/// A column per component, grey and waiting until it answers.
struct RefreshColumns: View {
  var state: RefreshState
  var palette: Palette
  var code: CGFloat
  var spacing: CGFloat
  var radius: CGFloat = 6

  var body: some View {
    HStack(spacing: spacing) {
      ForEach(Array(state.components.enumerated()), id: \.offset) { _, component in
        VStack(spacing: 3) {
          Bar(
            fraction: min(max(component.level, 0.08), 1), track: track,
            fill: component.answered
              ? palette.status(Tone(rawValue: component.tone) ?? .unknown) : wait,
            radius: radius, vertical: true)
          HStack(spacing: 0) {
            Text(component.code)
            Spacer(minLength: 2)
            Text(state.scanning && !component.answered ? "···" : component.value)
          }
          .font(.condensed(code))
          .foregroundStyle(palette.text)
          .lineLimit(1)
          .minimumScaleFactor(0.7)
          .opacity(component.answered ? 1 : 0.5)
        }
      }
    }
  }

  private var track: Color { palette.dark ? Color(hex: 0x26262B) : Color(hex: 0xE2E2DE) }
  private var wait: Color { palette.dark ? Color(hex: 0x3A3A40) : Color(hex: 0xC4C4C0) }
}

/// Stop and Add 60 s.
struct Pill: View {
  var text: String
  var filled: Bool
  var palette: Palette

  var body: some View {
    Text(text)
      .font(.condensed(13))
      .kerning(1.5)
      .padding(.horizontal, 14)
      .frame(height: 30)
      .foregroundStyle(filled ? Color(hex: 0x0A0A0B) : palette.text)
      .background {
        if filled {
          Capsule().fill(palette.text)
        } else {
          Capsule().strokeBorder(Color(hex: 0x3A3A40), lineWidth: 1)
        }
      }
  }
}

/// How an ended refresh reads: green when all answered, amber for some, grey otherwise.
func endTone(_ state: RefreshState, _ palette: Palette) -> Color {
  switch state.phase {
  case .done: palette.status(.good)
  case .partial: palette.status(.low)
  default: palette.muted
  }
}
