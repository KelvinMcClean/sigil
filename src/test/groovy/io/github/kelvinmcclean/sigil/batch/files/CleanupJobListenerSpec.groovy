package io.github.kelvinmcclean.sigil.batch.files

import io.github.kelvinmcclean.sigil.batch.VideoJobContext
import io.github.kelvinmcclean.sigil.files.FileService
import org.springframework.batch.core.job.JobExecution
import spock.lang.Specification
import spock.lang.Subject

class CleanupJobListenerSpec extends Specification {

    FileService fileService = Mock()
    VideoJobContext videoJobContext = Mock()

    @Subject
    CleanupJobListener listener = new CleanupJobListener(fileService, videoJobContext)

    def "afterJob cleans up the working directory of the job"() {
        given:
        def jobExecution = Stub(JobExecution)

        when:
        listener.afterJob(jobExecution)

        then:
        1 * videoJobContext.getFileDirectory() >> '/tmp/sigil/job-123/'
        1 * fileService.cleanupJob('/tmp/sigil/job-123/')
    }
}
