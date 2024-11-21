package vanillakotlin.kafka

// metric names
internal const val KAFKA_SEND_METRIC = "kafka.send"
internal const val KAFKA_PROCESS_TIMER_METRIC = "kafka.process.time"
internal const val LAG_METRIC = "kafka.topic.consumer.lag"
internal const val KAFKA_WORKER_CHANNEL_SIZE_METRIC = "kafka.worker.channel"
internal const val KAFKA_PUBLISHER_CHANNEL_SIZE_METRIC = "kafka.publisher.channel"
internal const val KAFKA_ACK_CHANNEL_SIZE_METRIC = "kafka.ack.channel"
internal const val KAFKA_POLLER_TIMER_METRIC = "kafka.poller.time"
internal const val KAFKA_PUBLISHER_TIMER_METRIC = "kafka.publisher.time"
internal const val KAFKA_ACK_TIMER_METRIC = "kafka.ack.time"
internal const val TRANSFORMER_EVENT = "transformer.event"
internal const val AGE_METRIC = "kafka.message.age"

// tag names
internal const val APP_TAG = "app"
internal const val TOPIC_TAG = "topic"
internal const val PARTITION_TAG = "partition"
internal const val SUCCESS_TAG = "success"
internal const val STATUS_TAG = "status"
internal const val FORWARDED_TAG = "forwarded"
internal const val DROPPED_TAG = "dropped"
internal const val WORKER_TAG = "worker"
internal const val SKIPPED_TAG = "skipped"
