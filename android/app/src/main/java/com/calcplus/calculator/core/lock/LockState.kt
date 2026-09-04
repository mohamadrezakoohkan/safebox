package com.calcplus.calculator.core.lock

sealed interface LockState {
    data object NeedsSetup : LockState
    data object Locked : LockState
    data object Unlocked : LockState
}

sealed interface SetupPhase {
    data object Entry : SetupPhase
    /** The captured-but-unstored code, held in memory only, as opaque tokens. */
    data class Confirm(val pending: List<String>) : SetupPhase
}
