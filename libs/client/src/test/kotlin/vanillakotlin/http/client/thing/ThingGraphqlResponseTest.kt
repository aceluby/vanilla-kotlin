package vanillakotlin.http.client.thing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import vanillakotlin.http.clients.thing.ThingResponse
import vanillakotlin.models.Thing

class ThingGraphqlResponseTest {

    @Test
    fun `test ThingResponse with valid data`() {
        val thing = Thing(
            id = "123",
            productName = "Test Product",
            sellingPrice = 19.99,
        )
        val thingData = ThingResponse.ThingData(thing = thing)
        val response = ThingResponse(data = thingData)

        val result = response.toThing()
        assertNotNull(result)
        assertEquals("123", result?.id)
        assertEquals("Test Product", result?.productName)
        assertEquals(19.99, result?.sellingPrice)
    }

    @Test
    fun `test ThingResponse with null data`() {
        val response = ThingResponse(data = null)

        val result = response.toThing()
        assertNull(result)
    }

    @Test
    fun `test ThingResponse toThing returns correct thing`() {
        val thing = Thing(
            id = "456",
            productName = "Another Product",
            sellingPrice = 29.99,
        )
        val thingData = ThingResponse.ThingData(thing = thing)
        val response = ThingResponse(data = thingData)

        val result = response.toThing()
        assertEquals(thing, result)
    }

    @Test
    fun `test ThingResponse with different thing properties`() {
        val thing = Thing(
            id = "789",
            productName = "Special Product",
            sellingPrice = 99.99,
        )
        val thingData = ThingResponse.ThingData(thing = thing)
        val response = ThingResponse(data = thingData)

        val result = response.toThing()
        assertNotNull(result)
        assertEquals("789", result?.id)
        assertEquals("Special Product", result?.productName)
        assertEquals(99.99, result?.sellingPrice)
    }

    @Test
    fun `test ThingResponse with zero price`() {
        val thing = Thing(
            id = "000",
            productName = "Free Product",
            sellingPrice = 0.0,
        )
        val thingData = ThingResponse.ThingData(thing = thing)
        val response = ThingResponse(data = thingData)

        val result = response.toThing()
        assertNotNull(result)
        assertEquals("000", result?.id)
        assertEquals("Free Product", result?.productName)
        assertEquals(0.0, result?.sellingPrice)
    }

    @Test
    fun `test ThingResponse with empty product name`() {
        val thing = Thing(
            id = "111",
            productName = "",
            sellingPrice = 5.0,
        )
        val thingData = ThingResponse.ThingData(thing = thing)
        val response = ThingResponse(data = thingData)

        val result = response.toThing()
        assertNotNull(result)
        assertEquals("111", result?.id)
        assertEquals("", result?.productName)
        assertEquals(5.0, result?.sellingPrice)
    }

    @Test
    fun `test ThingResponse with negative price`() {
        val thing = Thing(
            id = "222",
            productName = "Discounted Product",
            sellingPrice = -10.0,
        )
        val thingData = ThingResponse.ThingData(thing = thing)
        val response = ThingResponse(data = thingData)

        val result = response.toThing()
        assertNotNull(result)
        assertEquals("222", result?.id)
        assertEquals("Discounted Product", result?.productName)
        assertEquals(-10.0, result?.sellingPrice)
    }

    @Test
    fun `test multiple ThingResponse objects with same data`() {
        val thing1 = Thing(id = "same", productName = "Same Product", sellingPrice = 15.0)
        val thing2 = Thing(id = "same", productName = "Same Product", sellingPrice = 15.0)

        val data1 = ThingResponse.ThingData(thing = thing1)
        val data2 = ThingResponse.ThingData(thing = thing2)

        val response1 = ThingResponse(data = data1)
        val response2 = ThingResponse(data = data2)

        assertEquals(response1.toThing(), response2.toThing())
    }

    @Test
    fun `test ThingResponse with large price value`() {
        val thing = Thing(
            id = "999",
            productName = "Expensive Product",
            sellingPrice = 999999.99,
        )
        val thingData = ThingResponse.ThingData(thing = thing)
        val response = ThingResponse(data = thingData)

        val result = response.toThing()
        assertNotNull(result)
        assertEquals("999", result?.id)
        assertEquals("Expensive Product", result?.productName)
        assertEquals(999999.99, result?.sellingPrice)
    }
} 
