package io.github.kelvinmcclean.sigil


import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.client.NoOpResponseErrorHandler
import org.springframework.web.client.RestTemplate
import spock.lang.Specification

@Component
class RestSpec {

    private final RestTemplate restTemplate = createRestTemplate();

    private static RestTemplate createRestTemplate() {
        RestTemplate template = new RestTemplate();
        template.setErrorHandler(new NoOpResponseErrorHandler());
        return template
    }

    public <T> ResponseEntity<T> get(String uri, Class<T> responseType) {
        return restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(httpHeaders), responseType);
    }

    public <T> ResponseEntity<T> get(String uri, ParameterizedTypeReference<T> responseType) {
        return restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(httpHeaders), responseType);
    }

    public <T> ResponseEntity<T> get(String uri, Class<T> responseType, Object... urlVariables) {
        return restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(httpHeaders), responseType, urlVariables);
    }

    public <T> ResponseEntity<T> post(String uri, Class<T> responseType, Object headerBody, Object... urlVariables) {
        return restTemplate.exchange(uri, HttpMethod.POST, new HttpEntity<>(headerBody, httpHeaders), responseType, urlVariables);
    }

    public <T> ResponseEntity<T> post(String uri, ParameterizedTypeReference<T> responseType, Object headerBody, Object... urlVariables) {
        return restTemplate.exchange(uri, HttpMethod.POST, new HttpEntity<>(headerBody, httpHeaders), responseType, urlVariables);
    }

    public <T> ResponseEntity<T> post(String uri, Class<T> responseType, Object... urlVariables) {
        return restTemplate.exchange(uri, HttpMethod.POST, new HttpEntity<>(httpHeaders), responseType, urlVariables);
    }

    public <T> ResponseEntity<T> post(String uri, ParameterizedTypeReference<T> responseType, Object... urlVariables) {
        return restTemplate.exchange(uri, HttpMethod.POST, new HttpEntity<>(httpHeaders), responseType, urlVariables);
    }

    public <T> ResponseEntity<T> putWithHeader(String uri, Class<T> responseType, Object headerBody, Object... urlVariables) {
        return restTemplate.exchange(uri, HttpMethod.PUT, new HttpEntity<>(headerBody, httpHeaders), responseType, urlVariables);
    }

    public <T> ResponseEntity<T> put(String uri, Class<T> responseType, Object... urlVariables) {
        return restTemplate.exchange(uri, HttpMethod.PUT, new HttpEntity<>(httpHeaders), responseType, urlVariables);
    }

    public <T> ResponseEntity<T> delete(String uri, Class<T> responseType, Object... urlVariables) {
        return restTemplate.exchange(uri, HttpMethod.DELETE, new HttpEntity<>(httpHeaders), responseType, urlVariables);
    }
}
