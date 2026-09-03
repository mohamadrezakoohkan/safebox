import Foundation
import Testing
@testable import SafeBox

/// Pure formatting behind the Details sheet (N2) and the video duration badge
/// (N3), decisions §8. Locale, calendar and time zone are pinned so expected
/// strings do not depend on the simulator's settings.
struct MediaMetadataFormatterTests {
    private let en = Locale(identifier: "en_US")

    // MARK: - Dimensions

    @Test func dimensionsUseTheMultiplicationSign() {
        #expect(MediaMetadataFormatter.dimensions(width: 4032, height: 3024) == "4032 × 3024")
        #expect(MediaMetadataFormatter.dimensions(width: 20, height: 12) == "20 × 12")
    }

    // MARK: - File size

    @Test func fileSizePicksTheUnitAtEachBoundary() {
        // Unit casing ("kB" vs "KB") differs between Foundation generations;
        // the unit choice and the digits are what this pins.
        #expect(MediaMetadataFormatter.fileSize(512, locale: en).lowercased() == "512 bytes")
        #expect(MediaMetadataFormatter.fileSize(1_000, locale: en).lowercased() == "1 kb")
        #expect(MediaMetadataFormatter.fileSize(1_500_000, locale: en).lowercased() == "1.5 mb")
        #expect(MediaMetadataFormatter.fileSize(2_500_000_000, locale: en).lowercased() == "2.5 gb")
    }

    @Test func fileSizeHonorsTheLocale() {
        // German writes the decimal separator as a comma.
        let de = Locale(identifier: "de_DE")
        #expect(MediaMetadataFormatter.fileSize(1_500_000, locale: de).contains("1,5"))
    }

    // MARK: - Type label

    @Test func typeLabelMapsTheSharedTable() {
        let expected: [String: String] = [
            "image/jpeg": "JPEG", "image/png": "PNG", "image/heic": "HEIC", "image/heif": "HEIF",
            "image/gif": "GIF", "image/webp": "WEBP", "image/bmp": "BMP",
            "video/mp4": "MP4", "video/quicktime": "MOV", "video/x-matroska": "MKV",
            "video/webm": "WEBM", "video/3gpp": "3GP",
        ]
        for (mime, label) in expected {
            #expect(MediaMetadataFormatter.typeLabel(mimeType: mime) == label, "\(mime)")
        }
    }

    @Test func typeLabelFallsBackToTheUppercasedSubtype() {
        #expect(MediaMetadataFormatter.typeLabel(mimeType: "image/tiff") == "TIFF")
        #expect(MediaMetadataFormatter.typeLabel(mimeType: "image/x-portable-pixmap") == "X-PORTABLE-PIXMAP")
    }

    @Test func typeLabelIgnoresCaseAndParameters() {
        #expect(MediaMetadataFormatter.typeLabel(mimeType: "IMAGE/JPEG") == "JPEG")
        #expect(MediaMetadataFormatter.typeLabel(mimeType: "video/mp4; codecs=\"avc1\"") == "MP4")
        #expect(MediaMetadataFormatter.typeLabel(mimeType: "png") == "PNG") // bare subtype
    }

    // MARK: - Duration

    @Test func durationUsesMinutesAndSecondsUnderAnHour() {
        #expect(MediaMetadataFormatter.duration(milliseconds: 0) == "0:00")
        #expect(MediaMetadataFormatter.duration(milliseconds: 5_000) == "0:05")
        #expect(MediaMetadataFormatter.duration(milliseconds: 65_000) == "1:05")
        #expect(MediaMetadataFormatter.duration(milliseconds: 3_599_000) == "59:59")
    }

    @Test func durationAddsHoursFromSixtyMinutes() {
        #expect(MediaMetadataFormatter.duration(milliseconds: 3_600_000) == "1:00:00")
        #expect(MediaMetadataFormatter.duration(milliseconds: 3_725_000) == "1:02:05")
        #expect(MediaMetadataFormatter.duration(milliseconds: 36_061_000) == "10:01:01")
    }

    @Test func durationRoundsToTheNearestSecondAndClampsNegatives() {
        #expect(MediaMetadataFormatter.duration(milliseconds: 499) == "0:00")
        #expect(MediaMetadataFormatter.duration(milliseconds: 500) == "0:01")
        #expect(MediaMetadataFormatter.duration(milliseconds: 59_600) == "1:00")
        #expect(MediaMetadataFormatter.duration(milliseconds: -2_000) == "0:00")
    }

    // MARK: - Date

    @Test func dateTimeIsMediumDateWithShortTime() {
        let formatted = MediaMetadataFormatter.dateTime(Self.fixedDate, locale: en,
                                                        calendar: Self.gregorianUTC, timeZone: Self.utc)
        // "Mar 5, 2026 at 2:07 PM" — recent OS versions put a narrow no-break
        // space before "PM", so match the parts rather than the whole string.
        #expect(formatted.contains("Mar 5, 2026"))
        #expect(formatted.contains("2:07"))
        #expect(formatted.hasSuffix("PM"))
    }

    // MARK: - PhotoMetadata rows

    @Test func rowsFollowTheDecidedOrderForAPhoto() {
        let metadata = PhotoMetadata(id: UUID(), width: 4032, height: 3024, byteCount: 1_500_000,
                                     mimeType: "image/heic", importedAt: Self.fixedDate)
        let rows = metadata.rows(locale: en, calendar: Self.gregorianUTC, timeZone: Self.utc)
        #expect(rows.map(\.label) == [VaultCopy.photoInfoDimensions, VaultCopy.photoInfoSize,
                                      VaultCopy.photoInfoType, VaultCopy.photoInfoImported])
        #expect(rows[0].value == "4032 × 3024")
        #expect(rows[1].value.lowercased() == "1.5 mb")
        #expect(rows[2].value == "HEIC")
        #expect(rows[3].value.contains("Mar 5, 2026"))
    }

    @Test func rowsInsertDurationAfterTypeForAVideo() {
        let metadata = PhotoMetadata(id: UUID(), width: 1920, height: 1080, byteCount: 42_000_000,
                                     mimeType: "video/quicktime", importedAt: Self.fixedDate, durationMs: 95_000)
        let rows = metadata.rows(locale: en, calendar: Self.gregorianUTC, timeZone: Self.utc)
        #expect(rows.map(\.label) == [VaultCopy.photoInfoDimensions, VaultCopy.photoInfoSize,
                                      VaultCopy.photoInfoType, VaultCopy.photoInfoDuration,
                                      VaultCopy.photoInfoImported])
        #expect(rows[2].value == "MOV")
        #expect(rows[3].value == "1:35")
    }

    // MARK: - Fixtures

    private static let utc = TimeZone(identifier: "UTC")!

    private static var gregorianUTC: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = utc
        return calendar
    }

    /// 2026-03-05 14:07 UTC.
    private static let fixedDate: Date = {
        var components = DateComponents()
        components.year = 2026
        components.month = 3
        components.day = 5
        components.hour = 14
        components.minute = 7
        return gregorianUTC.date(from: components)!
    }()
}
