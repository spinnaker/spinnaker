/*
 * Copyright 2025 OpsMx, Inc.
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

package com.netflix.spinnaker.gate.security.oauth2;

import com.netflix.spinnaker.gate.config.AuthConfig;
import com.netflix.spinnaker.gate.security.SpinnakerAuthConfig;
import java.time.Duration;
import java.util.HashMap;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Configuration
@EnableWebSecurity
@SpinnakerAuthConfig
@Conditional(OAuthConfigEnabled.class)
@EnableConfigurationProperties(ExternalAuthTokenFilterConfigurationProperties.class)
public class OAuth2SsoConfig {

  @Autowired private AuthConfig authConfig;
  @Autowired private SpinnakerOAuth2UserInfoService customOAuth2UserService;
  @Autowired private SpinnakerOIDCUserInfoService oidcUserInfoService;
  @Autowired private DefaultCookieSerializer defaultCookieSerializer;
  @Autowired private ClientRegistrationRepository clientRegistrationRepository;
  @Autowired private OAuthUserInfoServiceHelper userInfoServiceHelper;

  @Autowired
  private OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> tokenResponseClient;

  @Autowired
  private ExternalAuthTokenFilterConfigurationProperties externalAuthTokenFilterProperties;

  @Bean
  // ManagedDeliverySchemaEndpointConfiguration#schemaSecurityFilterChain should go first
  @Order(2)
  SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception {
    defaultCookieSerializer.setSameSite(null);
    authConfig.configure(httpSecurity);
    httpSecurity
        .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
        .oauth2Login(
            oauth2 ->
                oauth2
                    .userInfoEndpoint(
                        userInfo ->
                            userInfo
                                .userService(customOAuth2UserService)
                                .oidcUserService(oidcUserInfoService))
                    // Using same token response client that get sets by default this is to allows
                    // injection of a mock or test implementation
                    // for unit/integration tests, so we don't need to call GitHub (or any real
                    // OAuth2 provider)
                    .tokenEndpoint()
                    .accessTokenResponseClient(tokenResponseClient));

    // Add external auth token filter if there is a registration ID
    String registrationId = getFirstRegistrationId();
    if (registrationId != null) {
      RestTemplate restTemplate = createRestTemplateWithTimeouts();
      ExternalAuthTokenFilter externalAuthTokenFilter =
          new ExternalAuthTokenFilter(
              clientRegistrationRepository, userInfoServiceHelper, registrationId, restTemplate);
      httpSecurity.addFilterBefore(externalAuthTokenFilter, OAuth2LoginAuthenticationFilter.class);
    }

    return httpSecurity.build();
  }

  private RestTemplate createRestTemplateWithTimeouts() {
    return new RestTemplateBuilder()
        .setConnectTimeout(
            Duration.ofMillis(externalAuthTokenFilterProperties.getConnectTimeoutMs()))
        .setReadTimeout(Duration.ofMillis(externalAuthTokenFilterProperties.getReadTimeoutMs()))
        .build();
  }

  private String getFirstRegistrationId() {
    if (clientRegistrationRepository instanceof InMemoryClientRegistrationRepository inMemoryRepo) {
      for (ClientRegistration registration : inMemoryRepo) {
        return registration.getRegistrationId();
      }
    }
    log.warn(
        "ClientRegistrationRepository is not an InMemoryClientRegistrationRepository (found: {}). "
            + "ExternalAuthTokenFilter will not be enabled.",
        clientRegistrationRepository.getClass().getName());
    return null;
  }

  /**
   * Use this class to specify how to map fields from the userInfoUri response to what's expected to
   * be in the User.
   */
  @Component
  @ConfigurationProperties("spring.security.oauth2.client.registration.user-info-mapping")
  @Data
  public static class UserInfoMapping {
    private String email = "email";
    private String firstName = "given_name";
    private String lastName = "family_name";
    private String username = "email";
    private String serviceAccountEmail = "client_email";
    private String roles = null;
  }

  @Component
  @ConfigurationProperties("spring.security.oauth2.client.registration.user-info-requirements")
  public static class UserInfoRequirements extends HashMap<String, String> {}
}
