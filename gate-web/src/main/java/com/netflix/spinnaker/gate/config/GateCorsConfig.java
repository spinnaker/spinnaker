/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.gate.config;

import com.netflix.spinnaker.gate.filters.CorsFilter;
import com.netflix.spinnaker.gate.filters.GateOriginValidator;
import com.netflix.spinnaker.gate.filters.OriginValidator;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class GateCorsConfig {

  private static final List<String> ALLOWED_HEADERS =
      Arrays.asList(
          "x-requested-with",
          "content-type",
          "authorization",
          "X-ratelimit-app",
          "X-spinnaker-priority");

  private static final Long MAX_AGE_IN_SECONDS = 3600L;

  @Bean
  OriginValidator gateOriginValidator(
      @Value("${services.deck.base-url:}") String deckBaseUrl,
      @Value("${services.deck.redirect-host-pattern:#{null}}") String redirectHostPattern,
      @Value("${cors.allowed-origins-pattern:#{null}}") String allowedOriginsPattern,
      @Value("${cors.expect-localhost:false}") boolean expectLocalhost) {
    return new GateOriginValidator(
        deckBaseUrl, redirectHostPattern, allowedOriginsPattern, expectLocalhost);
  }

  @Bean
  @ConditionalOnProperty(name = "cors.allow-mode", havingValue = "regex", matchIfMissing = true)
  FilterRegistrationBean regExCorsFilter(OriginValidator gateOriginValidator) {
    FilterRegistrationBean filterRegBean =
        new FilterRegistrationBean<>(new CorsFilter(gateOriginValidator));
    filterRegBean.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return filterRegBean;
  }

  @Bean
  @ConditionalOnProperty(name = "cors.allow-mode", havingValue = "list")
  FilterRegistrationBean allowedOriginCorsFilter(
      @Value("${cors.allowed-origins:*}") List<String> allowedOriginList) {
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowCredentials(true);
    config.setAllowedOrigins(allowedOriginList);
    config.setAllowedHeaders(ALLOWED_HEADERS);
    config.setMaxAge(MAX_AGE_IN_SECONDS);
    config.addAllowedMethod("*"); // Enable CORS for all methods.
    source.registerCorsConfiguration("/**", config); // Enable CORS for all paths
    FilterRegistrationBean filterRegBean =
        new FilterRegistrationBean<>(new org.springframework.web.filter.CorsFilter(source));
    filterRegBean.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return filterRegBean;
  }
}
