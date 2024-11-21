package vanillakotlin.models

import com.fasterxml.jackson.annotation.JsonIgnore
import java.time.Instant

data class UserFavoriteTcin(
    @JsonIgnore val id: Long? = null,
    val userName: UserName,
    val itemIdentifier: ItemIdentifier,
    val createdTs: Instant? = null,
    val updatedTs: Instant? = null,
    @JsonIgnore val isDeleted: Boolean = false,
) {
    fun buildOutbox(): Outbox {
        return Outbox(
            messageKey = "${this.userName}:${this.itemIdentifier}",
            body = mapper.writeValueAsBytes(this),
        )
    }

    companion object {
        fun parseKey(key: String?): Pair<UserName, ItemIdentifier> {
            if (key == null) {
                throw IllegalArgumentException("key cannot be null")
            }
            val (userName, tcin) = key.split(":")
            return userName to tcin
        }
    }
}

fun buildDeletedOutbox(
    userName: UserName,
    itemIdentifier: ItemIdentifier,
): Outbox {
    return Outbox(
        messageKey = "$userName:$itemIdentifier",
    )
}
