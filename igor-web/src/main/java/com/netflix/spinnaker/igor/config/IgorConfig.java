/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.igor.config;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.config.PluginsAutoConfiguration;
import com.netflix.spinnaker.fiat.shared.EnableFiatAutoConfig;
import com.netflix.spinnaker.filters.AuthenticatedRequestFilter;
import com.netflix.spinnaker.igor.IgorConfigurationProperties;
import com.netflix.spinnaker.igor.artifacts.ArtifactServices;
import com.netflix.spinnaker.igor.service.BuildServices;
import com.netflix.spinnaker.kork.artifacts.parsing.DefaultJinjavaFactory;
import com.netflix.spinnaker.kork.artifacts.parsing.JinjaArtifactExtractor;
import com.netflix.spinnaker.kork.artifacts.parsing.JinjavaFactory;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.kork.web.interceptors.MetricsInterceptor;
import groovy.util.logging.Slf4j;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.task.TaskSchedulerCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@Slf4j
@EnableFiatAutoConfig
@EnableScheduling
@Import(PluginsAutoConfiguration.class)
public class IgorConfig implements WebMvcConfigurer {

  private final Registry registry;

  public IgorConfig(Registry registry) {
    this.registry = registry;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(
        new MetricsInterceptor(
            this.registry,
            "controller.invocations",
            Collections.singletonList("master"),
            null,
            Collections.singletonList("BasicErrorController")));
  }

  @Bean
  public FilterRegistrationBean<AuthenticatedRequestFilter> authenticatedRequestFilter() {
    FilterRegistrationBean<AuthenticatedRequestFilter> frb =
        new FilterRegistrationBean<>(new AuthenticatedRequestFilter(true));
    frb.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return frb;
  }

  @Bean
  public StrictHttpFirewall httpFirewall() {
    StrictHttpFirewall firewall = new StrictHttpFirewall();
    firewall.setAllowUrlEncodedSlash(true);
    firewall.setAllowUrlEncodedPercent(true);
    return firewall;
  }

  @Bean
  public BuildServices buildServices() {
    return new BuildServices();
  }

  @Bean
  public ArtifactServices artifactServices() {
    return new ArtifactServices();
  }

  @Bean
  public ExecutorService executorService() {
    return Executors.newCachedThreadPool();
  }

  /** TODO: Replace with R4J */
  @Bean
  public RetrySupport retrySupport() {
    return new RetrySupport();
  }

  @Bean
  @ConditionalOnMissingBean
  public JinjavaFactory jinjavaFactory() {
    return new DefaultJinjavaFactory();
  }

  @Bean
  public JinjaArtifactExtractor.Factory jinjaArtifactExtractorFactory(
      JinjavaFactory jinjavaFactory) {
    return new JinjaArtifactExtractor.Factory(jinjavaFactory);
  }

  @Bean
  TaskSchedulerCustomizer taskSchedulerCustomizer(IgorConfigurationProperties igorProperties) {
    return (scheduler) ->
        scheduler.setPoolSize(igorProperties.getSpinnaker().getBuild().getSchedulerPoolSize());
  }
}
