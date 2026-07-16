package com.ceilbhin.sigil.media

import lombok.Getter
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@ConfigurationProperties(prefix = "sigil.media")
@EnableConfigurationProperties
@Configuration
@Getter
class MediaConfiguration {
    lateinit var baseDir: String
    lateinit var subDirPattern: String
    lateinit var datePattern: String
    lateinit var filenamePattern: String
    lateinit var defaultTitle: String
    lateinit var date: Date

    data class Date(
        var pattern: Pattern,
        var separator: String,
        val splitDay: Boolean,
        var splitMonth: Boolean
    )

    data class Pattern (
        var format: String,
        var year: String,
        var month: String,
        var day: String
    )
}