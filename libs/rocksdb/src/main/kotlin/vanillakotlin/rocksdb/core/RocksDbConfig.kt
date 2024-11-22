package vanillakotlin.rocksdb.core

import org.rocksdb.BlockBasedTableConfig
import org.rocksdb.BloomFilter
import org.rocksdb.LRUCache
import org.rocksdb.Options

/**
 * It's recommended to use an archetype based on your usage pattern.
 * Reference one of the included archetypes below when defining your RocksDbStore.
 * e.g.
 * val rocksDbStore = RocksDbStore(
 *     configureOptions = smallValueReadHeavyConfigArchetype::configureOptions
 * )
 *
 * You can also make your own archetype, or tweak existing values in your configureOptions function.
 */
class RocksDbConfig {
    var compression: RocksDbColumnFamilyCompressionOptions = RocksDbColumnFamilyCompressionOptions.Global()

    var blockSizeKb: Long = 16
    var blockRestartInterval: Int? = null
    var enableBlockCache: Boolean = true
    var cacheSizeMb: Long? = null
    var cacheIndexAndFilterBlocks: Boolean? = null

    var enableBloomFilter = true
    var bloomFilterBits = 10.0
    var useBlockBasedFilter = true

    var writeBufferSizeMb: Long? = null
    var maxWriteBufferNum: Int? = null
    var maxBytesForLevelBaseMb: Long? = null
    var targetFileSizeBaseMb: Long? = null
    var minWriteBufferNumberToMerge: Int? = null

    var maxBackgroundJobs: Int? = null

    var disableAutoCompactions = false
    var level0FileNumCompactionTrigger: Int? = null
    var level0SlowdownWritesTrigger: Int? = null
    var level0StopWritesTrigger: Int? = null

    fun configureOptions(options: Options) {
        options.apply {
            setDisableAutoCompactions(disableAutoCompactions)

            compression.configureCompressionOptions(this)

            setTableFormatConfig(
                BlockBasedTableConfig().apply {
                    setBlockSize(blockSizeKb * 1024L)
                    blockRestartInterval?.let(::setBlockRestartInterval)

                    setNoBlockCache(!enableBlockCache)
                    cacheIndexAndFilterBlocks?.let(::setCacheIndexAndFilterBlocks)
                    cacheSizeMb?.let { setBlockCache(LRUCache(it * 1024 * 1024L)) }

                    if (enableBloomFilter) {
                        setFilterPolicy(BloomFilter(bloomFilterBits, useBlockBasedFilter))
                    }
                },
            )

            maxBackgroundJobs?.let { ::setMaxBackgroundJobs }

            writeBufferSizeMb?.let { setWriteBufferSize(it * 1024 * 1024) }

            maxWriteBufferNum?.let(::setMaxWriteBufferNumber)
            maxBytesForLevelBaseMb?.let { setMaxBytesForLevelBase(it * 1024 * 1024) }
            minWriteBufferNumberToMerge?.let(::setMinWriteBufferNumberToMerge)
            targetFileSizeBaseMb?.let { setTargetFileSizeBase(it * 1024 * 1024) }

            level0FileNumCompactionTrigger?.let(::setLevel0FileNumCompactionTrigger)
            level0SlowdownWritesTrigger?.let(::setLevel0SlowdownWritesTrigger)
            level0StopWritesTrigger?.let(::setLevel0StopWritesTrigger)
        }
    }
}

