package io.github.kelvinmcclean.sigil.timestamp

import io.github.kelvinmcclean.sigil.batch.VideoJobContext
import io.github.kelvinmcclean.sigil.media.MediaConfiguration
import io.github.kelvinmcclean.sigil.timestamp.font.FontConfiguration
import io.github.kelvinmcclean.sigil.timestamp.font.ScreenLocation
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class TimestampServiceSpec extends Specification {

    static final long JUN_15_2024 = 1718452800L
    static final long JUN_17_2024 = 1718625600L
    static final long AUG_02_2024 = 1722600000L
    static final long JAN_03_2025 = 1735905600L

    @TempDir
    Path workingDir

    FontConfiguration fontConfiguration
    MediaConfiguration mediaConfiguration
    VideoJobContext videoJobContext = Mock()

    @Subject
    TimestampService timestampService

    static TimeZone originalTimeZone

    def setupSpec() {
        // SimpleDateFormat is zone sensitive - pin it so the formatted dates are stable
        originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone('UTC'))
    }

    def cleanupSpec() {
        TimeZone.setDefault(originalTimeZone)
    }

    def setup() {
        fontConfiguration = new FontConfiguration(
                path: '',
                size: '48',
                format: '\\:%Y}',
                location: new ScreenLocation('w-tw-20', 'h-th-20'))
        mediaConfiguration = new MediaConfiguration(
                baseDir: './output/',
                subDirPattern: 'yyyy',
                filenamePattern: '{date}-{title}',
                defaultTitle: 'export',
                date: newDateConfig(true, true))
        timestampService = new TimestampService(fontConfiguration, videoJobContext, mediaConfiguration)
    }

    private static MediaConfiguration.Date newDateConfig(boolean splitDay, boolean splitMonth) {
        new MediaConfiguration.Date(
                new MediaConfiguration.Pattern('yyyy', 'MM', 'dd'), '.', '-', splitDay, splitMonth)
    }

    def "processTimestamps renders a drawtext filter from the font configuration"() {
        when:
        def filter = timestampService.processTimestamps(JUN_15_2024, 'app-font.ttf')

        then:
        filter == ",drawtext=fontfile=app-font.ttf: text='%{pts\\:localtime\\:${JUN_15_2024}\\:%Y}':" +
                " x=w-tw-20: y=h-th-20: fontcolor=white: fontsize=48: box=1: boxcolor=black@0.5"
    }

    def "getLatestTimestamp and getEarliestTimestamp read the job context"() {
        given:
        videoJobContext.getTimestamps() >> [AUG_02_2024, JUN_15_2024, JUN_17_2024]

        expect:
        timestampService.getEarliestTimestamp() == JUN_15_2024
        timestampService.getLatestTimestamp() == AUG_02_2024
    }

    def "getTimestampFilter resolves a font and renders the timestamp for the given index"() {
        given:
        def context = Mock(VideoJobContext) {
            getFileDirectory() >> workingDir.toString()
            getTimestamps() >> [JUN_15_2024, JUN_17_2024]
        }

        when:
        def filter = timestampService.getTimestampFilter(context, 1)

        then: 'the bundled font is extracted into the job working directory'
        Files.isRegularFile(workingDir.resolve('app-font.ttf'))

        and: 'the timestamp at the requested index is used'
        filter.startsWith(',drawtext=fontfile=app-font.ttf:')
        filter.contains(JUN_17_2024.toString())
    }

    def "resolveTextTimestamp renders a single date when the job covers one day"() {
        given:
        videoJobContext.getTimestamps() >> [JUN_15_2024, JUN_15_2024 + 3600]

        expect:
        timestampService.resolveTextTimestamp() == '2024.06.15'
    }

    def "resolveTextTimestamp abbreviates the second date based on the configured splits"() {
        given:
        mediaConfiguration.date = newDateConfig(splitDay, splitMonth)
        videoJobContext.getTimestamps() >> [earliest, latest]

        expect:
        timestampService.resolveTextTimestamp() == expected

        where:
        description                 | splitDay | splitMonth | earliest    | latest      || expected
        'same month, day only'      | true     | true       | JUN_15_2024 | JUN_17_2024 || '2024.06.15-17'
        'same year, month and day'  | true     | true       | JUN_15_2024 | AUG_02_2024 || '2024.06.15-08.02'
        'different year, full date' | true     | true       | JUN_15_2024 | JAN_03_2025 || '2024.06.15-2025.01.03'
        'day split disabled'        | false    | true       | JUN_15_2024 | JUN_17_2024 || '2024.06.15-2024.06.17'
        'month split disabled'      | true     | false      | JUN_15_2024 | AUG_02_2024 || '2024.06.15-2024.08.02'
    }

    def "resolveTextTimestamp orders the dates regardless of the order they arrive in"() {
        given:
        videoJobContext.getTimestamps() >> [JAN_03_2025, JUN_15_2024]

        expect:
        timestampService.resolveTextTimestamp() == '2024.06.15-2025.01.03'
    }

    def "getEarliestTimestamp fails fast when the job has no timestamps"() {
        given:
        videoJobContext.getTimestamps() >> []

        when:
        timestampService.getEarliestTimestamp()

        then:
        thrown(NullPointerException)
    }
}
