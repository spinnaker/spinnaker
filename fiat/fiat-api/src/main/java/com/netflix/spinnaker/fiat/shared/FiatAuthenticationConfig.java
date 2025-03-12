/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.fiat.shared;

import static org.springframework.core.Ordered.HIGHEST_PRECEDENCE;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.config.ErrorConfiguration;
import com.netflix.spinnaker.config.OkHttp3ClientConfiguration;
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import com.netflix.spinnaker.kork.web.exceptions.ExceptionMessageDecorator;
import lombok.val;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.AuthenticationConverter;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@Import(ErrorConfiguration.class)
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
@Configuration
@EnableConfigurationProperties(FiatClientConfigurationProperties.class)
@ComponentScan("com.netflix.spinnaker.fiat.shared")
public class FiatAuthenticationConfig {

  @Bean
  @ConditionalOnMissingBean(FiatService.class) // Allows for override
  public FiatService fiatService(
      FiatClientConfigurationProperties fiatConfigurationProperties,
      OkHttp3ClientConfiguration okHttpClientConfig) {
    // New role providers break deserialization if this is not enabled.
    val objectMapper = new ObjectMapper();
    objectMapper.enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL);
    objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    return new Retrofit.Builder()
        .baseUrl(fiatConfigurationProperties.getBaseUrl())
        .client(okHttpClientConfig.createForRetrofit2().build())
        .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
        .addConverterFactory(JacksonConverterFactory.create(objectMapper))
        .build()
        .create(FiatService.class);
  }

  /**
   * When enabled, this authenticates the {@code X-SPINNAKER-USER} HTTP header using permissions
   * obtained from {@link FiatPermissionEvaluator#getPermission(String)}. This feature is part of a
   * larger effort to adopt standard Spring Security APIs rather than using Fiat directly where
   * possible.
   */
  @ConditionalOnProperty("services.fiat.granted-authorities.enabled")
  @Bean
  AuthenticationConverter fiatAuthenticationFilter(FiatPermissionEvaluator permissionEvaluator) {
    return new FiatAuthenticationConverter(permissionEvaluator);
  }

  /**
   * Provides the previous behavior of using PreAuthenticatedAuthenticationToken with no granted
   * authorities to indicate an authenticated user or an AnonymousAuthenticationToken with an
   * "ANONYMOUS" role for anonymous authenticated users.
   */
  @ConditionalOnMissingBean
  @Bean
  AuthenticationConverter defaultAuthenticationConverter() {
    return new AuthenticatedRequestAuthenticationConverter();
  }

  @Bean
  FiatWebSecurityConfigurerAdapter fiatSecurityConfig(
      FiatStatus fiatStatus, AuthenticationConverter authenticationConverter) {
    return new FiatWebSecurityConfigurerAdapter(fiatStatus, authenticationConverter);
  }

  @Bean
  @Order(HIGHEST_PRECEDENCE)
  FiatAccessDeniedExceptionHandler fiatAccessDeniedExceptionHandler(
      ExceptionMessageDecorator exceptionMessageDecorator) {
    return new FiatAccessDeniedExceptionHandler(exceptionMessageDecorator);
  }

  private static class FiatWebSecurityConfigurerAdapter extends WebSecurityConfigurerAdapter {
    private final FiatStatus fiatStatus;
    private final AuthenticationConverter authenticationConverter;

    private FiatWebSecurityConfigurerAdapter(
        FiatStatus fiatStatus, AuthenticationConverter authenticationConverter) {
      super(true);
      this.fiatStatus = fiatStatus;
      this.authenticationConverter = authenticationConverter;
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
      http.servletApi()
          .and()
          .exceptionHandling()
          .and()
          .anonymous()
          .and()
          .addFilterBefore(
              new FiatAuthenticationFilter(fiatStatus, authenticationConverter),
              AnonymousAuthenticationFilter.class);
    }
  }
}
