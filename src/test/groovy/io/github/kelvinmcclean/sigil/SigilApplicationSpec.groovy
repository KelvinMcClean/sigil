package io.github.kelvinmcclean.sigil

import org.springframework.boot.test.context.SpringBootTest
import spock.lang.Specification

@SpringBootTest
class SigilApplicationSpec extends Specification {

    def "the application context loads"() {
        expect:
        true
    }
}
