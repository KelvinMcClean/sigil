package com.ceilbhin.sigil.rest.controller

import com.ceilbhin.sigil.files.FileService
import com.ceilbhin.sigil.rest.status.JobStatusResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.JobExecution
import org.springframework.batch.core.job.parameters.JobParametersBuilder
import org.springframework.batch.core.launch.JobOperator
import org.springframework.batch.core.repository.JobRepository
import org.springframework.http.ResponseEntity
import org.springframework.http.ResponseEntity.accepted
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.util.*


@RestController
@RequestMapping("/api/video")
class VideoController(
    private val fileService: FileService,
    private val batchJobOperator: JobOperator,
    private val videoProcessingJob: Job,
    private val jobRepository: JobRepository) {

    var logger = KotlinLogging.logger {}

    @PostMapping("/process", consumes = ["multipart/form-data"])
    fun process(
        @RequestParam("files") files: Array<MultipartFile>,
        @RequestParam(value = "timestamps", required = false) timestamps: Array<Long>?,
        @RequestParam(value = "stabilize", defaultValue = "false") stabilize: Boolean): JobStatusResponse {

        // Generate a unique Job ID
        val jobId = UUID.randomUUID().toString()
        val tmpDir = fileService.saveFilesToTemp(jobId, files)

        // 2. Convert timestamps array to comma-separated string for Batch Parameters
        val timestampsStr: String = timestamps?.joinToString(",") ?: ""

        // 3. Build Batch Parameters
        val params = JobParametersBuilder()
            .addLong("fileCount", files.size.toLong())
            .addString("timestamps", timestampsStr)
            .addString("fileDirectory", tmpDir)
            .addString("stabilize", java.lang.String.valueOf(stabilize))
            .addLong("launchTime", System.currentTimeMillis()) // Ensures job uniqueness
            .toJobParameters()
        logger.info{"Launching with params: ${params.parameters()}"}

        val job = batchJobOperator.start(videoProcessingJob, params)
        return JobStatusResponse(job)

    }

    @GetMapping(value = ["/status/{jobId}"], produces = ["application/json"])
    fun getJobStatus(@PathVariable jobId: Long): ResponseEntity<JobStatusResponse> {
        // Note: You might need to query your DB for the actual JobExecutionId
        // mapping to your UUID jobId, or use the execution ID returned by jobLauncher.run()
        val jobExecution: JobExecution = jobRepository.getJobExecution(jobId)
            ?: return ResponseEntity.notFound().build()

        val response: MutableMap<String, Any> = HashMap<String, Any>()
        response["status"] = jobExecution.status.toString()

        return ResponseEntity.ok(JobStatusResponse(jobExecution))
    }
}