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

import com.netflix.spinnaker.gate.config.AuthConfig;
import com.netflix.spinnaker.gate.security.AllowedAccountsSupport;
import com.netflix.spinnaker.gate.security.SpinnakerAuthConfig;
import com.netflix.spinnaker.gate.services.AuthenticationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.session.DefaultCookieSerializerCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.saml2.provider.service.authentication.OpenSaml4AuthenticationProvider;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

/**
 * Configures SAML authentication for Spinnaker Gate.
 *
 * <p>SAML connection properties (IdP metadata URI, entity ID, ACS location, signing/decryption
 * credentials) are configured via Spring Boot's native {@code
 * spring.security.saml2.relyingparty.registration.*} properties. Spinnaker-specific behaviour (user
 * attribute mapping, required roles) is configured via the {@code saml.*} properties in {@link
 * SecuritySamlProperties}.
 *
 * <p>See {@code gate/gate-saml/docs/saml-migration.md} for migration instructions.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SecuritySamlProperties.class)
public class SAMLConfiguration {

  @EnableWebSecurity
  @SpinnakerAuthConfig
  @RequiredArgsConstructor
  @ConditionalOnProperty("saml.enabled")
  public static class WebSecurityConfig {
    private final SecuritySamlProperties properties;
    private final AuthConfig authConfig;
    private final ObjectProvider<UserIdentifierExtractor> userIdentifierExtractorProvider;
    private final ObjectProvider<UserRolesExtractor> userRolesExtractorProvider;
    private final ObjectFactory<AuthenticationService> authenticationServiceFactory;
    private final ObjectFactory<AllowedAccountsSupport> allowedAccountsSupportFactory;

    /** Disables the same-site requirement for cookies as configured in other SSO modules. */
    @Bean
    public static DefaultCookieSerializerCustomizer defaultCookieSerializerCustomizer() {
      return cookieSerializer -> cookieSerializer.setSameSite(null);
    }

    @Bean
    public ResponseAuthenticationConverter responseAuthenticationConverter() {
      return new ResponseAuthenticationConverter(
          properties,
          () ->
              userIdentifierExtractorProvider.getIfAvailable(
                  () -> new DefaultUserIdentifierExtractor(properties)),
          () ->
              userRolesExtractorProvider.getIfAvailable(
                  () -> new DefaultUserRolesExtractor(properties)),
          authenticationServiceFactory,
          allowedAccountsSupportFactory);
    }

    @Bean
    // ManagedDeliverySchemaEndpointConfiguration#schemaSecurityFilterChain should go first
    @Order(3)
    public SecurityFilterChain securityFilterChain(
        HttpSecurity http, RelyingPartyRegistrationRepository registrations) throws Exception {
      authConfig.configure(http);
      HttpSessionRequestCache requestCache = new HttpSessionRequestCache();
      requestCache.setMatchingRequestParameterName(null);

      var authenticationProvider = new OpenSaml4AuthenticationProvider();
      authenticationProvider.setResponseAuthenticationConverter(responseAuthenticationConverter());
      return http.saml2Login(
              saml ->
                  saml.authenticationManager(new ProviderManager(authenticationProvider))
                      .relyingPartyRegistrationRepository(registrations))
          .requestCache(cache -> cache.requestCache(requestCache))
          .build();
    }
  }
}
