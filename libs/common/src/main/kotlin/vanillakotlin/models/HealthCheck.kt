package vanillakotlin.models

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.CoroutineContext

interface HealthMonitor {
    val name: String
    fun check(): HealthCheckResponse
}

data class HealthCheckResponse(
    val name: String,
    val isHealthy: Boolean,
    val details: String
)

fun healthCheckAll(
    healthMonitors: List<HealthMonitor>,
    coroutineContext: CoroutineContext = Dispatchers.IO
): List<HealthCheckResponse> {
    return runBlocking(context = coroutineContext) {
        healthMonitors.map { monitor ->
            async {
                try {
                    monitor.check()
                } catch (t: Throwable) {
                    HealthCheckResponse(
                        name = monitor.name,
                        isHealthy = false,
                        details = "${t::class.qualifiedName} - ${t.message}"
                    )
                }
            }
        }.awaitAll()
    }
}

fun allFailedChecks(
    healthMonitors: List<HealthMonitor>,
    coroutineContext: CoroutineContext = Dispatchers.IO
): List<HealthCheckResponse> = healthCheckAll(healthMonitors, coroutineContext).filter { !it.isHealthy }
