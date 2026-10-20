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
import java.time.Instant as JavaInstant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone

@Service
class TimestampService(
    val fontConfiguration: FontConfiguration,
    var videoJobContext: VideoJobContext,
    var mediaConfiguration: MediaConfiguration,
    var ffmpegUtils: FfmpegUtils
) {
    private final val logger = KotlinLogging.logger {}

    fun getTimestampFilter(videoJobContext: VideoJobContext, index: Int): String {
        val workingDir = File(videoJobContext.fileDirectory)
        val userFont= fontConfiguration.path
        val fontOption: String = FontResolver().resolveFont(workingDir.toPath(), userFont)
        val timestamp = videoJobContext.timestamps[index]
        val isDateOnly = isDateOnlyTimestamp(timestamp)
        return processTimestamps(timestamp, fontOption, isDateOnly)
    }

    fun getTimestampFilter(videoJobContext: VideoJobContext, timestamp: Long): String {
        val workingDir = File(videoJobContext.fileDirectory)
        val userFont= fontConfiguration.path
        val fontOption: String = FontResolver().resolveFont(workingDir.toPath(), userFont)
        val isDateOnly = isDateOnlyTimestamp(timestamp)
        return processTimestamps(timestamp, fontOption, isDateOnly)
    }

    /**
     * Check if timestamp represents midnight UTC (i.e., extracted from filename as date only)
     */
    private fun isDateOnlyTimestamp(timestamp: Long): Boolean {
        // Timestamps from filename extraction are set to midnight UTC
        // which means they are divisible by 86400 (seconds in a day)
        return timestamp % 86400 == 0L
    }

    /**
     * Get a date-only format string by removing time-related format specifiers
     */
    private fun getDateOnlyFormat(format: String): String {
        // Remove time-related format specifiers like %H, %M, %S, %I, etc.
        // Keep only date specifiers: %Y, %m, %d, %B, %b, etc.
        val timeSpecifiers = setOf("%H", "%I", "%M", "%S", "%p", "%r", "%T", "%R", "%X", "%Z", "%z")
        var result = format
        for (specifier in timeSpecifiers) {
            result = result.replace(specifier, "")
        }
        // Remove escaped separators left behind after stripping time fields (for example, \:%H\:%M\:%S).
        result = result.replace(Regex("\\\\+:"), "")
        // Clean up extra spaces and punctuation
        result = result.replace("  ", " ").trim()
        return result
    }

    fun processTimestamps(timestamp: Long, fontOption: String?): String {
        return processTimestamps(timestamp, fontOption, false)
    }

    fun processTimestamps(timestamp: Long, fontOption: String?, isDateOnly: Boolean): String {
        val size = fontConfiguration.size
        val location = fontConfiguration.location
        val format = if (isDateOnly) getDateOnlyFormat(fontConfiguration.format) else fontConfiguration.format
        return StringBuilder().append(",drawtext=fontfile=$fontOption: text='%{pts\\:localtime\\:")
            .append(timestamp)
            .append("\\:")
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
        val earlyDateTime = JavaInstant.ofEpochSecond(earliestTimestamp)
            .atZone(TimeZone.getDefault().toZoneId())
            .toLocalDate()
        val latestDateTime = JavaInstant.ofEpochSecond(latestTimestamp)
            .atZone(TimeZone.getDefault().toZoneId())
            .toLocalDate()
        val isSameYear = earlyDateTime.year == latestDateTime.year
        val isSameMonth = isSameYear && earlyDateTime.month == latestDateTime.month
        val isSameDay = earlyDateTime == latestDateTime // LocalDate equality checks year, month, and day automatically
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
            val originalFilename = if (index < videoJobContext.originalFilenames.size) 
                videoJobContext.originalFilenames[index] else null
            val timestamp = ffmpegUtils.getTimestamp(inputFilePath, workingDir, originalFilename)
            videoJobContext.timestamps += timestamp
        }

        if (index == videoJobContext.fileCount.toInt() - 1) {
            sortInputsByTimestamp(videoJobContext)
        }
    }

    private fun sortInputsByTimestamp(videoJobContext: VideoJobContext) {
        if (videoJobContext.timestamps.size != videoJobContext.fileCount.toInt()) return

        val workingDir = File(videoJobContext.fileDirectory)
        val sortedIndices = videoJobContext.timestamps.indices.sortedBy { videoJobContext.timestamps[it] }
        val temporaryFiles = sortedIndices.mapIndexed { sortedIndex, originalIndex ->
            val source = File(workingDir, "_input_$originalIndex.mp4")
            val temporary = File(workingDir, "_sorted_input_$sortedIndex.mp4")
            if (!source.renameTo(temporary)) {
                throw IllegalStateException("Unable to stage ${source.name} for timestamp sorting")
            }
            temporary
        }
        temporaryFiles.forEachIndexed { sortedIndex, temporary ->
            if (!temporary.renameTo(File(workingDir, "_input_$sortedIndex.mp4"))) {
                throw IllegalStateException("Unable to apply timestamp order to ${temporary.name}")
            }
        }
        videoJobContext.timestamps = sortedIndices.map { videoJobContext.timestamps[it] }
    }
}
