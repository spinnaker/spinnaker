package com.netflix.spinnaker.gate.security.oauth.config;

import com.netflix.spinnaker.gate.security.oauth.TestAuthorizationCodeTokenResponseClient;
import java.io.IOException;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.impl.client.HttpClients;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

/**
 * Test configuration for simulating OAuth2 authentication flows in integration or unit tests.
 *
 * <p>This configuration provides mock beans and supporting infrastructure for testing Spring
 * Security OAuth2 login flows without requiring access to a real OAuth2 provider.
 *
 * <p>Components provided:
 *
 * <ul>
 *   <li>{@link #mockTokenResponseClient()} – a mock {@link OAuth2AccessTokenResponseClient} that
 *       returns a predefined access token and refresh token for OAuth2 Authorization Code Grant
 *       requests. Marked as {@link Primary} to override any default beans in the context.
 *   <li>{@link TestController} – a simple REST controller exposing a test endpoint (/testOAuth2Api)
 *       that can be used to verify successful authentication and redirection.
 *   <li>{@link #restTemplate()} – a {@link RestTemplate} bean using {@link DefaultRedirectStrategy}
 *       to automatically follow GET/POST redirects during tests.
 * </ul>
 *
 * <p>Use case: testing controllers or services that depend on OAuth2 authentication, including
 * scenarios with null fields, custom user attributes, or full redirect flows.
 */
@TestConfiguration
public class OAuth2TestConfiguration {

  /**
   * This provides a mock {@link OAuth2AccessTokenResponseClient} bean that can be used in
   * integration or unit tests to simulate the OAuth2 Authorization Code Grant flow without calling
   * an actual OAuth2 provider.
   *
   * <p>By defining this bean as {@link Primary}, it overrides any default {@link
   * OAuth2AccessTokenResponseClient} in the Spring context, ensuring that tests use a controlled,
   * predictable token response.
   *
   * <p>Typical use case: testing controllers or services that depend on OAuth2 authentication,
   * including scenarios with custom user attributes or edge cases like null fields.
   */
  @Bean
  @Primary
  OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> mockTokenResponseClient() {
    return new TestAuthorizationCodeTokenResponseClient();
  }

  @RestController
  public static class TestController {
    @GetMapping("/testOAuth2Api")
    public String testOAuth2Api() throws IOException {
      return "authenticated";
    }
  }

  @Bean
  public RestTemplate restTemplate() {
    CloseableHttpClient client =
        HttpClients.custom()
            .setRedirectStrategy(new DefaultRedirectStrategy()) // follows GET/POST redirects
            .build();

    HttpComponentsClientHttpRequestFactory factory =
        new HttpComponentsClientHttpRequestFactory(client);

    return new RestTemplate(factory);
  }
}
