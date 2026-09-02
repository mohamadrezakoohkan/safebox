import SwiftUI

/// Token palette from the disguise design spec §3.1. Graphite-blue background,
/// rounded-rect keys, burnt-amber operators — deliberately neither Apple's nor
/// Google's calculator trade dress.
struct DisguiseTheme {
    let background: Color
    let displayText: Color
    let keyDigit: Color
    let keyDigitPressed: Color
    let keyFn: Color
    let keyFnPressed: Color
    let keyOp: Color
    let keyOpPressed: Color
    let keyOpActiveRing: Color
    let keyLabel: Color
    let keyLabelOnOp: Color
    let caption: Color
    let captionError: Color

    static let dark = DisguiseTheme(
        background: Color(hex: 0x17191C),
        displayText: Color(hex: 0xF5F6F7),
        keyDigit: Color(hex: 0x2A2D33),
        keyDigitPressed: Color(hex: 0x3A3E46),
        keyFn: Color(hex: 0x43484F),
        keyFnPressed: Color(hex: 0x565B63),
        keyOp: Color(hex: 0xB45309),
        keyOpPressed: Color(hex: 0xD97706),
        keyOpActiveRing: Color(hex: 0xF7C77E),
        keyLabel: Color(hex: 0xF5F6F7),
        keyLabelOnOp: .white,
        caption: Color(hex: 0xA9AFB8),
        captionError: Color(hex: 0xE5484D)
    )

    static let light = DisguiseTheme(
        background: Color(hex: 0xF2F3F5),
        displayText: Color(hex: 0x1A1C1F),
        keyDigit: Color(hex: 0xFFFFFF),
        keyDigitPressed: Color(hex: 0xE2E5EA),
        keyFn: Color(hex: 0xD9DDE3),
        keyFnPressed: Color(hex: 0xC4C9D1),
        keyOp: Color(hex: 0xB45309),
        keyOpPressed: Color(hex: 0x92400E),
        keyOpActiveRing: Color(hex: 0xF7C77E),
        keyLabel: Color(hex: 0x1A1C1F),
        keyLabelOnOp: .white,
        caption: Color(hex: 0x5A6069),
        captionError: Color(hex: 0xB3261E)
    )

    static func theme(for colorScheme: ColorScheme) -> DisguiseTheme {
        colorScheme == .dark ? .dark : .light
    }
}

extension Color {
    init(hex: UInt32) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255
        )
    }
}

/// Keypad geometry per design spec §2.2: the height band is the driver
/// (keypad targets 62–68% of usable height), width supplies a ceiling.
struct KeypadMetrics {
    let sideMargin: CGFloat
    let gutter: CGFloat
    let verticalGutter: CGFloat
    let keyWidth: CGFloat
    let keyHeight: CGFloat
    let contentWidth: CGFloat

    init(size: CGSize) {
        let S = size.width
        let H = size.height
        let m = min(max(S * 0.04, 12), 20)
        var W = S - 2 * m
        W = min(W, 480) // tablet/large-screen bounded column
        let g = min(max(W * 0.03, 8), 14)
        let k = (W - 3 * g) / 4
        let U = max(H - g, 1)
        var h = (0.65 * U - 4 * g) / 5
        var gv = g
        if h > k {
            // Aspect ceiling: keys never taller than wide; grow the vertical
            // gutter (only) to reach the band floor, capped at 1.5×g.
            h = k
            let bandFloor = 0.62 * U
            if 5 * h + 4 * gv < bandFloor {
                gv = min((bandFloor - 5 * h) / 4, 1.5 * g)
            }
        }
        h = min(max(h, 44), 96)
        sideMargin = m
        gutter = g
        verticalGutter = max(gv, g)
        keyWidth = k
        keyHeight = h
        contentWidth = W
    }
}
