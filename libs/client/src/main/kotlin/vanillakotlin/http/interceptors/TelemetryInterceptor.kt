package vanillakotlin.http.interceptors

import org.slf4j.LoggerFactory
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import vanillakotlin.metrics.PublishTimerMetric
import kotlin.time.Duration.Companion.milliseconds

class TelemetryInterceptor(
    private val publishTimerMetric: PublishTimerMetric,
) : Interceptor {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val startTimeMillis = System.currentTimeMillis()

        val response =
            try {
                chain.proceed(request)
            } catch (e: Throwable) {
                log.warn("http client call exception occurred", e)

                publishTimerMetric(
                    "http.request",
                    mapOf("status" to e.javaClass.simpleName, "source" to "remote") + request.metricTags,
                    (System.currentTimeMillis() - startTimeMillis).milliseconds,
                )
                throw e
            }

        publishTimerMetric(
            "http.request",
            mapOf("status" to response.code.toString(), "source" to "remote") + request.metricTags,
            (System.currentTimeMillis() - startTimeMillis).milliseconds,
        )

        return response
    }

    private val Request.metricTags: Map<String, String>
        get() = this.tag(TelemetryTag::class.java)?.metricTags ?: emptyMap()
}
