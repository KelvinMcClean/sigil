package io.github.kelvinmcclean.sigil


import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.boot.testcontainers.context.ImportTestcontainers
import org.springframework.http.HttpStatus
import spock.lang.Specification

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ImportTestcontainers
class VideoProcessingIntegrationSpec extends Specification {
    @LocalServerPort
    def port

    def "the application context loads"() {
        expect:
        true
    }

    def "canTest"() {
        when:
            RestSpec restSpec = new RestSpec()
            def res = restSpec.post("http://localhost:$port/video/process", String.class, null)
        then:
            res.statusCode == HttpStatus.INTERNAL_SERVER_ERROR

    }

}

