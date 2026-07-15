package com.ceilbhin.sigil.rest.controller

import com.ceilbhin.sigil.rest.status.JobStatus
import com.ceilbhin.sigil.rest.status.StatusEnum
import com.ceilbhin.sigil.rest.status.StatusTracker
import com.ceilbhin.sigil.service.FileService
import com.ceilbhin.sigil.service.VideoService
import lombok.Getter
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

    @PostMapping("/process", consumes = ["multipart/form-data"])
    fun process(
        @RequestParam("files") files: Array<MultipartFile>,
        @RequestParam(value = "timestamps", required = false) timestamps: Array<Long>?,
        @RequestParam(value = "stabilize", defaultValue = "false") stabilize: Boolean): ResponseEntity<Map<String, String>> {

        // Generate a unique Job ID
        val jobId = UUID.randomUUID().toString()
        fileService.saveFilesToTemp(jobId, files)
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