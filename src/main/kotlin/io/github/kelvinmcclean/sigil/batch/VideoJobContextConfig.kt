package io.github.kelvinmcclean.sigil.batch

import io.github.kelvinmcclean.sigil.media.MediaConfiguration
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

        return VideoJobContextImpl(
            fileCount = (jobParameters["fileCount"] as Long),
            fileDirectory = jobParameters["fileDirectory"] as String,
            timestamps = ((jobParameters["timestamps"] as? String) ?: "").split(",").filter { it.isNotEmpty() }.map { it.toLong() },
            title = jobParameters["title"].toString().ifEmpty { mediaConfiguration.defaultTitle },
            stabilize = (jobParameters["stabilize"] as String).toBoolean()
        )
    }
}