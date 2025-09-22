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

package com.netflix.spinnaker.gate.security.oauth2;

import static net.logstash.logback.argument.StructuredArguments.entries;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.fiat.shared.FiatClientConfigurationProperties;
import com.netflix.spinnaker.gate.security.AllowedAccountsSupport;
import com.netflix.spinnaker.gate.security.oauth2.provider.SpinnakerProviderTokenServices;
import com.netflix.spinnaker.gate.services.PermissionService;
import com.netflix.spinnaker.gate.services.internal.Front50Service;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerServerException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

/**
 * A helper class to handle common user loading logic for both OAuth2 and OIDC authentication. This
 * class extracts shared code to avoid duplication in OAuth2 and OIDC user loading processes.
 */
@Component
@Slf4j
public class OAuthUserInfoServiceHelper {

  private final OAuth2SsoConfig.UserInfoMapping userInfoMapping;

  private final OAuth2SsoConfig.UserInfoRequirements userInfoRequirements;

  private final PermissionService permissionService;

  private final Front50Service front50Service;

  private final AllowedAccountsSupport allowedAccountsSupport;

  private final FiatClientConfigurationProperties fiatClientConfigurationProperties;

  private final Registry registry;

  private SpinnakerProviderTokenServices providerTokenServices;

  private final RetrySupport retrySupport = new RetrySupport();

  @Autowired
  public OAuthUserInfoServiceHelper(
      OAuth2SsoConfig.UserInfoMapping userInfoMapping,
      OAuth2SsoConfig.UserInfoRequirements userInfoRequirements,
      PermissionService permissionService,
      Front50Service front50Service,
      AllowedAccountsSupport allowedAccountsSupport,
      FiatClientConfigurationProperties fiatClientConfigurationProperties,
      Registry registry,
      Optional<SpinnakerProviderTokenServices> providerTokenServices) {
    this.userInfoMapping = userInfoMapping;
    this.userInfoRequirements = userInfoRequirements;
    this.permissionService = permissionService;
    this.front50Service = front50Service;
    this.allowedAccountsSupport = allowedAccountsSupport;
    this.fiatClientConfigurationProperties = fiatClientConfigurationProperties;
    this.registry = registry;
    this.providerTokenServices = providerTokenServices.orElse(null);
  }

  /**
   * Builds a {@link SpinnakerOAuth2User} from the given {@link OAuth2User} and its {@link
   * OAuth2UserRequest}.
   *
   * <p>This method extracts user identity information (email, first name, last name, username,
   * roles) from the provider's user attributes according to the configured {@link
   * OAuth2SsoConfig.UserInfoMapping}. It also applies account filtering through {@link
   * AllowedAccountsSupport}, validates user info requirements, and triggers a login in Fiat to
   * establish authorization context.
   *
   * @param oAuth2User the raw user object returned by the OAuth2 provider
   * @param userRequest the associated user request, including access token and client registration
   * @return a {@link SpinnakerOAuth2User} containing normalized user identity, roles, and allowed
   *     accounts
   * @throws org.springframework.security.authentication.BadCredentialsException if the user's
   *     attributes do not meet required constraints
   */
  OAuth2User getSpinnakerOAuth2User(OAuth2User oAuth2User, OAuth2UserRequest userRequest) {
    Map<String, Object> details = oAuth2User.getAttributes();

    ResolvedUserInfo userInfo = getUserInfo(details, userRequest);

    return new SpinnakerOAuth2User(
        Objects.toString(details.get(userInfoMapping.getEmail()), null),
        Objects.toString(details.get(userInfoMapping.getFirstName()), null),
        Objects.toString(details.get(userInfoMapping.getLastName()), null),
        allowedAccountsSupport.filterAllowedAccounts(userInfo.username(), userInfo.roles()),
        userInfo.roles(),
        userInfo.username(),
        details,
        oAuth2User.getAuthorities());
  }

