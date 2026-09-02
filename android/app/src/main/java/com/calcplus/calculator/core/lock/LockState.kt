package com.calcplus.calculator.core.lock

import com.calcplus.calculator.feature.calculator.CalcKey

sealed interface LockState {
    data object NeedsSetup : LockState
    data object Locked : LockState
    data object Unlocked : LockState
}

sealed interface SetupPhase {
    data object Entry : SetupPhase
    data class Confirm(val pending: List<CalcKey>) : SetupPhase
}

/** Caption strip content: primary banner line + optional secondary hint line. */
data class LockBanner(
    val primary: BannerText,
    val secondary: BannerText? = null,
)

/** String-resource-keyed banner text so the lock core stays context-free. */
enum class BannerText {
    SETUP_ENTRY,
    SETUP_HINT,
    SETUP_TOO_SHORT,
    SETUP_TOO_LONG,
    SETUP_CONFIRM,
    SETUP_MISMATCH,
    SETUP_TRIVIAL_WARNING,
    VERIFY_CURRENT,
    VERIFY_ERROR,
    CHANGE_ENTER_NEW,
    CHANGE_CONFIRM,
}
