/*
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
 *
 */

package com.netflix.spinnaker.gate.security.saml;

import com.netflix.spinnaker.kork.exceptions.ConfigurationException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import lombok.RequiredArgsConstructor;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal;

/**
 * Default implementation for extracting roles from an authenticated SAML user. This uses the
 * settings in {@link SecuritySamlProperties} related to roles. If role names appear to be
 * distinguished names (i.e., they contain the substring {@code CN=}), then they will be parsed as
 * DNs to extract the common name (CN) attribute.
 */
@RequiredArgsConstructor
public class DefaultUserRolesExtractor implements UserRolesExtractor {
  private final SecuritySamlProperties properties;

  @Override
  public Set<String> getRoles(Saml2AuthenticatedPrincipal principal) {
    var userAttributeMapping = properties.getUserAttributeMapping();
    List<String> roles = principal.getAttribute(userAttributeMapping.getRoles());
    Stream<String> roleStream = roles != null ? roles.stream() : Stream.empty();
    String delimiter = userAttributeMapping.getRolesDelimiter();
    roleStream =
        delimiter != null
            ? roleStream.flatMap(role -> Stream.of(role.split(delimiter)))
            : roleStream;
    roleStream = roleStream.map(DefaultUserRolesExtractor::parseRole);
    if (properties.isForceLowercaseRoles()) {
      roleStream = roleStream.map(role -> role.toLowerCase(Locale.ROOT));
    }
    if (properties.isSortRoles()) {
      roleStream = roleStream.sorted();
    }
    return roleStream.collect(Collectors.toCollection(LinkedHashSet::new));
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
