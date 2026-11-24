/*
 * Copyright 2026
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.gate.security.AllowedAccountsSupport;
import com.netflix.spinnaker.gate.services.AuthenticationService;
import com.netflix.spinnaker.security.AllowedAccountsAuthorities;
import com.netflix.spinnaker.security.SpinnakerAuthorities;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.saml2.provider.service.authentication.OpenSaml5AuthenticationProvider.ResponseToken;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal;
import org.springframework.security.saml2.provider.service.authentication.Saml2Authentication;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

class ResponseAuthenticationConverterTest {

  @Test
  void givenUserIdExtractor_whenConverting_thenSetsAllowedAccountsAndUserFields() {
    SecuritySamlProperties properties = new SecuritySamlProperties();
    properties.getUserAttributeMapping().setEmail("email");
    properties.getUserAttributeMapping().setFirstName("first");
    properties.getUserAttributeMapping().setLastName("last");

    Saml2AuthenticatedPrincipal principal =
        new MapBackedSaml2Principal(
            "principalName",
            Map.of(
                "email", List.of("john.doe@test.com"),
                "first", List.of("John"),
                "last", List.of("Doe")));

    Saml2Authentication samlAuthentication = Mockito.mock(Saml2Authentication.class);
    when(samlAuthentication.getPrincipal()).thenReturn(principal);

    AllowedAccountsSupport allowedAccountsSupport = Mockito.mock(AllowedAccountsSupport.class);
    when(allowedAccountsSupport.filterAllowedAccounts(
            eq("extractedUserId"), eq(Set.of("roleA", "roleB"))))
        .thenReturn(List.of("develop", "staging", "production"));

    ObjectFactory<UserIdentifierExtractor> userIdentifierExtractorFactory =
        () -> p -> "extractedUserId";
    ObjectFactory<UserRolesExtractor> userRolesExtractorFactory =
        () -> p -> Set.of("roleA", "roleB");

    AuthenticationService authenticationService = Mockito.mock(AuthenticationService.class);
    Collection<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
    Mockito.doReturn(authorities)
        .when(authenticationService)
        .loginWithRoles(eq("extractedUserId"), any());

    ResponseAuthenticationConverter converter =
        new TestableResponseAuthenticationConverter(
            properties,
            () -> allowedAccountsSupport,
            userIdentifierExtractorFactory,
            userRolesExtractorFactory,
            () -> authenticationService,
            samlAuthentication);

    PreAuthenticatedAuthenticationToken token =
        converter.convert(Mockito.mock(ResponseToken.class));

    assertThat(token.getName()).isEqualTo("extractedUserId");
    assertThat(token.getPrincipal()).isInstanceOf(OAuth2AuthenticatedPrincipal.class);
    OAuth2AuthenticatedPrincipal user = (OAuth2AuthenticatedPrincipal) token.getPrincipal();
    assertThat(user.<String>getAttribute("email")).isEqualTo("john.doe@test.com");
    assertThat(user.<String>getAttribute("given_name")).isEqualTo("John");
    assertThat(user.<String>getAttribute("family_name")).isEqualTo("Doe");
    assertThat(SpinnakerAuthorities.getRoleNames(token)).contains("roleA", "roleB");
    assertThat(AllowedAccountsAuthorities.getAllowedAccountNames(token))
        .containsExactlyInAnyOrder("develop", "production", "staging");

    verify(allowedAccountsSupport)
        .filterAllowedAccounts("extractedUserId", Set.of("roleA", "roleB"));
    verify(authenticationService).loginWithRoles(eq("extractedUserId"), any());
  }

  @Test
  void givenRequiredRolesConfigured_whenUserHasNone_thenThrowsBadCredentialsException() {
    SecuritySamlProperties properties = new SecuritySamlProperties();
    properties.getUserAttributeMapping().setEmail("email");
    properties.setRequiredRoles(List.of("required"));

    Saml2AuthenticatedPrincipal principal =
        new MapBackedSaml2Principal("principalName", Map.of("email", List.of("user@example.com")));

    Saml2Authentication samlAuthentication = Mockito.mock(Saml2Authentication.class);
    when(samlAuthentication.getPrincipal()).thenReturn(principal);

    ResponseAuthenticationConverter converter =
        new TestableResponseAuthenticationConverter(
            properties,
            () -> Mockito.mock(AllowedAccountsSupport.class),
            () -> p -> "extractedUserId",
            () -> p -> Set.of("other"),
            () -> Mockito.mock(AuthenticationService.class),
            samlAuthentication);

    assertThatThrownBy(() -> converter.convert(Mockito.mock(ResponseToken.class)))
        .isInstanceOf(BadCredentialsException.class)
        .hasMessageContaining("not in any required role");
  }

  static final class MapBackedSaml2Principal implements Saml2AuthenticatedPrincipal {
    private final String name;
    private final Map<String, List<Object>> attributes;

    MapBackedSaml2Principal(String name, Map<String, List<Object>> attributes) {
      this.name = name;
      @SuppressWarnings({"unchecked", "rawtypes"})
      Map<String, List<Object>> castAttributes = (Map) attributes;
      this.attributes = castAttributes;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public Map<String, List<Object>> getAttributes() {
      return attributes;
    }
  }

  static final class TestableResponseAuthenticationConverter
      extends ResponseAuthenticationConverter {
    private final Saml2Authentication authentication;

    TestableResponseAuthenticationConverter(
        SecuritySamlProperties properties,
        ObjectFactory<AllowedAccountsSupport> allowedAccountsSupport,
        ObjectFactory<UserIdentifierExtractor> userIdentifierExtractorFactory,
        ObjectFactory<UserRolesExtractor> userRolesExtractorFactory,
        ObjectFactory<AuthenticationService> authenticationServiceFactory,
        Saml2Authentication authentication) {
      super(
          properties,
          userIdentifierExtractorFactory,
          userRolesExtractorFactory,
          authenticationServiceFactory,
          allowedAccountsSupport);
      this.authentication = authentication;
    }

    @Override
    Saml2Authentication convertToken(ResponseToken source) {
      return authentication;
    }
  }
}
