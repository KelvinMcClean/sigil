package io.github.kelvinmcclean.sigil.rest.controller

import io.github.kelvinmcclean.sigil.files.FileService
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.JobExecution
import org.springframework.batch.core.job.JobInstance
import org.springframework.batch.core.job.parameters.JobParameters
import org.springframework.batch.core.job.parameters.JobParametersBuilder
import org.springframework.batch.core.launch.JobOperator
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.StepExecution
import org.springframework.http.HttpStatus
import org.springframework.web.multipart.MultipartFile
import spock.lang.Specification
import spock.lang.Subject

import java.time.LocalDateTime

class VideoControllerSpec extends Specification {

    FileService fileService = Mock()
    JobOperator batchJobOperator = Mock()
    Job videoProcessingJob = Mock()
    JobRepository jobRepository = Mock()

    @Subject
    VideoController controller = new VideoController(fileService, batchJobOperator, videoProcessingJob, jobRepository)

    def "process saves the uploads and launches the batch job"() {
        given:
        def files = [Mock(MultipartFile), Mock(MultipartFile)] as MultipartFile[]
        JobParameters launched = null

        when:
        def response = controller.process(files, [1718452800L, 1718625600L] as Long[], 'holiday', true)

        then: 'the uploads are staged under a generated job id'
        1 * fileService.saveFilesToTemp({ String it -> UUID.fromString(it) }, files) >> '/tmp/sigil/job-123/'

        and: 'the job is started with the parsed parameters'
        1 * batchJobOperator.start(videoProcessingJob, _ as JobParameters) >> { Job job, JobParameters params ->
            launched = params
            stubbedExecution()
        }

        and:
        response.statusCode == HttpStatus.ACCEPTED
        response.body.id == 42L

        and:
        launched.getLong('fileCount') == 2L
        launched.getString('timestamps') == '1718452800,1718625600'
        launched.getString('title') == 'holiday'
        launched.getString('fileDirectory') == '/tmp/sigil/job-123/'
        launched.getString('stabilize') == 'true'
        launched.getLong('launchTime') != null
    }

    def "process defaults the optional request parameters"() {
        given:
        def files = [Mock(MultipartFile)] as MultipartFile[]
        JobParameters launched = null
        fileService.saveFilesToTemp(_, _) >> '/tmp/sigil/job-123/'

        when:
        controller.process(files, null, null, false)

        then:
        1 * batchJobOperator.start(videoProcessingJob, _ as JobParameters) >> { Job job, JobParameters params ->
            launched = params
            stubbedExecution()
        }

        and:
        launched.getString('timestamps') == ''
        launched.getString('title') == ''
        launched.getString('stabilize') == 'false'
    }

    def "getJobStatus returns 404 for an unknown job"() {
        when:
        def response = controller.getJobStatus(99L)

        then:
        1 * jobRepository.getJobExecution(99L) >> null

        and:
        response.statusCode == HttpStatus.NOT_FOUND
        response.body == null
    }

    def "getJobStatus reports progress from the step executions"() {
        when:
        def response = controller.getJobStatus(42L)

        then:
        1 * jobRepository.getJobExecution(42L) >> stubbedExecution()

        and:
        response.statusCode == HttpStatus.OK

        and:
        with(response.body) {
            id == 42L
            name == 'videoProcessingJob'
            status == BatchStatus.STARTED
            exitStatus == ExitStatus.EXECUTING
            totalItems == 3
            completedItems == 1
        }
    }

    def "getJobStatus reports the job as complete once the concat step finishes"() {
        given:
        def execution = stubbedExecution(BatchStatus.COMPLETED)
        jobRepository.getJobExecution(42L) >> execution

        when:
        def response = controller.getJobStatus(42L)

        then:
        response.body.totalItems == 3
        response.body.completedItems == 3
    }

    private JobExecution stubbedExecution(BatchStatus concatStatus = BatchStatus.STARTING) {
        def createTime = LocalDateTime.of(2024, 6, 15, 12, 0)
        Stub(JobExecution) {
            getId() >> 42L
            getJobInstance() >> Stub(JobInstance) { getJobName() >> 'videoProcessingJob' }
            getStatus() >> BatchStatus.STARTED
            getExitStatus() >> ExitStatus.EXECUTING
            getStartTime() >> createTime
            getEndTime() >> null
            getCreateTime() >> createTime
            getLastUpdated() >> createTime
            getJobParameters() >> new JobParametersBuilder().addLong('fileCount', 2L).toJobParameters()
            getStepExecutions() >> [
                    Stub(StepExecution) {
                        getStepName() >> 'processFilesStep'
                        getStatus() >> BatchStatus.STARTED
                        getWriteCount() >> 1L
                    },
                    Stub(StepExecution) {
                        getStepName() >> 'concatStep'
                        getStatus() >> concatStatus
                    }
            ]
        }
    }
}
