/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.configuration.CredentialsConfiguration;
import com.netflix.spinnaker.clouddriver.requestqueue.RequestQueue;
import com.netflix.spinnaker.clouddriver.requestqueue.RequestQueueConfiguration;
import com.netflix.spinnaker.filters.AuthenticatedRequestFilter;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.netflix.spinnaker.kork.web.context.MdcCopyingAsyncTaskExecutor;
import com.netflix.spinnaker.kork.web.interceptors.MetricsInterceptor;
import jakarta.servlet.Filter;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.web.filter.ShallowEtagHeaderFilter;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@ComponentScan({
  "com.netflix.spinnaker.clouddriver.controllers",
  "com.netflix.spinnaker.clouddriver.filters",
  "com.netflix.spinnaker.clouddriver.listeners",
  "com.netflix.spinnaker.clouddriver.security",
})
@EnableConfigurationProperties({CredentialsConfiguration.class, RequestQueueConfiguration.class})
public class WebConfig implements WebMvcConfigurer {
  private final Registry registry;
  private final AsyncTaskExecutor asyncTaskExecutor;

  @Autowired
  public WebConfig(
      Registry registry,
      @Qualifier("threadPoolTaskScheduler") AsyncTaskExecutor asyncTaskExecutor) {
    this.registry = registry;
    this.asyncTaskExecutor = asyncTaskExecutor;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(
        new MetricsInterceptor(
            this.registry,
            "controller.invocations",
            List.of("account", "region"),
            List.of("BasicErrorController")));
  }

  @Bean
  Filter eTagFilter() {
    return new ShallowEtagHeaderFilter();
  }

  @Bean
  RequestQueue requestQueue(
      DynamicConfigService dynamicConfigService,
      RequestQueueConfiguration requestQueueConfiguration,
      Registry registry) {
    return RequestQueue.forConfig(dynamicConfigService, registry, requestQueueConfiguration);
  }

  @Bean
  AuthenticatedRequestFilter authenticatedRequestFilter() {
    return new AuthenticatedRequestFilter(true);
  }

  @Bean
  FilterRegistrationBean authenticatedRequestFilterRegistrationBean(
      AuthenticatedRequestFilter authenticatedRequestFilter) {
    FilterRegistrationBean frb = new FilterRegistrationBean(authenticatedRequestFilter);
    frb.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return frb;
  }

  @Override
  public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
    configurer
        .defaultContentType(MediaType.APPLICATION_JSON_UTF8)
        .favorPathExtension(false)
        .ignoreAcceptHeader(true);
  }

  @Override
  public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
    configurer.setTaskExecutor(new MdcCopyingAsyncTaskExecutor(asyncTaskExecutor));
  }
}
