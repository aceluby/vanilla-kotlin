package vanillakotlin.api

import vanillakotlin.models.Thing
import vanillakotlin.models.ThingIdentifier
import vanillakotlin.random.randomThing

fun buildTestThing(thingIdentifier: ThingIdentifier = randomThing()): Thing = Thing(
    id = thingIdentifier,
    productName = "Test Product",
    sellingPrice = 19.99,
)
