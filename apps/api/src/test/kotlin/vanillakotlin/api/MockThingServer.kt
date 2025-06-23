package vanillakotlin.api

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest

class MockThingServer {
    private val mockWebServer = MockWebServer()

    init {
        mockWebServer.dispatcher = ThingDispatcher()
    }

    private val sampleThingResponse =
        """
        {
          "data": {
            "thing": {
              "id": "1",
              "product_name": "Test Product",
              "selling_price": 19.99
            }
          }
        }
        """.trimIndent()

    private inner class ThingDispatcher : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse = when {
            request.path?.startsWith("/things/") == true -> MockResponse().setResponseCode(200).setBody(sampleThingResponse)
            else -> MockResponse().setResponseCode(404)
        }
    }

    val port: Int get() = mockWebServer.port

    fun close() {
        mockWebServer.close()
    }
}
