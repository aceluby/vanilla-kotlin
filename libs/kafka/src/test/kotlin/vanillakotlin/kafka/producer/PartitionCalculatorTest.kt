package vanillakotlin.kafka.producer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PartitionCalculatorTest {

    @Test fun `should return null for null partition key`() {
        val result = PartitionCalculator.partitionFor(null, 10)
        assertNull(result)
    }

    @Test fun `should calculate partition for given key and partition count`() {
        val partitionKey = "test-key"
        val partitionCount = 5

        val partition = PartitionCalculator.partitionFor(partitionKey, partitionCount)

        assertTrue(partition?.let { it >= 0 } ?: false, "Partition should be non-negative")
        assertTrue(partition?.let { it < partitionCount } ?: false, "Partition should be less than partition count")
    }

    @Test fun `should consistently return same partition for same key`() {
        val partitionKey = "consistent-key"
        val partitionCount = 10

        val partition1 = PartitionCalculator.partitionFor(partitionKey, partitionCount)
        val partition2 = PartitionCalculator.partitionFor(partitionKey, partitionCount)

        assertEquals(partition1, partition2, "Same key should always map to same partition")
    }

    @Test fun `should distribute keys across partitions`() {
        val partitionCount = 5
        val partitions = mutableSetOf<Int>()

        // Generate many keys to test distribution
        repeat(100) { i ->
            val partition = PartitionCalculator.partitionFor("key-$i", partitionCount)
            partition?.let { partitions.add(it) }
        }

        // Should use multiple partitions (at least 3 out of 5)
        assertTrue(partitions.size >= 3, "Keys should be distributed across multiple partitions")

        // All partitions should be within valid range
        partitions.forEach { partition ->
            assertTrue(partition >= 0 && partition < partitionCount)
        }
    }

    @Test fun `should handle different partition counts`() {
        val partitionKey = "test-key"

        val partition3 = PartitionCalculator.partitionFor(partitionKey, 3)
        val partition10 = PartitionCalculator.partitionFor(partitionKey, 10)
        val partition100 = PartitionCalculator.partitionFor(partitionKey, 100)

        assertTrue(partition3?.let { it < 3 } ?: false)
        assertTrue(partition10?.let { it < 10 } ?: false)
        assertTrue(partition100?.let { it < 100 } ?: false)
    }

    @Test fun `should handle single partition`() {
        val partitionKey = "single-partition-key"

        val partition = PartitionCalculator.partitionFor(partitionKey, 1)

        assertEquals(0, partition, "Single partition should always be 0")
    }

    @Test fun `should handle empty string key`() {
        val partition = PartitionCalculator.partitionFor("", 5)

        assertTrue(partition?.let { it >= 0 } ?: false, "Empty string should still produce valid partition")
        assertTrue(partition?.let { it < 5 } ?: false, "Empty string partition should be within range")
    }

    @Test fun `should handle special characters in key`() {
        val specialKeys = listOf(
            "key-with-dashes",
            "key_with_underscores",
            "key.with.dots",
            "key@with@symbols",
            "key with spaces",
            "key/with/slashes",
        )

        specialKeys.forEach { key ->
            val partition = PartitionCalculator.partitionFor(key, 10)
            assertTrue(
                partition?.let { it >= 0 && it < 10 } ?: false,
                "Special character key '$key' should produce valid partition",
            )
        }
    }

    @Test fun `should handle unicode characters`() {
        val unicodeKeys = listOf(
            "키-한국어",
            "clé-français",
            "ключ-русский",
            "键-中文",
            "🔑-emoji",
        )

        unicodeKeys.forEach { key ->
            val partition = PartitionCalculator.partitionFor(key, 7)
            assertTrue(
                partition?.let { it >= 0 && it < 7 } ?: false,
                "Unicode key '$key' should produce valid partition",
            )
        }
    }

    @Test fun `should demonstrate consistent hashing behavior`() {
        val partitionCount = 8
        val keyToPartitionMap = mutableMapOf<String, Int>()

        // Map a set of keys to partitions
        val testKeys = (1..20).map { "user-$it" }
        testKeys.forEach { key ->
            val partition = PartitionCalculator.partitionFor(key, partitionCount)
            partition?.let {
                keyToPartitionMap[key] = it
            }
        }

        // Verify consistency over multiple calls
        repeat(5) {
            testKeys.forEach { key ->
                val partition = PartitionCalculator.partitionFor(key, partitionCount)
                partition?.let {
                    assertEquals(
                        keyToPartitionMap[key],
                        it,
                        "Key '$key' should always map to same partition",
                    )
                }
            }
        }
    }
}
