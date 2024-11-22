package vanillakotlin.app

import org.slf4j.LoggerFactory
import vanillakotlin.extensions.eventually

private val log = LoggerFactory.getLogger("ReferenceApp")

interface VanillaApp : AutoCloseable {
    fun start()
}

// helper function to start an application and add a shutdown hook to gracefully close it
fun runApplication(block: () -> VanillaApp) =
    block().use { app ->
        log.atInfo().log { "adding shutdown hook to the application" }
        Runtime.getRuntime().addShutdownHook(
            Thread {
                runCatching {
                    eventually {
                        app.close()
                    }
                }.onFailure {
                    log.atError().log { "shutdown hook failed to complete within 10 seconds; halting the runtime." }
                    Runtime.getRuntime().halt(1)
                }
            },
        )
        app.start()
        Thread.sleep(Long.MAX_VALUE)
    }
