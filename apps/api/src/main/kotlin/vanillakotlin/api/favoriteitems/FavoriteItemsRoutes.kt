package vanillakotlin.api.favoriteitems

import org.http4k.contract.ContractRoute
import org.http4k.contract.div
import org.http4k.contract.meta
import org.http4k.contract.openapi.OpenAPIJackson.auto
import org.http4k.core.Body
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.lens.Path
import vanillakotlin.extensions.toJsonString
import vanillakotlin.models.FavoriteItem
import vanillakotlin.models.Item
import vanillakotlin.models.ItemIdentifier

/**
 * Each route's responsibility is to:
 * - read the request attributes
 * - call the underlying function
 * - create the response based on the outcome of the function
 *
 * It should not contain business logic for the function itself. This is handled by the underlying function.
 *
 * These routes are example of HTTP4K route handlers that integrates with OpenAPI.
 * Docs available at https://www.http4k.org/guide/reference/contracts/
 * and https://www.http4k.org/guide/howto/integrate_with_openapi/
 * and also here: https://www.http4k.org/blog/documenting_apis_with_openapi/#4_modelling_http_body_messages
 * They use a code-first perspective. i.e. The swagger spec is generated from the properties specified here.
 */
fun postFavoriteTcinsRoute(saveFavoriteItem: SaveFavoriteItem): ContractRoute {
    val spec = "/favorite_items" / Path.of("itemIdentifier") meta {
        summary = "Saves the favorited item"
        returning(Status.OK)
        returning(Status.UNAUTHORIZED)
    } bindContract Method.POST

    fun save(itemIdentifier: ItemIdentifier): HttpHandler = { _: Request ->
        when (val result = saveFavoriteItem(FavoriteItem(itemIdentifier = itemIdentifier))) {
            is SaveResult.Success -> Response(Status.OK)
            is SaveResult.Error -> Response(Status.INTERNAL_SERVER_ERROR).body(result.errorType.toString())
        }
    }

    return spec to ::save
}

fun deleteFavoriteTcinsRoute(deleteFavoriteItem: DeleteFavoriteItem): ContractRoute {
    val spec = "/favorite_items" / Path.of("itemIdentifier") meta {
        summary = "Deletes the favorited item"
        returning(Status.OK)
        returning(Status.UNAUTHORIZED)
    } bindContract Method.DELETE

    fun delete(itemIdentifier: ItemIdentifier): HttpHandler = { _: Request ->
        when (val result = deleteFavoriteItem(itemIdentifier)) {
            is DeleteResult.Success -> Response(Status.OK)
            is DeleteResult.NotFound -> Response(Status.NOT_FOUND)
            is DeleteResult.Error -> Response(Status.INTERNAL_SERVER_ERROR).body(result.errorType.toString())
        }
    }

    return spec to ::delete
}

fun getFavoriteItemIdsRoute(getFavoriteItemIds: GetFavoriteItemIds): ContractRoute {
    val spec = "/favorite_items_ids" meta {
        summary = "Gets the favorited item identifiers"
        returning(Status.OK, Body.auto<List<ItemIdentifier>>().toLens() to exampleGetFavoriteItemIdsResponse)
        returning(Status.UNAUTHORIZED)
    } bindContract Method.GET

    fun get(): HttpHandler = { _: Request ->
        val favoriteTcins = getFavoriteItemIds()
        Response(Status.OK).body(favoriteTcins.toJsonString())
    }

    return spec to ::get
}

fun getFavoriteItemsRoute(getFavoriteItems: GetFavoriteItems): ContractRoute {
    val spec = "/favorite_items" meta {
        summary = "Gets the favorited items for the authenticated user"
        returning(Status.OK, Body.auto<List<Item>>().toLens() to exampleGetFavoriteItemsResponse)
        returning(Status.UNAUTHORIZED)
    } bindContract Method.GET

    fun get(): HttpHandler = { _: Request ->
        Response(Status.OK).body(getFavoriteItems().toJsonString())
    }

    return spec to ::get
}

private val exampleGetFavoriteItemIdsResponse = listOf("85275041", "81549871")
private val exampleGetFavoriteItemsResponse = listOf(
    Item(
        id = "85275041",
        description = "READY_FOR_LAUNCH",
        price = Item.Price(
            cost = 100.0,
            sellingPrice = 110.0,
        ),
    ),
)
