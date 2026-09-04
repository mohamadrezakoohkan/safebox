import CoreGraphics

/// The pattern grid's pure rules (decisions §2.4). Everything a security or
/// behavior audit would look at lives here, not in the gesture handler.
enum PatternGeometry {
    static let side = 3
    static let nodeCount = 9
    static let gridMaxSide: CGFloat = 300
    static let nodeHitFactor: CGFloat = 0.6
    static let restingNodeDiameter: CGFloat = 16
    static let selectedNodeDiameter: CGFloat = 28
    static let lineWidth: CGFloat = 6
    static let lineOpacity: Double = 0.7

    /// Row-major: `N0 N1 N2` top, `N3 N4 N5` middle, `N6 N7 N8` bottom.
    static func token(_ index: Int) -> String { "N\(index)" }
    static let tokens: [String] = (0..<nodeCount).map(token)

    /// The exhaustive midpoint table: pairs two apart in a straight line, plus
    /// their reverses (handled by `midpoint(from:to:)`).
    static let midpointTable: [(a: Int, b: Int, mid: Int)] = [
        (0, 2, 1), (3, 5, 4), (6, 8, 7),
        (0, 6, 3), (1, 7, 4), (2, 8, 5),
        (0, 8, 4), (2, 6, 4),
    ]

    static func midpoint(from a: Int, to b: Int) -> Int? {
        for entry in midpointTable where (entry.a == a && entry.b == b) || (entry.a == b && entry.b == a) {
            return entry.mid
        }
        return nil
    }

    /// The nodes newly entered by moving the finger onto `to`.
    ///
    /// - An already-selected node contributes nothing.
    /// - Crossing an unselected midpoint auto-selects it first.
    static func nodesEntered(from: Int?, to: Int, selected: [Int]) -> [Int] {
        guard !selected.contains(to) else { return [] }
        guard let from,
              let mid = midpoint(from: from, to: to),
              !selected.contains(mid) else {
            return [to]
        }
        return [mid, to]
    }

    static func center(of index: Int, cell: CGFloat) -> CGPoint {
        CGPoint(x: (CGFloat(index % side) + 0.5) * cell,
                y: (CGFloat(index / side) + 0.5) * cell)
    }

    /// Hit area: a square of `cell × nodeHitFactor` centered on the node.
    static func node(at point: CGPoint, cell: CGFloat) -> Int? {
        let half = cell * nodeHitFactor / 2
        for index in 0..<nodeCount {
            let c = center(of: index, cell: cell)
            if abs(point.x - c.x) <= half && abs(point.y - c.y) <= half {
                return index
            }
        }
        return nil
    }
}

/// One stroke. A node cannot repeat, so a commit carries 1–9 tokens and
/// **overflow is impossible by construction**.
struct PatternStroke: Equatable, Sendable {
    private(set) var selected: [Int] = []

    var isEmpty: Bool { selected.isEmpty }

    mutating func reset() { selected.removeAll() }

    /// Enters `node`, returning the token IDs to emit, in order.
    mutating func enter(node: Int) -> [String] {
        let entered = PatternGeometry.nodesEntered(from: selected.last,
                                                   to: node,
                                                   selected: selected)
        selected.append(contentsOf: entered)
        return entered.map(PatternGeometry.token)
    }
}
