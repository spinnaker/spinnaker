/*
 * Copyright 2014 Netflix, Inc.
 * Copyright 2023 Apple, Inc.
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

package com.netflix.spinnaker.gate.security.saml;

import com.netflix.spinnaker.fiat.shared.FiatClientConfigurationProperties;
import com.netflix.spinnaker.gate.security.AllowedAccountsSupport;
import com.netflix.spinnaker.gate.services.PermissionService;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.kork.exceptions.ConfigurationException;
import com.netflix.spinnaker.security.User;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import lombok.extern.log4j.Log4j2;
import org.opensaml.saml2.core.Assertion;
import org.opensaml.saml2.core.Attribute;
import org.opensaml.saml2.core.AttributeStatement;
import org.opensaml.xml.schema.XSAny;
import org.opensaml.xml.schema.XSString;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.saml.SAMLCredential;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Log4j2
@Service
@ConditionalOnProperty("saml.enabled")
public class SAMLUserDetailsService
    implements org.springframework.security.saml.userdetails.SAMLUserDetailsService {
  private static final String COUNTER_NAME = "fiat.login";
  private static final Tag TYPE = Tag.of("type", "saml");
  private static final Tag SUCCESS = Tag.of("success", "true");
  private static final Tag FAILURE = Tag.of("success", "false");
  private static final Tag NO_FALLBACK = Tag.of("fallback", "none");

  private final PermissionService permissionService;
  private final AllowedAccountsSupport allowedAccountsSupport;
  private final FiatClientConfigurationProperties fiatClientConfigurationProperties;
  private final SAMLSecurityConfigProperties samlSecurityConfigProperties;
  private final Counter successes;
  private final Counter failures;
  private final RetrySupport retrySupport = new RetrySupport();

  public SAMLUserDetailsService(
      PermissionService permissionService,
      AllowedAccountsSupport allowedAccountsSupport,
      FiatClientConfigurationProperties fiatClientConfigurationProperties,
      SAMLSecurityConfigProperties samlSecurityConfigProperties,
      MeterRegistry meterRegistry) {
    this.permissionService = permissionService;
    this.allowedAccountsSupport = allowedAccountsSupport;
    this.fiatClientConfigurationProperties = fiatClientConfigurationProperties;
    this.samlSecurityConfigProperties = samlSecurityConfigProperties;
    successes = meterRegistry.counter(COUNTER_NAME, Tags.of(TYPE, SUCCESS, NO_FALLBACK));
    failures =
        meterRegistry.counter(
            COUNTER_NAME,
            Tags.of(
                TYPE,
                FAILURE,
                Tag.of(
                    "fallback",
                    Boolean.toString(fiatClientConfigurationProperties.isLegacyFallback()))));
  }

  @Override
  public Object loadUserBySAML(SAMLCredential credential) throws UsernameNotFoundException {
    var assertion = credential.getAuthenticationAssertion();
    var attributes = extractAttributes(assertion);
    var userAttributeMapping = samlSecurityConfigProperties.getUserAttributeMapping();
    @SuppressWarnings("deprecation")
    var user = new User();

    var subjectNameId = assertion.getSubject().getNameID().getValue();
    var emailAttributeValue =
        CollectionUtils.firstElement(attributes.get(userAttributeMapping.getEmail()));
    var email = emailAttributeValue != null ? emailAttributeValue : subjectNameId;
    user.setEmail(email);
    var usernameAttributeValue =
        CollectionUtils.firstElement(attributes.get(userAttributeMapping.getUsername()));
    var username = usernameAttributeValue != null ? usernameAttributeValue : subjectNameId;
    user.setUsername(username);
    var roles = extractRoles(attributes);
    user.setRoles(roles);

    if (!CollectionUtils.isEmpty(samlSecurityConfigProperties.getRequiredRoles())) {
      var requiredRoles = Set.copyOf(samlSecurityConfigProperties.getRequiredRoles());
      // check for at least one common role in both sets
      if (Collections.disjoint(roles, requiredRoles)) {
        throw new BadCredentialsException(
            String.format("User %s is not in any required role from %s", email, requiredRoles));
      }
    }

    Supplier<Void> login =
        () -> {
          permissionService.loginWithRoles(username, roles);
          return null;
        };

    try {
      retrySupport.retry(login, 5, Duration.ofSeconds(2), false);
      log.debug(
          "Successful SAML authentication (user: {}, roleCount: {}, roles: {})",
          username,
          roles.size(),
          roles);
      successes.increment();
    } catch (Exception e) {
      boolean legacyFallback = fiatClientConfigurationProperties.isLegacyFallback();
      log.debug(
          "Unsuccessful SAML authentication (user: {}, roleCount: {}, roles: {}, legacyFallback: {})",
          username,
          roles.size(),
          roles,
          legacyFallback,
          e);
      failures.increment();

      if (!legacyFallback) {
        throw e;
      }
    }

    user.setFirstName(
        CollectionUtils.firstElement(attributes.get(userAttributeMapping.getFirstName())));
    user.setLastName(
        CollectionUtils.firstElement(attributes.get(userAttributeMapping.getLastName())));
    user.setAllowedAccounts(allowedAccountsSupport.filterAllowedAccounts(username, roles));

    return user;
  }

  private Set<String> extractRoles(Map<String, List<String>> attributes) {
    var userAttributeMapping = samlSecurityConfigProperties.getUserAttributeMapping();
    var roleStream =
        attributes.getOrDefault(userAttributeMapping.getRoles(), List.of()).stream()
            .flatMap(roles -> Stream.of(roles.split(userAttributeMapping.getRolesDelimiter())))
            .map(SAMLUserDetailsService::parseRole);
    if (samlSecurityConfigProperties.isForceLowercaseRoles()) {
      roleStream = roleStream.map(String::toLowerCase);
    }
    if (samlSecurityConfigProperties.isSortRoles()) {
      roleStream = roleStream.sorted();
    }
    return roleStream.collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private static Map<String, List<String>> extractAttributes(Assertion assertion) {
    return assertion.getAttributeStatements().stream()
        .flatMap(SAMLUserDetailsService::streamAttributes)
        .collect(
            Collectors.groupingBy(
                Attribute::getName,
                Collectors.flatMapping(
                    SAMLUserDetailsService::streamAttributeValues, Collectors.toList())));
  }

  private static Stream<Attribute> streamAttributes(AttributeStatement statement) {
    return statement.getAttributes().stream();
  }

  private static Stream<String> streamAttributeValues(Attribute attribute) {
    return attribute.getAttributeValues().stream()
        .map(
            object -> {
              if (object instanceof XSString) {
                return ((XSString) object).getValue();
              }
              if (object instanceof XSAny) {
                return ((XSAny) object).getTextContent();
              }
              return null;
            })
        .filter(Objects::nonNull);
  }

  private static String parseRole(String role) {
    if (!role.contains("CN=")) {
      return role;
    }
    try {
      return new LdapName(role)
          .getRdns().stream()
              .filter(rdn -> rdn.getType().equals("CN"))
              .map(rdn -> (String) rdn.getValue())
              .findFirst()
              .orElseThrow(
                  () ->
                      new ConfigurationException(
                          String.format(
                              "SAML role '%s' contains 'CN=' but cannot be parsed as a DN", role)));
    } catch (InvalidNameException e) {
      throw new ConfigurationException(
          String.format("Unable to parse SAML role name '%s'", role), e);
    }
  }
}
