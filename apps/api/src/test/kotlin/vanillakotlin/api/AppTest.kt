package vanillakotlin.api

import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.extensions.system.withSystemProperties
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

// This test contains setup and teardown functions that are meant to run once for this class (not for each test)
// The `PER_CLASS` annotation enables that in combination with the @BeforeAll and @AfterAll annotated functions
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AppTest {
    private val mockItemServer = MockItemServer()
    private var app: App? = null

    @BeforeAll fun beforeAll() {
        // override the http client baseUrl to the mocked server's address. The app should use that when configured.
        withSystemProperties(
            mapOf(
                "config.override.http.client.item.gateway.baseUrl" to "http://localhost:${mockItemServer.port}",
            ),
        ) {
            app = App()
            app?.start()
        }
    }

    @AfterAll fun afterAll() {
        mockItemServer.close()
        app?.close()
    }

    @Test fun `health check ok`() {
        val request = Request(Method.GET, "http://localhost:${app?.httpServerPort}/health")

        val response = JavaHttpClient()(request)

        response.status shouldBe Status.OK
    }

    @Test fun `post favorite_tcins - missing auth`() {
        val request = Request(Method.POST, "http://localhost:${app?.httpServerPort}/api/v1/favorite_tcins/1")

        val response = JavaHttpClient()(request)

        response.status shouldBe Status.UNAUTHORIZED
    }

    @Test fun `delete favorite_tcins - missing auth`() {
        val request = Request(Method.DELETE, "http://localhost:${app?.httpServerPort}/api/v1/favorite_tcins/1")

        val response = JavaHttpClient()(request)

        response.status shouldBe Status.UNAUTHORIZED
    }

    @Test fun `get favorite_items - missing auth`() {
        val request = Request(Method.GET, "http://localhost:${app?.httpServerPort}/api/v1/favorite_items")

        val response = JavaHttpClient()(request)

        response.status shouldBe Status.UNAUTHORIZED
    }

    @Test fun `favorite_items - simple operations`() {
        val request = Request(Method.GET, "http://localhost:${app?.httpServerPort}/api/v1/favorite_items").addAuth()

        val response = JavaHttpClient()(request)

        response.status shouldBe Status.OK
    }

    @Test fun `admin get favorite_tcins authorized`() {
        val request = Request(Method.GET, "http://localhost:${app?.httpServerPort}/api/v1/admin/favorite_tcins?user_name=x").addAdminAuth()

        val response = JavaHttpClient()(request)

        response.status shouldBe Status.OK
    }

    @Test fun `admin get favorite_tcins missing required admin auth`() {
        val request = Request(Method.GET, "http://localhost:${app?.httpServerPort}/api/v1/admin/favorite_tcins").addAuth()

        val response = JavaHttpClient()(request)

        response.status shouldBe Status.UNAUTHORIZED
    }

    @Test fun `favorite_items - normal operations`() {
        val userName = randomUsername()
        val testTcin1 = "tcin1"
        val testTcin2 = "tcin2"
        val client = JavaHttpClient()

        testReadFavorite(userName, emptySet(), client)

        testPost(userName, testTcin1, client)

        testReadFavorite(userName, setOf(testTcin1), client)

        testPost(userName, testTcin2, client)

        testReadFavorite(userName, setOf(testTcin1, testTcin2), client)

        testDelete(userName, testTcin1, client)

        testReadFavorite(userName, setOf(testTcin2), client)

        testDelete(userName, testTcin2, client)

        testReadFavorite(userName, emptySet(), client)
    }

    private fun testReadFavorite(
        userName: String,
        tcins: Set<String>,
        client: HttpHandler = JavaHttpClient(),
    ) {
        val request = Request(Method.GET, "http://localhost:${app?.httpServerPort}/api/v1/favorite_tcins").addAuth(userName)

        val response = client(request)

        response.status shouldBe Status.OK
        val responseBody = response.body.toString()
        val getResult = mapper.readValue<List<String>>(responseBody).toSet()
        getResult.size shouldBe tcins.size
        getResult.forEach {
            it shouldBeIn tcins
        }
    }

    private fun testPost(
        userName: String,
        tcin: String,
        client: HttpHandler = JavaHttpClient(),
    ) {
        val request =
            Request(Method.POST, "http://localhost:${app?.httpServerPort}/api/v1/favorite_tcins/$tcin")
                .addAuth(userName)
        val response = client(request)

        response.status shouldBe Status.OK
    }

    private fun testDelete(
        userName: String,
        tcin: String,
        client: HttpHandler = JavaHttpClient(),
    ) {
        val request =
            Request(Method.DELETE, "http://localhost:${app?.httpServerPort}/api/v1/favorite_tcins/$tcin")
                .addAuth(userName)
        val response = client(request)

        response.status shouldBe Status.OK
    }
}
