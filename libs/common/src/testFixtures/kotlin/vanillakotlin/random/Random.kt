package vanillakotlin.random

import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.InstantRange
import io.kotest.property.arbitrary.alphanumeric
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.byteArray
import io.kotest.property.arbitrary.instant
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import java.time.Instant
import kotlin.math.pow

fun randomString(size: Int = 10) = Arb.string(size, Codepoint.alphanumeric()).sample(RandomSource.default()).value

fun randomInt(range: IntRange = 1..Int.MAX_VALUE) = Arb.int(range).sample(RandomSource.default()).value

fun randomLong(range: LongRange = 1L..Long.MAX_VALUE) = Arb.long(range).sample(RandomSource.default()).value

fun randomByteArray(size: Int = 10) = Arb.byteArray(Arb.int(size..size), Arb.byte()).sample(RandomSource.default()).value

fun randomThing() = randomInt(0 until 10.0.pow(8).toInt()).toString().padStart(8, '0')

fun randomUsername() = randomString(size = 7)

fun randomInstant(range: InstantRange = Instant.MIN..Instant.MAX) = Arb.instant(range).sample(RandomSource.default()).value

fun randomHeaders(
    keySize: Int = 10,
    valueSize: Int = 10,
    minSize: Int = 0,
    maxSize: Int = 100,
    slippage: Int = 10,
) = Arb.map(
    Arb.string(keySize, Codepoint.alphanumeric()),
    Arb.byteArray(Arb.int(valueSize..valueSize), Arb.byte()),
    minSize,
    maxSize,
    slippage,
).sample(RandomSource.default()).value
