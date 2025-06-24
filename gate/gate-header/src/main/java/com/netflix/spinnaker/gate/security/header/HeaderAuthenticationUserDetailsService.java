/*
 * Copyright 2025 Salesforce, Inc.
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

package com.netflix.spinnaker.gate.security.header;

import com.netflix.spinnaker.gate.security.AllowedAccountsSupport;
import com.netflix.spinnaker.gate.services.PermissionService;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.security.User;
import java.time.Duration;
import java.util.Collection;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedGrantedAuthoritiesUserDetailsService;

/**
 * Provide details for pre-authenticated tokens using kork-security's deprecated User class. See
 * HeaderAuthConfig.authenticationProvider for background. If gate stops using this class, perhaps
 * replacing it with spring-security-core's User (or the UserDetails interface where sufficient),
 * this class can likely disappear. This also means teaching code that uses roles and
 * allowedAccounts from User to work with collections of GrantedAuthority instead.
 */
public class HeaderAuthenticationUserDetailsService
    extends PreAuthenticatedGrantedAuthoritiesUserDetailsService {

  private final PermissionService permissionService;

  private final AllowedAccountsSupport allowedAccountsSupport;

  private final RetrySupport retrySupport = new RetrySupport();

  public HeaderAuthenticationUserDetailsService(
      PermissionService permissionService, AllowedAccountsSupport allowedAccountsSupport) {
    super();
    this.permissionService = permissionService;
    this.allowedAccountsSupport = allowedAccountsSupport;
  }

  /**
   * As header authentication is currently (13-jun-25) configured in gate,
   * PreAuthenticatedAuthenticationProvider.authenticate calls this via
   * PreAuthenticatedGrantedAuthoritiesUserDetailsService.loadUserDetails.
   *
   * @param token an unauthenticated PreAuthenticatedAuthenticationToken constructed by
   *     AbstractPreAuthenticatedProcessingFilter.doAuthenticate
   * @param authorities authorities from the token. These come from
   *     HeaderAuthenticationDetailsSource.buildDetails. Currently ignored as authorities are
   *     derived from roles in User objects.
   * @return a kork User object that PreAuthenticatedAuthenticationProvider.authenticate uses to
   *     build an authenticated PreAuthenticatedAuthenticationToken
   */
  @Override
  protected UserDetails createUserDetails(
      Authentication token, Collection<? extends GrantedAuthority> authorities) {

    // Log the user in.  This invalidates the cache in gate, and logs the user
    // in to fiat.  What this actually means is:
    // - load roles for this user from fiat's provider
    // - persist the permissions for this user
    //
    // This way, subsequent calls to fiat to retrieve permissions for the user
    // are guaranteed to get them.  Without this, there are potentially races
    // with role syncing in fiat.
    retrySupport.retry(
        () -> {
          permissionService.login(token.getName());
          return null;
        },
        5,
        Duration.ofMillis(2000),
        false);

    User user = new User();

    // Part of UserDetails
    user.setEmail(token.getName());

    // Neither firstName nor lastName are available in header auth (i.e. via
    // X-SPINNAKER-USER).

    // authorities are part of UserDetails, but they're derived from roles for
    // User.  There's no setAuthorities method.

    // Specific to User

    // roles aren't available in header auth, so don't bother setting them, and
    // pass an empty collection to filterAllowedAccounts
    user.setAllowedAccounts(
        allowedAccountsSupport.filterAllowedAccounts(token.getName(), Set.of() /* roles */));

    return user;
  }
}
