package com.writestreams.checkin.service

import com.writestreams.checkin.data.local.Checkin
import com.writestreams.checkin.data.local.Person
import java.time.format.DateTimeFormatter

/** One person with something not yet in Breeze. */
data class PendingItem(
    val personId: String,
    val name: String,
    val parts: List<String>,        // e.g. "new person", "check-in", "check-out from Nov 8"
    val rejection: String? = null   // Breeze refused this person's data; it won't clear on its own
) {
    val detail: String
        get() = parts.joinToString(", ") + (rejection?.let { " · rejected by Breeze: $it" } ?: "")
}

/**
 * Builds the Pending list: everything on this tablet that Breeze doesn't have yet -
 * new people, check-ins and check-outs, from any event - one row per person.
 */
object PendingItems {

    private val DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d")

    fun build(
        unsentCheckins: List<Checkin>,
        pendingNewPersons: List<Person>,
        nameOf: (String) -> String?,
        currentInstanceId: String
    ): List<PendingItem> {
        val parts = linkedMapOf<String, MutableList<String>>()
        pendingNewPersons.forEach { parts.getOrPut(it.id) { mutableListOf() }.add("new person") }
        unsentCheckins.forEach { checkin ->
            val what = if (checkin.checkoutDateTime != null) "check-out" else "check-in"
            val whenLabel = if (checkin.instanceId == currentInstanceId) "" else
                SettingsService.instanceDate(checkin.instanceId)
                    ?.let { " from ${it.format(DATE_FORMAT)}" } ?: " from event ${checkin.instanceId}"
            parts.getOrPut(checkin.personId) { mutableListOf() }.add(what + whenLabel)
        }
        val newPersonNames = pendingNewPersons.associate { it.id to it.fullName() }
        return parts.map { (personId, personParts) ->
            PendingItem(
                personId = personId,
                name = newPersonNames[personId] ?: nameOf(personId) ?: personId,
                parts = personParts
            )
        }.sortedBy { it.name.lowercase() }
    }

    // Marks items Breeze has rejected (keyed by person id), listing them first.
    fun withRejections(items: List<PendingItem>, rejections: Map<String, String>): List<PendingItem> {
        return items.map { it.copy(rejection = rejections[it.personId]) }
            .sortedWith(compareBy({ it.rejection == null }, { it.name.lowercase() }))
    }
}
