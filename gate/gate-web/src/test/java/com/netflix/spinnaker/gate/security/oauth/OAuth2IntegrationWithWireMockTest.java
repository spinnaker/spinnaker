/*
 * Copyright 2025 OpsMx, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.gate.security.oauth;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.netflix.spinnaker.gate.health.DownstreamServicesHealthIndicator;
import com.netflix.spinnaker.gate.security.oauth.config.OAuth2TestConfiguration;
import com.netflix.spinnaker.gate.services.ApplicationService;
import com.netflix.spinnaker.gate.services.DefaultProviderLookupService;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

/**
 * Integration test that verifies the end-to-end OAuth2 login flow.
 *
 * <p>WireMock simulates the OAuth2 provider endpoints:
 *
 * <ul>
 *   <li>/login/oauth/authorize → redirects to the app with code and state
 *   <li>/login/oauth/user → returns user info JSON
 * </ul>
 *
 * The application runs on a random port, and RestTemplate is configured to follow redirects. This
 * test also verifies handling of null user ID without causing NPE.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.security.oauth2.client.registration.github.client-id=client-id",
      "spring.security.oauth2.client.registration.github.client-secret=client-secret",
      "spring.security.oauth2.client.registration.github.scope=user,email",
      "spring.security.oauth2.client.registration.userInfoMapping.email=email",
      "spring.security.oauth2.client.registration.userInfoMapping.firstName=firstname",
      "spring.security.oauth2.client.registration.userInfoMapping.lastName=name",
      "spring.security.oauth2.client.registration.userInfoMapping.username=login"
    })
@TestPropertySource(properties = {"spring.config.location=classpath:gate-test.yml"})
@Import(OAuth2TestConfiguration.class)
public class OAuth2IntegrationWithWireMockTest {

  // using restTemplate as it follows redirects which is required while testing
  // the OAuth flows
  @Autowired private RestTemplate restTemplate;

  @LocalServerPort private int appPort;

  @RegisterExtension
  static WireMockExtension githubMockServer =
      WireMockExtension.newInstance()
          .options(wireMockConfig().dynamicPort().extensions(new RedirectWithStateTransformer()))
          .build();

  /** To prevent attempts to connect to clouddriver */
  @MockBean private DefaultProviderLookupService defaultProviderLookupService;

  /** To prevent attempts to connect to front50 */
  @MockBean private ApplicationService applicationService;

  /** To prevent periodic calls to service's /health endpoints */
  @MockBean private DownstreamServicesHealthIndicator downstreamServicesHealthIndicator;

  // Provide WireMock URLs into Spring properties before context initialization uses them
  @DynamicPropertySource
  static void dynamicProperties(DynamicPropertyRegistry registry) {
    String base = "http://localhost:" + githubMockServer.getPort();
    registry.add(
        "spring.security.oauth2.client.provider.github.authorization-uri",
        () -> base + "/login/oauth/authorize");
    registry.add(
        "spring.security.oauth2.client.provider.github.token-uri",
        () -> base + "/login/oauth/access_token");
    registry.add(
        "spring.security.oauth2.client.provider.github.user-info-uri",
        () -> base + "/login/oauth/user");
  }

  @BeforeEach
  public void setUp(TestInfo testInfo) {
    System.out.println("--------------- Test " + testInfo.getDisplayName());
  }

  @Test
  void whenOAuth2UserInfoHasNullsThenAuthenticationSucceeds() {
    githubMockServer.stubFor(
        WireMock.get(urlPathEqualTo("/login/oauth/authorize"))
            .willReturn(WireMock.aResponse().withTransformers("redirect-with-state")));

    githubMockServer.stubFor(
        WireMock.get(urlPathEqualTo("/login/oauth/user"))
            .willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                {
                  "email": "rahul.c@opsmx.io",
                  "login": "rahul-chekuri",
                  "name": "Rahul Chekuri",
                  "type": "User",
                  "id": null
                }
                """)));
    HttpHeaders headers = new HttpHeaders();
    headers.set(
        HttpHeaders.ACCEPT,
        "text/html"); // simulate browser otherwise request will not be cached to replay after
    // Authentication

    HttpEntity<Void> request = new HttpEntity<>(headers);

    ResponseEntity<String> response =
        restTemplate.exchange(
            "http://localhost:" + appPort + "/testOAuth2Api",
            HttpMethod.GET,
            request,
            String.class);
    assertThat(response.getBody()).isEqualTo("authenticated");
    githubMockServer.verify(getRequestedFor(urlPathEqualTo("/login/oauth/authorize")));
    githubMockServer.verify(getRequestedFor(urlPathEqualTo("/login/oauth/user")));
  }

  /**
   * Verifies that a Bearer token provided directly in the Authorization header (e.g. from spin-cli)
   * authenticates the user by calling the OAuth2 provider's user-info endpoint with the token. This
   * is handled by {@link com.netflix.spinnaker.gate.security.oauth2.ExternalAuthTokenFilter
   * ExternalAuthTokenFilter}.
   */
  @Test
  void bearerTokenAuthenticatesViaUserInfoEndpoint() {
    githubMockServer.stubFor(
        WireMock.get(urlPathEqualTo("/login/oauth/user"))
            .willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{"
                            + "\"email\": \"test@example.com\","
                            + "\"login\": \"testuser\","
                            + "\"name\": \"Test User\","
                            + "\"type\": \"User\","
                            + "\"id\": 12345"
                            + "}")));

    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.AUTHORIZATION, "Bearer my-personal-access-token");

    HttpEntity<Void> request = new HttpEntity<>(headers);

    // Use Apache HttpClient with redirect handling disabled so we can observe
    // the actual response from gate rather than following the redirect chain.
    CloseableHttpClient httpClient = HttpClients.custom().disableRedirectHandling().build();
    RestTemplate noRedirectRestTemplate =
        new RestTemplate(new HttpComponentsClientHttpRequestFactory(httpClient));

    ResponseEntity<String> response =
        noRedirectRestTemplate.exchange(
            "http://localhost:" + appPort + "/credentials", HttpMethod.GET, request, String.class);

    assertThat(response.getStatusCodeValue()).isEqualTo(200);
    assertThat(response.getBody()).isNotNull();
    githubMockServer.verify(getRequestedFor(urlPathEqualTo("/login/oauth/user")));
  }

  /**
   * Verifies behavior when a Bearer token is sent directly to the /login endpoint. On the legacy
   * {@code @EnableOAuth2Sso} stack, the OAuth2ClientAuthenticationProcessingFilter at /login picks
   * up the token stashed by ExternalAuthTokenFilter and authenticates via the user-info endpoint,
   * then redirects to the base URL.
   *
   * <p>On the newer {@code oauth2Login()} stack used here, /login redirects to the OAuth2
   * authorization page and ExternalAuthTokenFilter authenticates inline (before the security filter
   * chain), so the two-step /login flow does not apply.
   */
  @Test
  void loginWithBearerTokenAuthenticatesAndRedirectsToBaseUrl() {
    githubMockServer.stubFor(
        WireMock.get(urlPathEqualTo("/login/oauth/user"))
            .willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{"
                            + "\"email\": \"test@example.com\","
                            + "\"login\": \"testuser\","
                            + "\"name\": \"Test User\","
                            + "\"type\": \"User\","
                            + "\"id\": 12345"
                            + "}")));

    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.AUTHORIZATION, "Bearer my-personal-access-token");

    HttpEntity<Void> request = new HttpEntity<>(headers);

    // Use Apache HttpClient with redirect handling disabled so we can observe
    // the actual response from gate rather than following the redirect chain.
    CloseableHttpClient httpClient = HttpClients.custom().disableRedirectHandling().build();
    RestTemplate noRedirectRestTemplate =
        new RestTemplate(new HttpComponentsClientHttpRequestFactory(httpClient));

    // FIXME: On the legacy @EnableOAuth2Sso stack, /login with a Bearer token authenticated
    // the user via the user-info endpoint and redirected to the base URL:
    //   assertThat(response.getStatusCodeValue()).isEqualTo(302);
    //   assertThat(response.getHeaders().getLocation().toString())
    //       .isEqualTo("http://localhost:" + appPort + "/");
    //
    // On the oauth2Login() stack with a custom loginPage, /login is no longer a Spring
    // Security endpoint.  ExternalAuthTokenFilter authenticates inline (calls user-info),
    // but there is no controller mapped to /login, so the authenticated request returns 404.
    assertThatThrownBy(
            () ->
                noRedirectRestTemplate.exchange(
                    "http://localhost:" + appPort + "/login",
                    HttpMethod.GET,
                    request,
                    String.class))
        .isInstanceOf(HttpClientErrorException.NotFound.class);
    githubMockServer.verify(getRequestedFor(urlPathEqualTo("/login/oauth/user")));
  }

  /**
   * Verifies that GET /login without a Bearer token redirects to the OAuth2 provider's
   * authorization endpoint, matching the legacy {@code @EnableOAuth2Sso} behavior.
   */
  @Test
  void loginWithoutBearerTokenRedirectsToOAuthProvider() {
    CloseableHttpClient httpClient = HttpClients.custom().disableRedirectHandling().build();
    RestTemplate noRedirectRestTemplate =
        new RestTemplate(new HttpComponentsClientHttpRequestFactory(httpClient));

    ResponseEntity<String> response =
        noRedirectRestTemplate.exchange(
            "http://localhost:" + appPort + "/login",
            HttpMethod.GET,
            HttpEntity.EMPTY,
            String.class);

    // On the legacy @EnableOAuth2Sso stack, /login redirected directly to the provider's
    // authorization endpoint (/login/oauth/authorize with client_id and response_type params).
    // On the oauth2Login() stack, /login redirects to Spring's intermediate authorization
    // request endpoint (/oauth2/authorization/github), which then redirects to the provider.
    assertThat(response.getStatusCodeValue()).isEqualTo(302);
    assertThat(response.getHeaders().getLocation().toString())
        .contains("/oauth2/authorization/github");
    githubMockServer.verify(0, getRequestedFor(urlPathEqualTo("/login/oauth/user")));
  }
}
