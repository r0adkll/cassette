import SwiftUI
import WidgetKit

/// Small: the status block (iteration 15, H1): the bike, the headline, and a component: the one to
/// charge, else the `preferred` one (decision 0027), else the weakest.
struct SmallView: View {
  var bike: WidgetBike
  var date: Date
  var look: Look
  var preferred: String? = nil

  var body: some View {
    let status = bike.status(at: date)
    let palette = look.palette
    let block = palette.block(status.tone)
    // On a tinted home screen there's no block fill; in StandBy, on black, the headline and icon
    // take the status colour (S2).
    let content = look.accented || !look.background ? palette.text : block.content
    let signal = look.background ? content : palette.status(status.tone)
    VStack(alignment: .leading, spacing: 0) {
      HStack(spacing: 6) {
        Text(bike.name.uppercased())
          .font(.condensed(12))
          .tracking(1.9)
          .lineLimit(1)
          .foregroundStyle(look.background ? content : palette.muted)
        Spacer(minLength: 0)
        Icon(name: status.tone.icon, size: 22).foregroundStyle(signal).widgetAccentable()
      }
      Spacer(minLength: 4)
      Text(status.headline)
        .font(.condensed(40))
        .tracking(-0.8)
        .lineSpacing(-6)
        .lineLimit(2)
        .minimumScaleFactor(0.6)
        .foregroundStyle(signal)
      Spacer(minLength: 4)
      Text(status.featured(preferred).map { "\($0.code) \($0.value) · \($0.word)" } ?? bike.seen(at: date))
        .font(.condensed(12))
        .tracking(1)
        .lineLimit(1)
        .foregroundStyle(content)
        .widgetAccentable()
    }
    .padding(14)
    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
  }
}

/// The small widget's background: the block's fill (and an old reading's outline).
struct SmallBackground: View {
  var tone: Tone
  var look: Look

  var body: some View {
    let block = look.palette.block(tone)
    if look.accented {
      look.palette.surface
    } else {
      ZStack {
        block.fill
        if let outline = block.outline { ContainerRelativeShape().strokeBorder(outline, lineWidth: 1) }
      }
    }
  }
}

/// The name and "LOW · 2 MIN AGO" over the medium and large widgets.
struct Header: View {
  var bike: WidgetBike
  var status: WidgetStatus
  var date: Date
  var look: Look
  var size: CGFloat

  var body: some View {
    HStack(spacing: 8) {
      Text(bike.name)
        .font(.condensed(size))
        .lineLimit(1)
        .foregroundStyle(look.palette.text)
      Spacer(minLength: 0)
      HStack(spacing: 5) {
        Icon(name: status.tone.icon, size: 13)
        Text("\(status.word) · \(meta)")
          .font(.barlow(11))
          .tracking(1.3)
          .lineLimit(1)
      }
      .foregroundStyle(look.palette.status(status.tone))
      .widgetAccentable()
    }
  }

  private var meta: String {
    bike.ago(at: date).map { $0.uppercased() } ?? "NEVER SEEN"
  }
}

/// Medium: one vertical bar per component, each opening its component.
struct MediumView: View {
  var bike: WidgetBike
  var date: Date
  var look: Look

  var body: some View {
    let status = bike.status(at: date)
    let palette = look.palette
    VStack(alignment: .leading, spacing: 10) {
      Header(bike: bike, status: status, date: date, look: look, size: 22)
      HStack(spacing: 8) {
        ForEach(status.components.prefix(10)) { component in
          Link(destination: bike.link(component)) {
            VStack(spacing: 4) {
              Bar(
                fraction: component.fill, track: palette.track,
                fill: palette.status(component.tone), radius: 10, vertical: true)
              HStack(alignment: .firstTextBaseline, spacing: 0) {
                Text(component.code).foregroundStyle(palette.muted)
                Spacer(minLength: 2)
                Text(component.compact).foregroundStyle(palette.text)
              }
              .font(.condensed(12))
              .lineLimit(1)
            }
          }
          .accessibilityLabel("\(component.name) \(component.value)")
        }
      }
    }
    .padding(.horizontal, 16)
    .padding(.vertical, 14)
  }
}

/// Large: a thick bar per component with the name and value inside (after Pixel's battery widget).
struct LargeView: View {
  var bike: WidgetBike
  var date: Date
  var look: Look

