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

import java.util.Collection;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AuthenticationDetailsSource;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedGrantedAuthoritiesWebAuthenticationDetails;

/**
 * Provide details for pre-authenticated tokens. See
 * https://bwgjoseph.com/spring-security-custom-pre-authentication-flow for background.
 */
public class HeaderAuthenticationDetailsSource
    implements AuthenticationDetailsSource<
        HttpServletRequest, PreAuthenticatedGrantedAuthoritiesWebAuthenticationDetails> {

  @Override
  public PreAuthenticatedGrantedAuthoritiesWebAuthenticationDetails buildDetails(
      HttpServletRequest request) {

    // TODO: get authorities from fiat
    Collection<GrantedAuthority> authorities = Set.of();

    return new PreAuthenticatedGrantedAuthoritiesWebAuthenticationDetails(request, authorities);
  }
}
