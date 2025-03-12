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
package com.netflix.spinnaker.gate.security.oauth2;

import static net.logstash.logback.argument.StructuredArguments.entries;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.fiat.shared.FiatClientConfigurationProperties;
import com.netflix.spinnaker.gate.security.AllowedAccountsSupport;
import com.netflix.spinnaker.gate.security.oauth2.provider.SpinnakerProviderTokenServices;
import com.netflix.spinnaker.gate.services.CredentialsService;
import com.netflix.spinnaker.gate.services.PermissionService;
import com.netflix.spinnaker.gate.services.internal.Front50Service;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerServerException;
import com.netflix.spinnaker.security.User;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.security.oauth2.resource.ResourceServerProperties;
import org.springframework.boot.autoconfigure.security.oauth2.resource.UserInfoTokenServices;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.common.exceptions.InvalidTokenException;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.OAuth2Request;
import org.springframework.security.oauth2.provider.token.ResourceServerTokenServices;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

/**
 * ResourceServerTokenServices is an interface used to manage access tokens. The
 * UserInfoTokenService object is an implementation of that interface that uses an access token to
 * get the logged in user's data (such as email or profile). We want to customize the Authentication
 * object that is returned to include our custom (Kork) User.
 */
@Slf4j
public class SpinnakerUserInfoTokenServices implements ResourceServerTokenServices {

  private final ResourceServerProperties sso;
  private final UserInfoTokenServices userInfoTokenServices;
  private final CredentialsService credentialsService;
  private final OAuth2SsoConfig.UserInfoMapping userInfoMapping;
  private final OAuth2SsoConfig.UserInfoRequirements userInfoRequirements;
  private final PermissionService permissionService;
  private final Front50Service front50Service;
  private final SpinnakerProviderTokenServices providerTokenServices;

  private final AllowedAccountsSupport allowedAccountsSupport;
  private final FiatClientConfigurationProperties fiatClientConfigurationProperties;
  private final Registry registry;

  @Autowired(required = false)
  @Qualifier("spinnaker-oauth2-group-extractor")
  private BiFunction<String, Map, List<String>> groupExtractor;

  private RetrySupport retrySupport = new RetrySupport();

  @Autowired
  public SpinnakerUserInfoTokenServices(
      ResourceServerProperties sso,
      UserInfoTokenServices userInfoTokenServices,
      CredentialsService credentialsService,
      OAuth2SsoConfig.UserInfoMapping userInfoMapping,
      OAuth2SsoConfig.UserInfoRequirements userInfoRequirements,
      PermissionService permissionService,
      Front50Service front50Service,
      Optional<SpinnakerProviderTokenServices> providerTokenServices,
      AllowedAccountsSupport allowedAccountsSupport,
      FiatClientConfigurationProperties fiatClientConfigurationProperties,
      Registry registry) {
    this.sso = sso;
    this.userInfoTokenServices = userInfoTokenServices;
    this.credentialsService = credentialsService;
    this.userInfoMapping = userInfoMapping;
    this.userInfoRequirements = userInfoRequirements;
    this.permissionService = permissionService;
    this.front50Service = front50Service;
    this.providerTokenServices = providerTokenServices.orElse(null);
    this.allowedAccountsSupport = allowedAccountsSupport;
    this.fiatClientConfigurationProperties = fiatClientConfigurationProperties;
    this.registry = registry;
  }

  @Override
  public OAuth2Authentication loadAuthentication(final String accessToken)
      throws AuthenticationException, InvalidTokenException {
    OAuth2Authentication oAuth2Authentication =
        userInfoTokenServices.loadAuthentication(accessToken);

    final Map<String, Object> details =
        (Map<String, Object>) oAuth2Authentication.getUserAuthentication().getDetails();

    if (log.isDebugEnabled()) {
      log.debug("UserInfo details: " + entries(details));
    }

    boolean isServiceAccount = isServiceAccount(details);
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

    final String username = toStringOrNull(details.get(userInfoMapping.getUsername()));
    List<String> roles =
        Optional.ofNullable(groupExtractor)
            .map(extractor -> extractor.apply(accessToken, details))
            .orElseGet(
                () -> Optional.ofNullable(getRoles(details)).orElse(Collections.emptyList()));
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
              return "Successfully Logged in" + username;
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

    User spinnakerUser = new User();
    spinnakerUser.setEmail(toStringOrNull(details.get(userInfoMapping.getEmail())));
    spinnakerUser.setFirstName(toStringOrNull(details.get(userInfoMapping.getFirstName())));
    spinnakerUser.setLastName(toStringOrNull(details.get(userInfoMapping.getLastName())));
    spinnakerUser.setAllowedAccounts(allowedAccountsSupport.filterAllowedAccounts(username, roles));
    spinnakerUser.setRoles(roles);
    spinnakerUser.setUsername(username);

    PreAuthenticatedAuthenticationToken authentication =
        new PreAuthenticatedAuthenticationToken(
            spinnakerUser, null, spinnakerUser.getAuthorities());

    // impl copied from UserInfoTokenServices
    OAuth2Request storedRequest =
        new OAuth2Request(null, sso.getClientId(), null, true, null, null, null, null, null);

    return new OAuth2Authentication(storedRequest, authentication);
  }

  /**
   * Safely converts an object to a string representation.
   *
   * <p>This method checks if the provided object is non-null before calling {@code toString()}. If
   * the object is {@code null}, it returns {@code null} instead of throwing a {@code
   * NullPointerException}.
   *
   * @param o the object to convert to a string, may be {@code null}
   * @return the string representation of the object, or {@code null} if the object is {@code null}
   */
  private String toStringOrNull(Object o) {
    return o != null ? o.toString() : null;
  }

  @Override
  public OAuth2AccessToken readAccessToken(String accessToken) {
    return userInfoTokenServices.readAccessToken(accessToken);
  }

  protected boolean isServiceAccount(Map<String, Object> details) {
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

  public boolean hasAllUserInfoRequirements(Map<String, Object> details) {
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

  public static boolean isRegexExpression(String val) {
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

  public static String mutateRegexPattern(String val) {
    // "/expr/" -> "expr"
    return val.substring(1, val.length() - 1);
  }

  protected List<String> getRoles(Map<String, Object> details) {
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
