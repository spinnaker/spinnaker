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

import com.netflix.spinnaker.kork.common.Header;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationProvider;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedGrantedAuthoritiesUserDetailsService;
import org.springframework.security.web.authentication.preauth.RequestHeaderAuthenticationFilter;

/** See https://bwgjoseph.com/spring-security-custom-pre-authentication-flow for background. */
@ConditionalOnProperty("header.enabled")
@Configuration
public class HeaderAuthConfig {

  @Bean
  public RequestHeaderAuthenticationFilter requestHeaderAuthenticationFilter(
      AuthenticationManager authenticationManager) {
    RequestHeaderAuthenticationFilter requestHeaderAuthenticationFilter =
        new RequestHeaderAuthenticationFilter();
    requestHeaderAuthenticationFilter.setPrincipalRequestHeader(Header.USER.getHeader());
    requestHeaderAuthenticationFilter.setAuthenticationManager(authenticationManager);
    // Set this to false when there's logic in place to fall back to anonymous,
    // if that turns out to be desirable.
    requestHeaderAuthenticationFilter.setExceptionIfHeaderMissing(true);

    // By default, RequestHeaderAuthenticationFilter returns
    // PreAuthenticatedAuthenticationToken tokens whose details are of type
    // WebAuthenticationDetails, since that's the default in
    // AbstractPreAuthenticatedProcessingFilter.  So we have a place to put
    // roles from fiat, let's use
    // PreAuthenticatedGrantedAuthoritiesWebAuthenticationDetails.  It's
    // authorities property is perfect for fiat roles.  And, since
    // PreAuthenticatedGrantedAuthoritiesWebAuthenticationDetails implements
    // GrantedAuthoritiesContainer, it works with
    // PreAuthenticatedGrantedAuthoritiesUserDetailsService.
    requestHeaderAuthenticationFilter.setAuthenticationDetailsSource(
        new HeaderAuthenticationDetailsSource());
    return requestHeaderAuthenticationFilter;
  }

  @Bean
  public AuthenticationProvider authenticationProvider() {
    // The token provided by PreAuthenticatedAuthenticationProvider (a
    // PreAuthenticatedAuthenticationToken), has details of type
    // WebAuthenticationDetails, because our RequestHeaderAuthenticationFilter
    // doesn't set an authenticationDetailsSource, and the default is
    // WebAuthenticationDetailsSource.
    //
    PreAuthenticatedAuthenticationProvider provider = new PreAuthenticatedAuthenticationProvider();

    // PreAuthenticatedAuthenticationProvider requires an
    // AuthenticationUserDetailsService.  spring-security-web provides
    // PreAuthenticatedGrantedAuthoritiesUserDetailsService which seems
    // convenient, except it requires that the token's getDetails method returns
    // an implementation of GrantedAuthoritiesContainer.
    // WebAuthenticationDetails doesn't implement GrantedAuthoritiesContainer,
    // so this combination of AuthenticationProvider +
    // AuthenticationUserDetailsService doesn't work, at least not out of the
    // box.
    //
    // Do we stick with PreAuthenticatedAuthenticationProvider and implement our
    // own AuthenticationUserDetailsService (possibly as a child of
    // PreAuthenticatedGrantedAuthoritiesUserDetailsService), or do we use a
    // different AuthenticationProvider.  PreAuthenticatedAuthenticationProvider
    // really does feel like what we want, and (eventually), we do want to get
    // details (i.e. authorities/roles) from fiat, so implement our own
    // AuthenticationUserDetailsService<PreAuthenticatedAuthenticationToken>.
    provider.setPreAuthenticatedUserDetailsService(
        new PreAuthenticatedGrantedAuthoritiesUserDetailsService());
    return provider;
  }

  @Bean
  public AuthenticationManager authenticationManager(
      AuthenticationProvider authenticationProvider) {
    return new ProviderManager(authenticationProvider);
  }
}
