import Foundation

/// Pure formatting helpers for the media metadata surfaced in the vault
/// (decisions §8): the Details sheet (N2) and the video duration badge (N3).
/// Every function is deterministic for a given locale/calendar/time zone so it
/// is unit-testable; UI callers pass the defaults (the device's own settings).
enum MediaMetadataFormatter {
    /// Shared MIME-subtype → label table, identical on Android. Anything not
    /// listed falls back to the uppercased subtype.
    static let typeLabels: [String: String] = [
        "jpeg": "JPEG",
        "png": "PNG",
        "heic": "HEIC",
        "heif": "HEIF",
        "gif": "GIF",
        "webp": "WEBP",
        "bmp": "BMP",
        "mp4": "MP4",
        "quicktime": "MOV",
        "x-matroska": "MKV",
        "webm": "WEBM",
        "3gpp": "3GP",
    ]

    /// "W × H" — U+00D7 multiplication sign with a space on each side.
    static func dimensions(width: Int, height: Int) -> String {
        "\(width) × \(height)"
    }

    /// Human-readable size in the platform's file style (decimal units:
    /// bytes → KB → MB → GB), e.g. "1.5 MB". `ByteCountFormatStyle` is the
    /// `FormatStyle` face of `ByteCountFormatter`; it is used here because it
    /// takes an explicit locale, which the class-based formatter does not.
    static func fileSize(_ byteCount: Int, locale: Locale = .autoupdatingCurrent) -> String {
        ByteCountFormatStyle(style: .file, locale: locale).format(Int64(byteCount))
    }

    /// Localized medium date + short time, e.g. "Mar 5, 2026 at 2:07 PM".
    static func dateTime(_ date: Date,
                         locale: Locale = .autoupdatingCurrent,
                         calendar: Calendar = .autoupdatingCurrent,
                         timeZone: TimeZone = .autoupdatingCurrent) -> String {
        date.formatted(Date.FormatStyle(date: .abbreviated, time: .shortened,
                                        locale: locale, calendar: calendar, timeZone: timeZone))
    }

    /// Label for a MIME type: the subtype mapped through `typeLabels`, else
    /// the uppercased subtype. Matching is case-insensitive and ignores any
    /// parameters after ";" ("IMAGE/JPEG" → "JPEG").
    static func typeLabel(mimeType: String) -> String {
        let withoutParameters = mimeType.split(separator: ";", maxSplits: 1).first.map(String.init) ?? ""
        let subtype = withoutParameters.split(separator: "/", maxSplits: 1).last.map(String.init) ?? withoutParameters
        let key = subtype.trimmingCharacters(in: .whitespaces).lowercased()
        return typeLabels[key] ?? key.uppercased()
    }

    /// Duration as `m:ss` under one hour and `h:mm:ss` from one hour.
    /// Milliseconds round to the nearest whole second (so a 59.6 s clip reads
    /// "1:00", not "0:59"); negative input reads as zero.
    static func duration(milliseconds: Int) -> String {
        let clamped = max(0, milliseconds)
        let totalSeconds = clamped / 1000 + (clamped % 1000 >= 500 ? 1 : 0)
        let hours = totalSeconds / 3600
        let minutes = (totalSeconds % 3600) / 60
        let seconds = totalSeconds % 60
        if hours > 0 {
            return "\(hours):\(twoDigits(minutes)):\(twoDigits(seconds))"
        }
        return "\(minutes):\(twoDigits(seconds))"
    }

    private static func twoDigits(_ value: Int) -> String {
        value < 10 ? "0\(value)" : "\(value)"
    }
}