  /**
   * Builds a {@link SpinnakerOIDCUser} from the given {@link OidcUser} and its {@link
   * OAuth2UserRequest}.
   *
   * <p>This method extracts identity information (email, first name, last name, username, roles)
   * from the OIDC claims and user attributes, applies account filtering through {@link
   * AllowedAccountsSupport}, and triggers a Fiat login to establish authorization context. Unlike
   * {@link #getSpinnakerOAuth2User}, this method also preserves the OIDC-specific {@link
   * org.springframework.security.oauth2.core.oidc.OidcIdToken} and {@link
   * org.springframework.security.oauth2.core.oidc.OidcUserInfo}.
   *
   * @param oidcUser the raw user object returned by the OIDC provider, including ID token and user
   *     info claims
   * @param userRequest the associated user request, including access token and client registration
   * @return a {@link SpinnakerOIDCUser} containing normalized user identity, roles, allowed
   *     accounts, ID token, and OIDC user info
   * @throws org.springframework.security.authentication.BadCredentialsException if the user's
   *     claims do not meet required constraints
   */
  OidcUser getSpinnakerOIDCUser(OidcUser oidcUser, OAuth2UserRequest userRequest) {
    Map<String, Object> details = oidcUser.getAttributes();

    ResolvedUserInfo userInfo = getUserInfo(details, userRequest);

    return new SpinnakerOIDCUser(
        Objects.toString(details.get(userInfoMapping.getEmail()), null),
        Objects.toString(details.get(userInfoMapping.getFirstName()), null),
        Objects.toString(details.get(userInfoMapping.getLastName()), null),
        allowedAccountsSupport.filterAllowedAccounts(userInfo.username(), userInfo.roles()),
        userInfo.roles(),
        userInfo.username(),
        oidcUser.getIdToken(),
        oidcUser.getUserInfo(),
        details,
        oidcUser.getAuthorities());
  }

  /** Handles validation, login, and metric tracking for a given OAuth2/OIDC user. */
  private ResolvedUserInfo getUserInfo(Map<String, Object> details, OAuth2UserRequest userRequest) {
    if (log.isDebugEnabled()) {
      log.debug("UserInfo details: " + entries(details));
    }

    boolean isServiceAccount = isServiceAccount(details);
    String accessToken = userRequest.getAccessToken().getTokenValue();

    if (!isServiceAccount) {
      if (!hasAllUserInfoRequirements(details)) {
        throw new BadCredentialsException("User's info does not have all required fields.");
      }
      if (providerTokenServices != null
          && !providerTokenServices.hasAllProviderRequirements(accessToken, details)) {
        throw new BadCredentialsException(
            "User's provider info does not have all required fields.");
      }
    }

    final String username = Objects.toString(details.get(userInfoMapping.getUsername()), null);
    List<String> roles = Optional.ofNullable(getRoles(details)).orElse(Collections.emptyList());
    // Service accounts are already logged in.
    if (!isServiceAccount) {
      var id = registry.createId("fiat.login").withTag("type", "oauth2");

      try {
        retrySupport.retry(
            () -> {
              if (roles.isEmpty()) {
                permissionService.login(username);
              } else {
                permissionService.loginWithRoles(username, roles);
              }
              return "Successfully Logged in " + username;
            },
            5,
            Duration.ofSeconds(2),
            false);
        log.debug(
            "Successful oauth2 authentication (user: {}, roleCount: {}, roles: {})",
            username,
            roles.size(),
            roles);
        id = id.withTag("success", true).withTag("fallback", "none");
      } catch (Exception e) {
        log.debug(
            "Unsuccessful oauth2 authentication (user: {}, roleCount: {}, roles: {}, legacyFallback: {})",
            username,
            roles.size(),
            roles,
            fiatClientConfigurationProperties.isLegacyFallback(),
            e);
        id =
            id.withTag("success", false)
                .withTag("fallback", fiatClientConfigurationProperties.isLegacyFallback());

        if (!fiatClientConfigurationProperties.isLegacyFallback()) {
          throw e;
        }
      } finally {
        registry.counter(id).increment();
      }
    }

    return new ResolvedUserInfo(username, roles);
  }

