package com.ceilbhin.sigil.rest.status

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import lombok.AllArgsConstructor
import lombok.Builder
import lombok.Data
import lombok.NoArgsConstructor
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.job.JobExecution
import java.io.Serializable
import java.time.Instant
import java.time.ZoneOffset

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
data class JobStatusResponse(
    var id: Long,
    var name: String?,
    var status: BatchStatus?,

    var exitStatus: ExitStatus?,
    var startTime: Instant?,
    var endTime: Instant?,
    var createTime: Instant?,
    var updateTime: Instant?
) {
    var completedItems: Int? = null
    var totalItems: Int? = null

    @JsonIgnore
    constructor(jobExecution: JobExecution) : this(
        id = jobExecution.id,
        name = jobExecution.jobInstance.jobName,
        status = jobExecution.status,
        exitStatus = jobExecution.exitStatus,
        startTime = jobExecution.startTime?.toInstant(ZoneOffset.UTC),
        endTime = jobExecution.endTime?.toInstant(ZoneOffset.UTC),
        createTime = jobExecution.createTime.toInstant(ZoneOffset.UTC),
        updateTime = jobExecution.lastUpdated?.toInstant(ZoneOffset.UTC) ?: jobExecution.createTime.toInstant(ZoneOffset.UTC)
    )

    fun populateFromJob(jobExecution: JobExecution) {
        totalItems = (jobExecution.jobParameters.parameters.find { it.name == "fileCount" }
            ?.value?.toString()?.toInt())?.plus(1)
        completedItems = if (jobExecution.stepExecutions.find { it.stepName == "concatStep" }?.status == BatchStatus.COMPLETED) {
            totalItems
        } else {
            jobExecution.stepExecutions.find { it.stepName == "processFilesStep" }?.writeCount?.toInt()
        }
    }
}