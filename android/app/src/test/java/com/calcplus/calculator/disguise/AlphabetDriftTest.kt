package com.calcplus.calculator.disguise

import com.calcplus.calculator.R
import com.calcplus.calculator.core.disguise.AlphabetDescriptor
import com.calcplus.calculator.core.disguise.CoverAliases
import com.calcplus.calculator.core.disguise.DisguiseRegistry
import com.calcplus.calculator.feature.calculator.CalcKey
import com.calcplus.calculator.feature.calculator.CalculatorDisguise
import com.calcplus.calculator.feature.numpad.NumpadDisguise
import com.calcplus.calculator.feature.pattern.PatternDisguise
import com.calcplus.calculator.lock.testRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Alphabet drift: a face's descriptor and the tokens it can actually emit must
 * never diverge — a silently added key would change nobody's stored hash but
 * would let a code be entered that could never have been enrolled.
 */
class AlphabetDriftTest {
    @Test
    fun calculatorDescriptorMatchesTheEmittableKeys() {
        val emittable = CalcKey.entries.filter { it.isPasscodeKey }.map { it.id }
        assertEquals(17, emittable.size)
        assertEquals(emittable, CalculatorDisguise.alphabet.tokens)
        assertEquals("calculator", CalculatorDisguise.alphabet.tokenSetId)
        assertEquals(1, CalculatorDisguise.alphabet.alphabetVersion)
        // The commit and clear gestures are NOT tokens.
        assertTrue(CalcKey.EQUALS.id !in CalculatorDisguise.alphabet.tokens)
        assertTrue(CalcKey.CLEAR.id !in CalculatorDisguise.alphabet.tokens)
    }

    @Test
    fun numpadIsTenDigitsThatDeliberatelyOverlapTheCalculator() {
        assertEquals((0..9).map { "D$it" }, NumpadDisguise.alphabet.tokens)
        assertEquals(1, NumpadDisguise.alphabet.alphabetVersion)
        // The overlap is intentional (§2.3): it gives the fail-closed
        // calculator face a chance to still accept a digits-only PIN.
        assertTrue(NumpadDisguise.alphabet.tokens.all { it in CalculatorDisguise.alphabet.tokens })
    }

    @Test
    fun patternIsNineRowMajorNodes() {
        assertEquals(
            listOf("N0", "N1", "N2", "N3", "N4", "N5", "N6", "N7", "N8"),
            PatternDisguise.alphabet.tokens,
        )
        assertEquals(1, PatternDisguise.alphabet.alphabetVersion)
    }

    @Test
    fun noTokenIdContainsTheSeparator() {
        testRegistry().faces.forEach { face ->
            face.alphabet.tokens.forEach { token ->
                assertTrue("'$token' must not contain |", '|' !in token)
            }
        }
    }

    @Test
    fun tokenSetIdEqualsTheDisguiseId() {
        testRegistry().faces.forEach { face ->
            assertEquals(face.id, face.alphabet.tokenSetId)
        }
    }

    @Test
    fun serializationIsTheUniversalPipeJoin() {
        assertEquals("D1|D2|ADD", AlphabetDescriptor.serialize(listOf("D1", "D2", "ADD")))
        assertEquals("N0|N3|N6|N7", AlphabetDescriptor.serialize(listOf("N0", "N3", "N6", "N7")))
    }

    // MARK: registry

    @Test
    fun registryOrderIsCalculatorNumpadPattern() {
        assertEquals(listOf("calculator", "numpad", "pattern"), testRegistry().faces.map { it.id })
    }

    @Test
    fun resolveFailsClosedToTheCalculator() {
        val registry = testRegistry()
        assertSame(CalculatorDisguise, registry.default)
        assertSame(CalculatorDisguise, registry.resolve(null))
        assertSame(CalculatorDisguise, registry.resolve(""))
        assertSame(CalculatorDisguise, registry.resolve("tip-calculator"))
        assertSame(NumpadDisguise, registry.resolve("numpad"))
        assertSame(PatternDisguise, registry.resolve("pattern"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateIdsAreRejected() {
        DisguiseRegistry(listOf(CalculatorDisguise, CalculatorDisguise))
    }

    @Test
    fun exactlyOneFaceIsCovert() {
        val covert = testRegistry().faces.filter { it.isCovert }.map { it.id }
        assertEquals(listOf("calculator"), covert)
    }

    // MARK: cover identities (§9a)

    /**
     * Each face names the alias it wears on the home screen and the label that
     * alias shows. The alias strings must match the manifest exactly — a typo
     * would leave the app with no enabled launcher entry after a switch.
     */
    @Test
    fun everyFaceDeclaresItsCoverIdentity() {
        assertEquals(CoverAliases.CALCULATOR, CalculatorDisguise.coverAlias)
        assertEquals(R.string.cover_name_calculator, CalculatorDisguise.coverName)

        assertEquals(CoverAliases.NOTEPAD, NumpadDisguise.coverAlias)
        assertEquals(R.string.cover_name_notepad, NumpadDisguise.coverName)

        assertEquals(CoverAliases.GALLERY, PatternDisguise.coverAlias)
        assertEquals(R.string.cover_name_gallery, PatternDisguise.coverName)
    }

    @Test
    fun coverAliasesAreFullyQualifiedAndUnique() {
        val aliases = testRegistry().faces.map { it.coverAlias }
        assertEquals(3, aliases.size)
        // Two faces sharing an alias would make the swap unable to tell them
        // apart, and one of them would silently wear the other's identity.
        assertEquals(aliases.size, aliases.toSet().size)
        aliases.forEach {
            assertTrue("'$it' must be fully qualified", it.startsWith("com.calcplus.calculator."))
        }
    }

    /** The default face's alias is first, which is the one the manifest enables. */
    @Test
    fun theDefaultFaceWearsTheShippedIdentity() {
        assertEquals(CoverAliases.CALCULATOR, testRegistry().default.coverAlias)
    }
}
