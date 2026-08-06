package io.github.kelvinmcclean.sigil.timestamp

import io.github.kelvinmcclean.sigil.batch.VideoJobContext
import io.github.kelvinmcclean.sigil.ffmpeg.FfmpegUtils
import io.github.kelvinmcclean.sigil.files.FileService
import io.github.kelvinmcclean.sigil.media.MediaConfiguration
import io.github.kelvinmcclean.sigil.timestamp.font.FontConfiguration
import io.github.kelvinmcclean.sigil.timestamp.font.FontResolver
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.io.File
import java.util.TimeZone
import kotlin.time.Instant
import kotlin.time.toJavaInstant

@Service
class TimestampService(
    val fontConfiguration: FontConfiguration,
    var videoJobContext: VideoJobContext,
    var mediaConfiguration: MediaConfiguration,
    private val ffmpegUtils: FfmpegUtils
) {
    private final val logger = KotlinLogging.logger {}

    fun getTimestampFilter(videoJobContext: VideoJobContext, index: Int): String {
        val workingDir = File(videoJobContext.fileDirectory)
        val userFont= fontConfiguration.path
        val fontOption: String = FontResolver().resolveFont(workingDir.toPath(), userFont)
        return processTimestamps(videoJobContext.timestamps[index], fontOption)
    }

    fun getTimestampFilter(videoJobContext: VideoJobContext, timestamp: Long): String {
        val workingDir = File(videoJobContext.fileDirectory)
        val userFont= fontConfiguration.path
        val fontOption: String = FontResolver().resolveFont(workingDir.toPath(), userFont)
        return processTimestamps(timestamp, fontOption)
    }

    fun processTimestamps(timestamp: Long, fontOption: String?): String {
        val size = fontConfiguration.size
        val location = fontConfiguration.location
        val format = fontConfiguration.format
        return StringBuilder().append(",drawtext=fontfile=$fontOption: text='%{pts\\:localtime\\:")
            .append(timestamp)
            .append(format)
            .append("': x=${location.x}: y=${location.y}: fontcolor=white: fontsize=${size}: box=1: boxcolor=black@0.5").toString()
    }

    fun getLatestTimestamp(): Long {
        return videoJobContext.timestamps.maxByOrNull { it.toInt() }!!
    }
    fun getEarliestTimestamp(): Long {
        return videoJobContext.timestamps.minByOrNull { it.toInt() }!!
    }

    fun resolveTextTimestamp(): String {
        val latestTimestamp = getLatestTimestamp()
        val earliestTimestamp = getEarliestTimestamp()
        val dateConfiguration = mediaConfiguration.date
        val pattern1 = dateConfiguration.getYearFormatted(earliestTimestamp)
        val pattern2 = getSecondPattern(earliestTimestamp, latestTimestamp, dateConfiguration)
        logger.info { "Parsed timestamp: $pattern1$pattern2" }
        return pattern1 + pattern2
    }

    private fun getSecondPattern(
        earliestTimestamp: Long,
        latestTimestamp: Long,
        dateConfiguration: MediaConfiguration.Date
    ): String {
        // if year differs, get both in full
        val earlyDate =
            Instant.fromEpochSeconds(earliestTimestamp).toJavaInstant().atZone(TimeZone.getDefault().toZoneId()).toLocalDate()
        val latestDate =
            Instant.fromEpochSeconds(latestTimestamp).toJavaInstant().atZone(TimeZone.getDefault().toZoneId()).toLocalDate()
        val isSameYear = earlyDate.year == latestDate.year
        val isSameMonth = isSameYear && earlyDate.month == latestDate.month
        val isSameDay = earlyDate == latestDate // LocalDate equality checks year, month, and day automatically
        var res = ""
        when {
            (dateConfiguration.splitDay && (!isSameDay && isSameMonth)) -> {
                res = "${dateConfiguration.splitter}${dateConfiguration.getDayFormatted(latestTimestamp)}"
            }

            (dateConfiguration.splitMonth && (!isSameMonth && isSameYear)) -> {
                res = "${dateConfiguration.splitter}${dateConfiguration.getMonthFormatted(latestTimestamp)}"
            }

            !isSameDay -> {
                res = "${dateConfiguration.splitter}${dateConfiguration.getYearFormatted(latestTimestamp)}"
            }

        }

        return res
    }

    fun buildTimestamps(videoJobContext: VideoJobContext, index: Int) {
        if (videoJobContext.timestamps.size <= index) {
            val inputFilePath = "_input_$index.mp4"
            val workingDir = File(videoJobContext.fileDirectory)
            val timestamp = ffmpegUtils.getTimestamp(inputFilePath, workingDir)
            videoJobContext.timestamps += timestamp
        }
    }
}
