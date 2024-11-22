package vanillakotlin.extensions

import vanillakotlin.serde.mapper

fun Any.toJsonString(): String = mapper.writeValueAsString(this)
fun Any.toJsonBytes(): ByteArray = mapper.writeValueAsBytes(this)
