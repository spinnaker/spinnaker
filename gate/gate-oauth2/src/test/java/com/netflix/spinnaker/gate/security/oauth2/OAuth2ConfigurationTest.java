/*
 * Copyright 2025 Salesforce, Inc.
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
 */

package com.netflix.spinnaker.gate.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.netflix.spinnaker.fiat.shared.FiatClientConfigurationProperties;
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import com.netflix.spinnaker.fiat.shared.FiatStatus;
import com.netflix.spinnaker.gate.config.AuthConfig;
import com.netflix.spinnaker.gate.config.PermissionRevokingLogoutSuccessHandler;
import com.netflix.spinnaker.gate.config.RequestMatcherProvider;
import com.netflix.spinnaker.gate.services.PermissionService;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.session.web.http.DefaultCookieSerializer;

/** Verify that OAuth2SsoConfig is valid */
class OAuth2ConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withPropertyValues(
              "spring.security.oauth2.client.registration.github.client-id=client-id",
              "spring.security.oauth2.client.registration.github.client-secret=client-secret")
          .withConfiguration(
              UserConfigurations.of(
                  TestConfiguration.class,
                  FiatClientConfigurationProperties.class,
                  DefaultCookieSerializer.class,
                  PermissionRevokingLogoutSuccessHandler.class,
                  AuthConfig.class,
                  OAuth2BeanConfiguration.class,
                  OAuth2SsoConfig.class));

  @BeforeEach
  void init(TestInfo testInfo) {
    System.out.println("--------------- Test " + testInfo.getDisplayName());
  }

  @Test
  void testOAuth2ConfigIsValid() {
    runner.run(
        ctx -> {
          assertThat(ctx).hasSingleBean(OAuth2AccessTokenResponseClient.class);
        });
  }

  private static class TestConfiguration {
    @Bean
    PermissionService permissionService() {
      return mock(PermissionService.class);
    }

    @Bean
    FiatStatus fiatStatus() {
      return mock(FiatStatus.class);
    }

    @Bean
    FiatPermissionEvaluator fiatPermissionEvaluator() {
      return mock(FiatPermissionEvaluator.class);
    }

    @Bean
    RequestMatcherProvider requestMatcherProvider() {
      return mock(RequestMatcherProvider.class);
    }

    @Bean
    SpinnakerOAuth2UserInfoService spinnakerOAuth2UserInfoService() {
      return mock(SpinnakerOAuth2UserInfoService.class);
    }

    @Bean
    SpinnakerOIDCUserInfoService spinnakerOIDCUserInfoService() {
      return mock(SpinnakerOIDCUserInfoService.class);
    }

    @Bean
    DynamicConfigService dynamicConfigService() {
      return mock(DynamicConfigService.class);
    }

    @Bean
    ClientRegistrationRepository clientRegistrationRepository() {
      return mock(ClientRegistrationRepository.class);
    }

    @Bean
    OAuthUserInfoServiceHelper oAuthUserInfoServiceHelper() {
      return mock(OAuthUserInfoServiceHelper.class);
    }

    @Bean
    ExternalAuthTokenFilterConfigurationProperties
        externalAuthTokenFilterConfigurationProperties() {
      return new ExternalAuthTokenFilterConfigurationProperties();
    }
  }
}
