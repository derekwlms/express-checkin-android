package com.writestreams.checkin.service

import com.writestreams.checkin.data.local.FamilyMember
import com.writestreams.checkin.data.local.Person
import com.writestreams.checkin.data.local.PersonDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonMatcherTest {

    private fun person(id: String, first: String, last: String, birthdate: String?): Person {
        return Person(
            id = id,
            first_name = first,
            force_first_name = "",
            last_name = last,
            nick_name = "",
            middle_name = "",
            maiden_name = "",
            path = "",
            details = PersonDetails(id, emptyList(), emptyList(), null, birthdate, null),
            family = emptyList<FamilyMember>()
        )
    }

    @Test
    fun exactNameAndBirthdateIsAMatch() {
        val existing = person("123", "Pebbles", "Flintstone", "2020-06-01")
        val result = PersonMatcher.findExistingPerson("Pebbles", "Flintstone", "2020-06-01", listOf(existing))
        assertEquals(PersonMatcher.Result.Match(existing), result)
    }

    @Test
    fun nameMatchIsCaseInsensitiveAndTrimmed() {
        val existing = person("123", "Pebbles", "Flintstone", "2020-06-01")
        val result = PersonMatcher.findExistingPerson(" pebbles ", "FLINTSTONE", "2020-06-01", listOf(existing))
        assertEquals(PersonMatcher.Result.Match(existing), result)
    }

    @Test
    fun differentBirthdateIsNoMatch() {
        val existing = person("123", "Pebbles", "Flintstone", "2020-06-01")
        val result = PersonMatcher.findExistingPerson("Pebbles", "Flintstone", "2021-06-01", listOf(existing))
        assertEquals(PersonMatcher.Result.NoMatch, result)
    }

    @Test
    fun missingBirthdateOnEitherSideIsNoMatch() {
        val existing = person("123", "Pebbles", "Flintstone", null)
        assertEquals(PersonMatcher.Result.NoMatch,
            PersonMatcher.findExistingPerson("Pebbles", "Flintstone", "2020-06-01", listOf(existing)))
        val existingWithDob = person("123", "Pebbles", "Flintstone", "2020-06-01")
        assertEquals(PersonMatcher.Result.NoMatch,
            PersonMatcher.findExistingPerson("Pebbles", "Flintstone", null, listOf(existingWithDob)))
    }

    @Test
    fun offlineCandidatesAreIgnored() {
        val offline = person("OL_1751700000000", "Pebbles", "Flintstone", "2020-06-01")
        val result = PersonMatcher.findExistingPerson("Pebbles", "Flintstone", "2020-06-01", listOf(offline))
        assertEquals(PersonMatcher.Result.NoMatch, result)
    }

    @Test
    fun multipleStrongMatchesAreAmbiguous() {
        val one = person("123", "Pebbles", "Flintstone", "2020-06-01")
        val two = person("456", "Pebbles", "Flintstone", "2020-06-01")
        val result = PersonMatcher.findExistingPerson("Pebbles", "Flintstone", "2020-06-01", listOf(one, two))
        assertTrue(result is PersonMatcher.Result.Ambiguous)
    }

    @Test
    fun birthdateWithTimeSuffixStillMatches() {
        val existing = person("123", "Pebbles", "Flintstone", "2020-06-01 00:00:00")
        val result = PersonMatcher.findExistingPerson("Pebbles", "Flintstone", "2020-06-01", listOf(existing))
        assertEquals(PersonMatcher.Result.Match(existing), result)
    }

    @Test
    fun zeroBirthdateCountsAsAbsent() {
        val existing = person("123", "Pebbles", "Flintstone", "0000-00-00")
        val result = PersonMatcher.findExistingPerson("Pebbles", "Flintstone", "0000-00-00", listOf(existing))
        assertEquals(PersonMatcher.Result.NoMatch, result)
    }
}
