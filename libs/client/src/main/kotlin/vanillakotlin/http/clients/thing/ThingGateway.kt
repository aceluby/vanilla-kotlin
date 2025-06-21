package vanillakotlin.http.clients.thing

import com.fasterxml.jackson.module.kotlin.readValue
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import vanillakotlin.http.interceptors.CacheTag
import vanillakotlin.http.interceptors.TelemetryTag
import vanillakotlin.models.Thing
import vanillakotlin.models.ThingIdentifier
import vanillakotlin.serde.mapper

/**
 * using a SAM interface here allows for easier testing, especially of the FavoriteThingsService coroutines activity
 *
 * SAM interface for [ThingGateway.getThingDetails]
 */
fun interface GetThingDetails {
    operator fun invoke(thingIdentifier: ThingIdentifier): Thing?
}

class ThingGateway(
    private val httpClient: OkHttpClient,
    private val config: Config,
) {
    data class Config(
        val apiKey: String,
        val baseUrl: String,
    )

    fun getThingDetails(thingIdentifier: ThingIdentifier): Thing? {
        val url = config.baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("things/v4/graphql/compact/thing")
            .addQueryParameter("thing", thingIdentifier).build()

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .tag(CacheTag::class.java, CacheTag(context = "thingDetails", key = thingIdentifier))
            .tag(TelemetryTag::class.java, TelemetryTag(service = "thing", endpoint = "details"))
            .build()

        val response = httpClient.newCall(request).execute()
        response.use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body ?: return null
            val bodyBytes = body.bytes()
            if (bodyBytes.isEmpty()) return null
            return mapper.readValue<ThingResponse>(bodyBytes).toThing()
        }
    }
}
