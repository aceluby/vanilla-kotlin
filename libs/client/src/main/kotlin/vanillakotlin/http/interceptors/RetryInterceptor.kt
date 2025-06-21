package vanillakotlin.http.interceptors

import io.github.resilience4j.core.IntervalFunction
import io.github.resilience4j.kotlin.retry.RetryConfig
import io.github.resilience4j.kotlin.retry.RetryRegistry
import io.github.resilience4j.retry.Retry
import okhttp3.Interceptor
import okhttp3.Response
import vanillakotlin.metrics.PublishCounterMetric

class RetryInterceptor(
    private val config: Config,
    private val publishCounterMetric: PublishCounterMetric,
) : Interceptor {
    data class Config(
        val maxAttempts: Int = 3,
        val initialRetryDelayMs: Long = 1000L,
        val maxRetryDelayMs: Long = 10000L,
        val retryNonErrorCodes: Set<Int> = emptySet(),
    )

    private val retrySupplier = RetryConfig<Response> {
        maxAttempts(config.maxAttempts)
        intervalFunction(IntervalFunction.ofExponentialBackoff(config.initialRetryDelayMs, 2.0, config.maxRetryDelayMs))
        retryOnResult { response -> with(response) { code >= 500 || code in config.retryNonErrorCodes } }
        consumeResultBeforeRetryAttempt { _, response ->
            val telemetryTag = response.request.tag(TelemetryTag::class.java)
            if (telemetryTag != null) {
                publishCounterMetric("http.request.retry.count", telemetryTag.metricTags)
            }
            response.close()
        }
    }.let { retryConfig ->
        RetryRegistry { withRetryConfig(retryConfig) }.retry("okhttp")
    }.let { retry ->
        Retry.decorateCheckedFunction(retry) { chain: Interceptor.Chain -> with(chain) { proceed(request()) } }
    }

    override fun intercept(chain: Interceptor.Chain): Response = retrySupplier.apply(chain)
}
