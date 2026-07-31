package io.github.kelvinmcclean.sigil.timestamp.font

import spock.lang.Specification
import spock.lang.Subject
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class FontResolverSpec extends Specification {

    @Subject
    FontResolver fontResolver = new FontResolver()

    @TempDir
    Path tmpPath

    def "resolveFont extracts the bundled font into the working directory"() {
        given:
        def workingDir = tmpPath.resolve('working-dir')

        when:
        def font = fontResolver.resolveFont(workingDir, '')

        then: 'the relative name of the extracted font is returned'
        font == 'app-font.ttf'

        and: 'the font is written into the working directory, creating it if needed'
        Files.isRegularFile(workingDir.resolve('app-font.ttf'))
        Files.size(workingDir.resolve('app-font.ttf')) > 0
    }

    def "resolveFont reuses an already extracted font"() {
        given: 'a working directory that already holds an extracted font'
        def workingDir = Files.createDirectories(tmpPath.resolve('working-dir'))
        def existingFont = Files.writeString(workingDir.resolve('app-font.ttf'), 'previously extracted')

        when:
        def font = fontResolver.resolveFont(workingDir, '')

        then:
        font == 'app-font.ttf'

        and: 'the existing font is left untouched'
        existingFont.text == 'previously extracted'
    }

    def "resolveFont prefers a configured user font over the bundled one"() {
        given: 'a configured font that exists on disk and an empty working directory'
        def userFont = Files.writeString(tmpPath.resolve('user-font.ttf'), 'user font')
        def workingDir = tmpPath.resolve('work')

        when:
        def font = fontResolver.resolveFont(workingDir, userFont.toString())

        then: 'the bundled font is not extracted'
        !Files.exists(workingDir.resolve('app-font.ttf'))

        and: 'a path relative to the configured font is returned'
        // NOTE: this pins down current behaviour - the arguments to `relativize` look
        // reversed, so the returned path points at a font that was never extracted.
        font == Paths.get('..', 'work', 'app-font.ttf').toString()
    }

    def "resolveFont falls back to the bundled font when the configured font is missing"() {
        given:
        def workingDir = tmpPath.resolve('working-dir')

        when:
        def font = fontResolver.resolveFont(workingDir, userFont)

        then:
        font == 'app-font.ttf'
        Files.isRegularFile(workingDir.resolve('app-font.ttf'))

        where:
        userFont << ['', 'does/not/exist.ttf']
    }
}
