import SwiftUI

/// The caption strip, identical on every face (design §5.5): 13 pt, min height
/// 28, centered, up to two lines, 150 ms fade in/out, 120 ms crossfade on text
/// change. Only `WRONG_CODE` renders in the error color.
///
/// It is **never composed in `disguise` mode**. Overt faces put their static
/// face title in the same slot instead (§2.2).
struct CaptionStrip: View {
    let primary: String
    var primaryIsError = false
    var secondary: String?
    let theme: DisguiseTheme
    var horizontalPadding: CGFloat = 20

    var body: some View {
        VStack(spacing: 2) {
            Text(primary)
                .foregroundStyle(primaryIsError ? theme.captionError : theme.caption)
            if let secondary {
                Text(secondary)
                    .foregroundStyle(theme.caption)
            }
        }
        .font(.system(size: 13))
        .multilineTextAlignment(.center)
        .frame(maxWidth: .infinity, minHeight: 28)
        .padding(.horizontal, horizontalPadding)
        .padding(.top, 4)
        .transition(.opacity.combined(with: .move(edge: .top)))
    }
}
