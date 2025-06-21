package vanillakotlin.models

import com.fasterxml.jackson.annotation.JsonProperty

// using type aliases for some commonly used primitives helps with readability and avoiding some human errors when working
// with parameters (e.g. preventing ordering issues).  They're not as strict as working with e.g. inline classes, but are more flexible

typealias ThingIdentifier = String

data class Thing(
    @JsonProperty("id")
    val id: ThingIdentifier,
    @JsonProperty("product_name")
    val productName: String,
    @JsonProperty("selling_price")
    val sellingPrice: Double,
) {
    data class Price(
        @JsonProperty("selling_price")
        val sellingPrice: Double,
    )
}
