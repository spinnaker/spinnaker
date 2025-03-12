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

import com.netflix.spinnaker.gate.services.AuthenticationService;
import com.netflix.spinnaker.security.User;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.saml2.provider.service.authentication.OpenSaml4AuthenticationProvider;
import org.springframework.security.saml2.provider.service.authentication.OpenSaml4AuthenticationProvider.ResponseToken;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal;
import org.springframework.security.saml2.provider.service.authentication.Saml2Authentication;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.util.CollectionUtils;

/** Handles conversion of an authenticated SAML user into a Spinnaker user and populating Fiat. */
@Log4j2
@RequiredArgsConstructor
public class ResponseAuthenticationConverter
    implements Converter<ResponseToken, PreAuthenticatedAuthenticationToken> {
  private final SecuritySamlProperties properties;
  private final ObjectFactory<UserIdentifierExtractor> userIdentifierExtractorFactory;
  private final ObjectFactory<UserRolesExtractor> userRolesExtractorFactory;
  private final ObjectFactory<AuthenticationService> authenticationServiceFactory;

  @Override
  public PreAuthenticatedAuthenticationToken convert(ResponseToken source) {
    UserIdentifierExtractor userIdentifierExtractor = userIdentifierExtractorFactory.getObject();
    UserRolesExtractor userRolesExtractor = userRolesExtractorFactory.getObject();
    AuthenticationService loginService = authenticationServiceFactory.getObject();
    log.debug("Decoding SAML response: {}", source.getToken());

    Saml2Authentication authentication = convertToken(source);
    @SuppressWarnings("deprecation")
    var user = new User();
    Saml2AuthenticatedPrincipal principal =
        (Saml2AuthenticatedPrincipal) authentication.getPrincipal();
    String principalName = principal.getName();
    var userAttributeMapping = properties.getUserAttributeMapping();
    String email = principal.getFirstAttribute(userAttributeMapping.getEmail());
    user.setEmail(email != null ? email : principalName);
    String userid = userIdentifierExtractor.fromPrincipal(principal);
    user.setUsername(userid);
    user.setFirstName(principal.getFirstAttribute(userAttributeMapping.getFirstName()));
    user.setLastName(principal.getFirstAttribute(userAttributeMapping.getLastName()));

    Set<String> roles = userRolesExtractor.getRoles(principal);
    user.setRoles(roles);

    if (!CollectionUtils.isEmpty(properties.getRequiredRoles())) {
      var requiredRoles = Set.copyOf(properties.getRequiredRoles());
      // check for at least one common role in both sets
      if (Collections.disjoint(roles, requiredRoles)) {
        throw new BadCredentialsException(
            String.format("User %s is not in any required role from %s", email, requiredRoles));
      }
    }

    Collection<? extends GrantedAuthority> authorities = loginService.loginWithRoles(userid, roles);
    return new PreAuthenticatedAuthenticationToken(user, principal, authorities);
  }

  private static final Converter<ResponseToken, Saml2Authentication> DEFAULT_CONVERTER =
      OpenSaml4AuthenticationProvider.createDefaultResponseAuthenticationConverter();

  private static Saml2Authentication convertToken(ResponseToken token) {
    Saml2Authentication authentication = DEFAULT_CONVERTER.convert(token);
    if (authentication == null) {
      throw new IllegalArgumentException("Response token could not be converted");
    }
    return authentication;
  }
}
