package vanillakotlin.models

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Represents a user's favorite thing for bulk processing.
 * This is used as the message format for Kafka messages in the bulk inserter.
 */
data class UserFavoriteThing(
    @JsonProperty("user_name")
    val userName: String,
    @JsonProperty("thing_identifier")
    val thingIdentifier: ThingIdentifier,
    @JsonProperty("is_deleted")
    val isDeleted: Boolean = false,
) 
