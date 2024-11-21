package vanillakotlin.kafkatransformer

fun buildTestItem(itemIdentifier: ItemIdentifier = randomTcin()): Item {
    return Item(
        id = itemIdentifier,
        description = "READY_FOR_LAUNCH",
        price =
        Item.Classification(
            merchandise =
            Item.Classification.Merchandise(
                departmentId = 1,
                classId = 2,
            ),
        ),
    )
}
