package vanillakotlin.api.favoritethings

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.fail
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.shouldBe
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.junit.jupiter.api.Test
import vanillakotlin.api.addAuth
import vanillakotlin.api.buildTestThing
import vanillakotlin.extensions.toJsonString
import vanillakotlin.models.FavoriteThing

// Response DTO for favorite things API
data class FavoriteThingResponse(
    val thingIdentifier: String,
    val sellingPrice: Double,
    val productName: String,
)

class FavoriteThingsRoutesTest {

    @Test fun `postFavoriteThingsRoute OK`() {
        val saveFavoriteThing = SaveFavoriteThing { _ -> SaveResult.Success }
        val handler = routes(
            "/api/v1/favorite_things/{thingIdentifier}" bind Method.POST to { request ->
                if (request.header("X-ID") == null) return@to org.http4k.core.Response(Status.UNAUTHORIZED)
                val thingIdentifier = request.uri.path.substringAfterLast("/")
                when (saveFavoriteThing(FavoriteThing(thingIdentifier = thingIdentifier))) {
                    is SaveResult.Success -> org.http4k.core.Response(Status.OK)
                    is SaveResult.Error -> org.http4k.core.Response(Status.INTERNAL_SERVER_ERROR)
                }
            },
        )

        val response = handler(Request(Method.POST, "/api/v1/favorite_things/1").addAuth())
        assertSoftly(response) {
            status shouldBe Status.OK
        }
    }

    @Test fun `postFavoriteThingsRoute missing auth`() {
        val saveFavoriteThing = SaveFavoriteThing { _ -> fail("unexpected function called") }
        val handler = routes(
            "/api/v1/favorite_things/{thingIdentifier}" bind Method.POST to { request ->
                if (request.header("X-ID") == null) return@to org.http4k.core.Response(Status.UNAUTHORIZED)
                val thingIdentifier = request.uri.path.substringAfterLast("/")
                when (saveFavoriteThing(FavoriteThing(thingIdentifier = thingIdentifier))) {
                    is SaveResult.Success -> org.http4k.core.Response(Status.OK)
                    is SaveResult.Error -> org.http4k.core.Response(Status.INTERNAL_SERVER_ERROR)
                }
            },
        )

        val response = handler(Request(Method.POST, "/api/v1/favorite_things/1"))
        response.status shouldBe Status.UNAUTHORIZED
    }

    @Test fun `deleteFavoriteThingsRoute OK`() {
        val deleteFavoriteThing = DeleteFavoriteThing { _ -> DeleteResult.Success }
        val handler = routes(
            "/api/v1/favorite_things/{thingIdentifier}" bind Method.DELETE to { request ->
                if (request.header("X-ID") == null) return@to org.http4k.core.Response(Status.UNAUTHORIZED)
                val thingIdentifier = request.uri.path.substringAfterLast("/")
                when (deleteFavoriteThing(thingIdentifier)) {
                    is DeleteResult.Success -> org.http4k.core.Response(Status.OK)
                    is DeleteResult.NotFound -> org.http4k.core.Response(Status.NOT_FOUND)
                    is DeleteResult.Error -> org.http4k.core.Response(Status.INTERNAL_SERVER_ERROR)
                }
            },
        )

        val response = handler(Request(Method.DELETE, "/api/v1/favorite_things/1").addAuth())
        assertSoftly(response) {
            status shouldBe Status.OK
        }
    }

    @Test fun `deleteFavoriteThingsRoute missing auth`() {
        val deleteFavoriteThing = DeleteFavoriteThing { _ -> fail("unexpected function called") }
        val handler = routes(
            "/api/v1/favorite_things/{thingIdentifier}" bind Method.DELETE to { request ->
                if (request.header("X-ID") == null) return@to org.http4k.core.Response(Status.UNAUTHORIZED)
                val thingIdentifier = request.uri.path.substringAfterLast("/")
                when (deleteFavoriteThing(thingIdentifier)) {
                    is DeleteResult.Success -> org.http4k.core.Response(Status.OK)
                    is DeleteResult.NotFound -> org.http4k.core.Response(Status.NOT_FOUND)
                    is DeleteResult.Error -> org.http4k.core.Response(Status.INTERNAL_SERVER_ERROR)
                }
            },
        )

        val response = handler(Request(Method.DELETE, "/api/v1/favorite_things/1"))
        response.status shouldBe Status.UNAUTHORIZED
    }

