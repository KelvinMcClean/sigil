package io.github.kelvinmcclean.sigil.files

import io.github.kelvinmcclean.sigil.batch.VideoJobContext
import io.github.kelvinmcclean.sigil.media.MediaConfiguration
import io.github.kelvinmcclean.sigil.timestamp.TimestampService
import org.springframework.web.multipart.MultipartFile
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class FileServiceSpec extends Specification {

    static final long MIDDAY_15_JUNE_2024 = 1718452800L

    @TempDir
    Path baseDir

    TimestampService timestampService = Mock()
    VideoJobContext videoJobContext = Mock()
    MediaConfiguration mediaConfiguration

    @Subject
    FileService fileService

    static TimeZone originalTimeZone

    def setupSpec() {
        // SimpleDateFormat is locale/zone sensitive - pin it so the expected paths are stable
        originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone('UTC'))
    }

    def cleanupSpec() {
        TimeZone.setDefault(originalTimeZone)
    }

    def setup() {
        mediaConfiguration = new MediaConfiguration(
                baseDir: baseDir.toString(),
                subDirPattern: 'yyyy',
                filenamePattern: '{date}-{title}',
                defaultTitle: 'export',
                date: new MediaConfiguration.Date(
                        new MediaConfiguration.Pattern('yyyy', 'MM', 'dd'), '.', '-', true, true))
        fileService = new FileService(mediaConfiguration, timestampService, videoJobContext)
    }

    def "saveFilesToTemp transfers every upload into a job specific temp directory"() {
        given:
        def jobId = UUID.randomUUID().toString()
        def upload = Mock(MultipartFile)

        when:
        def tmpDir = fileService.saveFilesToTemp(jobId, [upload] as MultipartFile[])

        then:
        1 * upload.transferTo({ Path it -> it.fileName.toString() == '_input_0.mp4' }) >> { Path dest ->
            Files.writeString(dest, 'video bytes')
        }

        and:
        Path.of(tmpDir).endsWith(Path.of('sigil', jobId))
        Files.isDirectory(Path.of(tmpDir))

        cleanup:
        Path.of(tmpDir).toFile().deleteDir()
    }

    def "saveFilesToTemp propagates upload failures"() {
        given:
        def jobId = UUID.randomUUID().toString()
        def upload = Mock(MultipartFile) {
            transferTo(_ as Path) >> { throw new IOException('upload failed') }
        }

        when:
        fileService.saveFilesToTemp(jobId, [upload] as MultipartFile[])

        then:
        thrown(IOException)
    }

    def "cleanupJob removes the working directory and its contents"() {
        given:
        def workingDir = Files.createDirectories(baseDir.resolve('job-123'))
        Files.writeString(workingDir.resolve('_processed_0.mp4'), 'data')

        when:
        fileService.cleanupJob(workingDir.toString())

        then:
        !Files.exists(workingDir)
    }

    def "cleanupJob is a no-op when the working directory is already gone"() {
        given:
        def missing = baseDir.resolve('never-created')

        when:
        fileService.cleanupJob(missing.toString())

        then:
        noExceptionThrown()
        !Files.exists(missing)
    }

    def "getFinalPath builds the output path from the configured patterns"() {
        when:
        def finalPath = fileService.getFinalPath()

        then: 'the file name comes from the resolved timestamp and the job title'
        1 * timestampService.resolveTextTimestamp() >> '2024.06.15'
        1 * videoJobContext.getTitle() >> 'holiday'

        and: 'the sub directory comes from the earliest timestamp in the job'
        1 * timestampService.getEarliestTimestamp() >> MIDDAY_15_JUNE_2024

        and:
        finalPath == baseDir.resolve('2024').resolve('2024.06.15-holiday.mp4').toAbsolutePath().toString()
    }

    def "getFinalPath creates the dated output directory"() {
        given:
        timestampService.resolveTextTimestamp() >> '2024.06.15'
        timestampService.getEarliestTimestamp() >> MIDDAY_15_JUNE_2024
        videoJobContext.getTitle() >> 'holiday'

        expect:
        !Files.exists(baseDir.resolve('2024'))

        when:
        fileService.getFinalPath()

        then:
        Files.isDirectory(baseDir.resolve('2024'))
    }

    def "getFinalPath trims the rendered file name"() {
        given:
        mediaConfiguration.filenamePattern = '  {date} {title}  '
        timestampService.resolveTextTimestamp() >> '2024.06.15'
        timestampService.getEarliestTimestamp() >> MIDDAY_15_JUNE_2024
        videoJobContext.getTitle() >> title

        expect:
        fileService.getFinalPath().endsWith(expectedFileName)

        where:
        title     || expectedFileName
        'holiday' || '2024.06.15 holiday.mp4'
        ''        || '2024.06.15.mp4'
    }

    def "getFinalPath returns an absolute path even for a relative base dir"() {
        given:
        mediaConfiguration.baseDir = './build/tmp/sigil-output'
        timestampService.resolveTextTimestamp() >> '2024.06.15'
        timestampService.getEarliestTimestamp() >> MIDDAY_15_JUNE_2024
        videoJobContext.getTitle() >> 'holiday'

        when:
        def finalPath = fileService.getFinalPath()

        then:
        Path.of(finalPath).isAbsolute()

        cleanup:
        new File('./build/tmp/sigil-output').deleteDir()
    }
}
