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
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
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

  @Autowired private GithubRequirements requirements;
  @Autowired private ClientRegistrationRepository clientRegistrationRepository;

  private RestTemplate restTemplate =
      new RestTemplateBuilder()
          .setConnectTimeout(Duration.ofSeconds(10))
          .setReadTimeout(Duration.ofSeconds(10))
          .build();

  @VisibleForTesting
  void setRestTemplate(RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
  }

  private boolean githubOrganizationMember(
      String organization, List<Map<String, String>> organizations) {
    for (Map<String, String> org : organizations) {
      if (organization.equalsIgnoreCase(org.get("login"))) {
        return true;
      }
    }
    return false;
  }

  private boolean checkOrganization(
      String accessToken, String organizationsUrl, String organization) {
    try {
      log.debug("Getting user organizations from URL {}", organizationsUrl);

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
    if (requirements.getOrganization() != null
        && (requirements.isIncludePrivateMemberships()
            || details.containsKey("organizations_url"))) {
      boolean orgMatch =
          requirements.isIncludePrivateMemberships()
              ? checkMembership(token, requirements.getOrganization())
              : checkOrganization(
                  token, (String) details.get("organizations_url"), requirements.getOrganization());
      if (!orgMatch) {
        log.debug("User does not include required organization {}", requirements.getOrganization());
        hasRequirements = false;
      }
    }
    return hasRequirements;
  }

  private boolean checkMembership(String accessToken, String organization) {
    try {
      String url = membershipUrl(organization);
      if (url == null) {
        return false;
      }
      log.debug("Getting user membership of organization from URL {}", url);

      HttpHeaders headers = new HttpHeaders();
      headers.set("Authorization", "Bearer " + accessToken);
      headers.set("Accept", MediaType.APPLICATION_JSON_VALUE);

      ResponseEntity<Map> response =
          restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);

      Map<?, ?> membership = response.getBody();
      return membership != null && "active".equals(membership.get("state"));
    } catch (HttpClientErrorException.NotFound e) {
      log.debug("User is not a member of organization {}", organization);
      return false;
    } catch (Exception e) {
      log.warn("Could not fetch user organization membership", e);
      return false;
    }
  }

  private String membershipUrl(String organization) {
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
        .pathSegment("memberships", "orgs", organization)
        .toUriString();
  }

  @Component
  @ConfigurationProperties(
      "spring.security.oauth2.client.registration.github.provider-requirements")
  @Data
  public static class GithubRequirements {
    private String organization;

    /**
     * When true, the organization requirement is checked against the membership of the
     * authenticated user, which includes private memberships and requires the `user` or `read:org`
     * scope. When false, only public memberships are considered.
     */
    private boolean includePrivateMemberships = false;
  }
}
