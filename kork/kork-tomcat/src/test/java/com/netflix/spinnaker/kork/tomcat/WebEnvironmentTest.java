package com.netflix.spinnaker.kork.tomcat;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@AutoConfigureTestRestTemplate
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = {WebEnvironmentTest.TestControllerConfiguration.class})
// Tomcat 11 rejects malformed headers even when this legacy option is disabled.
@TestPropertySource(
    properties = {
      "logging.level.org.apache.coyote.http11.Http11InputBuffer = DEBUG",
      "default.rejectIllegalHeader = false"
    })
class WebEnvironmentTest {

  @LocalServerPort int port;

  @Autowired TestRestTemplate restTemplate;

  @Test
  void rejectsIllegalHeadersWithLegacySettingDisabled() throws Exception {
    // Use Apache HttpComponents client which allows invalid headers
    // The default JDK HttpClient in Spring Boot 3.5+ validates headers on the client side
    RestTemplate httpComponentsRestTemplate =
        new RestTemplateBuilder()
            .requestFactory(HttpComponentsClientHttpRequestFactory.class)
            .build();

    HttpHeaders headers = new HttpHeaders();
    // Tomcat 11 always rejects this invalid header name.
    headers.add("X-Dum@my", "foo");

    URI uri =
        UriComponentsBuilder.fromUriString("http://localhost/test-controller")
            .port(port)
            .build()
            .toUri();

    assertThrows(
        HttpClientErrorException.BadRequest.class,
        () ->
            httpComponentsRestTemplate.exchange(
                uri, HttpMethod.GET, new HttpEntity<>(headers), String.class));
  }

  @SpringBootApplication
  public static class TestControllerConfiguration {
    @Bean
    TestController testController() {
      return new TestController();
    }
  }

  @RestController
  @RequestMapping("/test-controller")
  static class TestController {

    @GetMapping
    void get() {
      return;
    }
  }
}
