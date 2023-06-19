package com.birdthedeveloper.prometheus.android.prometheus.android.exporter.worker

import android.util.Log
import io.prometheus.client.Collector.MetricFamilySamples
import org.iq80.snappy.Snappy
import remote.write.RemoteWrite.Label
import remote.write.RemoteWrite.Sample
import remote.write.RemoteWrite.TimeSeries
import remote.write.RemoteWrite.WriteRequest
import java.util.Enumeration
import java.util.LinkedList
import java.util.Queue

private const val TAG: String = "REMOTE_WRITE_SENDER_STORAGE"

// This is a very primitive implementation, may require some optimization later
//
// No need for locks as all operations are run on a single thread, defined in PromWorker
// This class defines contract for RemoteWriteSender storage

// data classes, the same structure as MetricFamilySamples
data class MetricsScrape(
    val timeSeriesList : List<StorageTimeSeries>
){
    companion object {
        fun fromMfs(input : Enumeration<MetricFamilySamples>) : MetricsScrape {
            val timeSeriesList : MutableList<StorageTimeSeries> = mutableListOf()

            for (family in input){
                for (sample in family.samples){
                    if (sample != null){
                        val labels : MutableList<TimeSeriesLabel> = mutableListOf()

                        // name label
                        val sampleName : String = sample.name
                        val sampleNameLabel = TimeSeriesLabel(
                            name = "__name__",
                            value = sampleName
                        )
                        labels.add(sampleNameLabel)

                        // labels are stored in parallel lists -> iterate over two lists at a time
                        val labelNamesIterator = sample.labelNames.iterator()
                        val labelValuesIterator = sample.labelValues.iterator()

                        while (labelNamesIterator.hasNext() && labelValuesIterator.hasNext()) {
                            val labelName: String = labelNamesIterator.next()
                            val labelValue: String = labelValuesIterator.next()

                            val label = TimeSeriesLabel(
                                name = labelName,
                                value = labelValue,
                            )
                            labels.add(label)
                        }

                        val timeSeries = StorageTimeSeries(
                            labels = labels.toList(),
                            sample = TimeSeriesSample(
                                value = sample.value,
                                timeStampMs = System.currentTimeMillis(),
                            )
                        )
                        timeSeriesList.add(timeSeries)
                    }
                }
            }

            return MetricsScrape(
                timeSeriesList = timeSeriesList
            )
        }
    }
}

data class StorageTimeSeries(
    val sample : TimeSeriesSample,
    val labels : List<TimeSeriesLabel>,
)

data class TimeSeriesLabel(
    val name: String,
    val value: String,
) {
    fun toProtobufLabel(): Label {
        return Label.newBuilder()
            .setName(this.name)
            .setValue(this.value)
            .build()
    }
}

data class TimeSeriesSample(
    val timeStampMs: Long,
    val value: Double,
) {
    fun toProtobufSample(): Sample {
        return Sample.newBuilder()
            .setTimestamp(this.timeStampMs)
            .setValue(this.value)
            .build()
    }
}

abstract class RemoteWriteSenderStorage {
    companion object{
        protected const val maxMetricsAge : Int = 58 * 60 // 58 minutes

        protected val remoteWriteLabel: TimeSeriesLabel = TimeSeriesLabel(
            name = "backfill",
            value = "true",
        )
        protected fun encodeWithSnappy(data: ByteArray): ByteArray {
            return Snappy.compress(data)
        }
    }

    abstract fun writeScrapedSample(metricsScrape: MetricsScrape)
    abstract fun getScrapedSamplesCompressedProtobuf(howMany: Int): ByteArray
    abstract fun removeNumberOfScrapedSamples(number: Int)
    abstract fun isEmpty(): Boolean
    abstract fun getLength(): Int
}

class RemoteWriteSenderSimpleMemoryStorage : RemoteWriteSenderStorage() {
    private val data: Queue<MetricsScrape> = LinkedList()

    override fun getScrapedSamplesCompressedProtobuf(howMany: Int): ByteArray {
        if (howMany < 1) {
            throw IllegalArgumentException("howMany must be bigger than zero")
        }

        val scrapedMetrics: MutableList<MetricsScrape> = mutableListOf()
        for (i in 1..howMany) {
            val oneMetric: MetricsScrape? = data.poll()
            if (oneMetric == null) {
                break
            } else {
                scrapedMetrics.add(oneMetric)
            }
        }
        Log.d(TAG, "Getting scraped samples: ${scrapedMetrics.size} samples")

        filterExpiredMetrics(scrapedMetrics)

        val writeRequest: WriteRequest = this.metricsScrapeListToProtobuf(scrapedMetrics.toList())
        val bytes: ByteArray = writeRequest.toByteArray()
        return this.encodeWithSnappy(bytes)
    }

    //TODO use this thing
    override fun removeNumberOfScrapedSamples(number: Int) {
        if (number > 0) {
            for (i in 1..number) {
                if(data.isEmpty()){
                    break;
                }else{
                    data.remove()
                }
            }
        } else {
            throw IllegalArgumentException("number must by higher than 0")
        }
    }

    override fun writeScrapedSample(metricsScrape: MetricsScrape) {
        Log.d(TAG, "Writing scraped sample to storage")
        data.add(metricsScrape)
    }

    override fun isEmpty(): Boolean {
        return data.isEmpty()
    }

    override fun getLength(): Int {
        return data.count()
    }
}

@Entity
data class RoomLabel {
    @PrimaryKey
}

@Entity
data class RoomTimeSeries {

}

interface RoomDao {
    @Query
    fun insertOneTimeSeriesSample(){

    }

}

class RemoteWriteSenderDatabaseStorage : RemoteWriteSenderStorage() {
    override fun getScrapedSamplesCompressedProtobuf(howMany: Int): ByteArray {
        TODO("Not yet implemented")
    }

    override fun removeNumberOfScrapedSamples(number: Int) {
        TODO("Not yet implemented")
    }

    override fun writeScrapedSample(metricsScrape: MetricsScrape) {
        TODO("Not yet implemented")
    }

    override fun isEmpty(): Boolean {
        TODO("Not yet implemented")
    }

    override fun getLength(): Int {
        TODO("Not yet implemented")
    }
}
