package vanillakotlin.rocksdb.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import vanillakotlin.random.randomByteArray
import java.nio.ByteBuffer

class ExtensionsTest {

    @Test
    fun `Long toByteArray should convert correctly`() {
        val longValue = 123456789L
        val byteArray = longValue.toByteArray()

        assertEquals(Long.SIZE_BYTES, byteArray.size)

        // Verify conversion by using ByteBuffer directly
        val expected = ByteBuffer.allocate(Long.SIZE_BYTES).putLong(longValue).array()
        assertTrue(expected.contentEquals(byteArray))
    }

    @Test
    fun `ByteArray toLong should convert correctly`() {
        val originalLong = 987654321L
        val byteArray = originalLong.toByteArray()
        val convertedLong = byteArray.toLong()

        assertEquals(originalLong, convertedLong)
    }

    @Test
    fun `toLong and toByteArray should be inverse operations`() {
        val testValues = listOf(
            0L,
            1L,
            -1L,
            Long.MAX_VALUE,
            Long.MIN_VALUE,
            123456789L,
            -987654321L,
        )

        testValues.forEach { originalValue ->
            val converted = originalValue.toByteArray().toLong()
            assertEquals(originalValue, converted, "Failed for value: $originalValue")
        }
    }

    @Test
    fun `ByteArray startsWith should work correctly`() {
        val array = "hello world".toByteArray()
        val prefix1 = "hello".toByteArray()
        val prefix2 = "world".toByteArray()
        val prefix3 = "hel".toByteArray()
        val emptyPrefix = ByteArray(0)

        assertTrue(array.startsWith(prefix1))
        assertFalse(array.startsWith(prefix2))
        assertTrue(array.startsWith(prefix3))
        assertTrue(array.startsWith(emptyPrefix))
    }

    @Test
    fun `ByteArray startsWith should handle edge cases`() {
        val array = "test".toByteArray()
        val longerPrefix = "testing".toByteArray()
        val sameArray = "test".toByteArray()

        assertFalse(array.startsWith(longerPrefix))
        assertTrue(array.startsWith(sameArray))
    }

    @Test
    fun `ByteArray compareTo should work correctly for equal arrays`() {
        val array1 = "hello".toByteArray()
        val array2 = "hello".toByteArray()

        assertEquals(0, array1.compareTo(array2))
    }

    @Test
    fun `ByteArray compareTo should work correctly for different arrays`() {
        val array1 = "apple".toByteArray()
        val array2 = "banana".toByteArray()

        assertTrue(array1.compareTo(array2) < 0)
        assertTrue(array2.compareTo(array1) > 0)
    }

    @Test
    fun `ByteArray compareTo should handle null values`() {
        val array = "test".toByteArray()
        val nullArray: ByteArray? = null

        assertEquals(0, nullArray.compareTo(null))
        assertTrue(array.compareTo(null) > 0)
        assertTrue(nullArray.compareTo(array) < 0)
    }

    @Test
    fun `ByteArray compareTo should handle different lengths`() {
        val shortArray = "ab".toByteArray()
        val longArray = "abc".toByteArray()

        assertTrue(shortArray.compareTo(longArray) < 0)
        assertTrue(longArray.compareTo(shortArray) > 0)
    }

    @Test
    fun `ByteArray compareTo should handle binary data`() {
        val array1 = byteArrayOf(0x01, 0x02, 0x03)
        val array2 = byteArrayOf(0x01, 0x02, 0x04)
        val array3 = byteArrayOf(0x01, 0x02, 0x03)

        assertTrue(array1.compareTo(array2) < 0)
        assertTrue(array2.compareTo(array1) > 0)
        assertEquals(0, array1.compareTo(array3))
    }

    @Test
    fun `ByteBuffer size should return remaining bytes`() {
        val buffer = ByteBuffer.allocate(100)
        assertEquals(100, buffer.size())

        buffer.position(20)
        assertEquals(80, buffer.size())

        buffer.limit(50)
        assertEquals(30, buffer.size())
    }

    @Test
    fun `ByteArray startsWith should handle empty arrays`() {
        val emptyArray = ByteArray(0)
        val nonEmptyArray = "test".toByteArray()
        val emptyPrefix = ByteArray(0)

        assertTrue(emptyArray.startsWith(emptyPrefix))
        assertTrue(nonEmptyArray.startsWith(emptyPrefix))
        assertFalse(emptyArray.startsWith(nonEmptyArray))
    }

    @Test
    fun `ByteArray compareTo should handle edge cases with bytes`() {
        // Test with unsigned byte values
        val array1 = byteArrayOf(0xFF.toByte()) // 255 as unsigned, -1 as signed
        val array2 = byteArrayOf(0x01) // 1

        // Since comparison is done on signed byte values, 0xFF (-1) < 0x01 (1)
        assertTrue(array1.compareTo(array2) < 0)
    }

    @Test
    fun `Long toByteArray should handle edge values`() {
        val edgeValues = listOf(
            0L,
            1L,
            -1L,
            Long.MAX_VALUE,
            Long.MIN_VALUE,
        )

        edgeValues.forEach { value ->
            val byteArray = value.toByteArray()
            assertEquals(Long.SIZE_BYTES, byteArray.size)
            assertEquals(value, byteArray.toLong())
        }
    }

    @Test
    fun `ByteArray operations should work with random data`() {
        repeat(100) {
            val originalLong = kotlin.random.Random.nextLong()
            val byteArray = originalLong.toByteArray()
            val convertedBack = byteArray.toLong()

            assertEquals(originalLong, convertedBack)
        }
    }

    @Test
    fun `startsWith should work with random prefixes`() {
        val baseArray = randomByteArray(100)

        // Test with various prefix lengths
        for (prefixLength in 0..50) {
            val prefix = baseArray.copyOfRange(0, prefixLength)
            assertTrue(baseArray.startsWith(prefix), "Failed with prefix length: $prefixLength")
        }

        // Test with non-matching prefix
        val nonMatchingPrefix = randomByteArray(10)
        // Very low probability of random match, but not impossible
        val matches = baseArray.startsWith(nonMatchingPrefix)
        // We can't assert false here due to randomness, but we can verify the logic works
        if (matches) {
            // If it matches, verify it actually does start with the prefix
            for (i in nonMatchingPrefix.indices) {
                assertEquals(baseArray[i], nonMatchingPrefix[i])
            }
        }
    }

    @Test
    fun `compareTo should provide consistent ordering`() {
        val arrays = listOf(
            "a".toByteArray(),
            "aa".toByteArray(),
            "ab".toByteArray(),
            "b".toByteArray(),
            "ba".toByteArray(),
        )

        // Test transitivity: if a < b and b < c, then a < c
        for (i in arrays.indices) {
            for (j in i + 1 until arrays.size) {
                assertTrue(
                    arrays[i].compareTo(arrays[j]) < 0,
                    "Expected ${String(arrays[i])} < ${String(arrays[j])}",
                )
                assertTrue(
                    arrays[j].compareTo(arrays[i]) > 0,
                    "Expected ${String(arrays[j])} > ${String(arrays[i])}",
                )
            }
        }
    }
} 
