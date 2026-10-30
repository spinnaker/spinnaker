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
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.boot.web.server.Cookie;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.saml2.provider.service.authentication.OpenSaml5AuthenticationProvider;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.util.StringUtils;

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
  @EnableConfigurationProperties(ServerProperties.class)
  public static class WebSecurityConfig {
    private final SecuritySamlProperties properties;
    private final AuthConfig authConfig;
    private final ObjectProvider<UserIdentifierExtractor> userIdentifierExtractorProvider;
    private final ObjectProvider<UserRolesExtractor> userRolesExtractorProvider;
    private final ObjectFactory<AuthenticationService> authenticationServiceFactory;
    private final ObjectFactory<AllowedAccountsSupport> allowedAccountsSupportFactory;

    /** Applies native session cookie settings while preserving the legacy unspecified SameSite. */
    @Bean
    public DefaultCookieSerializer defaultCookieSerializer(ServerProperties serverProperties) {
      Cookie cookie = serverProperties.getServlet().getSession().getCookie();
      DefaultCookieSerializer serializer = new DefaultCookieSerializer();
      serializer.setSameSite(null);
      // Match Boot's SessionAutoConfiguration mapping for this user-defined serializer.
      PropertyMapper map = PropertyMapper.get();
      map.from(cookie::getName).to(serializer::setCookieName);
      map.from(cookie::getDomain).to(serializer::setDomainName);
      map.from(cookie::getPath).to(serializer::setCookiePath);
      map.from(cookie::getHttpOnly).to(serializer::setUseHttpOnlyCookie);
      map.from(cookie::getSecure).to(serializer::setUseSecureCookie);
      map.from(cookie::getMaxAge).asInt(Duration::getSeconds).to(serializer::setCookieMaxAge);
      map.from(cookie::getSameSite)
          .to(sameSite -> serializer.setSameSite(sameSite.attributeValue()));
      map.from(cookie::getPartitioned).to(serializer::setPartitioned);
      return serializer;
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
      // Handles redirect in newer spring systems
      HttpSessionRequestCache requestCache = new HttpSessionRequestCache();
      requestCache.setMatchingRequestParameterName(null);
      http.requestCache(cache -> cache.requestCache(requestCache));

      http.saml2Login(
          saml -> {
            var authenticationProvider = new OpenSaml5AuthenticationProvider();
            authenticationProvider.setResponseAuthenticationConverter(
                responseAuthenticationConverter());
            saml.authenticationManager(new ProviderManager(authenticationProvider));
            saml.relyingPartyRegistrationRepository(registrations);
            if (StringUtils.hasLength(properties.getLoginProcessingUrl())) {
              saml.loginProcessingUrl(properties.getLoginProcessingUrl());
            }
          });
      return http.build();
    }
  }
}
