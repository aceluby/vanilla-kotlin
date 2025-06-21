package vanillakotlin.config

import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.PropertySource
import org.slf4j.LoggerFactory
import java.io.File

val log = LoggerFactory.getLogger("vanillakotlin.config.Configuration")

/**
 * The sources are first-in-wins, so define them in order of highest priority descending.
 */
fun buildOrderedSources(): List<PropertySource> {
    // See the [hoplite docs](https://github.com/sksamuel/hoplite#property-sources) on property sources that are enabled by default.
    // For example, you can set a `config.override.http.server.port=8090` environment variable or system property to override the
    // `server.port` configuration value.
    val sources = mutableListOf<PropertySource>()

    if (System.getenv().containsKey("CI")) {
        // we're running in the CI environment - load the ci.conf file
        sources.add(PropertySource.resource("/ci.conf", optional = true))
    } else if (System.getenv().containsKey("CLOUD_ENVIRONMENT")) {
        // we're running in a deployed environment. Load secrets and configuration specific to the environment.
        sources.add(PropertySource.file(File("/path/to/deployed/secret/restricted/restricted-secret.conf"), optional = true))
        sources.add(PropertySource.file(File("/path/to/deployed/secret/secret.conf"), optional = true))
        sources.add(PropertySource.file(File("/path/to/deployed/config/environment.conf"), optional = true))
    } else {
        // running locally - reference a local.conf file for local development configuration overrides.
        sources.add(PropertySource.resource("/local.conf", optional = true))
    }

    // finally add the default.conf file to the configuration sources
    sources.add(PropertySource.resource("/default.conf"))

    return sources
}

/**
 * Load config for the given class destination
 */
inline fun <reified T> loadConfig(): T {
    val configBuilder = ConfigLoaderBuilder.default()
    buildOrderedSources().forEach { propertySource -> configBuilder.addSource(propertySource) }
    try {
        return configBuilder
            .build()
            .loadConfigOrThrow()
    } catch (t: Throwable) {
        // configuration errors were getting swallowed in CI, so adding some explicit logging
        log.atError().setCause(t).log("Error loading configuration")
        throw t
    }
}
