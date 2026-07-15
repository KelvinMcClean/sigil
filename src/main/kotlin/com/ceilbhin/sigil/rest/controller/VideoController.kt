package com.ceilbhin.sigil.rest.controller

import com.ceilbhin.sigil.files.FileService
import com.ceilbhin.sigil.media.VideoService
import com.ceilbhin.sigil.rest.status.JobStatus
import com.ceilbhin.sigil.rest.status.StatusTracker
import lombok.Getter
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.JobExecution
import org.springframework.batch.core.job.parameters.JobParametersBuilder
import org.springframework.batch.core.launch.JobOperator
import org.springframework.batch.core.repository.JobRepository
import org.springframework.http.ResponseEntity
import org.springframework.http.ResponseEntity.accepted
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.*


@RestController
@RequestMapping("/api/video")
class VideoController(
    private val fileService: FileService,
    private val jobOperator: JobOperator,
    private val videoProcessingJob: Job,
    private val jobRepository: JobRepository) {

    @PostMapping("/process", consumes = ["multipart/form-data"])
    fun process(
        @RequestParam("files") files: Array<MultipartFile>,
        @RequestParam(value = "timestamps", required = false) timestamps: Array<Long>?,
        @RequestParam(value = "stabilize", defaultValue = "false") stabilize: Boolean): ResponseEntity<MutableMap<String, Long>> {

        // Generate a unique Job ID
        val jobId = UUID.randomUUID().toString()
        val tmpDir = fileService.saveFilesToTemp(jobId, files)

        // 2. Convert timestamps array to comma-separated string for Batch Parameters
        val timestampsStr: String = timestamps?.joinToString(",") ?: ""


        // 3. Build Batch Parameters
        val params = JobParametersBuilder()
            .addLong("fileCount", files.size.toLong())
            .addString("timestamps", timestampsStr)
            .addString("fileDirecotry", tmpDir)
            .addString("stabilize", java.lang.String.valueOf(stabilize))
            .addLong("launchTime", System.currentTimeMillis()) // Ensures job uniqueness
            .toJobParameters()


        // 4. Launch the job asynchronously
        // (Ensure your JobLauncher is configured for async execution in Boot 3)
        val job = jobOperator.start(videoProcessingJob, params)

        return accepted().body(
            Collections.singletonMap(
                "jobId",
                job.id
            )
        )
    }

    @GetMapping(value = ["/status/{jobId}"], produces = ["application/json"])
    fun getJobStatus(@PathVariable jobId: Long): ResponseEntity<Any> {


        // Note: You might need to query your DB for the actual JobExecutionId
        // mapping to your UUID jobId, or use the execution ID returned by jobLauncher.run()
        val jobExecution: JobExecution? = jobRepository.getJobExecution(jobId)

        if (jobExecution == null) return ResponseEntity.notFound().build()

        val response: MutableMap<String, Any> = HashMap<String, Any>()
        response["status"] = jobExecution.status.toString()


        // Dig into the specific steps to get files processed!
        for (stepExecution in jobExecution.stepExecutions) {
            if (stepExecution.stepName == "processFilesStep") {
                response["filesProcessed"] = stepExecution.getWriteCount()
                break
            }
        }

        return ResponseEntity.ok(response)
    }
}