package vanillakotlin.rocksdb.core

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.rocksdb.WriteBatch
import vanillakotlin.random.randomByteArray
import java.io.File
import java.nio.file.Path

class RocksDbStoreTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var store: RocksDbStore
    private lateinit var dataDirectory: String

    @BeforeEach
    fun setUp() {
        dataDirectory = tempDir.resolve("test-rocksdb").toString()
        store = RocksDbStore(dataDirectory)
    }

    @AfterEach
    fun tearDown() {
        store.close()
    }

    @Test
    fun `should create database directory if missing`() {
        val testDir = tempDir.resolve("new-db").toString()
        RocksDbStore(testDir).use { newStore ->
            assertTrue(File(testDir).exists())
            assertTrue(File(testDir).isDirectory)
        }
    }

    @Test
    fun `should put and get single key-value pair`() {
        val key = "test-key".toByteArray()
        val value = "test-value".toByteArray()

        store.put(key, value)
        val retrievedValue = store.get(key)

        assertNotNull(retrievedValue)
        assertTrue(value.contentEquals(retrievedValue))
    }

    @Test
    fun `should return null for non-existent key`() {
        val key = "non-existent-key".toByteArray()
        val retrievedValue = store.get(key)
        assertNull(retrievedValue)
    }

    @Test
    fun `should handle empty values`() {
        val key = "empty-key".toByteArray()
        val value = ByteArray(0)

        store.put(key, value)
        val retrievedValue = store.get(key)

        assertNotNull(retrievedValue)
        assertEquals(0, (retrievedValue ?: fail { "retrievedValue should not be null" }).size)
    }

    @Test
    fun `should overwrite existing key with new value`() {
        val key = "test-key".toByteArray()
        val originalValue = "original-value".toByteArray()
        val newValue = "new-value".toByteArray()

        store.put(key, originalValue)
        store.put(key, newValue)
        val retrievedValue = store.get(key)

        assertNotNull(retrievedValue)
        assertTrue(newValue.contentEquals(retrievedValue))
    }

    @Test
    fun `should delete existing key`() {
        val key = "test-key".toByteArray()
        val value = "test-value".toByteArray()

        store.put(key, value)
        assertNotNull(store.get(key))

        store.delete(key)
        assertNull(store.get(key))
    }

    @Test
    fun `should handle delete of non-existent key gracefully`() {
        val key = "non-existent-key".toByteArray()

        // Should not throw exception
        store.delete(key)
        assertNull(store.get(key))
    }

    @Test
    fun `should handle large values`() {
        val key = "large-key".toByteArray()
        val value = randomByteArray(1024 * 1024) // 1MB

        store.put(key, value)
        val retrievedValue = store.get(key)

        assertNotNull(retrievedValue)
        assertTrue(value.contentEquals(retrievedValue))
    }

    @Test
    fun `should handle binary data`() {
        val key = byteArrayOf(0x01, 0x02, 0x03, 0xFF.toByte())
        val value = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())

        store.put(key, value)
        val retrievedValue = store.get(key)

        assertNotNull(retrievedValue)
        assertTrue(value.contentEquals(retrievedValue))
    }

    @Test
    fun `should handle write batch operations`() {
        val keys = (1..5).map { "key-$it".toByteArray() }
        val values = (1..5).map { "value-$it".toByteArray() }

        WriteBatch().use { batch ->
            keys.zip(values).forEach { (key, value) ->
                batch.put(key, value)
            }
            store.write(batch)
        }

        keys.zip(values).forEach { (key, expectedValue) ->
            val retrievedValue = store.get(key)
            assertNotNull(retrievedValue)
            assertTrue(expectedValue.contentEquals(retrievedValue))
        }
    }

    @Test
    fun `should iterate over all key-value pairs with withSequence`() {
        val testData = mapOf(
            "key1".toByteArray() to "value1".toByteArray(),
            "key2".toByteArray() to "value2".toByteArray(),
            "key3".toByteArray() to "value3".toByteArray(),
        )

        testData.forEach { (key, value) ->
            store.put(key, value)
        }

        val retrievedPairs = mutableListOf<KeyValuePair>()
        store.withSequence { sequence ->
            retrievedPairs.addAll(sequence.toList())
        }

        assertEquals(testData.size, retrievedPairs.size)
        retrievedPairs.forEach { pair ->
            val expectedValue = testData.entries.find { it.key.contentEquals(pair.key) }?.value
            assertNotNull(expectedValue)
            assertNotNull(pair.value)
            assertTrue(expectedValue.contentEquals(pair.value))
        }
    }

    @Test
    fun `should iterate over range of keys with withRange`() {
        val testData = mapOf(
            "a".toByteArray() to "value-a".toByteArray(),
            "b".toByteArray() to "value-b".toByteArray(),
            "c".toByteArray() to "value-c".toByteArray(),
            "d".toByteArray() to "value-d".toByteArray(),
            "e".toByteArray() to "value-e".toByteArray(),
        )

        testData.forEach { (key, value) ->
            store.put(key, value)
        }

        val retrievedPairs = mutableListOf<KeyValuePair>()
        store.withRange("b".toByteArray(), "d".toByteArray()) { sequence ->
            retrievedPairs.addAll(sequence.toList())
        }

        assertEquals(3, retrievedPairs.size) // b, c, d
        val retrievedKeys = retrievedPairs.map { String(it.key) }.sorted()
        assertEquals(listOf("b", "c", "d"), retrievedKeys)
    }

    @Test
    fun `should iterate over keys with prefix using withPrefixed`() {
        val testData = mapOf(
            "user:1".toByteArray() to "data1".toByteArray(),
            "user:2".toByteArray() to "data2".toByteArray(),
            "user:3".toByteArray() to "data3".toByteArray(),
            "admin:1".toByteArray() to "admin-data".toByteArray(),
            "guest:1".toByteArray() to "guest-data".toByteArray(),
        )

        testData.forEach { (key, value) ->
            store.put(key, value)
        }

        val retrievedPairs = mutableListOf<KeyValuePair>()
        store.withPrefixed("user:".toByteArray()) { sequence ->
            retrievedPairs.addAll(sequence.toList())
        }

        assertEquals(3, retrievedPairs.size)
        retrievedPairs.forEach { pair ->
            assertTrue(String(pair.key).startsWith("user:"))
        }
    }

    @Test
    fun `should handle empty prefix search`() {
        store.put("test".toByteArray(), "value".toByteArray())

        val retrievedPairs = mutableListOf<KeyValuePair>()
        store.withPrefixed(ByteArray(0)) { sequence ->
            retrievedPairs.addAll(sequence.toList())
        }

        assertEquals(1, retrievedPairs.size)
    }

    @Test
    fun `should handle empty range search`() {
        val retrievedPairs = mutableListOf<KeyValuePair>()
        store.withRange("a".toByteArray(), "z".toByteArray()) { sequence ->
            retrievedPairs.addAll(sequence.toList())
        }

        assertEquals(0, retrievedPairs.size)
    }

    @Test
    fun `should handle compaction operations`() {
        // Add some data
        repeat(100) { i ->
            store.put("key-$i".toByteArray(), "value-$i".toByteArray())
        }

        // Should not throw exceptions
        store.enableAutoCompaction()
        store.compact()

        // Verify data is still accessible after compaction
        val retrievedValue = store.get("key-50".toByteArray())
        assertNotNull(retrievedValue)
        assertTrue("value-50".toByteArray().contentEquals(retrievedValue))
    }

    @Test
    fun `should handle custom configuration`() {
        val customStore = RocksDbStore(
            dataDirectory = tempDir.resolve("custom-db").toString(),
            configureOptions = { options ->
                options.setCreateIfMissing(true)
                options.setMaxOpenFiles(100)
            },
        )

        customStore.use { store ->
            store.put("test".toByteArray(), "value".toByteArray())
            val value = store.get("test".toByteArray())
            assertNotNull(value)
            assertTrue("value".toByteArray().contentEquals(value))
        }
    }

    @Test
    fun `should handle multiple stores in different directories`() {
        val store1 = RocksDbStore(tempDir.resolve("store1").toString())
        val store2 = RocksDbStore(tempDir.resolve("store2").toString())

        store1.use { s1 ->
            store2.use { s2 ->
                s1.put("key".toByteArray(), "value1".toByteArray())
                s2.put("key".toByteArray(), "value2".toByteArray())

                val value1 = s1.get("key".toByteArray())
                val value2 = s2.get("key".toByteArray())

                assertNotNull(value1)
                assertNotNull(value2)
                assertTrue("value1".toByteArray().contentEquals(value1 ?: fail { "value1 should not be null" }))
                assertTrue("value2".toByteArray().contentEquals(value2 ?: fail { "value2 should not be null" }))
            }
        }
    }

    @Test
    fun `should handle stress test with many operations`() {
        val numOperations = 1000
        val keys = (1..numOperations).map { "stress-key-$it".toByteArray() }
        val values = (1..numOperations).map { randomByteArray(100) }

        // Write all data
        keys.zip(values).forEach { (key, value) ->
            store.put(key, value)
        }

        // Verify all data
        keys.zip(values).forEach { (key, expectedValue) ->
            val retrievedValue = store.get(key)
            assertNotNull(retrievedValue)
            assertTrue(expectedValue.contentEquals(retrievedValue))
        }

        // Delete half the data
        keys.take(numOperations / 2).forEach { key ->
            store.delete(key)
        }

        // Verify deletions
        keys.take(numOperations / 2).forEach { key ->
            assertNull(store.get(key))
        }

        // Verify remaining data
        keys.drop(numOperations / 2).zip(values.drop(numOperations / 2)).forEach { (key, expectedValue) ->
            val retrievedValue = store.get(key)
            assertNotNull(retrievedValue)
            assertTrue(expectedValue.contentEquals(retrievedValue))
        }
    }
} 
