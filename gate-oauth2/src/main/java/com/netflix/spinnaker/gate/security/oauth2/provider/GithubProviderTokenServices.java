/*
 * Copyright 2025 Netflix, Inc.
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
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

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

  @Autowired private GithubRequirements requirements;

  private boolean githubOrganizationMember(
      String organization, List<Map<String, String>> organizations) {
    for (Map<String, String> org : organizations) {
      if (organization.equals(org.get("login"))) {
        return true;
      }
    }
    return false;
  }

  private boolean checkOrganization(
      String accessToken, String organizationsUrl, String organization) {
    try {
      log.debug("Getting user organizations from URL {}", organizationsUrl);
      RestTemplate restTemplate = new RestTemplate();

      HttpHeaders headers = new HttpHeaders();
      headers.set("Authorization", "Bearer " + accessToken);
      headers.set("Accept", MediaType.APPLICATION_JSON_VALUE);

      ResponseEntity<List> response =
          restTemplate.exchange(
              organizationsUrl, HttpMethod.GET, new HttpEntity<>(headers), List.class);

      List<Map<String, String>> organizations = response.getBody();
      return githubOrganizationMember(organization, organizations);
    } catch (Exception e) {
      log.warn("Could not fetch user organizations", e);
      return false;
    }
  }

  public boolean hasAllProviderRequirements(String token, Map details) {
    boolean hasRequirements = true;
    if (requirements.getOrganization() != null && details.containsKey("organizations_url")) {
      boolean orgMatch =
          checkOrganization(
              token, (String) details.get("organizations_url"), requirements.getOrganization());
      if (!orgMatch) {
        log.debug("User does not include required organization {}", requirements.getOrganization());
        hasRequirements = false;
      }
    }
    return hasRequirements;
  }

  @Component
  @ConfigurationProperties(
      "spring.security.oauth2.client.registration.github.provider-requirements")
  @Data
  public static class GithubRequirements {
    private String organization;
  }
}
