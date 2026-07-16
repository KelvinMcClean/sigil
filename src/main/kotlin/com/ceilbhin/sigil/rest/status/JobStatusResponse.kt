package com.ceilbhin.sigil.rest.status

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import lombok.AllArgsConstructor
import lombok.Builder
import lombok.Data
import lombok.NoArgsConstructor
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.job.JobExecution
import java.time.Instant
import java.time.ZoneOffset

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
class JobStatusResponse {
    private var id: Long? = null
    private var name: String? = null
    private var status: BatchStatus? = null

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private var exitStatus: ExitStatus? = null
    private var startTime: Instant? = null
    private var endTime: Instant? = null
    private var createTime: Instant? = null
    private var updateTime: Instant? = null

    @JsonIgnore
    constructor(jobExecution: JobExecution) {
        this.id = jobExecution.id
        this.name = jobExecution.jobInstance.jobName
        this.status = jobExecution.status
        this.exitStatus = jobExecution.exitStatus
        this.startTime = jobExecution.startTime?.toInstant(ZoneOffset.UTC)
        this.endTime = jobExecution.endTime?.toInstant(ZoneOffset.UTC)
        this.createTime = jobExecution.createTime.toInstant(ZoneOffset.UTC)
        this.updateTime = jobExecution.lastUpdated?.toInstant(ZoneOffset.UTC) ?: this.createTime
    }
}