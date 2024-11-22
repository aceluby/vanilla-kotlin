package vanillakotlin.api

import io.kotest.extensions.system.withEnvironment
import io.kotest.extensions.system.withSystemProperty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

// configuration can be both strongly typed and tested
class ConfigTest {

    @Test fun `default config`() {
        val config = loadConfig<Config>()
        config.metrics.tags["team"] shouldBe "reference" // default value in the MetricsPublisher.Config class
    }

    @Test fun `system property config override`() {
        val expectedValue = randomString()
        // hoplite's environment override capability has two notable features:
        // 1. it uses a `config.override.` prefix to avoid collisions with existing environment variables whose names might match
        //    the application's variable names. e.g. in TAP this can happen with `METRICS_URI` (and did happen previously)
        // 2. it doesn't do any case translation. this prevents problems around naming convention translation.
        // see the hoplite docs for more details
        withSystemProperty("config.override.db.host", expectedValue) {
            val config = loadConfig<Config>()
            config.db.host shouldBe expectedValue
        }
    }

    @Test fun `environment property config override`() {
        val expectedValue = randomString()
        withEnvironment("config.override.db.host", expectedValue) {
            val config = loadConfig<Config>()
            config.db.host shouldBe expectedValue
        }
    }
}
