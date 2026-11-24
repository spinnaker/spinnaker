/*
 * Copyright 2026 Wise, PLC.
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

package com.netflix.spinnaker.gate.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.ldap.userdetails.InetOrgPerson;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;

class AuthUserResponseTest {

  private static AuthUserResponse from(Authentication authentication) {
    return AuthUserResponse.from(authentication, List.of(), false, false, null, null);
  }

  @Test
  void readsProfileFromStandardClaimsOfAnAttributedPrincipal() {
    List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("dev"));
    var principal =
        new DefaultOAuth2AuthenticatedPrincipal(
            "jdoe",
            Map.of("email", "jdoe@example.com", "given_name", "Jane", "family_name", "Doe"),
            authorities);

    AuthUserResponse response =
        from(new UsernamePasswordAuthenticationToken(principal, "", authorities));

    assertThat(response.username()).isEqualTo("jdoe");
    assertThat(response.email()).isEqualTo("jdoe@example.com");
    assertThat(response.firstName()).isEqualTo("Jane");
    assertThat(response.lastName()).isEqualTo("Doe");
  }

  @Test
  void readsProfileFromAnLdapInetOrgPerson() {
    var essence = new InetOrgPerson.Essence();
    essence.setDn("uid=jdoe,ou=people");
    essence.setUsername("jdoe");
    essence.setPassword("");
    essence.setMail("jdoe@example.com");
    essence.setGivenName("Jane");
    essence.setSn("Doe");
    essence.setCn(new String[] {"Jane Doe"});
    var person = (InetOrgPerson) essence.createUserDetails();

    AuthUserResponse response =
        from(new UsernamePasswordAuthenticationToken(person, "", person.getAuthorities()));

    assertThat(response.email()).isEqualTo("jdoe@example.com");
    assertThat(response.firstName()).isEqualTo("Jane");
    assertThat(response.lastName()).isEqualTo("Doe");
  }

  @Test
  void fallsBackToTheUsernameForPrincipalsWithoutAProfile() {
    var user = new User("jdoe", "", List.of());

    AuthUserResponse response =
        from(new UsernamePasswordAuthenticationToken(user, "", user.getAuthorities()));

    assertThat(response.email()).isEqualTo("jdoe");
    assertThat(response.firstName()).isNull();
    assertThat(response.lastName()).isNull();
  }
}
