package io.github.kelvinmcclean.sigil

import groovy.json.JsonSlurper
import jakarta.annotation.PreDestroy
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.boot.testcontainers.context.ImportTestcontainers
import org.springframework.core.io.ClassPathResource
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpStatus
import org.springframework.test.context.TestPropertySource
import org.springframework.util.LinkedMultiValueMap
import spock.lang.Shared
import spock.lang.Specification

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = ["sigil.media.base-dir=./tmp/output"])
@ImportTestcontainers
class VideoProcessingIntegrationSpec extends Specification {

    @LocalServerPort
    def port

    @Shared String url
    @Shared String getUrl
    @Shared RestSpec restSpec


    def setup() {
        url = "http://localhost:$port/api/video/process"
        getUrl = "http://localhost:$port/api/video/status/"
        restSpec = new RestSpec()
    }


    def "sending no body fails due to unsupported media type"() {
        when:
            def res = restSpec.post(url, String.class)
        then:
            res.statusCode == HttpStatus.UNSUPPORTED_MEDIA_TYPE
    }

    def "Video can be processed and result in a valid file"() {
        given: "Body"
            def jsonSlurper = new JsonSlurper()
            def body = new LinkedMultiValueMap<String, Object>();
            def resource = new ClassPathResource("/stock.mp4")
            body.add("files", resource)
        when:
            def res = restSpec.post(url, String.class, body)
            def jsonBody = jsonSlurper.parseText(res.body) as Map<String, Object>
        then:
            res.statusCode == HttpStatus.ACCEPTED
        when:
            def pollStatus = pollJob(jsonBody.id, jsonSlurper)
        then:
            pollStatus.id == 1
            ["STARTED", "COMPLETED"].contains(pollStatus.status)
            pollStatus.completedItems != null
            pollStatus.totalItems == 2
        when: "Wait for job to complete"
            pollStatus = getCompletedJob(jsonBody.id, jsonSlurper)
            def completedFile = new File("./tmp/output/2017/2017.01.17-export.mp4")
        then:
            pollStatus.status == "COMPLETED"
            completedFile.exists()
            completedFile.size() > 3027780
    }

    private Map<String, Object> pollJob(def id, JsonSlurper jsonSlurper) {
        def res = restSpec.get(getUrl+id, String.class)
        return jsonSlurper.parseText(res.body as String) as Map<String, Object>
    }

    private Map<String, Object> getCompletedJob(def id, JsonSlurper jsonSlurper) {
        Map res
        do {
            res = pollJob(id, jsonSlurper)
        } while (res.status != "COMPLETED")
        return res
    }

    @PreDestroy
    def cleanup() {
        def completedFile = new File("./tmp/")
        if (completedFile.exists()) {
            completedFile.deleteDir()
        }
    }

}

