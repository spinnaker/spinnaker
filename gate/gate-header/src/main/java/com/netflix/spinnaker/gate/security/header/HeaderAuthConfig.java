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
import com.netflix.spinnaker.kork.common.Header;
import org.apache.catalina.Container;
import org.apache.catalina.core.StandardHost;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.websocket.servlet.TomcatWebSocketServletWebServerCustomizer;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationProvider;
import org.springframework.security.web.authentication.preauth.RequestHeaderAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

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

    // I don't see a way to retrieve the
    // PreAuthenticatedProcessingRequestMatcher from
    // RequestHeaderAuthenticationFilter (really
    // AbstractPreAuthenticatedProcessingFilter).  But, we'd like to be able to
    // say that e.g. /error and /auth/logout don't require authentication.
    // Words are hard here.  It's pre-authenticated, as in we've already
    // identified the user (via X-SPINNAKER-USER), but we haven't retrieved
    // roles...and for e.g. /error and /auth/logout, we don't want/need to.  It
    // does seem a little strange to duplicate some information that
    // AuthConfig.configure sets up, but so be it.
    HeaderAuthRequestMatcher headerAuthRequestMatcher = new HeaderAuthRequestMatcher();
    requestHeaderAuthenticationFilter.setRequiresAuthenticationRequestMatcher(
        headerAuthRequestMatcher);

    // Set this to false when there's logic in place to fall back to anonymous,
    // if that turns out to be desirable.
    requestHeaderAuthenticationFilter.setExceptionIfHeaderMissing(true);

    // By default, RequestHeaderAuthenticationFilter returns
    // PreAuthenticatedAuthenticationToken tokens whose details are of type
    // WebAuthenticationDetails, since that's the default in
    // AbstractPreAuthenticatedProcessingFilter.  To pave the way to migrate to
    // "real" GrantedAuthorities, as opposed roles and allowedAccounts in kork's
    // User class (which derives authorities from roles), let's use
    // PreAuthenticatedGrantedAuthoritiesWebAuthenticationDetails.
    //
    // Since PreAuthenticatedGrantedAuthoritiesWebAuthenticationDetails
    // implements GrantedAuthoritiesContainer, it works with
    // PreAuthenticatedGrantedAuthoritiesUserDetailsService.
    // HeaderAuthenticationDetailsSource takes care of this.
    requestHeaderAuthenticationFilter.setAuthenticationDetailsSource(
        new HeaderAuthenticationDetailsSource());

    HttpSessionSecurityContextRepository securityContextRepository =
        new HttpSessionSecurityContextRepository();

    // Save the work to read and write session information.  Each request
    // provides X-SPINNAKER-USER, and gate caches information from fiat, so
    // there's no need for callers to support session cookies, and dealing with
    // expiration, etc.
    //
    // With this, when services.fiat.legacyFallback is false, FiatSessionFilter
    // doesn't ever do meaningful work because request.getSession() always returns
    // null, so save some cycles by setting fiat.session-filter.enabled to false.
    //
    // When services.fiat.legacyFallback is true, FiatSessionFilter still
    // invalidates the cache for the user.
    securityContextRepository.setAllowSessionCreation(false);
    requestHeaderAuthenticationFilter.setSecurityContextRepository(securityContextRepository);
    return requestHeaderAuthenticationFilter;
  }

  @Bean
  public AuthenticationProvider authenticationProvider(
      PermissionService permissionService, AllowedAccountsSupport allowedAccountsSupport) {
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
    provider.setPreAuthenticatedUserDetailsService(
        new HeaderAuthenticationUserDetailsService(permissionService, allowedAccountsSupport));
    return provider;
  }

  @Bean
  public AuthenticationManager authenticationManager(
      AuthenticationProvider authenticationProvider) {
    return new ProviderManager(authenticationProvider);
  }

  /**
   * Inspired by https://github.com/spring-projects/spring-boot/issues/21257#issuecomment-745565376
   * to customize tomcat exception handling, and specifically to generate json responses for
   * exceptions that bubble up to tomcat / aren't handled by spring boot nor spring security.
   */
  @Bean
  public TomcatWebSocketServletWebServerCustomizer errorValveCustomizer() {
    return new TomcatWebSocketServletWebServerCustomizer() {
      @Override
      public void customize(TomcatServletWebServerFactory factory) {
        factory.addContextCustomizers(
            (context) -> {
              Container parent = context.getParent();
              if (parent instanceof StandardHost) {
                ((StandardHost) parent)
                    .setErrorReportValveClass(
                        "com.netflix.spinnaker.gate.tomcat.SpinnakerTomcatErrorValve");
              }
            });
      }
    };
  }
}
