package vanillakotlin.rocksdb.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class RocksDbConfigTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `should create default RocksDbConfig`() {
        val config = RocksDbConfig()

        assertNotNull(config.compression)
        assertEquals(16L, config.blockSizeKb)
        assertTrue(config.enableBlockCache)
        assertTrue(config.enableBloomFilter)
        assertEquals(10.0, config.bloomFilterBits)
        assertTrue(config.useBlockBasedFilter)
        assertFalse(config.disableAutoCompactions)
    }

    @Test
    fun `should configure options with default config`() {
        val config = RocksDbConfig()
        val store = RocksDbStore(
            dataDirectory = tempDir.resolve("default-config").toString(),
            configureOptions = config::configureOptions,
        )

        store.use { s ->
            // Should work without throwing exceptions
            s.put("test".toByteArray(), "value".toByteArray())
            val value = s.get("test".toByteArray())
            assertNotNull(value)
        }
    }

    @Test
    fun `should apply largeValueWriteHeavyConfigArchetype`() {
        val store = RocksDbStore(
            dataDirectory = tempDir.resolve("large-value-write-heavy").toString(),
            configureOptions = largeValueWriteHeavyConfigArchetype::configureOptions,
        )

        store.use { s ->
            // Test with larger values
            val largeValue = ByteArray(100 * 1024) { it.toByte() } // 100KB
            s.put("large-key".toByteArray(), largeValue)
            val retrievedValue = s.get("large-key".toByteArray())
            assertNotNull(retrievedValue)
            assertTrue(largeValue.contentEquals(retrievedValue ?: fail { "retrievedValue should not be null" }))
        }
    }

    @Test
    fun `should apply smallValueReadHeavyConfigArchetype`() {
        val store = RocksDbStore(
            dataDirectory = tempDir.resolve("small-value-read-heavy").toString(),
            configureOptions = smallValueReadHeavyConfigArchetype::configureOptions,
        )

        store.use { s ->
            // Test with many small values for read-heavy workload
            val testData = (1..100).associate {
                "key-$it".toByteArray() to "small-value-$it".toByteArray()
            }

            // Write all data
            testData.forEach { (key, value) ->
                s.put(key, value)
            }

            // Read all data multiple times (read-heavy)
            repeat(5) {
                testData.forEach { (key, expectedValue) ->
                    val retrievedValue = s.get(key)
                    assertNotNull(retrievedValue)
                    assertTrue(expectedValue.contentEquals(retrievedValue ?: fail { "retrievedValue should not be null" }))
                }
            }
        }
    }

    @Test
    fun `should apply bulkLoadSmallReadConfigArchetype`() {
        val store = RocksDbStore(
            dataDirectory = tempDir.resolve("bulk-load-small-read").toString(),
            configureOptions = bulkLoadSmallReadConfigArchetype::configureOptions,
        )

        store.use { s ->
            // Test bulk loading
            val bulkData = (1..1000).associate {
                "bulk-key-$it".toByteArray() to "bulk-value-$it".toByteArray()
            }

            // Bulk load data
            bulkData.forEach { (key, value) ->
                s.put(key, value)
            }

            // Enable auto compaction after bulk load (as recommended)
            s.enableAutoCompaction()

            // Verify data
            bulkData.entries.take(10).forEach { (key, expectedValue) ->
                val retrievedValue = s.get(key)
                assertNotNull(retrievedValue)
                assertTrue(expectedValue.contentEquals(retrievedValue ?: fail { "retrievedValue should not be null" }))
            }
        }
    }

    @Test
    fun `should apply tinyArchetype`() {
        val store = RocksDbStore(
            dataDirectory = tempDir.resolve("tiny").toString(),
            configureOptions = tinyArchetype::configureOptions,
        )

        store.use { s ->
            // Test with very small values
            val tinyData = (1..50).associate {
                "t$it".toByteArray() to "$it".toByteArray()
            }

            tinyData.forEach { (key, value) ->
                s.put(key, value)
            }

            tinyData.forEach { (key, expectedValue) ->
                val retrievedValue = s.get(key)
                assertNotNull(retrievedValue)
                assertTrue(expectedValue.contentEquals(retrievedValue ?: fail { "retrievedValue should not be null" }))
            }
        }
    }

    @Test
    fun `should handle custom configuration modifications`() {
        val customConfig = RocksDbConfig().apply {
            blockSizeKb = 64
            enableBloomFilter = false
            writeBufferSizeMb = 8
            maxWriteBufferNum = 4
        }

        val store = RocksDbStore(
            dataDirectory = tempDir.resolve("custom-config").toString(),
            configureOptions = customConfig::configureOptions,
        )

        store.use { s ->
            s.put("custom-test".toByteArray(), "custom-value".toByteArray())
            val value = s.get("custom-test".toByteArray())
            assertNotNull(value)
            assertTrue("custom-value".toByteArray().contentEquals(value ?: fail { "value should not be null" }))
        }
    }

    @Test
    fun `should handle compression configuration`() {
        val globalCompressionConfig = RocksDbConfig().apply {
            compression = RocksDbColumnFamilyCompressionOptions.Global().apply {
                type = "zstd"
            }
        }

        val store = RocksDbStore(
            dataDirectory = tempDir.resolve("compression-global").toString(),
            configureOptions = globalCompressionConfig::configureOptions,
        )

        store.use { s ->
            val testData = "This is test data that should be compressed".toByteArray()
            s.put("compression-test".toByteArray(), testData)
            val retrievedData = s.get("compression-test".toByteArray())
            assertNotNull(retrievedData)
            assertTrue(testData.contentEquals(retrievedData ?: fail { "retrievedData should not be null" }))
        }
    }

    @Test
    fun `should handle per-level compression configuration`() {
        val perLevelCompressionConfig = RocksDbConfig().apply {
            compression = RocksDbColumnFamilyCompressionOptions.PerLevel().apply {
                types.addAll(arrayOf("lz4", "lz4"))
                defaultType = "zstd"
            }
        }

        val store = RocksDbStore(
            dataDirectory = tempDir.resolve("compression-per-level").toString(),
            configureOptions = perLevelCompressionConfig::configureOptions,
        )

        store.use { s ->
            val testData = "This is test data for per-level compression".toByteArray()
            s.put("per-level-test".toByteArray(), testData)
            val retrievedData = s.get("per-level-test".toByteArray())
            assertNotNull(retrievedData)
            assertTrue(testData.contentEquals(retrievedData ?: fail { "retrievedData should not be null" }))
        }
    }

    @Test
    fun `should handle cache configuration`() {
        val cacheConfig = RocksDbConfig().apply {
            enableBlockCache = true
            cacheSizeMb = 128
            cacheIndexAndFilterBlocks = true
        }

        val store = RocksDbStore(
            dataDirectory = tempDir.resolve("cache-config").toString(),
            configureOptions = cacheConfig::configureOptions,
        )

        store.use { s ->
            // Add some data to test caching
            repeat(100) { i ->
                s.put("cache-key-$i".toByteArray(), "cache-value-$i".toByteArray())
            }

            // Read data multiple times to exercise cache
            repeat(3) {
                val value = s.get("cache-key-50".toByteArray())
                assertNotNull(value)
                assertTrue("cache-value-50".toByteArray().contentEquals(value ?: fail { "value should not be null" }))
            }
        }
    }

    @Test
    fun `should handle write buffer configuration`() {
        val writeBufferConfig = RocksDbConfig().apply {
            writeBufferSizeMb = 16
            maxWriteBufferNum = 3
            minWriteBufferNumberToMerge = 2
        }

        val store = RocksDbStore(
            dataDirectory = tempDir.resolve("write-buffer-config").toString(),
            configureOptions = writeBufferConfig::configureOptions,
        )

        store.use { s ->
            // Write enough data to potentially trigger write buffer operations
            repeat(1000) { i ->
                s.put("wb-key-$i".toByteArray(), "write-buffer-value-$i".toByteArray())
            }

            // Verify some data
            val value = s.get("wb-key-500".toByteArray())
            assertNotNull(value)
            assertTrue("write-buffer-value-500".toByteArray().contentEquals(value))
        }
    }

    @Test
    fun `should handle compaction configuration`() {
        val compactionConfig = RocksDbConfig().apply {
            disableAutoCompactions = true
            level0FileNumCompactionTrigger = 10
            level0SlowdownWritesTrigger = 50
            level0StopWritesTrigger = 60
        }

        val store = RocksDbStore(
            dataDirectory = tempDir.resolve("compaction-config").toString(),
            configureOptions = compactionConfig::configureOptions,
        )

        store.use { s ->
            // Add data with auto-compaction disabled
            repeat(100) { i ->
                s.put("compact-key-$i".toByteArray(), "compact-value-$i".toByteArray())
            }

            // Manually trigger compaction
            s.compact()

            // Verify data after compaction
            val value = s.get("compact-key-50".toByteArray())
            assertNotNull(value)
            assertTrue("compact-value-50".toByteArray().contentEquals(value))
        }
    }

    @Test
    fun `archetypes should have different configurations`() {
        // Verify that different archetypes have different settings
        assertFalse(largeValueWriteHeavyConfigArchetype.disableAutoCompactions)
        assertTrue(bulkLoadSmallReadConfigArchetype.disableAutoCompactions)

        assertTrue(largeValueWriteHeavyConfigArchetype.blockSizeKb > smallValueReadHeavyConfigArchetype.blockSizeKb)
        assertTrue(smallValueReadHeavyConfigArchetype.blockSizeKb > tinyArchetype.blockSizeKb)

        assertTrue(
            (
                largeValueWriteHeavyConfigArchetype.writeBufferSizeMb ?: fail {
                    "largeValueWriteHeavyConfigArchetype.writeBufferSizeMb should not be null"
                }
                ) > (tinyArchetype.writeBufferSizeMb ?: fail { "tinyArchetype.writeBufferSizeMb should not be null" }),
        )
    }

    @Test
    fun `should handle bloom filter configuration`() {
        val bloomFilterConfig = RocksDbConfig().apply {
            enableBloomFilter = true
            bloomFilterBits = 15.0
            useBlockBasedFilter = false
        }

        val store = RocksDbStore(
            dataDirectory = tempDir.resolve("bloom-filter-config").toString(),
            configureOptions = bloomFilterConfig::configureOptions,
        )

        store.use { s ->
            // Add data to test bloom filter effectiveness
            val existingKeys = (1..100).map { "existing-$it" }
            val nonExistingKeys = (1..100).map { "missing-$it" }

            existingKeys.forEach { key ->
                s.put(key.toByteArray(), "value-$key".toByteArray())
            }

            // Test existing keys
            existingKeys.forEach { key ->
                val value = s.get(key.toByteArray())
                assertNotNull(value)
            }

            // Test non-existing keys (bloom filter should help here)
            nonExistingKeys.forEach { key ->
                val value = s.get(key.toByteArray())
                // Should be null, bloom filter helps avoid disk reads
                assertEquals(null, value)
            }
        }
    }
} 
