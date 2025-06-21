package vanillakotlin.metrics

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.DoubleCounter
import io.opentelemetry.api.metrics.LongHistogram
import io.opentelemetry.api.metrics.MeterProvider
import io.opentelemetry.api.metrics.ObservableDoubleGauge
import kotlin.system.measureTimeMillis
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

typealias PublishCounterMetric = (name: String, tags: Map<String, String>) -> Unit
typealias PublishCounterMetricAmount = (name: String, tags: Map<String, String>, amount: Double) -> Unit
typealias PublishTimerMetric = (name: String, tags: Map<String, String>, duration: Duration) -> Unit
typealias PublishDistributionSummaryMetric = (name: String, tags: Map<String, String>, value: Double) -> Unit
typealias PublishGaugeMetric = (name: String, tags: Map<String, String>, supplier: () -> Number?) -> ObservableDoubleGauge
typealias Gauge = ObservableDoubleGauge

class OtelMetrics(
    private val config: Config,
    meterProvider: MeterProvider = GlobalOpenTelemetry.getMeterProvider(),
) {
    data class Config(
        val tags: Map<String, String>,
    )

    companion object {
        const val PROVIDER_NAME = "vanillakotlin.metrics.OtelMetricsPublisher"
    }

    private val meter = meterProvider.get(PROVIDER_NAME)
    private val timers = mutableMapOf<String, LongHistogram>()
    private val counters = mutableMapOf<String, DoubleCounter>()
    private val gauges = mutableMapOf<String, ObservableDoubleGauge>()

    // used as drop-in replacement for the micrometer timer
    fun publishTimerMetric(
        name: String,
        tags: Map<String, String>,
        duration: Duration,
    ) = timers.getOrPut(name) {
        meter.histogramBuilder(name)
            .setDescription("")
            .setUnit("ms")
            .ofLongs()
            .build()
    }.record(
        duration.inWholeMilliseconds,
        Attributes.builder().apply {
            (config.tags + tags).map { (k, v) -> put(AttributeKey.stringKey(k), v) }
        }.build(),
    )

    // used as drop-in replacement for the micrometer counter
    fun publishCounterMetric(
        name: String,
        tags: Map<String, String>,
    ) = counters.getOrPut(name) {
        meter.counterBuilder(name)
            .setDescription("")
            .setUnit("unit")
            .ofDoubles()
            .build()
    }.add(
        1.0,
        Attributes.builder().apply {
            (config.tags + tags).map { (k, v) -> put(AttributeKey.stringKey(k), v) }
        }.build(),
    )

    // used as drop-in replacement for the micrometer counter amount
    fun publishCounterMetricAmount(
        name: String,
        tags: Map<String, String>,
        amount: Double,
    ) = counters.getOrPut(name) {
        meter.counterBuilder(name)
            .setDescription("")
            .setUnit("unit")
            .ofDoubles()
            .build()
    }.add(
        amount,
        Attributes.builder().apply {
            (config.tags + tags).map { (k, v) -> put(AttributeKey.stringKey(k), v) }
        }.build(),
    )

    fun publishGaugeMetric(
        name: String,
        tags: Map<String, String>,
        supplier: () -> Number?,
    ): ObservableDoubleGauge = gauges.getOrPut(name) {
        meter.gaugeBuilder(name)
            .setDescription("")
            .setUnit("unit")
            .buildWithCallback {
                it.record(
                    supplier()?.toDouble() ?: Double.NaN,
                    Attributes.builder().apply { (config.tags + tags).map { (k, v) -> put(AttributeKey.stringKey(k), v) } }.build(),
                )
            }
    }
}

fun PublishTimerMetric.time(
    metricName: String,
    tags: Map<String, String> = emptyMap(),
    block: () -> Unit,
) {
    measureTimeMillis { block() }.also {
        this(metricName, tags, it.milliseconds)
    }
}
