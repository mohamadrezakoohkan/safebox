import Foundation

/// The one-way face→host token stream (iteration-3 decisions §1.2). A face is a
/// renderer and an input device: it emits these and learns nothing back — not
/// whether a commit matched, not whether the buffer overflowed.
///
/// `removeLast` is the fourth case added in iteration 3; only the PIN pad emits
/// it. The calculator and the pattern never do.
enum DisguiseEvent: Equatable, Sendable {
    /// One covert-input interaction, carrying its canonical token ID.
    case token(String)
    /// Pop the last token. Never resets the overflow flag (§1.2, sticky).
    case removeLast
    /// The face's designated commit gesture fired.
    case commit
    /// The face's designated start-over gesture fired.
    case clear
}

/// The host-driven rendering mode of a lock face. Exactly the pinned four.
enum DisguiseMode: Equatable, Sendable {
    /// The armed lock screen. No caption slot is composed; overt faces put
    /// their own static title there instead (§2.2).
    case disguise
    case captureNew
    case confirmNew
    case verifyCurrent
}
