package io.github.kelvinmcclean.sigil.batch

import io.github.kelvinmcclean.sigil.batch.files.CleanupJobListener
import io.github.kelvinmcclean.sigil.media.VideoService
import io.github.kelvinmcclean.sigil.timestamp.TimestampService
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
import org.springframework.batch.infrastructure.item.function.FunctionItemProcessor
import org.springframework.batch.infrastructure.item.support.ListItemReader
import org.springframework.batch.infrastructure.repeat.RepeatStatus
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration


@Configuration
class VideoBatchConfig {
    @Bean
    fun videoProcessingJob(jobRepository: JobRepository,
                           buildTimestampStep: Step,
                           processFilesStep: Step,
                           concatStep: Step,
                           listener: CleanupJobListener): Job {
        return JobBuilder("videoProcessingJob", jobRepository)
            .start(buildTimestampStep) // Run FFmpeg on all chunks
            .next(processFilesStep) // Run FFmpeg on all chunks
            .next(concatStep) // Then concatenate
            .listener(listener)
            .build()
    }

    @Bean
    fun processFilesStep(
        jobRepository: JobRepository,
        indexReader: ListItemReader<Int>,
        ffmpegProcessor: ItemProcessor<Int, Int>,
        noOpWriter: ItemWriter<Int>
    ): Step {
        return StepBuilder("processFilesStep", jobRepository)
            .chunk<Int, Int>(1) // Process 1 file at a time
            .reader(indexReader)
            .processor(ffmpegProcessor)
            .writer(noOpWriter)
            .build()
    }
    @Bean
    fun buildTimestampStep(
        jobRepository: JobRepository,
        indexReader: ListItemReader<Int>,
        timestampProcessor: ItemProcessor<Int, Int>,
        noOpWriter: ItemWriter<Int>
    ): Step {
        return StepBuilder("buildTimestampStep", jobRepository)
            .chunk<Int, Int>(1)
            .reader(indexReader)
            .processor(timestampProcessor)
            .writer(noOpWriter)
            .build()
    }

    @Bean
    @StepScope
    fun indexReader(jobConfig: VideoJobContext): ListItemReader<Int> {
        val indices: MutableList<Int> = ArrayList()
        for (i in 0 until jobConfig.fileCount) indices.add(i.toInt())
        return ListItemReader(indices)
    }

    @Bean
    @StepScope
    fun timestampProcessor(
        videoJobContext: VideoJobContext,
        timestampService: TimestampService
    ): FunctionItemProcessor<Int, Int> {
        return FunctionItemProcessor { index: Int ->
            timestampService.buildTimestamps(videoJobContext, index)
            // Return the index to pass to the writer (not used)
            index
        }
    }
    @Bean
    @StepScope
    fun ffmpegProcessor(
        videoJobContext: VideoJobContext,
        videoService: VideoService
    ): FunctionItemProcessor<Int, Int> {
        return FunctionItemProcessor { index: Int ->
            videoService.preprocess(videoJobContext, index)
            // Return the index to pass to the writer (not used)
            index
        }
    }

    @Bean
    fun noOpWriter(): ItemWriter<Int> {
        return ItemWriter { _: Chunk<out Int>? -> }
    }

    @Bean
    fun concatStep(
        jobRepository: JobRepository,
        concatTasklet: Tasklet
    ): Step {
        return StepBuilder("concatStep", jobRepository)
            .tasklet(concatTasklet)
            .build()
    }

    @Bean
    @StepScope
    fun concatTasklet(
        jobConfig: VideoJobContext,
        videoService: VideoService
    ): Tasklet {
        return Tasklet { _: StepContribution?, _: ChunkContext? ->
            videoService.concat(jobConfig)
            RepeatStatus.FINISHED
        }
    }
}