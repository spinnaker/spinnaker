/*
 * Copyright 2026 DoorDash, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.gate.security.apitoken;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import com.netflix.spinnaker.gate.security.AllowedAccountsSupport;
import com.netflix.spinnaker.gate.services.PermissionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPool;

/**
 * Registers all API token beans when {@code api-tokens.enabled=true}. All token state is stored in
 * Redis via {@link RedisApiTokenRepository}; no Front50 dependency for token operations.
 */
@Configuration
@ConditionalOnProperty("api-tokens.enabled")
@EnableConfigurationProperties(ApiTokenProperties.class)
public class ApiTokenConfig {

  @Bean
  public RedisApiTokenRepository redisApiTokenRepository(
      JedisPool jedisPool, ObjectMapper objectMapper, ApiTokenProperties properties) {
    return new RedisApiTokenRepository(jedisPool, objectMapper, properties.getRedisKeyPrefix());
  }

  @Bean
  public ApiTokenService apiTokenService(
      RedisApiTokenRepository redisApiTokenRepository,
      PermissionService permissionService,
      ApiTokenProperties properties) {
    return new ApiTokenService(redisApiTokenRepository, permissionService, properties);
  }

  @Bean
  public ApiTokenAuthenticationFilter apiTokenAuthenticationFilter(
      ApiTokenProperties properties,
      ApiTokenService apiTokenService,
      PermissionService permissionService,
      FiatPermissionEvaluator permissionEvaluator,
      AllowedAccountsSupport allowedAccountsSupport) {
    return new ApiTokenAuthenticationFilter(
        properties,
        apiTokenService,
        permissionService,
        permissionEvaluator,
        allowedAccountsSupport);
  }

  /**
   * Prevent Boot from auto-registering this filter as a standalone servlet filter; it's already
   * wired into the {@code ApiTokenAuthConfigurerAdapter} chain and would otherwise run twice.
   */
  @Bean
  public FilterRegistrationBean<ApiTokenAuthenticationFilter> apiTokenFilterRegistration(
      ApiTokenAuthenticationFilter filter) {
    FilterRegistrationBean<ApiTokenAuthenticationFilter> registration =
        new FilterRegistrationBean<>(filter);
    registration.setEnabled(false);
    return registration;
  }
}
