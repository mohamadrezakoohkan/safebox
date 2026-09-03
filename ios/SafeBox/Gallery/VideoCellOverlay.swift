import SwiftUI

/// The video affordance on a grid cell (decisions §9): a white play glyph
/// centered on the poster frame, plus the duration in a dark translucent pill
/// at the **bottom-leading** corner. Bottom-trailing is reserved for the
/// selection indicator (P6), so the two never collide.
///
/// The duration string comes from `MediaMetadataFormatter.duration`, the same
/// function the Details sheet uses, so a badge can never disagree with the
/// sheet it opens.
struct VideoCellOverlay: View {
    /// `nil` renders the play glyph with no badge (a video whose duration was
    /// never read — the glyph still tells the user it is a video).
    let durationMs: Int?

    enum Metrics {
        static let glyphSize: CGFloat = 26
        static let badgeInset: CGFloat = 6
        static let badgeHorizontalPadding: CGFloat = 6
        static let badgeVerticalPadding: CGFloat = 2
        static let badgeOpacity: Double = 0.55
    }

    var body: some View {
        ZStack {
            Image(systemName: "play.fill")
                .font(.system(size: Metrics.glyphSize * 0.5, weight: .semibold))
                .foregroundStyle(.white)
                .frame(width: Metrics.glyphSize, height: Metrics.glyphSize)
                .background(Circle().fill(.black.opacity(Metrics.badgeOpacity)))
                .shadow(color: .black.opacity(0.35), radius: 3)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .overlay(alignment: .bottomLeading) {
            if let durationMs {
                Text(MediaMetadataFormatter.duration(milliseconds: durationMs))
                    .font(.caption2.weight(.semibold))
                    .monospacedDigit()
                    .foregroundStyle(.white)
                    .padding(.horizontal, Metrics.badgeHorizontalPadding)
                    .padding(.vertical, Metrics.badgeVerticalPadding)
                    .background(Capsule().fill(.black.opacity(Metrics.badgeOpacity)))
                    .padding(Metrics.badgeInset)
            }
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

#Preview {
    ZStack {
        Color.gray
        VideoCellOverlay(durationMs: 92_400)
    }
    .frame(width: 120, height: 120)
}
