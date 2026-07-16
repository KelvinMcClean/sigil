package com.ceilbhin.sigil.timestamp

import com.ceilbhin.sigil.batch.VideoJobContext
import com.ceilbhin.sigil.media.MediaConfiguration
import com.ceilbhin.sigil.timestamp.font.FontConfiguration
import com.ceilbhin.sigil.timestamp.font.FontResolver
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.io.File
import java.text.SimpleDateFormat
import kotlin.time.Instant

@Service
class TimestampService(val fontConfiguration: FontConfiguration, var videoJobContext: VideoJobContext, var mediaConfiguration: MediaConfiguration) {
    private final val logger = KotlinLogging.logger {}

    fun getTimestampFilter(videoJobContext: VideoJobContext, index: Int): String {
        val workingDir = File(videoJobContext.fileDirectory)
        val userFont= fontConfiguration.path
        val fontOption: String = FontResolver().resolveFont(workingDir.toPath(), userFont)
        return processTimestamps(videoJobContext.timestamps[index], fontOption)
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
        val timestamp = getLatestTimestamp()
        val earliestTimestamp = getEarliestTimestamp()
        val dateConfiguration = mediaConfiguration.date
        // if year differs, get both in full

        //if month differs, get both months and days

        //if day differs, get both days
        val latestFilenamePattern = SimpleDateFormat(mediaConfiguration.datePattern).format(Instant.fromEpochSeconds(timestamp).toEpochMilliseconds())
        val earliestFilenamePattern = SimpleDateFormat(mediaConfiguration.datePattern).format(Instant.fromEpochSeconds(earliestTimestamp).toEpochMilliseconds())
        var res = earliestFilenamePattern
        if (latestFilenamePattern != earliestFilenamePattern) {
            res += "-${latestFilenamePattern}"
        }
        return res

    }
}