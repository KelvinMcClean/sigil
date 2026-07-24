package com.ceilbhin.sigil.media

import lombok.Getter
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import java.text.SimpleDateFormat
import kotlin.time.Instant

@ConfigurationProperties(prefix = "sigil.media")
@EnableConfigurationProperties
@Configuration
@Getter
class MediaConfiguration {
    lateinit var baseDir: String
    lateinit var subDirPattern: String
    lateinit var filenamePattern: String
    lateinit var defaultTitle: String
    lateinit var date: Date

    class Date(
        var pattern: Pattern,
        var separator: String,
        var splitter: String,
        val splitDay: Boolean,
        var splitMonth: Boolean
    ) {
        fun getYear(): String {
            return "${pattern.year}${separator}${pattern.month}${separator}${pattern.day}"
        }

        fun getMonth(): String {
            return "${pattern.month}${separator}${pattern.day}"
        }

        fun getDay(): String {
            return pattern.day
        }

        fun getYearFormatted(epochSeconds: Long): String {
            return SimpleDateFormat(this.getYear())
                .format(Instant.fromEpochSeconds(epochSeconds).toEpochMilliseconds())
        }

        fun getMonthFormatted(epochSeconds: Long): String {
            return SimpleDateFormat(this.getMonth())
                .format(Instant.fromEpochSeconds(epochSeconds).toEpochMilliseconds())
        }

        fun getDayFormatted(epochSeconds: Long): String {
            return SimpleDateFormat(this.getDay())
                .format(Instant.fromEpochSeconds(epochSeconds).toEpochMilliseconds())
        }

    }

    class Pattern (
        var year: String,
        var month: String,
        var day: String
    )
}