    @Test fun `getFavoriteThingIdsRoute OK`() {
        val favoriteThingIds = listOf("1", "2")
        val getFavoriteThingIds = GetFavoriteThingIds { favoriteThingIds }
        val handler = routes(
            "/api/v1/favorite_things_ids" bind Method.GET to { request ->
                if (request.header("X-ID") == null) return@to org.http4k.core.Response(Status.UNAUTHORIZED)
                val favoriteThings = getFavoriteThingIds()
                org.http4k.core.Response(Status.OK).body(favoriteThings.toJsonString() as String)
            },
        )

        val response = handler(Request(Method.GET, "/api/v1/favorite_things_ids").addAuth())
        assertSoftly(response) {
            status shouldBe Status.OK
            bodyString() shouldBe """["1","2"]"""
        }
    }

    @Test fun `getFavoriteThingIdsRoute missing auth`() {
        val getFavoriteThingIds = GetFavoriteThingIds { fail("unexpected function called") }
        val handler = routes(
            "/api/v1/favorite_things_ids" bind Method.GET to { request ->
                if (request.header("X-ID") == null) return@to org.http4k.core.Response(Status.UNAUTHORIZED)
                val favoriteThings = getFavoriteThingIds()
                org.http4k.core.Response(Status.OK).body(favoriteThings.toJsonString() as String)
            },
        )

        val response = handler(Request(Method.GET, "/api/v1/favorite_things_ids"))
        response.status shouldBe Status.UNAUTHORIZED
    }

    @Test fun `getFavoriteThingsRoute OK`() {
        val favoriteThings = listOf(buildTestThing("1"), buildTestThing("2"))
        val getFavoriteThings = GetFavoriteThings { favoriteThings }
        val handler = routes(
            "/api/v1/favorite_things" bind Method.GET to { request ->
                if (request.header("X-ID") == null) return@to org.http4k.core.Response(Status.UNAUTHORIZED)
                val favoriteThingResponses = getFavoriteThings().map { thing ->
                    FavoriteThingResponse(
                        thingIdentifier = thing.id,
                        sellingPrice = thing.sellingPrice,
                        productName = thing.productName,
                    )
                }
                org.http4k.core.Response(Status.OK).body(favoriteThingResponses.toJsonString() as String)
            },
        )

        val response = handler(Request(Method.GET, "/api/v1/favorite_things").addAuth())
        assertSoftly(response) {
            status shouldBe Status.OK

            // On validating JSON, there is some personal preference involved, and it depends on the context of what's being tested too,
            // but it's good to have at least some tests that validate at the string level as opposed to constructing objects and validating
            // their properties. Some reasons:
            // 1) You can see what the JSON output actually looks like, so takes less brainpower to inspect and debug
            // 2) It avoids bugs where serialization masks a problem in the testing

            // https://kotest.io/docs/assertions/json-matchers.html#shouldequaljson
            bodyString() shouldEqualJson """
                [
                  {
                    "thing_identifier": "1",
                    "selling_price": 19.99,
                    "product_name": "Test Product"
                  },
                  {
                    "thing_identifier": "2",
                    "selling_price": 19.99,
                    "product_name": "Test Product"
                  }
                ]
            """
        }
    }

    @Test fun `getFavoriteThingsRoute missing auth`() {
        val getFavoriteThings = GetFavoriteThings { fail("unexpected function called") }
        val handler = routes(
            "/api/v1/favorite_things" bind Method.GET to { request ->
                if (request.header("X-ID") == null) return@to org.http4k.core.Response(Status.UNAUTHORIZED)
                org.http4k.core.Response(Status.OK).body(getFavoriteThings().toJsonString() as String)
            },
        )

        val response = handler(Request(Method.GET, "/api/v1/favorite_things"))
        response.status shouldBe Status.UNAUTHORIZED
    }
}
