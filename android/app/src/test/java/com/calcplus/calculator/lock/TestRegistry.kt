package com.calcplus.calculator.lock

import com.calcplus.calculator.core.disguise.AlphabetDescriptor
import com.calcplus.calculator.core.disguise.DisguiseRegistry
import com.calcplus.calculator.core.domain.repository.PasscodeRepository
import com.calcplus.calculator.feature.calculator.CalculatorDisguise
import com.calcplus.calculator.feature.numpad.NumpadDisguise
import com.calcplus.calculator.feature.pattern.PatternDisguise

/** The production registry, shared by the lock/settings unit tests. */
fun testRegistry(): DisguiseRegistry =
    DisguiseRegistry(listOf(CalculatorDisguise, NumpadDisguise, PatternDisguise))

/**
 * Fake store with call counting: proves sub-minimum / overflowed commits skip
 * the KDF, and lets a test see exactly what the single `set()` wrote.
 */
class FakePasscodeRepository(
    var stored: List<String>? = null,
    var storedFaceId: String? = null,
    var storedAlphabet: AlphabetDescriptor? = null,
    private val failWrites: Boolean = false,
) : PasscodeRepository {
    var matchesCallCount = 0
    var setCallCount = 0

    override suspend fun set(
        tokens: List<String>,
        alphabet: AlphabetDescriptor,
        activeDisguiseId: String,
    ) {
        setCallCount += 1
        if (failWrites) throw java.io.IOException("write failed")
        stored = tokens
        storedAlphabet = alphabet
        storedFaceId = activeDisguiseId
    }

    override suspend fun matches(tokens: List<String>): Boolean {
        matchesCallCount += 1
        return stored == tokens
    }

    override suspend fun activeDisguiseId(): String? = storedFaceId
}
