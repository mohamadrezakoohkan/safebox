package com.calcplus.calculator.markdown

import com.calcplus.calculator.core.markdown.NoteDerivation
import org.junit.Assert.assertEquals
import org.junit.Test

/** Asserts the shared derivation example table from the idea plan §3.2. */
class NoteDerivationTest {
    @Test
    fun headingWithList() {
        val derived = NoteDerivation.derive("# Shopping\n- milk\n- eggs")
        assertEquals("Shopping", derived.title)
        assertEquals("milk eggs", derived.snippet)
    }

    @Test
    fun boldStartWithBlankLine() {
        val derived = NoteDerivation.derive("**Bold** start\n\nsecond para")
        assertEquals("Bold start", derived.title)
        assertEquals("second para", derived.snippet)
    }

    @Test
    fun checklistLines() {
        val derived = NoteDerivation.derive("- [ ] pack bags\n- [x] tickets")
        assertEquals("pack bags", derived.title)
        assertEquals("tickets", derived.snippet)
    }

    @Test
    fun inlineCode() {
        val derived = NoteDerivation.derive("`config` notes")
        assertEquals("config notes", derived.title)
        assertEquals("", derived.snippet)
    }

    @Test
    fun emptyBody() {
        val derived = NoteDerivation.derive("")
        assertEquals("", derived.title)
        assertEquals("", derived.snippet)
    }

    @Test
    fun leadingBlankLinesSkipped() {
        val derived = NoteDerivation.derive("\n\n\nFirst real line\nmore")
        assertEquals("First real line", derived.title)
        assertEquals("more", derived.snippet)
    }

    @Test
    fun titleLineExcludedFromSnippet() {
        val derived = NoteDerivation.derive("Title\nTitle again\nbody")
        assertEquals("Title again body", derived.snippet)
    }

    @Test
    fun numberedListMarkerStripped() {
        val derived = NoteDerivation.derive("1. first step\n2. second step")
        assertEquals("first step", derived.title)
        assertEquals("second step", derived.snippet)
    }
}
