package vanillakotlin.models

interface HealthMonitor {
    val name: String
    fun check(): HealthCheckResponse
}

data class HealthCheckResponse(
    val name: String,
    val isHealthy: Boolean,
    val details: String
)
