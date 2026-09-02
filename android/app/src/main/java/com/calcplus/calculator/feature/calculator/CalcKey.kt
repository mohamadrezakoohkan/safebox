package com.calcplus.calculator.feature.calculator

/**
 * Canonical calculator key identifiers shared with iOS. The [id] values are the
 * cross-platform serialization contract: a passcode is the ordered key IDs
 * joined with "|" (e.g. "D7|ADD|D3|DOT"), which is the exact input to the KDF
 * on both platforms.
 */
enum class CalcKey(val id: String) {
    D0("D0"), D1("D1"), D2("D2"), D3("D3"), D4("D4"),
    D5("D5"), D6("D6"), D7("D7"), D8("D8"), D9("D9"),
    DOT("DOT"), ADD("ADD"), SUB("SUB"), MUL("MUL"), DIV("DIV"),
    PCT("PCT"), SIGN("SIGN"),
    // Non-passcode keys: EQUALS commits, CLEAR is the "start over" gesture.
    CLEAR("CLEAR"), EQUALS("EQUALS");

    val isPasscodeKey: Boolean
        get() = this != CLEAR && this != EQUALS

    val digitValue: Int?
        get() = when (this) {
            D0 -> 0; D1 -> 1; D2 -> 2; D3 -> 3; D4 -> 4
            D5 -> 5; D6 -> 6; D7 -> 7; D8 -> 8; D9 -> 9
            else -> null
        }

    companion object {
        fun serialize(sequence: List<CalcKey>): String =
            sequence.joinToString("|") { it.id }
    }
}
