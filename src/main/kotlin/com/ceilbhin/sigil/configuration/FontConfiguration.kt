package com.ceilbhin.sigil.configuration

import lombok.Getter
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@ConfigurationProperties(prefix = "font")
@EnableConfigurationProperties
@Configuration
@Getter
class FontConfiguration {
    lateinit var defaultFont: String
}