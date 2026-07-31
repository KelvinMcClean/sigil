package io.github.kelvinmcclean.sigil.files

import org.springframework.web.multipart.MultipartFile
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

@Subject(FileUtils)
class FileUtilsSpec extends Specification {

    @TempDir
    Path tmpPath

    def "createConcatPath writes one ffmpeg concat entry per processed file"() {
        when:
        def listFile = FileUtils.createConcatPath(tmpPath, fileCount)

        then:
        Files.exists(listFile)
        listFile.fileName.toString() == '_fileList.txt'
        listFile.readLines() == (0..<fileCount).collect { "file '_processed_${it}.mp4'".toString() }

        where:
        fileCount << [0, 1, 3]
    }

    def "getTmpDir creates and returns a job specific directory under the system temp dir"() {
        given:
        def jobId = UUID.randomUUID().toString()

        when:
        def tmpDir = FileUtils.getTmpDir(jobId)

        then:
        !tmpDir.isEmpty()
        tmpDir.contains("sigil/${jobId}")
        tmpDir.endsWith('/')
        Files.isDirectory(Path.of(tmpDir))

        cleanup:
        Path.of(tmpDir).toFile().deleteDir()
    }

    def "getTmpDir is idempotent for the same job id"() {
        given:
        def jobId = UUID.randomUUID().toString()

        when:
        def first = FileUtils.getTmpDir(jobId)
        def second = FileUtils.getTmpDir(jobId)

        then:
        first == second
        Files.isDirectory(Path.of(first))

        cleanup:
        Path.of(first).toFile().deleteDir()
    }

    def "transfer writes each uploaded file to an indexed input file"() {
        given: 'two uploads that write their content when transferred'
        def first = Mock(MultipartFile)
        def second = Mock(MultipartFile)

        when:
        FileUtils.transfer([first, second] as MultipartFile[], tmpPath)

        then: 'each file is transferred to its indexed destination'
        1 * first.transferTo({ Path it -> it == tmpPath.resolve('_input_0.mp4') }) >> { Path dest ->
            Files.writeString(dest, 'first content')
        }
        1 * second.transferTo({ Path it -> it == tmpPath.resolve('_input_1.mp4') }) >> { Path dest ->
            Files.writeString(dest, 'second content')
        }

        and: 'the content ends up on disk'
        tmpPath.resolve('_input_0.mp4').text == 'first content'
        tmpPath.resolve('_input_1.mp4').text == 'second content'
    }

    def "transfer does nothing when there are no files"() {
        when:
        FileUtils.transfer(new MultipartFile[0], tmpPath)

        then:
        tmpPath.toFile().list().length == 0
    }

    def "transfer propagates IO failures from the underlying upload"() {
        given:
        def file = Mock(MultipartFile) {
            transferTo(_ as Path) >> { throw new IOException('disk full') }
        }

        when:
        FileUtils.transfer([file] as MultipartFile[], tmpPath)

        then:
        def e = thrown(IOException)
        e.message == 'disk full'
    }
}
