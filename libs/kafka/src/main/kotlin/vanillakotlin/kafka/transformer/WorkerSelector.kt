package vanillakotlin.kafka.transformer

import kotlin.math.absoluteValue

fun interface WorkerSelector {
    fun selectWorker(key: String?): Int
}

class DefaultWorkerSelector(private val numberOfWorkers: Int) : WorkerSelector {
    override fun selectWorker(key: String?): Int = key?.hashCode()?.absoluteValue?.rem(numberOfWorkers) ?: 0
}

// this is to be used when receiving null keys in the message
class RoundRobinWorkerSelector(private val numberOfWorkers: Int) : WorkerSelector {
    // always send use worker index 0 since all workers are listening to the same channel
    // this allows for messages to be worked on as room becomes available for any worker
    override fun selectWorker(key: String?): Int = 0
}
