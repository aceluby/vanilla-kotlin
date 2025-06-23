package vanillakotlin.http.interceptors

/**
 * Tag used for caching. Everything here will be used to generate a cache key.
 */
data class CacheTag(
    val context: String,
    val key: String,
) {
    fun cacheKey(): String = "$context:$key"
}

/**
 * Tag used for metrics and logging. Everything here might be logged; only service and endpoint will be influx'd.
 * @param service The top-level api name being called. e.g for /digital_items/v1/lite/ the service might be "digital_items"
 * @param endpoint The low-level api name being called. e.g. "lite" using the above example.
 */
data class TelemetryTag(
    val service: String,
    val endpoint: String,
    val logMap: Map<String, String?> = emptyMap(),
    val metricTags: Map<String, String> = mapOf("service" to service, "endpoint" to endpoint),
) {
    init {
        (listOf(service, endpoint) + metricTags.values).forEach { tag ->
            check(tag == tag.cleanedForMetrics()) { "invalid metrics tag: $service" }
        }
    }

    // https://docs.influxdata.com/influxdb/v1.7/write_protocols/line_protocol_tutorial/#special-characters-and-keywords
    // we take it one step farther and strip out anything that's not alphanumeric, '.', '/', '-', or '_'
    private fun String.cleanedForMetrics(): String = replace(Regex("[^a-zA-Z0-9._\\-/]"), "_")
}

/**
 * Build this up with each request to provide metadata used to control the request behavior, and add metrics/logging identifiers
 */
data class RequestContext(
    val telemetryTag: TelemetryTag,
    val cacheTag: CacheTag? = null,
)
