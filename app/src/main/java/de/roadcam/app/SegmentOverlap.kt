package de.roadcam.app

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/**
 * Builds a new segment that starts with the last seconds of the previous segment. This
 * creates a small overlap so the transition between two clips does not lose any content.
 * The re-mux is best effort: if the device rejects the operation the original files stay
 * untouched.
 */
object SegmentOverlap {

    private data class TrackInfo(val index: Int, val mime: String, val format: MediaFormat)
    private data class Sample(val data: ByteArray, val presentationTimeUs: Long, val flags: Int)

    fun prependTail(previous: File, current: File, seconds: Int): Boolean {
        if (seconds <= 0 || !previous.isFile || !current.isFile) return false
        val temp = File(current.parentFile, "${current.nameWithoutExtension}.overlap.tmp")
        var previousExtractor: MediaExtractor? = null
        var currentExtractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        return try {
            previousExtractor = MediaExtractor().apply { setDataSource(previous.absolutePath) }
            currentExtractor = MediaExtractor().apply { setDataSource(current.absolutePath) }

            val currentTracks = tracksOf(currentExtractor)
                .filter { it.mime.startsWith("video/") || it.mime.startsWith("audio/") }
            val previousTracks = tracksOf(previousExtractor)
            val previousVideo = previousTracks.firstOrNull { it.mime.startsWith("video/") }
                ?: return false

            val tailStartUs = (videoEndUs(previousExtractor, previousVideo) - seconds * 1_000_000L)
                .coerceAtLeast(0L)

            val videoSamples = collect(previousExtractor, previousVideo.index, seekToUs = tailStartUs)
            if (videoSamples.isEmpty()) return false
            val videoBaseUs = videoSamples.first().presentationTimeUs

            val prefixByMime = mutableMapOf<String, List<Sample>>()
            prefixByMime[previousVideo.mime] = videoSamples.map {
                it.copy(presentationTimeUs = it.presentationTimeUs - videoBaseUs)
            }
            previousTracks
                .filter { it.mime.startsWith("audio/") }
                .forEach { audio ->
                    val samples = collect(
                        previousExtractor,
                        audio.index,
                        seekToUs = videoBaseUs,
                        minTimeUs = videoBaseUs
                    )
                    prefixByMime[audio.mime] = samples.map {
                        it.copy(presentationTimeUs = it.presentationTimeUs - videoBaseUs)
                    }
                }

            val prefixEndUs = prefixByMime.values.flatten()
                .maxOfOrNull { it.presentationTimeUs }
                ?: return false

            muxer = MediaMuxer(temp.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerTrackByMime = mutableMapOf<String, Int>()
            currentTracks.forEach { track ->
                if (track.mime !in muxerTrackByMime) {
                    muxerTrackByMime[track.mime] = muxer.addTrack(track.format)
                }
            }
            muxer.start()

            val buffer = ByteBuffer.allocateDirect(1 shl 20)
            prefixByMime.forEach { (mime, samples) ->
                val muxerTrack = muxerTrackByMime[mime] ?: return@forEach
                samples.forEach { sample ->
                    writeSample(muxer, muxerTrack, sample, buffer)
                }
            }
            currentTracks.forEach { track ->
                val muxerTrack = muxerTrackByMime[track.mime] ?: return@forEach
                currentExtractor.selectTrack(track.index)
                currentExtractor.seekTo(0L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                while (true) {
                    val info = readSample(currentExtractor, buffer) ?: break
                    val shifted = info.copy(presentationTimeUs = info.presentationTimeUs + prefixEndUs)
                    writeSample(muxer, muxerTrack, shifted, buffer)
                    currentExtractor.advance()
                }
            }

            muxer.stop()
            muxer.release()
            muxer = null

            val backup = File(current.parentFile, "${current.name}.bak")
            if (!current.renameTo(backup)) {
                temp.delete()
                return false
            }
            if (!temp.renameTo(current)) {
                backup.renameTo(current)
                temp.delete()
                return false
            }
            backup.delete()
            true
        } catch (_: Exception) {
            temp.delete()
            false
        } finally {
            runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            runCatching { previousExtractor?.release() }
            runCatching { currentExtractor?.release() }
        }
    }

    private fun tracksOf(extractor: MediaExtractor): List<TrackInfo> =
        (0 until extractor.trackCount).mapNotNull { index ->
            val format = extractor.getTrackFormat(index)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return@mapNotNull null
            TrackInfo(index, mime, format)
        }

    private fun videoEndUs(extractor: MediaExtractor, track: TrackInfo): Long {
        val duration = if (track.format.containsKey(MediaFormat.KEY_DURATION)) {
            track.format.getLong(MediaFormat.KEY_DURATION)
        } else {
            -1L
        }
        if (duration > 0L) return duration
        extractor.selectTrack(track.index)
        var last = 0L
        while (true) {
            if (extractor.sampleTime < 0L) break
            last = extractor.sampleTime
            if (!extractor.advance()) break
        }
        return last
    }

    private fun collect(
        extractor: MediaExtractor,
        trackIndex: Int,
        seekToUs: Long,
        minTimeUs: Long? = null
    ): List<Sample> {
        extractor.selectTrack(trackIndex)
        extractor.seekTo(seekToUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        val buffer = ByteBuffer.allocateDirect(1 shl 20)
        val samples = mutableListOf<Sample>()
        while (true) {
            val sample = readSample(extractor, buffer) ?: break
            if (minTimeUs == null || sample.presentationTimeUs >= minTimeUs) {
                samples += sample
            }
            if (!extractor.advance()) break
        }
        return samples
    }

    private fun readSample(extractor: MediaExtractor, buffer: ByteBuffer): Sample? {
        val time = extractor.sampleTime
        if (time < 0L) return null
        val size = extractor.readSampleData(buffer, 0)
        if (size < 0) return null
        val data = ByteArray(size)
        buffer.position(0)
        buffer.limit(size)
        buffer.get(data)
        buffer.clear()
        return Sample(data, time, extractor.sampleFlags)
    }

    private fun writeSample(
        muxer: MediaMuxer,
        trackIndex: Int,
        sample: Sample,
        buffer: ByteBuffer
    ) {
        val target = if (sample.data.size > buffer.capacity()) {
            ByteBuffer.allocateDirect(sample.data.size)
        } else {
            buffer
        }
        target.clear()
        target.put(sample.data)
        target.position(0)
        target.limit(sample.data.size)
        val info = android.media.MediaCodec.BufferInfo().apply {
            set(0, sample.data.size, sample.presentationTimeUs, sample.flags)
        }
        muxer.writeSampleData(trackIndex, target, info)
    }
}
