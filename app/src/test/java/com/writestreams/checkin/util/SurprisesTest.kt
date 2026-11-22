package com.writestreams.checkin.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SurprisesTest {
    @Test
    fun matchesAaronAndersonIgnoringCaseAndSpaces() {
        assertTrue(Surprises.isAaronAnderson("Aaron", "Anderson"))
        assertTrue(Surprises.isAaronAnderson(" aaron ", "ANDERSON"))
    }

    @Test
    fun doesNotMatchOtherPeople() {
        assertFalse(Surprises.isAaronAnderson("Aaron", "Andrews"))
        assertFalse(Surprises.isAaronAnderson("Erin", "Anderson"))
        assertFalse(Surprises.isAaronAnderson(null, null))
    }
}
