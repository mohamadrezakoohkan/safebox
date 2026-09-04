import CoreGraphics
import Testing
@testable import SafeBox

/// The PIN pad's dot-row rules and grid geometry (decisions §2.3, §8).
struct NumpadEntryTests {
    @Test func digitsAppendAndBackspacePops() {
        var entry = NumpadEntry()
        entry.append()
        entry.append()
        entry.append()
        #expect(entry.count == 3)
        entry.removeLast()
        #expect(entry.count == 2)
    }

    @Test func backspaceOnAnEmptyRowIsANoOp() {
        var entry = NumpadEntry()
        entry.removeLast()
        #expect(entry.count == 0)
        #expect(entry.visibleDots == 0)
    }

    @Test func longPressClearEmptiesTheRow() {
        var entry = NumpadEntry()
        for _ in 0..<9 { entry.append() }
        entry.clear()
        #expect(entry.count == 0)
    }

    @Test func dotsAreCappedAtThirtyTwoButPressesStillCount() {
        var entry = NumpadEntry()
        for _ in 0..<40 { entry.append() }
        #expect(entry.count == 40)          // the press happened
        #expect(entry.visibleDots == 32)    // the row stopped growing
        #expect(NumpadEntry.visibleDotCap == TokenRecorder.maxTokens)
    }

    // MARK: - Dot metrics

    @Test func shortRowsKeepTheBaseMetrics() {
        let metrics = NumpadEntry.dotMetrics(columnWidth: 320, dots: 4)
        #expect(metrics.size == NumpadEntry.baseDotSize)
        #expect(metrics.gap == NumpadEntry.baseDotGap)
    }

    @Test func longRowsShrinkUniformlyToFit() {
        let dots = 16
        let width: CGFloat = 320
        let metrics = NumpadEntry.dotMetrics(columnWidth: width, dots: dots)
        #expect(metrics.size < NumpadEntry.baseDotSize)
        #expect(metrics.size == metrics.gap) // uniform
        let total = CGFloat(dots) * metrics.size + CGFloat(dots - 1) * metrics.gap
        #expect(total <= width + 0.001)
    }

    /// Below the floor the row is allowed to overflow the column rather than
    /// shrink into invisibility — 32 dots never fit a 320 pt column at 6 pt.
    @Test func theShrinkHasAFloorOfSix() {
        for width in [CGFloat(40), 320] {
            let metrics = NumpadEntry.dotMetrics(columnWidth: width, dots: 32)
            #expect(metrics.size == NumpadEntry.minDotSize)
            #expect(metrics.gap == NumpadEntry.minDotSize)
        }
    }

    // MARK: - Grid geometry

    @Test func theKeyDiameterIsClampedToTheContract() {
        // A wide column would give (320 − 48)/3 ≈ 90.7 → clamped to 80.
        #expect(NumpadMetrics.keyDiameter(columnWidth: 320) == 80)
        // A narrow one clamps up to 64.
        #expect(NumpadMetrics.keyDiameter(columnWidth: 200) == 64)
        // In between, the formula wins.
        let middle = NumpadMetrics.keyDiameter(columnWidth: 260)
        #expect(middle > 64 && middle < 80)
    }

    @Test func theColumnNeverExceedsItsMaximum() {
        #expect(NumpadMetrics.columnWidth(width: 1_024) == NumpadMetrics.columnMaxWidth)
        #expect(NumpadMetrics.columnWidth(width: 320) < NumpadMetrics.columnMaxWidth)
    }

    @Test func theKeypadLayoutIsThreeByFour() {
        #expect(NumpadKeypad.rows.count == 4)
        #expect(NumpadKeypad.rows.allSatisfy { $0.count == 3 })
        #expect(NumpadKeypad.rows[3] == [.delete, .digit(0), .enter])
    }
}