/*
The column family archetype here is:
- large values, often 100KB plus compressed
- write heavy - with debouncing often writing much more than we read
- iterator lookups, not point/get lookups (bloom filters don't help much here, could consider disabling those)

we want:
- big block size to allow us to get better compression on larger blocks
- lots of big write buffers so that
- waiting to merge write buffers so that we can be more efficient by batching things up

*/
val largeValueWriteHeavyConfigArchetype =
    RocksDbConfig().apply {
        compression =
            RocksDbColumnFamilyCompressionOptions.PerLevel().apply {
                // use lz4 level 9 the for level 0-1, use zstd with a dictionary after that
                types.addAll(arrayOf("lz4", "lz4"))

                defaultType = "zstd"
                level = 9
                dictionaryBytesKb = 128
            }

        // big to allow compression to go across the larger block, this is the size
        // that will come back from rocksdb for every query, so for small payloads this can be expensive on get
        blockSizeKb = 128
        enableBlockCache = true
        cacheSizeMb = 2048
        cacheIndexAndFilterBlocks = false

        // this is used during `get` operations, but not for iterators, could consider disabling this if there are never gets
        // we can look at "prefix" bloom filters for iterators, but they are more work
        enableBloomFilter = true
        bloomFilterBits = 10.0
        useBlockBasedFilter = false

        // this is per write buffer
        writeBufferSizeMb = 32

        // one is active, the others are waiting to be flushed
        maxWriteBufferNum = 8
        // having this at 1 would mean flush right away, > 1 lets it not force a merge operation on filling a single buffer
        minWriteBufferNumberToMerge = 4
        // this is the L0 base, all levels beyond this will be 10x increase in size
        maxBytesForLevelBaseMb = 1024
        targetFileSizeBaseMb = 128

        level0FileNumCompactionTrigger = 1
        level0SlowdownWritesTrigger = 50
        level0StopWritesTrigger = 60
    }

// WARNING: this archetype disables auto-compaction, so be sure to re-enable autocompactions after initial load
val bulkLoadSmallReadConfigArchetype =
    RocksDbConfig().apply {
        compression =
            RocksDbColumnFamilyCompressionOptions.PerLevel().apply {
                types.addAll(arrayOf("lz4", "lz4"))

                defaultType = "zstd"
                level = 9
                dictionaryBytesKb = 128
            }

        disableAutoCompactions = true

        blockSizeKb = 64
        enableBlockCache = true
        cacheSizeMb = 512
        cacheIndexAndFilterBlocks = false

        enableBloomFilter = false

        // this is per write buffer
        writeBufferSizeMb = 32

        // one is active, the others are waiting to be flushed
        maxWriteBufferNum = 8
        // having this at 1 would mean flush right away, > 1 lets it not force a merge operation on filling a single buffer
        minWriteBufferNumberToMerge = 4
        // this is the L0 base, all levels beyond this will be 10x increase in size
        maxBytesForLevelBaseMb = 1024
        targetFileSizeBaseMb = 128

        level0FileNumCompactionTrigger = 10
        level0SlowdownWritesTrigger = 60
        level0StopWritesTrigger = 80
    }

/*
  column family archetype:
  - small values
  - writing not heavily outweighing reading
  - often: lots of point lookups/gets

  we want:
  - smaller block sizes here (each `get` needs to retrieve/decompress a block worth of information
  - bloom filters are useful
  - decent write buffers, but not huge

 */
val smallValueReadHeavyConfigArchetype =
    RocksDbConfig().apply {
        compression =
            RocksDbColumnFamilyCompressionOptions.Global().apply {
                type = "zstd"
                level = 9
                dictionaryBytesKb = 64
            }

        blockSizeKb = 32
        enableBlockCache = true
        cacheSizeMb = 1024
        cacheIndexAndFilterBlocks = false

        enableBloomFilter = true
        bloomFilterBits = 10.0
        useBlockBasedFilter = false

        writeBufferSizeMb = 16
        maxWriteBufferNum = 6
        minWriteBufferNumberToMerge = 2
        maxBytesForLevelBaseMb = 128
        targetFileSizeBaseMb = 16

        level0FileNumCompactionTrigger = 1
        level0SlowdownWritesTrigger = 30
        level0StopWritesTrigger = 40
    }

/*
  used for column families that have very little data in them
  have individual values that are very small
  and are read and written to a decent amount

  ex: storing partition offsets
 */
val tinyArchetype =
    RocksDbConfig().apply {
        compression =
            RocksDbColumnFamilyCompressionOptions.Global().apply {
                type = "zstd"
                level = 4
                dictionaryBytesKb = 32
            }

        blockSizeKb = 4
        enableBlockCache = true
        cacheSizeMb = 16
        cacheIndexAndFilterBlocks = false

        enableBloomFilter = true
        bloomFilterBits = 10.0
        useBlockBasedFilter = false

        writeBufferSizeMb = 4
        maxWriteBufferNum = 1
        minWriteBufferNumberToMerge = 1
        maxBytesForLevelBaseMb = 16
        targetFileSizeBaseMb = 4

        level0FileNumCompactionTrigger = 1
        level0SlowdownWritesTrigger = 30
        level0StopWritesTrigger = 40
    }
