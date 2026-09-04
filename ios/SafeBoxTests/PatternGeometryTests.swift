import CoreGraphics
import Testing
@testable import SafeBox

/// The pattern stroke reducer (decisions §2.4). Every rule that would show up
/// in an audit is here, not in the gesture handler.
struct PatternGeometryTests {
    // MARK: - The eight midpoint pairs and their reverses

    @Test func everyMidpointPairAndItsReverse() {
        let expected: [(Int, Int, Int)] = [
            (0, 2, 1), (3, 5, 4), (6, 8, 7),
            (0, 6, 3), (1, 7, 4), (2, 8, 5),
            (0, 8, 4), (2, 6, 4),
        ]
        #expect(expected.count == 8)
        for (a, b, mid) in expected {
            #expect(PatternGeometry.midpoint(from: a, to: b) == mid)
            #expect(PatternGeometry.midpoint(from: b, to: a) == mid)
        }
    }

    @Test func adjacentAndKnightMovesHaveNoMidpoint() {
        // Adjacent, diagonal-adjacent and knight moves cross nothing.
        for (a, b) in [(0, 1), (0, 3), (0, 4), (1, 3), (1, 5), (2, 4), (3, 7), (4, 8), (5, 6)] {
            #expect(PatternGeometry.midpoint(from: a, to: b) == nil)
            #expect(PatternGeometry.midpoint(from: b, to: a) == nil)
        }
    }

    @Test func theTableIsExactlyEightPairs() {
        var found: Set<Set<Int>> = []
        for pair in (0..<9).flatMap({ a in (0..<9).map { (a, $0) } })
        where PatternGeometry.midpoint(from: pair.0, to: pair.1) != nil {
            found.insert([pair.0, pair.1])
        }
        #expect(found.count == 8)
    }

    // MARK: - nodesEntered

    @Test func aFreshNodeEmitsItself() {
        #expect(PatternGeometry.nodesEntered(from: nil, to: 4, selected: []) == [4])
        #expect(PatternGeometry.nodesEntered(from: 0, to: 1, selected: [0]) == [1])
    }

    @Test func anAlreadySelectedNodeEmitsNothing() {
        #expect(PatternGeometry.nodesEntered(from: 1, to: 0, selected: [0, 1]).isEmpty)
    }

    @Test func crossingAnUnselectedMidpointAutoSelectsItFirst() {
        #expect(PatternGeometry.nodesEntered(from: 0, to: 2, selected: [0]) == [1, 2])
        #expect(PatternGeometry.nodesEntered(from: 2, to: 6, selected: [2]) == [4, 6])
    }

    @Test func anAlreadySelectedMidpointIsNotRepeated() {
        #expect(PatternGeometry.nodesEntered(from: 0, to: 2, selected: [1, 0]) == [2])
    }

    // MARK: - The stroke

    @Test func aStrokeNeverRepeatsANode() {
        var stroke = PatternStroke()
        #expect(stroke.enter(node: 0) == ["N0"])
        #expect(stroke.enter(node: 1) == ["N1"])
        #expect(stroke.enter(node: 0).isEmpty)
        #expect(stroke.selected == [0, 1])
    }

    @Test func aStrokeEmitsTheCrossedMidpointBeforeTheDestination() {
        var stroke = PatternStroke()
        _ = stroke.enter(node: 0)
        #expect(stroke.enter(node: 8) == ["N4", "N8"])
        #expect(stroke.selected == [0, 4, 8])
    }

    /// Overflow is impossible by construction: 9 nodes, none repeatable.
    @Test func overflowIsImpossible() {
        var stroke = PatternStroke()
        var emitted: [String] = []
        // Walk every node, three times over.
        for _ in 0..<3 {
            for node in 0..<PatternGeometry.nodeCount {
                emitted.append(contentsOf: stroke.enter(node: node))
            }
        }
        #expect(stroke.selected.count == PatternGeometry.nodeCount)
        #expect(emitted.count <= TokenRecorder.maxTokens)

        var recorder = TokenRecorder()
        for token in emitted { recorder.record(token) }
        #expect(!recorder.overflowed)
    }

    /// TRIVIAL_WARNING is unreachable too: a valid-length pattern has ≥ 4
    /// distinct nodes, so `isTrivial` (one distinct token) can never fire.
    @Test func trivialAndTooLongAreUnreachable() {
        var stroke = PatternStroke()
        var emitted: [String] = []
        for node in [0, 3, 6, 7, 8, 5, 2, 1, 4] {
            emitted.append(contentsOf: stroke.enter(node: node))
        }
        #expect(!PasscodeRules.isTrivial(emitted))
        #expect(PasscodeRules.isValidLength(emitted))
        // A single node cannot be a valid-length commit, so a one-token
        // (therefore "trivial") stroke never reaches the warning.
        var single = PatternStroke()
        let one = single.enter(node: 4)
        #expect(PasscodeRules.isTrivial(one))
        #expect(!PasscodeRules.isValidLength(one))
    }

    @Test func resetEmptiesTheStroke() {
        var stroke = PatternStroke()
        _ = stroke.enter(node: 3)
        stroke.reset()
        #expect(stroke.isEmpty)
    }

    // MARK: - Hit testing

    @Test func nodeCentersAreRowMajor() {
        let cell: CGFloat = 100
        #expect(PatternGeometry.center(of: 0, cell: cell) == CGPoint(x: 50, y: 50))
        #expect(PatternGeometry.center(of: 2, cell: cell) == CGPoint(x: 250, y: 50))
        #expect(PatternGeometry.center(of: 4, cell: cell) == CGPoint(x: 150, y: 150))
        #expect(PatternGeometry.center(of: 8, cell: cell) == CGPoint(x: 250, y: 250))
    }

    @Test func theHitAreaIsSixtyPercentOfTheCell() {
        let cell: CGFloat = 100 // half-extent 30
        #expect(PatternGeometry.node(at: CGPoint(x: 50, y: 50), cell: cell) == 0)
        #expect(PatternGeometry.node(at: CGPoint(x: 79, y: 50), cell: cell) == 0)
        // Just outside the 0.6 square: the gap between nodes selects nothing.
        #expect(PatternGeometry.node(at: CGPoint(x: 90, y: 50), cell: cell) == nil)
        #expect(PatternGeometry.node(at: CGPoint(x: 150, y: 250), cell: cell) == 7)
    }
}
