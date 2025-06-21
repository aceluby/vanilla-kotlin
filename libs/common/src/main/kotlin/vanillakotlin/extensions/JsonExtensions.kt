package vanillakotlin.extensions

import vanillakotlin.serde.mapper

fun Any.toJsonString(): String = mapper.writeValueAsString(this)
