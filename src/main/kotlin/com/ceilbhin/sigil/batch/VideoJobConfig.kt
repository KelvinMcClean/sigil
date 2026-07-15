package com.ceilbhin.sigil.batch

import org.springframework.batch.core.configuration.annotation.JobScope
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class VideoJobConfig {

    @Bean
    @JobScope
    fun videoJobContext(
        @Value("#{jobParameters['jobId']}") jobId: String,
        @Value("#{jobParameters['fileCount']}") fileCount: Long,
        @Value("#{jobParameters['timestamps']}") timestampsStr: String,
        @Value("#{jobParameters['fileDirectory']}") fileDirectory: String,
        @Value("#{jobParameters['stabilize']}") stabilizeStr: String
    ): VideoJobContext {

        // Parse the raw strings into usable, strictly-typed Kotlin objects once
        return VideoJobContext(
            jobId = jobId,
            fileCount = fileCount,
            fileDirectory = fileDirectory,
            timestamps = timestampsStr.split(",").map { it.toLong() },
            stabilize = stabilizeStr.toBoolean()
        )
    }
}