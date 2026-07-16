package com.ceilbhin.sigil.batch

import com.ceilbhin.sigil.media.MediaConfiguration
import org.springframework.batch.core.configuration.annotation.JobScope
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class VideoJobContextConfig {

    @Bean
    @JobScope
    fun videoJobContext(
        @Value("#{jobParameters}") jobParameters: Map<Any, Any>,
        mediaConfiguration: MediaConfiguration
    ): VideoJobContext {

        // Parse the raw strings into usable, strictly-typed Kotlin objects once
        return VideoJobContextImpl(
            fileCount = (jobParameters["fileCount"] as Long),
            fileDirectory = jobParameters["fileDirectory"] as String,
            timestamps = (jobParameters["timestamps"] as String).split(",").map { it.toLong() },
            title = jobParameters["title"].toString().ifEmpty { mediaConfiguration.defaultTitle },
            stabilize = (jobParameters["stabilize"] as String).toBoolean()
        )
    }
}