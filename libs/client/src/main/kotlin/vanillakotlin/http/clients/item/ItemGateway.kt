package vanillakotlin.http.clients.item

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.module.kotlin.readValue
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * using a typealias here allows for easier testing, especially of the FavoriteItemsService coroutines activity
 * alias for [ItemGateway.getItemDetails]
 */
typealias GetItemDetails = (itemIdentifier: ItemIdentifier) -> Item?

// we're using the [Gateway pattern](https://martinfowler.com/articles/gateway-pattern.html) name here to indicate the usage pattern
// of this class more specifically than e.g. ItemService or ItemFacade.  Either naming convention is fine as long as one exists.
// The OkHttpClient singleton and this class's configuration are class parameters
class ItemGateway(
    private val httpClient: OkHttpClient,
    private val config: Config,
) {
    data class Config(
        val apiKey: String,
        val baseUrl: String,
    )

    private val itemDetailsTelemetryTag = TelemetryTag(service = "item", endpoint = "details")

    fun getItemDetails(itemIdentifier: ItemIdentifier): Item? {
        val url =
            "${config.baseUrl}/items/v4/graphql/compact/item".toHttpUrl()
                .newBuilder()
                .addQueryParameter("selection", "AEUBAAAASGNgCGAgCShgFeUAAA")
                .addQueryParameter("tcin", itemIdentifier).build()

        val request =
            Request.Builder()
                .url(url)
                .header("x-api-key", config.apiKey)
                .tag(CacheTag::class.java, CacheTag(namespace = "itemDetails", key = itemIdentifier))
                .tag(TelemetryTag::class.java, itemDetailsTelemetryTag)
                .build()

        // execute a synchronous http request. `enqueue` is available instead of execute if you want asynchronous behavior.
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Unexpected response $response")
            } else {
                val body = checkNotNull(response.body)
                return mapper.readValue<ItemResponse>(body.bytes()).toItem()
            }
        }
    }
}
