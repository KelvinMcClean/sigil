package com.ceilbhin.sigil.timestamp.font

import lombok.Getter
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@ConfigurationProperties(prefix = "sigil.font")
@EnableConfigurationProperties
@Configuration
@Getter
class FontConfiguration {

    lateinit var path: String
    lateinit var size: String
    lateinit var format: String
    lateinit var location: ScreenLocation
}