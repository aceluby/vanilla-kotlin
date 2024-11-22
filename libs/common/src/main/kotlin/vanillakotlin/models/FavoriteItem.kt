package vanillakotlin.models

import com.fasterxml.jackson.annotation.JsonIgnore
import vanillakotlin.serde.mapper
import java.time.Instant

data class FavoriteItem(
    @JsonIgnore val id: Long? = null,
    val itemIdentifier: ItemIdentifier,
    val createdTs: Instant? = null,
    val updatedTs: Instant? = null,
    @JsonIgnore val isDeleted: Boolean = false,
) {
    fun buildOutbox(): Outbox {
        return Outbox(
            messageKey = itemIdentifier,
            body = mapper.writeValueAsBytes(this),
        )
    }
}

fun buildDeletedOutbox(
    itemIdentifier: ItemIdentifier,
): Outbox {
    return Outbox(
        messageKey = itemIdentifier,
    )
}
