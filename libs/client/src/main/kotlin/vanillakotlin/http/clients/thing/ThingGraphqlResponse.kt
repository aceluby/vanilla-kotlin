package vanillakotlin.http.clients.thing

import vanillakotlin.models.Thing

/*
This file contains the data classes used to deserialize the body for the item graphql response.
*/

data class ThingResponse(
    val data: ThingData?,
) {
    data class ThingData(
        val thing: Thing,
    )

    fun toThing(): Thing? = data?.thing
}
