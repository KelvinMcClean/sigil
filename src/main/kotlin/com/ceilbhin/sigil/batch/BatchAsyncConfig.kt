package com.ceilbhin.sigil.batch

import org.springframework.batch.core.configuration.JobRegistry
import org.springframework.batch.core.configuration.support.MapJobRegistry
import org.springframework.batch.core.launch.JobOperator
import org.springframework.batch.core.launch.support.TaskExecutorJobOperator
import org.springframework.batch.core.repository.JobRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.TaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

@Configuration
class BatchAsyncConfig {

    // 1. Thread Pool - Use a real ThreadPool, not SimpleAsyncTaskExecutor
    @Bean
    fun batchTaskExecutor(): TaskExecutor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 5
        executor.maxPoolSize = 10
        executor.queueCapacity = 25
        executor.setThreadNamePrefix("batch-")
        executor.initialize()
        return executor
    }

    // 2. Registry
    @Bean
    fun jobRegistry(): JobRegistry = MapJobRegistry()

    // 3. The Operator (The only bean you need to manually configure)
    @Bean
    fun batchJobOperator(
        jobRepository: JobRepository,
        jobRegistry: JobRegistry,
        batchTaskExecutor: TaskExecutor
    ): JobOperator {
        val operator = TaskExecutorJobOperator()
        operator.setJobRepository(jobRepository) // Injected by Spring Boot
        operator.setJobRegistry(jobRegistry)
        operator.setTaskExecutor(batchTaskExecutor)
        operator.afterPropertiesSet()
        return operator
    }
}
