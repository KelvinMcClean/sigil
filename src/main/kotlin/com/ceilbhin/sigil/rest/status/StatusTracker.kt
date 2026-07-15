package com.ceilbhin.sigil.rest.status

import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class StatusTracker {

    private final val jobTracker: ConcurrentHashMap<String, JobStatus> = ConcurrentHashMap()

    fun getJobStatus(jobId: String?): JobStatus? {
        return jobTracker[jobId]
    }

    fun setJobStatus(jobId: String, status: StatusEnum, message: String? = null, error: Throwable? = null) {
        jobTracker[jobId] = JobStatus(jobId, status, message, error)
    }
}