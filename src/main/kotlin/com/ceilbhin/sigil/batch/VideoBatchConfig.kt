package com.ceilbhin.sigil.batch

import com.ceilbhin.sigil.media.VideoService
import org.springframework.beans.factory.annotation.Value
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.Step
import org.springframework.batch.core.step.StepContribution
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.infrastructure.item.Chunk
import org.springframework.batch.infrastructure.item.ItemProcessor
import org.springframework.batch.infrastructure.item.ItemWriter
import org.springframework.batch.infrastructure.item.support.ListItemReader
import org.springframework.batch.infrastructure.repeat.RepeatStatus
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager
import java.nio.file.Paths


@Configuration
class VideoBatchConfig {
    // --- 1. THE JOB DEFINITION ---
    @Bean
    fun videoProcessingJob(jobRepository: JobRepository, processFilesStep: Step, concatStep: Step): Job {
        return JobBuilder("videoProcessingJob", jobRepository)
            .start(processFilesStep) // Run FFmpeg on all chunks
            .next(concatStep) // Then concatenate
            .build()
    }

    // --- 2. STEP 1: CHUNK-ORIENTED PROCESSING ---
    @Bean
    fun processFilesStep(
        jobRepository: JobRepository, transactionManager: PlatformTransactionManager,
        indexReader: ListItemReader<Int>,
        ffmpegProcessor: ItemProcessor<Int, Int>,
        noOpWriter: ItemWriter<Int>
    ): Step {
        return StepBuilder("processFilesStep", jobRepository)
            .chunk<Int, Int>(1) // Process 1 file at a time
            .transactionManager(transactionManager)
            .reader(indexReader)
            .processor(ffmpegProcessor)
            .writer(noOpWriter)
            .build()
    }

    // Reader: Generates a list of indices [0, 1, 2...] based on file count
    @Bean
    @StepScope
    fun indexReader(@Value("#{jobParameters['fileCount']}") fileCount: Long): ListItemReader<Int> {
        val indices: MutableList<Int> = ArrayList()
        for (i in 0..<fileCount) indices.add(i.toInt())
        return ListItemReader(indices)
    }

    // Processor: Where your Jave2 FFmpeg logic lives
    @Bean
    @StepScope
    fun ffmpegProcessor(
        jobContext: VideoJobContext,
        videoService: VideoService
    ): ItemProcessor<Int, Int> {
        return ItemProcessor { index: Int ->
            val workingDir = Paths.get(System.getProperty("java.io.tmpdir")).resolve("video-app")
            val inputName = jobContext.jobId + "_input_" + index + ".mp4"
            val outputName = jobContext.jobId + "_processed_" + index + ".mp4"


            // Parse your params
            val stabilize = jobContext.stabilize
            val timestamp = jobContext.timestamps[index]

            videoService.format()
        }
    }

    // Writer: Spring Batch requires a writer for chunks, even if we just save to disk natively
    @Bean
    fun noOpWriter(): ItemWriter<Int> {
        return ItemWriter { chunk: Chunk<out Int>? -> }
    }

    // --- 3. STEP 2: CONCATENATION TASKLET ---
    @Bean
    fun concatStep(
        jobRepository: JobRepository,
        transactionManager: PlatformTransactionManager,
        concatTasklet: Tasklet
    ): Step {
        return StepBuilder("concatStep", jobRepository)
            .tasklet(concatTasklet, transactionManager)
            .build()
    }

    @Bean
    @StepScope
    fun concatTasklet(
        @Value("#{jobParameters['jobId']}") jobId: String,
        @Value("#{jobParameters['fileCount']}") fileCount: Long
    ): Tasklet {
        return Tasklet { contribution: StepContribution?, chunkContext: ChunkContext? ->
            val workingDir = Paths.get(System.getProperty("java.io.tmpdir")).resolve("video-app")
            RepeatStatus.FINISHED
        }
    }
}