package vanillakotlin.api.favoritethings

import org.http4k.contract.div
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import vanillakotlin.extensions.toJsonString
import vanillakotlin.models.FavoriteThing
import vanillakotlin.models.Thing

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

// Response DTO
data class FavoriteThingResponse(
    val thingIdentifier: String,
    val sellingPrice: Double,
    val productName: String,
)

// Extension function to convert Thing to FavoriteThingResponse
fun Thing.toFavoriteThingResponse(): FavoriteThingResponse {
    return FavoriteThingResponse(
        thingIdentifier = this.id,
        sellingPrice = this.sellingPrice,
        productName = this.productName,
    )
}

// Extension functions for auth checking
private fun Request.hasAuth(): Boolean {
    return this.header("X-ID") != null && this.header("X-EMAIL") != null && this.header("X-MEMBER-OF") != null
}

private fun Request.hasAdminAuth(): Boolean {
    return hasAuth() && this.header("X-MEMBER-OF") == "admin-user"
}

// Individual route functions
fun postFavoriteThingsRoute(saveFavoriteThing: SaveFavoriteThing): RoutingHttpHandler {
    return org.http4k.routing.routes(
        "/api/v1/favorite_things/{thingIdentifier}" bind Method.POST to { request ->
            if (!request.hasAuth()) return@to Response(Status.UNAUTHORIZED)
            val thingIdentifier = request.uri.path.substringAfterLast("/")
            when (val result = saveFavoriteThing(FavoriteThing(thingIdentifier = thingIdentifier))) {
                is SaveResult.Success -> Response(Status.OK)
                is SaveResult.Error -> Response(Status.INTERNAL_SERVER_ERROR).body(result.errorType.toString())
            }
        },
    )
}

fun deleteFavoriteThingsRoute(deleteFavoriteThing: DeleteFavoriteThing): RoutingHttpHandler {
    return org.http4k.routing.routes(
        "/api/v1/favorite_things/{thingIdentifier}" bind Method.DELETE to { request ->
            if (!request.hasAuth()) return@to Response(Status.UNAUTHORIZED)
            val thingIdentifier = request.uri.path.substringAfterLast("/")
            when (val result = deleteFavoriteThing(thingIdentifier)) {
                is DeleteResult.Success -> Response(Status.OK)
                is DeleteResult.NotFound -> Response(Status.NOT_FOUND)
                is DeleteResult.Error -> Response(Status.INTERNAL_SERVER_ERROR).body(result.errorType.toString())
            }
        },
    )
}

fun getFavoriteThingIdsRoute(getFavoriteThingIds: GetFavoriteThingIds): RoutingHttpHandler {
    return org.http4k.routing.routes(
        "/api/v1/favorite_things_ids" bind Method.GET to { request ->
            if (!request.hasAuth()) return@to Response(Status.UNAUTHORIZED)
            val favoriteThings = getFavoriteThingIds()
            Response(Status.OK).body(favoriteThings.toJsonString())
        },
    )
}

fun getFavoriteThingsRoute(getFavoriteThings: GetFavoriteThings): RoutingHttpHandler {
    return org.http4k.routing.routes(
        "/api/v1/favorite_things" bind Method.GET to { request ->
            if (!request.hasAuth()) return@to Response(Status.UNAUTHORIZED)
            val favoriteThingResponses = getFavoriteThings().map { it.toFavoriteThingResponse() }
            Response(Status.OK).body(favoriteThingResponses.toJsonString())
        },
    )
}

fun getAdminFavoriteThingsRoute(getFavoriteThingIds: GetFavoriteThingIds): RoutingHttpHandler {
    return org.http4k.routing.routes(
        "/api/v1/admin/favorite_things" bind Method.GET to { request ->
            if (!request.hasAdminAuth()) return@to Response(Status.UNAUTHORIZED)
            val favoriteThings = getFavoriteThingIds()
            Response(Status.OK).body(favoriteThings.toJsonString())
        },
    )
}
