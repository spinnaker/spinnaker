/*
 * Copyright 2025 Salesforce, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.gate;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.netflix.spinnaker.kork.common.Header.REQUEST_ID;
import static com.netflix.spinnaker.kork.common.Header.USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.google.common.collect.ImmutableSet;
import com.netflix.spinnaker.fiat.model.Authorization;
import com.netflix.spinnaker.fiat.model.UserPermission;
import com.netflix.spinnaker.fiat.model.resources.Account;
import com.netflix.spinnaker.fiat.model.resources.Role;
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import com.netflix.spinnaker.gate.health.DownstreamServicesHealthIndicator;
import com.netflix.spinnaker.gate.services.internal.ClouddriverService;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.authentication.preauth.RequestHeaderAuthenticationFilter;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import retrofit2.mock.Calls;

/**
 * Use SpringBootTest.WebEnvironment so tomcat is involved in the test, and all the filters and
 * interceptors present in running code are also present here. Use Wiremock so it's easier to assert
 * on the request headers that downstream services receive (e.g. X-SPINNAKER-REQUEST-ID)
 */
@SpringBootTest(classes = Main.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    properties = {
      "header.enabled=true",
      "logging.level.org.springframework.security=DEBUG",
      "spring.config.location=classpath:gate-test.yml",
      "services.front50.applicationRefreshInitialDelayMs=3600000",
      "services.fiat.enabled=true",
      "provided-id-request-filter.enabled=true",
      "logging.level.com.netflix.spinnaker.gate.filters=DEBUG"
    })
public class HeaderAuthWiremockTest {

  private static final String USERNAME = "test@email.com";

  private static final String TEST_REQUEST_ID = "test-request-id";

  private static final TypeReference<Map<String, Object>> mapType = new TypeReference<>() {};

  @LocalServerPort private int port;

  @Autowired ObjectMapper objectMapper;

  @MockBean ClouddriverService clouddriverService;

  /** To prevent periodic calls to service's /health endpoints */
  @MockBean DownstreamServicesHealthIndicator downstreamServicesHealthIndicator;

  @SpyBean RequestHeaderAuthenticationFilter requestHeaderAuthenticationFilter;

  /** See https://wiremock.org/docs/junit-jupiter/#advanced-usage---programmatic */
  @RegisterExtension
  static WireMockExtension wmFiat =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @Autowired FiatPermissionEvaluator fiatPermissionEvaluator;

  @DynamicPropertySource
  static void registerUrls(DynamicPropertyRegistry registry) {
    // Configure wiremock's random ports into gate
    System.out.println("wiremock fiat url: " + wmFiat.baseUrl());
    registry.add("services.fiat.base-url", wmFiat::baseUrl);
  }

  @BeforeEach
  void init(TestInfo testInfo) {
    System.out.println("--------------- Test " + testInfo.getDisplayName());

    // To keep DefaultProviderLookupService.loadAccounts happy
    when(clouddriverService.getAccountDetails()).thenReturn(Calls.response(List.of()));
  }

  @AfterEach
  void cleanup() {
    // Clean up the permissions cache in FiatPermissionEvaluator since we don't
    // get a fresh bean for each test.  There's an invalidateAll method on the
    // permissions cache, but FiatPermissionEvaluator doesn't expose it.  For
    // now we're testing with one user, so this is sufficient.
    fiatPermissionEvaluator.invalidatePermission(USERNAME);
  }

  @Test
  void testRequestIdHandling() throws Exception {
    // Choose an arbitrary endpoint to verify that gate picks up a
    // X-SPINNAKER-REQUEST-ID request header and uses the value for subsequent
    // requests to downstream services.  Since header auth communicates with
    // fiat, that's the service we use.
    URI uri = new URI("http://localhost:" + port + "/auth/rawUser");

    HttpRequest request =
        HttpRequest.newBuilder(uri)
            .GET()
            .header(USER.getHeader(), USERNAME)
            .header(REQUEST_ID.getHeader(), TEST_REQUEST_ID)
            .build();

    // The first call header auth makes to fiat is loginUser or a post to /roles/{userId}
    String encodedUserId = URLEncoder.encode(USERNAME, StandardCharsets.UTF_8);
    wmFiat.stubFor(
        WireMock.post(urlMatching("/roles/" + encodedUserId))
            .willReturn(aResponse().withStatus(HttpStatus.OK.value())));

    // The second call to fiat is to get permissions
    UserPermission.View userPermissionView =
        new UserPermission.View()
            .setName(USERNAME)
            .setAdmin(false)
            .setAccounts(
                Set.of(
                    new Account.View()
                        .setName("test-account-a")
                        .setAuthorizations(ImmutableSet.of(Authorization.WRITE))))
            .setRoles(
                Set.of(
                    new Role.View().setName("testRoleA").setSource(Role.Source.LDAP))); // arbitrary

    String userPermissionViewJson = objectMapper.writeValueAsString(userPermissionView);

    wmFiat.stubFor(
        WireMock.get(urlMatching("/authorize/" + encodedUserId))
            .willReturn(
                aResponse().withStatus(HttpStatus.OK.value()).withBody(userPermissionViewJson)));

    // ignore the response body since it's not relevant to this test.
    callGate(request, 200);

    // No USER.getHeader() means there's no X-SPINNAKER-USER in the request to fiat.  This is OK.
    wmFiat.verify(
        postRequestedFor(urlPathEqualTo("/roles/" + encodedUserId))
            .withoutHeader(USER.getHeader())
            .withHeader(REQUEST_ID.getHeader(), equalTo(TEST_REQUEST_ID)));

    wmFiat.verify(
        getRequestedFor(urlPathEqualTo("/authorize/" + encodedUserId))
            .withoutHeader(USER.getHeader())
            .withHeader(REQUEST_ID.getHeader(), equalTo(TEST_REQUEST_ID)));
  }

  private String callGate(HttpRequest request, int expectedStatusCode) throws Exception {
    HttpClient client = HttpClient.newBuilder().build();

    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(expectedStatusCode);

    // Verify that there's an X-SPINNAKER-REQUEST-ID header in the response,
    // with the expected value.  That is, the same value provided in the
    // request.  This verifies that gate doesn't use a randomly-generated UUID
    // when one is provided.
    assertThat(response.headers().allValues(REQUEST_ID.getHeader()))
        .containsExactly(TEST_REQUEST_ID);

    return response.body();
  }
}
