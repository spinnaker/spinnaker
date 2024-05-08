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
import com.netflix.spinnaker.gate.security.SpinnakerAuthConfig;
import com.netflix.spinnaker.gate.services.AuthenticationService;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.session.DefaultCookieSerializerCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.saml2.core.Saml2X509Credential;
import org.springframework.security.saml2.provider.service.authentication.OpenSaml4AuthenticationProvider;
import org.springframework.security.saml2.provider.service.registration.InMemoryRelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrations;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SecuritySamlProperties.class)
public class SAMLConfiguration {

  @EnableWebSecurity
  @SpinnakerAuthConfig
  @RequiredArgsConstructor
  @ConditionalOnProperty("saml.enabled")
  public static class WebSecurityConfig extends WebSecurityConfigurerAdapter {
    private final SecuritySamlProperties properties;
    private final AuthConfig authConfig;
    private final ObjectProvider<UserIdentifierExtractor> userIdentifierExtractorProvider;
    private final ObjectProvider<UserRolesExtractor> userRolesExtractorProvider;
    private final ObjectFactory<AuthenticationService> authenticationServiceFactory;

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
          authenticationServiceFactory);
    }

    @Bean
    @SneakyThrows
    public RelyingPartyRegistrationRepository relyingPartyRegistrationRepository() {
      var builder =
          RelyingPartyRegistrations.fromMetadataLocation(properties.getMetadataUrl())
              .registrationId(properties.getRegistrationId())
              .entityId(properties.getIssuerId())
              .assertionConsumerServiceLocation(properties.getAssertionConsumerServiceLocation());
      Saml2X509Credential decryptionCredential = properties.getDecryptionCredential();
      if (decryptionCredential != null) {
        builder.decryptionX509Credentials(credentials -> credentials.add(decryptionCredential));
      }
      RelyingPartyRegistration registration = builder.build();
      return new InMemoryRelyingPartyRegistrationRepository(registration);
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
      authConfig.configure(http);
      var authenticationProvider = new OpenSaml4AuthenticationProvider();
      authenticationProvider.setResponseAuthenticationConverter(responseAuthenticationConverter());
      http.rememberMe(Customizer.withDefaults())
          .saml2Login(
              saml ->
                  saml.authenticationManager(new ProviderManager(authenticationProvider))
                      .loginProcessingUrl(properties.getLoginProcessingUrl())
                      .relyingPartyRegistrationRepository(relyingPartyRegistrationRepository()));
    }
  }
}
