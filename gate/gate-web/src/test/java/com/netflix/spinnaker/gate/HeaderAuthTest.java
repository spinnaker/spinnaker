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

import static com.netflix.spinnaker.kork.common.Header.USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.BOOLEAN;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;
import com.netflix.spinnaker.fiat.model.Authorization;
import com.netflix.spinnaker.fiat.model.UserPermission;
import com.netflix.spinnaker.fiat.model.resources.Account;
import com.netflix.spinnaker.fiat.model.resources.Role;
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import com.netflix.spinnaker.fiat.shared.FiatService;
import com.netflix.spinnaker.gate.health.DownstreamServicesHealthIndicator;
import com.netflix.spinnaker.gate.services.internal.ClouddriverService;
import com.netflix.spinnaker.gate.services.internal.OrcaService;
import com.netflix.spinnaker.gate.services.internal.OrcaServiceSelector;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedCredentialsNotFoundException;
import org.springframework.security.web.authentication.preauth.RequestHeaderAuthenticationFilter;
import org.springframework.test.context.TestPropertySource;
import retrofit2.mock.Calls;

/**
 * Use SpringBootTest.WebEnvironment so tomcat is involved in the test. This makes it possible to
 * test more exception handling cases (e.g. no X-SPINNAKER-USER header provided) in a way that's
 * closer to what happens in running code.
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
public class HeaderAuthTest {

  private static final String USERNAME = "test@email.com";

  private static final TypeReference<Map<String, Object>> mapType = new TypeReference<>() {};

  @LocalServerPort private int port;

  @Autowired ObjectMapper objectMapper;

  @MockBean ClouddriverService clouddriverService;

  /** To prevent periodic calls to service's /health endpoints */
  @MockBean DownstreamServicesHealthIndicator downstreamServicesHealthIndicator;

  @SpyBean RequestHeaderAuthenticationFilter requestHeaderAuthenticationFilter;

  @MockBean FiatService fiatService;

  @MockBean OrcaServiceSelector orcaServiceSelector;

  @MockBean OrcaService orcaService;

  @Autowired FiatPermissionEvaluator fiatPermissionEvaluator;

  @BeforeEach
  void init(TestInfo testInfo) {
    System.out.println("--------------- Test " + testInfo.getDisplayName());

    // To keep DefaultProviderLookupService.loadAccounts happy
    when(clouddriverService.getAccountDetails()).thenReturn(Calls.response(List.of()));

    when(orcaServiceSelector.select()).thenReturn(orcaService);
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
  void testAuthUserWithUser() throws Exception {
    URI uri = new URI("http://localhost:" + port + "/auth/user");

    HttpRequest request =
        HttpRequest.newBuilder(uri).GET().header(USER.getHeader(), USERNAME).build();

    when(fiatService.loginUser(USERNAME)).thenReturn(Calls.response((Void) null));

    // simulate a role from fiat
    when(fiatService.getUserPermission(USERNAME))
        .thenReturn(
            Calls.response(
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
                            new Role.View()
                                .setName("testRoleA")
                                .setSource(Role.Source.LDAP))))); // arbitrary
    String response = callGate(request, 200);

    Map<String, Object> jsonResponse = objectMapper.readValue(response, mapType);

    assertThat(jsonResponse.get("email")).isEqualTo(USERNAME);
    assertThat(jsonResponse.get("username")).isEqualTo(USERNAME);
    assertThat(jsonResponse.get("firstName")).isNull();
    assertThat(jsonResponse.get("lastName")).isNull();
    assertThat(jsonResponse.get("roles")).asInstanceOf(LIST).containsExactly("testRoleA");
    assertThat(jsonResponse.get("allowedAccounts"))
        .asInstanceOf(LIST)
        .containsExactly("test-account-a");
    assertThat(jsonResponse.get("enabled")).asInstanceOf(BOOLEAN).isTrue();
    assertThat(jsonResponse.get("authorities"))
        .asInstanceOf(LIST)
        .contains(Map.of("authority", "testRoleA"));
    assertThat(jsonResponse.get("accountNonExpired")).asInstanceOf(BOOLEAN).isTrue();
    assertThat(jsonResponse.get("accountNonLocked")).asInstanceOf(BOOLEAN).isTrue();
    assertThat(jsonResponse.get("credentialsNonExpired")).asInstanceOf(BOOLEAN).isTrue();

    // Make sure there isn't some exception-handling path that added a message to the response
    assertThat(jsonResponse.containsKey("message")).isFalse();

    verifyRequestProcessing(1);

    // Verify that gate called fiat
    verify(fiatService).loginUser(USERNAME);
    verify(fiatService).getUserPermission(USERNAME);

    // Verify that there were no other fiat interactions
    verifyNoMoreInteractions(fiatService);
  }

  @Test
  void testAuthRawUserWithUser() throws Exception {
    URI uri = new URI("http://localhost:" + port + "/auth/rawUser");

    HttpRequest request =
        HttpRequest.newBuilder(uri).GET().header(USER.getHeader(), USERNAME).build();

    when(fiatService.loginUser(USERNAME)).thenReturn(Calls.response((Void) null));

    when(fiatService.getUserPermission(USERNAME))
        .thenReturn(
            Calls.response(
                new UserPermission.View()
                    .setName(USERNAME)
                    .setAdmin(false)
                    .setAccounts(
                        Set.of(
                            new Account.View()
                                .setName("test-account-b")
                                .setAuthorizations(ImmutableSet.of(Authorization.WRITE))))
                    .setRoles(
                        Set.of(
                            new Role.View()
                                .setName("testRoleB")
                                .setSource(Role.Source.LDAP))))); // arbitrary

    String response = callGate(request, 200);

    Map<String, Object> jsonResponse = objectMapper.readValue(response, mapType);

    assertThat(jsonResponse.get("email")).isEqualTo(USERNAME);
    assertThat(jsonResponse.get("username")).isEqualTo(USERNAME);
    assertThat(jsonResponse.get("firstName")).isNull();
    assertThat(jsonResponse.get("lastName")).isNull();
    assertThat(jsonResponse.get("roles")).asInstanceOf(LIST).isEmpty();
    assertThat(jsonResponse.get("allowedAccounts"))
        .asInstanceOf(LIST)
        .containsExactly("test-account-b");
    assertThat(jsonResponse.get("enabled")).asInstanceOf(BOOLEAN).isTrue();
    assertThat(jsonResponse.get("authorities")).asInstanceOf(LIST).isEmpty();
    assertThat(jsonResponse.get("accountNonExpired")).asInstanceOf(BOOLEAN).isTrue();
    assertThat(jsonResponse.get("accountNonLocked")).asInstanceOf(BOOLEAN).isTrue();
    assertThat(jsonResponse.get("credentialsNonExpired")).asInstanceOf(BOOLEAN).isTrue();

    // Make sure there isn't some exception-handling path that added a message to the response
    assertThat(jsonResponse.containsKey("message")).isFalse();

    verifyRequestProcessing(1);

    // Verify interactions with fiat.
    verify(fiatService).loginUser(USERNAME);
    verify(fiatService).getUserPermission(USERNAME);
    verifyNoMoreInteractions(fiatService);
  }

  // TODO: expect anonymous once the code is set up to do that
  @Test
  void testAuthUserWithNoUser() throws Exception {
    URI uri = new URI("http://localhost:" + port + "/auth/user");

    HttpRequest request = HttpRequest.newBuilder(uri).GET().build();

    String response = callGate(request, 500);

    Map<String, Object> jsonResponse = objectMapper.readValue(response, mapType);
    assertThat(jsonResponse.get("message"))
        .isEqualTo("X-SPINNAKER-USER header not found in request.");
    assertThat(jsonResponse.get("exception"))
        .isEqualTo(PreAuthenticatedCredentialsNotFoundException.class.getName());
    assertThat(jsonResponse.get("status")).isEqualTo(500);

    verifyRequestProcessing(1);
  }

  @Test
  void testSpinnakerTomcatErrorValve() throws Exception {
    // If error handling is configured properly, other tests don't exercise
    // SpinnakerTomcatErrorValve, so let's exercise it here.an-invalid-character")
    URI uri = new URI("http://localhost:" + port + "/bracket-is-an-invalid-character?[foo]");

    HttpRequest request = HttpRequest.newBuilder(uri).GET().build();

    String response = callGate(request, 400);

    Map<String, Object> jsonResponse = objectMapper.readValue(response, mapType);
    assertThat(jsonResponse.get("message"))
        .isEqualTo(
            "Invalid character found in the request target [/bracket-is-an-invalid-character?[foo] ]. The valid characters are defined in RFC 7230 and RFC 3986");
    assertThat(jsonResponse.get("exception")).isEqualTo(IllegalArgumentException.class.getName());
    assertThat(jsonResponse.get("status")).isEqualTo(400);
  }

  @Test
  void testCsrfDisabled() throws Exception {
    // Choose an arbitrary endpoint that only works if csrf is disabled.  That
    // is, any endpoint with an http method that DefaultRequiresCsrfMatcher
    // doesn't allow.  Something besides: GET, HEAD, TRACE, OPTIONS.
    URI uri = new URI("http://localhost:" + port + "/pipelines/start");

    HttpRequest request =
        HttpRequest.newBuilder(uri)
            .header(USER.getHeader(), USERNAME)
            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .POST(HttpRequest.BodyPublishers.ofString("{}"))
            .build();

    when(fiatService.loginUser(USERNAME)).thenReturn(Calls.response((Void) null));

    when(fiatService.getUserPermission(USERNAME))
        .thenReturn(
            Calls.response(
                new UserPermission.View()
                    .setName(USERNAME)
                    .setAdmin(false)
                    .setAccounts(
                        Set.of(
                            new Account.View()
                                .setName("test-account-c")
                                .setAuthorizations(ImmutableSet.of(Authorization.WRITE))))));

    // arbitrary execution info
    when(orcaService.startPipeline(anyMap(), eq(USERNAME))).thenReturn(Calls.response(Map.of()));

    String response = callGate(request, 200);

    // The response from orcaService.startPipeline configured above
    assertThat(response).isEqualTo("{}");

    verify(fiatService).loginUser(USERNAME);
    verify(fiatService).getUserPermission(USERNAME);
    verifyNoMoreInteractions(fiatService);

    verify(orcaService).startPipeline(anyMap(), eq(USERNAME));
    verifyNoMoreInteractions(orcaService);
  }

  private String callGate(HttpRequest request, int expectedStatusCode) throws Exception {
    HttpClient client = HttpClient.newBuilder().build();

    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(expectedStatusCode);

    return response.body();
  }

  /**
   * Verify the number of times that RequestHeaderAuthenticationFilter processed requests. This is
   * an implementation detail of spring + spring security, but it tells us something about how the
   * request is handled, and whether we've got error/exception handling set up properly.
   */
  private void verifyRequestProcessing(int desiredNumberOfTimes) throws Exception {
    // Use reflection to access getPreAuthenticatedPrincipal since it's protected
    Method getPreAuthenticatedPrincipalMethod =
        RequestHeaderAuthenticationFilter.class.getDeclaredMethod(
            "getPreAuthenticatedPrincipal", HttpServletRequest.class);
    getPreAuthenticatedPrincipalMethod.setAccessible(true);

    // With a Method, this is the cumbersome/non-obvious way to verify it was
    // called.
    getPreAuthenticatedPrincipalMethod.invoke(
        verify(requestHeaderAuthenticationFilter, times(desiredNumberOfTimes)),
        any(HttpServletRequest.class));
  }
}
