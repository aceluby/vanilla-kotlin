package vanillakotlin.extensions

import java.time.Duration
import java.time.Instant
import java.time.LocalDate

val today: LocalDate
    get() = LocalDate.now()

val Number.minutesAgo: Instant
    get() = Instant.now().minus(Duration.ofMinutes(this.toLong()))

val Number.minutes: Duration
    get() = Duration.ofMinutes(this.toLong())

val Number.milliseconds: Duration
    get() = Duration.ofMillis(this.toLong())

val Number.seconds: Duration
    get() = Duration.ofSeconds(this.toLong())
