/*
 * Copyright 2026 Harness, Inc.
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
package com.netflix.spinnaker.kork.web.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.filter.UrlHandlerFilter;

/**
 * Registers a {@link UrlHandlerFilter} that trims trailing slashes from incoming request paths so
 * that requests like {@code PUT /artifacts/fetch/} are routed to controllers mapped to {@code
 * /artifacts/fetch}.
 *
 * <p>This restores the lenient trailing slash matching that was the default before Spring Framework
 * 6.0 / Spring Boot 3.0. Because it lives in kork-web, every Spinnaker service that depends on
 * kork-web inherits the behavior without per-service configuration. It can be disabled with {@code
 * url-handler.trailing-slash.enabled=false}.
 */
@AutoConfiguration
@ConditionalOnClass(UrlHandlerFilter.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(value = "url-handler.trailing-slash.enabled", matchIfMissing = true)
@EnableConfigurationProperties(UrlHandlerFilterConfigurationProperties.class)
public class UrlHandlerFilterAutoConfiguration {

  @Bean
  public FilterRegistrationBean<UrlHandlerFilter> trailingSlashUrlHandlerFilter(
      UrlHandlerFilterConfigurationProperties properties) {
    UrlHandlerFilter filter =
        UrlHandlerFilter.trailingSlashHandler(properties.getPathPatterns().toArray(new String[0]))
            .wrapRequest()
            .build();

    FilterRegistrationBean<UrlHandlerFilter> registration = new FilterRegistrationBean<>(filter);
    // Run before other filters (e.g. security) so the trimmed path is used consistently.
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return registration;
  }
}
