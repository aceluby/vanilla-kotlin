package vanillakotlin.api

import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.extensions.system.withSystemProperties
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.shouldBe
import org.http4k.client.JavaHttpClient
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import vanillakotlin.config.loadConfig
import vanillakotlin.db.createJdbi
import vanillakotlin.random.randomUsername
import vanillakotlin.serde.mapper

// This test contains setup and teardown functions that are meant to run once for this class (not for each test)
// The `PER_CLASS` annotation enables that in combination with the @BeforeAll and @AfterAll annotated functions
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AppTest {
    private val mockThingServer = MockThingServer()
    private var app: App? = null

    @BeforeEach fun beforeEach() {
        // Clean up database before each test
        val config = loadConfig<Config>()
        val jdbi = createJdbi(config.db, mapper)
        jdbi.useHandle<Exception> { handle ->
            handle.execute("DELETE FROM favorite_thing")
        }
    }

    @BeforeAll fun beforeAll() {
        // override the http client baseUrl to the mocked server's address. The app should use that when configured.
        withSystemProperties(
            mapOf(
                "config.override.http.client.thing.gateway.baseUrl" to "http://localhost:${mockThingServer.port}",
            ),
        ) {
            app = App()
            app?.start()
        }
    }

    @AfterAll fun afterAll() {
        mockThingServer.close()
        app?.close()
    }

    @Test fun `health check ok`() {
        val request = Request(Method.GET, "http://localhost:${app?.httpServerPort}/health")

        val response = JavaHttpClient()(request)

        response.status shouldBe Status.OK
    }

    @Test fun `post favorite_things - missing auth`() {
        val request = Request(Method.POST, "http://localhost:${app?.httpServerPort}/api/v1/favorite_things/1")

        val response = JavaHttpClient()(request)

        response.status shouldBe Status.UNAUTHORIZED
    }

    @Test fun `delete favorite_things - missing auth`() {
        val request = Request(Method.DELETE, "http://localhost:${app?.httpServerPort}/api/v1/favorite_things/1")

        val response = JavaHttpClient()(request)

        response.status shouldBe Status.UNAUTHORIZED
    }

    @Test fun `get favorite_things - missing auth`() {
        val request = Request(Method.GET, "http://localhost:${app?.httpServerPort}/api/v1/favorite_things")

        val response = JavaHttpClient()(request)

        response.status shouldBe Status.UNAUTHORIZED
    }

    @Test fun `favorite_things - simple operations`() {
        val request = Request(Method.GET, "http://localhost:${app?.httpServerPort}/api/v1/favorite_things").addAuth()

        val response = JavaHttpClient()(request)

        response.status shouldBe Status.OK
    }

    @Test fun `admin get favorite_things authorized`() {
        val request = Request(Method.GET, "http://localhost:${app?.httpServerPort}/api/v1/admin/favorite_things?user_name=x").addAdminAuth()

        val response = JavaHttpClient()(request)

        response.status shouldBe Status.OK
    }

    @Test fun `admin get favorite_things missing required admin auth`() {
        val request = Request(Method.GET, "http://localhost:${app?.httpServerPort}/api/v1/admin/favorite_things").addAuth()

        val response = JavaHttpClient()(request)

        response.status shouldBe Status.UNAUTHORIZED
    }

    @Test fun `favorite_things - normal operations`() {
        val userName = randomUsername()
        val testThing1 = "thing1"
        val testThing2 = "thing2"
        val client = JavaHttpClient()

        testReadFavorite(userName, emptySet(), client)

        testPost(userName, testThing1, client)

        testReadFavorite(userName, setOf(testThing1), client)

        testPost(userName, testThing2, client)

        testReadFavorite(userName, setOf(testThing1, testThing2), client)

        testDelete(userName, testThing1, client)

        testReadFavorite(userName, setOf(testThing2), client)

        testDelete(userName, testThing2, client)

        testReadFavorite(userName, emptySet(), client)
    }

    private fun testReadFavorite(
        userName: String,
        things: Set<String>,
        client: HttpHandler = JavaHttpClient(),
    ) {
        val request = Request(Method.GET, "http://localhost:${app?.httpServerPort}/api/v1/favorite_things_ids").addAuth(userName)

        val response = client(request)

        response.status shouldBe Status.OK
        val responseBody = response.body.toString()
        val getResult = mapper.readValue<List<String>>(responseBody).toSet()
        getResult.size shouldBe things.size
        getResult.forEach {
            it shouldBeIn things
        }
    }

    private fun testPost(
        userName: String,
        thing: String,
        client: HttpHandler = JavaHttpClient(),
    ) {
        val request =
            Request(Method.POST, "http://localhost:${app?.httpServerPort}/api/v1/favorite_things/$thing")
                .addAuth(userName)
        val response = client(request)

        response.status shouldBe Status.OK
    }

    private fun testDelete(
        userName: String,
        thing: String,
        client: HttpHandler = JavaHttpClient(),
    ) {
        val request =
            Request(Method.DELETE, "http://localhost:${app?.httpServerPort}/api/v1/favorite_things/$thing")
                .addAuth(userName)
        val response = client(request)

        response.status shouldBe Status.OK
    }
}
