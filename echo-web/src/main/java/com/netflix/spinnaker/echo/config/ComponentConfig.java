/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.echo.config;

import com.google.common.collect.ImmutableList;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.fiat.shared.EnableFiatAutoConfig;
import com.netflix.spinnaker.filters.AuthenticatedRequestFilter;
import com.netflix.spinnaker.kork.web.interceptors.MetricsInterceptor;
import java.util.List;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Finds spring beans (@Component, @Resource, @Controller, etc.) on your classpath. If you don't
 * like classpath scanning, don't use it, you'll always have the choice. I generally
 * exclude @Configuration's from this scan, as picking those up can affect your tests.
 */
@Configuration
@EnableFiatAutoConfig
@ComponentScan(
    basePackages = {"com.netflix.spinnaker.echo"},
    excludeFilters = @Filter(value = Configuration.class, type = FilterType.ANNOTATION))
public class ComponentConfig implements WebMvcConfigurer {
  private Registry registry;

  public ComponentConfig(Registry registry) {
    this.registry = registry;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    List<String> pathVarsToTag = ImmutableList.of("type", "source");
    List<String> exclude = ImmutableList.of("BasicErrorController");
    MetricsInterceptor interceptor =
        new MetricsInterceptor(this.registry, "controller.invocations", pathVarsToTag, exclude);
    registry.addInterceptor(interceptor);
  }

  @Bean
  public FilterRegistrationBean authenticatedRequestFilter() {
    FilterRegistrationBean frb =
        new FilterRegistrationBean(new AuthenticatedRequestFilter(true, true, false));
    frb.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return frb;
  }
}
