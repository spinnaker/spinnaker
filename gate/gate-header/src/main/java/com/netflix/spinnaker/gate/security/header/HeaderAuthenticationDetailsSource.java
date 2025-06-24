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

import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AuthenticationDetailsSource;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedGrantedAuthoritiesWebAuthenticationDetails;

/**
 * Provide details for pre-authenticated tokens. See
 * https://bwgjoseph.com/spring-security-custom-pre-authentication-flow for background.
 */
public class HeaderAuthenticationDetailsSource
    implements AuthenticationDetailsSource<
        HttpServletRequest, PreAuthenticatedGrantedAuthoritiesWebAuthenticationDetails> {
  /**
   * As header authentication is currently (13-jun-25) configured in gate,
   * AbstractPreAuthenticatedProcessingFilter.doAuthenticate calls buildDetails with the request,
   * and stores the return value in the details of an unauthenticated
   * PreAuthenticatedAuthenticationToken before calling
   * PreAuthenticatedAuthenticationProvider.authenticate (and so before
   * HeaderAuthenticationUserDetailsService.createUserDetails.
   */
  @Override
  public PreAuthenticatedGrantedAuthoritiesWebAuthenticationDetails buildDetails(
      HttpServletRequest request) {
    // Note that there's a choice about whether to call fiat here, or in
    // HeaderAuthenticationUserDetailsService.createUserDetails.  To try to act
    // more like the gate-oauth module (see
    // SpinnakerUserInfoTokenServices.loadAuthentication), let's leave
    // authorities empty here, and leave it to
    // HeaderAuthenticationUserDetailsService.createUserDetails to build a kork
    // User object with the appropriate roles + allowedAccounts from which it
    // derives authorities.  If we ever migrate to using GrantedAuthorities and
    // can ditch the kork User object, the logic to call fiat likely moves here.
    return new PreAuthenticatedGrantedAuthoritiesWebAuthenticationDetails(request, Set.of());
  }
}
