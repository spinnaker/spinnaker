/*
 * Copyright 2025 OpsMx, Inc.
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

import com.netflix.spinnaker.kork.annotations.VisibleForTesting;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * This class implements {@link SpinnakerProviderTokenServices} to verify if a user meets the
 * provider-specific authentication requirements for GitHub.
 *
 * <p>It checks if a user is a member of a required GitHub organization before granting access.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "spring.security.oauth2.client.registration.github.client-id")
public class GithubProviderTokenServices implements SpinnakerProviderTokenServices {

  private static final String REGISTRATION_ID = "github";
  private static final int PAGE_SIZE = 100;

  @Autowired private GithubRequirements requirements;
  @Autowired private ClientRegistrationRepository clientRegistrationRepository;

  private RestTemplate restTemplate = new RestTemplate();

  @VisibleForTesting
  void setRestTemplate(RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
  }

  private boolean githubOrganizationMember(
      String organization, List<Map<String, String>> organizations) {
    for (Map<String, String> org : organizations) {
      if (org != null && organization.equals(org.get("login"))) {
        return true;
      }
    }
    return false;
  }

  private String organizationsUrl() {
    ClientRegistration registration =
        clientRegistrationRepository.findByRegistrationId(REGISTRATION_ID);
    if (registration == null) {
      log.warn("No client registration found for registrationId: {}", REGISTRATION_ID);
      return null;
    }
    String userInfoUri = registration.getProviderDetails().getUserInfoEndpoint().getUri();
    if (userInfoUri == null || userInfoUri.isEmpty()) {
      log.warn("No user-info-uri configured for registrationId: {}", REGISTRATION_ID);
      return null;
    }
    return UriComponentsBuilder.fromUriString(userInfoUri)
        .path("/orgs")
        .queryParam("per_page", PAGE_SIZE)
        .toUriString();
  }

  private boolean checkOrganization(String accessToken, String organization) {
    try {
      String url = organizationsUrl();
      if (url == null) {
        return false;
      }
      log.debug("Getting user organizations from URL {}", url);

      HttpHeaders headers = new HttpHeaders();
      headers.set("Authorization", "Bearer " + accessToken);
      headers.set("Accept", MediaType.APPLICATION_JSON_VALUE);

      ResponseEntity<List> response =
          restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), List.class);

      List<Map<String, String>> organizations = response.getBody();
      return organizations != null && githubOrganizationMember(organization, organizations);
    } catch (Exception e) {
      log.warn("Could not fetch user organizations", e);
      return false;
    }
  }

  public boolean hasAllProviderRequirements(String token, Map details) {
    String organization = requirements.getOrganization();
    if (organization == null) {
      return true;
    }
    boolean orgMatch = checkOrganization(token, organization);
    if (!orgMatch) {
      log.debug("User does not include required organization {}", organization);
    }
    return orgMatch;
  }

  @Component
  @ConfigurationProperties(
      "spring.security.oauth2.client.registration.github.provider-requirements")
  @Data
  public static class GithubRequirements {
    private String organization;
  }
}
