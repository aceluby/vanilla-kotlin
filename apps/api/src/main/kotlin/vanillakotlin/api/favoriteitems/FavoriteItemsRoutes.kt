package vanillakotlin.api.favoriteitems

import vanillakotlin.api.adminUserSecurity
import vanillakotlin.api.standardUserSecurity

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

fun postFavoriteTcinsRoute(saveFavoriteTcin: SaveFavoriteTcin): ContractRoute {
    val spec =
        "/favorite_tcins" / Path.of("tcin") meta {
            summary = "Saves the favorited TCIN for the authenticated user"
            returning(Status.OK)
            returning(Status.UNAUTHORIZED)

            // this security property configures the `StandardUserSecurity` filter we've defined, which checks for existence of a
            // role in the security headers via go-proxy sidecar integration. See the StandardUserSecurity class for what things look like
            // for documentation on configuring go-proxy.
            security = standardUserSecurity
        } bindContract Method.POST

    fun save(itemIdentifier: ItemIdentifier): HttpHandler =
        { request: Request ->
            when (
                val result =
                    saveFavoriteTcin(
                        UserFavoriteTcin(
                            userName = request.getAuthenticatedSecurityContext().lanId,
                            itemIdentifier = itemIdentifier,
                        ),
                    )
            ) {
                is SaveResult.Success -> Response(Status.OK)
                is SaveResult.Error -> Response(Status.INTERNAL_SERVER_ERROR).body(result.errorType.toString())
            }
        }

    return spec to ::save
}

fun deleteFavoriteTcinsRoute(deleteFavoriteTcin: DeleteFavoriteTcin): ContractRoute {
    val spec =
        "/favorite_tcins" / Path.of("tcin") meta {
            summary = "Deletes the favorited TCIN for the authenticated user"
            returning(Status.OK)
            returning(Status.UNAUTHORIZED)
            security = standardUserSecurity
        } bindContract Method.DELETE

    fun delete(itemIdentifier: ItemIdentifier): HttpHandler =
        { request: Request ->
            when (val result = deleteFavoriteTcin(request.getAuthenticatedSecurityContext().lanId, itemIdentifier)) {
                is DeleteResult.Success -> Response(Status.OK)
                is DeleteResult.NotFound -> Response(Status.NOT_FOUND)
                is DeleteResult.Error -> Response(Status.INTERNAL_SERVER_ERROR).body(result.errorType.toString())
            }
        }

    return spec to ::delete
}

fun getFavoriteTcinsRoute(getFavoriteTcins: GetFavoriteTcins): ContractRoute {
    val spec =
        "/favorite_tcins" meta {
            summary = "Gets the favorited item TCINs for the authenticated user"
            returning(Status.OK, Body.auto<List<ItemIdentifier>>().toLens() to exampleGetFavoriteTcinsResponse)
            returning(Status.UNAUTHORIZED)
            security = standardUserSecurity
        } bindContract Method.GET

    fun get(): HttpHandler =
        { request: Request ->
            val favoriteTcins = getFavoriteTcins(request.getAuthenticatedSecurityContext().lanId)
            Response(Status.OK).addBody(favoriteTcins)
        }

    return spec to ::get
}

fun getFavoriteItemsRoute(getFavoriteItems: GetFavoriteItems): ContractRoute {
    val spec =
        "/favorite_items" meta {
            summary = "Gets the favorited items for the authenticated user"
            returning(Status.OK, Body.auto<List<Item>>().toLens() to exampleGetFavoriteItemsResponse)
            returning(Status.UNAUTHORIZED)
            security = standardUserSecurity
        } bindContract Method.GET

    fun get(): HttpHandler =
        { request: Request ->
            val favoriteItems = getFavoriteItems(request.getAuthenticatedSecurityContext().lanId)
            Response(Status.OK).addBody(favoriteItems)
        }

    return spec to ::get
}

val userNameQuery = Query.string().required("user_name")

fun adminGetFavoriteTcinsRoute(getFavoriteTcins: GetFavoriteTcins): ContractRoute {
    val spec =
        "/admin/favorite_tcins" meta {
            summary = "Gets the favorited item TCINs for the given user. Requires administrative role for the requesting user."
            queries += listOf(userNameQuery)
            returning(Status.OK)
            returning(Status.UNAUTHORIZED)
            security = adminUserSecurity
        } bindContract Method.GET

    fun get(): HttpHandler =
        { request: Request ->
            val userName = userNameQuery(request)
            val favoriteTcins = getFavoriteTcins(userName)
            Response(Status.OK).addBody(favoriteTcins)
        }

    return spec to ::get
}

private val exampleGetFavoriteTcinsResponse = listOf("85275041", "81549871")
private val exampleGetFavoriteItemsResponse =
    listOf(
        Item(
            id = "85275041",
            description = "READY_FOR_LAUNCH",
            price =
            Item.Classification(
                merchandise =
                Item.Classification.Merchandise(
                    departmentId = 62,
                    classId = 6,
                ),
            ),
        ),
    )
