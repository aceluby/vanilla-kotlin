package vanillakotlin.models

// using type aliases for some commonly used primitives helps with readability and avoiding some human errors when working
// with parameters (e.g. preventing ordering issues).  They're not as strict as working with e.g. inline classes, but are more flexible

typealias ItemIdentifier = String

data class Item(
    val id: ItemIdentifier,
    val description: String,
    val price: Price,
) {

    data class Price(
        val cost: Double,
        val sellingPrice: Double,
    )
}
