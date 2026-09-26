package dev.shadowcrawler.flashcards.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MatchSpokenChoiceTest {

    private val choices = listOf("Service", "Downward API", "ReplicaSet", "EndpointSlice")

    @Test
    fun `bare digit selects by position`() {
        assertEquals("Service", matchSpokenChoice("1", choices))
        assertEquals("Downward API", matchSpokenChoice("2", choices))
        assertEquals("EndpointSlice", matchSpokenChoice("4", choices))
    }

    @Test
    fun `number word and ordinal word select by position`() {
        assertEquals("Service", matchSpokenChoice("one", choices))
        assertEquals("ReplicaSet", matchSpokenChoice("three", choices))
        assertEquals("Downward API", matchSpokenChoice("second", choices))
    }

    @Test
    fun `lead-in phrasing is stripped before matching`() {
        assertEquals("Service", matchSpokenChoice("option 1", choices))
        assertEquals("ReplicaSet", matchSpokenChoice("number three", choices))
        assertEquals("Downward API", matchSpokenChoice("the second one", choices))
        assertEquals("Service", matchSpokenChoice("answer one", choices))
    }

    @Test
    fun `plain spoken answer matches choice text directly`() {
        assertEquals("ReplicaSet", matchSpokenChoice("ReplicaSet", choices))
        assertEquals("ReplicaSet", matchSpokenChoice("replicaset", choices))
        assertEquals("Downward API", matchSpokenChoice("Downward API.", choices))
    }

    @Test
    fun `out of range digit does not match`() {
        assertNull(matchSpokenChoice("9", choices))
        assertNull(matchSpokenChoice("0", choices))
    }

    @Test
    fun `unrecognized speech does not match`() {
        assertNull(matchSpokenChoice("I have no idea", choices))
        assertNull(matchSpokenChoice("", choices))
    }
}
