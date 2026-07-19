package com.writestreams.checkin.service

import com.writestreams.checkin.data.local.Person

/**
 * Decides whether an offline-created person already exists in the Breeze
 * mirror (e.g. a member added through the Breeze web app while the tablet was
 * offline, then re-added at check-in as a guest). Used before pushing an OL_
 * person so we link to the existing record instead of creating a duplicate.
 *
 * Policy: only a *strong* match links automatically - same first and last name
 * (case-insensitive) AND the same birthdate, both present. Anything weaker
 * creates a new person as before (a duplicate in Breeze is fixable by an
 * admin; linking check-ins to the wrong person is not). Multiple strong
 * matches means Breeze itself has duplicates - we skip and leave the person
 * pending rather than guess.
 */
object PersonMatcher {

    sealed class Result {
        data class Match(val person: Person) : Result()
        data class Ambiguous(val candidates: List<Person>) : Result()
        object NoMatch : Result()
    }

    fun findExistingPerson(
        firstName: String,
        lastName: String,
        birthdate: String?,
        candidates: List<Person>
    ): Result {
        val normalizedBirthdate = normalizeBirthdate(birthdate) ?: return Result.NoMatch
        val matches = candidates.filter { candidate ->
            !candidate.id.startsWith(CheckinService.OFFLINE_BREEZE_ID_PREFIX) &&
                    candidate.first_name.trim().equals(firstName.trim(), ignoreCase = true) &&
                    candidate.last_name.trim().equals(lastName.trim(), ignoreCase = true) &&
                    normalizeBirthdate(candidate.details.birthdate) == normalizedBirthdate
        }
        return when {
            matches.isEmpty() -> Result.NoMatch
            matches.size == 1 -> Result.Match(matches.first())
            else -> Result.Ambiguous(matches)
        }
    }

    // Breeze birthdates are "YYYY-MM-DD" (sometimes with a time suffix);
    // blank or zero dates count as absent.
    fun normalizeBirthdate(birthdate: String?): String? {
        val datePart = birthdate?.trim()?.take(10) ?: return null
        return datePart.takeIf { it.length == 10 && !it.startsWith("0000") }
    }
}
