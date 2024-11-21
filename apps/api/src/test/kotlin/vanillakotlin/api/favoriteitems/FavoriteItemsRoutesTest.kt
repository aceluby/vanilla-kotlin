package vanillakotlin.api.favoriteitems

import vanillakotlin.api.addAdminAuth
import vanillakotlin.api.addAuth
import vanillakotlin.api.buildTestHandler
import vanillakotlin.api.buildTestItem
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.fail
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class FavoriteItemsRoutesTest {

    @Test fun `postFavoriteTcinsRoute OK`() {
        val saveFavoriteTcin: SaveFavoriteTcin = { _ -> SaveResult.Success }
        val route = postFavoriteTcinsRoute(saveFavoriteTcin = saveFavoriteTcin)
        val handler = buildTestHandler(contractRoute = route)

        val response = handler(Request(Method.POST, "/api/v1/favorite_tcins/1").addAuth())
        assertSoftly(response) {
            status shouldBe Status.OK
        }
    }

    @Test fun `postFavoriteTcinsRoute missing auth`() {
        val saveFavoriteTcin: SaveFavoriteTcin = { _ -> fail("unexpected function called") }
        val route = postFavoriteTcinsRoute(saveFavoriteTcin = saveFavoriteTcin)
        val handler = buildTestHandler(contractRoute = route)

        val response = handler(Request(Method.POST, "/api/v1/favorite_tcins/1"))
        response.status shouldBe Status.UNAUTHORIZED
    }

    @Test fun `deleteFavoriteTcinsRoute OK`() {
        val deleteFavoriteTcin: DeleteFavoriteTcin = { _, _ -> DeleteResult.Success }
        val route = deleteFavoriteTcinsRoute(deleteFavoriteTcin = deleteFavoriteTcin)
        val handler = buildTestHandler(contractRoute = route)

        val response = handler(Request(Method.DELETE, "/api/v1/favorite_tcins/1").addAuth())
        assertSoftly(response) {
            status shouldBe Status.OK
        }
    }

    @Test fun `deleteFavoriteTcinsRoute missing auth`() {
        val deleteFavoriteTcin: DeleteFavoriteTcin = { _, _ -> fail("unexpected function called") }
        val route = deleteFavoriteTcinsRoute(deleteFavoriteTcin = deleteFavoriteTcin)
        val handler = buildTestHandler(contractRoute = route)

        val response = handler(Request(Method.DELETE, "/api/v1/favorite_tcins/1"))
        response.status shouldBe Status.UNAUTHORIZED
    }

    @Test fun `getFavoriteTcinsRoute OK`() {
        val favoriteTcins = listOf("1", "2")
        val getFavorites: GetFavoriteTcins = { _ -> favoriteTcins }
        val route = getFavoriteTcinsRoute(getFavoriteTcins = getFavorites)
        val handler = buildTestHandler(contractRoute = route)

        val response = handler(Request(Method.GET, "/api/v1/favorite_tcins").addAuth())
        assertSoftly(response) {
            status shouldBe Status.OK
            bodyString() shouldBe """["1","2"]"""
        }
    }

    @Test fun `getFavoriteTcinsRoute missing auth`() {
        val getFavoriteTcins: GetFavoriteTcins = { _ -> fail("unexpected function called") }
        val route = getFavoriteTcinsRoute(getFavoriteTcins = getFavoriteTcins)
        val handler = buildTestHandler(contractRoute = route)

        val response = handler(Request(Method.GET, "/api/v1/favorite_tcins"))
        response.status shouldBe Status.UNAUTHORIZED
    }

    @Test fun `getFavoriteItemsRoute OK`() {
        val favoriteItems = listOf(buildTestItem("1"), buildTestItem("2"))
        val getFavorites: GetFavoriteItems = { _ -> favoriteItems }
        val route = getFavoriteItemsRoute(getFavoriteItems = getFavorites)
        val handler = buildTestHandler(contractRoute = route)

        val response = handler(Request(Method.GET, "/api/v1/favorite_items").addAuth())
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
                    "tcin": "1",
                    "lifecycle_state": "READY_FOR_LAUNCH",
                    "classification": {
                      "merchandise": {
                        "department_id": 1,
                        "class_id": 2
                      }
                    }
                  },
                  {
                    "tcin": "2",
                    "lifecycle_state": "READY_FOR_LAUNCH",
                    "classification": {
                      "merchandise": {
                        "department_id": 1,
                        "class_id": 2
                      }
                    }
                  }
                ]
            """
        }
    }

    @Test fun `getFavoriteItemsRoute missing auth`() {
        val getFavorites: GetFavoriteItems = { _ -> fail("unexpected function called") }
        val route = getFavoriteItemsRoute(getFavoriteItems = getFavorites)
        val handler = buildTestHandler(contractRoute = route)

        val response = handler(Request(Method.GET, "/api/v1/favorite_items"))
        response.status shouldBe Status.UNAUTHORIZED
    }

    @Test fun `admin getFavoriteTcinsRoute OK`() {
        val favoriteTcins = listOf("1", "2")
        val getFavorites: GetFavoriteTcins = { _ -> favoriteTcins }
        val route = adminGetFavoriteTcinsRoute(getFavoriteTcins = getFavorites)
        val handler = buildTestHandler(contractRoute = route)

        val response = handler(Request(Method.GET, "/api/v1/admin/favorite_tcins?user_name=${randomUsername()}").addAdminAuth())
        assertSoftly(response) {
            status shouldBe Status.OK
            bodyString() shouldBe """["1","2"]"""
        }
    }

    @Test fun `admin getFavoriteTcinsRoute missing userName query param`() {
        val getFavorites: GetFavoriteTcins = { _ -> fail("unexpected function called") }
        val route = adminGetFavoriteTcinsRoute(getFavoriteTcins = getFavorites)
        val handler = buildTestHandler(contractRoute = route)

        val response = handler(Request(Method.GET, "/api/v1/admin/favorite_tcins").addAdminAuth())
        assertSoftly(response) {
            status shouldBe Status.BAD_REQUEST
        }
    }

    @Test fun `admin getFavoriteTcinsRoute insufficient auth`() {
        val getFavoriteTcins: GetFavoriteTcins = { _ -> fail("unexpected function called") }
        val route = adminGetFavoriteTcinsRoute(getFavoriteTcins = getFavoriteTcins)
        val handler = buildTestHandler(contractRoute = route)

        // only adding standard user auth roles, not admin
        val response = handler(Request(Method.GET, "/api/v1/admin/favorite_tcins").addAuth())
        response.status shouldBe Status.UNAUTHORIZED
    }
}