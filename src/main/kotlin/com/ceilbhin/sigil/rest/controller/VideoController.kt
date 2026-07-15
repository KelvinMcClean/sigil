package com.ceilbhin.sigil.rest.controller

import com.ceilbhin.sigil.files.FileService
import com.ceilbhin.sigil.media.VideoService
import com.ceilbhin.sigil.rest.status.JobStatus
import com.ceilbhin.sigil.rest.status.StatusEnum
import com.ceilbhin.sigil.rest.status.StatusTracker
import lombok.Getter
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.parameters.JobParametersBuilder
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.batch.core.launch.JobOperator
import org.springframework.http.ResponseEntity
import org.springframework.http.ResponseEntity.*
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.util.*


@RestController
@RequestMapping("/api/video")
class VideoController(
    @Getter private final val videoService: VideoService,
    private val statusTracker: StatusTracker,
    private val fileService: FileService) {
    private val jobOperator: JobOperator? = null
    private val videoProcessingJob: Job? = null
    @PostMapping("/process", consumes = ["multipart/form-data"])
    fun process(
        @RequestParam("files") files: Array<MultipartFile>,
        @RequestParam(value = "timestamps", required = false) timestamps: Array<Long>?,
        @RequestParam(value = "stabilize", defaultValue = "false") stabilize: Boolean): ResponseEntity<Map<String, String>> {

        // Generate a unique Job ID
        val jobId = UUID.randomUUID().toString()
        fileService.saveFilesToTemp(jobId, files)

        // 2. Convert timestamps array to comma-separated string for Batch Parameters
        val timestampsStr: String = timestamps?.joinToString(",") ?: ""


        // 3. Build Batch Parameters
        val params = JobParametersBuilder()
            .addString("jobId", jobId)
            .addLong("fileCount", files.size.toLong())
            .addString("timestamps", timestampsStr)
            .addString("stabilize", java.lang.String.valueOf(stabilize))
            .addLong("launchTime", System.currentTimeMillis()) // Ensures job uniqueness
            .toJobParameters()


        // 4. Launch the job asynchronously
        // (Ensure your JobLauncher is configured for async execution in Boot 3)
        jobOperator.r(videoProcessingJob, params)

        return accepted().body<MutableMap<String?, String?>?>(
            Collections.singletonMap<String?, String?>(
                "jobId",
                jobId
            )
        )
        videoService.processVideoJobAsync(jobId, files, timestamps, stabilize)

        return accepted().body(mapOf("jobId" to jobId))
    }

    @GetMapping(value = ["/status/{jobId}"], produces = ["application/json"])
    fun getJobStatus(@PathVariable jobId: String): ResponseEntity<JobStatus> {
        val status: JobStatus =
            statusTracker.getJobStatus(jobId) ?: return status(404).body(
                JobStatus("UNKNOWN", StatusEnum.UNKNOWN, "Job not found")
            )

        // Handle the case where the frontend asks for a job that doesn't exist

        // Return the JSON object: {"status": "RUNNING"}
        return ok(status)
    }
}