package vanillakotlin.http.clients.item

/*
This file contains the data classes used to deserialize the body for the item graphql response.
*/

data class ItemResponse(
    val data: ItemData?,
) {
    fun toItem(): Item? {
        return data?.item?.let {
            Item(
                id = it.id,
                description = it.description,
                price =
                Item.Classification(
                    merchandise =
                    it.price.merchandise?.let { merchandise ->
                        Item.Classification.Merchandise(
                            departmentId = merchandise.departmentId,
                            classId = merchandise.classId,
                        )
                    },
                ),
            )
        }
    }
}

data class ItemData(
    val item: Item
)
