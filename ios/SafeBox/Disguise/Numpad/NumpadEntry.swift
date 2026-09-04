import CoreGraphics

/// The PIN pad's display-only entry model (decisions §2.3). Pure so the dot
/// rules are unit-testable without a view.
///
/// The count is NOT the recorder: the host owns the buffer, and this only
/// decides how many dots to draw. Presses beyond the visible cap still animate,
/// buzz and emit a token — they simply add no dot.
struct NumpadEntry: Equatable, Sendable {
    /// Same value as `TokenRecorder.maxTokens`; beyond it the row stops growing.
    static let visibleDotCap = 32
    static let baseDotSize: CGFloat = 12
    static let baseDotGap: CGFloat = 12
    static let minDotSize: CGFloat = 6

    private(set) var count = 0

    mutating func append() { count += 1 }

    mutating func removeLast() {
        guard count > 0 else { return }
        count -= 1
    }

    mutating func clear() { count = 0 }

    var visibleDots: Int { min(count, Self.visibleDotCap) }

    /// Dot size and gap shrink uniformly when the row would exceed the column,
    /// with a floor of 6.
    static func dotMetrics(columnWidth: CGFloat, dots: Int) -> (size: CGFloat, gap: CGFloat) {
        guard dots > 1, columnWidth > 0 else { return (baseDotSize, baseDotGap) }
        let natural = CGFloat(dots) * baseDotSize + CGFloat(dots - 1) * baseDotGap
        guard natural > columnWidth else { return (baseDotSize, baseDotGap) }
        let scale = columnWidth / natural
        return (max(minDotSize, baseDotSize * scale), max(minDotSize, baseDotGap * scale))
    }
}

/// PIN pad geometry (decisions §8).
enum NumpadMetrics {
    static let columnMaxWidth: CGFloat = 320
    static let keyGap: CGFloat = 24
    static let minKeyDiameter: CGFloat = 64
    static let maxKeyDiameter: CGFloat = 80
    static let dotsToKeypadGap: CGFloat = 40

    static func sideMargin(width: CGFloat) -> CGFloat {
        min(max(width * 0.04, 12), 20)
    }

    static func columnWidth(width: CGFloat) -> CGFloat {
        min(width - 2 * sideMargin(width: width), columnMaxWidth)
    }

    static func keyDiameter(columnWidth: CGFloat) -> CGFloat {
        min(max((columnWidth - 2 * keyGap) / 3, minKeyDiameter), maxKeyDiameter)
    }
}
