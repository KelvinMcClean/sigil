package io.github.kelvinmcclean.sigil

import org.springframework.http.ContentDisposition
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.client.NoOpResponseErrorHandler
import org.springframework.web.client.RestTemplate

@Component
class RestSpec {

    private final RestTemplate restTemplate = createRestTemplate();

    private static RestTemplate createRestTemplate() {
        RestTemplate template = new RestTemplate();
        template.setErrorHandler(new NoOpResponseErrorHandler());
        return template
    }

    def <T> ResponseEntity<T> get(String uri, Class<T> responseType) {
        return restTemplate.exchange(uri, HttpMethod.GET, HttpEntity.EMPTY, responseType);
    }

    def <T> ResponseEntity<T> get(String uri, Class<T> responseType, Object... urlVariables) {
        return restTemplate.exchange(uri, HttpMethod.GET, HttpEntity.EMPTY, responseType, urlVariables);
    }

    def <T> ResponseEntity<T> post(String uri, Class<T> responseType, Object body = null, ContentDisposition contentDisposition = null) {
        def headers = new HttpHeaders()
        headers.contentType = MediaType.MULTIPART_FORM_DATA
        headers.contentDisposition = contentDisposition
        def httpEntity = new HttpEntity<>(body, headers)
        return restTemplate.exchange(uri, HttpMethod.POST, httpEntity, responseType)
    }
}
