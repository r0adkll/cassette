import SwiftUI
import WidgetKit

/// Every bike as a row tinted by its weakest status; the large widget adds each bike's components
/// under its row while there's room (iteration 15).
struct AllBikesView: View {
  var bikes: [WidgetBike]
  var date: Date
  var look: Look
  var large: Bool

  var body: some View {
    let palette = look.palette
    VStack(alignment: .leading, spacing: 8) {
      Text("ALL BIKES")
        .font(.condensed(12))
        .tracking(2.4)
        .foregroundStyle(palette.muted)
      if bikes.isEmpty {
        Text("No bikes yet.").font(.barlow(14)).foregroundStyle(palette.muted)
      }
      // Two bikes leave room for their components; more get a row each.
      let detailed = large && bikes.count <= 2
      ForEach(bikes.prefix(large ? 5 : 2)) { bike in
        Link(destination: bike.link) {
          VStack(alignment: .leading, spacing: 4) {
            Row(bike: bike, date: date, look: look)
            if detailed { Components(status: bike.status(at: date), look: look) }
          }
        }
      }
      Spacer(minLength: 0)
    }
    .padding(.horizontal, 16)
    .padding(.vertical, 14)
  }

  private struct Row: View {
    var bike: WidgetBike
    var date: Date
    var look: Look

    var body: some View {
      let status = bike.status(at: date)
      HStack(spacing: 10) {
        Text(bike.name).font(.condensed(20)).lineLimit(1)
        Spacer(minLength: 4)
        HStack(spacing: 5) {
          Icon(name: status.tone.icon, size: 14)
          Text(status.short).font(.condensed(12)).tracking(1.2).lineLimit(1)
        }
        .widgetAccentable()
        if let ago = bike.ago(at: date) {
          Text(ago.replacingOccurrences(of: " ago", with: ""))
            .font(.barlow(12))
            .opacity(0.8)
            .lineLimit(1)
        }
      }
      .foregroundStyle(look.palette.text)
      .padding(.horizontal, 14)
      .frame(height: 50)
      .background(
        RoundedRectangle(cornerRadius: 18)
          .fill(look.palette.thickFill(status.tone))
          .widgetAccentable()
      )
      .accessibilityElement(children: .combine)
    }
  }

  private struct Components: View {
    var status: WidgetStatus
    var look: Look

    var body: some View {
      let palette = look.palette
      ForEach(status.components.prefix(6)) { component in
        HStack(spacing: 8) {
          Text(component.code)
            .font(.condensed(12))
            .foregroundStyle(palette.muted)
            .frame(width: 20, alignment: .leading)
          Bar(fraction: component.fill, track: palette.track, fill: palette.status(component.tone), radius: 3)
            .frame(height: 6)
          Text(component.value)
            .font(.condensed(14))
            .foregroundStyle(palette.text)
            .frame(width: 44, alignment: .trailing)
        }
        .frame(height: 14)
        .padding(.horizontal, 14)
      }
    }
  }
}
