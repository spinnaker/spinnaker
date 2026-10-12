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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

class GithubProviderTokenServicesTest {
  private static final String USER_ORGS_URL = "https://api.github.com/users/octocat/orgs";
  private static final String AUTHENTICATED_ORGS_URL =
      "https://api.github.com/user/orgs?per_page=100";

  private final RestTemplate restTemplate = mock(RestTemplate.class);
  private GithubProviderTokenServices services;

  @BeforeEach
  void setUp() {
    var requirements = new GithubProviderTokenServices.GithubRequirements();
    requirements.setOrganization("example-org");

    services = new GithubProviderTokenServices();
    ReflectionTestUtils.setField(services, "requirements", requirements);
    services.setRestTemplate(restTemplate);
  }

  @Test
  void acceptsPrivateMember() {
    respond(AUTHENTICATED_ORGS_URL, List.of(Map.of("login", "example-org")));

    assertThat(
            services.hasAllProviderRequirements(
                "token", Map.of("organizations_url", USER_ORGS_URL)))
        .isTrue();
    verify(restTemplate, never())
        .exchange(eq(USER_ORGS_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(List.class));
  }

  @Test
  void rejectsNonMember() {
    respond(AUTHENTICATED_ORGS_URL, List.of(Map.of("login", "SomeOtherOrg")));

    assertThat(
            services.hasAllProviderRequirements(
                "token", Map.of("organizations_url", USER_ORGS_URL)))
        .isFalse();
  }

  @Test
  void rejectsWhenOrganizationsCannotBeFetched() {
    when(restTemplate.exchange(any(String.class), any(), any(), eq(List.class)))
        .thenThrow(new RestClientException("GitHub API unavailable"));

    assertThat(
            services.hasAllProviderRequirements(
                "token", Map.of("organizations_url", USER_ORGS_URL)))
        .isFalse();
  }

  private void respond(String url, List<Map<String, String>> body) {
    when(restTemplate.exchange(eq(url), eq(HttpMethod.GET), any(HttpEntity.class), eq(List.class)))
        .thenReturn(ResponseEntity.ok(body));
  }
}
