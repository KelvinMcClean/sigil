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
    lateinit var filenamePattern: String
    lateinit var defaultTitle: String
}