  /** Holds the resolved user identity information after authentication flow. */
  private record ResolvedUserInfo(String username, List<String> roles) {}

  boolean isServiceAccount(Map<String, Object> details) {
    String email = (String) details.get(userInfoMapping.getServiceAccountEmail());
    if (email == null || !permissionService.isEnabled()) {
      return false;
    }
    try {
      return Optional.ofNullable(Retrofit2SyncCall.execute(front50Service.getServiceAccounts()))
          .orElse(new ArrayList<>())
          .stream()
          .anyMatch(sa -> email.equalsIgnoreCase(sa.getName()));
    } catch (SpinnakerServerException e) {
      log.warn("Could not get list of service accounts.", e);
    }
    return false;
  }

  private static boolean valueMatchesConstraint(Object value, String requiredVal) {
    if (value == null) {
      return false;
    }

    if (isRegexExpression(requiredVal)) {
      return String.valueOf(value).matches(mutateRegexPattern(requiredVal));
    }

    return value.equals(requiredVal);
  }

  boolean hasAllUserInfoRequirements(Map<String, Object> details) {
    if (userInfoRequirements == null || userInfoRequirements.isEmpty()) {
      return true;
    }

    Map<String, String> invalidFields =
        userInfoRequirements.entrySet().stream()
            .filter(
                entry -> {
                  String reqKey = entry.getKey();
                  String reqVal = entry.getValue();
                  Object value = details.get(reqKey);

                  if (value instanceof Collection<?> collection) {
                    return collection.stream()
                        .noneMatch(item -> valueMatchesConstraint(item, reqVal));
                  }
                  return !valueMatchesConstraint(value, reqVal);
                })
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    if (!invalidFields.isEmpty() && log.isDebugEnabled()) {
      log.debug(
          "Invalid userInfo response: "
              + invalidFields.entrySet().stream()
                  .map(
                      entry ->
                          "got "
                              + entry.getKey()
                              + "="
                              + details.get(entry.getKey())
                              + ", wanted "
                              + entry.getValue())
                  .collect(Collectors.joining(", ")));
    }

    return invalidFields.isEmpty();
  }

  private static boolean isRegexExpression(String val) {
    if (val.startsWith("/") && val.endsWith("/")) {
      try {
        Pattern.compile(val);
        return true;
      } catch (PatternSyntaxException ignored) {
        return false;
      }
    }

    return false;
  }

  private static String mutateRegexPattern(String val) {
    // "/expr/" -> "expr"
    return val.substring(1, val.length() - 1);
  }

  List<String> getRoles(Map<String, Object> details) {
    if (userInfoMapping == null || userInfoMapping.getRoles() == null) {
      return List.of();
    }

    Object roles = Optional.ofNullable(details.get(userInfoMapping.getRoles())).orElse(List.of());
    if (roles instanceof Collection<?> collection) {
      // Some providers (Azure AD) return roles in this format: ["[\"role-1\", \"role-2\"]"]
      if (!collection.isEmpty()
          && collection.iterator().next() instanceof String firstRole
          && firstRole.startsWith("[")) {
        return parseJsonRoles(firstRole);
      }
      return collection.stream().map(Object::toString).collect(Collectors.toList());
    }

    if (roles instanceof String roleString) {
      if (roleString.trim().isEmpty()) {
        return new ArrayList<>();
      }

      return Arrays.asList(roleString.split("[, ]+"));
    }
    log.warn("unsupported roles value in details, type: {}, value: {}", roles.getClass(), roles);
    return List.of();
  }

  private List<String> parseJsonRoles(String jsonString) {
    ObjectMapper objectMapper = new ObjectMapper();
    try {
      return objectMapper.readValue(jsonString, List.class);
    } catch (JsonProcessingException e) {
      log.warn("Failed to parse JSON roles: {}", jsonString, e);
      return List.of();
    }
  }
}
