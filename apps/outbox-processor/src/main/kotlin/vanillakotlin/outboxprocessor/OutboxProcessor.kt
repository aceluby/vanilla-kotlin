package vanillakotlin.outboxprocessor

import org.jdbi.v3.core.Jdbi
import org.slf4j.LoggerFactory
import vanillakotlin.db.repository.popOutbox
import vanillakotlin.kafka.models.KafkaOutputMessage
import vanillakotlin.kafka.producer.KafkaSendAsync
import java.time.Duration
import java.util.Timer
import kotlin.concurrent.timerTask
import kotlin.system.exitProcess

typealias UncaughtErrorHandler = (t: Throwable) -> Unit

private val log = LoggerFactory.getLogger(OutboxProcessor::class.java)

// Running in TAP, the default error handler is to halt the process, letting TAP restart the container. This handles occasional glitches
// like broker and network errors. Any more permanent issues should trigger an alert for the engineers to investigate and fix.
// By allowing a function for error handling, we can more easily test and allow different behavior for different scenarios.
val shutdownErrorHandler = { throwable: Throwable ->
    log.atError().setCause(throwable).log("Fatal error encountered during outbox processing. Will halt.")
    exitProcess(1)
}

class OutboxProcessor(
    private val config: Config,
    private val jdbi: Jdbi,
    private val kafkaSendAsync: KafkaSendAsync<ByteArray?>,
    private val uncaughtErrorHandler: UncaughtErrorHandler = shutdownErrorHandler,
) {
    data class Config(
        val pollEvery: Duration,
        val popMessageLimit: Int,
    )

    fun start() {
        // this timer is currently running every X seconds. it could be made to be a lot smarter,
        // for example if it's behind, it could poll faster, and if it's idle, it could poll more slowly
        Timer(true).scheduleAtFixedRate(
            timerTask {
                try {
                    popAndSend()
                } catch (t: Throwable) {
                    uncaughtErrorHandler(t)
                }
            },
            0,
            config.pollEvery.toMillis(),
        )
    }

    /**
     * In a transaction, pop a set of messages from the outbox and send them all.
     * If the send completes successfully, the transaction will commit.
     * If there's an exception, the transaction will rollback and throw the exception.
     */
    fun popAndSend() {
        jdbi.inTransaction<Unit, Exception> { handle ->
            val messages = popOutbox(handle, config.popMessageLimit)
            messages.map { message ->
                log.atDebug().log("Sending message")

                // send all the messages asynchronously
                kafkaSendAsync(
                    KafkaOutputMessage(
                        key = message.messageKey,
                        value = message.body,
                        headers = message.headers,
                    ),
                )
            }.forEach {
                // then wait for all the futures to complete. any errors will throw and rollback the transaction.
                it.join()
            }
        }
    }
}
