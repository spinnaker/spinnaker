/*
 * Copyright 2026 Harness, Inc.
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

package com.netflix.spinnaker.filters;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.kork.common.Header;
import com.netflix.spinnaker.security.AllowedAccountsAuthorities;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticatedPrincipal;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;

class AuthenticatedRequestFilterTest {

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    AuthenticatedRequest.clear();
  }

  @Test
  void derivesUserAndAccountsFromAuthenticationWithoutUserDetailsPrincipal() throws Exception {
    List<GrantedAuthority> authorities = new ArrayList<>();
    authorities.add(AuthorityUtils.createAuthorityList("some-role").get(0));
    authorities.addAll(AllowedAccountsAuthorities.buildAllowedAccounts(List.of("prod", "test")));
    AuthenticatedPrincipal principal = () -> "oauth-user";
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, authorities));

    var seen = filter();

    assertThat(seen.user.get()).contains("oauth-user");
    assertThat(seen.accounts.get()).contains("prod,test");
  }

  @Test
  void anonymousTokenWithStringPrincipalHasNoIdentity() throws Exception {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

    var seen = filter();

    assertThat(seen.user.get()).isEmpty();
  }

  @Test
  void anonymousTokenWithUserPrincipalKeepsItsIdentityAndAccounts() throws Exception {
    Collection<GrantedAuthority> authorities =
        new ArrayList<>(AllowedAccountsAuthorities.buildAllowedAccounts(List.of("anon-account")));
    authorities.add(AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS").get(0));
    SecurityContextHolder.getContext()
        .setAuthentication(
            new AnonymousAuthenticationToken(
                "key", new User("anonymous", "", authorities), authorities));

    var seen = filter();

    assertThat(seen.user.get()).contains("anonymous");
    assertThat(seen.accounts.get()).contains("anon-account");
  }

  private record Seen(
      AtomicReference<Optional<String>> user, AtomicReference<Optional<String>> accounts) {}

  private Seen filter() throws Exception {
    var user = new AtomicReference<Optional<String>>();
    var accounts = new AtomicReference<Optional<String>>();
    new AuthenticatedRequestFilter()
        .doFilter(
            new MockHttpServletRequest(),
            new MockHttpServletResponse(),
            new MockFilterChain() {
              @Override
              public void doFilter(ServletRequest request, ServletResponse response)
                  throws IOException, ServletException {
                user.set(AuthenticatedRequest.get(Header.USER));
                accounts.set(AuthenticatedRequest.get(Header.ACCOUNTS));
              }
            });
    return new Seen(user, accounts);
  }
}
