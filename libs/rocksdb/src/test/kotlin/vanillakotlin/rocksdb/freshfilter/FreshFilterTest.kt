package vanillakotlin.rocksdb.freshfilter

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import vanillakotlin.random.randomByteArray
import java.nio.file.Path

class FreshFilterTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var freshFilter: FreshFilter
    private lateinit var dataDirectory: String

    @BeforeEach
    fun setUp() {
        dataDirectory = tempDir.resolve("fresh-filter-test").toString()
        freshFilter = FreshFilter(dataDirectory)
    }

    @AfterEach
    fun tearDown() {
        freshFilter.close()
    }

    @Test
    fun `should return NEW for first message`() {
        val message = createTestMessage("key1", "hash1".toByteArray(), 1000L)

        val result = freshFilter.filter(message)

        assertEquals(FilterStatus.NEW, result)
    }

    @Test
    fun `should return REDUNDANT for same message`() {
        val key = "key1"
        val hash = "hash1".toByteArray()
        val timestamp = 1000L

        val message1 = createTestMessage(key, hash, timestamp)
        val message2 = createTestMessage(key, hash, timestamp)

        freshFilter.filter(message1)
        val result = freshFilter.filter(message2)

        assertEquals(FilterStatus.REDUNDANT, result)
    }

    @Test
    fun `should return NEW for same key with different hash`() {
        val key = "key1"
        val timestamp = 1000L

        val message1 = createTestMessage(key, "hash1".toByteArray(), timestamp)
        val message2 = createTestMessage(key, "hash2".toByteArray(), timestamp)

        freshFilter.filter(message1)
        val result = freshFilter.filter(message2)

        assertEquals(FilterStatus.NEW, result)
    }

    @Test
    fun `should return NEW for same key with newer timestamp`() {
        val key = "key1"
        val hash = "hash1".toByteArray()

        val message1 = createTestMessage(key, hash, 1000L)
        val message2 = createTestMessage(key, hash, 2000L)

        freshFilter.filter(message1)
        val result = freshFilter.filter(message2)

        assertEquals(FilterStatus.NEW, result)
    }

    @Test
    fun `should return STALE for older timestamp`() {
        val key = "key1"
        val hash = "hash1".toByteArray()

        val message1 = createTestMessage(key, hash, 2000L)
        val message2 = createTestMessage(key, hash, 1000L)

        freshFilter.filter(message1)
        val result = freshFilter.filter(message2)

        assertEquals(FilterStatus.STALE, result)
    }

    @Test
    fun `should handle null value hash`() {
        val message1 = FreshFilterable.of("key1", null, 1000L)
        val message2 = FreshFilterable.of("key1", null, 1000L)

        freshFilter.filter(message1)
        val result = freshFilter.filter(message2)

        assertEquals(FilterStatus.REDUNDANT, result)
    }

    @Test
    fun `should handle null vs non-null hash`() {
        val message1 = FreshFilterable.of("key1", null, 1000L)
        val message2 = FreshFilterable.of("key1", "hash".toByteArray(), 1000L)

        freshFilter.filter(message1)
        val result = freshFilter.filter(message2)

        assertEquals(FilterStatus.NEW, result)
    }

    @Test
    fun `should handle ByteArray keys`() {
        val key = byteArrayOf(0x01, 0x02, 0x03)
        val hash = "hash1".toByteArray()
        val timestamp = 1000L

        val message1 = FreshFilterable.of(key, hash, timestamp)
        val message2 = FreshFilterable.of(key, hash, timestamp)

        freshFilter.filter(message1)
        val result = freshFilter.filter(message2)

        assertEquals(FilterStatus.REDUNDANT, result)
    }

    @Test
    fun `should handle multiple keys independently`() {
        val message1 = createTestMessage("key1", "hash1".toByteArray(), 1000L)
        val message2 = createTestMessage("key2", "hash1".toByteArray(), 1000L)
        val message3 = createTestMessage("key1", "hash1".toByteArray(), 1000L)

        val result1 = freshFilter.filter(message1)
        val result2 = freshFilter.filter(message2)
        val result3 = freshFilter.filter(message3)

        assertEquals(FilterStatus.NEW, result1)
        assertEquals(FilterStatus.NEW, result2)
        assertEquals(FilterStatus.REDUNDANT, result3)
    }

    @Test
    fun `should handle complex filtering sequence`() {
        val key = "complex-key"
        val hash1 = "hash1".toByteArray()
        val hash2 = "hash2".toByteArray()

        // First message - should be NEW
        val message1 = createTestMessage(key, hash1, 1000L)
        assertEquals(FilterStatus.NEW, freshFilter.filter(message1))

        // Same message - should be REDUNDANT
        val message2 = createTestMessage(key, hash1, 1000L)
        assertEquals(FilterStatus.REDUNDANT, freshFilter.filter(message2))

        // Newer timestamp, same hash - should be NEW
        val message3 = createTestMessage(key, hash1, 2000L)
        assertEquals(FilterStatus.NEW, freshFilter.filter(message3))

        // Older timestamp - should be STALE
        val message4 = createTestMessage(key, hash2, 1500L)
        assertEquals(FilterStatus.STALE, freshFilter.filter(message4))

        // Newer timestamp, different hash - should be NEW
        val message5 = createTestMessage(key, hash2, 3000L)
        assertEquals(FilterStatus.NEW, freshFilter.filter(message5))

        // Same as last - should be REDUNDANT
        val message6 = createTestMessage(key, hash2, 3000L)
        assertEquals(FilterStatus.REDUNDANT, freshFilter.filter(message6))
    }

    @Test
    fun `should handle empty hash values`() {
        val key = "empty-hash-key"
        val emptyHash = ByteArray(0)

        val message1 = createTestMessage(key, emptyHash, 1000L)
        val message2 = createTestMessage(key, emptyHash, 1000L)

        freshFilter.filter(message1)
        val result = freshFilter.filter(message2)

        assertEquals(FilterStatus.REDUNDANT, result)
    }

    @Test
    fun `should handle large hash values`() {
        val key = "large-hash-key"
        val largeHash = randomByteArray(1024) // 1KB hash

        val message1 = createTestMessage(key, largeHash, 1000L)
        val message2 = createTestMessage(key, largeHash, 1000L)

        freshFilter.filter(message1)
        val result = freshFilter.filter(message2)

        assertEquals(FilterStatus.REDUNDANT, result)
    }

    @Test
    fun `should handle binary hash data`() {
        val key = "binary-hash-key"
        val binaryHash = byteArrayOf(0x00, 0xFF.toByte(), 0x7F, 0x80.toByte())

        val message1 = createTestMessage(key, binaryHash, 1000L)
        val message2 = createTestMessage(key, binaryHash, 1000L)

        freshFilter.filter(message1)
        val result = freshFilter.filter(message2)

        assertEquals(FilterStatus.REDUNDANT, result)
    }

    @Test
    fun `should handle timestamp edge cases`() {
        val key = "timestamp-edge-key"
        val hash = "hash".toByteArray()

        // Test with Long.MAX_VALUE
        val message1 = createTestMessage(key, hash, Long.MAX_VALUE)
        assertEquals(FilterStatus.NEW, freshFilter.filter(message1))

        // Test with Long.MIN_VALUE (should be STALE)
        val message2 = createTestMessage(key, hash, Long.MIN_VALUE)
        assertEquals(FilterStatus.STALE, freshFilter.filter(message2))

        // Test with 0
        val message3 = createTestMessage(key, hash, 0L)
        assertEquals(FilterStatus.STALE, freshFilter.filter(message3))
    }

    @Test
    fun `should handle stress test with many keys`() {
        val numKeys = 1000
        val keys = (1..numKeys).map { "stress-key-$it" }
        val hash = "common-hash".toByteArray()
        val timestamp = 1000L

        // First pass - all should be NEW
        keys.forEach { key ->
            val message = createTestMessage(key, hash, timestamp)
            assertEquals(FilterStatus.NEW, freshFilter.filter(message))
        }

        // Second pass - all should be REDUNDANT
        keys.forEach { key ->
            val message = createTestMessage(key, hash, timestamp)
            assertEquals(FilterStatus.REDUNDANT, freshFilter.filter(message))
        }
    }

    @Test
    fun `should persist data across filter operations`() {
        val key = "persistence-key"
        val hash = "hash".toByteArray()
        val timestamp = 1000L

        val message1 = createTestMessage(key, hash, timestamp)
        freshFilter.filter(message1)

        // Create many other operations to potentially trigger compaction
        repeat(100) { i ->
            val otherMessage = createTestMessage("other-key-$i", "other-hash".toByteArray(), timestamp + i)
            freshFilter.filter(otherMessage)
        }

        // Original message should still be REDUNDANT
        val message2 = createTestMessage(key, hash, timestamp)
        assertEquals(FilterStatus.REDUNDANT, freshFilter.filter(message2))
    }

    @Test
    fun `should handle concurrent-like operations`() {
        val key = "concurrent-key"
        val hash1 = "hash1".toByteArray()
        val hash2 = "hash2".toByteArray()

        // Simulate rapid updates with different hashes
        val results = mutableListOf<FilterStatus>()

        results.add(freshFilter.filter(createTestMessage(key, hash1, 1000L)))
        results.add(freshFilter.filter(createTestMessage(key, hash2, 1001L)))
        results.add(freshFilter.filter(createTestMessage(key, hash1, 1002L)))
        results.add(freshFilter.filter(createTestMessage(key, hash2, 1003L)))
        results.add(freshFilter.filter(createTestMessage(key, hash1, 1004L)))

        assertEquals(FilterStatus.NEW, results[0])
        assertEquals(FilterStatus.NEW, results[1])
        assertEquals(FilterStatus.NEW, results[2])
        assertEquals(FilterStatus.NEW, results[3])
        assertEquals(FilterStatus.NEW, results[4])
    }

    @Test
    fun `FreshFilterable companion object should create instances correctly`() {
        val stringKey = "test-key"
        val byteKey = stringKey.toByteArray()
        val hash = "hash".toByteArray()
        val timestamp = 1000L

        val fromString = FreshFilterable.of(stringKey, hash, timestamp)
        val fromByteArray = FreshFilterable.of(byteKey, hash, timestamp)

        assertNotNull(fromString)
        assertNotNull(fromByteArray)
        assertEquals(timestamp, fromString.timestamp)
        assertEquals(timestamp, fromByteArray.timestamp)
        assertEquals(hash.contentToString(), fromString.valueHash.contentToString())
        assertEquals(hash.contentToString(), fromByteArray.valueHash.contentToString())
    }

    @Test
    fun `should handle multiple filters with different directories`() {
        val filter1 = FreshFilter(tempDir.resolve("filter1").toString())
        val filter2 = FreshFilter(tempDir.resolve("filter2").toString())

        filter1.use { f1 ->
            filter2.use { f2 ->
                val message = createTestMessage("key", "hash".toByteArray(), 1000L)

                val result1 = f1.filter(message)
                val result2 = f2.filter(message)

                // Both should return NEW since they're independent
                assertEquals(FilterStatus.NEW, result1)
                assertEquals(FilterStatus.NEW, result2)
            }
        }
    }

    private fun createTestMessage(
        key: String,
        hash: ByteArray,
        timestamp: Long,
    ): FreshFilterable = FreshFilterable.of(key, hash, timestamp)
}
