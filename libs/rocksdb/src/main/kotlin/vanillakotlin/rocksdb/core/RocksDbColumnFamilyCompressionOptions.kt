package vanillakotlin.rocksdb.core

import org.rocksdb.CompressionOptions
import org.rocksdb.CompressionType
import org.rocksdb.Options

sealed class RocksDbColumnFamilyCompressionOptions {
    class Global : RocksDbColumnFamilyCompressionOptions() {
        var type: String = "lz4"
    }

    class PerLevel : RocksDbColumnFamilyCompressionOptions() {
        var defaultType: String = "lz4"
        val types = mutableListOf<String>()
    }

    var level: Int = 4
    var dictionaryBytesKb: Int? = null
    var maxTrainBytesKb: Int? = null

    fun configureCompressionOptions(options: Options) {
        when (this) {
            is Global -> {
                val compressionType = CompressionType.getCompressionType(type)
                options.setCompressionType(compressionType)
            }
            is PerLevel -> {
                val compressionTypes =
                    (0..options.numLevels())
                        .map { types.elementAtOrNull(it) ?: defaultType }
                        .map { CompressionType.getCompressionType(it) }
                        .toList()

                options.setCompressionPerLevel(compressionTypes)
            }
        }

        options.setCompressionOptions(
            CompressionOptions()
                .setLevel(level)
                .apply {
                    val bytesKb = dictionaryBytesKb

                    if (bytesKb != null) {
                        setMaxDictBytes(bytesKb * 1024)
                    }

                    val maxTrainBytesKb = maxTrainBytesKb
                    if (maxTrainBytesKb != null) {
                        setZStdMaxTrainBytes(maxTrainBytesKb * 1024)
                    }
                },
        )
    }
}
