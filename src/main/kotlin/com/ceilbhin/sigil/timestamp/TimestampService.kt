package com.ceilbhin.sigil.timestamp

import com.ceilbhin.sigil.timestamp.font.FontConfiguration
import com.ceilbhin.sigil.timestamp.font.FontResolver
import com.ceilbhin.sigil.util.FileUtils.Companion.getWorkingDir
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

@Service
class TimestampService(val fontConfiguration: FontConfiguration) {
    private final val logger = KotlinLogging.logger {}

    fun getTimestampFilter(jobId: String, timestamp: Long): String {
        val workingDir = getWorkingDir(jobId)
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
}