/*
 * Copyright 2026 spinnaker.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
package com.netflix.spinnaker.gate.security.oauth2.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

class GithubProviderTokenServicesTest {
  private static final String USER_ORGS_URL = "https://api.github.com/users/octocat/orgs";
  private static final String MEMBERSHIP_URL =
      "https://api.github.com/user/memberships/orgs/example-org";
  private static final Map<String, Object> DETAILS =
      Map.of("login", "octocat", "organizations_url", USER_ORGS_URL);

  private final RestTemplate restTemplate = mock(RestTemplate.class);
  private GithubProviderTokenServices services;

  @BeforeEach
  void setUp() {
    var requirements = new GithubProviderTokenServices.GithubRequirements();
    requirements.setOrganization("example-org");

    services = new GithubProviderTokenServices();
    ReflectionTestUtils.setField(services, "requirements", requirements);
    useUserInfoUri("https://api.github.com/user");
    services.setRestTemplate(restTemplate);
  }

  @Test
  void acceptsActiveMemberIncludingPrivate() {
    respond(MEMBERSHIP_URL, Map.of("state", "active"));

    assertThat(services.hasAllProviderRequirements("token", DETAILS)).isTrue();
    verify(restTemplate, never())
        .exchange(eq(USER_ORGS_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(List.class));
  }

  @Test
  void checksMembershipWithoutOrganizationsUrlInUserInfo() {
    respond(MEMBERSHIP_URL, Map.of("state", "active"));

    assertThat(services.hasAllProviderRequirements("token", Map.of("login", "octocat"))).isTrue();
  }

  @Test
  void sendsBearerToken() {
    respond(MEMBERSHIP_URL, Map.of("state", "active"));

    services.hasAllProviderRequirements("token", DETAILS);

    var entity = ArgumentCaptor.forClass(HttpEntity.class);
    verify(restTemplate)
        .exchange(eq(MEMBERSHIP_URL), eq(HttpMethod.GET), entity.capture(), eq(Map.class));
    assertThat(entity.getValue().getHeaders().getFirst("Authorization")).isEqualTo("Bearer token");
  }

  @Test
  void rejectsPendingMember() {
    respond(MEMBERSHIP_URL, Map.of("state", "pending"));

    assertThat(services.hasAllProviderRequirements("token", DETAILS)).isFalse();
  }

  @Test
  void rejectsNonMember() {
    when(restTemplate.exchange(
            eq(MEMBERSHIP_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
        .thenThrow(
            HttpClientErrorException.create(
                HttpStatus.NOT_FOUND, "Not Found", new HttpHeaders(), null, null));

    assertThat(services.hasAllProviderRequirements("token", DETAILS)).isFalse();
  }

  @Test
  void rejectsWhenMembershipCannotBeFetched() {
    when(restTemplate.exchange(any(String.class), any(), any(), eq(Map.class)))
        .thenThrow(new RestClientException("GitHub API unavailable"));

    assertThat(services.hasAllProviderRequirements("token", DETAILS)).isFalse();
  }

  @Test
  void rejectsWhenResponseHasNoBody() {
    when(restTemplate.exchange(
            eq(MEMBERSHIP_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
        .thenReturn(ResponseEntity.ok().build());

    assertThat(services.hasAllProviderRequirements("token", DETAILS)).isFalse();
  }

  @Test
  void derivesMembershipEndpointFromUserInfoUri() {
    useUserInfoUri("https://ghe.example.com/api/v3/user");
    respond(
        "https://ghe.example.com/api/v3/user/memberships/orgs/example-org",
        Map.of("state", "active"));

    assertThat(services.hasAllProviderRequirements("token", DETAILS)).isTrue();
  }

  @Test
  void acceptsAnyUserWithoutOrganizationRequirement() {
    ReflectionTestUtils.setField(
        services, "requirements", new GithubProviderTokenServices.GithubRequirements());

    assertThat(services.hasAllProviderRequirements("token", DETAILS)).isTrue();
    verify(restTemplate, never()).exchange(any(String.class), any(), any(), eq(Map.class));
  }

  @Test
  void rejectsWhenGithubRegistrationIsMissing() {
    useRegistration("other", "https://other.example.com/user");

    assertThat(services.hasAllProviderRequirements("token", DETAILS)).isFalse();
    verify(restTemplate, never()).exchange(any(String.class), any(), any(), eq(Map.class));
  }

  @Test
  void rejectsWhenUserInfoUriIsNotConfigured() {
    useUserInfoUri(null);

    assertThat(services.hasAllProviderRequirements("token", DETAILS)).isFalse();
    verify(restTemplate, never()).exchange(any(String.class), any(), any(), eq(Map.class));
  }

  private void useUserInfoUri(String userInfoUri) {
    useRegistration("github", userInfoUri);
  }

  private void useRegistration(String registrationId, String userInfoUri) {
    var registration =
        ClientRegistration.withRegistrationId(registrationId)
            .clientId("client-id")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("https://gate.example.com/login/oauth2/code/github")
            .authorizationUri("https://github.com/login/oauth/authorize")
            .tokenUri("https://github.com/login/oauth/access_token")
            .userInfoUri(userInfoUri)
            .build();
    ReflectionTestUtils.setField(
        services,
        "clientRegistrationRepository",
        new InMemoryClientRegistrationRepository(registration));
  }

  private void respond(String url, Map<String, String> body) {
    when(restTemplate.exchange(eq(url), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
        .thenReturn(ResponseEntity.ok(body));
  }
}
