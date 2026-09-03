import UIKit

/// Tiny opaque PNG for tests that need a real image through `PhotoFileStore`.
/// Shared so new suites do not each re-implement the renderer.
enum TestPNG {
    static func data(width: Int = 20, height: Int = 12, color: UIColor = .systemIndigo) -> Data {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: width, height: height), format: format)
        let image = renderer.image { ctx in
            color.setFill()
            ctx.fill(CGRect(x: 0, y: 0, width: width, height: height))
        }
        return image.pngData()!
    }
}