  var body: some View {
    let status = bike.status(at: date)
    let palette = look.palette
    let rows = Array(status.components.prefix(10))
    VStack(alignment: .leading, spacing: 10) {
      Header(bike: bike, status: status, date: date, look: look, size: 24)
      GeometryReader { space in
        // Rows share the space: 46 pt each, thinner when a bike has many components.
        let gap: CGFloat = 6
        let height = min(46, (space.size.height - gap * CGFloat(max(rows.count - 1, 0))) / CGFloat(max(rows.count, 1)))
        VStack(spacing: gap) {
          ForEach(rows) { component in
            Link(destination: bike.link(component)) {
              ZStack {
                Bar(
                  fraction: component.fill, track: palette.track,
                  fill: palette.thickFill(component.tone), radius: height / 2.6)
                HStack(spacing: 0) {
                  Text(component.code).font(.condensed(14)).frame(width: 32, alignment: .leading)
                  Text(component.name).font(.barlow(14)).lineLimit(1)
                  Spacer(minLength: 6)
                  Text(component.value).font(.condensed(height < 34 ? 16 : 20))
                }
                .foregroundStyle(palette.text)
                .padding(.horizontal, 14)
              }
              .frame(height: height)
            }
            .accessibilityLabel("\(component.name) \(component.value)")
          }
        }
      }
    }
    .padding(16)
  }
}

/// Lock Screen, rectangular (L2): the bike and its status, then a mini bar per component.
struct RectangularView: View {
  var bike: WidgetBike
  var date: Date

  var body: some View {
    let status = bike.status(at: date)
    VStack(alignment: .leading, spacing: 3) {
      HStack(spacing: 5) {
        Icon(name: status.tone.icon, size: 13)
        Text(bike.name.uppercased()).lineLimit(1)
        Spacer(minLength: 4)
        Text(status.short).lineLimit(1).layoutPriority(1)
      }
      .font(.condensed(12))
      .tracking(1.2)
      HStack(spacing: 6) {
        ForEach(status.components.prefix(8)) { component in
          VStack(spacing: 2) {
            Bar(fraction: component.fill, track: .primary.opacity(0.26), fill: .primary, radius: 4, vertical: true)
            Text(component.code).font(.condensed(10)).lineLimit(1)
          }
        }
      }
    }
    .accessibilityElement(children: .ignore)
    .accessibilityLabel("\(bike.name): \(status.headline) \(status.summary)")
  }
}

/// Lock Screen, circular (L2): the status icon over a short word.
struct CircularView: View {
  var bike: WidgetBike
  var date: Date

  var body: some View {
    let status = bike.status(at: date)
    ZStack {
      AccessoryWidgetBackground()
      VStack(spacing: 3) {
        Icon(name: status.tone.icon, size: 24)
        // Charge now is too long for the circle: "CHARGE", as the Wear complication says it.
        Text(status.tone == .critical ? "CHARGE" : status.short)
          .font(.condensed(12))
          .tracking(0.8)
          .lineLimit(1)
          .minimumScaleFactor(0.7)
      }
      .padding(.horizontal, 6)
    }
    .accessibilityElement(children: .ignore)
    .accessibilityLabel("\(bike.name): \(status.headline)")
  }
}

/// Lock Screen, inline: the icon and a few words, in the system font (the only one it allows).
struct InlineView: View {
  var bike: WidgetBike
  var date: Date

  var body: some View {
    let status = bike.status(at: date)
    let text: String =
      switch status.tone {
      case .low, .critical: status.weakest.map { "\($0.code) \($0.word) · \($0.value)" } ?? status.word
      default: "\(bike.name) · \(status.word)"
      }
    Label {
      Text(text)
    } icon: {
      Image(status.tone.icon, bundle: .widgets).renderingMode(.template)
    }
  }
}

/// No bike yet (or no snapshot): Android's empty widget.
struct NoBikeView: View {
  var look: Look

  var body: some View {
    VStack(alignment: .leading, spacing: 2) {
      Text("No bike yet.").font(.condensed(24)).foregroundStyle(look.palette.text)
      Text("Open Cassette to add one.").font(.barlow(12)).foregroundStyle(look.palette.muted)
    }
    .padding(16)
    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
  }
}
