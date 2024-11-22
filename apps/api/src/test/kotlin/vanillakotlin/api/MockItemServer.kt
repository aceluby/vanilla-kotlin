package vanillakotlin.api

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.intellij.lang.annotations.Language

class MockItemServer : AutoCloseable {
    val server = MockWebServer()

    @Language("JSON") private val sampleItemGraphQlResponse =
        """
        {
          "data": {
            "item": {
              "lifecycleState": "READY_FOR_LAUNCH",
              "item": "50183713",
              "classification": {
                "merchandise": {
                  "classId": 6,
                  "departmentId": 62
                }
              }
            }
          }
        }
        """.trimIndent()

    init {
        // mock the item endpoint to return the same sample responses based on the request path
        val dispatcher: Dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    return when (request.path?.substringBefore("?")) {
                        "/items/v4/graphql/compact/item" -> return MockResponse().setResponseCode(200).setBody(sampleItemGraphQlResponse)
                        else -> MockResponse().setResponseCode(404)
                    }
                }
            }
        server.dispatcher = dispatcher
    }

    val port get() = server.port

    override fun close() {
        server.close()
    }
}
