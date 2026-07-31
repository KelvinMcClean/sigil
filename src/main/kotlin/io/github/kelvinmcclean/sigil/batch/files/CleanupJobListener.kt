package io.github.kelvinmcclean.sigil.batch.files

import io.github.kelvinmcclean.sigil.batch.VideoJobContext
import io.github.kelvinmcclean.sigil.files.FileService
import org.springframework.batch.core.job.JobExecution
import org.springframework.batch.core.listener.JobExecutionListener
import org.springframework.stereotype.Component

@Component
class CleanupJobListener(private val fileService: FileService, private val videoJobContext: VideoJobContext) : JobExecutionListener {

    override fun afterJob(jobExecution: JobExecution) {
        super.afterJob(jobExecution)
        fileService.cleanupJob(videoJobContext.fileDirectory)
    }

}