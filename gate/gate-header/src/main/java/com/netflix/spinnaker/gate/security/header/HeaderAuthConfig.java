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
import org.springframework.security.web.authentication.preauth.RequestHeaderAuthenticationFilter;

/**
 * In combination with HeaderAuthConfigurerAdapter, authenticate the X-SPINNAKER-USER header using
 * permissions obtained from fiat. See
 * https://bwgjoseph.com/spring-security-custom-pre-authentication-flow for background.
 */
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
    // HeaderAuthenticationDetailsSource takes care of this.
    requestHeaderAuthenticationFilter.setAuthenticationDetailsSource(
        new HeaderAuthenticationDetailsSource());
    return requestHeaderAuthenticationFilter;
  }

  @Bean
  public AuthenticationProvider authenticationProvider() {
    // PreAuthenticatedAuthenticationProvider provides tokens of type
    // PreAuthenticatedAuthenticationToken.  Because our
    // RequestHeaderAuthenticationFilter sets an authenticationDetailsSource to
    // gate's HeaderAuthenticationDetailsSource class, those tokens have details
    // of type PreAuthenticatedGrantedAuthoritiesWebAuthenticationDetails
    // instead of the default WebAuthenticationDetails.
    PreAuthenticatedAuthenticationProvider provider = new PreAuthenticatedAuthenticationProvider();

    // PreAuthenticatedAuthenticationProvider requires an
    // AuthenticationUserDetailsService.  spring-security-web provides
    // PreAuthenticatedGrantedAuthoritiesUserDetailsService which seems
    // convenient.  It requires that the token's getDetails method returns an
    // implementation of GrantedAuthoritiesContainer, and happily,
    // PreAuthenticatedGrantedAuthoritiesWebAuthenticationDetails implements it.
    //
    // However, there's a snag.  Some parts of gate (e.g. the /auth/user and
    // /credentials/{account} endpoints) expect a User from kork-security, as
    // opposed to the UserDetails interface that spring security specifies (or
    // perhaps the User from spring-security-core.  So,
    // PreAuthenticatedGrantedAuthoritiesUserDetailsService isn't sufficient.
    // We eiter need to stop using User from kork-security, or implement
    // something that generates user details with kork-security User objects.
    // To try to rock the boat as little as possible, generate kork-security
    // User objects via gate's own user details service
    provider.setPreAuthenticatedUserDetailsService(new HeaderAuthenticationUserDetailsService());
    return provider;
  }

  @Bean
  public AuthenticationManager authenticationManager(
      AuthenticationProvider authenticationProvider) {
    return new ProviderManager(authenticationProvider);
  }
}
