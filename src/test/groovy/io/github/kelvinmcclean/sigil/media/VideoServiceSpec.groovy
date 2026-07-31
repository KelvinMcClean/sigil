package io.github.kelvinmcclean.sigil.media

import io.github.kelvinmcclean.sigil.batch.VideoJobContext
import io.github.kelvinmcclean.sigil.ffmpeg.FfmpegUtils
import io.github.kelvinmcclean.sigil.files.FileService
import io.github.kelvinmcclean.sigil.timestamp.TimestampService
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class VideoServiceSpec extends Specification {

    static final String STANDARD_FILTERS =
            'scale=1920:1080:force_original_aspect_ratio=decrease,pad=1920:1080:(ow-iw)/2:(oh-ih)/2,setsar=1'

    @TempDir
    Path workingDir

    TimestampService timestampService = Mock()
    FileService fileService = Mock()
    FfmpegUtils ffmpegUtils = Mock()
    VideoJobContext videoJobContext = Mock()

    @Subject
    VideoService videoService = new VideoService(timestampService, fileService, ffmpegUtils)

    def setup() {
        videoJobContext.getFileDirectory() >> workingDir.toString()
        videoJobContext.getTimestamps() >> []
        videoJobContext.getStabilize() >> false
    }

    private Path givenInputFile(int index) {
        Files.writeString(workingDir.resolve("_input_${index}.mp4"), 'video bytes')
    }

    def "preprocess skips files that are not on disk"() {
        when:
        videoService.preprocess(videoJobContext, 0)

        then:
        0 * ffmpegUtils._
        0 * timestampService._
    }

    def "preprocess renders with the standard scale and pad filters"() {
        given:
        givenInputFile(0)

        when:
        videoService.preprocess(videoJobContext, 0)

        then:
        1 * ffmpegUtils.preprocess(
                { StringBuilder it -> it.toString() == STANDARD_FILTERS },
                '_input_0.mp4',
                '_processed_0.mp4',
                { File it -> it.toPath() == workingDir }) >> 0

        and: 'no stabilisation or repair pass is needed'
        0 * ffmpegUtils.stabalize(*_)
        0 * ffmpegUtils.repair(*_)
    }

    def "preprocess runs a stabilisation detection pass first when requested"() {
        given:
        givenInputFile(0)
        videoJobContext = Mock(VideoJobContext) {
            getFileDirectory() >> workingDir.toString()
            getTimestamps() >> []
            getStabilize() >> true
        }

        when:
        videoService.preprocess(videoJobContext, 0)

        then: 'the transforms are detected before the render pass'
        1 * ffmpegUtils.stabalize({ File it -> it.toPath() == workingDir }, '_input_0.mp4', '_transforms_0.trf')

        then: 'the transform filter is prepended to the filtergraph'
        1 * ffmpegUtils.preprocess(
                { StringBuilder it ->
                    it.toString() == "vidstabtransform=input=_transforms_0.trf:zoom=0:smoothing=10,${STANDARD_FILTERS}"
                },
                '_input_0.mp4', '_processed_0.mp4', _) >> 0
    }

    def "preprocess appends the timestamp filter for the file being processed"() {
        given:
        givenInputFile(1)
        videoJobContext = Mock(VideoJobContext) {
            getFileDirectory() >> workingDir.toString()
            getTimestamps() >> [1718452800L, 1718625600L]
            getStabilize() >> false
        }

        when:
        videoService.preprocess(videoJobContext, 1)

        then:
        1 * timestampService.getTimestampFilter(videoJobContext, 1) >> ',drawtext=fontfile=app-font.ttf'

        and:
        1 * ffmpegUtils.preprocess(
                { StringBuilder it -> it.toString() == "${STANDARD_FILTERS},drawtext=fontfile=app-font.ttf" },
                '_input_1.mp4', '_processed_1.mp4', _) >> 0
    }

    def "preprocess repairs and retries when the first render pass fails"() {
        given:
        givenInputFile(0)

        when:
        videoService.preprocess(videoJobContext, 0)

        then: 'the first render fails'
        1 * ffmpegUtils.preprocess(_, '_input_0.mp4', '_processed_0.mp4', _) >> 1

        then: 'the input is repaired'
        1 * ffmpegUtils.repair('_input_0.mp4', 'repaired__input_0.mp4', _) >> 0

        then: 'the render is retried against the repaired input'
        1 * ffmpegUtils.preprocess(_, 'repaired__input_0.mp4', '_processed_0.mp4', _) >> 0
    }

    def "preprocess gives up when the repair pass also fails"() {
        given:
        givenInputFile(0)

        when:
        videoService.preprocess(videoJobContext, 0)

        then:
        1 * ffmpegUtils.preprocess(_, '_input_0.mp4', '_processed_0.mp4', _) >> 1
        1 * ffmpegUtils.repair('_input_0.mp4', 'repaired__input_0.mp4', _) >> 127
        0 * ffmpegUtils.preprocess(_, 'repaired__input_0.mp4', _, _)
    }

    def "concat joins every processed file into the final output"() {
        given: 'two processed files plus files that should be ignored'
        Files.writeString(workingDir.resolve('_processed_0.mp4'), 'a')
        Files.writeString(workingDir.resolve('_processed_1.mp4'), 'b')
        Files.writeString(workingDir.resolve('_input_0.mp4'), 'raw')
        Files.writeString(workingDir.resolve('app-font.ttf'), 'font')
        def finalPath = workingDir.resolve('holiday.mp4').toString()

        when:
        videoService.concat(videoJobContext)

        then: 'the destination is resolved by the file service'
        1 * fileService.getFinalPath() >> finalPath

        and: 'ffmpeg is handed a concat list containing only the processed files'
        1 * ffmpegUtils.concat(
                { Path it -> it.fileName.toString() == '_fileList.txt' },
                finalPath,
                { File it -> it.toPath() == workingDir }) >> 0

        and:
        workingDir.resolve('_fileList.txt').readLines() == ["file '_processed_0.mp4'", "file '_processed_1.mp4'"]
    }

    def "concat still runs when there is nothing to join"() {
        given:
        def finalPath = workingDir.resolve('holiday.mp4').toString()
        fileService.getFinalPath() >> finalPath

        when:
        videoService.concat(videoJobContext)

        then:
        1 * ffmpegUtils.concat(_, finalPath, _) >> 0

        and:
        workingDir.resolve('_fileList.txt').readLines() == []
    }
}
