package vanillakotlin.models

import vanillakotlin.serde.mapper
import java.time.LocalDateTime

data class FavoriteThing(
    val id: Long? = null,
    val thingIdentifier: ThingIdentifier,
    val createdTs: LocalDateTime = LocalDateTime.now(),
    val updatedTs: LocalDateTime = LocalDateTime.now(),
    val isDeleted: Boolean = false,
) {
    fun buildOutbox(): Outbox = Outbox(
        messageKey = thingIdentifier,
        body = mapper.writeValueAsBytes(this),
    )
}

fun buildDeletedOutbox(thingIdentifier: ThingIdentifier): Outbox = Outbox(
    messageKey = thingIdentifier,
    body = mapper.writeValueAsBytes(FavoriteThing(thingIdentifier = thingIdentifier, isDeleted = true)),
)
